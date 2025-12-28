package com.httppal.service.impl

import com.httppal.model.*
import com.httppal.service.*
import com.httppal.settings.HttpPalSettings
import com.httppal.util.ErrorHandler
import com.httppal.util.LoggingUtils
import com.httppal.util.RecoveryManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementation of HttpPalService
 * Coordinates all plugin services and manages global state
 */
@Service
class HttpPalServiceImpl : HttpPalService {
    
    private val logger = thisLogger()
    private val initialized = AtomicBoolean(false)
    private val serviceInstances = ConcurrentHashMap<String, Any>()
    private var lastError: String? = null
    
    private val settings: HttpPalSettings
        get() = HttpPalSettings.getInstance()
    
    // === Global Headers Management ===
    
    override fun getGlobalHeaders(): Map<String, String> {
        return settings.getGlobalHeaders()
    }
    
    override fun setGlobalHeaders(headers: Map<String, String>) {
        ErrorHandler.withErrorHandling("Set global headers") {
            LoggingUtils.logConfigurationChange("globalHeaders", getGlobalHeaders(), headers)
            settings.setGlobalHeaders(headers)
            ErrorHandler.showSuccess("Global headers updated successfully")
        }
    }
    
    // === Environment Management ===
    
    override fun getCurrentEnvironment(): Environment? {
        val environmentId = settings.getCurrentEnvironmentId() ?: return null
        return settings.getEnvironments().find { it.id == environmentId }
    }
    
    override fun setCurrentEnvironment(environment: Environment?) {
        ErrorHandler.withErrorHandling("Set current environment") {
            val oldEnvironment = getCurrentEnvironment()
            LoggingUtils.logConfigurationChange(
                "currentEnvironment", 
                oldEnvironment?.name, 
                environment?.name
            )
            settings.setCurrentEnvironmentId(environment?.id)
            ErrorHandler.showSuccess(
                "Environment switched to: ${environment?.name ?: "None"}"
            )
        }
    }
    
    override fun getEnvironments(): List<Environment> {
        return settings.getEnvironments()
    }
    
    override fun saveEnvironment(environment: Environment) {
        logger.info("Saving environment: ${environment.name}")
        settings.addEnvironment(environment)
    }
    
    override fun removeEnvironment(environmentId: String) {
        logger.info("Removing environment: $environmentId")
        settings.removeEnvironment(environmentId)
    }
    
    // === Request History Management ===
    
    override fun getRequestHistory(): List<RequestHistoryEntry> {
        return getHistoryService().getAllHistory()
    }
    
    override fun addToHistory(entry: RequestHistoryEntry) {
        getHistoryService().addToHistory(entry)
    }
    
    override fun clearHistory() {
        logger.info("Clearing request history")
        getHistoryService().clearHistory()
    }
    
    // === Favorites Management ===
    
    override fun getFavorites(): List<FavoriteRequest> {
        return getFavoritesService().getAllFavorites()
    }
    
    override fun addToFavorites(request: FavoriteRequest) {
        logger.info("Adding request to favorites: ${request.name}")
        getFavoritesService().addFavorite(request)
    }
    
    override fun removeFromFavorites(requestId: String) {
        logger.info("Removing request from favorites: $requestId")
        getFavoritesService().removeFavorite(requestId)
    }
    
    // === Service Coordination ===
    
    override fun getEndpointDiscoveryService(project: Project): EndpointDiscoveryService {
        return project.getService(EndpointDiscoveryService::class.java)
    }
    
    override fun getRequestExecutionService(): RequestExecutionService {
        return serviceInstances.computeIfAbsent("RequestExecutionService") {
            RequestExecutionServiceImpl()
        } as RequestExecutionService
    }
    
    override fun getWebSocketService(): WebSocketService {
        return serviceInstances.computeIfAbsent("WebSocketService") {
            WebSocketServiceImpl()
        } as WebSocketService
    }
    
    override fun getEnvironmentService(project: Project): EnvironmentService {
        return project.getService(EnvironmentService::class.java)
    }
    
    override fun getFavoritesService(): FavoritesService {
        return FavoritesServiceImpl.getInstance()
    }
    
    override fun getHistoryService(): HistoryService {
        return HistoryServiceImpl.getInstance()
    }
    
    // === Request Execution with Coordination ===
    
    override suspend fun executeRequest(config: RequestConfig, project: Project?): HttpResponse {
        return LoggingUtils.measureAndLog("Execute HTTP Request", mapOf("method" to config.method, "url" to config.url)) {
            try {
                // Apply global headers and environment settings
                val enhancedConfig = enhanceRequestConfig(config, project)
                
                LoggingUtils.logHttpRequest(
                    method = enhancedConfig.method.name,
                    url = enhancedConfig.url,
                    headers = enhancedConfig.headers,
                    bodySize = enhancedConfig.body?.length ?: 0
                )
                
                // Execute the request with recovery
                val response = RecoveryManager.executeWithRetry(
                    operationId = "http_request_${config.url}",
                    maxRetries = 2
                ) {
                    getRequestExecutionService().executeRequest(enhancedConfig)
                }
                
                LoggingUtils.logHttpResponse(
                    statusCode = response.statusCode,
                    responseTime = response.responseTime,
                    bodySize = response.body.length,
                    headers = response.headers
                )
                
                // Add to history
                val historyEntry = RequestHistoryEntry(
                    id = generateId(),
                    request = enhancedConfig,
                    response = response,
                    timestamp = Instant.now(),
                    executionTime = response.responseTime
                )
                addToHistory(historyEntry)
                
                lastError = null
                ErrorHandler.showSuccess("Request completed: ${response.statusCode}", project)
                response
                
            } catch (e: Exception) {
                lastError = e.message
                ErrorHandler.handleError(
                    message = "HTTP request failed",
                    cause = e,
                    project = project,
                    severity = ErrorHandler.ErrorSeverity.ERROR
                )
                throw e
            }
        }
    }
    
