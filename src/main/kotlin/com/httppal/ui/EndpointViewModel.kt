package com.httppal.ui

import com.httppal.model.DiscoveredEndpoint

/**
 * 端点视图模型
 * 
 * 维护端点数据并提供视图特定的转换
 */
class EndpointViewModel {
    private val allEndpoints: MutableList<DiscoveredEndpoint> = mutableListOf()
    private var filterState: FilterState = FilterState.inactive()
    
    // 筛选策略映射
    private val filterStrategies = mapOf(
        ViewMode.AUTO_DISCOVERY to AutoDiscoveryFilterStrategy(),
        ViewMode.SWAGGER to SwaggerFilterStrategy(),
        ViewMode.CLASS_METHOD to ClassMethodFilterStrategy()
    )
    
    /**
     * 更新端点数据
     */
    fun updateEndpoints(endpoints: List<DiscoveredEndpoint>) {
        allEndpoints.clear()
        allEndpoints.addAll(endpoints)
    }
    
    /**
     * 获取所有端点
     */
    fun getAllEndpoints(): List<DiscoveredEndpoint> {
        return allEndpoints.toList()
    }
    
    /**
     * 获取指定视图模式的端点结构
     */
    fun getEndpointsForView(viewMode: ViewMode): TreeNodeStructure {
        // 先应用筛选
        val filteredEndpoints = if (filterState.isActive) {
            applyFilter(filterState.filterText, viewMode)
        } else {
            allEndpoints
        }
        
        return when (viewMode) {
            ViewMode.AUTO_DISCOVERY -> createAutoDiscoveryStructure(filteredEndpoints)
            ViewMode.SWAGGER -> createSwaggerStructure(filteredEndpoints)
            ViewMode.CLASS_METHOD -> createClassMethodStructure(filteredEndpoints)
        }
    }

    /**
     * 应用筛选
     */
    fun applyFilter(filterText: String, viewMode: ViewMode): List<DiscoveredEndpoint> {
        filterState = FilterState.active(filterText)
        
        if (!filterState.isActive) {
            return allEndpoints
        }
        
        val strategy = filterStrategies[viewMode] ?: AutoDiscoveryFilterStrategy()
        return allEndpoints.filter { endpoint ->
            strategy.matches(endpoint, filterState.keywords)
        }
    }
    
    /**
     * 清除筛选
     */
    fun clearFilter() {
        filterState = FilterState.inactive()
    }
    
    /**
     * 获取当前筛选状态
     */
    fun getFilterState(): FilterState {
        return filterState
    }
    
    /**
     * 创建自动发现视图结构
     */
    private fun createAutoDiscoveryStructure(endpoints: List<DiscoveredEndpoint>): TreeNodeStructure.AutoDiscoveryNode {
        val groupedByClass = endpoints.groupBy { it.className }
        return TreeNodeStructure.AutoDiscoveryNode(groupedByClass)
    }
    
    /**
     * 创建Swagger视图结构
     */
    private fun createSwaggerStructure(endpoints: List<DiscoveredEndpoint>): TreeNodeStructure.SwaggerNode {
        val groupedByTags = mutableMapOf<String, MutableMap<String, MutableList<DiscoveredEndpoint>>>()
        
        endpoints.forEach { endpoint ->
            if (endpoint.tags.isEmpty()) {
                // 没有标签的端点放在"未分类"组
                val tag = "未分类"
                val classMap = groupedByTags.getOrPut(tag) { mutableMapOf() }
                val endpointList = classMap.getOrPut(endpoint.className) { mutableListOf() }
                endpointList.add(endpoint)
            } else {
                // 有标签的端点按标签分组
                endpoint.tags.forEach { tag ->
                    val classMap = groupedByTags.getOrPut(tag) { mutableMapOf() }
                    val endpointList = classMap.getOrPut(endpoint.className) { mutableListOf() }
                    endpointList.add(endpoint)
                }
            }
        }
        
        return TreeNodeStructure.SwaggerNode(groupedByTags)
    }
    
    /**
     * 创建类名方法名视图结构
     */
    private fun createClassMethodStructure(endpoints: List<DiscoveredEndpoint>): TreeNodeStructure.ClassMethodNode {
        val groupedByClass = mutableMapOf<String, MutableMap<String, DiscoveredEndpoint>>()
        
        endpoints.forEach { endpoint ->
            val methodMap = groupedByClass.getOrPut(endpoint.className) { mutableMapOf() }
            // 使用方法名作为键，如果有重复则添加路径作为区分
            val key = if (methodMap.containsKey(endpoint.methodName)) {
                "${endpoint.methodName} (${endpoint.path})"
            } else {
                endpoint.methodName
            }
            methodMap[key] = endpoint
        }
        
        return TreeNodeStructure.ClassMethodNode(groupedByClass)
    }
}
