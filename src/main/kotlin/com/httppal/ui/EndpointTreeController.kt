package com.httppal.ui

import com.httppal.model.DiscoveredEndpoint
import com.intellij.openapi.project.Project
import javax.swing.JTree

/**
 * 端点树控制器
 * 
 * 管理视图模式切换、筛选和位置保持
 */
class EndpointTreeController(
    private val project: Project,
    private val viewModel: EndpointViewModel,
    private val tree: JTree
) {
    private var currentViewMode: ViewMode = ViewMode.AUTO_DISCOVERY
    private var currentFilter: String = ""
    private var selectedEndpointId: EndpointIdentifier? = null
    private var expansionState: TreeExpansionState = TreeExpansionState.empty()
    
    // 回调函数
    private var onViewModeChanged: ((ViewMode) -> Unit)? = null
    private var onFilterChanged: ((String) -> Unit)? = null
    
    /**
     * 获取当前视图模式
     */
    fun getCurrentViewMode(): ViewMode {
        return currentViewMode
    }
    
    /**
     * 切换视图模式
     */
    fun switchViewMode(newMode: ViewMode) {
        if (currentViewMode == newMode) {
            return
        }
        
        // 保存当前展开状态
        expansionState = TreeExpansionState().captureFrom(tree)
        
        // 切换视图模式
        currentViewMode = newMode
        
        // 通知视图更新
        onViewModeChanged?.invoke(newMode)
    }

    /**
     * 应用筛选
     */
    fun applyFilter(filterText: String) {
        currentFilter = filterText
        
        // 通知视图更新
        onFilterChanged?.invoke(filterText)
    }
    
    /**
     * 清除筛选
     */
    fun clearFilter() {
        currentFilter = ""
        viewModel.clearFilter()
        onFilterChanged?.invoke("")
    }
    
    /**
     * 记录当前选中的端点
     */
    fun recordSelectedEndpoint(endpoint: DiscoveredEndpoint) {
        selectedEndpointId = EndpointIdentifier.from(endpoint)
    }
    
    /**
     * 恢复选中状态
     * 
     * 使用三级匹配策略：
     * 1. 精确匹配（路径+方法）
     * 2. 模糊匹配（类名+方法名）
     * 3. 标签匹配（共同标签）
     * 
     * @return 是否成功恢复选中状态
     */
    fun restoreSelection(endpoints: List<DiscoveredEndpoint>): Boolean {
        val identifier = selectedEndpointId ?: return false
        
        // 1. 尝试精确匹配
        val exactMatch = endpoints.firstOrNull { identifier.exactMatch(it) }
        if (exactMatch != null) {
            return true // 由调用方负责实际选中
        }
        
        // 2. 尝试模糊匹配
        val fuzzyMatch = endpoints.firstOrNull { identifier.fuzzyMatch(it) }
        if (fuzzyMatch != null) {
            return true
        }
        
        // 3. 尝试标签匹配
        val tagMatch = endpoints.firstOrNull { identifier.tagMatch(it) }
        if (tagMatch != null) {
            return true
        }
        
        // 所有匹配策略都失败
        return false
    }
    
    /**
     * 查找匹配的端点
     * 
     * @return 匹配的端点，如果没有匹配则返回 null
     */
    fun findMatchingEndpoint(endpoints: List<DiscoveredEndpoint>): DiscoveredEndpoint? {
        val identifier = selectedEndpointId ?: return null
        
        // 1. 尝试精确匹配
        val exactMatch = endpoints.firstOrNull { identifier.exactMatch(it) }
        if (exactMatch != null) {
            return exactMatch
        }
        
        // 2. 尝试模糊匹配
        val fuzzyMatch = endpoints.firstOrNull { identifier.fuzzyMatch(it) }
        if (fuzzyMatch != null) {
            return fuzzyMatch
        }
        
        // 3. 尝试标签匹配
        val tagMatch = endpoints.firstOrNull { identifier.tagMatch(it) }
        return tagMatch
    }
    
    /**
     * 获取展开状态
     */
    fun getExpansionState(): TreeExpansionState {
        return expansionState
    }
    
    /**
     * 设置视图模式变更回调
     */
    fun setOnViewModeChanged(callback: (ViewMode) -> Unit) {
        onViewModeChanged = callback
    }
    
    /**
     * 设置筛选变更回调
     */
    fun setOnFilterChanged(callback: (String) -> Unit) {
        onFilterChanged = callback
    }
}
