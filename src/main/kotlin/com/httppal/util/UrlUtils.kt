package com.httppal.util

import java.net.URI
import java.net.URISyntaxException

/**
 * Utility functions for URL validation and processing
 */
object UrlUtils {
    
    /**
     * Validate if a URL is safe and properly formatted
     */
    fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) {
            return false
        }
        
        // Check for potential path traversal
        if (url.contains("../") || url.contains("..\\") || url.startsWith("..")) {
            return false
        }
        
        // Allow environment variable placeholders
        if (url.contains("{{") && url.contains("}}")) {
            return true
        }
        
        return try {
            val uri = URI(url)
            val scheme = uri.scheme
            val host = uri.host
            
            // Check if scheme is valid (http or https for security)
            if (scheme != null && !scheme.matches(Regex("https?", RegexOption.IGNORE_CASE))) {
                // Allow relative URLs (no scheme)
                scheme == null || scheme.isEmpty()
            } else {
                // If scheme exists, host must also exist
                host != null
            }
        } catch (e: URISyntaxException) {
            false
        }
    }
    
    /**
     * Validate URL for file path safety
     */
    fun isSafeFilePath(filePath: String): Boolean {
        if (filePath.isBlank()) {
            return false
        }
        
        // Check for path traversal attempts
        if (filePath.contains("../") || filePath.contains("..\\") || 
            filePath.contains("%2e%2e%2f") || filePath.contains("..%2f")) {
            return false
        }
        
        return try {
            val file = java.io.File(filePath)
            // Check if the resolved path is within allowed boundaries
            val canonicalPath = file.canonicalPath
            val basePath = java.io.File(System.getProperty("user.home")).canonicalPath
            canonicalPath.startsWith(basePath)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Normalize URL by removing redundant components
     */
    fun normalizeUrl(url: String): String {
        return url.trim()
            .replace(Regex("//+"), "/") // Replace multiple slashes with single slash
            .replace(Regex("https?://"), { matchResult -> matchResult.value.lowercase() }) // Lowercase protocol
    }
    
    /**
     * Sanitize URL by removing potentially dangerous components
     */
    fun sanitizeUrl(url: String): String {
        // Remove potential script tags or other dangerous content
        var sanitized = url.replace(Regex("<script.*?>.*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("javascript:", RegexOption.IGNORE_CASE), "")
            .replace(Regex("data:", RegexOption.IGNORE_CASE), "")
            .replace(Regex("vbscript:", RegexOption.IGNORE_CASE), "")
        
        // Only allow safe characters in URLs
        sanitized = sanitized.filter { 
            it.isLetterOrDigit() || 
            it in listOf('/', ':', '?', '#', '[', ']', '@', '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=', '.', '-', '_', '~', '%') 
        }
        
        return sanitized
    }
    
    /**
     * Validate that URL doesn't contain potentially malicious content
     */
    fun isSafeUrlContent(url: String): Boolean {
        val dangerousPatterns = listOf(
            Regex("javascript:", RegexOption.IGNORE_CASE),
            Regex("data:", RegexOption.IGNORE_CASE),
            Regex("vbscript:", RegexOption.IGNORE_CASE),
            Regex("<script", RegexOption.IGNORE_CASE)
        )
        
        return dangerousPatterns.none { pattern -> pattern.containsMatchIn(url) }
    }
}