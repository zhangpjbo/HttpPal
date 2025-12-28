package com.httppal.model

import java.time.Duration

/**
 * Plugin settings data model
 */
data class PluginSettings(
    val globalHeaders: Map<String, String> = emptyMap(),
    val currentEnvironmentId: String? = null,
    val environments: List<Environment> = emptyList(),
    val maxHistorySize: Int = 1000,
    val defaultTimeout: Duration = Duration.ofSeconds(30),
    val defaultThreadCount: Int = 10,
    val autoSaveRequests: Boolean = true,
    val enableEndpointDiscovery: Boolean = true,
    val autoRefreshEndpoints: Boolean = true
) {
    /**
     * Validate plugin settings
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (maxHistorySize < 0) {
            errors.add("Max history size cannot be negative")
        }
        
        if (maxHistorySize > 10000) {
            errors.add("Max history size cannot exceed 10,000 entries")
        }
        
        if (defaultTimeout.isNegative || defaultTimeout.isZero) {
            errors.add("Default timeout must be positive")
        }
        
        if (defaultTimeout.toSeconds() > 300) {
            errors.add("Default timeout cannot exceed 5 minutes")
        }
        
        if (defaultThreadCount < 1) {
            errors.add("Default thread count must be at least 1")
        }
        
        if (defaultThreadCount > 100) {
            errors.add("Default thread count cannot exceed 100")
        }
        
        // Validate global headers
        globalHeaders.forEach { (name, value) ->
            if (name.isBlank()) {
                errors.add("Global header name cannot be empty")
            }
            if (name.contains(":") || name.contains("\n") || name.contains("\r")) {
                errors.add("Global header name '$name' contains invalid characters")
            }
        }
        
        return errors
    }
    
    /**
     * Get current environment by ID
     */
    fun getCurrentEnvironment(): Environment? {
        return currentEnvironmentId?.let { id ->
            environments.find { it.id == id }
        }
    }
}