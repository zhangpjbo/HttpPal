package com.httppal.ui

/**
 * 端点树面板的视图模式
 * 
 * 定义了三种不同的端点组织和显示方式
 */
enum class ViewMode {
    /**
     * 自动发现视图（默认）
     * 显示通过代码扫描自动发现的API端点，按类分组
     */
    AUTO_DISCOVERY,
    
    /**
     * Swagger/OpenAPI视图
     * 按照Swagger/OpenAPI规范中的标签和接口描述结构展示端点
     */
    SWAGGER,
    
    /**
     * 类名方法名视图
     * 按照源代码中的类名和方法名结构展示端点
     */
    CLASS_METHOD
}
