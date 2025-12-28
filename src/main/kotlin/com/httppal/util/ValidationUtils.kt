package com.httppal.util

import com.httppal.model.ValidationResult

/**
 * Utility functions for validation
 */
object ValidationUtils {
    
    /**
     * Validate that a string is not blank
     */
    fun validateNotBlank(value: String?, fieldName: String): ValidationResult {
        return if (value.isNullOrBlank()) {
            ValidationResult.invalid(HttpPalBundle.message("validation.field.required", fieldName))
        } else {
            ValidationResult.valid()
        }
    }
    
    /**
     * Validate numeric range
     */
    fun validateRange(value: Int, fieldName: String, min: Int, max: Int): ValidationResult {
        return when {
            value < min -> ValidationResult.invalid("$fieldName must be at least $min")
            value > max -> ValidationResult.invalid("$fieldName cannot exceed $max")
            else -> ValidationResult.valid()
        }
    }
    
    /**
     * Validate header name format
     */
    fun validateHeaderName(name: String): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (name.isBlank()) {
            errors.add("Header name cannot be empty")
        }
        
        if (name.contains(":") || name.contains("\n") || name.contains("\r")) {
            errors.add("Header name '$name' contains invalid characters")
        }
        
        // HTTP header names should be ASCII
        if (!name.matches(Regex("^[!#$%&'*+\\-.0-9A-Z^_`a-z|~]+$"))) {
            errors.add("Header name '$name' contains invalid characters")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.valid()
        } else {
            ValidationResult.invalid(errors)
        }
    }
}