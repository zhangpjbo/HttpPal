package com.httppal.ui

import com.httppal.model.HttpMethod
import com.httppal.model.RequestTemplate
import com.httppal.service.RequestTemplateService
import com.httppal.util.HttpPalBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * Dialog for managing request templates
 * Implements requirements 12.1, 12.2, 12.3, 12.4, 12.5
 */
class TemplateManagementDialog(
    private val project: Project
) : DialogWrapper(project) {
    
    private val templateService = project.service<RequestTemplateService>()
    
    private val templateTable: JBTable
    private val tableModel: DefaultTableModel
    
    private val newButton = JButton(HttpPalBundle.message("template.management.new"))
    private val editButton = JButton(HttpPalBundle.message("template.management.edit"))
    private val deleteButton = JButton(HttpPalBundle.message("template.management.delete"))
    private val duplicateButton = JButton(HttpPalBundle.message("template.management.duplicate"))
    
    init {
        title = HttpPalBundle.message("template.management.title")
        
        // Create table model
        val columnNames = arrayOf(
            HttpPalBundle.message("template.management.column.name"),
            HttpPalBundle.message("template.management.column.method"),
            HttpPalBundle.message("template.management.column.type"),
            HttpPalBundle.message("template.management.column.description")
        )
        
        tableModel = object : DefaultTableModel(columnNames, 0) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
        
        templateTable = JBTable(tableModel)
        templateTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        
        init()
        loadTemplates()
        updateButtonStates()
    }
    
    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.preferredSize = Dimension(700, 400)
        mainPanel.border = JBUI.Borders.empty(10)
        
        // Table
        val scrollPane = JBScrollPane(templateTable)
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        
        // Button panel
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        buttonPanel.add(newButton)
        buttonPanel.add(editButton)
        buttonPanel.add(duplicateButton)
        buttonPanel.add(deleteButton)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)
        
        // Setup listeners
        setupListeners()
        
        return mainPanel
    }
    
    private fun setupListeners() {
        // Table selection listener
        templateTable.selectionModel.addListSelectionListener {
            updateButtonStates()
        }
        
        // Double-click to edit
        templateTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    editSelectedTemplate()
                }
            }
        })
        
        // Button listeners
        newButton.addActionListener { createNewTemplate() }
        editButton.addActionListener { editSelectedTemplate() }
        duplicateButton.addActionListener { duplicateSelectedTemplate() }
        deleteButton.addActionListener { deleteSelectedTemplate() }
    }
    
    private fun loadTemplates() {
        tableModel.rowCount = 0
        
        val templates = templateService.getAllTemplates()
        templates.forEach { template ->
            val row = arrayOf(
                template.name,
                template.method.name,
                if (template.isBuiltIn) 
                    HttpPalBundle.message("template.type.builtin") 
                else 
                    HttpPalBundle.message("template.type.custom"),
                template.description ?: ""
            )
            tableModel.addRow(row)
        }
    }
    
    private fun updateButtonStates() {
        val selectedRow = templateTable.selectedRow
        val hasSelection = selectedRow >= 0
        
        editButton.isEnabled = hasSelection
        duplicateButton.isEnabled = hasSelection
        
        // Can only delete custom templates
        if (hasSelection) {
            val templates = templateService.getAllTemplates()
            val selectedTemplate = templates[selectedRow]
            deleteButton.isEnabled = !selectedTemplate.isBuiltIn
        } else {
            deleteButton.isEnabled = false
        }
    }
    
    private fun createNewTemplate() {
        val dialog = TemplateEditorDialog(project, null)
        if (dialog.showAndGet()) {
            val template = dialog.getTemplate()
            if (template != null) {
                if (templateService.createTemplate(template)) {
                    loadTemplates()
                    Messages.showInfoMessage(
                        project,
                        HttpPalBundle.message("template.create.success", template.name),
                        HttpPalBundle.message("dialog.success")
                    )
                } else {
                    Messages.showErrorDialog(
                        project,
                        HttpPalBundle.message("template.create.error"),
                        HttpPalBundle.message("error.title.general")
                    )
                }
            }
        }
    }
    
    private fun editSelectedTemplate() {
        val selectedRow = templateTable.selectedRow
        if (selectedRow < 0) return
        
        val templates = templateService.getAllTemplates()
        val template = templates[selectedRow]
        
        if (template.isBuiltIn) {
            Messages.showWarningDialog(
                project,
                HttpPalBundle.message("template.edit.builtin.warning"),
                HttpPalBundle.message("dialog.warning")
            )
            return
        }
        
        val dialog = TemplateEditorDialog(project, template)
        if (dialog.showAndGet()) {
            val updatedTemplate = dialog.getTemplate()
            if (updatedTemplate != null) {
                if (templateService.updateTemplate(updatedTemplate)) {
                    loadTemplates()
                    Messages.showInfoMessage(
                        project,
                        HttpPalBundle.message("template.update.success", updatedTemplate.name),
                        HttpPalBundle.message("dialog.success")
                    )
                } else {
                    Messages.showErrorDialog(
                        project,
                        HttpPalBundle.message("template.update.error"),
                        HttpPalBundle.message("error.title.general")
                    )
                }
            }
        }
    }
    
    private fun duplicateSelectedTemplate() {
        val selectedRow = templateTable.selectedRow
        if (selectedRow < 0) return
        
        val templates = templateService.getAllTemplates()
        val template = templates[selectedRow]
        
        // Create a copy with a new name
        val newName = "${template.name} (Copy)"
        val duplicatedTemplate = template.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = newName,
            isBuiltIn = false,
            createdAt = java.time.Instant.now(),
            lastUsed = null,
            useCount = 0
        )
        
        if (templateService.createTemplate(duplicatedTemplate)) {
            loadTemplates()
            Messages.showInfoMessage(
                project,
                HttpPalBundle.message("template.duplicate.success", newName),
                HttpPalBundle.message("dialog.success")
            )
        } else {
            Messages.showErrorDialog(
                project,
                HttpPalBundle.message("template.duplicate.error"),
                HttpPalBundle.message("error.title.general")
            )
        }
    }
    
    private fun deleteSelectedTemplate() {
        val selectedRow = templateTable.selectedRow
        if (selectedRow < 0) return
        
        val templates = templateService.getAllTemplates()
        val template = templates[selectedRow]
        
        if (template.isBuiltIn) {
            Messages.showWarningDialog(
                project,
                HttpPalBundle.message("template.delete.builtin.warning"),
                HttpPalBundle.message("dialog.warning")
            )
            return
        }
        
        val result = Messages.showYesNoDialog(
            project,
            HttpPalBundle.message("template.delete.confirm", template.name),
            HttpPalBundle.message("template.delete.title"),
            Messages.getQuestionIcon()
        )
        
        if (result == Messages.YES) {
            if (templateService.deleteTemplate(template.id)) {
                loadTemplates()
                Messages.showInfoMessage(
                    project,
                    HttpPalBundle.message("template.delete.success", template.name),
                    HttpPalBundle.message("dialog.success")
                )
            } else {
                Messages.showErrorDialog(
                    project,
                    HttpPalBundle.message("template.delete.error"),
                    HttpPalBundle.message("error.title.general")
                )
            }
        }
    }
}

