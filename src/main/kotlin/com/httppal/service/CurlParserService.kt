package com.httppal.service

import com.httppal.model.RequestConfig

/**
 * Service for parsing cURL commands and raw HTTP requests
 */
interface CurlParserService {
    
    /**
     * Parse cURL command to RequestConfig
     */
    fun parseCurl(curlCommand: String): ParseResult<RequestConfig>
    
    /**
     * Parse raw HTTP request to RequestConfig
     */
    fun parseRawHttp(rawHttp: String): ParseResult<RequestConfig>
    
    /**
     * Detect clipboard content format
     */
    fun detectFormat(content: String): ClipboardFormat
    
    /**
     * Import from clipboard (auto-detect format)
     */
    fun importFromClipboard(): ParseResult<RequestConfig>
}

/**
 * Result of parsing operation
 */
sealed class ParseResult<T> {
    data class Success<T>(val data: T) : ParseResult<T>()
    data class Failure<T>(val errors: List<String>) : ParseResult<T>()
}

/**
 * Clipboard content format
 */
enum class ClipboardFormat {
    CURL,
    RAW_HTTP,
    JSON,
    UNKNOWN;
    
    fun getDisplayName(): String {
        return when (this) {
            CURL -> "cURL 命令"
            RAW_HTTP -> "原始 HTTP 请求"
            JSON -> "JSON 数据"
            UNKNOWN -> "未知格式"
        }
    }
}
