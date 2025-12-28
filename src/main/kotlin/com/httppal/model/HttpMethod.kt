package com.httppal.model

/**
 * HTTP methods supported by the HttpPal plugin
 */
enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS;
    
    companion object {
        fun fromString(method: String): HttpMethod? {
            return try {
                valueOf(method.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}