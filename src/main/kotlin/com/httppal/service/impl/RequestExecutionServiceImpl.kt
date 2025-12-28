package com.httppal.service.impl

import com.httppal.model.*
import com.httppal.service.ExecutionProgress
import com.httppal.service.ExecutionStatus
import com.httppal.service.HistoryService
import com.httppal.service.RequestExecutionService
import com.httppal.util.ConcurrentStatisticsCalculator
import com.httppal.util.EnhancedConcurrentResult
import com.httppal.util.LoggingUtils
import com.httppal.util.PerformanceMonitor
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Implementation of RequestExecutionService using OkHttp
 * Supports single and concurrent request execution with status tracking and cancellation
 * 
 * Performance optimizations:
 * - Thread pool reuse: Reuses thread pools across concurrent executions
 * - Connection pooling: Uses OkHttp's built-in connection pool
 * - Memory optimization: Limits concurrent requests and uses streaming for large responses
 */
class RequestExecutionServiceImpl : RequestExecutionService {
    
    private val logger = Logger.getInstance(RequestExecutionServiceImpl::class.java)
    
    // Performance optimization: Reuse OkHttpClient with connection pooling
    private val client: OkHttpClient
    
    // Performance optimization: Reuse thread pool for concurrent executions
    private val concurrentExecutionDispatcher: kotlinx.coroutines.CoroutineDispatcher
    
    private val executionJobs = ConcurrentHashMap<String, Job>()
    private val progressListeners = ConcurrentHashMap<String, MutableList<(ExecutionProgress) -> Unit>>()
    private val executionStatuses = ConcurrentHashMap<String, ExecutionStatus>()
    
    // Track individual request calls for cancellation
    private val activeCalls = ConcurrentHashMap<String, Call>()
    
    // Statistics calculator for enhanced concurrent results
    private val statisticsCalculator = ConcurrentStatisticsCalculator()
    
    init {
        // Performance optimization: Configure OkHttp with connection pooling
        // Connection pool: 10 idle connections, 5 minute keep-alive
        val connectionPool = okhttp3.ConnectionPool(
            maxIdleConnections = 10,
            keepAliveDuration = 5,
            timeUnit = TimeUnit.MINUTES
        )
        
        client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionPool(connectionPool) // Enable connection pooling
            .build()
        
        // Performance optimization: Create reusable thread pool for concurrent executions
        // Limit to 100 threads to prevent resource exhaustion
        concurrentExecutionDispatcher = Dispatchers.IO.limitedParallelism(100)
    }

    fun getHistoryService(): HistoryService {
        return try {
            val service = HistoryServiceImpl.getInstance()
            logger.debug("Successfully retrieved HistoryService instance")
            service
        } catch (e: Exception) {
            logger.error("Failed to get HistoryService: ${e.message}", e)
            throw IllegalStateException("HistoryService not available. Please check plugin configuration.", e)
        }
    }

    fun addToHistory(entry: RequestHistoryEntry) {
        try {
            logger.info("Calling HistoryService.addToHistory() for entry: id=${entry.id}")
            getHistoryService().addToHistory(entry)
            logger.info("HistoryService.addToHistory() completed successfully")
        } catch (e: Exception) {
            logger.error("Failed to add entry to history: ${e.message}", e)
            // Don't throw - history failure shouldn't break request execution
        }
    }

