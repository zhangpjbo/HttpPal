package com.httppal.settings

import com.httppal.model.EndpointInfo
import com.httppal.model.Environment
import com.httppal.util.MapUtils.safeMapOf
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

/**
 * Project-level settings for HttpPal plugin
 */
@Service(Service.Level.PROJECT)
@State(
    name = "HttpPalProjectSettings",
    storages = [Storage("httppal-project.xml")]
)
class ProjectSettings : PersistentStateComponent<ProjectSettings.State> {
    
    data class State(
        var manualEndpoints: MutableList<EndpointInfo> = mutableListOf(),
        var discoveryEnabled: Boolean = true,
        var autoRefreshEndpoints: Boolean = true,
        var excludedPackages: MutableList<String> = mutableListOf(),
        var includedPackages: MutableList<String> = mutableListOf(),
        var environments: MutableList<Environment> = mutableListOf(),
        var currentEnvironmentId: String? = null
    )
    
    private var myState = State()
    
    override fun getState(): State = myState
    
    override fun loadState(state: State) {
        myState = state
    }
    
    // Manual Endpoints
    fun getManualEndpoints(): List<EndpointInfo> = myState.manualEndpoints.toList()
    
    fun addManualEndpoint(endpoint: EndpointInfo) {
        myState.manualEndpoints.removeIf { it.id == endpoint.id }
        myState.manualEndpoints.add(endpoint)
    }
    
    fun removeManualEndpoint(endpointId: String) {
        myState.manualEndpoints.removeIf { it.id == endpointId }
    }
    
    fun updateManualEndpoint(endpoint: EndpointInfo) {
        val index = myState.manualEndpoints.indexOfFirst { it.id == endpoint.id }
        if (index >= 0) {
            myState.manualEndpoints[index] = endpoint
        }
    }
    
    // Discovery Settings
    fun isDiscoveryEnabled(): Boolean = myState.discoveryEnabled
    
    fun setDiscoveryEnabled(enabled: Boolean) {
        myState.discoveryEnabled = enabled
    }
    
    fun isAutoRefreshEnabled(): Boolean = myState.autoRefreshEndpoints
    
    fun setAutoRefreshEnabled(enabled: Boolean) {
        myState.autoRefreshEndpoints = enabled
    }
    
    // Package Filtering
    fun getExcludedPackages(): List<String> = myState.excludedPackages.toList()
    
    fun addExcludedPackage(packageName: String) {
        if (!myState.excludedPackages.contains(packageName)) {
            myState.excludedPackages.add(packageName)
        }
    }
    
    fun removeExcludedPackage(packageName: String) {
        myState.excludedPackages.remove(packageName)
    }
    
    fun getIncludedPackages(): List<String> = myState.includedPackages.toList()
    
    fun addIncludedPackage(packageName: String) {
        if (!myState.includedPackages.contains(packageName)) {
            myState.includedPackages.add(packageName)
        }
    }
    
    fun removeIncludedPackage(packageName: String) {
        myState.includedPackages.remove(packageName)
    }
    
    // Package filtering utilities
    fun isPackageExcluded(packageName: String): Boolean {
        return myState.excludedPackages.any { excluded ->
            packageName.startsWith(excluded)
        }
    }
    
    fun isPackageIncluded(packageName: String): Boolean {
        if (myState.includedPackages.isEmpty()) {
            return true // If no includes specified, all packages are included
        }
        return myState.includedPackages.any { included ->
            packageName.startsWith(included)
        }
    }
    
    fun shouldProcessPackage(packageName: String): Boolean {
        return isPackageIncluded(packageName) && !isPackageExcluded(packageName)
    }
    
    // Environment Management
    fun getEnvironments(): List<Environment> = myState.environments.toList()
    
    fun addEnvironment(environment: Environment) {
        myState.environments.removeIf { it.id == environment.id }
        myState.environments.add(environment)
    }
    
    fun removeEnvironment(environmentId: String) {
        myState.environments.removeIf { it.id == environmentId }
        if (myState.currentEnvironmentId == environmentId) {
            myState.currentEnvironmentId = null
        }
    }
    
    fun updateEnvironment(environment: Environment) {
        val index = myState.environments.indexOfFirst { it.id == environment.id }
        if (index >= 0) {
            myState.environments[index] = environment
        }
    }
    
    fun getCurrentEnvironmentId(): String? = myState.currentEnvironmentId
    
    fun setCurrentEnvironmentId(environmentId: String?) {
        myState.currentEnvironmentId = environmentId
    }
    
    fun getEnvironmentById(id: String): Environment? {
        return myState.environments.find { it.id == id }
    }
    
    fun getEnvironmentByName(name: String): Environment? {
        return myState.environments.find { it.name == name }
    }
    
    /**
     * Get statistics about project settings
     */
    fun getStatistics(): Map<String, Any> {
        return safeMapOf(
            "manualEndpointsCount" to myState.manualEndpoints.size,
            "excludedPackagesCount" to myState.excludedPackages.size,
            "includedPackagesCount" to myState.includedPackages.size,
            "environmentsCount" to myState.environments.size,
            "currentEnvironmentId" to myState.currentEnvironmentId,
            "discoveryEnabled" to myState.discoveryEnabled,
            "autoRefreshEnabled" to myState.autoRefreshEndpoints
        )
    }
    
    companion object {
        fun getInstance(project: Project): ProjectSettings {
            return project.getService(ProjectSettings::class.java)
        }
    }
}