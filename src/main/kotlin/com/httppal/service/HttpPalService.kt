package com.httppal.service

import com.httppal.model.*
import com.intellij.openapi.project.Project

/**
 * Main application service for HttpPal plugin
 * Manages global state, settings, and coordinates other services
 */
interface HttpPalService {
    
    // === Global Headers Management ===
    
    /**
     * Get current global headers
     */
    fun getGlobalHeaders(): Map<String, String>
    
    /**
     * Set global headers that apply to all requests
     */
    fun setGlobalHeaders(headers: Map<String, String>)
    
    // === Environment Management ===
    
    /**
     * Get currently selected environment
     */
    fun getCurrentEnvironment(): Environment?
    
    /**
     * Set current environment
     */
    fun setCurrentEnvironment(environment: Environment?)
    
    /**
     * Get all available environments
     */
    fun getEnvironments(): List<Environment>
    
    /**
     * Add or update environment
     */
    fun saveEnvironment(environment: Environment)
    
    /**
     * Remove environment
     */
    fun removeEnvironment(environmentId: String)
    
    // === Request History Management ===
    
    /**
     * Get request history
     */
    fun getRequestHistory(): List<RequestHistoryEntry>
    
    /**
     * Add request to history
     */
    fun addToHistory(entry: RequestHistoryEntry)
    
    /**
     * Clear request history
     */
    fun clearHistory()
    
    // === Favorites Management ===
    
    /**
     * Get favorite requests
     */
    fun getFavorites(): List<FavoriteRequest>
    
    /**
     * Add request to favorites
     */
    fun addToFavorites(request: FavoriteRequest)
    
    /**
     * Remove request from favorites
     */
    fun removeFromFavorites(requestId: String)
    
    // === Service Coordination ===
    
    /**
     * Get endpoint discovery service for a project
     */
    fun getEndpointDiscoveryService(project: Project): EndpointDiscoveryService
    
    /**
     * Get request execution service
     */
    fun getRequestExecutionService(): RequestExecutionService
    
    /**
     * Get WebSocket service
     */
    fun getWebSocketService(): WebSocketService
    
    /**
     * Get environment service for a project
     */
    fun getEnvironmentService(project: Project): EnvironmentService
    
    /**
     * Get favorites service
     */
    fun getFavoritesService(): FavoritesService
    
    /**
     * Get history service
     */
    fun getHistoryService(): HistoryService
    
    // === Request Execution with Coordination ===
    
    /**
     * Execute HTTP request with full service coordination
     * Applies global headers, environment settings, and saves to history
     */
    suspend fun executeRequest(config: RequestConfig, project: Project? = null): HttpResponse
    
    // === Plugin Lifecycle Management ===
    
    /**
     * Initialize plugin services and state
     */
    fun initialize()
    
    /**
     * Cleanup plugin resources
     */
    fun dispose()
    
    /**
     * Check if plugin is properly initialized
     */
    fun isInitialized(): Boolean
    
    /**
     * Get plugin status information
     */
    fun getPluginStatus(): PluginStatus
    
    // === State Management ===
    
    /**
     * Save current plugin state
     */
    fun saveState()
    
    /**
     * Load plugin state
     */
    fun loadState()
    
    /**
     * Reset plugin to default state
     */
    fun resetToDefaults()
}

/**
 * Plugin status information
 */
/*
data class PluginStatus(
    val initialized: Boolean,
    val servicesReady: Boolean,
    val activeConnections: Int,
    val runningExecutions: Int,
    val lastError: String? = null
)*/
