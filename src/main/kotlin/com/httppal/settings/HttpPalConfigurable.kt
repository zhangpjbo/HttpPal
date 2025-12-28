package com.httppal.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.httppal.util.HttpPalBundle
import com.httppal.service.SettingsExportImportService
import com.intellij.openapi.project.ProjectManager
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.time.Duration
import javax.swing.*

/**
 * Enhanced Configurable for HttpPal plugin settings with import/export functionality
 */
class HttpPalConfigurable : Configurable {
    
    private var settingsPanel: JPanel? = null
    
    // General Settings
    private var maxHistorySizeField: JBTextField? = null
    private var defaultTimeoutField: JBTextField? = null
    private var defaultThreadCountField: JBTextField? = null
    private var autoSaveRequestsCheckBox: JBCheckBox? = null
    private var enableHistoryPersistenceCheckBox: JBCheckBox? = null
    private var excludeSensitiveFromHistoryCheckBox: JBCheckBox? = null
    
    // Security Settings
    private var sensitiveHeadersField: JBTextField? = null
    
    // Performance Settings
    private var maxConcurrentRequestsField: JBTextField? = null
    private var connectionPoolSizeField: JBTextField? = null
    private var requestRetryCountField: JBTextField? = null
    
    // UI Settings
    private var enableSyntaxHighlightingCheckBox: JBCheckBox? = null
    private var autoFormatResponseCheckBox: JBCheckBox? = null
    private var showResponseTimeCheckBox: JBCheckBox? = null
    
    // Import/Export
    private var exportPathField: TextFieldWithBrowseButton? = null
    private var importPathField: TextFieldWithBrowseButton? = null
    private var exportButton: JButton? = null
    private var importButton: JButton? = null
    
    // Statistics
    private var statisticsLabel: JBLabel? = null
    
    private val settings: HttpPalSettings
        get() = HttpPalSettings.getInstance()
    
    override fun getDisplayName(): String = "HttpPal"
    
    override fun createComponent(): JComponent? {
        if (settingsPanel == null) {
            createSettingsPanel()
        }
        return settingsPanel
    }
    
    private fun createSettingsPanel() {
        initializeFields()
        
        val tabbedPane = JTabbedPane()
        
        // General Settings Tab
        tabbedPane.addTab(HttpPalBundle.message("settings.tab.general"), createGeneralSettingsPanel())
        
        // Performance Settings Tab
        tabbedPane.addTab(HttpPalBundle.message("settings.tab.performance"), createPerformanceSettingsPanel())
        
        // Security Settings Tab
        tabbedPane.addTab(HttpPalBundle.message("settings.tab.security"), createSecuritySettingsPanel())
        
        // UI Settings Tab
        tabbedPane.addTab(HttpPalBundle.message("settings.tab.ui"), createUISettingsPanel())
        
        // Import/Export Tab
        tabbedPane.addTab(HttpPalBundle.message("settings.tab.import.export"), createImportExportPanel())
        
        // Statistics Tab
        tabbedPane.addTab(HttpPalBundle.message("settings.tab.statistics"), createStatisticsPanel())
        
        settingsPanel = JPanel(BorderLayout())
        settingsPanel!!.add(tabbedPane, BorderLayout.CENTER)
        
        setupEventListeners()
    }
    
