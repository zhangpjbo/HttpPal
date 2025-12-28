package com.httppal.util

import com.httppal.model.*
import java.time.Duration

/**
 * Calculator for enhanced concurrent execution statistics
 * Implements requirements 5.4, 7.1, 7.2, 7.3, 7.4, 7.5
 * 
 * Provides comprehensive statistical analysis of concurrent request execution results including:
 * - Response time statistics (average, min, max, percentiles)
 * - Success and failure rates
 * - Requests per second (RPS)
 * - Error classification and distribution
 */
class ConcurrentStatisticsCalculator {
    
    /**
     * Calculate enhanced statistics from concurrent execution result
     * Requirements 5.4, 7.1, 7.2, 7.3, 7.4, 7.5
     */
    fun calculateStatistics(result: ConcurrentExecutionResult): EnhancedConcurrentResult {
        val rps = calculateRPS(result)
        val percentiles = calculatePercentiles(result)
        val errorBreakdown = classifyErrors(result)
        
        return EnhancedConcurrentResult(
            basicResult = result,
            rps = rps,
            percentiles = percentiles,
            errorBreakdown = errorBreakdown
        )
    }
    
    /**
     * Calculate requests per second (RPS)
     * Requirements 7.3
     */
    private fun calculateRPS(result: ConcurrentExecutionResult): Double {
        val totalDuration = result.getTotalDuration()
        val durationSeconds = totalDuration.toMillis() / 1000.0
        
        return if (durationSeconds > 0) {
            result.totalRequests / durationSeconds
        } else {
            0.0
        }
    }
    
    /**
     * Calculate response time percentiles (P50, P95, P99)
     * Requirements 7.4
     */
    private fun calculatePercentiles(result: ConcurrentExecutionResult): ResponseTimePercentiles {
        val responseTimes = result.responses
            .map { it.responseTime.toMillis() }
            .sorted()
        
        if (responseTimes.isEmpty()) {
            return ResponseTimePercentiles(
                p50 = Duration.ZERO,
                p95 = Duration.ZERO,
                p99 = Duration.ZERO
            )
        }
        
        val p50 = calculatePercentile(responseTimes, 0.50)
        val p95 = calculatePercentile(responseTimes, 0.95)
        val p99 = calculatePercentile(responseTimes, 0.99)
        
        return ResponseTimePercentiles(
            p50 = Duration.ofMillis(p50),
            p95 = Duration.ofMillis(p95),
            p99 = Duration.ofMillis(p99)
        )
    }
    
    /**
     * Calculate a specific percentile from sorted response times
     */
    private fun calculatePercentile(sortedTimes: List<Long>, percentile: Double): Long {
        if (sortedTimes.isEmpty()) return 0L
        
        val index = (sortedTimes.size * percentile).toInt()
            .coerceAtMost(sortedTimes.size - 1)
            .coerceAtLeast(0)
        
        return sortedTimes[index]
    }
    
    /**
     * Classify and count errors by type
     * Requirements 7.5
     */
    private fun classifyErrors(result: ConcurrentExecutionResult): Map<ErrorType, Int> {
        val errorCounts = mutableMapOf<ErrorType, Int>()
        
        // Initialize all error types with 0
        ErrorType.values().forEach { errorType ->
            errorCounts[errorType] = 0
        }
        
        // Count errors by type
        result.errors.forEach { error ->
            val currentCount = errorCounts[error.errorType] ?: 0
            errorCounts[error.errorType] = currentCount + 1
        }
        
        return errorCounts
    }
    
    /**
     * Calculate average response time
     * Requirements 7.1
     */
    fun calculateAverageResponseTime(result: ConcurrentExecutionResult): Duration {
        if (result.responses.isEmpty()) {
            return Duration.ZERO
        }
        
        val totalMillis = result.responses.sumOf { it.responseTime.toMillis() }
        val averageMillis = totalMillis / result.responses.size
        
        return Duration.ofMillis(averageMillis)
    }
    
    /**
     * Calculate minimum response time
     * Requirements 7.1
     */
    fun calculateMinResponseTime(result: ConcurrentExecutionResult): Duration {
        if (result.responses.isEmpty()) {
            return Duration.ZERO
        }
        
        val minMillis = result.responses.minOf { it.responseTime.toMillis() }
        return Duration.ofMillis(minMillis)
    }
    
    /**
     * Calculate maximum response time
     * Requirements 7.1
     */
    fun calculateMaxResponseTime(result: ConcurrentExecutionResult): Duration {
        if (result.responses.isEmpty()) {
            return Duration.ZERO
        }
        
        val maxMillis = result.responses.maxOf { it.responseTime.toMillis() }
        return Duration.ofMillis(maxMillis)
    }
    
