package com.httppal.startup

import com.httppal.service.EndpointDiscoveryService
import com.httppal.service.EnvironmentService
import com.httppal.service.HttpPalService
import com.httppal.service.listener.EnhancedFileChangeListener
import com.httppal.settings.HttpPalSettings
import com.httppal.util.LoggingUtils
import com.httppal.util.VersionCompatibility
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enhanced startup activity for HttpPal plugin initialization.
 * 
 * This activity performs comprehensive initialization when the IDE starts:
 * - Validates version compatibility
 * - Initializes core services
 * - Loads persisted settings and environments
 * - Registers file change listeners for auto-refresh
 * - Triggers automatic endpoint discovery in background
 * 
 * All initialization is performed in background threads to avoid blocking
 * the IDE startup process.
 * 
 * Requirements: 1.1, 1.2, 8.1, 8.2, 8.3
 */
class HttpPalStartupActivity : ProjectActivity {
    
    private var fileChangeListener: EnhancedFileChangeListener? = null
    
    override suspend fun execute(project: Project) {
        try {
            LoggingUtils.logWithContext(
                LoggingUtils.LogLevel.INFO,
                "Starting HttpPal plugin initialization",
                mapOf<String, Any>("project" to project.name)
            )
            
            // Check version compatibility first
            if (!VersionCompatibility.checkAllCompatibility()) {
                val errorMessage = "HttpPal plugin compatibility check failed"
                LoggingUtils.logWithContext(
                    LoggingUtils.LogLevel.ERROR,
                    errorMessage,
                    mapOf<String, Any>("project" to project.name)
                )
                showErrorNotification(project, errorMessage)
                return
            }
            
            // Initialize services in background
            initializeServices(project)
            
            // Load persisted settings
            loadPersistedSettings(project)
            
            // Register file change listener for auto-refresh
            registerFileChangeListener(project)
            
            // Trigger automatic endpoint discovery in background
            autoLoadEndpoints(project)
            
            // Notify UI components that initialization is complete
            notifyInitializationComplete(project)
            
            LoggingUtils.logWithContext(
                LoggingUtils.LogLevel.INFO,
                "HttpPal plugin initialized successfully",
                mapOf<String, Any>(
                    "project" to project.name,
                    "version" to "1.0.0"
                )
            )
            
        } catch (e: Exception) {
            handleInitializationError(project, e)
        }
    }
    
    /**
     * Initialize core plugin services.
     * Validates that all required services are available and ready.
     * 
     * Requirement: 8.1
     */
    private suspend fun initializeServices(project: Project) {
        withContext(Dispatchers.IO) {
            try {
                // Initialize HttpPal service
                val httpPalService = service<HttpPalService>()
                
                // Check plugin status
                val status = httpPalService.getPluginStatus()
                if (!status.initialized || !status.servicesReady) {
                    LoggingUtils.logWithContext(
                        LoggingUtils.LogLevel.WARN,
                        "HttpPal plugin initialization incomplete",
                        mapOf<String, Any>(
                            "initialized" to status.initialized,
                            "servicesReady" to status.servicesReady
                        )
                    )
                }
                
                // Ensure endpoint discovery service is available
                project.service<EndpointDiscoveryService>()
                
                // Ensure environment service is available
                project.service<EnvironmentService>()
                
                LoggingUtils.logWithContext(
                    LoggingUtils.LogLevel.DEBUG,
                    "Core services initialized",
                    mapOf<String, Any>("project" to project.name)
                )
                
            } catch (e: Exception) {
                LoggingUtils.logWithContext(
                    LoggingUtils.LogLevel.ERROR,
                    "Failed to initialize core services",
                    mapOf<String, Any>("error" to (e.message ?: "Unknown error")),
                    e
                )
                throw e
            }
        }
    }
    
    /**
     * Load persisted settings and environment configurations.
     * Restores user preferences from previous sessions.
     * 
     * Requirement: 8.2
     */
    private suspend fun loadPersistedSettings(project: Project) {
        withContext(Dispatchers.IO) {
            try {
                // Load application-level settings
                val settings = HttpPalSettings.getInstance()
                val stats = settings.getStatistics()
                
                LoggingUtils.logWithContext(
                    LoggingUtils.LogLevel.DEBUG,
                    "Loaded persisted settings",
                    mapOf<String, Any>(
                        "historyCount" to stats["historyCount"]!!,
                        "favoritesCount" to stats["favoritesCount"]!!,
                        "environmentsCount" to stats["environmentsCount"]!!
                    )
                )
                
                // Load environment configurations
                val environmentService = project.service<EnvironmentService>()
                val environments = environmentService.getAllEnvironments()
                val currentEnv = environmentService.getCurrentEnvironment()
                
                LoggingUtils.logWithContext(
                    LoggingUtils.LogLevel.DEBUG,
                    "Loaded environment configurations",
                    mapOf<String, Any>(
                        "totalEnvironments" to environments.size,
                        "currentEnvironment" to (currentEnv?.name ?: "None")
                    )
                )
                
            } catch (e: Exception) {
                LoggingUtils.logWithContext(
                    LoggingUtils.LogLevel.WARN,
                    "Failed to load persisted settings",
                    mapOf<String, Any>("error" to (e.message ?: "Unknown error")),
                    e
                )
                // Don't throw - settings loading failure shouldn't prevent plugin from working
            }
        }
    }
    
