package com.httppal.model

import java.time.Duration
import java.time.Instant

/**
 * Results from concurrent request execution
 */
data class ConcurrentExecutionResult(
    val totalRequests: Int,
    val successfulRequests: Int,
    val failedRequests: Int,
    val averageResponseTime: Duration,
    val minResponseTime: Duration,
    val maxResponseTime: Duration,
    val responses: List<HttpResponse>,
    val errors: List<ExecutionError>,
    val startTime: Instant = Instant.now(),
    val endTime: Instant = Instant.now(),
    val threadCount: Int,
    val requestsPerSecond: Double = 0.0
) {
    /**
     * Calculate success rate as percentage
     */
    fun getSuccessRate(): Double {
        return if (totalRequests > 0) {
            (successfulRequests.toDouble() / totalRequests) * 100
        } else {
            0.0
        }
    }
    
    /**
     * Calculate failure rate as percentage
     */
    fun getFailureRate(): Double {
        return if (totalRequests > 0) {
            (failedRequests.toDouble() / totalRequests) * 100
        } else {
            0.0
        }
    }
    
    /**
     * Get total execution duration
     */
    fun getTotalDuration(): Duration {
        return Duration.between(startTime, endTime)
    }
    
    /**
     * Get response time statistics
     */
    fun getResponseTimeStats(): ResponseTimeStats {
        val responseTimes = responses.map { it.responseTime.toMillis() }
        val sortedTimes = responseTimes.sorted()
        
        return ResponseTimeStats(
            min = minResponseTime.toMillis(),
            max = maxResponseTime.toMillis(),
            average = averageResponseTime.toMillis(),
            median = if (sortedTimes.isNotEmpty()) {
                sortedTimes[sortedTimes.size / 2]
            } else 0L,
            p95 = if (sortedTimes.isNotEmpty()) {
                val index = (sortedTimes.size * 0.95).toInt().coerceAtMost(sortedTimes.size - 1)
                sortedTimes[index]
            } else 0L,
            p99 = if (sortedTimes.isNotEmpty()) {
                val index = (sortedTimes.size * 0.99).toInt().coerceAtMost(sortedTimes.size - 1)
                sortedTimes[index]
            } else 0L
        )
    }
    
    /**
     * Get status code distribution
     */
    fun getStatusCodeDistribution(): Map<Int, Int> {
        return responses.groupingBy { it.statusCode }.eachCount()
    }
    
    /**
     * Get error distribution
     */
    fun getErrorDistribution(): Map<String, Int> {
        return errors.groupingBy { it.message }.eachCount()
    }
    
    /**
     * Calculate actual requests per second
     */
    fun getActualRequestsPerSecond(): Double {
        val durationSeconds = getTotalDuration().toMillis() / 1000.0
        return if (durationSeconds > 0) {
            totalRequests / durationSeconds
        } else {
            0.0
        }
    }
    
    /**
     * Get throughput statistics
     */
    fun getThroughputStats(): ThroughputStats {
        val totalBytes = responses.sumOf { it.getBodySize() }
        val durationSeconds = getTotalDuration().toMillis() / 1000.0
        
        return ThroughputStats(
            requestsPerSecond = getActualRequestsPerSecond(),
            bytesPerSecond = if (durationSeconds > 0) totalBytes / durationSeconds else 0.0,
            totalBytes = totalBytes,
            averageResponseSize = if (responses.isNotEmpty()) totalBytes / responses.size else 0
        )
    }
    
    /**
     * Get execution summary
     */
    fun getSummary(): String {
        val successRate = String.format("%.1f", getSuccessRate())
        val avgTime = averageResponseTime.toMillis()
        val rps = String.format("%.1f", getActualRequestsPerSecond())
        return "Total: $totalRequests | Success: $successRate% | Avg Time: ${avgTime}ms | RPS: $rps"
    }
    
    /**
     * Validate the execution result
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (totalRequests < 0) {
            errors.add("Total requests cannot be negative")
        }
        
        if (successfulRequests < 0) {
            errors.add("Successful requests cannot be negative")
        }
        
        if (failedRequests < 0) {
            errors.add("Failed requests cannot be negative")
        }
        
        if (successfulRequests + failedRequests != totalRequests) {
            errors.add("Sum of successful and failed requests must equal total requests")
        }
        
        if (threadCount < 1) {
            errors.add("Thread count must be at least 1")
        }
        
        if (endTime.isBefore(startTime)) {
            errors.add("End time cannot be before start time")
        }
        
        return errors
    }
}

/**
 * Execution error information
 */
data class ExecutionError(
    val message: String,
    val exception: String? = null,
    val requestIndex: Int? = null,
    val timestamp: Instant = Instant.now(),
    val errorType: ErrorType = ErrorType.UNKNOWN
) {
    /**
     * Get display summary
     */
    fun getDisplaySummary(): String {
        val indexStr = requestIndex?.let { " (Request #$it)" } ?: ""
        return "$message$indexStr"
    }
}

/**
 * Types of execution errors
 */
enum class ErrorType {
    NETWORK, TIMEOUT, VALIDATION, AUTHENTICATION, SERVER_ERROR, UNKNOWN
}

/**
 * Response time statistics
 */
data class ResponseTimeStats(
    val min: Long,
    val max: Long,
    val average: Long,
    val median: Long,
    val p95: Long = 0L,
    val p99: Long = 0L
)

/**
 * Throughput statistics
 */
data class ThroughputStats(
    val requestsPerSecond: Double,
    val bytesPerSecond: Double,
    val totalBytes: Int,
    val averageResponseSize: Int
)