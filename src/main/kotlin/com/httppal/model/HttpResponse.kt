package com.httppal.model

import java.time.Duration
import java.time.Instant

/**
 * Represents an HTTP response
 */
data class HttpResponse(
    val statusCode: Int,
    val statusText: String,
    val headers: Map<String, List<String>>,
    val body: String,
    val responseTime: Duration,
    val timestamp: Instant = Instant.now(),
    val requestUrl: String? = null,
    val requestMethod: HttpMethod? = null
) {
    /**
     * Check if the response indicates success (2xx status codes)
     */
    fun isSuccessful(): Boolean = statusCode in 200..299
    
    /**
     * Check if the response indicates client error (4xx status codes)
     */
    fun isClientError(): Boolean = statusCode in 400..499
    
    /**
     * Check if the response indicates server error (5xx status codes)
     */
    fun isServerError(): Boolean = statusCode in 500..599
    
    /**
     * Check if the response indicates redirection (3xx status codes)
     */
    fun isRedirection(): Boolean = statusCode in 300..399
    
    /**
     * Get content type from headers
     */
    fun getContentType(): String? {
        return headers.entries
            .find { it.key.equals("content-type", ignoreCase = true) }
            ?.value?.firstOrNull()
    }
    
    /**
     * Get content length from headers
     */
    fun getContentLength(): Long? {
        return headers.entries
            .find { it.key.equals("content-length", ignoreCase = true) }
            ?.value?.firstOrNull()?.toLongOrNull()
    }
    
    /**
     * Get server information from headers
     */
    fun getServer(): String? {
        return headers.entries
            .find { it.key.equals("server", ignoreCase = true) }
            ?.value?.firstOrNull()
    }
    
    /**
     * Check if response body is JSON
     */
    fun isJson(): Boolean {
        val contentType = getContentType()?.lowercase()
        return contentType?.contains("application/json") == true ||
               contentType?.contains("text/json") == true ||
               (body.trimStart().startsWith("{") || body.trimStart().startsWith("["))
    }
    
    /**
     * Check if response body is XML
     */
    fun isXml(): Boolean {
        val contentType = getContentType()?.lowercase()
        return contentType?.contains("application/xml") == true ||
               contentType?.contains("text/xml") == true ||
               body.trimStart().startsWith("<")
    }
    
    /**
     * Check if response body is HTML
     */
    fun isHtml(): Boolean {
        val contentType = getContentType()?.lowercase()
        return contentType?.contains("text/html") == true ||
               body.trimStart().lowercase().startsWith("<!doctype html") ||
               body.trimStart().lowercase().startsWith("<html")
    }
    
    /**
     * Check if response body is plain text
     */
    fun isPlainText(): Boolean {
        val contentType = getContentType()?.lowercase()
        return contentType?.contains("text/plain") == true
    }
    
    /**
     * Get response size in bytes
     */
    fun getBodySize(): Int = body.toByteArray().size
    
    /**
     * Get status category description
     */
    fun getStatusCategory(): String {
        return when {
            isSuccessful() -> "Success"
            isClientError() -> "Client Error"
            isServerError() -> "Server Error"
            isRedirection() -> "Redirection"
            else -> "Informational"
        }
    }
    
    /**
     * Get display summary
     */
    fun getDisplaySummary(): String {
        val method = requestMethod?.name ?: "REQUEST"
        val url = requestUrl ?: "Unknown URL"
        return "$method $url - $statusCode $statusText (${responseTime.toMillis()}ms)"
    }
    
    // Removed formatJson, formatXml, and getFormattedBody methods as they are redundant
    // Use ResponseFormatter for formatting instead
}