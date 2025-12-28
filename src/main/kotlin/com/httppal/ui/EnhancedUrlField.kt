package com.httppal.ui

import com.httppal.util.HttpPalBundle
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Enhanced URL input field with history, auto-complete, and validation
 * Implements requirements 3.1, 3.2, 3.3, 3.4, 3.5
 */
class EnhancedUrlField(private val project: Project) : JPanel(BorderLayout()) {
    
    private val urlTextField = JBTextField()
    private val historyButton = JButton("▼")
    private val validationIcon = JBLabel()
    
    // History management (LRU cache)
    private val urlHistory = object : LinkedHashMap<String, Long>(
        MAX_HISTORY_SIZE + 1,
        0.75f,
        true // Access order
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean {
            return size > MAX_HISTORY_SIZE
        }
    }
    
    // Auto-complete
    private var autoCompletePopup: JWindow? = null
    private var autoCompleteJob: Job? = null
    private val autoCompleteScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Validation state
    private var isValid: Boolean = true
    private var validationMessage: String? = null
    
    // Callbacks
    var onUrlChanged: ((String) -> Unit)? = null
    var onValidationChanged: ((Boolean, String?) -> Unit)? = null
    
    init {
        setupUI()
        setupEventHandlers()
    }
    
    private fun setupUI() {
        border = JBUI.Borders.empty()
        
        // URL text field
        urlTextField.columns = 50
        urlTextField.font = urlTextField.font.deriveFont(13f)
        urlTextField.toolTipText = HttpPalBundle.message("form.request.url.tooltip")
        add(urlTextField, BorderLayout.CENTER)
        
        // Right panel (validation icon + history button)
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0))
        rightPanel.border = JBUI.Borders.empty()
        
        // Validation icon
        validationIcon.preferredSize = Dimension(20, 20)
        validationIcon.isVisible = false
        rightPanel.add(validationIcon)
        
        // History button
        historyButton.preferredSize = Dimension(30, 25)
        historyButton.toolTipText = HttpPalBundle.message("url.field.history.tooltip")
        historyButton.isFocusable = false
        rightPanel.add(historyButton)
        
        add(rightPanel, BorderLayout.EAST)
    }
    
    private fun setupEventHandlers() {
        // Document listener for text changes
        urlTextField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onTextChanged()
            override fun removeUpdate(e: DocumentEvent?) = onTextChanged()
            override fun changedUpdate(e: DocumentEvent?) = onTextChanged()
        })
        
        // Focus listener
        urlTextField.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) {
                highlightPathParameters()
            }
            
            override fun focusLost(e: FocusEvent?) {
                hideAutoComplete()
            }
        })
        
        // History button
        historyButton.addActionListener {
            showHistory()
        }
    }
    
    private fun onTextChanged() {
        val text = urlTextField.text
        
        // Notify callback
        onUrlChanged?.invoke(text)
        
        // Trigger auto-complete with debounce
        if (text.length >= 3) {
            triggerAutoComplete(text)
        } else {
            hideAutoComplete()
        }
        
        // Highlight path parameters
        highlightPathParameters()
        
        // Validate URL
        validateUrl(text)
    }
    
    private fun triggerAutoComplete(text: String) {
        autoCompleteJob?.cancel()
        autoCompleteJob = autoCompleteScope.launch {
            delay(AUTO_COMPLETE_DEBOUNCE_MS)
            
            val suggestions = getSuggestions(text)
            
            withContext(Dispatchers.Main) {
                if (suggestions.isNotEmpty() && urlTextField.hasFocus()) {
                    showAutoComplete(suggestions)
                } else {
                    hideAutoComplete()
                }
            }
        }
    }
    
    private fun getSuggestions(text: String): List<String> {
        return urlHistory.keys
            .filter { it.contains(text, ignoreCase = true) }
            .take(5)
            .toList()
    }
    
    private fun showAutoComplete(suggestions: List<String>) {
        hideAutoComplete()
        
        if (suggestions.isEmpty()) return
        
        // Create popup window
        val popup = JWindow(SwingUtilities.getWindowAncestor(this))
        popup.type = Window.Type.POPUP
        popup.focusableWindowState = false
        
        // Create suggestion list
        val listModel = DefaultListModel<String>()
        suggestions.forEach { listModel.addElement(it) }
        
        val suggestionList = JList(listModel)
        suggestionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        suggestionList.font = urlTextField.font
        
        // Handle selection
        suggestionList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = suggestionList.selectedValue
                if (selected != null) {
                    urlTextField.text = selected
                    hideAutoComplete()
                    urlTextField.requestFocus()
                }
            }
        }
        
        // Add to popup
        val scrollPane = JScrollPane(suggestionList)
        scrollPane.preferredSize = Dimension(urlTextField.width, 100)
        popup.contentPane.add(scrollPane)
        popup.pack()
        
        // Position below text field
        val location = urlTextField.locationOnScreen
        popup.setLocation(location.x, location.y + urlTextField.height)
        
        popup.isVisible = true
        autoCompletePopup = popup
    }
    
    private fun hideAutoComplete() {
        autoCompletePopup?.isVisible = false
        autoCompletePopup?.dispose()
        autoCompletePopup = null
    }
    
    private fun showHistory() {
        if (urlHistory.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                HttpPalBundle.message("url.field.history.empty"),
                HttpPalBundle.message("url.field.history.title"),
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        
        val popup = JPopupMenu()
        
        // Add history items (most recent first)
        urlHistory.keys.reversed().take(MAX_HISTORY_SIZE).forEach { url ->
            val item = JMenuItem(url)
            item.addActionListener {
                urlTextField.text = url
                urlTextField.requestFocus()
            }
            popup.add(item)
        }
        
        // Add clear history option
        popup.addSeparator()
        val clearItem = JMenuItem(HttpPalBundle.message("url.field.history.clear"))
        clearItem.addActionListener {
            urlHistory.clear()
        }
        popup.add(clearItem)
        
        popup.show(historyButton, 0, historyButton.height)
    }
    
    private fun highlightPathParameters() {
        // Path parameter highlighting would require custom text field rendering
        // For now, we'll just detect them for validation
        val text = urlTextField.text
        val paramPattern = Regex("\\{([^}]+)\\}")
        val matches = paramPattern.findAll(text)
        
        // Store detected parameters for potential future use
        val parameters = matches.map { it.groupValues[1] }.toList()
        
        // Could add visual feedback here if needed
        // For example, changing text color or adding tooltips
    }
    
    private fun validateUrl(url: String) {
        if (url.isBlank()) {
            setValidationState(true, null)
            return
        }
        
        val errors = mutableListOf<String>()
        
        // Check if URL is valid format
        if (!isValidUrlFormat(url)) {
            errors.add(HttpPalBundle.message("url.field.validation.invalid.format"))
        }
        
        // Check if URL is relative and warn
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("{{")) {
            errors.add(HttpPalBundle.message("url.field.validation.relative.path"))
        }
        
        // Check for unmatched braces in variables
        val openBraces = url.count { it == '{' }
        val closeBraces = url.count { it == '}' }
        if (openBraces != closeBraces) {
            errors.add(HttpPalBundle.message("url.field.validation.unmatched.braces"))
        }
        
        if (errors.isNotEmpty()) {
            setValidationState(false, errors.joinToString("; "))
        } else {
            setValidationState(true, null)
        }
    }
    
    private fun isValidUrlFormat(url: String): Boolean {
        // Allow environment variables
        if (url.contains("{{") && url.contains("}}")) {
            return true
        }
        
        // Basic URL validation
        return try {
            // Check for valid characters
            val validPattern = Regex("^[a-zA-Z0-9:/?#\\[\\]@!$&'()*+,;=._~%-{}]+$")
            validPattern.matches(url)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun setValidationState(valid: Boolean, message: String?) {
        isValid = valid
        validationMessage = message
        
        // Update icon
        if (!valid && message != null) {
            validationIcon.icon = UIManager.getIcon("OptionPane.errorIcon")
            validationIcon.toolTipText = message
            validationIcon.isVisible = true
        } else {
            validationIcon.isVisible = false
        }
        
        // Notify callback
        onValidationChanged?.invoke(valid, message)
    }
    
    // Public API
    
    fun getText(): String = urlTextField.text
    
    fun setText(text: String) {
        urlTextField.text = text
    }
    
    fun addToHistory(url: String) {
        if (url.isNotBlank()) {
            urlHistory[url] = System.currentTimeMillis()
        }
    }
    
    fun getValidationState(): Pair<Boolean, String?> {
        return Pair(isValid, validationMessage)
    }
    
    fun requestFocusOnTextField() {
        urlTextField.requestFocus()
    }
    
    fun getTextField(): JBTextField = urlTextField
    
    // Cleanup
    fun dispose() {
        autoCompleteJob?.cancel()
        autoCompleteScope.cancel()
        hideAutoComplete()
    }
    
    companion object {
        private const val MAX_HISTORY_SIZE = 10
        private const val AUTO_COMPLETE_DEBOUNCE_MS = 300L
    }
}
