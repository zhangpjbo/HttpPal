package com.httppal.service.impl

import com.httppal.model.HttpMethod
import com.httppal.model.RequestConfig
import com.httppal.service.ClipboardFormat
import com.httppal.service.CurlParserService
import com.httppal.service.ParseResult
import com.httppal.util.LoggingUtils
import com.intellij.openapi.components.Service
import java.time.Duration

/**
 * Implementation of CurlParserService for parsing cURL commands and raw HTTP requests
 */
@Service
class CurlParserServiceImpl : CurlParserService {
    
    override fun parseCurl(curlCommand: String): ParseResult<RequestConfig> {
        return try {
            val trimmed = curlCommand.trim()
            if (!trimmed.startsWith("curl", ignoreCase = true)) {
                LoggingUtils.logWarning("Not a valid cURL command")
                return ParseResult.Failure(listOf("Not a valid cURL command"))
            }
            
            // Extract URL
            val url = extractUrl(trimmed) ?: run {
                LoggingUtils.logWarning("Failed to extract URL from cURL command")
                return ParseResult.Failure(listOf("Failed to extract URL from cURL command"))
            }
            
            // Extract method (default to GET)
            val method = extractMethod(trimmed)
            
            // Extract headers
            val headers = extractHeaders(trimmed)
            
            // Extract body
            val body = extractBody(trimmed)
            
            val requestConfig = RequestConfig(
                method = method,
                url = url,
                headers = headers,
                body = body,
                timeout = Duration.ofSeconds(30),
                followRedirects = true
            )
            
            ParseResult.Success(requestConfig)
        } catch (e: Exception) {
            LoggingUtils.logError("Failed to parse cURL command", e)
            ParseResult.Failure(listOf("Failed to parse cURL command: ${e.message}"))
        }
    }
    
    override fun parseRawHttp(rawHttp: String): ParseResult<RequestConfig> {
        return try {
            val lines = rawHttp.trim().lines()
            if (lines.isEmpty()) {
                LoggingUtils.logWarning("Empty HTTP request")
                return ParseResult.Failure(listOf("Empty HTTP request"))
            }
            
            // Parse request line (e.g., "GET /api/users HTTP/1.1")
            val requestLine = lines[0].trim()
            val requestParts = requestLine.split("\\s+".toRegex())
            if (requestParts.size < 2) {
                LoggingUtils.logWarning("Invalid HTTP request line")
                return ParseResult.Failure(listOf("Invalid HTTP request line"))
            }
            
            val method = HttpMethod.fromString(requestParts[0]) ?: run {
                LoggingUtils.logWarning("Invalid HTTP method: ${requestParts[0]}")
                return ParseResult.Failure(listOf("Invalid HTTP method: ${requestParts[0]}"))
            }
            
            val path = requestParts[1]
            
            // Parse headers
            val headers = mutableMapOf<String, String>()
            var bodyStartIndex = 1
            
            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line.isEmpty()) {
                    bodyStartIndex = i + 1
                    break
                }
                
                val colonIndex = line.indexOf(':')
                if (colonIndex > 0) {
                    val headerName = line.substring(0, colonIndex).trim()
                    val headerValue = line.substring(colonIndex + 1).trim()
                    headers[headerName] = headerValue
                }
            }
            
            // Extract Host header to build full URL
            val host = headers["Host"] ?: headers["host"]
            val url = if (host != null) {
                val protocol = if (headers.containsKey("X-Forwarded-Proto") && 
                                   headers["X-Forwarded-Proto"] == "https") "https" else "http"
                "$protocol://$host$path"
            } else {
                path
            }
            
            // Parse body
            val body = if (bodyStartIndex < lines.size) {
                lines.subList(bodyStartIndex, lines.size).joinToString("\n").trim()
            } else {
                null
            }
            
            val requestConfig = RequestConfig(
                method = method,
                url = url,
                headers = headers,
                body = body?.takeIf { it.isNotEmpty() },
                timeout = Duration.ofSeconds(30),
                followRedirects = true
            )
            
