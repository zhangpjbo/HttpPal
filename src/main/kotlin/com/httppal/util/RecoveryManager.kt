package com.httppal.util

import com.httppal.util.MapUtils.safeMapOf
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Manages error recovery mechanisms and retry strategies
 */
object RecoveryManager {
    
    private val logger = thisLogger()
    private val retryAttempts = ConcurrentHashMap<String, AtomicInteger>()
    private val recoveryStrategies = ConcurrentHashMap<String, RecoveryStrategy>()
    
    /**
     * Recovery strategy configuration
     */
    data class RecoveryStrategy(
        val maxRetries: Int = 3,
        val initialDelay: Duration = 1.seconds,
        val maxDelay: Duration = 30.seconds,
        val backoffMultiplier: Double = 2.0,
        val retryableExceptions: Set<Class<out Exception>> = setOf(
            java.net.ConnectException::class.java,
            java.net.SocketTimeoutException::class.java,
            java.io.IOException::class.java
        ),
        val customRecoveryAction: (suspend () -> Unit)? = null
    )
    
    /**
     * Recovery result
     */
    sealed class RecoveryResult<T> {
        data class Success<T>(val value: T, val attemptsUsed: Int) : RecoveryResult<T>()
        data class Failure<T>(val lastException: Exception, val attemptsUsed: Int) : RecoveryResult<T>()
        data class Recovered<T>(val value: T, val attemptsUsed: Int, val recoveryUsed: Boolean) : RecoveryResult<T>()
    }
    
    /**
     * Register a recovery strategy for a specific operation
     */
    fun registerRecoveryStrategy(operationId: String, strategy: RecoveryStrategy) {
        recoveryStrategies[operationId] = strategy
        LoggingUtils.logWithContext(
            LoggingUtils.LogLevel.DEBUG,
            "Recovery strategy registered",
            mapOf(
                "operationId" to operationId,
                "maxRetries" to strategy.maxRetries,
                "initialDelay" to strategy.initialDelay.toString()
            )
        )
    }
    
    /**
     * Execute operation with automatic retry and recovery
     */
    suspend fun <T> executeWithRecovery(
        operationId: String,
        project: Project? = null,
        operation: suspend () -> T
    ): RecoveryResult<T> {
        val strategy = recoveryStrategies[operationId] ?: RecoveryStrategy()
        val attemptCounter = retryAttempts.computeIfAbsent(operationId) { AtomicInteger(0) }
        
        var lastException: Exception? = null
        var delay = strategy.initialDelay
        
        repeat(strategy.maxRetries + 1) { attempt ->
            try {
                LoggingUtils.logWithContext(
                    LoggingUtils.LogLevel.DEBUG,
                    "Executing operation",
                    mapOf(
                        "operationId" to operationId,
                        "attempt" to (attempt + 1),
                        "maxRetries" to strategy.maxRetries
                    )
                )
                
                val result = operation()
                
                // Reset attempt counter on success
                attemptCounter.set(0)
                
                return if (attempt == 0) {
                    RecoveryResult.Success(result, 1)
                } else {
                    LoggingUtils.logWithContext(
                        LoggingUtils.LogLevel.INFO,
                        "Operation recovered after retries",
                        mapOf(
                            "operationId" to operationId,
                            "attemptsUsed" to (attempt + 1)
                        )
                    )
                    RecoveryResult.Recovered(result, attempt + 1, false)
                }
                
            } catch (e: Exception) {
                lastException = e
                attemptCounter.incrementAndGet()
                
                LoggingUtils.logWithContext(
                    LoggingUtils.LogLevel.WARN,
                    "Operation attempt failed",
                    safeMapOf(
                        "operationId" to operationId,
                        "attempt" to (attempt + 1),
                        "error" to e.message
                    ),
                    e
                )
                
                // Check if exception is retryable
                if (!isRetryableException(e, strategy)) {
                    LoggingUtils.logWithContext(
                        LoggingUtils.LogLevel.ERROR,
                        "Non-retryable exception, aborting",
                        safeMapOf("operationId" to operationId, "exception" to e::class.simpleName)
                    )
                    return RecoveryResult.Failure(e, attempt + 1)
                }
                
                // Try custom recovery action if available
                strategy.customRecoveryAction?.let { recoveryAction ->
                    try {
                        LoggingUtils.logWithContext(
                            LoggingUtils.LogLevel.INFO,
                            "Attempting custom recovery",
                            mapOf("operationId" to operationId)
                        )
                        recoveryAction()
                        
                        // Try operation again after recovery
                        val recoveredResult = operation()
                        attemptCounter.set(0)
                        
                        return RecoveryResult.Recovered(recoveredResult, attempt + 1, true)
                        
                    } catch (recoveryException: Exception) {
                        LoggingUtils.logWithContext(
                            LoggingUtils.LogLevel.WARN,
                            "Custom recovery failed",
                            safeMapOf("operationId" to operationId, "error" to recoveryException.message),
                            recoveryException
                        )
                    }
                }
                
                // Wait before retry (except on last attempt)
                if (attempt < strategy.maxRetries) {
                    LoggingUtils.logWithContext(
                        LoggingUtils.LogLevel.DEBUG,
                        "Waiting before retry",
                        mapOf(
                            "operationId" to operationId,
                            "delay" to delay.toString()
                        )
                    )
                    delay(delay.inWholeMilliseconds)
                    
                    // Exponential backoff
                    delay = minOf(
                        (delay.inWholeMilliseconds * strategy.backoffMultiplier).toLong().milliseconds,
                        strategy.maxDelay
                    )
                }
            }
        }
        
        return RecoveryResult.Failure(
            lastException ?: RuntimeException("Unknown error"),
            strategy.maxRetries + 1
        )
    }
    
