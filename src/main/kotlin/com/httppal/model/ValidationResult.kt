package com.httppal.model

/**
 * Result of validation operations
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    companion object {
        fun valid(): ValidationResult = ValidationResult(true)
        
        fun invalid(errors: List<String>): ValidationResult = 
            ValidationResult(false, errors)
        
        fun invalid(error: String): ValidationResult = 
            ValidationResult(false, listOf(error))
        
        fun withWarnings(warnings: List<String>): ValidationResult = 
            ValidationResult(true, emptyList(), warnings)
    }
    
    /**
     * Combine with another validation result
     */
    fun combine(other: ValidationResult): ValidationResult {
        return ValidationResult(
            isValid = this.isValid && other.isValid,
            errors = this.errors + other.errors,
            warnings = this.warnings + other.warnings
        )
    }
    
    /**
     * Get all issues (errors + warnings)
     */
    fun getAllIssues(): List<String> {
        return errors + warnings
    }
    
    /**
     * Check if there are any issues
     */
    fun hasIssues(): Boolean {
        return errors.isNotEmpty() || warnings.isNotEmpty()
    }
}