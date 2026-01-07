package com.httppal.ui

/**
 * 筛选状态
 * 
 * 维护当前的筛选配置，包括筛选文本和解析后的关键词
 */
data class FilterState(
    val filterText: String = "",
    val keywords: List<String> = emptyList(),
    val isActive: Boolean = false
) {
    /**
     * 从筛选文本解析关键词
     * 支持空格分隔的多关键词搜索
     */
    fun parseKeywords(text: String): List<String> {
        return text.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotEmpty() }
    }
    
    companion object {
        /**
         * 创建活动的筛选状态
         */
        fun active(text: String): FilterState {
            val trimmedText = text.trim()
            return FilterState(
                filterText = trimmedText,
                keywords = trimmedText.split("\\s+".toRegex()).filter { it.isNotEmpty() },
                isActive = trimmedText.isNotEmpty()
            )
        }
        
        /**
         * 创建非活动的筛选状态
         */
        fun inactive(): FilterState {
            return FilterState(
                filterText = "",
                keywords = emptyList(),
                isActive = false
            )
        }
    }
}