    /**
     * Execute operation with simple retry
     */
    suspend fun <T> executeWithRetry(
        operationId: String,
        maxRetries: Int = 3,
        delay: Duration = 1.seconds,
        operation: suspend () -> T
    ): T {
        val strategy = RecoveryStrategy(maxRetries = maxRetries, initialDelay = delay)
        registerRecoveryStrategy(operationId, strategy)
        
        return when (val result = executeWithRecovery(operationId, null, operation)) {
            is RecoveryResult.Success -> result.value
            is RecoveryResult.Recovered -> result.value
            is RecoveryResult.Failure -> throw result.lastException
        }
    }
    
    /**
     * Reset retry attempts for an operation
     */
    fun resetRetryAttempts(operationId: String) {
        retryAttempts[operationId]?.set(0)
        LoggingUtils.logWithContext(
            LoggingUtils.LogLevel.DEBUG,
            "Reset retry attempts",
            mapOf("operationId" to operationId)
        )
    }
    
    /**
     * Get current retry attempt count
     */
    fun getRetryAttempts(operationId: String): Int {
        return retryAttempts[operationId]?.get() ?: 0
    }
    
    /**
     * Clear all retry attempts
     */
    fun clearAllRetryAttempts() {
        retryAttempts.clear()
        LoggingUtils.logWithContext(LoggingUtils.LogLevel.DEBUG, "Cleared all retry attempts")
    }
    
