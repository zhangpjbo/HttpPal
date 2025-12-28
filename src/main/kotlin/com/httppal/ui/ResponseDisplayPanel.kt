package com.httppal.ui

import com.httppal.model.HttpResponse
import com.httppal.util.ContentType
import com.httppal.util.HttpPalBundle
import com.httppal.util.ResponseFormatter
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * Response display panel that shows HTTP response details.
 * Displays response body, headers, and status information.
 */
class ResponseDisplayPanel : JPanel() {
    private val responseFormatter = ResponseFormatter()
    private val tabbedPane = JBTabbedPane()
    private val responseBodyArea = JTextArea()
    private val headersTable = JBTable()

    // UI Components
    private val statusLabel = JLabel(HttpPalBundle.message("response.no.response"))
    private val responseTimeLabel = JLabel(HttpPalBundle.message("response.time.label") + " -")
    private val responseSizeLabel = JLabel("Size: -")
    private val contentTypeLabel = JLabel("Content-Type: -")

    private val headersTableModel = DefaultTableModel(arrayOf(HttpPalBundle.message("headers.name.column"), HttpPalBundle.message("headers.value.column")), 0)

    // Store current response for reference
    private var currentResponse: HttpResponse? = null

    init {
        initializeComponents()
        setupLayout()
        clearResponse()
    }

    private fun initializeComponents() {
        // Configure response body text area
        responseBodyArea.apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            lineWrap = true
            wrapStyleWord = true
            background = UIManager.getColor("TextArea.background")
        }

