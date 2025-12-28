package com.httppal.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import java.time.Duration
import kotlin.time.measureTime

/**
 * Enhanced logging utilities for debugging and monitoring
 */
object LoggingUtils {
    
    val logger = thisLogger()
    
    /**
     * Log levels for different types of operations
     */
    enum class LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }
    
    /**
     * Performance monitoring data
     */
    data class PerformanceMetrics(
        val operation: String,
        val duration: Duration,
        val success: Boolean,
        val details: Map<String, Any> = emptyMap()
    )
    
    /**
     * Create a logger for a specific class
     */
    fun getLogger(clazz: Class<*>): Logger = Logger.getInstance(clazz)
    
    /**
     * Log with context information
     */
    fun logWithContext(
        level: LogLevel,
        message: String,
        context: Map<String, Any> = emptyMap(),
        throwable: Throwable? = null,
        logger: Logger = LoggingUtils.logger
    ) {
        val contextString = if (context.isNotEmpty()) {
            " [${context.entries.joinToString(", ") { "${it.key}=${it.value}" }}]"
        } else ""
        
        val fullMessage = "$message$contextString"
        
        when (level) {
            LogLevel.TRACE -> logger.trace(fullMessage)
            LogLevel.DEBUG -> logger.debug(fullMessage)
            LogLevel.INFO -> logger.info(fullMessage)
            LogLevel.WARN -> logger.warn(fullMessage, throwable)
            LogLevel.ERROR -> logger.error(fullMessage, throwable)
        }
    }
    
    /**
     * Log HTTP request details
     */
    fun logHttpRequest(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        bodySize: Int = 0,
        logger: Logger = LoggingUtils.logger
    ) {
        val context = mutableMapOf<String, Any>(
            "method" to method,
            "url" to url,
            "headerCount" to headers.size,
            "bodySize" to bodySize
        )
        
        // Add sensitive header detection
        val sensitiveHeaders = headers.keys.filter { 
            it.lowercase().contains("auth") || 
            it.lowercase().contains("token") || 
            it.lowercase().contains("key")
        }
        if (sensitiveHeaders.isNotEmpty()) {
            context["sensitiveHeaders"] = sensitiveHeaders.size
        }
        
        logWithContext(LogLevel.INFO, "HTTP Request", context, logger = logger)
    }
    
    /**
     * Log HTTP response details
     */
    fun logHttpResponse(
        statusCode: Int,
        responseTime: Duration,
        bodySize: Int = 0,
        headers: Map<String, List<String>> = emptyMap(),
        logger: Logger = LoggingUtils.logger
    ) {
        val context = mapOf(
            "statusCode" to statusCode,
            "responseTime" to "${responseTime.toMillis()}ms",
            "bodySize" to bodySize,
            "headerCount" to headers.size
        )
        
        val level = when (statusCode) {
            in 200..299 -> LogLevel.INFO
            in 300..399 -> LogLevel.DEBUG
            in 400..499 -> LogLevel.WARN
            else -> LogLevel.ERROR
        }
        
        logWithContext(level, "HTTP Response", context, logger = logger)
    }
    
    /**
     * Log WebSocket connection events
     */
    fun logWebSocketEvent(
        event: String,
        url: String,
        details: Map<String, Any> = emptyMap(),
        logger: Logger = LoggingUtils.logger
    ) {
        val context = mutableMapOf<String, Any>(
            "event" to event,
            "url" to url
        )
        context.putAll(details)
        
        logWithContext(LogLevel.INFO, "WebSocket Event", context, logger = logger)
    }
    
    /**
     * Log validation errors with details
     */
    fun logValidationErrors(
        operation: String,
        errors: List<String>,
        context: Map<String, Any> = emptyMap(),
        logger: Logger = LoggingUtils.logger
    ) {
        val fullContext = context.toMutableMap()
        fullContext["errorCount"] = errors.size
        fullContext["errors"] = errors.joinToString("; ")
        
        logWithContext(LogLevel.WARN, "Validation failed: $operation", fullContext, logger = logger)
    }
    
    /**
     * Measure and log performance of an operation
     */
    inline fun <T> measureAndLog(
        operation: String,
        context: Map<String, Any> = emptyMap(),
        logger: Logger = LoggingUtils.logger,
        action: () -> T
    ): T {
        var success = true
        var result: T
        
        val duration = measureTime {
            try {
                logWithContext(LogLevel.DEBUG, "Starting: $operation", context, logger = logger)
                result = action()
            } catch (e: Exception) {
                success = false
                val errorContext = mapOf("error" to (e.message ?: "Unknown error"))
                logWithContext(
                    LogLevel.ERROR, 
                    "Failed: $operation", 
                    context + errorContext,
                    e, 
                    logger
                )
                throw e
            }
        }
        val javaDurationFromWholeNanos: Duration = java.time.Duration.ofNanos(duration.inWholeNanoseconds)
        val metrics = PerformanceMetrics(operation, javaDurationFromWholeNanos, success, context)
        logPerformanceMetrics(metrics, logger)
        
        return result
    }
    
    /**
     * Log performance metrics
     */
    fun logPerformanceMetrics(
        metrics: PerformanceMetrics,
        logger: Logger = LoggingUtils.logger
    ) {
        val context = mutableMapOf<String, Any>(
            "duration" to "${metrics.duration.toMillis()}ms",
            "success" to metrics.success
        )
        context.putAll(metrics.details)
        
        val level = if (metrics.success) {
            when {
                metrics.duration.toMillis() > 5000 -> LogLevel.WARN
                metrics.duration.toMillis() > 1000 -> LogLevel.INFO
                else -> LogLevel.DEBUG
            }
        } else {
            LogLevel.ERROR
        }
        
        logWithContext(level, "Performance: ${metrics.operation}", context, logger = logger)
    }
    
    /**
     * Log service lifecycle events
     */
    fun logServiceLifecycle(
        service: String,
        event: String,
        details: Map<String, Any> = emptyMap(),
        logger: Logger = LoggingUtils.logger
    ) {
        val context = mutableMapOf<String, Any>(
            "service" to service,
            "event" to event
        )
        context.putAll(details)
        
        logWithContext(LogLevel.INFO, "Service Lifecycle", context, logger = logger)
    }
    
    /**
     * Log UI events for debugging
     */
    fun logUIEvent(
        component: String,
        event: String,
        details: Map<String, Any> = emptyMap(),
        logger: Logger = LoggingUtils.logger
    ) {
        val context = mutableMapOf<String, Any>(
            "component" to component,
            "event" to event
        )
        context.putAll(details)
        
        logWithContext(LogLevel.DEBUG, "UI Event", context, logger = logger)
    }
    
    /**
     * Log configuration changes
     */
    fun logConfigurationChange(
        setting: String,
        oldValue: Any?,
        newValue: Any?,
        logger: Logger = LoggingUtils.logger
    ) {
        val context = mapOf(
            "setting" to setting,
            "oldValue" to (oldValue?.toString() ?: "null"),
            "newValue" to (newValue?.toString() ?: "null")
        )
        
        logWithContext(LogLevel.INFO, "Configuration Change", context, logger = logger)
    }
    
    /**
     * Log security-related events
     */
    fun logSecurityEvent(
        event: String,
        details: Map<String, Any> = emptyMap(),
        logger: Logger = LoggingUtils.logger
    ) {
        // Sanitize sensitive information
        val sanitizedDetails = details.mapValues { (key, value) ->
            if (key.lowercase().contains("password") || 
                key.lowercase().contains("token") || 
                key.lowercase().contains("key")) {
                "[REDACTED]"
            } else {
                value
            }
        }
        
        val context = mutableMapOf<String, Any>("event" to event)
        context.putAll(sanitizedDetails)
        
        logWithContext(LogLevel.WARN, "Security Event", context, logger = logger)
    }
    
    /**
     * Create a debug session for complex operations
     */
    class DebugSession(
        private val sessionName: String,
        private val logger: Logger
    ) {
        private val startTime = System.currentTimeMillis()
        private val events = mutableListOf<String>()
        
        fun log(event: String, details: Map<String, Any> = emptyMap()) {
            val timestamp = System.currentTimeMillis() - startTime
            val eventWithTime = "[$timestamp ms] $event"
            events.add(eventWithTime)
            
            logWithContext(LogLevel.DEBUG, "$sessionName: $event", details, logger = logger)
        }
        
        fun complete(success: Boolean = true) {
            val duration = System.currentTimeMillis() - startTime
            val context = mapOf(
                "duration" to "${duration}ms",
                "success" to success,
                "eventCount" to events.size
            )
            
            logWithContext(
                if (success) LogLevel.INFO else LogLevel.ERROR,
                "$sessionName completed",
                context,
                logger = logger
            )
        }
        
        fun getEvents(): List<String> = events.toList()
    }
    
    /**
     * Start a debug session
     */
    fun startDebugSession(sessionName: String, logger: Logger = this.logger): DebugSession {
        return DebugSession(sessionName, logger)
    }
    
    /**
     * Convenience method to log error messages
     */
    fun logError(message: String, throwable: Throwable? = null, logger: Logger = this.logger) {
        logWithContext(LogLevel.ERROR, message, emptyMap(), throwable, logger)
    }
    
    /**
     * Convenience method to log warning messages
     */
    fun logWarning(message: String, throwable: Throwable? = null, logger: Logger = this.logger) {
        logWithContext(LogLevel.WARN, message, emptyMap(), throwable, logger)
    }
    
    /**
     * Convenience method to log info messages
     */
    fun logInfo(message: String, logger: Logger = this.logger) {
        logWithContext(LogLevel.INFO, message, emptyMap(), null, logger)
    }
    
    /**
     * Convenience method to log debug messages
     */
    fun logDebug(message: String, logger: Logger = this.logger) {
        logWithContext(LogLevel.DEBUG, message, emptyMap(), null, logger)
    }
}