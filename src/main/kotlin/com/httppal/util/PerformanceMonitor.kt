package com.httppal.util

import com.intellij.openapi.diagnostic.thisLogger
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Performance monitoring utility for tracking operation execution times
 * Provides detailed performance metrics and logging for debugging and optimization
 */
object PerformanceMonitor {
    
    private val logger = thisLogger()
    
    /**
     * Performance thresholds for different operation types
     */
    object Thresholds {
        const val ENDPOINT_DISCOVERY_MS = 5000L      // 5 seconds
        const val REQUEST_EXECUTION_MS = 10000L      // 10 seconds
        const val UI_UPDATE_MS = 100L                // 100 milliseconds
        const val FILE_SCAN_MS = 1000L               // 1 second
        const val CONCURRENT_EXECUTION_MS = 30000L   // 30 seconds
    }
    
    /**
     * Performance metric data
     */
    data class PerformanceMetric(
        val operation: String,
        val startTime: Instant,
        val endTime: Instant,
        val duration: Duration,
        val success: Boolean,
        val details: Map<String, Any> = emptyMap(),
        val threshold: Long? = null
    ) {
        val durationMs: Long get() = duration.toMillis()
        val exceededThreshold: Boolean get() = threshold?.let { durationMs > it } ?: false
    }
    
    /**
     * Performance statistics for an operation type
     */
    data class PerformanceStats(
        val operation: String,
        val count: Long,
        val totalDurationMs: Long,
        val averageDurationMs: Long,
        val minDurationMs: Long,
        val maxDurationMs: Long,
        val successCount: Long,
        val failureCount: Long
    ) {
        val successRate: Double get() = if (count > 0) successCount.toDouble() / count else 0.0
    }
    
    /**
     * Performance tracking session
     */
    class PerformanceSession(
        val operation: String,
        val threshold: Long? = null,
        val details: MutableMap<String, Any> = mutableMapOf()
    ) {
        private val startTime = Instant.now()
        private var endTime: Instant? = null
        private var success: Boolean = true
        
        fun addDetail(key: String, value: Any) {
            details[key] = value
        }
        
        fun markFailure() {
            success = false
        }
        
        fun complete(): PerformanceMetric {
            endTime = Instant.now()
            val duration = Duration.between(startTime, endTime)
            
            val metric = PerformanceMetric(
                operation = operation,
                startTime = startTime,
                endTime = endTime!!,
                duration = duration,
                success = success,
                details = details.toMap(),
                threshold = threshold
            )
            
            recordMetric(metric)
            logMetric(metric)
            
            return metric
        }
    }
    
    // Statistics tracking
    private val operationStats = ConcurrentHashMap<String, MutableList<PerformanceMetric>>()
    private val operationCounts = ConcurrentHashMap<String, AtomicLong>()
    
    /**
     * Start a performance monitoring session
     */
    fun startSession(
        operation: String,
        threshold: Long? = null,
        details: Map<String, Any> = emptyMap()
    ): PerformanceSession {
        logger.debug("Performance monitoring started: $operation")
        return PerformanceSession(operation, threshold, details.toMutableMap())
    }
    
    /**
     * Measure and log the performance of an operation
     */
    inline fun <T> measure(
        operation: String,
        threshold: Long? = null,
        details: Map<String, Any> = emptyMap(),
        action: () -> T
    ): T {
        val session = startSession(operation, threshold, details)
        
        return try {
            val result = action()
            session.complete()
            result
        } catch (e: Exception) {
            session.markFailure()
            session.addDetail("error", e.message ?: "Unknown error")
            session.complete()
            throw e
        }
    }
    
    /**
     * Measure endpoint discovery performance
     */
    inline fun <T> measureEndpointDiscovery(
        details: Map<String, Any> = emptyMap(),
        action: () -> T
    ): T {
        return measure(
            operation = "Endpoint Discovery",
            threshold = Thresholds.ENDPOINT_DISCOVERY_MS,
            details = details,
            action = action
        )
    }
    
    /**
     * Measure request execution performance
     */
    inline fun <T> measureRequestExecution(
        method: String,
        url: String,
        details: Map<String, Any> = emptyMap(),
        action: () -> T
    ): T {
        return measure(
            operation = "Request Execution",
            threshold = Thresholds.REQUEST_EXECUTION_MS,
            details = details + mapOf(
                "method" to method,
                "url" to url
            ),
            action = action
        )
    }
    
    /**
     * Measure UI update performance
     */
    inline fun <T> measureUIUpdate(
        component: String,
        details: Map<String, Any> = emptyMap(),
        action: () -> T
    ): T {
        return measure(
            operation = "UI Update",
            threshold = Thresholds.UI_UPDATE_MS,
            details = details + mapOf(
                "component" to component
            ),
            action = action
        )
    }
    
    /**
     * Measure file scanning performance
     */
    inline fun <T> measureFileScan(
        fileName: String,
        details: Map<String, Any> = emptyMap(),
        action: () -> T
    ): T {
        return measure(
            operation = "File Scan",
            threshold = Thresholds.FILE_SCAN_MS,
            details = details + mapOf(
                "file" to fileName
            ),
            action = action
        )
    }
    
    /**
     * Measure concurrent execution performance
     */
    inline fun <T> measureConcurrentExecution(
        threadCount: Int,
        iterations: Int,
        details: Map<String, Any> = emptyMap(),
        action: () -> T
    ): T {
        return measure(
            operation = "Concurrent Execution",
            threshold = Thresholds.CONCURRENT_EXECUTION_MS,
            details = details + mapOf(
                "threadCount" to threadCount,
                "iterations" to iterations
            ),
            action = action
        )
    }
    