            ParseResult.Success(requestConfig)
        } catch (e: Exception) {
            LoggingUtils.logError("Failed to parse raw HTTP request", e)
            ParseResult.Failure(listOf("Failed to parse raw HTTP request: ${e.message}"))
        }
    }

    
    override fun detectFormat(content: String): ClipboardFormat {
        val trimmed = content.trim()
        
        return when {
            trimmed.startsWith("curl", ignoreCase = true) -> ClipboardFormat.CURL
            trimmed.matches(Regex("^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+.+", RegexOption.IGNORE_CASE)) -> 
                ClipboardFormat.RAW_HTTP
            else -> ClipboardFormat.UNKNOWN
        }
    }
    
    override fun importFromClipboard(): ParseResult<RequestConfig> {
        return try {
            // Get clipboard content
            val clipboardContent = java.awt.Toolkit.getDefaultToolkit().systemClipboard.getContents(null)
            
            if (clipboardContent == null || !clipboardContent.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                return ParseResult.Failure(listOf("Clipboard is empty or contains unsupported data"))
            }
            
            val content = clipboardContent.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
            if (content.isNullOrEmpty()) {
                return ParseResult.Failure(listOf("Clipboard is empty"))
            }
            
            // Detect format and parse accordingly
            return when (detectFormat(content)) {
                ClipboardFormat.CURL -> {
                    parseCurl(content)
                }
                ClipboardFormat.RAW_HTTP -> {
                    parseRawHttp(content)
                }
                else -> {
                    ParseResult.Failure(listOf("Unsupported clipboard format. Supported formats: cURL command, raw HTTP request"))
                }
            }
        } catch (e: Exception) {
            ParseResult.Failure(listOf("Failed to read clipboard: ${e.message}"))
        }
    }
    
    /**
     * Extract URL from cURL command
     */
    private fun extractUrl(curlCommand: String): String? {
        // Try to find URL in quotes
        val quotedUrlPattern = Regex("""['"]([^'"]+)['"]""")
        val quotedMatch = quotedUrlPattern.find(curlCommand)
        if (quotedMatch != null) {
            val url = quotedMatch.groupValues[1]
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/")) {
                return url
            }
        }
        
        // Try to find URL without quotes (after curl command)
        val parts = curlCommand.split("\\s+".toRegex())
        for (i in 1 until parts.size) {
            val part = parts[i]
            if (part.startsWith("http://") || part.startsWith("https://") || 
                (part.startsWith("/") && !part.startsWith("-"))) {
                return part
            }
        }
        
        return null
    }
    
    /**
     * Extract HTTP method from cURL command
     */
    private fun extractMethod(curlCommand: String): HttpMethod {
        val methodPattern = Regex("""-X\s+([A-Z]+)""", RegexOption.IGNORE_CASE)
        val match = methodPattern.find(curlCommand)
        
        if (match != null) {
            val methodStr = match.groupValues[1]
            return HttpMethod.fromString(methodStr) ?: HttpMethod.GET
        }
        
        // Check for --request
        val requestPattern = Regex("""--request\s+([A-Z]+)""", RegexOption.IGNORE_CASE)
        val requestMatch = requestPattern.find(curlCommand)
        
        if (requestMatch != null) {
            val methodStr = requestMatch.groupValues[1]
            return HttpMethod.fromString(methodStr) ?: HttpMethod.GET
        }
        
        // Check if -d or --data is present (implies POST)
        if (curlCommand.contains(Regex("""-d\s+|--data\s+|--data-raw\s+"""))) {
            return HttpMethod.POST
        }
        
        return HttpMethod.GET
    }
    
    /**
     * Extract headers from cURL command
     */
    private fun extractHeaders(curlCommand: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        
        // Pattern for -H or --header
        val headerPattern = Regex("""(?:-H|--header)\s+['"]([^'"]+)['"]""")
        val matches = headerPattern.findAll(curlCommand)
        
        for (match in matches) {
            val headerLine = match.groupValues[1]
            val colonIndex = headerLine.indexOf(':')
            if (colonIndex > 0) {
                val name = headerLine.substring(0, colonIndex).trim()
                val value = headerLine.substring(colonIndex + 1).trim()
                headers[name] = value
            }
        }
        
        return headers
    }
    
    /**
     * Extract body from cURL command
     */
    private fun extractBody(curlCommand: String): String? {
        // Pattern for -d, --data, --data-raw, --data-binary
        val dataPattern = Regex("""(?:-d|--data|--data-raw|--data-binary)\s+['"]([^'"]+)['"]""")
        val match = dataPattern.find(curlCommand)
        
        if (match != null) {
            return match.groupValues[1]
        }
        
        // Try without quotes
        val dataPatternNoQuotes = Regex("""(?:-d|--data|--data-raw|--data-binary)\s+(\S+)""")
        val matchNoQuotes = dataPatternNoQuotes.find(curlCommand)
        
        if (matchNoQuotes != null) {
            return matchNoQuotes.groupValues[1]
        }
        
        return null
    }
}