package com.httppal.ui

import com.httppal.model.Environment
import com.httppal.service.EnvironmentService
import com.httppal.util.HttpPalBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Dialog for managing environments - create, edit, delete, and switch
 * Implements requirements 10.1, 10.4: environment configuration and management
 */
class EnvironmentManagementDialog(
    private val project: Project,
    private val parentPanel: EnvironmentSelectionPanel? = null
) : DialogWrapper(project) {
    
    private val environmentService = project.getService(EnvironmentService::class.java)
    private val environmentTableModel = EnvironmentTableModel()
    private val environmentTable = JBTable(environmentTableModel)
    
    private val addButton = JButton(HttpPalBundle.message("environment.create.button"))
    private val editButton = JButton(HttpPalBundle.message("environment.edit.button"))
    private val deleteButton = JButton(HttpPalBundle.message("environment.delete.button"))
    private val activateButton = JButton(HttpPalBundle.message("dialog.environment.activate.button"))
    private val deactivateButton = JButton(HttpPalBundle.message("dialog.environment.deactivate.button"))
    
    init {
        title = HttpPalBundle.message("dialog.environment.edit")
        setSize(800, 500)
        init()
        loadEnvironments()
        setupTableListeners()
        updateButtonStates()
    }
    
    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.border = JBUI.Borders.empty(10)
        
        // Title
        val titleLabel = JBLabel(HttpPalBundle.message("action.manage.environments"))
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        mainPanel.add(titleLabel, BorderLayout.NORTH)
        
        // Table panel
        val tablePanel = createTablePanel()
        mainPanel.add(tablePanel, BorderLayout.CENTER)
        
        // Button panel
        val buttonPanel = createButtonPanel()
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)
        
        return mainPanel
    }
    
    private fun createTablePanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10, 0)
        
        // Setup table
        environmentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        environmentTable.setDefaultRenderer(String::class.java, EnvironmentTableCellRenderer())
        
        // Set column widths
        val columnModel = environmentTable.columnModel
        columnModel.getColumn(0).preferredWidth = 150 // Name
        columnModel.getColumn(1).preferredWidth = 300 // Base URL
        columnModel.getColumn(2).preferredWidth = 100 // Status
        columnModel.getColumn(3).preferredWidth = 200 // Description
        
        val scrollPane = JBScrollPane(environmentTable)
        scrollPane.preferredSize = Dimension(750, 300)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createButtonPanel(): JComponent {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))
        panel.border = JBUI.Borders.empty(10, 0, 0, 0)
        
        // Setup buttons
        addButton.addActionListener { addEnvironment() }
        editButton.addActionListener { editSelectedEnvironment() }
        deleteButton.addActionListener { deleteSelectedEnvironment() }
        activateButton.addActionListener { activateSelectedEnvironment() }
        deactivateButton.addActionListener { deactivateEnvironment() }
        
        panel.add(addButton)
        panel.add(editButton)
        panel.add(deleteButton)
        panel.add(JSeparator(SwingConstants.VERTICAL))
        panel.add(activateButton)
        panel.add(deactivateButton)
        
        return panel
    }
    
    private fun setupTableListeners() {
        // Selection listener
        environmentTable.selectionModel.addListSelectionListener {
            updateButtonStates()
        }
        
        // Double-click to edit
        environmentTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    editSelectedEnvironment()
                }
            }
        })
    }
    
    private fun loadEnvironments() {
        val environments = environmentService.getAllEnvironments()
        environmentTableModel.setEnvironments(environments)
        updateButtonStates()
    }
    
    private fun updateButtonStates() {
        val selectedRow = environmentTable.selectedRow
        val hasSelection = selectedRow >= 0
        val selectedEnvironment = if (hasSelection) {
            environmentTableModel.getEnvironmentAt(selectedRow)
        } else null
        
        editButton.isEnabled = hasSelection
        deleteButton.isEnabled = hasSelection
        activateButton.isEnabled = hasSelection && selectedEnvironment?.isActive != true
        deactivateButton.isEnabled = selectedEnvironment?.isActive == true
    }
    
    private fun addEnvironment() {
        val dialog = EnvironmentEditDialog(project, null)
        if (dialog.showAndGet()) {
            val newEnvironment = dialog.getEnvironment()
            if (newEnvironment != null) {
                try {
                    environmentService.createEnvironment(newEnvironment)
                    loadEnvironments()
                    parentPanel?.refreshEnvironments()
                } catch (e: Exception) {
                    showErrorMessage(
                        HttpPalBundle.message("error.environment.create.failed") + ": " + (e.message ?: "Unknown error")
                    )
                }
            }
        }
    }
    
    private fun editSelectedEnvironment() {
        val selectedRow = environmentTable.selectedRow
        if (selectedRow < 0) return
        
        val environment = environmentTableModel.getEnvironmentAt(selectedRow)
        val dialog = EnvironmentEditDialog(project, environment)
        if (dialog.showAndGet()) {
            val updatedEnvironment = dialog.getEnvironment()
            if (updatedEnvironment != null) {
                try {
                    environmentService.updateEnvironment(updatedEnvironment)
                    loadEnvironments()
                    parentPanel?.refreshEnvironments()
                } catch (e: Exception) {
                    showErrorMessage(
                        HttpPalBundle.message("error.environment.update.failed") + ": " + (e.message ?: "Unknown error")
                    )
                }
            }
        }
    }
    
    private fun deleteSelectedEnvironment() {
        val selectedRow = environmentTable.selectedRow
        if (selectedRow < 0) return
        
        val environment = environmentTableModel.getEnvironmentAt(selectedRow)
        val result = JOptionPane.showConfirmDialog(
            this.contentPane,
            HttpPalBundle.message("dialog.environment.delete.confirm", environment.name),
            HttpPalBundle.message("dialog.environment.delete.title.confirm"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        )
        
        if (result == JOptionPane.YES_OPTION) {
            try {
                environmentService.deleteEnvironment(environment.id)
                loadEnvironments()
                parentPanel?.refreshEnvironments()
            } catch (e: Exception) {
                showErrorMessage(
                    HttpPalBundle.message("error.environment.delete.failed", e.message ?: "Unknown error")
                )
            }
        }
    }
    
    private fun activateSelectedEnvironment() {
        val selectedRow = environmentTable.selectedRow
        if (selectedRow < 0) return
        
        val environment = environmentTableModel.getEnvironmentAt(selectedRow)
        try {
            environmentService.switchToEnvironment(environment.id)
            loadEnvironments()
            parentPanel?.refreshEnvironments()
        } catch (e: Exception) {
            showErrorMessage(
                HttpPalBundle.message("error.environment.activate.failed") + ": " + (e.message ?: "Unknown error")
            )
        }
    }
    
    private fun deactivateEnvironment() {
        try {
            environmentService.switchToEnvironment(null)
            loadEnvironments()
            parentPanel?.refreshEnvironments()
        } catch (e: Exception) {
            showErrorMessage(
                HttpPalBundle.message("error.environment.deactivate.failed") + ": " + (e.message ?: "Unknown error")
            )
        }
    }
    
    private fun showErrorMessage(message: String) {
        JOptionPane.showMessageDialog(
            this.contentPane,
            message,
            HttpPalBundle.message("error.title.general"),
            JOptionPane.ERROR_MESSAGE
        )
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(cancelAction)
    }
    
    /**
     * Table model for environments
     */
    private class EnvironmentTableModel : AbstractTableModel() {
        private val columnNames = arrayOf(
            HttpPalBundle.message("table.environment.name"),
            HttpPalBundle.message("table.environment.baseurl"),
            HttpPalBundle.message("table.environment.status"),
            HttpPalBundle.message("table.environment.description")
        )
        private var environments = listOf<Environment>()
        
        fun setEnvironments(environments: List<Environment>) {
            this.environments = environments
            fireTableDataChanged()
        }
        
        fun getEnvironmentAt(row: Int): Environment = environments[row]
        
        override fun getRowCount(): Int = environments.size
        
        override fun getColumnCount(): Int = columnNames.size
        
        override fun getColumnName(column: Int): String = columnNames[column]
        
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val environment = environments[rowIndex]
            return when (columnIndex) {
                0 -> environment.name
                1 -> environment.baseUrl
                2 -> if (environment.isActive) 
                    HttpPalBundle.message("table.environment.status.active") 
                    else HttpPalBundle.message("table.environment.status.inactive")
                3 -> environment.description ?: ""
                else -> ""
            }
        }
        
        override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java
    }
    
    /**
     * Custom cell renderer for environment table
     */
    private class EnvironmentTableCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            
            if (table is JBTable && row < table.model.rowCount) {
                val model = table.model as EnvironmentTableModel
                val environment = model.getEnvironmentAt(row)
                
                // Highlight active environment
                if (environment.isActive && !isSelected) {
                    background = Color(240, 255, 240) // Light green
                }
                
                // Bold font for active environment name
                if (column == 0 && environment.isActive) {
                    font = font.deriveFont(Font.BOLD)
                }
                
                // Status column coloring
                if (column == 2) {
                    if (environment.isActive) {
                        foreground = if (isSelected) Color.WHITE else Color(0, 128, 0) // Green
                    } else {
                        foreground = if (isSelected) Color.WHITE else Color.GRAY
                    }
                }
            }
            
            return this
        }
    }
}