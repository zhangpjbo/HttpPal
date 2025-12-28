package com.httppal.service.impl

import com.httppal.model.Environment
import com.httppal.model.ValidationResult
import com.httppal.service.EnvironmentService
import com.httppal.service.EnvironmentVariableResolver
import com.httppal.service.ResolveResult
import com.httppal.service.VariableReference
import com.httppal.util.LoggingUtils
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * Implementation of EnvironmentVariableResolver for detecting and resolving environment variables
 */
@Service(Service.Level.PROJECT)
class EnvironmentVariableResolverImpl(private val project: Project) : EnvironmentVariableResolver {
    
    private val environmentService: EnvironmentService by lazy { project.getService(EnvironmentService::class.java) }
    
    // Regex patterns for variable detection
    private val doubleBracePattern = Regex("""\{\{([a-zA-Z_][a-zA-Z0-9_]*)\}\}""")
    private val dollarBracePattern = Regex("""\{\{([a-zA-Z_][a-zA-Z0-9_]*)\}\}""")
    
    override fun detectVariables(text: String): List<VariableReference> {
        val variables = mutableListOf<VariableReference>()
        
        // Find {{variable}} format
        doubleBracePattern.findAll(text).forEach { match ->
            variables.add(VariableReference(
                name = match.groupValues[1],
                startIndex = match.range.first,
                endIndex = match.range.last,
                fullMatch = match.value
            ))
        }
        
        // Find ${variable} format
        dollarBracePattern.findAll(text).forEach { match ->
            variables.add(VariableReference(
                name = match.groupValues[1],
                startIndex = match.range.first,
                endIndex = match.range.last,
                fullMatch = match.value
            ))
        }
        
        // Sort by start index to maintain order in text
        return variables.sortedBy { it.startIndex }
    }
    
    override fun resolveVariables(
        text: String,
        environment: Environment?
    ): ResolveResult {
        val env = environment ?: environmentService.getCurrentEnvironment()
        
        if (env == null) {
            LoggingUtils.logDebug("No environment available for variable resolution")
            return ResolveResult.success(text)
        }
        
        var result = text
        val unresolvedVariables = mutableListOf<String>()
        
        // Resolve {{variable}} format
        result = doubleBracePattern.replace(result) { matchResult ->
            val varName = matchResult.groupValues[1]
            val value = env.variables[varName]
            if (value != null) {
                value
            } else {
                unresolvedVariables.add(varName)
                matchResult.value
            }
        }
        
        // Resolve ${variable} format
        result = dollarBracePattern.replace(result) { matchResult ->
            val varName = matchResult.groupValues[1]
            val value = env.variables[varName]
            if (value != null) {
                value
            } else {
                unresolvedVariables.add(varName)
                matchResult.value
            }
        }
        
        return if (unresolvedVariables.isNotEmpty()) {
            ResolveResult.withWarnings(result, unresolvedVariables)
        } else {
            ResolveResult.success(result)
        }
    }
    
    override fun getVariableValue(
        variableName: String,
        environment: Environment?
    ): String? {
        val env = environment ?: environmentService.getCurrentEnvironment()
        return env?.variables?.get(variableName)
    }
    
    override fun validateVariables(
        text: String,
        environment: Environment?
    ): ValidationResult {
        val env = environment ?: environmentService.getCurrentEnvironment()
        
        if (env == null) {
            val detectedVars = detectVariables(text).map { it.name }
            return if (detectedVars.isNotEmpty()) {
                ValidationResult.invalid(listOf("No active environment to resolve variables: ${detectedVars.joinToString(", ")}"))
            } else {
                ValidationResult.valid()
            }
        }
        
        val detectedVars = detectVariables(text).map { it.name }.toSet()
        val missingVars = detectedVars.filter { !env.variables.containsKey(it) }
        
        return if (missingVars.isNotEmpty()) {
            ValidationResult.invalid(missingVars.map { "Variable '$it' is not defined in environment '${env.name}'" })
        } else {
            ValidationResult.valid()
        }
    }
    
    fun hasUnresolvedVariables(text: String): Boolean {
        return doubleBracePattern.containsMatchIn(text) || dollarBracePattern.containsMatchIn(text)
    }
    
    fun previewResolution(text: String, environment: Environment?): Map<String, String> {
        val env = environment ?: environmentService.getCurrentEnvironment()
        
        if (env == null) {
            return emptyMap()
        }
        
        val detectedVars = detectVariables(text).map { it.name }.toSet()
        return detectedVars.associateWith { varName ->
            env.variables[varName] ?: "[UNRESOLVED]"
        }
    }
}