    /**
     * Record a performance metric
     */
    private fun recordMetric(metric: PerformanceMetric) {
        operationStats.computeIfAbsent(metric.operation) { mutableListOf() }.add(metric)
        operationCounts.computeIfAbsent(metric.operation) { AtomicLong(0) }.incrementAndGet()
        
        // Keep only last 100 metrics per operation to avoid memory issues
        val metrics = operationStats[metric.operation]
        if (metrics != null && metrics.size > 100) {
            metrics.removeAt(0)
        }
    }
    
    /**
     * Log a performance metric
     */
    private fun logMetric(metric: PerformanceMetric) {
        val logLevel = when {
            !metric.success -> LoggingUtils.LogLevel.ERROR
            metric.exceededThreshold -> LoggingUtils.LogLevel.WARN
            metric.durationMs > 1000 -> LoggingUtils.LogLevel.INFO
            else -> LoggingUtils.LogLevel.DEBUG
        }
        
        val context = mutableMapOf<String, Any>(
            "duration" to "${metric.durationMs}ms",
            "success" to metric.success
        )
        
        if (metric.threshold != null) {
            context["threshold"] to "${metric.threshold}ms"
            context["exceededThreshold"] to metric.exceededThreshold
        }
        
        context.putAll(metric.details)
        
        LoggingUtils.logWithContext(
            logLevel,
            "Performance: ${metric.operation}",
            context
        )
        
        // Log warning if threshold exceeded
        if (metric.exceededThreshold) {
            logger.warn(
                "Performance threshold exceeded for ${metric.operation}: " +
                "${metric.durationMs}ms > ${metric.threshold}ms"
            )
        }
    }
    
    /**
     * Get performance statistics for an operation
     */
    fun getStats(operation: String): PerformanceStats? {
        val metrics = operationStats[operation] ?: return null
        if (metrics.isEmpty()) return null
        
        val durations = metrics.map { it.durationMs }
        val successCount = metrics.count { it.success }.toLong()
        val failureCount = metrics.count { !it.success }.toLong()
        
        return PerformanceStats(
            operation = operation,
            count = metrics.size.toLong(),
            totalDurationMs = durations.sum(),
            averageDurationMs = durations.average().toLong(),
            minDurationMs = durations.minOrNull() ?: 0,
            maxDurationMs = durations.maxOrNull() ?: 0,
            successCount = successCount,
            failureCount = failureCount
        )
    }
    
    /**
     * Get all performance statistics
     */
    fun getAllStats(): Map<String, PerformanceStats> {
        return operationStats.keys.mapNotNull { operation ->
            getStats(operation)?.let { operation to it }
        }.toMap()
    }
    
    /**
     * Log performance summary for all operations
     */
    fun logPerformanceSummary() {
        val allStats = getAllStats()
        
        if (allStats.isEmpty()) {
            logger.info("No performance metrics recorded")
            return
        }
        
        logger.info("=== Performance Summary ===")
        
        allStats.forEach { (operation, stats) ->
            logger.info(
                "$operation: " +
                "count=${stats.count}, " +
                "avg=${stats.averageDurationMs}ms, " +
                "min=${stats.minDurationMs}ms, " +
                "max=${stats.maxDurationMs}ms, " +
                "success=${String.format("%.1f%%", stats.successRate * 100)}"
            )
        }
        
        logger.info("=== End Performance Summary ===")
    }
    
    /**
     * Clear all performance metrics
     */
    fun clearMetrics() {
        operationStats.clear()
        operationCounts.clear()
        logger.info("Performance metrics cleared")
    }
    
    /**
     * Get recent metrics for an operation
     */
    fun getRecentMetrics(operation: String, count: Int = 10): List<PerformanceMetric> {
        val metrics = operationStats[operation] ?: return emptyList()
        return metrics.takeLast(count)
    }
    
    /**
     * Check if an operation is performing poorly
     */
    fun isPerformingPoorly(operation: String, threshold: Long): Boolean {
        val stats = getStats(operation) ?: return false
        return stats.averageDurationMs > threshold || stats.successRate < 0.9
    }
    
    /**
     * Get slow operations (exceeding their thresholds)
     */
    fun getSlowOperations(): List<Pair<String, PerformanceStats>> {
        return getAllStats()
            .filter { (operation, stats) ->
                val threshold = when (operation) {
                    "Endpoint Discovery" -> Thresholds.ENDPOINT_DISCOVERY_MS
                    "Request Execution" -> Thresholds.REQUEST_EXECUTION_MS
                    "UI Update" -> Thresholds.UI_UPDATE_MS
                    "File Scan" -> Thresholds.FILE_SCAN_MS
                    "Concurrent Execution" -> Thresholds.CONCURRENT_EXECUTION_MS
                    else -> null
                }
                threshold != null && stats.averageDurationMs > threshold
            }
            .toList()
    }
    
    /**
     * Log slow operations warning
     */
    fun logSlowOperationsWarning() {
        val slowOps = getSlowOperations()
        
        if (slowOps.isNotEmpty()) {
            logger.warn("=== Slow Operations Detected ===")
            slowOps.forEach { (operation, stats) ->
                logger.warn(
                    "$operation is performing slowly: " +
                    "avg=${stats.averageDurationMs}ms, " +
                    "max=${stats.maxDurationMs}ms"
                )
            }
            logger.warn("=== End Slow Operations ===")
        }
    }
}
