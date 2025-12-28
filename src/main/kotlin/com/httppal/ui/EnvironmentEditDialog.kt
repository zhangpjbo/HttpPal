package com.httppal.ui

import com.httppal.model.Environment
import com.httppal.util.HttpPalBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import java.util.*

/**
 * Dialog for creating and editing environment configurations
 * Implements requirements 10.1: environment creation and editing forms
 */
class EnvironmentEditDialog(
    private val project: Project,
    private val existingEnvironment: Environment?
) : DialogWrapper(project) {
    
    private val nameField = JBTextField()
    private val baseUrlField = JBTextField()
    private val descriptionField = JBTextArea(3, 30)
    
    private val headersTableModel = HeadersTableModel()
    private val headersTable = JBTable(headersTableModel)
    private val addHeaderButton = JButton(HttpPalBundle.message("headers.add.button"))
    private val removeHeaderButton = JButton(HttpPalBundle.message("headers.remove.button"))
    
    private val variablesTableModel = VariablesTableModel()
    private val variablesTable = JBTable(variablesTableModel)
    private val addVariableButton = JButton(HttpPalBundle.message("button.add.variable"))
    private val removeVariableButton = JButton(HttpPalBundle.message("button.remove.variable"))
    
    private var resultEnvironment: Environment? = null
    
    init {
        title = if (existingEnvironment != null) 
            HttpPalBundle.message("dialog.environment.edit.title.edit") 
            else HttpPalBundle.message("dialog.environment.edit.title.create")
        setSize(600, 500)
        init()
        
        if (existingEnvironment != null) {
            populateFields(existingEnvironment)
        }
        
        setupTableListeners()
        updateButtonStates()
    }
    
    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.border = JBUI.Borders.empty(10)
        
        // Create tabbed pane for different sections
        val tabbedPane = JBTabbedPane()
        
        // Basic settings tab
        tabbedPane.addTab(HttpPalBundle.message("dialog.environment.edit.tab.basic"), createBasicPanel())
        
        // Headers tab
        tabbedPane.addTab(HttpPalBundle.message("dialog.environment.edit.tab.headers"), createHeadersPanel())
        
        // Variables tab
        tabbedPane.addTab(HttpPalBundle.message("dialog.environment.edit.tab.variables"), createVariablesPanel())
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER)
        
        return mainPanel
    }
    
    private fun createBasicPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(10)
        
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(5)
        gbc.anchor = GridBagConstraints.WEST
        
        // Name field
        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JBLabel(HttpPalBundle.message("environment.name.label")), gbc)
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        nameField.preferredSize = Dimension(300, nameField.preferredSize.height)
        panel.add(nameField, gbc)
        
        // Base URL field
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JBLabel(HttpPalBundle.message("environment.base.url.label")), gbc)
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        baseUrlField.preferredSize = Dimension(300, baseUrlField.preferredSize.height)
        panel.add(baseUrlField, gbc)
        
        // Description field
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        gbc.anchor = GridBagConstraints.NORTHWEST
        panel.add(JBLabel(HttpPalBundle.message("environment.description.label")), gbc)
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0
        descriptionField.lineWrap = true
        descriptionField.wrapStyleWord = true
        val descScrollPane = JBScrollPane(descriptionField)
        descScrollPane.preferredSize = Dimension(300, 80)
        panel.add(descScrollPane, gbc)
        
        // Help text
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weighty = 0.0
        val helpLabel = JBLabel("<html><i>" + HttpPalBundle.message("dialog.environment.edit.help.text") + "</i></html>")
        helpLabel.foreground = Color.GRAY
        panel.add(helpLabel, gbc)
        
        return panel
    }
    
    private fun createHeadersPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)
        
        // Info label
        val infoLabel = JBLabel(HttpPalBundle.message("dialog.environment.edit.info.headers"))
        panel.add(infoLabel, BorderLayout.NORTH)
        
        // Table
        headersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        val scrollPane = JBScrollPane(headersTable)
        scrollPane.preferredSize = Dimension(500, 200)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // Buttons
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        addHeaderButton.addActionListener { addHeader() }
        removeHeaderButton.addActionListener { removeSelectedHeader() }
        
        buttonPanel.add(addHeaderButton)
        buttonPanel.add(removeHeaderButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun createVariablesPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)
        
        // Info label
        val infoLabel = JBLabel(HttpPalBundle.message("dialog.environment.edit.info.variables"))
        panel.add(infoLabel, BorderLayout.NORTH)
        
        // Table
        variablesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        val scrollPane = JBScrollPane(variablesTable)
        scrollPane.preferredSize = Dimension(500, 200)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // Buttons
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        addVariableButton.addActionListener { addVariable() }
        removeVariableButton.addActionListener { removeSelectedVariable() }
        
        buttonPanel.add(addVariableButton)
        buttonPanel.add(removeVariableButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun populateFields(environment: Environment) {
        nameField.text = environment.name
        baseUrlField.text = environment.baseUrl
        descriptionField.text = environment.description ?: ""
        
        headersTableModel.setHeaders(environment.globalHeaders.toMutableMap())
        variablesTableModel.setVariables(environment.variables.toMutableMap())
    }
    
    private fun setupTableListeners() {
        headersTable.selectionModel.addListSelectionListener {
            updateButtonStates()
        }
        
        variablesTable.selectionModel.addListSelectionListener {
            updateButtonStates()
        }
    }
    
    private fun updateButtonStates() {
        removeHeaderButton.isEnabled = headersTable.selectedRow >= 0
        removeVariableButton.isEnabled = variablesTable.selectedRow >= 0
    }
    
    private fun addHeader() {
        val name = JOptionPane.showInputDialog(
            this.contentPane,
            HttpPalBundle.message("dialog.environment.header.add.name.prompt"),
            HttpPalBundle.message("dialog.environment.header.add.title"),
            JOptionPane.PLAIN_MESSAGE
        )
        
        if (!name.isNullOrBlank()) {
            val value = JOptionPane.showInputDialog(
                this.contentPane,
                HttpPalBundle.message("dialog.environment.header.add.value.prompt"),
                HttpPalBundle.message("dialog.environment.header.add.title"),
                JOptionPane.PLAIN_MESSAGE
            ) ?: ""
            
            headersTableModel.addHeader(name.trim(), value)
        }
    }
    
    private fun removeSelectedHeader() {
        val selectedRow = headersTable.selectedRow
        if (selectedRow >= 0) {
            headersTableModel.removeHeader(selectedRow)
        }
    }
    
    private fun addVariable() {
        val name = JOptionPane.showInputDialog(
            this.contentPane,
            HttpPalBundle.message("dialog.environment.variable.add.name.prompt"),
            HttpPalBundle.message("dialog.environment.variable.add.title"),
            JOptionPane.PLAIN_MESSAGE
        )
        
        if (!name.isNullOrBlank() && name.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) {
            val value = JOptionPane.showInputDialog(
                this.contentPane,
                HttpPalBundle.message("dialog.environment.variable.add.value.prompt"),
                HttpPalBundle.message("dialog.environment.variable.add.title"),
                JOptionPane.PLAIN_MESSAGE
            ) ?: ""
            
            variablesTableModel.addVariable(name.trim(), value)
        } else if (!name.isNullOrBlank()) {
            JOptionPane.showMessageDialog(
                this.contentPane,
                HttpPalBundle.message("dialog.environment.variable.invalid.name"),
                HttpPalBundle.message("dialog.environment.variable.invalid.title"),
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
    
    private fun removeSelectedVariable() {
        val selectedRow = variablesTable.selectedRow
        if (selectedRow >= 0) {
            variablesTableModel.removeVariable(selectedRow)
        }
    }
    
    override fun doValidate(): ValidationInfo? {
        val name = nameField.text.trim()
        if (name.isEmpty()) {
            return ValidationInfo(HttpPalBundle.message("validation.field.required", "Environment name"), nameField)
        }
        
        if (name.length > 50) {
            return ValidationInfo(HttpPalBundle.message("dialog.environment.edit.validation.name.length"), nameField)
        }
        
        val baseUrl = baseUrlField.text.trim()
        if (baseUrl.isEmpty()) {
            return ValidationInfo(HttpPalBundle.message("validation.field.required", "Base URL"), baseUrlField)
        }
        
        try {
            val url = java.net.URL(baseUrl)
            if (url.protocol !in listOf("http", "https")) {
                return ValidationInfo(HttpPalBundle.message("dialog.environment.edit.validation.url.protocol"), baseUrlField)
            }
        } catch (e: Exception) {
            return ValidationInfo(HttpPalBundle.message("dialog.environment.edit.validation.url.format"), baseUrlField)
        }
        
        return null
    }
    
    override fun doOKAction() {
        val validation = doValidate()
        if (validation != null) {
            return
        }
        
        val environment = Environment(
            id = existingEnvironment?.id ?: UUID.randomUUID().toString(),
            name = nameField.text.trim(),
            baseUrl = baseUrlField.text.trim(),
            globalHeaders = headersTableModel.getHeaders(),
            description = descriptionField.text.trim().takeIf { it.isNotEmpty() },
            variables = variablesTableModel.getVariables(),
            isActive = existingEnvironment?.isActive ?: false
        )
        
        resultEnvironment = environment
        super.doOKAction()
    }
    
    fun getEnvironment(): Environment? = resultEnvironment
    
    /**
     * Table model for headers
     */
    private class HeadersTableModel : AbstractTableModel() {
        private val columnNames = arrayOf(
            HttpPalBundle.message("table.header.name"),
            HttpPalBundle.message("table.header.value")
        )
        private val headers = mutableMapOf<String, String>()
        private val headerKeys = mutableListOf<String>()
        
        fun setHeaders(headers: MutableMap<String, String>) {
            this.headers.clear()
            this.headers.putAll(headers)
            this.headerKeys.clear()
            this.headerKeys.addAll(headers.keys)
            fireTableDataChanged()
        }
        
        fun getHeaders(): Map<String, String> = headers.toMap()
        
        fun addHeader(name: String, value: String) {
            if (!headers.containsKey(name)) {
                headerKeys.add(name)
            }
            headers[name] = value
            fireTableDataChanged()
        }
        
        fun removeHeader(row: Int) {
            if (row >= 0 && row < headerKeys.size) {
                val key = headerKeys[row]
                headers.remove(key)
                headerKeys.removeAt(row)
                fireTableDataChanged()
            }
        }
        
        override fun getRowCount(): Int = headers.size
        
        override fun getColumnCount(): Int = columnNames.size
        
        override fun getColumnName(column: Int): String = columnNames[column]
        
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val key = headerKeys[rowIndex]
            return when (columnIndex) {
                0 -> key
                1 -> headers[key] ?: ""
                else -> ""
            }
        }
        
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true
        
        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            val key = headerKeys[rowIndex]
            when (columnIndex) {
                0 -> {
                    val newKey = aValue.toString().trim()
                    if (newKey.isNotEmpty() && newKey != key) {
                        val value = headers[key] ?: ""
                        headers.remove(key)
                        headers[newKey] = value
                        headerKeys[rowIndex] = newKey
                    }
                }
                1 -> {
                    headers[key] = aValue.toString()
                }
            }
            fireTableCellUpdated(rowIndex, columnIndex)
        }
    }
    
    /**
     * Table model for variables
     */
    private class VariablesTableModel : AbstractTableModel() {
        private val columnNames = arrayOf(
            HttpPalBundle.message("table.variable.name"),
            HttpPalBundle.message("table.variable.value")
        )
        private val variables = mutableMapOf<String, String>()
        private val variableKeys = mutableListOf<String>()
        
        fun setVariables(variables: MutableMap<String, String>) {
            this.variables.clear()
            this.variables.putAll(variables)
            this.variableKeys.clear()
            this.variableKeys.addAll(variables.keys)
            fireTableDataChanged()
        }
        
        fun getVariables(): Map<String, String> = variables.toMap()
        
        fun addVariable(name: String, value: String) {
            if (!variables.containsKey(name)) {
                variableKeys.add(name)
            }
            variables[name] = value
            fireTableDataChanged()
        }
        
        fun removeVariable(row: Int) {
            if (row >= 0 && row < variableKeys.size) {
                val key = variableKeys[row]
                variables.remove(key)
                variableKeys.removeAt(row)
                fireTableDataChanged()
            }
        }
        
        override fun getRowCount(): Int = variables.size
        
        override fun getColumnCount(): Int = columnNames.size
        
        override fun getColumnName(column: Int): String = columnNames[column]
        
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val key = variableKeys[rowIndex]
            return when (columnIndex) {
                0 -> key
                1 -> variables[key] ?: ""
                else -> ""
            }
        }
        
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true
        
        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            val key = variableKeys[rowIndex]
            when (columnIndex) {
                0 -> {
                    val newKey = aValue.toString().trim()
                    if (newKey.isNotEmpty() && newKey != key && newKey.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) {
                        val value = variables[key] ?: ""
                        variables.remove(key)
                        variables[newKey] = value
                        variableKeys[rowIndex] = newKey
                    }
                }
                1 -> {
                    variables[key] = aValue.toString()
                }
            }
            fireTableCellUpdated(rowIndex, columnIndex)
        }
    }
}