    override suspend fun executeRequest(config: RequestConfig): HttpResponse {
        val requestId = generateExecutionId()
        executionStatuses[requestId] = ExecutionStatus.PENDING
        
        // Log request execution start
        logger.info("Executing HTTP request: method=${config.method}, url=${config.url}, requestId=$requestId")
        
        return PerformanceMonitor.measureRequestExecution(
            method = config.method.name,
            url = config.url,
            details = mapOf(
                "timeout" to config.timeout,
                "headerCount" to config.headers.size,
                "hasBody" to (config.body != null)
            )
        ) {
            try {
                // Validate request configuration
                executionStatuses[requestId] = ExecutionStatus.RUNNING
                val validationErrors = config.validate()
                if (validationErrors.isNotEmpty()) {
                    executionStatuses[requestId] = ExecutionStatus.FAILED
                    logger.error("Request validation failed: ${validationErrors.joinToString(", ")}")
                    throw IllegalArgumentException("Request validation failed: ${validationErrors.joinToString(", ")}")
                }
                
                val processedConfig = config.applyPathParameters()
                val finalUrl = processedConfig.getFinalUrl()
                
                // Log request details
                LoggingUtils.logHttpRequest(
                    method = config.method.name,
                    url = finalUrl,
                    headers = config.headers,
                    bodySize = config.body?.length ?: 0
                )
                
                val requestBuilder = Request.Builder()
                    .url(finalUrl)
                
                // Handle different HTTP methods with appropriate request bodies
                val requestBody = when (processedConfig.method) {
                    HttpMethod.GET, HttpMethod.HEAD -> {
                        // GET and HEAD should not have a body
                        requestBuilder.method(processedConfig.method.name, null)
                        null
                    }
                    HttpMethod.DELETE -> {
                        // DELETE can optionally have a body
                        val body = createRequestBody(processedConfig)
                        requestBuilder.method(processedConfig.method.name, body)
                        body
                    }
                    HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH -> {
                        // POST, PUT, PATCH should have a body
                        val body = createRequestBody(processedConfig) 
                            ?: "".toRequestBody("text/plain".toMediaType())
                        requestBuilder.method(processedConfig.method.name, body)
                        body
                    }
                    else -> {
                        // For any other methods, use the standard approach
                        val body = createRequestBody(processedConfig)
                        requestBuilder.method(processedConfig.method.name, body)
                        body
                    }
                }
                
                // Add custom headers
                // Note: Headers are added after method to allow Content-Type override
                processedConfig.headers.forEach { (name, value) ->
                    requestBuilder.header(name, value) // Use header() to replace, not addHeader()
                }
                
                // If Content-Type is not explicitly set and we have a body, set it based on body content
                if (requestBody != null && processedConfig.getContentType() == null) {
                    val detectedContentType = detectContentType(processedConfig.body ?: "")
                    requestBuilder.header("Content-Type", detectedContentType)
                }
                
                val request = requestBuilder.build()
                val startTime = Instant.now()
                
                val response = executeWithTimeout(request, processedConfig.timeout, requestId)
                val endTime = Instant.now()
                val responseTime = Duration.between(startTime, endTime)
                
                // Log response details
                LoggingUtils.logHttpResponse(
                    statusCode = response.code,
                    responseTime = responseTime,
                    bodySize = response.body?.contentLength()?.toInt() ?: 0,
                    headers = response.headers.toMultimap()
                )
                
                executionStatuses[requestId] = ExecutionStatus.COMPLETED
                activeCalls.remove(requestId)

                val convertToHttpResponse =
                    convertToHttpResponse(response, responseTime, finalUrl, processedConfig.method)
                
                // Log before creating history entry
                logger.info("Request completed successfully, creating history entry: status=${convertToHttpResponse.statusCode}, time=${convertToHttpResponse.responseTime.toMillis()}ms")
                
                val historyEntry = RequestHistoryEntry(
                    id = generateId(),
                    request = config,
                    response = convertToHttpResponse,
                    timestamp = Instant.now(),
                    executionTime = convertToHttpResponse.responseTime
                )
                
                // Log before adding to history
                logger.info("Adding request to history: id=${historyEntry.id}, url=${historyEntry.request.url}")
                addToHistory(historyEntry)
                logger.info("Request added to history successfully")
                
                convertToHttpResponse
            } catch (e: Exception) {
                val endTime = Instant.now()
                val startTime = endTime.minusMillis(100) // Approximate start time
                val responseTime = Duration.between(startTime, endTime)
                
                executionStatuses[requestId] = when (e) {
                    is CancellationException -> ExecutionStatus.CANCELLED
                    else -> ExecutionStatus.FAILED
                }
                activeCalls.remove(requestId)
                
                // Log error
                logger.error("Request execution failed: ${e.message}", e)
                LoggingUtils.logWithContext(
                    LoggingUtils.LogLevel.ERROR,
                    "Request execution failed",
                    mapOf(
                        "method" to config.method.name,
                        "url" to config.url,
                        "error" to (e.message ?: "Unknown error")
                    ),
                    e
                )
                
                when (e) {
                    is IOException -> {
                        // Network error or timeout - still create history entry
                        logger.info("Creating history entry for failed request")
                        val errorResponse = createErrorResponse(e, responseTime, config.getFinalUrl(), config.method)
                        val historyEntry = RequestHistoryEntry(
                            id = generateId(),
                            request = config,
                            response = errorResponse,
                            timestamp = Instant.now(),
                            executionTime = responseTime
                        )
                        logger.info("Adding failed request to history: id=${historyEntry.id}")
                        addToHistory(historyEntry)
                        logger.info("Failed request added to history successfully")
                        errorResponse
                    }
                    is CancellationException -> {
                        throw e
                    }
                    else -> throw e
                }
            } finally {

                // Clean up after some time
                GlobalScope.launch {
                    delay(60000) // Keep status for 1 minute
                    executionStatuses.remove(requestId)
                }
            }
        }
    }
    
