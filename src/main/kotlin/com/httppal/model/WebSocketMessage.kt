package com.httppal.model

import java.time.Instant
import java.util.*

/**
 * Represents a WebSocket message
 */
data class WebSocketMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val direction: MessageDirection,
    val timestamp: Instant = Instant.now(),
    val messageType: WebSocketMessageType = WebSocketMessageType.TEXT,
    val size: Int = content.toByteArray().size
) {
    /**
     * Get formatted timestamp
     */
    fun getFormattedTimestamp(): String {
        return timestamp.toString().substring(11, 23) // HH:MM:SS.mmm
    }
    
    /**
     * Get display content (truncated if too long)
     */
    fun getDisplayContent(maxLength: Int = 100): String {
        return if (content.length <= maxLength) {
            content
        } else {
            "${content.take(maxLength)}..."
        }
    }
    
    /**
     * Get direction symbol
     */
    fun getDirectionSymbol(): String {
        return when (direction) {
            MessageDirection.SENT -> "→"
            MessageDirection.RECEIVED -> "←"
        }
    }
    
    /**
     * Get message summary for display
     */
    fun getSummary(): String {
        val dirSymbol = getDirectionSymbol()
        val time = getFormattedTimestamp()
        val preview = getDisplayContent(50)
        return "$dirSymbol $time: $preview"
    }
    
    /**
     * Check if message is JSON
     */
    fun isJson(): Boolean {
        val trimmed = content.trim()
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
               (trimmed.startsWith("[") && trimmed.endsWith("]"))
    }
    
    /**
     * Get formatted content based on type
     */
    fun getFormattedContent(): String {
        return when {
            messageType == WebSocketMessageType.BINARY -> "[Binary Data: $size bytes]"
            isJson() -> formatJson(content)
            else -> content
        }
    }
    
    /**
     * Validate the message
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (messageType == WebSocketMessageType.TEXT && content.isEmpty()) {
            errors.add("Text message content cannot be empty")
        }
        
        if (size < 0) {
            errors.add("Message size cannot be negative")
        }
        
        return errors
    }
    
    private fun formatJson(json: String): String {
        return try {
            // Basic JSON formatting - in a real implementation, you'd use a JSON library
            json.replace(",", ",\n").replace("{", "{\n").replace("}", "\n}")
        } catch (e: Exception) {
            json
        }
    }
}

/**
 * Direction of the message (sent or received)
 */
enum class MessageDirection {
    SENT, RECEIVED;
    
    companion object {
        fun fromString(direction: String): MessageDirection? {
            return try {
                valueOf(direction.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

/**
 * Type of WebSocket message
 */
enum class WebSocketMessageType {
    TEXT, BINARY;
    
    companion object {
        fun fromString(type: String): WebSocketMessageType? {
            return try {
                valueOf(type.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}