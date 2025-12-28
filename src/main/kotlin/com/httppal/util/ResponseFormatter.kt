package com.httppal.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.httppal.model.HttpResponse
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource

/**
 * Content type enumeration for response formatting.
 * Implements requirement 10.1 for content type detection.
 */
enum class ContentType {
    JSON,
    XML,
    HTML,
    PLAIN_TEXT,
    BINARY,
    UNKNOWN
}

/**
 * Syntax highlighting information for formatted content.
 * Implements requirement 10.2 for syntax highlighting support.
 */
data class SyntaxHighlightInfo(
    val language: String,
    val highlightedContent: String
)

/**
 * Formatted HTTP response with enhanced display information.
 * Implements requirements 4.5, 10.1, 10.2 for comprehensive response formatting.
 */
data class FormattedHttpResponse(
    val response: HttpResponse,
    val formattedBody: String,
    val contentType: ContentType,
    val syntaxHighlighting: SyntaxHighlightInfo?
)

/**
 * ResponseFormatter provides content type detection, formatting, and syntax highlighting
 * for HTTP responses.
 * 
 * Implements requirements:
 * - 4.5: Response formatting and display
 * - 10.1: Content type detection and JSON/XML formatting
 * - 10.2: Syntax highlighting support
 */
class ResponseFormatter {
    
    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        enable(SerializationFeature.INDENT_OUTPUT)
    }
    
    /**
     * Format an HTTP response with content type detection, formatting, and syntax highlighting.
     * Implements requirements 4.5, 10.1, 10.2.
     * 
     * @param response The HTTP response to format
     * @return FormattedHttpResponse with formatted body and syntax highlighting
     */
    fun formatResponse(response: HttpResponse): FormattedHttpResponse {
        val contentType = detectContentType(response)
        
        val formattedBody = when (contentType) {
            ContentType.JSON -> formatJson(response.body)
            ContentType.XML -> formatXml(response.body)
            ContentType.HTML -> response.body // HTML preserved as-is for rendering
            ContentType.PLAIN_TEXT -> response.body
            ContentType.BINARY -> "[Binary content - ${response.getBodySize()} bytes]"
            ContentType.UNKNOWN -> response.body
        }
        
        val syntaxHighlighting = when (contentType) {
            ContentType.JSON -> createSyntaxHighlighting("json", formattedBody)
            ContentType.XML -> createSyntaxHighlighting("xml", formattedBody)
            ContentType.HTML -> createSyntaxHighlighting("html", formattedBody)
            else -> null
        }
        
        return FormattedHttpResponse(
            response = response,
            formattedBody = formattedBody,
            contentType = contentType,
            syntaxHighlighting = syntaxHighlighting
        )
    }
    
    /**
     * Detect content type from HTTP response.
     * Implements requirement 10.1 for content type detection.
     * 
     * Uses both Content-Type header and content analysis for accurate detection.
     * 
     * @param response The HTTP response to analyze
     * @return Detected ContentType
     */
    fun detectContentType(response: HttpResponse): ContentType {
        val contentTypeHeader = response.getContentType()?.lowercase()
        
        // Check Content-Type header first
        if (contentTypeHeader != null) {
            return when {
                contentTypeHeader.contains("application/json") || 
                contentTypeHeader.contains("text/json") -> ContentType.JSON
                
                contentTypeHeader.contains("application/xml") || 
                contentTypeHeader.contains("text/xml") -> ContentType.XML
                
                contentTypeHeader.contains("text/html") -> ContentType.HTML
                
                contentTypeHeader.contains("text/plain") -> ContentType.PLAIN_TEXT
                
                contentTypeHeader.contains("image/") ||
                contentTypeHeader.contains("application/octet-stream") ||
                contentTypeHeader.contains("application/pdf") -> ContentType.BINARY
                
                else -> detectFromContent(response.body)
            }
        }
        
        // Fallback to content analysis
        return detectFromContent(response.body)
    }
    
    /**
     * Detect content type by analyzing the response body content.
     * 
     * @param body The response body to analyze
     * @return Detected ContentType
     */
    private fun detectFromContent(body: String): ContentType {
        if (body.isEmpty()) {
            return ContentType.PLAIN_TEXT
        }
        
        val trimmedBody = body.trimStart()
        
        return when {
            // JSON detection
            trimmedBody.startsWith("{") || trimmedBody.startsWith("[") -> {
                if (isValidJson(body)) ContentType.JSON else ContentType.PLAIN_TEXT
            }
            
            // XML detection
            trimmedBody.startsWith("<") -> {
                when {
                    trimmedBody.lowercase().startsWith("<!doctype html") ||
                    trimmedBody.lowercase().startsWith("<html") -> ContentType.HTML
                    isValidXml(body) -> ContentType.XML
                    else -> ContentType.PLAIN_TEXT
                }
            }
            
            // Binary detection (non-printable characters)
            body.any { it.code < 32 && it != '\n' && it != '\r' && it != '\t' } -> ContentType.BINARY
            
            else -> ContentType.PLAIN_TEXT
        }
    }
    
    /**
     * Format JSON content with proper indentation.
     * Implements requirement 10.1 for JSON formatting.
     * 
     * @param jsonString The JSON string to format
     * @return Formatted JSON string, or original if formatting fails
     */
    fun formatJson(jsonString: String): String {
        return try {
            val jsonNode = objectMapper.readTree(jsonString)
            objectMapper.writeValueAsString(jsonNode)
        } catch (e: Exception) {
            // If JSON parsing fails, return original content
            jsonString
        }
    }
    
    /**
     * Format XML content with proper indentation.
     * Implements requirement 10.1 for XML formatting.
     * 
     * @param xmlString The XML string to format
     * @return Formatted XML string, or original if formatting fails
     */
    fun formatXml(xmlString: String): String {
        return try {
            val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val document = documentBuilder.parse(InputSource(StringReader(xmlString)))
            
            val transformer = TransformerFactory.newInstance().newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            
            val source = DOMSource(document)
            val stringWriter = StringWriter()
            val result = StreamResult(stringWriter)
            transformer.transform(source, result)
            
            stringWriter.toString()
        } catch (e: Exception) {
            // If XML parsing fails, return original content
            xmlString
        }
    }
    
    /**
     * Create syntax highlighting information for formatted content.
     * Implements requirement 10.2 for syntax highlighting.
     * 
     * @param language The language identifier (json, xml, html)
     * @param content The formatted content
     * @return SyntaxHighlightInfo with language and content
     */
    private fun createSyntaxHighlighting(language: String, content: String): SyntaxHighlightInfo {
        return SyntaxHighlightInfo(
            language = language,
            highlightedContent = content
        )
    }
    
    /**
     * Validate if a string is valid JSON.
     * 
     * @param jsonString The string to validate
     * @return true if valid JSON, false otherwise
     */
    private fun isValidJson(jsonString: String): Boolean {
        return try {
            objectMapper.readTree(jsonString)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validate if a string is valid XML.
     * 
     * @param xmlString The string to validate
     * @return true if valid XML, false otherwise
     */
    private fun isValidXml(xmlString: String): Boolean {
        return try {
            val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            documentBuilder.parse(InputSource(StringReader(xmlString)))
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Format plain text content for display.
     * Implements requirement 10.3 for plain text display.
     * 
     * @param text The plain text to format
     * @return Formatted plain text (unchanged)
     */
    fun formatPlainText(text: String): String {
        return text
    }
    
    /**
     * Process HTML content for display with both preview and source view options.
     * Implements requirement 10.4 for HTML content handling.
     * 
     * @param htmlString The HTML string to process
     * @return Pair of (rendered preview, source code)
     */
    fun processHtmlContent(htmlString: String): Pair<String, String> {
        // Return both the original HTML for source view and formatted for preview
        val sourceView = htmlString
        val previewHtml = htmlString // In a full implementation, this would be rendered
        
        return Pair(previewHtml, sourceView)
    }
    
    /**
     * Check if response content is large and requires streaming.
     * Implements requirement 10.5 for large response handling.
     * 
     * @param response The HTTP response to check
     * @param thresholdBytes The size threshold in bytes (default 10MB)
     * @return true if content should be streamed, false otherwise
     */
    fun shouldStreamContent(response: HttpResponse, thresholdBytes: Int = 10 * 1024 * 1024): Boolean {
        val contentLength = response.getContentLength()
        if (contentLength != null) {
            return contentLength > thresholdBytes
        }
        
        // Fallback to actual body size
        return response.getBodySize() > thresholdBytes
    }
    
    /**
     * Format large response content with streaming support.
     * Implements requirement 10.5 for large response streaming.
     * 
     * @param response The HTTP response with large content
     * @param maxPreviewSize Maximum size to preview (default 1MB)
     * @return Formatted content with truncation notice if needed
     */
    fun formatLargeResponse(response: HttpResponse, maxPreviewSize: Int = 1024 * 1024): String {
        val bodySize = response.getBodySize()
        
        if (bodySize <= maxPreviewSize) {
            // Content is within preview size, format normally
            return formatResponse(response).formattedBody
        }
        
        // Content is too large, show preview with truncation notice
        val preview = response.body.take(maxPreviewSize)
        val remainingBytes = bodySize - maxPreviewSize
        val remainingKB = remainingBytes / 1024
        val remainingMB = remainingKB / 1024
        
        val truncationNotice = when {
            remainingMB > 0 -> "\n\n[Content truncated - ${remainingMB}MB remaining. Full content not displayed to prevent memory issues.]"
            remainingKB > 0 -> "\n\n[Content truncated - ${remainingKB}KB remaining. Full content not displayed to prevent memory issues.]"
            else -> "\n\n[Content truncated - ${remainingBytes} bytes remaining. Full content not displayed to prevent memory issues.]"
        }
        
        return preview + truncationNotice
    }
    
    /**
     * Get content preview for large responses without full formatting.
     * Implements requirement 10.5 for efficient large content handling.
     * 
     * @param response The HTTP response
     * @param previewLines Number of lines to preview (default 100)
     * @return Preview string with line count information
     */
    fun getContentPreview(response: HttpResponse, previewLines: Int = 100): String {
        val lines = response.body.lines()
        
        if (lines.size <= previewLines) {
            return response.body
        }
        
        val preview = lines.take(previewLines).joinToString("\n")
        val remainingLines = lines.size - previewLines
        
        return "$preview\n\n[Preview showing first $previewLines lines. $remainingLines more lines available.]"
    }
}