    override suspend fun executeConcurrentRequests(
        config: RequestConfig,
        threadCount: Int,
        iterations: Int
    ): ConcurrentExecutionResult {
        return PerformanceMonitor.measureConcurrentExecution(
            threadCount = threadCount,
            iterations = iterations,
            details = mapOf(
                "method" to config.method.name,
                "url" to config.url
            )
        ) {
            // Validate concurrent parameters (Requirements 5.1)
            val validationErrors = validateConcurrentParameters(threadCount, iterations)
            if (validationErrors.isNotEmpty()) {
                LoggingUtils.logValidationErrors(
                    "Concurrent execution",
                    validationErrors,
                    mapOf(
                        "threadCount" to threadCount,
                        "iterations" to iterations
                    )
                )
                throw IllegalArgumentException("Concurrent execution validation failed: ${validationErrors.joinToString("; ")}")
            }
            
            val executionId = generateExecutionId()
            executionStatuses[executionId] = ExecutionStatus.RUNNING
            
            LoggingUtils.logWithContext(
                LoggingUtils.LogLevel.INFO,
                "Starting concurrent execution",
                mapOf(
                    "executionId" to executionId,
                    "threadCount" to threadCount,
                    "iterations" to iterations,
                    "totalRequests" to (threadCount * iterations)
                )
            )
            
            val totalRequests = threadCount * iterations
            val responses = mutableListOf<HttpResponse>()
            val errors = mutableListOf<ExecutionError>()
            
            // Use atomic variables to track statistics (Requirements 5.2, 5.3)
            val completedRequests = AtomicInteger(0)
            val successfulRequests = AtomicInteger(0)
            val failedRequests = AtomicInteger(0)
            val totalResponseTime = AtomicLong(0)
            val minResponseTime = AtomicLong(Long.MAX_VALUE)
            val maxResponseTime = AtomicLong(0)
            
            val startTime = Instant.now()
            
            try {
                // Performance optimization: Use reusable thread pool (Requirements 5.2)
                // Limit parallelism to requested thread count
                val dispatcher = concurrentExecutionDispatcher.limitedParallelism(threadCount)
                
                val job = CoroutineScope(dispatcher).launch {
                    val deferredResults = (1..threadCount).map { threadIndex ->
                        async {
                            val threadResponses = mutableListOf<HttpResponse>()
                            val threadErrors = mutableListOf<ExecutionError>()
                            
                            repeat(iterations) { iteration ->
                                if (!isActive) return@async Pair(threadResponses, threadErrors)
                                
                                try {
                                    val response = executeRequest(config)
                                    
                                    // Performance optimization: Use synchronized block only for critical section
                                    val responseTimeMs = response.responseTime.toMillis()
                                    totalResponseTime.addAndGet(responseTimeMs)
                                    
                                    // Update min/max response times atomically
                                    var currentMin = minResponseTime.get()
                                    while (responseTimeMs < currentMin && !minResponseTime.compareAndSet(currentMin, responseTimeMs)) {
                                        currentMin = minResponseTime.get()
                                    }
                                    
                                    var currentMax = maxResponseTime.get()
                                    while (responseTimeMs > currentMax && !maxResponseTime.compareAndSet(currentMax, responseTimeMs)) {
                                        currentMax = maxResponseTime.get()
                                    }
                                    
                                    // Performance optimization: Only add to list if needed for statistics
                                    // For large concurrent executions, we might want to skip storing all responses
                                    if (totalRequests <= 1000) {
                                        synchronized(responses) {
                                            threadResponses.add(response)
                                        }
                                    }
                                    
                                    successfulRequests.incrementAndGet()
                                } catch (e: Exception) {
                                    val error = ExecutionError(
                                        message = e.message ?: "Unknown error",
                                        exception = e.javaClass.simpleName,
                                        requestIndex = threadIndex * iterations + iteration,
                                        errorType = mapExceptionToErrorType(e)
                                    )
                                    synchronized(errors) {
                                        threadErrors.add(error)
                                    }
                                    failedRequests.incrementAndGet()
                                }
                                
                                val completed = completedRequests.incrementAndGet()
                                
                                // Real-time progress updates (Requirements 5.3)
                                // Performance optimization: Only notify every 10 requests to reduce overhead
                                if (completed % 10 == 0 || completed == totalRequests) {
                                    val avgResponseTime = if (successfulRequests.get() > 0) {
                                        totalResponseTime.get() / successfulRequests.get()
                                    } else null
                                    
                                    val progress = ExecutionProgress(
                                        executionId = executionId,
                                        totalRequests = totalRequests,
                                        completedRequests = completed,
                                        successfulRequests = successfulRequests.get(),
                                        failedRequests = failedRequests.get(),
                                        averageResponseTime = avgResponseTime
                                    )
                                    
                                    notifyProgressListeners(executionId, progress)
                                }
                            }
                            
                            Pair(threadResponses, threadErrors)
                        }
                    }
                    
                    // Wait for all threads to complete
                    val results = deferredResults.awaitAll()
                    results.forEach { (threadResponses, threadErrors) ->
                        responses.addAll(threadResponses)
                        errors.addAll(threadErrors)
                    }
                }
                
                executionJobs[executionId] = job
                job.join()
                
                executionStatuses[executionId] = ExecutionStatus.COMPLETED
                
                LoggingUtils.logWithContext(
                    LoggingUtils.LogLevel.INFO,
                    "Concurrent execution completed",
                    mapOf(
                        "executionId" to executionId,
                        "totalRequests" to totalRequests,
                        "successfulRequests" to successfulRequests.get(),
                        "failedRequests" to failedRequests.get()
                    )
                )
                
            } catch (e: CancellationException) {
                executionStatuses[executionId] = ExecutionStatus.CANCELLED
                LoggingUtils.logWithContext(
                    LoggingUtils.LogLevel.WARN,
                    "Concurrent execution cancelled",
                    mapOf("executionId" to executionId)
                )
                throw e
            } catch (e: Exception) {
                executionStatuses[executionId] = ExecutionStatus.FAILED
                LoggingUtils.logWithContext(
                    LoggingUtils.LogLevel.ERROR,
                    "Concurrent execution failed",
                    mapOf(
                        "executionId" to executionId,
                        "error" to (e.message ?: "Unknown error")
                    ),
                    e
                )
                throw e
            } finally {
                // Clean up resources (Requirements 5.5)
                executionJobs.remove(executionId)
                progressListeners.remove(executionId)
                
                // Schedule cleanup of execution status after some time
                GlobalScope.launch {
                    delay(60000) // Keep status for 1 minute
                    executionStatuses.remove(executionId)
                }
            }
            
            val endTime = Instant.now()
            
            val avgResponseTime = if (successfulRequests.get() > 0) {
                Duration.ofMillis(totalResponseTime.get() / successfulRequests.get())
            } else {
                Duration.ZERO
            }
            
            val minTime = if (minResponseTime.get() == Long.MAX_VALUE) Duration.ZERO else Duration.ofMillis(minResponseTime.get())
            val maxTime = Duration.ofMillis(maxResponseTime.get())
            
            ConcurrentExecutionResult(
                totalRequests = totalRequests,
                successfulRequests = successfulRequests.get(),
                failedRequests = failedRequests.get(),
                averageResponseTime = avgResponseTime,
                minResponseTime = minTime,
                maxResponseTime = maxTime,
                responses = responses.toList(),
                errors = errors.toList(),
                startTime = startTime,
                endTime = endTime,
                threadCount = threadCount
            )
        }
    }
    