    /**
     * Create a circuit breaker for an operation
     */
    class CircuitBreaker(
        private val operationId: String,
        private val failureThreshold: Int = 5,
        private val recoveryTimeout: Duration = 30.seconds
    ) {
        private var failureCount = AtomicInteger(0)
        private var lastFailureTime = 0L
        private var state = CircuitState.CLOSED
        
        enum class CircuitState { CLOSED, OPEN, HALF_OPEN }
        
        suspend fun <T> execute(operation: suspend () -> T): T {
            when (state) {
                CircuitState.OPEN -> {
                    if (System.currentTimeMillis() - lastFailureTime > recoveryTimeout.inWholeMilliseconds) {
                        state = CircuitState.HALF_OPEN
                        LoggingUtils.logWithContext(
                            LoggingUtils.LogLevel.INFO,
                            "Circuit breaker half-open",
                            mapOf("operationId" to operationId)
                        )
                    } else {
                        throw RuntimeException("Circuit breaker is OPEN for operation: $operationId")
                    }
                }
                CircuitState.HALF_OPEN -> {
                    // Allow one attempt in half-open state
                }
                CircuitState.CLOSED -> {
                    // Normal operation
                }
            }
            
            return try {
                val result = operation()
                
                // Reset on success
                if (state == CircuitState.HALF_OPEN) {
                    state = CircuitState.CLOSED
                    failureCount.set(0)
                    LoggingUtils.logWithContext(
                        LoggingUtils.LogLevel.INFO,
                        "Circuit breaker closed after recovery",
                        mapOf("operationId" to operationId)
                    )
                }
                
                result
                
            } catch (e: Exception) {
                val currentFailures = failureCount.incrementAndGet()
                lastFailureTime = System.currentTimeMillis()
                
                if (currentFailures >= failureThreshold) {
                    state = CircuitState.OPEN
                    LoggingUtils.logWithContext(
                        LoggingUtils.LogLevel.ERROR,
                        "Circuit breaker opened",
                        mapOf(
                            "operationId" to operationId,
                            "failureCount" to currentFailures,
                            "threshold" to failureThreshold
                        )
                    )
                }
                
                throw e
            }
        }
        
        fun getState(): CircuitState = state
        fun getFailureCount(): Int = failureCount.get()
    }
    
    /**
     * Create a circuit breaker for an operation
     */
    fun createCircuitBreaker(
        operationId: String,
        failureThreshold: Int = 5,
        recoveryTimeout: Duration = 30.seconds
    ): CircuitBreaker {
        return CircuitBreaker(operationId, failureThreshold, recoveryTimeout)
    }
    
    /**
     * Common recovery strategies
     */
    object CommonStrategies {
        val HTTP_REQUEST = RecoveryStrategy(
            maxRetries = 3,
            initialDelay = 1.seconds,
            maxDelay = 10.seconds,
            retryableExceptions = setOf(
                java.net.ConnectException::class.java,
                java.net.SocketTimeoutException::class.java,
                java.io.IOException::class.java
            )
        )
        
        val WEBSOCKET_CONNECTION = RecoveryStrategy(
            maxRetries = 5,
            initialDelay = 2.seconds,
            maxDelay = 30.seconds,
            retryableExceptions = setOf(
                java.net.ConnectException::class.java,
                java.net.SocketException::class.java
            )
        )
        
        val FILE_OPERATION = RecoveryStrategy(
            maxRetries = 2,
            initialDelay = 500.milliseconds,
            maxDelay = 2.seconds,
            retryableExceptions = setOf(
                java.io.IOException::class.java,
                java.nio.file.AccessDeniedException::class.java
            )
        )
        
        val DATABASE_OPERATION = RecoveryStrategy(
            maxRetries = 3,
            initialDelay = 1.seconds,
            maxDelay = 15.seconds,
            retryableExceptions = setOf(
                java.sql.SQLException::class.java,
                java.net.ConnectException::class.java
            )
        )
    }
    
    private fun isRetryableException(exception: Exception, strategy: RecoveryStrategy): Boolean {
        return strategy.retryableExceptions.any { retryableClass ->
            retryableClass.isAssignableFrom(exception::class.java)
        }
    }
    
    /**
     * Initialize common recovery strategies
     */
    fun initializeCommonStrategies() {
        registerRecoveryStrategy("http_request", CommonStrategies.HTTP_REQUEST)
        registerRecoveryStrategy("websocket_connection", CommonStrategies.WEBSOCKET_CONNECTION)
        registerRecoveryStrategy("file_operation", CommonStrategies.FILE_OPERATION)
        registerRecoveryStrategy("database_operation", CommonStrategies.DATABASE_OPERATION)
        
        LoggingUtils.logWithContext(
            LoggingUtils.LogLevel.INFO,
            "Common recovery strategies initialized"
        )
    }
}