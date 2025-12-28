package com.httppal.ui

import com.httppal.model.RequestSource
import com.httppal.model.RequestTemplate
import com.httppal.service.RequestTemplateService
import com.httppal.util.HttpPalBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

/**
 * Toolbar for request creation and management
 * Implements requirements 1.1, 2.1, 5.1, 11.1-11.5
 */
class RequestToolbar(private val project: Project) : JPanel(BorderLayout()) {
    
    private val templateService = project.service<RequestTemplateService>()
    
    // Toolbar buttons
    private val newRequestButton = JButton(HttpPalBundle.message("toolbar.new.request"))
    private val saveButton = JButton(HttpPalBundle.message("toolbar.save.request"))
    private val quickCreateButton = JButton(HttpPalBundle.message("toolbar.quick.create"))
    private val importFromClipboardButton = JButton(HttpPalBundle.message("toolbar.import.clipboard"))
    private val importFromPostmanButton = JButton(HttpPalBundle.message("toolbar.import.postman"))
    
    // Source indicator
    private val sourceLabel = JBLabel()
    private val modifiedIndicator = JBLabel("*")
    
    // Callbacks
    private var onNewRequestCallback: ((RequestTemplate?) -> Unit)? = null
    private var onSaveRequestCallback: (() -> Unit)? = null
    private var onQuickCreateCallback: (() -> Unit)? = null
    private var onImportFromClipboardCallback: (() -> Unit)? = null
    private var onImportFromPostmanCallback: (() -> Unit)? = null
    
    // State
    private var currentSource: RequestSource = RequestSource.MANUAL
    private var isModified: Boolean = false
    
    init {
        setupUI()
        setupEventHandlers()
        updateSourceDisplay()
    }
    
    private fun setupUI() {
        border = JBUI.Borders.empty(5, 10)
        
        // Left panel with action buttons
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        
        // New Request button - now just creates blank request directly
        newRequestButton.toolTipText = HttpPalBundle.message("toolbar.new.request.tooltip")
        leftPanel.add(newRequestButton)
        
        // Save button
        saveButton.toolTipText = HttpPalBundle.message("toolbar.save.request.tooltip")
        leftPanel.add(saveButton)
        
        // Quick Create button
        quickCreateButton.toolTipText = HttpPalBundle.message("toolbar.quick.create.tooltip")
        leftPanel.add(quickCreateButton)
        
        // Import from Clipboard button
        importFromClipboardButton.toolTipText = HttpPalBundle.message("toolbar.import.clipboard.tooltip")
        leftPanel.add(importFromClipboardButton)
        
        // Import from Postman button
        importFromPostmanButton.toolTipText = HttpPalBundle.message("toolbar.import.postman.tooltip")
        leftPanel.add(importFromPostmanButton)
        
        add(leftPanel, BorderLayout.WEST)
        
        // Right panel with source indicator
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0))
        
        sourceLabel.border = JBUI.Borders.empty(0, 10, 0, 5)
        rightPanel.add(sourceLabel)
        
        modifiedIndicator.isVisible = false
        modifiedIndicator.toolTipText = HttpPalBundle.message("toolbar.modified.tooltip")
        rightPanel.add(modifiedIndicator)
        
        add(rightPanel, BorderLayout.EAST)
    }
    
    private fun setupEventHandlers() {
        // New Request button - now creates blank request directly
        newRequestButton.addActionListener {
            onNewRequestCallback?.invoke(null)  // Always pass null for blank request
        }
        
        // Save button
        saveButton.addActionListener {
            onSaveRequestCallback?.invoke()
        }
        
        // Quick Create button
        quickCreateButton.addActionListener {
            onQuickCreateCallback?.invoke()
        }
        
        // Import from Clipboard button
        importFromClipboardButton.addActionListener {
            onImportFromClipboardCallback?.invoke()
        }
        
        // Import from Postman button
        importFromPostmanButton.addActionListener {
            onImportFromPostmanCallback?.invoke()
        }
    }
    

    
    /**
     * Update source display label
     */
    private fun updateSourceDisplay() {
        val sourceText = when (currentSource) {
            RequestSource.MANUAL -> HttpPalBundle.message("source.manual")
            RequestSource.TEMPLATE -> HttpPalBundle.message("source.template")
            RequestSource.ENDPOINT -> HttpPalBundle.message("source.endpoint")
            RequestSource.HISTORY -> HttpPalBundle.message("source.history")
            RequestSource.FAVORITE -> HttpPalBundle.message("source.favorite")
            RequestSource.CLIPBOARD -> HttpPalBundle.message("source.clipboard")
            RequestSource.SCANNED_ENDPOINT -> HttpPalBundle.message("source.scanned.endpoint")
        }
        
        sourceLabel.text = HttpPalBundle.message("toolbar.source.label", sourceText)
        modifiedIndicator.isVisible = isModified
    }
    
    /**
     * Set the current request source
     */
    fun setRequestSource(source: RequestSource) {
        currentSource = source
        updateSourceDisplay()
    }
    
    /**
     * Set the modified state
     */
    fun setModified(modified: Boolean) {
        isModified = modified
        updateSourceDisplay()
    }
    
    /**
     * Set callback for new request action
     */
    fun setOnNewRequestCallback(callback: (RequestTemplate?) -> Unit) {
        onNewRequestCallback = callback
    }
    
    /**
     * Set callback for save request action
     */
    fun setOnSaveRequestCallback(callback: () -> Unit) {
        onSaveRequestCallback = callback
    }
    
    /**
     * Set callback for quick create action
     */
    fun setOnQuickCreateCallback(callback: () -> Unit) {
        onQuickCreateCallback = callback
    }
    
    /**
     * Set callback for import from clipboard action
     */
    fun setOnImportFromClipboardCallback(callback: () -> Unit) {
        onImportFromClipboardCallback = callback
    }
    
    /**
     * Set callback for import from Postman action
     */
    fun setOnImportFromPostmanCallback(callback: () -> Unit) {
        onImportFromPostmanCallback = callback
    }
    
    /**
     * Enable or disable toolbar buttons
     */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        newRequestButton.isEnabled = enabled
        quickCreateButton.isEnabled = enabled
        importFromClipboardButton.isEnabled = enabled
        importFromPostmanButton.isEnabled = enabled
    }
}