package com.httppal.service.impl

import com.httppal.model.*
import com.httppal.service.WebSocketService
import okhttp3.*
import okio.ByteString
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Implementation of WebSocketService using OkHttp WebSocket client
 */
class WebSocketServiceImpl : WebSocketService {
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for WebSocket
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // Store active connections
    private val connections = ConcurrentHashMap<String, MutableWebSocketConnection>()
    
    // Store message listeners
    private val messageListeners = ConcurrentHashMap<String, CopyOnWriteArrayList<(WebSocketMessage) -> Unit>>()
    
    // Store status listeners
    private val statusListeners = ConcurrentHashMap<String, CopyOnWriteArrayList<(ConnectionStatus) -> Unit>>()
    
    override fun connect(url: String, headers: Map<String, String>): String {
        val connectionId = java.util.UUID.randomUUID().toString()
        
        // Create initial connection object
        val connection = MutableWebSocketConnection(
            id = connectionId,
            url = url,
            status = ConnectionStatus.CONNECTING,
            headers = headers,
            messages = mutableListOf()
        )
        
        connections[connectionId] = connection
        
        // Build request with headers
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (name, value) ->
            requestBuilder.addHeader(name, value)
        }
        
        // Create WebSocket listener
        val listener = createWebSocketListener(connectionId)
        
        // Start WebSocket connection
        val webSocket = okHttpClient.newWebSocket(requestBuilder.build(), listener)
        connection.webSocket = webSocket
        
        // Notify status listeners
        notifyStatusListeners(connectionId, ConnectionStatus.CONNECTING)
        
        return connectionId
    }
    
    override fun disconnect(connectionId: String): Boolean {
        val connection = connections[connectionId] ?: return false
        
        connection.webSocket?.close(1000, "Client disconnect")
        connection.status = ConnectionStatus.DISCONNECTED
        connection.disconnectedAt = Instant.now()
        
        // Notify status listeners
        notifyStatusListeners(connectionId, ConnectionStatus.DISCONNECTED)
        
        return true
    }
    
    override fun sendMessage(connectionId: String, message: String): Boolean {
        val connection = connections[connectionId] ?: return false
        
        if (connection.status != ConnectionStatus.CONNECTED) {
            return false
        }
        
        val webSocket = connection.webSocket ?: return false
        
        return try {
            val success = webSocket.send(message)
            if (success) {
                // Add sent message to history
                val wsMessage = WebSocketMessage(
                    content = message,
                    direction = MessageDirection.SENT,
                    timestamp = Instant.now(),
                    messageType = WebSocketMessageType.TEXT
                )
                connection.messages.add(wsMessage)
                
                // Notify message listeners
                notifyMessageListeners(connectionId, wsMessage)
            }
            success
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getConnectionStatus(connectionId: String): ConnectionStatus? {
        return connections[connectionId]?.status
    }
    
    override fun getConnection(connectionId: String): WebSocketConnection? {
        val mutableConnection = connections[connectionId] ?: return null
        return mutableConnection.toImmutable()
    }
    
    override fun getActiveConnections(): List<WebSocketConnection> {
        return connections.values
            .filter { it.status == ConnectionStatus.CONNECTED }
            .map { it.toImmutable() }
    }
    
    override fun addMessageListener(connectionId: String, listener: (WebSocketMessage) -> Unit) {
        messageListeners.computeIfAbsent(connectionId) { CopyOnWriteArrayList() }.add(listener)
    }
    
    override fun removeMessageListener(connectionId: String, listener: (WebSocketMessage) -> Unit) {
        messageListeners[connectionId]?.remove(listener)
    }
    
    override fun addStatusListener(connectionId: String, listener: (ConnectionStatus) -> Unit) {
        statusListeners.computeIfAbsent(connectionId) { CopyOnWriteArrayList() }.add(listener)
    }
    
    override fun removeStatusListener(connectionId: String, listener: (ConnectionStatus) -> Unit) {
        statusListeners[connectionId]?.remove(listener)
    }
    
    private fun createWebSocketListener(connectionId: String): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val connection = connections[connectionId] ?: return
                connection.status = ConnectionStatus.CONNECTED
                connection.connectedAt = Instant.now()
                
                // Notify status listeners
                notifyStatusListeners(connectionId, ConnectionStatus.CONNECTED)
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                val connection = connections[connectionId] ?: return
                
                val wsMessage = WebSocketMessage(
                    content = text,
                    direction = MessageDirection.RECEIVED,
                    timestamp = Instant.now(),
                    messageType = WebSocketMessageType.TEXT
                )
                
                connection.messages.add(wsMessage)
                
                // Notify message listeners
                notifyMessageListeners(connectionId, wsMessage)
            }
            
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val connection = connections[connectionId] ?: return
                
                val wsMessage = WebSocketMessage(
                    content = bytes.hex(),
                    direction = MessageDirection.RECEIVED,
                    timestamp = Instant.now(),
                    messageType = WebSocketMessageType.BINARY
                )
                
                connection.messages.add(wsMessage)
                
                // Notify message listeners
                notifyMessageListeners(connectionId, wsMessage)
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                val connection = connections[connectionId] ?: return
                connection.status = ConnectionStatus.DISCONNECTED
                connection.disconnectedAt = Instant.now()
                
                // Acknowledge the close
                webSocket.close(code, reason)
                
                // Notify status listeners
                notifyStatusListeners(connectionId, ConnectionStatus.DISCONNECTED)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val connection = connections[connectionId] ?: return
                connection.status = ConnectionStatus.DISCONNECTED
                connection.disconnectedAt = Instant.now()
                
                // Notify status listeners
                notifyStatusListeners(connectionId, ConnectionStatus.DISCONNECTED)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val connection = connections[connectionId] ?: return
                connection.status = ConnectionStatus.ERROR
                connection.errorMessage = t.message ?: "Unknown error"
                connection.disconnectedAt = Instant.now()
                
                // Notify status listeners
                notifyStatusListeners(connectionId, ConnectionStatus.ERROR)
            }
        }
    }
    
    private fun notifyMessageListeners(connectionId: String, message: WebSocketMessage) {
        messageListeners[connectionId]?.forEach { listener ->
            try {
                listener(message)
            } catch (e: Exception) {
                // Log error but don't let it affect other listeners
                e.printStackTrace()
            }
        }
    }
    
    private fun notifyStatusListeners(connectionId: String, status: ConnectionStatus) {
        statusListeners[connectionId]?.forEach { listener ->
            try {
                listener(status)
            } catch (e: Exception) {
                // Log error but don't let it affect other listeners
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Mutable version of WebSocketConnection for internal use
     */
    private data class MutableWebSocketConnection(
        val id: String,
        val url: String,
        var status: ConnectionStatus,
        val headers: Map<String, String>,
        val messages: MutableList<WebSocketMessage>,
        var connectedAt: Instant? = null,
        var disconnectedAt: Instant? = null,
        var errorMessage: String? = null,
        var webSocket: WebSocket? = null
    ) {
        fun toImmutable(): WebSocketConnection {
            return WebSocketConnection(
                id = id,
                url = url,
                status = status,
                headers = headers,
                messages = messages.toList(),
                connectedAt = connectedAt,
                disconnectedAt = disconnectedAt,
                errorMessage = errorMessage
            )
        }
    }
}