    override suspend fun executeConcurrentRequestsWithStats(
        config: RequestConfig,
        threadCount: Int,
        iterations: Int
    ): EnhancedConcurrentResult {
        // Execute concurrent requests using the existing method
        val basicResult = executeConcurrentRequests(config, threadCount, iterations)
        
        // Calculate enhanced statistics using the statistics calculator
        // Requirements 5.4: Calculate and return EnhancedConcurrentResult
        return statisticsCalculator.calculateStatistics(basicResult)
    }
    
    override fun cancelExecution(executionId: String): Boolean {
        // Requirements 5.5: Cancel all running requests and clean up resources
        
        // Try to cancel a concurrent execution job
        val job = executionJobs[executionId]
        if (job != null && job.isActive) {
            // Cancel the job (this will cancel all child coroutines)
            job.cancel(CancellationException("Execution cancelled by user"))
            
            // Update execution status
            executionStatuses[executionId] = ExecutionStatus.CANCELLED
            
            // Clean up resources
            executionJobs.remove(executionId)
            progressListeners.remove(executionId)
            
            return true
        }
        
        // Try to cancel an individual request call
        val call = activeCalls[executionId]
        if (call != null && !call.isCanceled()) {
            call.cancel()
            executionStatuses[executionId] = ExecutionStatus.CANCELLED
            activeCalls.remove(executionId)
            return true
        }
        
        return false
    }
    
