package com.httppal.ui

import com.httppal.model.Environment
import com.httppal.service.EnvironmentService
import com.httppal.util.HttpPalBundle
import com.intellij.openapi.project.Project
import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*

/**
 * Panel for environment selection dropdown and quick actions
 * Implements requirements 10.1, 10.4: environment selection and visual feedback
 */
class EnvironmentSelectionPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val environmentService = project.getService(EnvironmentService::class.java)
    private val environmentComboBox = JComboBox<EnvironmentComboBoxItem>()
    private val statusLabel = JBLabel()
    private val manageButton = JButton(HttpPalBundle.message("environment.manage.button"))
    
    private var onEnvironmentChangeCallback: ((Environment?) -> Unit)? = null
    private var isUpdating = false  // Flag to prevent recursive calls
    
    init {
        setupUI()
        loadEnvironments()
        setupListeners()
    }
    
    private fun setupUI() {
        border = JBUI.Borders.empty(5)
        
        // Left side - environment selection
        val selectionPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        selectionPanel.add(JBLabel(HttpPalBundle.message("environment.label")))
        
        environmentComboBox.renderer = EnvironmentComboBoxRenderer()
        environmentComboBox.preferredSize = Dimension(200, environmentComboBox.preferredSize.height)
        selectionPanel.add(environmentComboBox)
        
        selectionPanel.add(manageButton)
        add(selectionPanel, BorderLayout.WEST)
        
        // Right side - status
        statusLabel.horizontalAlignment = SwingConstants.RIGHT
        add(statusLabel, BorderLayout.EAST)
        
        updateStatusDisplay()
    }
    
    private fun setupListeners() {
        environmentComboBox.addActionListener { e ->
            // Ignore events triggered by programmatic changes
            if (!isUpdating) {
                val selectedItem = environmentComboBox.selectedItem as? EnvironmentComboBoxItem
                handleEnvironmentSelection(selectedItem)
            }
        }
        
        manageButton.addActionListener {
            openEnvironmentManagementDialog()
        }
        
        // Listen for environment changes from the service
        environmentService.addEnvironmentChangeListener { environments ->
            SwingUtilities.invokeLater {
                isUpdating = true
                try {
                    loadEnvironments()
                } finally {
                    isUpdating = false
                }
            }
        }
    }
    
    private fun loadEnvironments() {
        isUpdating = true
        try {
            val currentSelection = environmentComboBox.selectedItem as? EnvironmentComboBoxItem
            environmentComboBox.removeAllItems()
            
            // Add "None" option
            environmentComboBox.addItem(EnvironmentComboBoxItem(null, HttpPalBundle.message("environment.none")))
            
            // Add all environments
            val environments = environmentService.getAllEnvironments()
            environments.forEach { env ->
                environmentComboBox.addItem(EnvironmentComboBoxItem(env, env.getDisplayName()))
            }
            
            // Restore selection or select current environment
            val currentEnv = environmentService.getCurrentEnvironment()
            if (currentEnv != null) {
                selectEnvironment(currentEnv)
            } else {
                environmentComboBox.selectedIndex = 0 // Select "None"
            }
            
            updateStatusDisplay()
        } finally {
            isUpdating = false
        }
    }
    
    private fun handleEnvironmentSelection(selectedItem: EnvironmentComboBoxItem?) {
        if (selectedItem == null) return
        
        val environment = selectedItem.environment
        
        // Switch to the selected environment
        try {
            environmentService.switchToEnvironment(environment?.id)
            updateStatusDisplay()
            
            // Notify callback
            onEnvironmentChangeCallback?.invoke(environment)
            
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                HttpPalBundle.message("error.environment.switch.failed", e.message ?: "Unknown error"),
                HttpPalBundle.message("error.title.general"),
                JOptionPane.ERROR_MESSAGE
            )
            
            // Revert selection
            loadEnvironments()
        }
    }
    
    private fun selectEnvironment(environment: Environment) {
        for (i in 0 until environmentComboBox.itemCount) {
            val item = environmentComboBox.getItemAt(i)
            if (item.environment?.id == environment.id) {
                environmentComboBox.selectedIndex = i
                break
            }
        }
    }
    
    private fun updateStatusDisplay() {
        val currentEnv = environmentService.getCurrentEnvironment()
        if (currentEnv != null) {
            statusLabel.text = HttpPalBundle.message("status.environment.switched", currentEnv.name)
            statusLabel.foreground = Color(0, 128, 0) // Green
            statusLabel.toolTipText = "Base URL: ${currentEnv.baseUrl}"
        } else {
            statusLabel.text = HttpPalBundle.message("status.environment.deactivated")
            statusLabel.foreground = Color.GRAY
            statusLabel.toolTipText = null
        }
    }
    
    private fun openEnvironmentManagementDialog() {
        val dialog = EnvironmentManagementDialog(project, this)
        dialog.show()
    }
    
    /**
     * Set callback for environment changes
     */
    fun setOnEnvironmentChangeCallback(callback: (Environment?) -> Unit) {
        onEnvironmentChangeCallback = callback
    }
    
    /**
     * Get currently selected environment
     */
    fun getCurrentEnvironment(): Environment? {
        return environmentService.getCurrentEnvironment()
    }
    
    /**
     * Refresh the environment list (called from management dialog)
     */
    fun refreshEnvironments() {
        loadEnvironments()
    }
    
    /**
     * Data class for combo box items
     */
    private data class EnvironmentComboBoxItem(
        val environment: Environment?,
        val displayText: String
    ) {
        override fun toString(): String = displayText
    }
    
    /**
     * Custom renderer for environment combo box
     */
    private class EnvironmentComboBoxRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            
            val item = value as? EnvironmentComboBoxItem
            if (item != null) {
                text = item.displayText
                
                if (item.environment != null) {
                    toolTipText = "Base URL: ${item.environment.baseUrl}"
                    if (item.environment.isActive) {
                        font = font.deriveFont(Font.BOLD)
                    }
                } else {
                    toolTipText = "No environment - use full URLs"
                }
            }
            
            return this
        }
    }
}