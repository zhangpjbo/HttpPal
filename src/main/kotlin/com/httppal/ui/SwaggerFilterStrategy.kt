package com.httppal.ui

import com.httppal.model.DiscoveredEndpoint

/**
 * Swagger/OpenAPI视图的筛选策略
 * 
 * 按类名、接口名、标签和描述进行筛选
 */
class SwaggerFilterStrategy : FilterStrategy {
    override fun matches(endpoint: DiscoveredEndpoint, keywords: List<String>): Boolean {
        if (keywords.isEmpty()) {
            return true
        }
        
        // 所有关键词都必须匹配（AND逻辑）
        return keywords.all { keyword ->
            // 在类名、接口名（operationId）、标签或描述中查找关键词
            containsKeyword(endpoint.className, keyword) ||
            containsKeyword(endpoint.operationId, keyword) ||
            endpoint.tags.any { tag -> containsKeyword(tag, keyword) } ||
            containsKeyword(endpoint.summary, keyword)
        }
    }
}