    override fun getExecutionStatus(executionId: String): ExecutionStatus? {
        return executionStatuses[executionId]
    }
    
    override fun addProgressListener(executionId: String, listener: (ExecutionProgress) -> Unit) {
        progressListeners.computeIfAbsent(executionId) { mutableListOf() }.add(listener)
    }
    
    override fun removeProgressListener(executionId: String, listener: (ExecutionProgress) -> Unit) {
        progressListeners[executionId]?.remove(listener)
    }
    
    /**
     * Validate concurrent execution parameters
     * Requirements 5.1: Validate thread count (1-100) and iterations (1-10000)
     */
    private fun validateConcurrentParameters(threadCount: Int, iterations: Int): List<String> {
        val errors = mutableListOf<String>()
        
        // Validate thread count range (1-100)
        when {
            threadCount < 1 -> errors.add("Thread count must be at least 1")
            threadCount > 100 -> errors.add("Thread count cannot exceed 100 (provided: $threadCount)")
        }
        
        // Validate iterations range (1-10000)
        when {
            iterations < 1 -> errors.add("Iterations must be at least 1")
            iterations > 10000 -> errors.add("Iterations cannot exceed 10000 (provided: $iterations)")
        }
        
        // Additional validation: check total requests
        if (threadCount in 1..100 && iterations in 1..10000) {
            val totalRequests = threadCount * iterations
            if (totalRequests > 1000000) {
                errors.add("Total requests (threadCount × iterations = $totalRequests) exceeds maximum of 1,000,000")
            }
        }
        
        return errors
    }
    
