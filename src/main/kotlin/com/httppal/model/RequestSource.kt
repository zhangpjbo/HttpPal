package com.httppal.model

/**
 * Represents the source of a request in the request panel
 */
enum class RequestSource {
    /**
     * Manually created by user
     */
    MANUAL,
    
    /**
     * Loaded from scanned endpoint
     */
    SCANNED_ENDPOINT,
    
    /**
     * Loaded from history
     */
    HISTORY,
    
    /**
     * Loaded from favorites
     */
    FAVORITE,
    
    /**
     * Loaded from template
     */
    TEMPLATE,
    
    /**
     * Loaded from endpoint
     */
    ENDPOINT,
    
    /**
     * Loaded from clipboard
     */
    CLIPBOARD;

    /**
     * Get display name for UI
     */
    fun getDisplayName(): String {
        return when (this) {
            MANUAL -> "来源：手动创建"
            SCANNED_ENDPOINT -> "来源：扫描端点"
            HISTORY -> "来源：历史记录"
            FAVORITE -> "来源：收藏"
            TEMPLATE -> "来源：模板"
            ENDPOINT -> "来源：端点"
            CLIPBOARD -> "来源：剪贴板"
        }
    }
}