        // Configure headers table
        headersTable.apply {
            model = headersTableModel
            autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
            fillsViewportHeight = true

            // Set column widths
            columnModel.getColumn(0).preferredWidth = 200
            columnModel.getColumn(1).preferredWidth = 400
        }
    }

    private fun setupLayout() {
        layout = BorderLayout()
        border = JBUI.Borders.empty(10)

        // Top panel with response status and timing information
        val statusPanel = createStatusPanel()
        add(statusPanel, BorderLayout.NORTH)

        // Main content area with tabs
        val contentPanel = createContentPanel()
        add(contentPanel, BorderLayout.CENTER)
    }

    private fun createStatusPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(0, 0, 10, 0)

        // Status line with code and text
        val statusLinePanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        statusLabel.font = statusLabel.font.deriveFont(Font.BOLD, 14f)
        statusLinePanel.add(statusLabel)
        panel.add(statusLinePanel)

        // Metrics line with timing and size information
        val metricsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        metricsPanel.add(responseTimeLabel)
        metricsPanel.add(Box.createHorizontalStrut(20))
        metricsPanel.add(responseSizeLabel)
        metricsPanel.add(Box.createHorizontalStrut(20))
        metricsPanel.add(contentTypeLabel)
        panel.add(metricsPanel)

        return panel
    }

    private fun createContentPanel(): JComponent {
        // Response Body tab with formatted content
        val bodyScrollPane = JBScrollPane(responseBodyArea)
        bodyScrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        bodyScrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        tabbedPane.addTab("Response Body", bodyScrollPane)
        
        // Response Headers tab with table display
        val headersScrollPane = JBScrollPane(headersTable)
        tabbedPane.addTab("Response Headers", headersScrollPane)
        
        return tabbedPane
    }

    /**
     * Display HTTP response with formatted body, headers, and timing information.
     * Implements requirements 4.5, 7.1, 7.2, 7.3, 7.4, 10.1, 10.2, 10.3, 10.4, 10.5.
     */
    fun displayResponse(response: HttpResponse) {
        // Store current response
        currentResponse = response

        // Update status information (Requirements 7.2, 7.3)
        updateStatusDisplay(response)

        // Display formatted response body using ResponseFormatter (Requirements 4.5, 10.1, 10.2, 10.3, 10.4, 10.5)
        displayFormattedResponseBody(response)

        // Display response headers (Requirement 7.2)
        displayResponseHeaders(response)

        // Switch to body tab by default
        tabbedPane.selectedIndex = 0
    }

    private fun updateStatusDisplay(response: HttpResponse) {
        // Status code and text with color coding
        val statusColor = when {
            response.isSuccessful() -> JBColor.GREEN // Green for success
            response.isClientError() -> JBColor.ORANGE // Orange for client errors
            response.isServerError() -> JBColor.RED // Red for server errors
            response.isRedirection() -> JBColor.BLUE // Blue for redirects
            else -> JBColor.BLACK // Black for informational
        }

        statusLabel.text = "${response.statusCode} ${response.statusText}"
        statusLabel.foreground = statusColor

        // Response time in milliseconds
        responseTimeLabel.text = "Time: ${response.responseTime.toMillis()}ms"

        // Response size
        val sizeInBytes = response.getBodySize()
        val sizeText = when {
            sizeInBytes < 1024 -> "${sizeInBytes}B"
            sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024}KB"
            else -> "${sizeInBytes / (1024 * 1024)}MB"
        }
        responseSizeLabel.text = "Size: $sizeText"

        // Content type with detected type information
        val detectedType = responseFormatter.detectContentType(response)
        val contentTypeHeader = response.getContentType() ?: "Unknown"
        contentTypeLabel.text = "Content-Type: $contentTypeHeader (${detectedType.name})"
    }

    /**
     * Format and display the response body based on its content type.
     * Implements requirements 10.1, 10.2, 10.3, 10.4, and 10.5.
     */
    private fun displayFormattedResponseBody(response: HttpResponse) {
        // Check if content is large and should be streamed (Requirement 10.5)
        if (responseFormatter.shouldStreamContent(response)) {
            val formattedContent = responseFormatter.formatLargeResponse(response)
            responseBodyArea.text = formattedContent
            responseBodyArea.caretPosition = 0
            return
        }
        
        // Format response using ResponseFormatter (Requirements 10.1, 10.2)
        val formattedResponse = responseFormatter.formatResponse(response)
        
        // Handle different content types (Requirements 10.3, 10.4)
        when (formattedResponse.contentType) {
            ContentType.HTML -> {
                // For HTML, provide formatted content
                val (preview, source) = responseFormatter.processHtmlContent(response.body)
                responseBodyArea.text = source // Show source by default
            }
            ContentType.PLAIN_TEXT -> {
                // Plain text display (Requirement 10.3)
                responseBodyArea.text = responseFormatter.formatPlainText(response.body)
            }
            ContentType.BINARY -> {
                // Binary content indication
                responseBodyArea.text = formattedResponse.formattedBody
            }
            else -> {
                // JSON, XML, or other formatted content (Requirements 10.1, 10.2)
                responseBodyArea.text = formattedResponse.formattedBody
                
                // Apply syntax highlighting if available (Requirement 10.2)
                if (formattedResponse.syntaxHighlighting != null) {
                    applySyntaxHighlighting(formattedResponse.syntaxHighlighting.language)
                } else {
                    // Switch back to text area for content without syntax highlighting
                    switchToTextArea()
                }
            }
        }
        
        responseBodyArea.caretPosition = 0 // Scroll to top
    }

    /**
     * Apply syntax highlighting to the response body.
     * This method determines the appropriate syntax highlighting based on the content type.
     */
    private fun applySyntaxHighlighting(language: String) {
        // For now, using a simple approach - in a full implementation, 
        // we would integrate with IntelliJ's syntax highlighting
        // by creating a proper editor component with syntax highlighting
        when (language.lowercase()) {
            "json", "javascript", "xml", "html", "css", "sql", "yaml", "properties" -> {
                // In a full implementation, use IntelliJ's editor with syntax highlighting
                applyIntelliJSyntaxHighlighting(language)
            }
            else -> {
                // For unsupported languages, use monospace font as fallback
                // Switch back to text area for non-highlighted content
                switchToTextArea()
                responseBodyArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            }
        }
    }

    private fun toIntelliJLanguage(language: String): com.intellij.lang.Language {
        // This function is no longer needed since EditorFactory.createEditor doesn't accept Language parameter
        // Instead, we rely on IntelliJ's automatic language detection based on content
        return getDefaultLanguage()
    }

    private fun getDefaultLanguage(): com.intellij.lang.Language {
        // Return a default language instance - plain text
        try {
            val plainTextLanguageClass = Class.forName("com.intellij.lang.PlainTextLanguage")
            val instanceField = plainTextLanguageClass.getDeclaredField("INSTANCE")
            return instanceField.get(null) as com.intellij.lang.Language
        } catch (e: Exception) {
            // If we can't find PlainTextLanguage, return a fallback
            try {
                val languageClass = Class.forName("com.intellij.lang.Language")
                // Create a fallback language instance
                return object : com.intellij.lang.Language("TEXT") {}
            } catch (e2: Exception) {
                // As a last resort, return a basic language object
                return object : com.intellij.lang.Language("TEXT") {}
            }
        }
    }

    /**
     * Apply IntelliJ's syntax highlighting to the response body.
     * This creates a proper editor component with syntax highlighting support.
     */
    private fun applyIntelliJSyntaxHighlighting(language: String) {
        val responseScrollPane = tabbedPane.getComponentAt(0) as? JBScrollPane
        if (responseScrollPane != null) {
            // Create an editor with syntax highlighting
            val editorFactory = EditorFactory.getInstance()
            // Normalize line separators to prevent AssertionError
            val normalizedText = responseBodyArea.text?.replace("\r\n", "\n") ?: ""
            val document = editorFactory.createDocument(normalizedText)

            val editor = editorFactory.createEditor(document) // Use simpler method

            // Configure the editor appearance to match our panel
            if (editor is EditorEx) {
                editor.settings.isLineNumbersShown = true
                editor.settings.isFoldingOutlineShown = true
                editor.settings.isVirtualSpace = false
                editor.settings.isUseSoftWraps = false
            }

            // Replace the text area with the editor component
            responseScrollPane.setViewportView(editor.contentComponent)

            // Store the original text area for later use if needed
            responseBodyArea.text = document.text
        }
    }

    /**
     * Switch back to the original text area component
     */
    private fun switchToTextArea() {
        // If we're currently using an editor component, dispose of it
        val currentViewport = (tabbedPane.getComponentAt(0) as? JBScrollPane)?.viewport?.view
        if (currentViewport != responseBodyArea) {
            // Dispose of the editor if it exists
            if (currentViewport is Editor) {
                val disposable = currentViewport as? com.intellij.openapi.Disposable
                if (disposable != null) {
                    Disposer.dispose(disposable)
                }
            }
        }
        
        // Switch back to text area
        val responseScrollPane = tabbedPane.getComponentAt(0) as? JBScrollPane
        responseScrollPane?.viewport?.view = responseBodyArea
    }

    private fun displayResponseHeaders(response: HttpResponse) {
        // Clear existing headers
        headersTableModel.rowCount = 0

        // Add all headers to the table
        response.headers.forEach { (name, values) ->
            values.forEach { value ->
                headersTableModel.addRow(arrayOf(name, value))
            }
        }

        // Update tab title with header count
        val headerCount = response.headers.size
        tabbedPane.setTitleAt(1, "Response Headers ($headerCount)")
    }

    /**
     * Clear the response display and reset to initial state.
     */
    fun clearResponse() {
        currentResponse = null

        statusLabel.text = "No response"
        statusLabel.foreground = JBColor.BLACK
        responseTimeLabel.text = "Time: -"
        responseSizeLabel.text = "Size: -"
        contentTypeLabel.text = "Content-Type: -"

        responseBodyArea.text = "No response body to display"

        headersTableModel.rowCount = 0
        tabbedPane.setTitleAt(1, "Response Headers")

        tabbedPane.selectedIndex = 0

        // Switch back to text area when clearing
        switchToTextArea()
    }

    /**
     * Show loading state while request is in progress.
     */
    fun showLoadingState() {
        statusLabel.text = "Loading..."
        statusLabel.foreground = JBColor.BLUE
        responseTimeLabel.text = "Time: -"
        responseSizeLabel.text = "Size: -"
        contentTypeLabel.text = "Content-Type: -"

        responseBodyArea.text = "Request in progress..."

        headersTableModel.rowCount = 0
        tabbedPane.setTitleAt(1, "Response Headers")

        // Switch back to text area when showing loading state
        switchToTextArea()
    }

    /**
     * Get the currently displayed response for external access.
     */
    fun getCurrentResponse(): HttpResponse? {
        return currentResponse
    }

    /**
     * Export response body content to string.
     */
    fun exportResponseBody(): String {
        return responseBodyArea.text
    }

    /**
     * Export response headers as map.
     */
    fun exportResponseHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        for (i in 0 until headersTableModel.rowCount) {
            val name = headersTableModel.getValueAt(i, 0) as String
            val value = headersTableModel.getValueAt(i, 1) as String
            headers[name] = value
        }
        return headers
    }
}