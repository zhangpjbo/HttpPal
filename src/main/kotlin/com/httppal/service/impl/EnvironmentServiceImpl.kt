package com.httppal.service.impl

import com.httppal.model.Environment
import com.httppal.service.EnvironmentService
import com.httppal.service.HttpPalService
import com.httppal.settings.ProjectSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Implementation of EnvironmentService for project-level environment management
 */
@Service(Service.Level.PROJECT)
class EnvironmentServiceImpl(private val project: Project) : EnvironmentService {
    
    private val projectSettings: ProjectSettings by lazy(NONE) {
        ProjectSettings.getInstance(project)
    }
    
    private val httpPalService: HttpPalService
        get() = HttpPalServiceImpl.getInstance()
    
    private val changeListeners = mutableListOf<(List<Environment>) -> Unit>()
    
    override fun getAllEnvironments(): List<Environment> {
        return projectSettings.getEnvironments()
    }
    
    override fun getEnvironmentById(id: String): Environment? {
        return projectSettings.getEnvironmentById(id)
    }
    
    override fun getEnvironmentByName(name: String): Environment? {
        return projectSettings.getEnvironmentByName(name)
    }
    
    override fun getCurrentEnvironment(): Environment? {
        val currentId = projectSettings.getCurrentEnvironmentId()
        return if (currentId != null) {
            getEnvironmentById(currentId)
        } else {
            null
        }
    }
    
    override fun createEnvironment(environment: Environment): Environment {
        val validationErrors = validateEnvironment(environment)
        if (validationErrors.isNotEmpty()) {
            throw IllegalArgumentException("Environment validation failed: ${validationErrors.joinToString(", ")}")
        }
        
        if (isEnvironmentNameInUse(environment.name)) {
            throw IllegalArgumentException("Environment name '${environment.name}' is already in use")
        }
        
        val newEnvironment = environment.copy(isActive = false)
        projectSettings.addEnvironment(newEnvironment)
        notifyEnvironmentChange()
        
        return newEnvironment
    }
    
    override fun updateEnvironment(environment: Environment): Environment {
        val existingEnvironment = getEnvironmentById(environment.id)
            ?: throw IllegalArgumentException("Environment with ID '${environment.id}' not found")
        
        val validationErrors = validateEnvironment(environment)
        if (validationErrors.isNotEmpty()) {
            throw IllegalArgumentException("Environment validation failed: ${validationErrors.joinToString(", ")}")
        }
        
        if (isEnvironmentNameInUse(environment.name, environment.id)) {
            throw IllegalArgumentException("Environment name '${environment.name}' is already in use")
        }
        
        // Preserve active status from existing environment
        val updatedEnvironment = environment.copy(isActive = existingEnvironment.isActive)
        projectSettings.updateEnvironment(updatedEnvironment)
        notifyEnvironmentChange()
        
        return updatedEnvironment
    }
    
    override fun deleteEnvironment(environmentId: String): Boolean {
        val environment = getEnvironmentById(environmentId) ?: return false
        
        // If this is the current environment, deactivate it
        if (environment.isActive) {
            switchToEnvironment(null)
        }
        
        projectSettings.removeEnvironment(environmentId)
        notifyEnvironmentChange()
        
        return true
    }
    
    override fun switchToEnvironment(environmentId: String?): Environment? {
        // Deactivate all environments first
        val allEnvironments = getAllEnvironments()
        allEnvironments.forEach { env ->
            if (env.isActive) {
                projectSettings.updateEnvironment(env.withActiveStatus(false))
            }
        }
        
        val targetEnvironment = if (environmentId != null) {
            val env = getEnvironmentById(environmentId)
            if (env != null) {
                val activeEnv = env.withActiveStatus(true)
                projectSettings.updateEnvironment(activeEnv)
                projectSettings.setCurrentEnvironmentId(environmentId)
                activeEnv
            } else {
                projectSettings.setCurrentEnvironmentId(null)
                null
            }
        } else {
            projectSettings.setCurrentEnvironmentId(null)
            null
        }
        
        notifyEnvironmentChange()
        return targetEnvironment
    }
    
    override fun validateEnvironment(environment: Environment): List<String> {
        return environment.validate()
    }
    
    override fun buildUrlWithEnvironment(path: String, environment: Environment?): String {
        val env = environment ?: getCurrentEnvironment()
        return if (env != null) {
            env.buildUrl(path)
        } else {
            // If no environment, return path as-is (assuming it's a full URL)
            path
        }
    }
    
    override fun getEffectiveGlobalHeaders(environment: Environment?): Map<String, String> {
        val env = environment ?: getCurrentEnvironment()
        val applicationHeaders = httpPalService.getGlobalHeaders()
        
        return if (env != null) {
            // Environment headers override application headers
            val effectiveHeaders = applicationHeaders.toMutableMap()
            effectiveHeaders.putAll(env.globalHeaders)
            effectiveHeaders.toMap()
        } else {
            applicationHeaders
        }
    }
    
    override fun isEnvironmentNameInUse(name: String, excludeId: String?): Boolean {
        return getAllEnvironments().any { env ->
            env.name.equals(name, ignoreCase = true) && env.id != excludeId
        }
    }
    
    override fun getEnvironmentStatistics(): Map<String, Any> {
        val environments = getAllEnvironments()
        val currentEnv = getCurrentEnvironment()
        
        return mapOf(
            "totalEnvironments" to environments.size,
            "activeEnvironmentId" to (currentEnv?.id ?: "none"),
            "activeEnvironmentName" to (currentEnv?.name ?: "none"),
            "environmentsWithVariables" to environments.count { it.variables.isNotEmpty() },
            "environmentsWithGlobalHeaders" to environments.count { it.globalHeaders.isNotEmpty() },
            "averageHeadersPerEnvironment" to if (environments.isNotEmpty()) {
                environments.sumOf { it.globalHeaders.size } / environments.size
            } else 0,
            "averageVariablesPerEnvironment" to if (environments.isNotEmpty()) {
                environments.sumOf { it.variables.size } / environments.size
            } else 0
        )
    }
    
    override fun addEnvironmentChangeListener(listener: (List<Environment>) -> Unit) {
        changeListeners.add(listener)
    }
    
    override fun removeEnvironmentChangeListener(listener: (List<Environment>) -> Unit) {
        changeListeners.remove(listener)
    }
    
    private fun notifyEnvironmentChange() {
        val environments = getAllEnvironments()
        changeListeners.forEach { listener ->
            try {
                listener(environments)
            } catch (e: Exception) {
                // Log error but don't let one listener failure affect others
                // In a real implementation, we'd use proper logging
                System.err.println("Error notifying environment change listener: ${e.message}")
            }
        }
    }
    
    companion object {
        fun getInstance(project: Project): EnvironmentService {
            return project.getService(EnvironmentServiceImpl::class.java)
        }
    }
}