    // === Plugin Lifecycle Management ===
    
    override fun initialize() {
        if (initialized.compareAndSet(false, true)) {
            LoggingUtils.logServiceLifecycle("HttpPalService", "initialize")
            
            ErrorHandler.withErrorHandlingAndRecovery(
                operation = "Initialize HttpPal plugin",
                recoveryAction = {
                    // Recovery: Clear service instances and try again
                    serviceInstances.clear()
                    RecoveryManager.clearAllRetryAttempts()
                    loadState()
                }
            ) {
                // Initialize recovery manager with common strategies
                RecoveryManager.initializeCommonStrategies()
                
                // Load plugin state
                loadState()
                
                // Initialize service instances
                serviceInstances.clear()
                
                lastError = null
                LoggingUtils.logServiceLifecycle("HttpPalService", "initialized_successfully")
                ErrorHandler.showSuccess("HttpPal plugin initialized successfully")
            } ?: run {
                initialized.set(false)
                throw RuntimeException("Failed to initialize HttpPal plugin after recovery attempts")
            }
        }
    }
    
    override fun dispose() {
        if (initialized.compareAndSet(true, false)) {
            logger.info("Disposing HttpPal plugin services")
            
            try {
                // Save current state
                saveState()
                
                // Dispose service instances
                serviceInstances.values.forEach { service ->
                    if (service is WebSocketService) {
                        // Disconnect all WebSocket connections
                        service.getActiveConnections().forEach { connection ->
                            service.disconnect(connection.id)
                        }
                    }
                }
                serviceInstances.clear()
                
                logger.info("HttpPal plugin disposed successfully")
                
            } catch (e: Exception) {
                logger.error("Error during plugin disposal", e)
                lastError = e.message
            }
        }
    }
    
    override fun isInitialized(): Boolean {
        return initialized.get()
    }
    
    override fun getPluginStatus(): PluginStatus {
        val webSocketService = if (initialized.get()) {
            try {
                getWebSocketService()
            } catch (e: Exception) {
                null
            }
        } else null

        val requestExecutionService = if (initialized.get()) {
            try {
                getRequestExecutionService()
            } catch (e: Exception) {
                null
            }
        } else null

        return PluginStatus(
            initialized = initialized.get(),
            servicesReady = webSocketService != null && requestExecutionService != null,
            activeConnections = webSocketService?.getActiveConnections()?.size ?: 0,
            runningExecutions = 0, // TODO: Track running executions
            lastError = lastError
        )
    }
    
    // === State Management ===
    
    override fun saveState() {
        logger.debug("Saving plugin state")
        // State is automatically saved by persistent components
        // This method can be used for additional state management if needed
    }
    
    override fun loadState() {
        logger.debug("Loading plugin state")
        // State is automatically loaded by persistent components
        // This method can be used for additional state management if needed
    }
    
    override fun resetToDefaults() {
        logger.info("Resetting plugin to defaults")
        
        try {
            // Clear global headers
            setGlobalHeaders(emptyMap())
            
            // Clear current environment
            setCurrentEnvironment(null)
            
            // Clear history
            clearHistory()
            
            // Note: We don't clear favorites or environments as they might be valuable to keep
            
            logger.info("Plugin reset to defaults successfully")
            
        } catch (e: Exception) {
            logger.error("Error resetting plugin to defaults", e)
            lastError = e.message
            throw e
        }
    }
    
    // === Private Helper Methods ===
    
    private fun enhanceRequestConfig(config: RequestConfig, project: Project?): RequestConfig {
        val globalHeaders = getGlobalHeaders()
        val currentEnvironment = getCurrentEnvironment()
        
        // Build effective URL
        val effectiveUrl = if (currentEnvironment != null && !config.url.startsWith("http")) {
            "${currentEnvironment.baseUrl.trimEnd('/')}/${config.url.trimStart('/')}"
        } else {
            config.url
        }
        
        // Merge headers: global headers + environment headers + request headers
        val effectiveHeaders = mutableMapOf<String, String>()
        effectiveHeaders.putAll(globalHeaders)
        currentEnvironment?.globalHeaders?.let { effectiveHeaders.putAll(it) }
        effectiveHeaders.putAll(config.headers) // Request headers override global/environment headers
        
        return config.copy(
            url = effectiveUrl,
            headers = effectiveHeaders
        )
    }
    
    private fun generateId(): String {
        return "req_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
    }
    
    companion object {
        fun getInstance(): HttpPalService {
            return ApplicationManager.getApplication().getService(HttpPalService::class.java)
        }
    }
    
    init {
        // Auto-initialize when service is created
        runBlocking {
            try {
                initialize()
            } catch (e: Exception) {
                logger.error("Failed to auto-initialize HttpPalService", e)
            }
        }
    }
}