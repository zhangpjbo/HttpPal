package com.httppal.ui

import com.httppal.model.DiscoveredEndpoint

/**
 * 树节点结构
 * 
 * 表示不同视图模式下的树层次结构
 */
sealed class TreeNodeStructure {
    /**
     * 自动发现视图节点结构
     * 按类名分组端点
     */
    data class AutoDiscoveryNode(
        val groupedByClass: Map<String, List<DiscoveredEndpoint>>
    ) : TreeNodeStructure()
    
    /**
     * Swagger/OpenAPI视图节点结构
     * 按标签分组端点（如果没有标签则使用类名）
     * 两层结构：第一层是 tag/类名，第二层是端点列表
     */
    data class SwaggerNode(
        val groupedByTags: Map<String, List<DiscoveredEndpoint>>
    ) : TreeNodeStructure()
    
    /**
     * 类名方法名视图节点结构
     * 按类名分组，然后按方法名组织端点
     */
    data class ClassMethodNode(
        val groupedByClass: Map<String, Map<String, DiscoveredEndpoint>>
    ) : TreeNodeStructure()
}