    private fun initializeFields() {
        // General Settings
        maxHistorySizeField = JBTextField(settings.getMaxHistorySize().toString())
        defaultTimeoutField = JBTextField(settings.getDefaultTimeout().seconds.toString())
        defaultThreadCountField = JBTextField(settings.getDefaultThreadCount().toString())
        autoSaveRequestsCheckBox = JBCheckBox("Auto-save requests to history", settings.isAutoSaveRequests())
        enableHistoryPersistenceCheckBox = JBCheckBox("Enable history persistence", settings.isHistoryPersistenceEnabled())
        excludeSensitiveFromHistoryCheckBox = JBCheckBox("Exclude sensitive headers from history", settings.isExcludeSensitiveFromHistory())
        
        // Security Settings
        sensitiveHeadersField = JBTextField(settings.getSensitiveHeaderNames().joinToString(", "))
        
        // Performance Settings (load from settings)
        maxConcurrentRequestsField = JBTextField(settings.getMaxConcurrentRequests().toString())
        connectionPoolSizeField = JBTextField(settings.getConnectionPoolSize().toString())
        requestRetryCountField = JBTextField(settings.getRequestRetryCount().toString())
        
        // UI Settings (load from settings)
        enableSyntaxHighlightingCheckBox = JBCheckBox("Enable syntax highlighting", settings.isSyntaxHighlightingEnabled())
        autoFormatResponseCheckBox = JBCheckBox("Auto-format JSON/XML responses", settings.isAutoFormatResponseEnabled())
        showResponseTimeCheckBox = JBCheckBox("Show response time in results", settings.isShowResponseTimeEnabled())
        
        // Import/Export
        exportPathField = TextFieldWithBrowseButton()
        exportPathField!!.addBrowseFolderListener(
            TextBrowseFolderListener(
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
            )
        )
        
        importPathField = TextFieldWithBrowseButton()
        importPathField!!.addBrowseFolderListener(
            TextBrowseFolderListener(
                FileChooserDescriptorFactory.createSingleFileDescriptor("json")
            )
        )
        
        exportButton = JButton(HttpPalBundle.message("settings.button.export"))
        importButton = JButton(HttpPalBundle.message("settings.button.import"))
        
        // Statistics
        statisticsLabel = JBLabel()
        updateStatistics()
    }
    
    private fun createGeneralSettingsPanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Max history size:"), maxHistorySizeField!!, 1, false)
            .addTooltip("Maximum number of requests to keep in history (1-10000)")
            .addLabeledComponent(JBLabel("Default timeout (seconds):"), defaultTimeoutField!!, 1, false)
            .addTooltip("Default timeout for HTTP requests in seconds (1-300)")
            .addLabeledComponent(JBLabel("Default thread count:"), defaultThreadCountField!!, 1, false)
            .addTooltip("Default number of threads for concurrent execution (1-100)")
            .addComponent(enableHistoryPersistenceCheckBox!!, 1)
            .addComponent(autoSaveRequestsCheckBox!!, 1)
            .addComponent(excludeSensitiveFromHistoryCheckBox!!, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }
    
    private fun createPerformanceSettingsPanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Max concurrent requests:"), maxConcurrentRequestsField!!, 1, false)
            .addTooltip("Maximum number of concurrent HTTP requests (1-100)")
            .addLabeledComponent(JBLabel("Connection pool size:"), connectionPoolSizeField!!, 1, false)
            .addTooltip("HTTP connection pool size (1-50)")
            .addLabeledComponent(JBLabel("Request retry count:"), requestRetryCountField!!, 1, false)
            .addTooltip("Number of retries for failed requests (0-10)")
            .addComponent(createRecommendedSettingsPanel(), 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }
    
    private fun createRecommendedSettingsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        val recommendedButton = JButton(HttpPalBundle.message("settings.performance.apply.recommended"))
        recommendedButton.addActionListener { applyRecommendedSettings() }
        
        val infoLabel = JBLabel("<html><i>Click to apply system-optimized performance settings</i></html>")
        
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        buttonPanel.add(recommendedButton)
        buttonPanel.add(infoLabel)
        
