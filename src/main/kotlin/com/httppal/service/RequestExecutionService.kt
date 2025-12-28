package com.httppal.service

import com.httppal.model.*
import com.httppal.util.EnhancedConcurrentResult

/**
 * Service for executing HTTP requests
 * Handles single requests, concurrent execution, and request/response processing
 */
interface RequestExecutionService {
    
    /**
     * Execute a single HTTP request
     */
    suspend fun executeRequest(config: RequestConfig): HttpResponse
    
    /**
     * Execute multiple requests concurrently
     * Returns basic ConcurrentExecutionResult for backward compatibility
     */
    suspend fun executeConcurrentRequests(
        config: RequestConfig,
        threadCount: Int,
        iterations: Int
    ): ConcurrentExecutionResult
    
    /**
     * Execute multiple requests concurrently with enhanced statistics
     * Requirements 5.4: Returns EnhancedConcurrentResult with detailed statistics
     */
    suspend fun executeConcurrentRequestsWithStats(
        config: RequestConfig,
        threadCount: Int,
        iterations: Int
    ): EnhancedConcurrentResult
    
    /**
     * Cancel a running execution
     */
    fun cancelExecution(executionId: String): Boolean
    
    /**
     * Get status of a running execution
     */
    fun getExecutionStatus(executionId: String): ExecutionStatus?
    
    /**
     * Add listener for execution progress updates
     */
    fun addProgressListener(executionId: String, listener: (ExecutionProgress) -> Unit)
    
    /**
     * Remove progress listener
     */
    fun removeProgressListener(executionId: String, listener: (ExecutionProgress) -> Unit)
}

/**
 * Status of a request execution
 */
enum class ExecutionStatus {
    PENDING, RUNNING, COMPLETED, CANCELLED, FAILED
}

/**
 * Progress information for concurrent execution
 */
data class ExecutionProgress(
    val executionId: String,
    val totalRequests: Int,
    val completedRequests: Int,
    val successfulRequests: Int,
    val failedRequests: Int,
    val averageResponseTime: Long? = null
)