    /**
     * Calculate success rate as percentage
     * Requirements 7.2
     */
    fun calculateSuccessRate(result: ConcurrentExecutionResult): Double {
        return if (result.totalRequests > 0) {
            (result.successfulRequests.toDouble() / result.totalRequests) * 100.0
        } else {
            0.0
        }
    }
    
    /**
     * Calculate failure rate as percentage
     * Requirements 7.2
     */
    fun calculateFailureRate(result: ConcurrentExecutionResult): Double {
        return if (result.totalRequests > 0) {
            (result.failedRequests.toDouble() / result.totalRequests) * 100.0
        } else {
            0.0
        }
    }
    
    /**
     * Get detailed error statistics
     * Requirements 7.5
     */
    fun getErrorStatistics(result: ConcurrentExecutionResult): ErrorStatistics {
        val errorBreakdown = classifyErrors(result)
        val totalErrors = result.errors.size
        
        val errorPercentages = errorBreakdown.mapValues { (_, count) ->
            if (totalErrors > 0) {
                (count.toDouble() / totalErrors) * 100.0
            } else {
                0.0
            }
        }
        
        return ErrorStatistics(
            totalErrors = totalErrors,
            errorBreakdown = errorBreakdown,
            errorPercentages = errorPercentages
        )
    }
}

/**
 * Enhanced concurrent execution result with additional statistics
 * Requirements 5.4
 */
data class EnhancedConcurrentResult(
    val basicResult: ConcurrentExecutionResult,
    val rps: Double,
    val percentiles: ResponseTimePercentiles,
    val errorBreakdown: Map<ErrorType, Int>
) {
    /**
     * Get success rate as percentage
     */
    fun getSuccessRate(): Double {
        return basicResult.getSuccessRate()
    }
    
    /**
     * Get failure rate as percentage
     */
    fun getFailureRate(): Double {
        return basicResult.getFailureRate()
    }
    
    /**
     * Get average response time
     */
    fun getAverageResponseTime(): Duration {
        return basicResult.averageResponseTime
    }
    
    /**
     * Get minimum response time
     */
    fun getMinResponseTime(): Duration {
        return basicResult.minResponseTime
    }
    
    /**
     * Get maximum response time
     */
    fun getMaxResponseTime(): Duration {
        return basicResult.maxResponseTime
    }
    
    /**
     * Get total requests
     */
    fun getTotalRequests(): Int {
        return basicResult.totalRequests
    }
    
    /**
     * Get successful requests count
     */
    fun getSuccessfulRequests(): Int {
        return basicResult.successfulRequests
    }
    
    /**
     * Get failed requests count
     */
    fun getFailedRequests(): Int {
        return basicResult.failedRequests
    }
    
    /**
     * Get execution duration
     */
    fun getTotalDuration(): Duration {
        return basicResult.getTotalDuration()
    }
    
    /**
     * Get summary string
     */
    fun getSummary(): String {
        val successRate = String.format("%.1f", getSuccessRate())
        val avgTime = getAverageResponseTime().toMillis()
        val rpsFormatted = String.format("%.1f", rps)
        
        return "Total: ${getTotalRequests()} | Success: $successRate% | " +
               "Avg Time: ${avgTime}ms | RPS: $rpsFormatted | " +
               "P50: ${percentiles.p50.toMillis()}ms | " +
               "P95: ${percentiles.p95.toMillis()}ms | " +
               "P99: ${percentiles.p99.toMillis()}ms"
    }
}

/**
 * Response time percentiles
 * Requirements 7.4
 */
data class ResponseTimePercentiles(
    val p50: Duration,  // Median
    val p95: Duration,  // 95th percentile
    val p99: Duration   // 99th percentile
)

/**
 * Detailed error statistics
 * Requirements 7.5
 */
data class ErrorStatistics(
    val totalErrors: Int,
    val errorBreakdown: Map<ErrorType, Int>,
    val errorPercentages: Map<ErrorType, Double>
) {
    /**
     * Get the most common error type
     */
    fun getMostCommonErrorType(): ErrorType? {
        return errorBreakdown.maxByOrNull { it.value }?.key
    }
    
    /**
     * Get error summary string
     */
    fun getSummary(): String {
        if (totalErrors == 0) {
            return "No errors"
        }
        
        val breakdown = errorBreakdown
            .filter { it.value > 0 }
            .entries
            .sortedByDescending { it.value }
            .joinToString(", ") { (type, count) ->
                val percentage = String.format("%.1f", errorPercentages[type] ?: 0.0)
                "$type: $count ($percentage%)"
            }
        
        return "Total Errors: $totalErrors | $breakdown"
    }
}
