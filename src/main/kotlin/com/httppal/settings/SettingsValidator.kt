package com.httppal.settings

/**
 * Utility class for validating HttpPal settings
 */
object SettingsValidator {
    
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList()
    )
    
    /**
     * Validate all settings values
     */
    fun validateSettings(
        maxHistorySize: Int?,
        defaultTimeoutSeconds: Long?,
        defaultThreadCount: Int?,
        maxConcurrentRequests: Int?,
        connectionPoolSize: Int?,
        requestRetryCount: Int?
    ): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate max history size
        maxHistorySize?.let { size ->
            if (size <= 0 || size > 10000) {
                errors.add("Max history size must be between 1 and 10,000")
            }
        }
        
        // Validate timeout
        defaultTimeoutSeconds?.let { timeout ->
            if (timeout <= 0 || timeout > 300) {
                errors.add("Default timeout must be between 1 and 300 seconds")
            }
        }
        
        // Validate thread count
        defaultThreadCount?.let { count ->
            if (count <= 0 || count > 100) {
                errors.add("Default thread count must be between 1 and 100")
            }
        }
        
        // Validate max concurrent requests
        maxConcurrentRequests?.let { count ->
            if (count <= 0 || count > 100) {
                errors.add("Max concurrent requests must be between 1 and 100")
            }
        }
        
        // Validate connection pool size
        connectionPoolSize?.let { size ->
            if (size <= 0 || size > 50) {
                errors.add("Connection pool size must be between 1 and 50")
            }
        }
        
        // Validate retry count
        requestRetryCount?.let { count ->
            if (count < 0 || count > 10) {
                errors.add("Request retry count must be between 0 and 10")
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    /**
     * Validate sensitive header names
     */
    fun validateSensitiveHeaders(headerNames: List<String>): ValidationResult {
        val errors = mutableListOf<String>()
        
        headerNames.forEach { name ->
            if (name.isBlank()) {
                errors.add("Header names cannot be empty")
            } else if (name.contains(":") || name.contains("\n") || name.contains("\r")) {
                errors.add("Header name '$name' contains invalid characters")
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    /**
     * Validate import data structure
     */
    fun validateImportData(data: Map<String, Any>): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Check for required fields
        val requiredFields = listOf("maxHistorySize", "defaultTimeout", "defaultThreadCount")
        requiredFields.forEach { field ->
            if (!data.containsKey(field)) {
                errors.add("Missing required field: $field")
            }
        }
        
        // Validate data types
        data["maxHistorySize"]?.let { value ->
            if (value.toString().toIntOrNull() == null) {
                errors.add("maxHistorySize must be a valid integer")
            }
        }
        
        data["defaultTimeout"]?.let { value ->
            if (value.toString().toLongOrNull() == null) {
                errors.add("defaultTimeout must be a valid number")
            }
        }
        
        data["defaultThreadCount"]?.let { value ->
            if (value.toString().toIntOrNull() == null) {
                errors.add("defaultThreadCount must be a valid integer")
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    /**
     * Get recommended settings based on system capabilities
     */
    fun getRecommendedSettings(): Map<String, Any> {
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        val maxMemory = Runtime.getRuntime().maxMemory()
        
        return mapOf(
            "defaultThreadCount" to minOf(availableProcessors * 2, 20),
            "maxConcurrentRequests" to minOf(availableProcessors * 4, 50),
            "connectionPoolSize" to minOf(availableProcessors * 2, 20),
            "maxHistorySize" to if (maxMemory > 1024 * 1024 * 1024) 2000 else 1000, // 2000 if >1GB RAM
            "requestRetryCount" to 3,
            "defaultTimeout" to 30
        )
    }
}