/**
 * Dialog for editing a template
 */
private class TemplateEditorDialog(
    private val project: Project,
    private val existingTemplate: RequestTemplate?
) : DialogWrapper(project) {
    
    private val nameField = JBTextField(20)
    private val descriptionField = JBTextArea(3, 20)
    private val methodComboBox = JComboBox(HttpMethod.values())
    private val urlField = JBTextField(30)
    private val headersField = JBTextArea(5, 30)
    private val bodyField = JBTextArea(8, 30)
    
    private var resultTemplate: RequestTemplate? = null
    
    init {
        title = if (existingTemplate != null) {
            HttpPalBundle.message("template.editor.title.edit")
        } else {
            HttpPalBundle.message("template.editor.title.new")
        }
        
        // Pre-fill fields if editing
        existingTemplate?.let { template ->
            nameField.text = template.name
            descriptionField.text = template.description ?: ""
            methodComboBox.selectedItem = template.method
            urlField.text = template.urlTemplate ?: ""
            
            // Format headers as key: value pairs
            headersField.text = template.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            bodyField.text = template.body ?: ""
        }
        
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(10)
        
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(5)
        gbc.anchor = GridBagConstraints.WEST
        
        // Name
        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JBLabel(HttpPalBundle.message("template.editor.name")), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(nameField, gbc)
        
        // Description
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JBLabel(HttpPalBundle.message("template.editor.description")), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 0.2
        descriptionField.lineWrap = true
        descriptionField.wrapStyleWord = true
        panel.add(JBScrollPane(descriptionField), gbc)
        
        // Method
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0; gbc.weighty = 0.0
        panel.add(JBLabel(HttpPalBundle.message("template.editor.method")), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(methodComboBox, gbc)
        
        // URL
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JBLabel(HttpPalBundle.message("template.editor.url")), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(urlField, gbc)
        
        // Headers
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JBLabel(HttpPalBundle.message("template.editor.headers")), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 0.3
        headersField.lineWrap = true
        headersField.wrapStyleWord = true
        panel.add(JBScrollPane(headersField), gbc)
        
        // Body
        gbc.gridx = 0; gbc.gridy = 5; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0; gbc.weighty = 0.0
        panel.add(JBLabel(HttpPalBundle.message("template.editor.body")), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 0.5
        bodyField.lineWrap = true
        bodyField.wrapStyleWord = true
        panel.add(JBScrollPane(bodyField), gbc)
        
        return panel
    }
    
    override fun doOKAction() {
        // Validate name
        if (nameField.text.isBlank()) {
            Messages.showErrorDialog(
                project,
                HttpPalBundle.message("template.editor.validation.name.empty"),
                HttpPalBundle.message("error.title.validation")
            )
            return
        }
        
        // Parse headers
        val headers = mutableMapOf<String, String>()
        headersField.text.lines().forEach { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                if (key.isNotEmpty()) {
                    headers[key] = value
                }
            }
        }
        
        // Create template
        resultTemplate = RequestTemplate(
            id = existingTemplate?.id ?: java.util.UUID.randomUUID().toString(),
            name = nameField.text.trim(),
            description = descriptionField.text.trim().ifBlank { null },
            method = methodComboBox.selectedItem as HttpMethod,
            urlTemplate = urlField.text.trim().ifBlank { null },
            headers = headers,
            body = bodyField.text.trim().ifBlank { null },
            isBuiltIn = false,
            createdAt = existingTemplate?.createdAt ?: java.time.Instant.now(),
            lastUsed = existingTemplate?.lastUsed,
            useCount = existingTemplate?.useCount ?: 0
        )
        
        super.doOKAction()
    }
    
    fun getTemplate(): RequestTemplate? = resultTemplate
}
