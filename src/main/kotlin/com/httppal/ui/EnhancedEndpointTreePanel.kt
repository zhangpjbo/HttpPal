package com.httppal.ui

import com.httppal.model.DiscoveredEndpoint
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

/**
 * 增强的端点树面板
 * 
 * 在原有 EndpointTreePanel 基础上添加视图模式选择和筛选功能
 */
class EnhancedEndpointTreePanel(private val project: Project) : JPanel(BorderLayout()) {
    
    // 原始端点树面板
    private val originalPanel: EndpointTreePanel = EndpointTreePanel(project)
    
    // 视图模型和控制器
    private val viewModel = EndpointViewModel()
    private lateinit var controller: EndpointTreeController
    
    // UI组件
    private lateinit var viewModeComboBox: JComboBox<String>
    private lateinit var filterTextField: JTextField
    private lateinit var clearFilterButton: JButton
    
    init {
        // 初始化控制器
        controller = EndpointTreeController(project, viewModel, originalPanel.tree)
        
        setupUI()
        setupControllerCallbacks()
    }
    
    private fun setupUI() {
        border = JBUI.Borders.empty(5)
        
        // 控制面板（视图模式选择器 + 筛选框）
        val controlPanel = createControlPanel()
        add(controlPanel, BorderLayout.NORTH)
        
        // 原始端点树面板
        add(originalPanel, BorderLayout.CENTER)
    }
    
    /**
     * 创建控制面板
     */
    private fun createControlPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(0, 0, 5, 0)
        
        // 视图模式选择器行
        val viewModePanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 2))
        val viewModeLabel = JBLabel("视图模式:")
        viewModeComboBox = JComboBox(arrayOf("自动发现", "Swagger", "类名/方法名"))
        viewModeComboBox.selectedIndex = 0
        viewModeComboBox.addActionListener {
            val selectedMode = when (viewModeComboBox.selectedIndex) {
                0 -> ViewMode.AUTO_DISCOVERY
                1 -> ViewMode.SWAGGER
                2 -> ViewMode.CLASS_METHOD
                else -> ViewMode.AUTO_DISCOVERY
            }
            controller.switchViewMode(selectedMode)
        }
        viewModePanel.add(viewModeLabel)
        viewModePanel.add(viewModeComboBox)
        
        // 筛选框行
        val filterPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 2))
        val filterLabel = JBLabel("筛选:")
        filterTextField = JTextField(20)
        filterTextField.toolTipText = "输入关键词筛选端点（支持空格分隔多个关键词）"
        
        // 实时筛选
        filterTextField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) {
                controller.applyFilter(filterTextField.text)
            }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) {
                controller.applyFilter(filterTextField.text)
            }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {
                controller.applyFilter(filterTextField.text)
            }
        })
        
        clearFilterButton = JButton("清除")
        clearFilterButton.addActionListener {
            filterTextField.text = ""
            controller.clearFilter()
        }
        
        filterPanel.add(filterLabel)
        filterPanel.add(filterTextField)
        filterPanel.add(clearFilterButton)
        
        panel.add(viewModePanel)
        panel.add(filterPanel)
        
        return panel
    }
    
    /**
     * 设置控制器回调
     */
    private fun setupControllerCallbacks() {
        // 视图模式变更回调
        controller.setOnViewModeChanged { newMode ->
            refreshTreeForViewMode(newMode)
        }
        
        // 筛选变更回调
        controller.setOnFilterChanged { filterText ->
            refreshTreeWithFilter(filterText)
        }
    }
    
    /**
     * 根据视图模式刷新树
     */
    private fun refreshTreeForViewMode(viewMode: ViewMode) {
        // 获取当前所有端点
        val allEndpoints = originalPanel.getAllEndpoints()
        viewModel.updateEndpoints(allEndpoints)
        
        // 应用当前筛选
        val structure = viewModel.getEndpointsForView(viewMode)
        updateTreeDisplay(structure, viewMode)
    }
    
    /**
     * 根据筛选刷新树
     */
    private fun refreshTreeWithFilter(filterText: String) {
        val currentMode = controller.getCurrentViewMode()
        val allEndpoints = originalPanel.getAllEndpoints()
        viewModel.updateEndpoints(allEndpoints)
        
        // 应用筛选
        viewModel.applyFilter(filterText, currentMode)
        val structure = viewModel.getEndpointsForView(currentMode)
        updateTreeDisplay(structure, currentMode)
    }
    
    /**
     * 更新树显示
     */
    private fun updateTreeDisplay(structure: TreeNodeStructure, viewMode: ViewMode) {
        // TODO: 在任务7中实现树的实际更新逻辑
        // 这里暂时只是占位，实际的树更新将在下一个任务中完成
    }
    
    /**
     * 设置端点选中回调
     */
    fun setOnEndpointSelectedCallback(callback: (DiscoveredEndpoint) -> Unit) {
        originalPanel.setOnEndpointSelectedCallback(callback)
    }
    
    /**
     * 设置刷新回调
     */
    fun setOnRefreshCallback(callback: () -> Unit) {
        originalPanel.setOnRefreshCallback(callback)
    }
}
