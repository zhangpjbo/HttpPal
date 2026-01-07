package com.httppal.ui

import com.httppal.model.DiscoveredEndpoint

/**
 * 类名方法名视图的筛选策略
 * 
 * 按类名、方法名和路径进行筛选
 */
class ClassMethodFilterStrategy : FilterStrategy {
    override fun matches(endpoint: DiscoveredEndpoint, keywords: List<String>): Boolean {
        if (keywords.isEmpty()) {
            return true
        }
        
        // 所有关键词都必须匹配（AND逻辑）
        return keywords.all { keyword ->
            // 在类名、方法名或路径中查找关键词
            containsKeyword(endpoint.className, keyword) ||
            containsKeyword(endpoint.methodName, keyword) ||
            containsKeyword(endpoint.path, keyword)
        }
    }
}
