package com.httppal.model

import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Represents a WebSocket connection
 */
data class WebSocketConnection(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val status: ConnectionStatus,
    val headers: Map<String, String>,
    val messages: List<WebSocketMessage> = emptyList(),
    val connectedAt: Instant? = null,
    val disconnectedAt: Instant? = null,
    val errorMessage: String? = null,
    val name: String? = null
) {
    /**
     * Get display name for the connection
     */
    fun getDisplayName(): String {
        return name ?: url
    }
    
    /**
     * Get connection duration
     */
    fun getConnectionDuration(): Duration? {
        return if (connectedAt != null) {
            val endTime = disconnectedAt ?: Instant.now()
            Duration.between(connectedAt, endTime)
        } else {
            null
        }
    }
    
    /**
     * Get message count by direction
     */
    fun getMessageCounts(): Pair<Int, Int> {
        val sent = messages.count { it.direction == MessageDirection.SENT }
        val received = messages.count { it.direction == MessageDirection.RECEIVED }
        return Pair(sent, received)
    }
    
    /**
     * Get recent messages (last N messages)
     */
    fun getRecentMessages(count: Int = 50): List<WebSocketMessage> {
        return messages.takeLast(count)
    }
    
    /**
     * Check if connection is active
     */
    fun isActive(): Boolean {
        return status == ConnectionStatus.CONNECTED
    }
    
    /**
     * Get status description
     */
    fun getStatusDescription(): String {
        return when (status) {
            ConnectionStatus.DISCONNECTED -> "Disconnected"
            ConnectionStatus.CONNECTING -> "Connecting..."
            ConnectionStatus.CONNECTED -> "Connected"
            ConnectionStatus.ERROR -> "Error: ${errorMessage ?: "Unknown error"}"
        }
    }
    
    /**
     * Validate the connection configuration
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (url.isBlank()) {
            errors.add("WebSocket URL cannot be empty")
        }
        
        if (!isValidWebSocketUrl(url)) {
            errors.add("WebSocket URL format is invalid")
        }
        
        // Validate headers
        headers.forEach { (headerName, _) ->
            if (headerName.isBlank()) {
                errors.add("Header name cannot be empty")
            }
            if (headerName.contains(":") || headerName.contains("\n") || headerName.contains("\r")) {
                errors.add("Header name '$headerName' contains invalid characters")
            }
        }
        
        return errors
    }
    
    private fun isValidWebSocketUrl(url: String): Boolean {
        return try {
            val urlObj = java.net.URL(url)
            urlObj.protocol in listOf("ws", "wss")
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Status of a WebSocket connection
 */
enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR;
    
    companion object {
        fun fromString(status: String): ConnectionStatus? {
            return try {
                valueOf(status.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}