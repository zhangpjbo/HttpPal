package com.httppal.ui

import com.httppal.model.DiscoveredEndpoint

/**
 * 自动发现视图的筛选策略
 * 
 * 按路径、HTTP方法和描述进行筛选
 */
class AutoDiscoveryFilterStrategy : FilterStrategy {
    override fun matches(endpoint: DiscoveredEndpoint, keywords: List<String>): Boolean {
        if (keywords.isEmpty()) {
            return true
        }
        
        // 所有关键词都必须匹配（AND逻辑）
        return keywords.all { keyword ->
            // 在路径、方法名或描述中查找关键词
            containsKeyword(endpoint.path, keyword) ||
            containsKeyword(endpoint.method.name, keyword) ||
            containsKeyword(endpoint.summary, keyword)
        }
    }
}
