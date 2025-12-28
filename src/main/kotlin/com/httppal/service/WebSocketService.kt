package com.httppal.service

import com.httppal.model.*

/**
 * Service for managing WebSocket connections
 */
interface WebSocketService {
    
    /**
     * Create a new WebSocket connection
     */
    fun connect(url: String, headers: Map<String, String> = emptyMap()): String
    
    /**
     * Disconnect a WebSocket connection
     */
    fun disconnect(connectionId: String): Boolean
    
    /**
     * Send a message through WebSocket connection
     */
    fun sendMessage(connectionId: String, message: String): Boolean
    
    /**
     * Get connection status
     */
    fun getConnectionStatus(connectionId: String): ConnectionStatus?
    
    /**
     * Get connection details
     */
    fun getConnection(connectionId: String): WebSocketConnection?
    
    /**
     * Get all active connections
     */
    fun getActiveConnections(): List<WebSocketConnection>
    
    /**
     * Add message listener for a connection
     */
    fun addMessageListener(connectionId: String, listener: (WebSocketMessage) -> Unit)
    
    /**
     * Remove message listener
     */
    fun removeMessageListener(connectionId: String, listener: (WebSocketMessage) -> Unit)
    
    /**
     * Add connection status listener
     */
    fun addStatusListener(connectionId: String, listener: (ConnectionStatus) -> Unit)
    
    /**
     * Remove connection status listener
     */
    fun removeStatusListener(connectionId: String, listener: (ConnectionStatus) -> Unit)
}