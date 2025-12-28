package com.httppal.ui

import com.httppal.model.Environment
import com.httppal.model.PostmanExportOptions
import com.httppal.service.EnvironmentService
import com.httppal.util.HttpPalBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import javax.swing.*

/**
 * Dialog for configuring Postman export options
 */
class PostmanExportDialog(
    private val project: Project,
    private val itemCount: Int,
    private val onConfirm: (String, PostmanExportOptions) -> Unit
) : DialogWrapper(project) {
    
    private val collectionNameField = JBTextField()
    private val filePathField = TextFieldWithBrowseButton()
    private val applyEnvironmentCheckBox = JBCheckBox(
        HttpPalBundle.message("dialog.postman.export.apply.environment")
    )
    private val resolveVariablesCheckBox = JBCheckBox(
        HttpPalBundle.message("dialog.postman.export.resolve.variables")
    )
    private val environmentComboBox = JComboBox<EnvironmentItem>()
    
    private val environmentService: EnvironmentService by lazy {
        project.getService(EnvironmentService::class.java)
    }
    
    init {
        title = HttpPalBundle.message("dialog.postman.export.title")
        setSize(500, 350)
        init()
        
        setupListeners()
        loadEnvironments()
        updateEnvironmentFieldsState()
    }
    
    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.border = JBUI.Borders.empty(10)
        
        val formPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(5)
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.HORIZONTAL
        
        // Info label
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.gridwidth = 2
        val infoLabel = JBLabel(
            HttpPalBundle.message("dialog.postman.export.info", itemCount)
        )
        formPanel.add(infoLabel, gbc)
        
        // Collection name
        gbc.gridy++
        gbc.gridwidth = 1
        gbc.weightx = 0.0
        formPanel.add(JBLabel(HttpPalBundle.message("dialog.postman.export.collection.name")), gbc)
        
        gbc.gridx = 1
        gbc.weightx = 1.0
        collectionNameField.text = "HttpPal Collection"
        formPanel.add(collectionNameField, gbc)
        
        // File path
        gbc.gridx = 0
        gbc.gridy++
        gbc.weightx = 0.0
        formPanel.add(JBLabel(HttpPalBundle.message("dialog.postman.export.file.path")), gbc)
        
        gbc.gridx = 1
        gbc.weightx = 1.0
        val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        fileChooserDescriptor.title = HttpPalBundle.message("dialog.postman.export.file.chooser.title")
        fileChooserDescriptor.description = HttpPalBundle.message("dialog.postman.export.file.chooser.description")
        
        filePathField.addActionListener {
            val chooserDialog = com.intellij.openapi.fileChooser.FileChooser.chooseFiles(
                fileChooserDescriptor, 
                project, 
                null
            )
            
            chooserDialog?.firstOrNull()?.let { file ->
                filePathField.text = file.path
            }
        }
        filePathField.text = System.getProperty("user.home")
        formPanel.add(filePathField, gbc)
        
        // Separator
        gbc.gridx = 0
        gbc.gridy++
        gbc.gridwidth = 2
        formPanel.add(JSeparator(), gbc)
        
        // Apply environment checkbox
        gbc.gridy++
        applyEnvironmentCheckBox.isSelected = true
        formPanel.add(applyEnvironmentCheckBox, gbc)
        
        // Environment selection
        gbc.gridx = 0
        gbc.gridy++
        gbc.gridwidth = 1
        gbc.weightx = 0.0
        gbc.insets = JBUI.insets(5, 20, 5, 5)
        formPanel.add(JBLabel(HttpPalBundle.message("dialog.postman.export.environment")), gbc)
        
        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.insets = JBUI.insets(5)
        formPanel.add(environmentComboBox, gbc)
        
        // Resolve variables checkbox
        gbc.gridx = 0
        gbc.gridy++
        gbc.gridwidth = 2
        gbc.insets = JBUI.insets(5, 20, 5, 5)
        formPanel.add(resolveVariablesCheckBox, gbc)
        
        // Help text
        gbc.gridy++
        gbc.insets = JBUI.insets(10, 5, 5, 5)
        val helpLabel = JBLabel(
            "<html><i>" + HttpPalBundle.message("dialog.postman.export.help") + "</i></html>"
        )
        formPanel.add(helpLabel, gbc)
        
        mainPanel.add(formPanel, BorderLayout.NORTH)
        
        return mainPanel
    }
    
    private fun setupListeners() {
        applyEnvironmentCheckBox.addActionListener {
            updateEnvironmentFieldsState()
        }
        
        resolveVariablesCheckBox.addActionListener {
            if (resolveVariablesCheckBox.isSelected && !applyEnvironmentCheckBox.isSelected) {
                applyEnvironmentCheckBox.isSelected = true
                updateEnvironmentFieldsState()
            }
        }
    }
    
    private fun loadEnvironments() {
        environmentComboBox.removeAllItems()
        
        // Add "None" option
        environmentComboBox.addItem(EnvironmentItem(null, HttpPalBundle.message("environment.none")))
        
        // Add all environments
        environmentService.getAllEnvironments().forEach { env ->
            environmentComboBox.addItem(EnvironmentItem(env, env.name))
        }
        
        // Select active environment if exists
        val activeEnv = environmentService.getCurrentEnvironment()
        if (activeEnv != null) {
            for (i in 0 until environmentComboBox.itemCount) {
                val item = environmentComboBox.getItemAt(i)
                if (item.environment?.id == activeEnv.id) {
                    environmentComboBox.selectedIndex = i
                    break
                }
            }
        }
    }
    
    private fun updateEnvironmentFieldsState() {
        val enabled = applyEnvironmentCheckBox.isSelected
        environmentComboBox.isEnabled = enabled
        resolveVariablesCheckBox.isEnabled = enabled
        
        if (!enabled) {
            resolveVariablesCheckBox.isSelected = false
        }
    }
    
    override fun doValidate(): ValidationInfo? {
        val collectionName = collectionNameField.text.trim()
        if (collectionName.isEmpty()) {
            return ValidationInfo(
                HttpPalBundle.message("validation.field.required", "Collection name"),
                collectionNameField
            )
        }
        
        if (collectionName.length > 100) {
            return ValidationInfo(
                HttpPalBundle.message("dialog.postman.export.validation.name.length"),
                collectionNameField
            )
        }
        
        val filePath = filePathField.text.trim()
        if (filePath.isEmpty()) {
            return ValidationInfo(
                HttpPalBundle.message("validation.field.required", "File path"),
                filePathField
            )
        }
        
        val directory = File(filePath)
        if (!directory.exists() || !directory.isDirectory) {
            return ValidationInfo(
                HttpPalBundle.message("dialog.postman.export.validation.directory"),
                filePathField
            )
        }
        
        if (applyEnvironmentCheckBox.isSelected) {
            val selectedItem = environmentComboBox.selectedItem as? EnvironmentItem
            if (selectedItem?.environment == null) {
                return ValidationInfo(
                    HttpPalBundle.message("dialog.postman.export.validation.environment"),
                    environmentComboBox
                )
            }
        }
        
        return null
    }
    
    override fun doOKAction() {
        val validation = doValidate()
        if (validation != null) {
            return
        }
        
        val collectionName = collectionNameField.text.trim()
        val filePath = filePathField.text.trim()
        val selectedItem = environmentComboBox.selectedItem as? EnvironmentItem
        
        val options = PostmanExportOptions(
            applyEnvironment = applyEnvironmentCheckBox.isSelected,
            resolveVariables = resolveVariablesCheckBox.isSelected,
            includeHeaders = true,
            includeBody = true,
            preserveFolderStructure = true,
            environment = if (applyEnvironmentCheckBox.isSelected) selectedItem?.environment else null
        )
        
        // Build full file path
        val fileName = "$collectionName.postman_collection.json"
        val fullPath = File(filePath, fileName).absolutePath
        
        super.doOKAction()
        
        onConfirm(fullPath, options)
    }
    
    /**
     * Wrapper class for environment combo box items
     */
    private data class EnvironmentItem(
        val environment: Environment?,
        val displayName: String
    ) {
        override fun toString(): String = displayName
    }
}