        panel.add(buttonPanel, BorderLayout.NORTH)
        return panel
    }
    
    private fun createSecuritySettingsPanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Sensitive header names:"), sensitiveHeadersField!!, 1, false)
            .addTooltip("Comma-separated list of header names that should be stored securely")
            .addComponent(JBLabel("<html><i>Sensitive headers are stored using IDE's secure storage and excluded from exports</i></html>"), 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }
    
    private fun createUISettingsPanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addComponent(enableSyntaxHighlightingCheckBox!!, 1)
            .addComponent(autoFormatResponseCheckBox!!, 1)
            .addComponent(showResponseTimeCheckBox!!, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }
    
    private fun createImportExportPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        val formPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Export to:"), exportPathField!!, 1, false)
            .addLabeledComponent(JBLabel("Import from:"), importPathField!!, 1, false)
            .panel
        
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        buttonPanel.add(exportButton!!)
        buttonPanel.add(importButton!!)
        
        val infoPanel = JPanel(BorderLayout())
        infoPanel.add(JBLabel("<html><b>Export/Import Information:</b><br>" +
                "• Exports include all non-sensitive settings and configurations<br>" +
                "• Sensitive headers and credentials are not included in exports<br>" +
                "• Import will merge settings with existing configuration<br>" +
                "• Backup your settings before importing</html>"), BorderLayout.CENTER)
        infoPanel.border = JBUI.Borders.empty(10)
        
        panel.add(formPanel, BorderLayout.NORTH)
        panel.add(buttonPanel, BorderLayout.CENTER)
        panel.add(infoPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun createStatisticsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(statisticsLabel!!, BorderLayout.NORTH)
        panel.add(JPanel(), BorderLayout.CENTER) // Filler
        return panel
    }
    
    private fun setupEventListeners() {
        // History persistence dependency
        enableHistoryPersistenceCheckBox!!.addActionListener {
            val enabled = enableHistoryPersistenceCheckBox!!.isSelected
            autoSaveRequestsCheckBox!!.isEnabled = enabled
            excludeSensitiveFromHistoryCheckBox!!.isEnabled = enabled
            maxHistorySizeField!!.isEnabled = enabled
        }
        
        // Initialize dependent state
        val historyEnabled = enableHistoryPersistenceCheckBox!!.isSelected
        autoSaveRequestsCheckBox!!.isEnabled = historyEnabled
        excludeSensitiveFromHistoryCheckBox!!.isEnabled = historyEnabled
        maxHistorySizeField!!.isEnabled = historyEnabled
        
        // Export button
        exportButton!!.addActionListener { exportSettings() }
        
        // Import button
        importButton!!.addActionListener { importSettings() }
    }
    
    private fun updateStatistics() {
        val stats = settings.getStatistics()
        val html = buildString {
            append("<html><b>HttpPal Statistics:</b><br><br>")
            append("History entries: ${stats["historyCount"]}<br>")
            append("Favorite requests: ${stats["favoritesCount"]}<br>")
            append("Environments: ${stats["environmentsCount"]}<br>")
            append("Global headers: ${stats["globalHeadersCount"]}<br>")
            append("Sensitive headers: ${stats["sensitiveHeadersCount"]}<br>")
            append("History usage: ${stats["historyUsagePercent"]}%<br>")
            append("</html>")
        }
        statisticsLabel!!.text = html
    }
    
    override fun isModified(): Boolean {
        return try {
            // General settings
            val currentMaxHistorySize = maxHistorySizeField?.text?.toIntOrNull() ?: settings.getMaxHistorySize()
            val currentTimeout = defaultTimeoutField?.text?.toLongOrNull() ?: settings.getDefaultTimeout().seconds
            val currentThreadCount = defaultThreadCountField?.text?.toIntOrNull() ?: settings.getDefaultThreadCount()
            val currentAutoSave = autoSaveRequestsCheckBox?.isSelected ?: settings.isAutoSaveRequests()
            val currentHistoryPersistence = enableHistoryPersistenceCheckBox?.isSelected ?: settings.isHistoryPersistenceEnabled()
            val currentExcludeSensitive = excludeSensitiveFromHistoryCheckBox?.isSelected ?: settings.isExcludeSensitiveFromHistory()
            val currentSensitiveHeaders = sensitiveHeadersField?.text?.split(",")?.map { it.trim() }?.toSet() ?: settings.getSensitiveHeaderNames()
            
            // Performance settings
            val currentMaxConcurrent = maxConcurrentRequestsField?.text?.toIntOrNull() ?: settings.getMaxConcurrentRequests()
            val currentPoolSize = connectionPoolSizeField?.text?.toIntOrNull() ?: settings.getConnectionPoolSize()
            val currentRetryCount = requestRetryCountField?.text?.toIntOrNull() ?: settings.getRequestRetryCount()
            
            // UI settings
            val currentSyntaxHighlighting = enableSyntaxHighlightingCheckBox?.isSelected ?: settings.isSyntaxHighlightingEnabled()
            val currentAutoFormat = autoFormatResponseCheckBox?.isSelected ?: settings.isAutoFormatResponseEnabled()
            val currentShowResponseTime = showResponseTimeCheckBox?.isSelected ?: settings.isShowResponseTimeEnabled()
            
            // Check if any setting has changed
            currentMaxHistorySize != settings.getMaxHistorySize() ||
            currentTimeout != settings.getDefaultTimeout().seconds ||
            currentThreadCount != settings.getDefaultThreadCount() ||
            currentAutoSave != settings.isAutoSaveRequests() ||
            currentHistoryPersistence != settings.isHistoryPersistenceEnabled() ||
            currentExcludeSensitive != settings.isExcludeSensitiveFromHistory() ||
            currentSensitiveHeaders != settings.getSensitiveHeaderNames() ||
            currentMaxConcurrent != settings.getMaxConcurrentRequests() ||
            currentPoolSize != settings.getConnectionPoolSize() ||
            currentRetryCount != settings.getRequestRetryCount() ||
            currentSyntaxHighlighting != settings.isSyntaxHighlightingEnabled() ||
            currentAutoFormat != settings.isAutoFormatResponseEnabled() ||
            currentShowResponseTime != settings.isShowResponseTimeEnabled()
        } catch (e: Exception) {
            false
        }
    }
    
    override fun apply() {
        val validationErrors = validateSettings()
        if (validationErrors.isNotEmpty()) {
            JOptionPane.showMessageDialog(
                settingsPanel,
                "Validation errors:\n${validationErrors.joinToString("\n")}",
                "Settings Validation Error",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }
        
        try {
            // General settings
            maxHistorySizeField?.text?.toIntOrNull()?.let { size ->
                settings.setMaxHistorySize(size)
            }
            
            defaultTimeoutField?.text?.toLongOrNull()?.let { timeout ->
                settings.setDefaultTimeout(Duration.ofSeconds(timeout))
            }
            
            defaultThreadCountField?.text?.toIntOrNull()?.let { count ->
                settings.setDefaultThreadCount(count)
            }
            
            autoSaveRequestsCheckBox?.isSelected?.let { autoSave ->
                settings.setAutoSaveRequests(autoSave)
            }
            
            enableHistoryPersistenceCheckBox?.isSelected?.let { enabled ->
                settings.setHistoryPersistenceEnabled(enabled)
            }
            
            excludeSensitiveFromHistoryCheckBox?.isSelected?.let { exclude ->
                settings.setExcludeSensitiveFromHistory(exclude)
            }
            
            // Security settings
            sensitiveHeadersField?.text?.let { headersText ->
                val headerNames = headersText.split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .toSet()
                
                // Clear existing and add new ones
                val currentHeaders = settings.getSensitiveHeaderNames().toList()
                currentHeaders.forEach { settings.removeSensitiveHeaderName(it) }
                headerNames.forEach { settings.addSensitiveHeaderName(it) }
            }
            
            // Performance settings
            maxConcurrentRequestsField?.text?.toIntOrNull()?.let { count ->
                settings.setMaxConcurrentRequests(count)
            }
            
            connectionPoolSizeField?.text?.toIntOrNull()?.let { size ->
                settings.setConnectionPoolSize(size)
            }
            
            requestRetryCountField?.text?.toIntOrNull()?.let { count ->
                settings.setRequestRetryCount(count)
            }
            
            // UI settings
            enableSyntaxHighlightingCheckBox?.isSelected?.let { enabled ->
                settings.setSyntaxHighlightingEnabled(enabled)
            }
            
            autoFormatResponseCheckBox?.isSelected?.let { enabled ->
                settings.setAutoFormatResponseEnabled(enabled)
            }
            
            showResponseTimeCheckBox?.isSelected?.let { enabled ->
                settings.setShowResponseTimeEnabled(enabled)
            }
            
            // Update statistics after applying changes
            updateStatistics()
            
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                settingsPanel,
                "Error saving settings: ${e.message}",
                "Settings Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
    
    private fun validateSettings(): List<String> {
        val maxHistorySize = maxHistorySizeField?.text?.toIntOrNull()
        val defaultTimeout = defaultTimeoutField?.text?.toLongOrNull()
        val defaultThreadCount = defaultThreadCountField?.text?.toIntOrNull()
        val maxConcurrentRequests = maxConcurrentRequestsField?.text?.toIntOrNull()
        val connectionPoolSize = connectionPoolSizeField?.text?.toIntOrNull()
        val requestRetryCount = requestRetryCountField?.text?.toIntOrNull()
        
        val validationResult = SettingsValidator.validateSettings(
            maxHistorySize,
            defaultTimeout,
            defaultThreadCount,
            maxConcurrentRequests,
            connectionPoolSize,
            requestRetryCount
        )
        
        val errors = validationResult.errors.toMutableList()
        
        // Additional validation for null values
        if (maxHistorySize == null) errors.add("Max history size must be a valid number")
        if (defaultTimeout == null) errors.add("Default timeout must be a valid number")
        if (defaultThreadCount == null) errors.add("Default thread count must be a valid number")
        if (maxConcurrentRequests == null) errors.add("Max concurrent requests must be a valid number")
        if (connectionPoolSize == null) errors.add("Connection pool size must be a valid number")
        if (requestRetryCount == null) errors.add("Request retry count must be a valid number")
        
        // Validate sensitive headers
        sensitiveHeadersField?.text?.let { headersText ->
            val headerNames = headersText.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val headerValidation = SettingsValidator.validateSensitiveHeaders(headerNames)
            errors.addAll(headerValidation.errors)
        }
        
        return errors
    }
    
    override fun reset() {
        // General settings
        maxHistorySizeField?.text = settings.getMaxHistorySize().toString()
        defaultTimeoutField?.text = settings.getDefaultTimeout().seconds.toString()
        defaultThreadCountField?.text = settings.getDefaultThreadCount().toString()
        autoSaveRequestsCheckBox?.isSelected = settings.isAutoSaveRequests()
        enableHistoryPersistenceCheckBox?.isSelected = settings.isHistoryPersistenceEnabled()
        excludeSensitiveFromHistoryCheckBox?.isSelected = settings.isExcludeSensitiveFromHistory()
        
        // Security settings
        sensitiveHeadersField?.text = settings.getSensitiveHeaderNames().joinToString(", ")
        
        // Performance settings (load from settings)
        maxConcurrentRequestsField?.text = settings.getMaxConcurrentRequests().toString()
        connectionPoolSizeField?.text = settings.getConnectionPoolSize().toString()
        requestRetryCountField?.text = settings.getRequestRetryCount().toString()
        
        // UI settings (load from settings)
        enableSyntaxHighlightingCheckBox?.isSelected = settings.isSyntaxHighlightingEnabled()
        autoFormatResponseCheckBox?.isSelected = settings.isAutoFormatResponseEnabled()
        showResponseTimeCheckBox?.isSelected = settings.isShowResponseTimeEnabled()
        
        // Clear import/export paths
        exportPathField?.text = ""
        importPathField?.text = ""
        
        // Update dependent state
        val historyEnabled = settings.isHistoryPersistenceEnabled()
        autoSaveRequestsCheckBox?.isEnabled = historyEnabled
        excludeSensitiveFromHistoryCheckBox?.isEnabled = historyEnabled
        maxHistorySizeField?.isEnabled = historyEnabled
        
        // Update statistics
        updateStatistics()
    }
    
    private fun applyRecommendedSettings() {
        val recommended = SettingsValidator.getRecommendedSettings()
        
        // Apply recommended performance settings
        maxConcurrentRequestsField?.text = recommended["maxConcurrentRequests"].toString()
        connectionPoolSizeField?.text = recommended["connectionPoolSize"].toString()
        requestRetryCountField?.text = recommended["requestRetryCount"].toString()
        defaultThreadCountField?.text = recommended["defaultThreadCount"].toString()
        defaultTimeoutField?.text = recommended["defaultTimeout"].toString()
        maxHistorySizeField?.text = recommended["maxHistorySize"].toString()
        
        JOptionPane.showMessageDialog(
            settingsPanel,
            "Recommended settings have been applied based on your system capabilities.\n" +
            "Click 'Apply' to save these settings.",
            "Recommended Settings Applied",
            JOptionPane.INFORMATION_MESSAGE
        )
    }
    
    override fun disposeUIResources() {
        settingsPanel = null
        maxHistorySizeField = null
        defaultTimeoutField = null
        defaultThreadCountField = null
        autoSaveRequestsCheckBox = null
        enableHistoryPersistenceCheckBox = null
        excludeSensitiveFromHistoryCheckBox = null
        sensitiveHeadersField = null
        maxConcurrentRequestsField = null
        connectionPoolSizeField = null
        requestRetryCountField = null
        enableSyntaxHighlightingCheckBox = null
        autoFormatResponseCheckBox = null
        showResponseTimeCheckBox = null
        exportPathField = null
        importPathField = null
        exportButton = null
        importButton = null
        statisticsLabel = null
    }
    
    /**
     * Export settings to a JSON file
     */
    private fun exportSettings() {
        val exportPath = exportPathField?.text
        if (exportPath.isNullOrBlank()) {
            JOptionPane.showMessageDialog(
                settingsPanel,
                "Please select an export directory",
                "Export Error",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        
        // Show export options dialog
        val options = arrayOf("Application Settings Only", "Complete Settings (with Project)", "Cancel")
        val choice = JOptionPane.showOptionDialog(
            settingsPanel,
            "Choose what to export:",
            "Export Options",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        )
        
        if (choice == 2) return // Cancel
        
        try {
            val exportService = SettingsExportImportService.getInstance()
            val result = if (choice == 0) {
                // Application settings only
                exportService.exportApplicationSettings(exportPath)
            } else {
                // Complete settings with project
                val project = ProjectManager.getInstance().openProjects.firstOrNull()
                if (project != null) {
                    exportService.exportAllSettings(project, exportPath)
                } else {
                    exportService.exportApplicationSettings(exportPath)
                }
            }
            
            if (result.success) {
                JOptionPane.showMessageDialog(
                    settingsPanel,
                    "Settings exported successfully to:\n${result.filePath}",
                    "Export Successful",
                    JOptionPane.INFORMATION_MESSAGE
                )
            } else {
                JOptionPane.showMessageDialog(
                    settingsPanel,
                    result.error ?: "Unknown export error",
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                settingsPanel,
                "Error exporting settings: ${e.message}",
                "Export Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
    
    /**
     * Import settings from a JSON file
     */
    private fun importSettings() {
        val importPath = importPathField?.text
        if (importPath.isNullOrBlank()) {
            JOptionPane.showMessageDialog(
                settingsPanel,
                "Please select a file to import",
                "Import Error",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        
        // Validate file first
        val exportService = SettingsExportImportService.getInstance()
        val validationErrors = exportService.validateExportFile(importPath)
        
        if (validationErrors.isNotEmpty()) {
            val errorMessage = "File validation failed:\n${validationErrors.joinToString("\n")}"
            JOptionPane.showMessageDialog(
                settingsPanel,
                errorMessage,
                "Import Validation Error",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }
        
        val confirm = JOptionPane.showConfirmDialog(
            settingsPanel,
            "Importing will merge settings with your current configuration.\n" +
            "This operation cannot be undone.\n\n" +
            "Do you want to continue?",
            "Confirm Import",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        )
        
        if (confirm != JOptionPane.YES_OPTION) {
            return
        }
        
        try {
            val project = ProjectManager.getInstance().openProjects.firstOrNull()
            val result = exportService.importSettings(importPath, project)
            
            if (result.success) {
                // Reload UI with imported settings
                reset()
                
                val message = buildString {
                    append("Settings imported successfully!\n\n")
                    append("Imported: ${result.importedSettings} settings\n")
                    if (result.skippedSettings > 0) {
                        append("Skipped: ${result.skippedSettings} settings\n")
                    }
                    if (result.errors.isNotEmpty()) {
                        append("\nWarnings:\n${result.errors.joinToString("\n")}")
                    }
                }
                
                JOptionPane.showMessageDialog(
                    settingsPanel,
                    message,
                    "Import Successful",
                    JOptionPane.INFORMATION_MESSAGE
                )
            } else {
                JOptionPane.showMessageDialog(
                    settingsPanel,
                    "Import failed:\n${result.errors.joinToString("\n")}",
                    "Import Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                settingsPanel,
                "Error importing settings: ${e.message}",
                "Import Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
    

}