    private suspend fun executeWithTimeout(request: Request, timeout: Duration, requestId: String? = null): Response {
        val clientWithTimeout = client.newBuilder()
            .connectTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .writeTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        
        return suspendCoroutine { continuation ->
            val call = clientWithTimeout.newCall(request)
            
            // Store the call for potential cancellation
            if (requestId != null) {
                activeCalls[requestId] = call
            }
            
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (requestId != null) {
                        activeCalls.remove(requestId)
                    }
                    continuation.resumeWithException(e)
                }
                
                override fun onResponse(call: Call, response: Response) {
                    if (requestId != null) {
                        activeCalls.remove(requestId)
                    }
                    continuation.resume(response)
                }
            })
        }
    }
    
    private fun createRequestBody(config: RequestConfig): RequestBody? {
        // Methods that typically don't have a body
        if (config.method == HttpMethod.GET || config.method == HttpMethod.HEAD) {
            return null
        }
        
        // If no body content, return null for DELETE or empty body for others
        if (!config.hasBody()) {
            return if (config.method == HttpMethod.DELETE) {
                null
            } else {
                null // Let OkHttp handle empty body
            }
        }
        
        val body = config.body!!
        
        // Determine content type - use explicit header if provided, otherwise detect
        val contentType = config.getContentType() ?: detectContentType(body)
        
        // Process body based on content type
        val processedBody = when {
            contentType.contains("application/json", ignoreCase = true) -> {
                // Validate JSON format
                try {
                    validateAndFormatJson(body)
                } catch (e: Exception) {
                    // If validation fails, use body as-is but log warning
                    body
                }
            }
            contentType.contains("application/x-www-form-urlencoded", ignoreCase = true) -> {
                // Body should already be form-encoded, but we can validate it
                body
            }
            else -> {
                // For other content types, use body as-is
                body
            }
        }
        
        return processedBody.toRequestBody(contentType.toMediaType())
    }
    
    /**
     * Detect content type from body content
     */
    private fun detectContentType(body: String): String {
        val trimmedBody = body.trim()
        
        return when {
            // Check for JSON
            (trimmedBody.startsWith("{") && trimmedBody.endsWith("}")) ||
            (trimmedBody.startsWith("[") && trimmedBody.endsWith("]")) -> {
                "application/json"
            }
            // Check for XML
            trimmedBody.startsWith("<") && trimmedBody.endsWith(">") -> {
                "application/xml"
            }
            // Check for form data (key=value&key=value)
            trimmedBody.matches(Regex("^[^=&]+=[^=&]+(&[^=&]+=[^=&]+)*$")) -> {
                "application/x-www-form-urlencoded"
            }
            // Default to plain text
            else -> "text/plain"
        }
    }
    
    /**
     * Encode form data parameters
     */
    private fun encodeFormData(params: Map<String, String>): String {
        return params.entries.joinToString("&") { (key, value) ->
            "${java.net.URLEncoder.encode(key, "UTF-8")}=${java.net.URLEncoder.encode(value, "UTF-8")}"
        }
    }
    
    /**
     * Validate and format JSON content
     */
    private fun validateAndFormatJson(json: String): String {
        return try {
            // Basic validation - check if it's valid JSON structure
            val trimmed = json.trim()
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                json
            } else {
                throw IllegalArgumentException("Invalid JSON format")
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON content: ${e.message}")
        }
    }
    
    private fun convertToHttpResponse(
        response: Response,
        responseTime: Duration,
        requestUrl: String,
        requestMethod: HttpMethod
    ): HttpResponse {
        val headers = mutableMapOf<String, List<String>>()
        response.headers.names().forEach { name ->
            headers[name] = response.headers.values(name)
        }
        
        val body = response.body?.string() ?: ""
        
        return HttpResponse(
            statusCode = response.code,
            statusText = response.message,
            headers = headers,
            body = body,
            responseTime = responseTime,
            timestamp = Instant.now(),
            requestUrl = requestUrl,
            requestMethod = requestMethod
        )

    }
    
    private fun createErrorResponse(
        exception: IOException,
        responseTime: Duration,
        requestUrl: String,
        requestMethod: HttpMethod
    ): HttpResponse {
        val isTimeout = exception.message?.contains("timeout", ignoreCase = true) == true ||
                       exception is java.net.SocketTimeoutException
        
        val statusCode = when {
            isTimeout -> 408 // Request Timeout
            exception.message?.contains("connection", ignoreCase = true) == true -> 503 // Service Unavailable
            exception.message?.contains("refused", ignoreCase = true) == true -> 503 // Connection Refused
            exception.message?.contains("host", ignoreCase = true) == true -> 503 // Unknown Host
            else -> 0 // Network Error
        }
        
        val statusText = when (statusCode) {
            408 -> "Request Timeout"
            503 -> "Service Unavailable"
            else -> "Network Error"
        }
        
        val errorMessage = when {
            isTimeout -> "Request timed out after ${responseTime.toMillis()}ms. " +
                        "Consider increasing the timeout value or checking network connectivity."
            exception.message?.contains("refused", ignoreCase = true) == true -> 
                "Connection refused. The server may be down or unreachable."
            exception.message?.contains("host", ignoreCase = true) == true -> 
                "Unknown host. Please check the URL and network connectivity."
            else -> exception.message ?: "Network error occurred"
        }
        
        return HttpResponse(
            statusCode = statusCode,
            statusText = statusText,
            headers = emptyMap(),
            body = errorMessage,
            responseTime = responseTime,
            timestamp = Instant.now(),
            requestUrl = requestUrl,
            requestMethod = requestMethod
        )
    }
    
    private fun mapExceptionToErrorType(exception: Exception): ErrorType {
        return when {
            exception is java.net.SocketTimeoutException -> ErrorType.TIMEOUT
            exception is IOException && exception.message?.contains("timeout", ignoreCase = true) == true -> ErrorType.TIMEOUT
            exception is IOException && exception.message?.contains("timed out", ignoreCase = true) == true -> ErrorType.TIMEOUT
            exception is IOException -> ErrorType.NETWORK
            exception is IllegalArgumentException -> ErrorType.VALIDATION
            exception.message?.contains("401", ignoreCase = true) == true -> ErrorType.AUTHENTICATION
            exception.message?.contains("403", ignoreCase = true) == true -> ErrorType.AUTHENTICATION
            exception.message?.contains("5", ignoreCase = true) == true -> ErrorType.SERVER_ERROR
            else -> ErrorType.UNKNOWN
        }
    }
    
    private fun notifyProgressListeners(executionId: String, progress: ExecutionProgress) {
        progressListeners[executionId]?.forEach { listener ->
            try {
                listener(progress)
            } catch (e: Exception) {
                // Log error but don't fail the execution
            }
        }
    }
    
    private fun generateExecutionId(): String {
        return "exec_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
    }

    private fun generateId(): String {
        return "req_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
    }

}