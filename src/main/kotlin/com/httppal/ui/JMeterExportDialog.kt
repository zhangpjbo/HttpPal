package com.httppal.ui

import com.httppal.model.*
import com.httppal.service.JMeterExportService
import com.httppal.service.impl.JMeterExportServiceImpl
import com.httppal.util.HttpPalBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserDialog
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import java.awt.*
import com.intellij.openapi.ui.DialogWrapper
import java.awt.event.ActionEvent
import java.io.File
import javax.swing.*
import javax.swing.border.TitledBorder

/**
 * Dialog for exporting HTTP requests to JMeter .jmx format
 * Implements requirements 11.1, 11.2, 11.5
 */
class JMeterExportDialog(
    private val project: Project,
    private val requests: List<RequestConfig>,
    private val currentEnvironment: Environment?
) : DialogWrapper(project, true) {
    
    private val jmeterExportService: JMeterExportService = JMeterExportServiceImpl()
    
    // Export type selection
    private val singleRequestRadio = JRadioButton(HttpPalBundle.message("jmeter.export.single"), requests.size == 1)
    private val multipleRequestsRadio = JRadioButton(HttpPalBundle.message("jmeter.export.multiple"), requests.size > 1)
    private val concurrentScenarioRadio = JRadioButton(HttpPalBundle.message("jmeter.export.concurrent"))
    
    // Concurrent scenario options
    private val threadCountSpinner = JSpinner(SpinnerNumberModel(10, 1, 1000, 1))
    private val iterationsSpinner = JSpinner(SpinnerNumberModel(1, 1, 10000, 1))
    private val rampUpSpinner = JSpinner(SpinnerNumberModel(1, 0, 300, 1))
    
    // File selection
    private val filePathField = JBTextField()
    private val browseButton = JButton(HttpPalBundle.message("jmeter.export.browse.button"))
    
    // Environment options
    private val includeEnvironmentCheckBox = JCheckBox(HttpPalBundle.message("jmeter.export.include.environment"), currentEnvironment != null)
    private val environmentInfoLabel = JBLabel()
    
    // Request selection (for multiple requests)
    private val requestSelectionPanel = JPanel()
    private val requestCheckBoxes = mutableListOf<JCheckBox>()
    
    // Preview
    private val previewArea = JBTextArea()
    
    init {
        title = HttpPalBundle.message("jmeter.export.title")
        init()
        setupUI()
        setupEventHandlers()
        updateUI()
    }
    
    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.preferredSize = Dimension(600, 500)
        
        // Create tabbed pane for better organization
        val tabbedPane = JBTabbedPane()
        
        // Export Configuration Tab
        val configPanel = createConfigurationPanel()
        tabbedPane.addTab("Configuration", configPanel)
        
        // Preview Tab
        val previewPanel = createPreviewPanel()
        tabbedPane.addTab("Preview", previewPanel)
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER)
        
        return mainPanel
    }
    
    private fun createConfigurationPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)
        
        // Main configuration panel
        val configPanel = JPanel()
        configPanel.layout = BoxLayout(configPanel, BoxLayout.Y_AXIS)
        
        // Export type selection
        val exportTypePanel = createExportTypePanel()
        configPanel.add(exportTypePanel)
        configPanel.add(Box.createVerticalStrut(10))
        
        // Concurrent scenario options
        val concurrentOptionsPanel = createConcurrentOptionsPanel()
        configPanel.add(concurrentOptionsPanel)
        configPanel.add(Box.createVerticalStrut(10))
        
        // Request selection (for multiple requests)
        if (requests.size > 1) {
            val requestSelectionPanel = createRequestSelectionPanel()
            configPanel.add(requestSelectionPanel)
            configPanel.add(Box.createVerticalStrut(10))
        }
        
        // Environment options
        val environmentPanel = createEnvironmentPanel()
        configPanel.add(environmentPanel)
        configPanel.add(Box.createVerticalStrut(10))
        
        // File selection
        val filePanel = createFileSelectionPanel()
        configPanel.add(filePanel)
        
        panel.add(configPanel, BorderLayout.NORTH)
        
        return panel
    }
    
    private fun createExportTypePanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = TitledBorder(HttpPalBundle.message("dialog.jmeter.export.type.title"))
        
        // Radio button group
        val buttonGroup = ButtonGroup()
        buttonGroup.add(singleRequestRadio)
        buttonGroup.add(multipleRequestsRadio)
        buttonGroup.add(concurrentScenarioRadio)
        
        // Enable/disable based on request count
        singleRequestRadio.isEnabled = requests.size == 1
        multipleRequestsRadio.isEnabled = requests.size > 1
        
        // Default selection
        when {
            requests.size == 1 -> singleRequestRadio.isSelected = true
            requests.size > 1 -> multipleRequestsRadio.isSelected = true
            else -> singleRequestRadio.isSelected = true
        }
        
        panel.add(singleRequestRadio)
        panel.add(multipleRequestsRadio)
        panel.add(concurrentScenarioRadio)
        
        // Add descriptions
        val descPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val descLabel = JBLabel("<html><i>" + HttpPalBundle.message("dialog.jmeter.export.description") + "</i></html>")
        descLabel.foreground = Color.GRAY
        descPanel.add(descLabel)
        panel.add(descPanel)
        
        return panel
    }
    
    private fun createConcurrentOptionsPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = TitledBorder(HttpPalBundle.message("dialog.jmeter.export.concurrent.title"))
        
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(5)
        gbc.anchor = GridBagConstraints.WEST
        
        // Thread count
        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JBLabel(HttpPalBundle.message("concurrent.thread.count.label")), gbc)
        gbc.gridx = 1
        threadCountSpinner.preferredSize = Dimension(80, threadCountSpinner.preferredSize.height)
        panel.add(threadCountSpinner, gbc)
        gbc.gridx = 2
        panel.add(JBLabel(HttpPalBundle.message("concurrent.thread.count.description")), gbc)
        
        // Iterations
        gbc.gridx = 0; gbc.gridy = 1
        panel.add(JBLabel(HttpPalBundle.message("concurrent.iterations.label")), gbc)
        gbc.gridx = 1
        iterationsSpinner.preferredSize = Dimension(80, iterationsSpinner.preferredSize.height)
        panel.add(iterationsSpinner, gbc)
        gbc.gridx = 2
        panel.add(JBLabel(HttpPalBundle.message("concurrent.iterations.description")), gbc)
        
        // Ramp-up period
        gbc.gridx = 0; gbc.gridy = 2
        panel.add(JBLabel(HttpPalBundle.message("concurrent.ramp.up.label")), gbc)
        gbc.gridx = 1
        rampUpSpinner.preferredSize = Dimension(80, rampUpSpinner.preferredSize.height)
        panel.add(rampUpSpinner, gbc)
        gbc.gridx = 2
        panel.add(JBLabel(HttpPalBundle.message("concurrent.ramp.up.description")), gbc)
        
        return panel
    }
    
    private fun createRequestSelectionPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = TitledBorder(HttpPalBundle.message("dialog.jmeter.export.selection.title"))
        
        val selectionPanel = JPanel()
        selectionPanel.layout = BoxLayout(selectionPanel, BoxLayout.Y_AXIS)
        
        // Add checkboxes for each request
        requestCheckBoxes.clear()
        requests.forEachIndexed { index, request ->
            val checkBox = JCheckBox("${request.method} ${request.url}", true)
            checkBox.toolTipText = HttpPalBundle.message("tooltip.request.include")
            requestCheckBoxes.add(checkBox)
            selectionPanel.add(checkBox)
        }
        
        // Select/Deselect all buttons
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val selectAllButton = JButton(HttpPalBundle.message("button.select.all"))
        val deselectAllButton = JButton(HttpPalBundle.message("button.deselect.all"))
        
        selectAllButton.addActionListener {
            requestCheckBoxes.forEach { it.isSelected = true }
        }
        
        deselectAllButton.addActionListener {
            requestCheckBoxes.forEach { it.isSelected = false }
        }
        
        buttonPanel.add(selectAllButton)
        buttonPanel.add(deselectAllButton)
        
        panel.add(selectionPanel, BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun createEnvironmentPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = TitledBorder(HttpPalBundle.message("dialog.jmeter.export.environment.title"))
        
        panel.add(includeEnvironmentCheckBox)
        
        // Environment info
        updateEnvironmentInfo()
        environmentInfoLabel.foreground = Color.GRAY
        panel.add(environmentInfoLabel)
        
        return panel
    }
    
    private fun createFileSelectionPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = TitledBorder(HttpPalBundle.message("dialog.jmeter.export.file.title"))
        
        // File path field
        filePathField.text = getDefaultFileName()
        filePathField.columns = 40
        panel.add(filePathField, BorderLayout.CENTER)
        
        // Browse button
        browseButton.addActionListener { browseForFile() }
        panel.add(browseButton, BorderLayout.EAST)
        
        // Info label
        val infoPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val infoLabel = JBLabel(HttpPalBundle.message("dialog.jmeter.export.file.info"))
        infoLabel.foreground = Color.GRAY
        infoPanel.add(infoLabel)
        panel.add(infoPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun createPreviewPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)
        
        // Preview area
        previewArea.isEditable = false
        previewArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        previewArea.background = Color(248, 248, 248)
        
        val scrollPane = JBScrollPane(previewArea)
        scrollPane.preferredSize = Dimension(500, 300)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // Refresh preview button
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val refreshButton = JButton(HttpPalBundle.message("button.refresh.preview"))
        refreshButton.addActionListener { updatePreview() }
        buttonPanel.add(refreshButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun setupUI() {
        // Set initial state
        updateConcurrentOptionsState()
        updateRequestSelectionState()
    }
    
    private fun setupEventHandlers() {
        // Export type radio buttons
        singleRequestRadio.addActionListener { updateUI() }
        multipleRequestsRadio.addActionListener { updateUI() }
        concurrentScenarioRadio.addActionListener { updateUI() }
        
        // Environment checkbox
        includeEnvironmentCheckBox.addActionListener { updateEnvironmentInfo() }
        
        // Concurrent scenario spinners - auto-calculate ramp-up
        threadCountSpinner.addChangeListener {
            val threadCount = threadCountSpinner.value as Int
            rampUpSpinner.value = maxOf(1, threadCount / 10)
        }
    }
    
    private fun updateUI() {
        updateConcurrentOptionsState()
        updateRequestSelectionState()
        updatePreview()
    }
    
    private fun updateConcurrentOptionsState() {
        val isConcurrent = concurrentScenarioRadio.isSelected
        threadCountSpinner.isEnabled = isConcurrent
        iterationsSpinner.isEnabled = isConcurrent
        rampUpSpinner.isEnabled = isConcurrent
    }
    
    private fun updateRequestSelectionState() {
        val isMultiple = multipleRequestsRadio.isSelected
        requestCheckBoxes.forEach { it.isEnabled = isMultiple }
    }
    
    private fun updateEnvironmentInfo() {
        val text = if (includeEnvironmentCheckBox.isSelected && currentEnvironment != null) {
            "<html><i>" + HttpPalBundle.message(
                "dialog.jmeter.export.environment.info.active",
                currentEnvironment.name,
                currentEnvironment.baseUrl,
                currentEnvironment.globalHeaders.size
            ) + "</i></html>"
        } else {
            "<html><i>" + HttpPalBundle.message("dialog.jmeter.export.environment.info.none") + "</i></html>"
        }
        environmentInfoLabel.text = text
    }
    
    private fun updatePreview() {
        try {
            val testPlan = generateTestPlan()
            val preview = jmeterExportService.generateJmxFile(testPlan)
            
            // Show first 50 lines of the JMX file
            val lines = preview.lines()
            val previewLines = if (lines.size > 50) {
                lines.take(50) + listOf("", HttpPalBundle.message("dialog.jmeter.export.preview.truncated", lines.size - 50))
            } else {
                lines
            }
            
            previewArea.text = previewLines.joinToString("\n")
            previewArea.caretPosition = 0
        } catch (e: Exception) {
            previewArea.text = HttpPalBundle.message("dialog.jmeter.export.preview.error", e.message ?: "Unknown error")
        }
    }
    
    private fun getDefaultFileName(): String {
        val baseName = when {
            singleRequestRadio.isSelected -> "single-request"
            multipleRequestsRadio.isSelected -> "multiple-requests"
            concurrentScenarioRadio.isSelected -> "concurrent-scenario"
            else -> "jmeter-export"
        }
        return "$baseName.jmx"
    }
    
    private fun browseForFile() {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        descriptor.title = HttpPalBundle.message("dialog.jmeter.export.file.chooser.title")
        descriptor.description = HttpPalBundle.message("dialog.jmeter.export.file.chooser.description")
        
        val chooser: FileChooserDialog = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
        val files: Array<VirtualFile> = chooser.choose(project)
        
        if (files.isNotEmpty()) {
            val selectedDir = files[0]
            val fileName = getDefaultFileName()
            filePathField.text = File(selectedDir.path, fileName).absolutePath
        }
    }
    
    private fun generateTestPlan(): JMeterTestPlan {
        val environment = if (includeEnvironmentCheckBox.isSelected) currentEnvironment else null
        
        return when {
            singleRequestRadio.isSelected -> {
                jmeterExportService.exportSingleRequest(requests.first(), environment)
            }
            multipleRequestsRadio.isSelected -> {
                val selectedRequests = getSelectedRequests()
                jmeterExportService.exportMultipleRequests(selectedRequests, environment)
            }
            concurrentScenarioRadio.isSelected -> {
                val threadCount = threadCountSpinner.value as Int
                val iterations = iterationsSpinner.value as Int
                jmeterExportService.exportConcurrentScenario(requests.first(), threadCount, iterations, environment)
            }
            else -> throw IllegalStateException("No export type selected")
        }
    }
    
    private fun getSelectedRequests(): List<RequestConfig> {
        return requests.filterIndexed { index, _ ->
            index < requestCheckBoxes.size && requestCheckBoxes[index].isSelected
        }
    }
    
    override fun doOKAction() {
        // Validate inputs
        val validationErrors = validateInputs()
        if (validationErrors.isNotEmpty()) {
            Messages.showErrorDialog(
                project,
                validationErrors.joinToString("\n"),
                HttpPalBundle.message("error.title.validation")
            )
            return
        }
        
        // Perform export with progress indicator
        val filePath = filePathField.text.trim()
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, HttpPalBundle.message("dialog.jmeter.export.progress.title"), true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = HttpPalBundle.message("dialog.jmeter.export.progress.generating")
                    indicator.fraction = 0.3
                    
                    val testPlan = generateTestPlan()
                    
                    indicator.text = HttpPalBundle.message("dialog.jmeter.export.progress.writing")
                    indicator.fraction = 0.7
                    
                    jmeterExportService.saveJmxFile(testPlan, filePath)
                    
                    indicator.text = HttpPalBundle.message("dialog.jmeter.export.progress.completed")
                    indicator.fraction = 1.0
                    
                    // Show success notification
                    SwingUtilities.invokeLater {
                        Messages.showInfoMessage(
                            project,
                            HttpPalBundle.message("dialog.jmeter.export.success", filePath),
                            HttpPalBundle.message("dialog.success")
                        )
                    }
                    
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        Messages.showErrorDialog(
                            project,
                            HttpPalBundle.message("dialog.jmeter.export.failed", e.message ?: "Unknown error"),
                            HttpPalBundle.message("error.title.export")
                        )
                    }
                }
            }
        })
        
        super.doOKAction()
    }
    
    private fun validateInputs(): List<String> {
        val errors = mutableListOf<String>()
        
        // Validate file path
        val filePath = filePathField.text.trim()
        if (filePath.isEmpty()) {
            errors.add(HttpPalBundle.message("dialog.jmeter.export.validation.path.required"))
        } else {
            val file = File(filePath)
            if (!file.name.endsWith(".jmx")) {
                errors.add(HttpPalBundle.message("dialog.jmeter.export.validation.extension.invalid"))
            }
            
            val parentDir = file.parentFile
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                errors.add(HttpPalBundle.message("dialog.jmeter.export.validation.directory.create", parentDir.absolutePath))
            }
        }
        
        // Validate concurrent scenario options
        if (concurrentScenarioRadio.isSelected) {
            val threadCount = threadCountSpinner.value as Int
            val iterations = iterationsSpinner.value as Int
            
            if (threadCount < 1) {
                errors.add(HttpPalBundle.message("dialog.jmeter.export.validation.thread.count.min"))
            }
            if (threadCount > 1000) {
                errors.add(HttpPalBundle.message("dialog.jmeter.export.validation.thread.count.max"))
            }
            if (iterations < 1) {
                errors.add(HttpPalBundle.message("dialog.jmeter.export.validation.iterations.min"))
            }
        }
        
        // Validate request selection for multiple requests
        if (multipleRequestsRadio.isSelected) {
            val selectedRequests = getSelectedRequests()
            if (selectedRequests.isEmpty()) {
                errors.add(HttpPalBundle.message("dialog.jmeter.export.validation.selection.empty"))
            }
        }
        
        return errors
    }
    
    override fun getPreferredFocusedComponent(): JComponent? {
        return filePathField
    }
}