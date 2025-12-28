package com.httppal.model

import com.intellij.openapi.diagnostic.Logger
import java.time.Duration
import java.time.Instant

/**
 * Serializable wrapper for RequestHistoryEntry
 * Uses primitive types to avoid Instant/Duration serialization issues
 */
data class SerializableHistoryEntry(
    var id: String = "",
    var requestMethod: String = "",
    var requestUrl: String = "",
    var requestHeaders: Map<String, String> = emptyMap(),
    var requestBody: String? = null,
    var requestQueryParams: Map<String, String> = emptyMap(),
    var requestPathParams: Map<String, String> = emptyMap(),
    var responseStatusCode: Int = 0,
    var responseStatusText: String = "",
    var responseHeaders: Map<String, List<String>> = emptyMap(),
    var responseBody: String = "",
    var timestampMillis: Long = 0,
    var executionTimeMillis: Long = 0,
    var error: String? = null,
    var environment: String? = null
) {
    companion object {
        private val logger = Logger.getInstance(SerializableHistoryEntry::class.java)
        
        /**
         * Convert RequestHistoryEntry to SerializableHistoryEntry
         */
        fun fromHistoryEntry(entry: RequestHistoryEntry): SerializableHistoryEntry {
            return try {
                logger.debug("Converting RequestHistoryEntry to SerializableHistoryEntry: id=${entry.id}")
                
                SerializableHistoryEntry(
                    id = entry.id,
                    requestMethod = entry.request.method.name,
                    requestUrl = entry.request.url,
                    requestHeaders = entry.request.headers,
                    requestBody = entry.request.body,
                    requestQueryParams = entry.request.queryParameters,
                    requestPathParams = entry.request.pathParameters,
                    responseStatusCode = entry.response?.statusCode ?: 0,
                    responseStatusText = entry.response?.statusText ?: "",
                    responseHeaders = entry.response?.headers ?: emptyMap(),
                    responseBody = entry.response?.body ?: "",
                    timestampMillis = entry.timestamp.toEpochMilli(),
                    executionTimeMillis = entry.executionTime?.toMillis() ?: 0,
                    error = entry.error,
                    environment = entry.environment
                ).also {
                    logger.debug("Successfully converted RequestHistoryEntry: id=${entry.id}, url=${entry.request.url}")
                }
            } catch (e: Exception) {
                logger.error("Failed to convert RequestHistoryEntry to SerializableHistoryEntry: id=${entry.id}, error=${e.message}", e)
                throw IllegalArgumentException("Failed to serialize history entry: ${e.message}", e)
            }
        }
    }
    
    /**
     * Convert SerializableHistoryEntry to RequestHistoryEntry
     */
    fun toHistoryEntry(): RequestHistoryEntry {
        return try {
            logger.debug("Converting SerializableHistoryEntry to RequestHistoryEntry: id=$id")
            
            // Validate required fields
            if (id.isBlank()) {
                logger.warn("SerializableHistoryEntry has blank id, generating new one")
            }
            if (requestUrl.isBlank()) {
                logger.warn("SerializableHistoryEntry has blank URL: id=$id")
            }
            if (requestMethod.isBlank()) {
                logger.warn("SerializableHistoryEntry has blank method: id=$id, defaulting to GET")
            }
            
            // Parse HTTP method with fallback
            val method = try {
                HttpMethod.valueOf(requestMethod)
            } catch (e: IllegalArgumentException) {
                logger.warn("Invalid HTTP method '$requestMethod' for id=$id, defaulting to GET")
                HttpMethod.GET
            }
            
            val request = RequestConfig(
                method = method,
                url = requestUrl.ifBlank { "http://unknown" },
                headers = requestHeaders,
                body = requestBody,
                queryParameters = requestQueryParams,
                pathParameters = requestPathParams
            )
            
            // Create response only if we have a valid status code
            val response = if (responseStatusCode > 0) {
                try {
                    HttpResponse(
                        statusCode = responseStatusCode,
                        statusText = responseStatusText,
                        headers = responseHeaders,
                        body = responseBody,
                        responseTime = Duration.ofMillis(executionTimeMillis),
                        timestamp = Instant.ofEpochMilli(timestampMillis),
                        requestUrl = requestUrl,
                        requestMethod = method
                    )
                } catch (e: Exception) {
                    logger.warn("Failed to create HttpResponse for id=$id: ${e.message}")
                    null
                }
            } else {
                null
            }
            
            // Validate and create timestamp
            val timestamp = try {
                if (timestampMillis > 0) {
                    Instant.ofEpochMilli(timestampMillis)
                } else {
                    logger.warn("Invalid timestamp for id=$id, using current time")
                    Instant.now()
                }
            } catch (e: Exception) {
                logger.warn("Failed to parse timestamp for id=$id: ${e.message}, using current time")
                Instant.now()
            }
            
            // Create execution time
            val executionTime = if (executionTimeMillis > 0) {
                try {
                    Duration.ofMillis(executionTimeMillis)
                } catch (e: Exception) {
                    logger.warn("Failed to parse execution time for id=$id: ${e.message}")
                    null
                }
            } else {
                null
            }
            
            RequestHistoryEntry(
                id = id.ifBlank { java.util.UUID.randomUUID().toString() },
                request = request,
                response = response,
                timestamp = timestamp,
                executionTime = executionTime,
                error = error,
                environment = environment
            ).also {
                logger.debug("Successfully converted SerializableHistoryEntry: id=$id, url=$requestUrl")
            }
        } catch (e: Exception) {
            logger.error("Failed to convert SerializableHistoryEntry to RequestHistoryEntry: id=$id, error=${e.message}", e)
            
            // Return a minimal valid entry instead of throwing to prevent data loss
            logger.warn("Creating fallback RequestHistoryEntry for id=$id")
            RequestHistoryEntry(
                id = id.ifBlank { java.util.UUID.randomUUID().toString() },
                request = RequestConfig(
                    method = HttpMethod.GET,
                    url = requestUrl.ifBlank { "http://unknown" }
                ),
                response = null,
                timestamp = if (timestampMillis > 0) {
                    try {
                        Instant.ofEpochMilli(timestampMillis)
                    } catch (ex: Exception) {
                        Instant.now()
                    }
                } else {
                    Instant.now()
                },
                executionTime = null,
                error = "Failed to deserialize: ${e.message}",
                environment = environment
            )
        }
    }
}
