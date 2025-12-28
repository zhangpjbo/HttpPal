package com.httppal.model

import java.time.Instant
import java.util.*

/**
 * Represents a request template for quick request creation
 */
data class RequestTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val method: HttpMethod = HttpMethod.GET,
    val urlTemplate: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val isBuiltIn: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val lastUsed: Instant? = null,
    val useCount: Int = 0
) {
    /**
     * Convert template to RequestConfig
     */
    fun toRequestConfig(): RequestConfig {
        return RequestConfig(
            method = method,
            url = urlTemplate ?: "",
            headers = headers,
            body = body
        )
    }
    
    /**
     * Validate the template
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (name.isBlank()) {
            errors.add("Template name cannot be empty")
        }
        
        if (name.length > 100) {
            errors.add("Template name cannot exceed 100 characters")
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
        
        if (useCount < 0) {
            errors.add("Use count cannot be negative")
        }
        
        return errors
    }
    
    /**
     * Mark as used (increment use count and update last used time)
     */
    fun markAsUsed(): RequestTemplate {
        return copy(
            lastUsed = Instant.now(),
            useCount = useCount + 1
        )
    }
    
    /**
     * Get display name with type indicator
     */
    fun getDisplayName(): String {
        val typeIndicator = if (isBuiltIn) " [Built-in]" else ""
        return "$name$typeIndicator"
    }
}