    /**
     * Register enhanced file change listener for automatic endpoint refresh.
     * The listener monitors Java/Kotlin file changes and triggers incremental scans.
     * 
     * Requirement: 8.3
     */
    private fun registerFileChangeListener(project: Project) {
        try {
            val discoveryService = project.service<EndpointDiscoveryService>()
            
            // Create enhanced file change listener with 500ms debounce
            fileChangeListener = EnhancedFileChangeListener(
                project = project,
                discoveryService = discoveryService,
                debounceDelayMs = 500
            )
            
            // Register listener with message bus
            val connection = project.messageBus.connect()
            connection.subscribe(VirtualFileManager.VFS_CHANGES, fileChangeListener!!)
            
            LoggingUtils.logWithContext(
                LoggingUtils.LogLevel.DEBUG,
                "Registered file change listener",
                mapOf<String, Any>("project" to project.name)
            )
            
        } catch (e: Exception) {
            LoggingUtils.logWithContext(
                LoggingUtils.LogLevel.ERROR,
                "Failed to register file change listener",
                mapOf<String, Any>("error" to (e.message ?: "Unknown error")),
                e
            )
            // Don't throw - file change listener failure shouldn't prevent plugin from working
        }
    }
    
    /**
     * Trigger automatic endpoint discovery in background thread.
     * This provides the auto-load functionality when the plugin starts.
     * 
     * After discovery completes, notifies all registered listeners of the discovered endpoints.
     * 
     * Requirement: 1.1, 1.2, 2.1, 2.2, 2.4
     */
    private suspend fun autoLoadEndpoints(project: Project) {
        withContext(Dispatchers.IO) {
            try {
                LoggingUtils.logWithContext(
                    LoggingUtils.LogLevel.INFO,
                    "Starting automatic endpoint discovery",
                    mapOf<String, Any>("project" to project.name)
                )
                
                val discoveryService = project.service<EndpointDiscoveryService>()
                val startTime = System.currentTimeMillis()
                
                // Discover endpoints in background
                val endpoints = discoveryService.discoverEndpoints()
                
                val duration = System.currentTimeMillis() - startTime
                
                LoggingUtils.logWithContext(
                    LoggingUtils.LogLevel.INFO,
                    "Automatic endpoint discovery completed",
                    mapOf<String, Any>(
                        "endpointCount" to endpoints.size,
                        "durationMs" to duration
                    )
                )
                
                // Notify listeners of discovered endpoints
                // This ensures UI components are updated after initial scan
                try {
                    LoggingUtils.logWithContext(
                        LoggingUtils.LogLevel.DEBUG,
                        "Notifying listeners of discovered endpoints",
                        mapOf<String, Any>(
                            "endpointCount" to endpoints.size,
                            "project" to project.name
                        )
                    )
                    
                    discoveryService.notifyEndpointsChanged(endpoints)
                    
                    LoggingUtils.logWithContext(
                        LoggingUtils.LogLevel.DEBUG,
                        "Successfully notified listeners",
                        mapOf<String, Any>("project" to project.name)
                    )
                } catch (e: Exception) {
                    LoggingUtils.logWithContext(
                        LoggingUtils.LogLevel.WARN,
                        "Failed to notify listeners of discovered endpoints",
                        mapOf<String, Any>("error" to (e.message ?: "Unknown error")),
                        e
                    )
                    // Don't throw - notification failure shouldn't prevent plugin from working
                }
                
            } catch (e: Exception) {
                LoggingUtils.logWithContext(
                    LoggingUtils.LogLevel.ERROR,
                    "Automatic endpoint discovery failed",
                    mapOf<String, Any>("error" to (e.message ?: "Unknown error")),
                    e
                )
                // Don't throw - endpoint discovery failure shouldn't prevent plugin from working
            }
        }
    }
    
    /**
     * Notify UI components that initialization is complete.
     * 
     * Note: Endpoint discovery and listener notification are now handled in autoLoadEndpoints().
     * This method is kept for future extensibility but no longer triggers endpoint scanning
     * to avoid duplicate scans during startup.
     * 
     * Requirement: 8.5, 2.3
     */
    private fun notifyInitializationComplete(project: Project) {
        try {
            LoggingUtils.logWithContext(
                LoggingUtils.LogLevel.DEBUG,
                "Plugin initialization complete - endpoints already loaded and notified in autoLoadEndpoints()",
                mapOf<String, Any>("project" to project.name)
            )
            
            // Endpoints have already been discovered and listeners notified in autoLoadEndpoints()
            // No need to trigger refreshEndpoints() here, which would cause a duplicate scan
            
        } catch (e: Exception) {
            LoggingUtils.logWithContext(
                LoggingUtils.LogLevel.WARN,
                "Failed to complete initialization notification",
                mapOf<String, Any>("error" to (e.message ?: "Unknown error")),
                e
            )
            // Don't throw - notification failure shouldn't prevent plugin from working
        }
    }
    
    /**
     * Handle initialization errors with user-friendly messages.
     * Logs detailed error information and displays notification to user.
     * 
     * Requirement: 1.5, 8.4
     */
    private fun handleInitializationError(project: Project, error: Exception) {
        val errorMessage = "Failed to initialize HttpPal plugin: ${error.message ?: "Unknown error"}"
        
        LoggingUtils.logWithContext(
            LoggingUtils.LogLevel.ERROR,
            errorMessage,
            mapOf<String, Any>(
                "project" to project.name,
                "errorType" to error.javaClass.simpleName
            ),
            error
        )
        
        // Show user-friendly error notification
        showErrorNotification(project, errorMessage)
    }
    
    /**
     * Show error notification to user with option to retry.
     */
    private fun showErrorNotification(project: Project, message: String) {
        ApplicationManager.getApplication().invokeLater {
            com.intellij.notification.NotificationGroupManager.getInstance()
                .getNotificationGroup("HttpPal")
                .createNotification(
                    "HttpPal Initialization Error",
                    message,
                    com.intellij.notification.NotificationType.ERROR
                )
                .notify(project)
        }
    }
}