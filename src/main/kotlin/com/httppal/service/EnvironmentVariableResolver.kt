package com.httppal.service

import com.httppal.model.Environment
import com.httppal.model.ValidationResult

/**
 * Service for resolving environment variables in requests
 */
interface EnvironmentVariableResolver {
    
    /**
     * Detect environment variables in text
     */
    fun detectVariables(text: String): List<VariableReference>
    
    /**
     * Resolve environment variables in text
     */
    fun resolveVariables(
        text: String,
        environment: Environment?
    ): ResolveResult
    
    /**
     * Get variable value from environment
     */
    fun getVariableValue(
        variableName: String,
        environment: Environment?
    ): String?
    
    /**
     * Validate all variables exist in environment
     */
    fun validateVariables(
        text: String,
        environment: Environment?
    ): ValidationResult
}

/**
 * Reference to an environment variable in text
 */
data class VariableReference(
    val name: String,
    val startIndex: Int,
    val endIndex: Int,
    val fullMatch: String // e.g., "{{variableName}}"
)

/**
 * Result of variable resolution
 */
data class ResolveResult(
    val resolvedText: String,
    val unresolvedVariables: List<String>,
    val hasWarnings: Boolean
) {
    companion object {
        fun success(resolvedText: String): ResolveResult {
            return ResolveResult(resolvedText, emptyList(), false)
        }
        
        fun withWarnings(
            resolvedText: String,
            unresolvedVariables: List<String>
        ): ResolveResult {
            return ResolveResult(resolvedText, unresolvedVariables, true)
        }
    }
}
