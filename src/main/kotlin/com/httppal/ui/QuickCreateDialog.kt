package com.httppal.ui

import com.httppal.model.HttpMethod
import com.httppal.model.RequestConfig
import com.httppal.util.HttpPalBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.*
import java.time.Duration
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Quick create dialog for creating HTTP requests with simplified interface
 * Implements requirements 4.1, 4.2, 4.3, 4.4, 4.5
 */
class QuickCreateDialog(
    private val project: Project,
    private val onConfirm: (RequestConfig) -> Unit
) : DialogWrapper(project) {
    
    private val methodComboBox = JComboBox(HttpMethod.values())
    private val urlField = JBTextField()
    
    // Common headers quick input
    private val contentTypeField = JBTextField()
    private val authorizationField = JBTextField()
    private val acceptField = JBTextField()
    
    init {
        title = HttpPalBundle.message("dialog.quick.create.title")
        setSize(600, 400)
        init()
        
        setupValidation()
    }
    
    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(GridBagLayout())
        mainPanel.border = JBUI.Borders.empty(15)
        
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(8)
        gbc.anchor = GridBagConstraints.WEST
        
        // Method selection
        gbc.gridx = 0; gbc.gridy = 0
        val methodLabel = JBLabel(HttpPalBundle.message("request.method.label"))
        methodLabel.font = methodLabel.font.deriveFont(Font.BOLD)
        mainPanel.add(methodLabel, gbc)
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        methodComboBox.preferredSize = Dimension(150, 30)
        mainPanel.add(methodComboBox, gbc)
        
        // URL input
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        val urlLabel = JBLabel(HttpPalBundle.message("request.url.label"))
        urlLabel.font = urlLabel.font.deriveFont(Font.BOLD)
        mainPanel.add(urlLabel, gbc)
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        urlField.preferredSize = Dimension(400, 30)
        urlField.toolTipText = HttpPalBundle.message("dialog.quick.create.url.tooltip")
        mainPanel.add(urlField, gbc)
        
        // Separator
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = JBUI.insets(15, 0, 10, 0)
        val separator = JSeparator()
        mainPanel.add(separator, gbc)
        
        // Common headers section
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2
        gbc.insets = JBUI.insets(5, 0, 10, 0)
        val headersLabel = JBLabel(HttpPalBundle.message("dialog.quick.create.common.headers"))
        headersLabel.font = headersLabel.font.deriveFont(Font.BOLD)
        mainPanel.add(headersLabel, gbc)
        
        // Content-Type
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        gbc.insets = JBUI.insets(5)
        mainPanel.add(JBLabel(HttpPalBundle.message("dialog.quick.create.content.type")), gbc)
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        contentTypeField.preferredSize = Dimension(400, 30)
        contentTypeField.toolTipText = HttpPalBundle.message("dialog.quick.create.content.type.tooltip")
        mainPanel.add(contentTypeField, gbc)
        
        // Authorization
        gbc.gridx = 0; gbc.gridy = 5; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        mainPanel.add(JBLabel(HttpPalBundle.message("dialog.quick.create.authorization")), gbc)
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        authorizationField.preferredSize = Dimension(400, 30)
        authorizationField.toolTipText = HttpPalBundle.message("dialog.quick.create.authorization.tooltip")
        mainPanel.add(authorizationField, gbc)
        
        // Accept
        gbc.gridx = 0; gbc.gridy = 6; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        mainPanel.add(JBLabel(HttpPalBundle.message("dialog.quick.create.accept")), gbc)
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        acceptField.preferredSize = Dimension(400, 30)
        acceptField.toolTipText = HttpPalBundle.message("dialog.quick.create.accept.tooltip")
        mainPanel.add(acceptField, gbc)
        
        // Info label
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2
        gbc.insets = JBUI.insets(15, 0, 0, 0)
        val infoLabel = JBLabel(HttpPalBundle.message("dialog.quick.create.info"))
        infoLabel.foreground = Color.GRAY
        infoLabel.font = infoLabel.font.deriveFont(Font.ITALIC, 11f)
        mainPanel.add(infoLabel, gbc)
        
        return mainPanel
    }
    
    private fun setupValidation() {
        // Add document listener to URL field to enable/disable OK button
        urlField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = validateInput()
            override fun removeUpdate(e: DocumentEvent?) = validateInput()
            override fun changedUpdate(e: DocumentEvent?) = validateInput()
        })
        
        // Initial validation
        validateInput()
    }
    
    private fun validateInput() {
        // Enable OK button only if URL is not empty
        isOKActionEnabled = urlField.text.isNotBlank()
    }
    
    override fun doValidate(): ValidationInfo? {
        // Validate URL field
        if (urlField.text.isBlank()) {
            return ValidationInfo(
                HttpPalBundle.message("dialog.quick.create.validation.url.empty"),
                urlField
            )
        }
        
        return null
    }
    
    override fun doOKAction() {
        val config = buildRequestConfig()
        onConfirm(config)
        super.doOKAction()
    }
    
    private fun buildRequestConfig(): RequestConfig {
        val method = methodComboBox.selectedItem as HttpMethod
        val url = urlField.text.trim()
        
        // Build headers map from common header fields
        val headers = mutableMapOf<String, String>()
        
        if (contentTypeField.text.isNotBlank()) {
            headers["Content-Type"] = contentTypeField.text.trim()
        }
        
        if (authorizationField.text.isNotBlank()) {
            headers["Authorization"] = authorizationField.text.trim()
        }
        
        if (acceptField.text.isNotBlank()) {
            headers["Accept"] = acceptField.text.trim()
        }
        
        return RequestConfig(
            method = method,
            url = url,
            headers = headers,
            body = null,
            timeout = Duration.ofSeconds(30),
            followRedirects = true
        )
    }
    
    // Public API for pre-filling fields
    
    fun setMethod(method: HttpMethod) {
        methodComboBox.selectedItem = method
    }
    
    fun setUrl(url: String) {
        urlField.text = url
    }
    
    fun setContentType(contentType: String) {
        contentTypeField.text = contentType
    }
    
    fun setAuthorization(authorization: String) {
        authorizationField.text = authorization
    }
    
    fun setAccept(accept: String) {
        acceptField.text = accept
    }
}
