package com.httppal.model

/**
 * Represents an endpoint discovered from source code analysis
 */
data class DiscoveredEndpoint(
    val method: HttpMethod,
    val path: String,
    val className: String,
    val methodName: String,
    val parameters: List<EndpointParameter>,
    val sourceFile: String,
    val lineNumber: Int
) {
    /**
     * Convert to EndpointInfo for use in requests
     */
    fun toEndpointInfo(baseUrl: String? = null): EndpointInfo {
        return EndpointInfo(
            method = method,
            path = path,
            baseUrl = baseUrl,
            parameters = parameters,
            source = EndpointSource.DISCOVERED,
            sourceLocation = SourceLocation(
                fileName = sourceFile,
                className = className,
                methodName = methodName,
                lineNumber = lineNumber
            )
        )
    }
}