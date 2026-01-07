package com.httppal.ui

import com.httppal.model.DiscoveredEndpoint

/**
 * 筛选策略接口
 * 
 * 定义了不同视图模式下的端点筛选逻辑
 */
interface FilterStrategy {
    /**
     * 判断端点是否匹配给定的关键词
     * 
     * @param endpoint 要检查的端点
     * @param keywords 关键词列表（所有关键词都必须匹配）
     * @return 如果端点匹配所有关键词则返回 true
     */
    fun matches(endpoint: DiscoveredEndpoint, keywords: List<String>): Boolean
    
    /**
     * 在文本中高亮匹配的关键词
     * 
     * @param text 原始文本
     * @param keywords 要高亮的关键词
     * @return 包含HTML高亮标记的文本
     */
    fun highlightMatches(text: String, keywords: List<String>): String {
        if (keywords.isEmpty()) {
            return text
        }
        
        var result = text
        keywords.forEach { keyword ->
            // 使用HTML标记高亮匹配的文本
            val regex = Regex(Regex.escape(keyword), RegexOption.IGNORE_CASE)
            result = regex.replace(result) { matchResult ->
                "<b style='background-color: yellow;'>${matchResult.value}</b>"
            }
        }
        return result
    }
    
    /**
     * 检查文本是否包含关键词（模糊匹配）
     */
    fun containsKeyword(text: String?, keyword: String): Boolean {
        if (text == null) return false
        return text.contains(keyword, ignoreCase = true)
    }
}
