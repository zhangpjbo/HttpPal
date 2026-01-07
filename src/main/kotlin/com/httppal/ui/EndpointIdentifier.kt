package com.httppal.ui

import com.httppal.model.DiscoveredEndpoint
import com.httppal.model.HttpMethod

/**
 * 端点唯一标识符
 * 
 * 用于在刷新操作后识别和恢复选中的端点
 * 支持精确匹配、模糊匹配和标签匹配三种策略
 */
data class EndpointIdentifier(
    val path: String,
    val method: HttpMethod,
    val className: String,
    val methodName: String,
    val tags: List<String>
) {
    /**
     * 精确匹配：路径和HTTP方法完全一致
     */
    fun exactMatch(endpoint: DiscoveredEndpoint): Boolean {
        return endpoint.path == path && endpoint.method == method
    }
    
    /**
     * 模糊匹配：类名和方法名一致
     */
    fun fuzzyMatch(endpoint: DiscoveredEndpoint): Boolean {
        return endpoint.className == className && endpoint.methodName == methodName
    }
    
    /**
     * 标签匹配：至少有一个共同的标签
     */
    fun tagMatch(endpoint: DiscoveredEndpoint): Boolean {
        if (tags.isEmpty() || endpoint.tags.isEmpty()) {
            return false
        }
        return tags.any { tag -> endpoint.tags.contains(tag) }
    }
    
    companion object {
        /**
         * 从 DiscoveredEndpoint 创建标识符
         */
        fun from(endpoint: DiscoveredEndpoint): EndpointIdentifier {
            return EndpointIdentifier(
                path = endpoint.path,
                method = endpoint.method,
                className = endpoint.className,
                methodName = endpoint.methodName,
                tags = endpoint.tags
            )
        }
    }
}
