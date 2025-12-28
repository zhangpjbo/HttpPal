package com.httppal.ui

import com.httppal.model.*
import com.httppal.service.ExecutionProgress
import com.httppal.service.RequestExecutionService
import com.httppal.util.EnhancedConcurrentResult
import com.httppal.util.HttpPalBundle
import com.httppal.util.LoggingUtils
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.*
import java.text.DecimalFormat
import java.time.Duration
import java.time.Instant
import javax.swing.*
import javax.swing.border.TitledBorder
import javax.swing.table.DefaultTableModel

/**
 * Panel for configuring and executing concurrent HTTP requests
 * Implements requirements 8.1, 8.4, 8.5
 */
class ConcurrentExecutionPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val requestExecutionService = service<RequestExecutionService>()
    
    // Configuration components
    private val enableConcurrentCheckBox = JCheckBox("Enable Concurrent Execution")
    private val threadCountSpinner = JSpinner(SpinnerNumberModel(5, 1, 100, 1))
    private val iterationsSpinner = JSpinner(SpinnerNumberModel(10, 1, 10000, 1))
    private val rampUpSpinner = JSpinner(SpinnerNumberModel(1, 0, 60, 1))
    
    // Validation error display
    private val validationErrorLabel = JBLabel("")
    
    // Execution control components
    private val executeButton = JButton("Execute Concurrent Requests")
    private val cancelButton = JButton(HttpPalBundle.message("button.cancel"))
    private val progressBar = JProgressBar()
    private val statusLabel = JBLabel(HttpPalBundle.message("status.ready"))
    
    // Real-time progress display
    private val currentRPSLabel = JBLabel("RPS: --")
    private val avgResponseTimeLabel = JBLabel("Avg Time: --")
    private val completedCountLabel = JBLabel("Completed: 0/0")
    
    // Results display components
    private val resultsPanel = JPanel(BorderLayout())
    private val statisticsTable = createStatisticsTable()
    private val responseTimeChart = createResponseTimeChart()
    private val statusCodeChart = createStatusCodeChart()
    private val errorTable = createErrorTable()
    
    // State management
    private var currentExecution: Job? = null
    private var currentExecutionId: String? = null
    private var executionResults: ConcurrentExecutionResult? = null
    private var enhancedResults: com.httppal.util.EnhancedConcurrentResult? = null
    private var onExecutionCallback: ((ConcurrentExecutionResult) -> Unit)? = null
    private var executionStartTime: Instant? = null
    
    // Callback for concurrent execution state changes
    private var onConcurrentStateChange: ((Boolean) -> Unit)? = null
    
    // Formatting
    private val decimalFormat = DecimalFormat("#,##0.00")
    private val integerFormat = DecimalFormat("#,##0")
    
    init {
        setupUI()
        setupEventHandlers()
        updateUIState()
    }
    
    private fun setupUI() {
        border = JBUI.Borders.empty(10)
        
        // Configuration panel
        val configPanel = createConfigurationPanel()
        add(configPanel, BorderLayout.NORTH)
        
        // Results panel (initially hidden)
        setupResultsPanel()
        add(resultsPanel, BorderLayout.CENTER)
        resultsPanel.isVisible = false
    }
    
    private fun createConfigurationPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = TitledBorder("Concurrent Execution Configuration")
        
        // Enable checkbox
        val enablePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        enablePanel.add(enableConcurrentCheckBox)
        panel.add(enablePanel, BorderLayout.NORTH)
        
        // Configuration form
        val formPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(5)
        gbc.anchor = GridBagConstraints.WEST
        
        // Thread count
        gbc.gridx = 0; gbc.gridy = 0
        formPanel.add(JBLabel(HttpPalBundle.message("concurrent.thread.count")), gbc)
        gbc.gridx = 1
        threadCountSpinner.preferredSize = Dimension(80, threadCountSpinner.preferredSize.height)
        threadCountSpinner.toolTipText = "Number of concurrent threads (1-100)"
        formPanel.add(threadCountSpinner, gbc)
        
        gbc.gridx = 2
        formPanel.add(JBLabel(HttpPalBundle.message("unit.threads")), gbc)
        
        // Iterations per thread
        gbc.gridx = 0; gbc.gridy = 1
        formPanel.add(JBLabel(HttpPalBundle.message("concurrent.iterations")), gbc)
        gbc.gridx = 1
        iterationsSpinner.preferredSize = Dimension(80, iterationsSpinner.preferredSize.height)
        iterationsSpinner.toolTipText = "Number of requests per thread (1-1000)"
        formPanel.add(iterationsSpinner, gbc)
        
        gbc.gridx = 2
        formPanel.add(JBLabel(HttpPalBundle.message("unit.requests")), gbc)
        
        // Ramp-up period
        gbc.gridx = 0; gbc.gridy = 2
        formPanel.add(JBLabel("Ramp-up Period:"), gbc)
        gbc.gridx = 1
        rampUpSpinner.preferredSize = Dimension(80, rampUpSpinner.preferredSize.height)
        rampUpSpinner.toolTipText = "Time to reach full thread count (0-60 seconds)"
        formPanel.add(rampUpSpinner, gbc)
        
        gbc.gridx = 2
        formPanel.add(JBLabel("seconds"), gbc)
        
        // Total requests calculation
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3
        val totalRequestsLabel = JBLabel()
        updateTotalRequestsLabel(totalRequestsLabel)
        totalRequestsLabel.font = totalRequestsLabel.font.deriveFont(Font.ITALIC)
        formPanel.add(totalRequestsLabel, gbc)
        
        // Validation error display
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3
        validationErrorLabel.foreground = Color.RED
        validationErrorLabel.font = validationErrorLabel.font.deriveFont(Font.BOLD)
        validationErrorLabel.isVisible = false
        formPanel.add(validationErrorLabel, gbc)
        
        // Add listeners to update total requests
        threadCountSpinner.addChangeListener { updateTotalRequestsLabel(totalRequestsLabel) }
        iterationsSpinner.addChangeListener { updateTotalRequestsLabel(totalRequestsLabel) }
        
        panel.add(formPanel, BorderLayout.CENTER)
        
        // Control buttons panel
        val controlPanel = createControlPanel()
        panel.add(controlPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun createControlPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10, 0, 0, 0)
        
        // Progress panel with real-time metrics
        val progressPanel = JPanel(BorderLayout())
        
        // Status and progress bar
        val statusProgressPanel = JPanel(BorderLayout())
        statusProgressPanel.add(statusLabel, BorderLayout.WEST)
        progressBar.isVisible = false
        statusProgressPanel.add(progressBar, BorderLayout.CENTER)
        progressPanel.add(statusProgressPanel, BorderLayout.NORTH)
        
        // Real-time metrics panel
        val metricsPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        metricsPanel.border = JBUI.Borders.empty(5, 0, 0, 0)
        completedCountLabel.isVisible = false
        currentRPSLabel.isVisible = false
        avgResponseTimeLabel.isVisible = false
        metricsPanel.add(completedCountLabel)
        metricsPanel.add(Box.createHorizontalStrut(15))
        metricsPanel.add(currentRPSLabel)
        metricsPanel.add(Box.createHorizontalStrut(15))
        metricsPanel.add(avgResponseTimeLabel)
        progressPanel.add(metricsPanel, BorderLayout.CENTER)
        
        panel.add(progressPanel, BorderLayout.CENTER)
        
        // Buttons panel
        val buttonsPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        executeButton.preferredSize = Dimension(180, 30)
        cancelButton.preferredSize = Dimension(80, 30)
        cancelButton.isVisible = false
        
        buttonsPanel.add(cancelButton)
        buttonsPanel.add(executeButton)
        panel.add(buttonsPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun setupResultsPanel() {
        resultsPanel.border = TitledBorder("Execution Results")
        
        // Create tabbed pane for different result views
        val resultsTabs = JBTabbedPane()
        
        // Statistics tab
        val statsPanel = createStatisticsPanel()
        resultsTabs.addTab("Statistics", statsPanel)
        
        // Charts tab
        val chartsPanel = createChartsPanel()
        resultsTabs.addTab("Performance Charts", chartsPanel)
        
        // Errors tab
        val errorsPanel = createErrorsPanel()
        resultsTabs.addTab("Errors", errorsPanel)
        
        resultsPanel.add(resultsTabs, BorderLayout.CENTER)
        
        // Results summary and export at top
        val topPanel = JPanel(BorderLayout())
        val summaryPanel = createResultsSummaryPanel()
        topPanel.add(summaryPanel, BorderLayout.CENTER)
        
        // Export button
        val exportPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val exportButton = JButton("Export Results")
        exportButton.addActionListener { exportResults() }
        exportPanel.add(exportButton)
        topPanel.add(exportPanel, BorderLayout.EAST)
        
        resultsPanel.add(topPanel, BorderLayout.NORTH)
    }
    
    private fun createStatisticsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)
        
        val scrollPane = JBScrollPane(statisticsTable)
        scrollPane.preferredSize = Dimension(600, 300)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createChartsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)
        
        // Split pane for two charts
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
        splitPane.resizeWeight = 0.5
        
        // Response time chart
        val responseTimePanel = JPanel(BorderLayout())
        responseTimePanel.border = TitledBorder("Response Time Distribution")
        responseTimePanel.add(responseTimeChart, BorderLayout.CENTER)
        splitPane.leftComponent = responseTimePanel
        
        // Status code chart
        val statusCodePanel = JPanel(BorderLayout())
        statusCodePanel.border = TitledBorder("Status Code Distribution")
        statusCodePanel.add(statusCodeChart, BorderLayout.CENTER)
        splitPane.rightComponent = statusCodePanel
        
        panel.add(splitPane, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createErrorsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)
        
        val scrollPane = JBScrollPane(errorTable)
        scrollPane.preferredSize = Dimension(600, 300)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createResultsSummaryPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))
        panel.border = JBUI.Borders.empty(5)
        
        // Will be populated when results are available
        return panel
    }
    
    private fun createStatisticsTable(): JBTable {
        val columnNames = arrayOf("Metric", "Value")
        val model = object : DefaultTableModel(columnNames, 0) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
        
        val table = JBTable(model)
        table.fillsViewportHeight = true
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        
        return table
    }
    
    private fun createResponseTimeChart(): JPanel {
        // Simple text-based chart for now - could be enhanced with actual charting library
        val panel = JPanel(BorderLayout())
        val textArea = JTextArea()
        textArea.isEditable = false
        textArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        textArea.text = "Response time chart will appear here after execution"
        
        val scrollPane = JBScrollPane(textArea)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createStatusCodeChart(): JPanel {
        // Simple text-based chart for now - could be enhanced with actual charting library
        val panel = JPanel(BorderLayout())
        val textArea = JTextArea()
        textArea.isEditable = false
        textArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        textArea.text = "Status code distribution will appear here after execution"
        
        val scrollPane = JBScrollPane(textArea)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createErrorTable(): JBTable {
        val columnNames = arrayOf("Request #", "Error Type", "Message", "Timestamp")
        val model = object : DefaultTableModel(columnNames, 0) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
        
        val table = JBTable(model)
        table.fillsViewportHeight = true
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        
        return table
    }
    
    private fun setupEventHandlers() {
        enableConcurrentCheckBox.addActionListener { updateUIState() }
        
        executeButton.addActionListener { executeConcurrentRequests() }
        
        cancelButton.addActionListener { cancelExecution() }
    }
    
    private fun updateTotalRequestsLabel(label: JBLabel) {
        val threadCount = threadCountSpinner.value as Int
        val iterations = iterationsSpinner.value as Int
        val total = threadCount * iterations
        label.text = "Total requests: $total"
    }
    
    private fun updateUIState() {
        val isEnabled = enableConcurrentCheckBox.isSelected
        val isExecuting = currentExecution?.isActive == true
        
        // Enable/disable configuration controls
        threadCountSpinner.isEnabled = isEnabled && !isExecuting
        iterationsSpinner.isEnabled = isEnabled && !isExecuting
        rampUpSpinner.isEnabled = isEnabled && !isExecuting
        
        // Update button states (Requirements 5.5)
        executeButton.isEnabled = isEnabled && !isExecuting
        executeButton.text = if (isExecuting) "Executing..." else "Execute Concurrent Requests"
        
        cancelButton.isVisible = isExecuting
        cancelButton.isEnabled = isExecuting
        progressBar.isVisible = isExecuting
        
        // Update status
        statusLabel.text = when {
            isExecuting -> "Executing concurrent requests..."
            enhancedResults != null -> "Execution completed"
            executionResults != null -> "Execution completed"
            else -> HttpPalBundle.message("status.ready")
        }
        
        // Notify about concurrent execution state change
        onConcurrentStateChange?.invoke(isEnabled)
    }
    
    private fun executeConcurrentRequests() {
        val requestConfig = getCurrentRequestConfig() ?: return
        
        val threadCount = threadCountSpinner.value as Int
        val iterations = iterationsSpinner.value as Int
        val rampUpPeriod = rampUpSpinner.value as Int
        
        // Validate parameters (Requirements 5.1)
        val validationErrors = validateConcurrentParameters(threadCount, iterations)
        if (validationErrors.isNotEmpty()) {
            showValidationError(validationErrors.joinToString("; "))
            return
        }
        clearValidationError()
        
        // Generate execution ID
        currentExecutionId = "concurrent_${System.currentTimeMillis()}"
        executionStartTime = Instant.now()
        
        // Setup progress tracking
        progressBar.isIndeterminate = false
        progressBar.minimum = 0
        progressBar.maximum = threadCount * iterations
        progressBar.value = 0
        
        // Show real-time metrics
        completedCountLabel.isVisible = true
        currentRPSLabel.isVisible = true
        avgResponseTimeLabel.isVisible = true
        
        // Add progress listener (Requirements 5.3)
        requestExecutionService.addProgressListener(currentExecutionId!!) { progress ->
            SwingUtilities.invokeLater {
                updateProgressDisplay(progress)
            }
        }
        
        // Start execution
        currentExecution = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Apply ramp-up delay if specified
                if (rampUpPeriod > 0) {
                    val delayPerThread = (rampUpPeriod * 1000) / threadCount
                    // This is a simplified ramp-up - in a real implementation,
                    // threads would be started with delays
                }
                
                // Execute with enhanced statistics (Requirements 5.4)
                val result = requestExecutionService.executeConcurrentRequestsWithStats(
                    requestConfig, threadCount, iterations
                )
                
                SwingUtilities.invokeLater {
                    displayEnhancedResults(result)
                    onExecutionCallback?.invoke(result.basicResult)
                }
                
            } catch (e: CancellationException) {
                SwingUtilities.invokeLater {
                    statusLabel.text = "Execution cancelled"
                    hideProgressMetrics()
                    updateUIState()
                }
                throw e
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    statusLabel.text = "Execution failed: ${e.message}"
                    hideProgressMetrics()
                    updateUIState()
                    JOptionPane.showMessageDialog(
                        this@ConcurrentExecutionPanel,
                        "Execution failed: ${e.message}",
                        "Execution Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            } finally {
                currentExecutionId?.let { id ->
                    requestExecutionService.removeProgressListener(id) { }
                }
                currentExecutionId = null
                executionStartTime = null
                SwingUtilities.invokeLater { updateUIState() }
            }
        }
        
        updateUIState()
    }
    
    /**
     * Validate concurrent execution parameters (Requirements 5.1)
     */
    private fun validateConcurrentParameters(threadCount: Int, iterations: Int): List<String> {
        val errors = mutableListOf<String>()
        
        if (threadCount < 1 || threadCount > 100) {
            errors.add("Thread count must be between 1 and 100")
        }
        
        if (iterations < 1 || iterations > 10000) {
            errors.add("Iterations must be between 1 and 10000")
        }
        
        val totalRequests = threadCount * iterations
        if (totalRequests > 100000) {
            errors.add("Total requests (${totalRequests}) exceeds maximum of 100,000")
        }
        
        return errors
    }
    
    /**
     * Show validation error message
     */
    private fun showValidationError(message: String) {
        validationErrorLabel.text = "⚠ $message"
        validationErrorLabel.isVisible = true
        revalidate()
        repaint()
    }
    
    /**
     * Clear validation error message
     */
    private fun clearValidationError() {
        validationErrorLabel.text = ""
        validationErrorLabel.isVisible = false
    }
    
    /**
     * Update progress display with real-time metrics (Requirements 5.3)
     */
    private fun updateProgressDisplay(progress: ExecutionProgress) {
        progressBar.value = progress.completedRequests
        
        // Update completed count
        completedCountLabel.text = "Completed: ${progress.completedRequests}/${progress.totalRequests} " +
                "(✓ ${progress.successfulRequests} | ✗ ${progress.failedRequests})"
        
        // Calculate and display current RPS
        executionStartTime?.let { startTime ->
            val elapsedSeconds = Duration.between(startTime, Instant.now()).toMillis() / 1000.0
            if (elapsedSeconds > 0) {
                val currentRPS = progress.completedRequests / elapsedSeconds
                currentRPSLabel.text = "RPS: ${decimalFormat.format(currentRPS)}"
            }
        }
        
        // Display average response time
        progress.averageResponseTime?.let { avgTime ->
            avgResponseTimeLabel.text = "Avg Time: ${avgTime}ms"
        }
        
        // Update status label
        statusLabel.text = "Executing concurrent requests..."
    }
    
    /**
     * Hide progress metrics
     */
    private fun hideProgressMetrics() {
        completedCountLabel.isVisible = false
        currentRPSLabel.isVisible = false
        avgResponseTimeLabel.isVisible = false
    }
    
    private fun cancelExecution() {
        currentExecutionId?.let { id ->
            val cancelled = requestExecutionService.cancelExecution(id)
            if (cancelled) {
                currentExecution?.cancel()
                statusLabel.text = "Cancelling execution..."
                hideProgressMetrics()
            }
        }
    }
    
    /**
     * Display enhanced results with detailed statistics (Requirements 5.4, 7.1-7.5)
     */
    private fun displayEnhancedResults(result: EnhancedConcurrentResult) {
        enhancedResults = result
        executionResults = result.basicResult
        
        // Update statistics table with enhanced data
        updateEnhancedStatisticsTable(result)
        
        // Update charts
        updateResponseTimeChart(result.basicResult)
        updateStatusCodeChart(result.basicResult)
        
        // Update errors table with error breakdown
        updateEnhancedErrorsTable(result)
        
        // Update summary with enhanced metrics
        updateEnhancedResultsSummary(result)
        
        // Show results panel
        resultsPanel.isVisible = true
        hideProgressMetrics()
        revalidate()
        repaint()
        
        updateUIState()
    }
    
    /**
     * Update statistics table with enhanced metrics (Requirements 5.4, 7.1-7.4)
     */
    private fun updateEnhancedStatisticsTable(result: EnhancedConcurrentResult) {
        val model = statisticsTable.model as DefaultTableModel
        model.rowCount = 0
        
        val basicResult = result.basicResult
        val stats = basicResult.getResponseTimeStats()
        val throughput = basicResult.getThroughputStats()
        
        // Request statistics
        model.addRow(arrayOf("Total Requests", integerFormat.format(result.getTotalRequests())))
        model.addRow(arrayOf("Successful Requests", integerFormat.format(result.getSuccessfulRequests())))
        model.addRow(arrayOf("Failed Requests", integerFormat.format(result.getFailedRequests())))
        model.addRow(arrayOf("Success Rate", "${decimalFormat.format(result.getSuccessRate())}%"))
        model.addRow(arrayOf("Failure Rate", "${decimalFormat.format(result.getFailureRate())}%"))
        model.addRow(arrayOf("", "")) // Separator
        
        // Response time statistics (Requirements 7.1)
        model.addRow(arrayOf("Average Response Time", "${result.getAverageResponseTime().toMillis()} ms"))
        model.addRow(arrayOf("Min Response Time", "${result.getMinResponseTime().toMillis()} ms"))
        model.addRow(arrayOf("Max Response Time", "${result.getMaxResponseTime().toMillis()} ms"))
        model.addRow(arrayOf("Median Response Time", "${stats.median} ms"))
        model.addRow(arrayOf("", "")) // Separator
        
        // Percentiles (Requirements 7.4)
        model.addRow(arrayOf("50th Percentile (P50)", "${result.percentiles.p50.toMillis()} ms"))
        model.addRow(arrayOf("95th Percentile (P95)", "${result.percentiles.p95.toMillis()} ms"))
        model.addRow(arrayOf("99th Percentile (P99)", "${result.percentiles.p99.toMillis()} ms"))
        model.addRow(arrayOf("", "")) // Separator
        
        // Throughput statistics (Requirements 7.3)
        model.addRow(arrayOf("Requests per Second (RPS)", decimalFormat.format(result.rps)))
        model.addRow(arrayOf("Bytes per Second", integerFormat.format(throughput.bytesPerSecond.toLong())))
        model.addRow(arrayOf("Total Bytes", integerFormat.format(throughput.totalBytes)))
        model.addRow(arrayOf("Average Response Size", "${integerFormat.format(throughput.averageResponseSize)} bytes"))
        model.addRow(arrayOf("", "")) // Separator
        
        // Execution details
        model.addRow(arrayOf("Thread Count", integerFormat.format(basicResult.threadCount)))
        model.addRow(arrayOf("Total Duration", "${result.getTotalDuration().toMillis()} ms"))
        model.addRow(arrayOf("Start Time", basicResult.startTime.toString()))
        model.addRow(arrayOf("End Time", basicResult.endTime.toString()))
    }
    
    /**
     * Update errors table with error breakdown (Requirements 7.5)
     */
    private fun updateEnhancedErrorsTable(result: EnhancedConcurrentResult) {
        val model = errorTable.model as DefaultTableModel
        model.rowCount = 0
        
        // Add error breakdown summary first
        result.errorBreakdown.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .forEach { (errorType, count) ->
                val percentage = if (result.getFailedRequests() > 0) {
                    (count.toDouble() / result.getFailedRequests()) * 100.0
                } else 0.0
                
                model.addRow(arrayOf(
                    "SUMMARY",
                    errorType.name,
                    "$count errors (${decimalFormat.format(percentage)}%)",
                    ""
                ))
            }
        
        // Add separator if there are errors
        if (result.errorBreakdown.values.sum() > 0) {
            model.addRow(arrayOf("", "", "", ""))
        }
        
        // Add individual errors
        result.basicResult.errors.forEach { error ->
            model.addRow(arrayOf(
                error.requestIndex?.toString() ?: "N/A",
                error.errorType.name,
                error.message,
                error.timestamp.toString()
            ))
        }
    }
    
    /**
     * Update results summary with enhanced metrics
     */
    private fun updateEnhancedResultsSummary(result: EnhancedConcurrentResult) {
        // Find the topPanel which is in NORTH position
        val topPanel = resultsPanel.layout.let { layout ->
            if (layout is BorderLayout) {
                layout.getLayoutComponent(BorderLayout.NORTH) as? JPanel
            } else {
                null
            }
        }
        
        if (topPanel == null) {
            LoggingUtils.logWarning("Could not find top panel in results panel")
            return
        }
        
        // Find the summaryPanel which should be in CENTER of topPanel
        val summaryPanel = topPanel.layout.let { layout ->
            if (layout is BorderLayout) {
                layout.getLayoutComponent(BorderLayout.CENTER) as? JPanel
            } else {
                null
            }
        }
        
        if (summaryPanel == null) {
            LoggingUtils.logWarning("Could not find summary panel in top panel")
            return
        }
        
        summaryPanel.removeAll()
        
        val summaryLabel = JBLabel(result.getSummary())
        summaryLabel.font = summaryLabel.font.deriveFont(Font.BOLD, 14f)
        summaryPanel.add(summaryLabel)
        
        summaryPanel.revalidate()
        summaryPanel.repaint()
    }
    
    private fun updateResponseTimeChart(result: ConcurrentExecutionResult) {
        val chartPanel = responseTimeChart.components[0] as JScrollPane
        val textArea = chartPanel.viewport.view as JTextArea
        
        val responseTimes = result.responses.map { it.responseTime.toMillis() }.sorted()
        if (responseTimes.isEmpty()) {
            textArea.text = "No response time data available"
            return
        }
        
        // Create simple histogram
        val bucketCount = 10
        val min = responseTimes.minOrNull() ?: 0L
        val max = responseTimes.maxOrNull() ?: 0L
        val bucketSize = if (max > min) (max - min) / bucketCount else 1L
        
        val buckets = IntArray(bucketCount)
        responseTimes.forEach { time ->
            val bucketIndex = if (bucketSize > 0) {
                ((time - min) / bucketSize).toInt().coerceAtMost(bucketCount - 1)
            } else 0
            buckets[bucketIndex]++
        }
        
        val chart = StringBuilder()
        chart.append("Response Time Distribution (ms)\n")
        chart.append("=" .repeat(40)).append("\n\n")
        
        val maxCount = buckets.maxOrNull() ?: 1
        buckets.forEachIndexed { index, count ->
            val rangeStart = min + index * bucketSize
            val rangeEnd = min + (index + 1) * bucketSize
            val barLength = (count * 30) / maxCount
            val bar = "█".repeat(barLength)
            
            chart.append(String.format("%4d-%4d ms: %s (%d)\n", rangeStart, rangeEnd, bar, count))
        }
        
        textArea.text = chart.toString()
    }
    
    private fun updateStatusCodeChart(result: ConcurrentExecutionResult) {
        val chartPanel = statusCodeChart.components[0] as JScrollPane
        val textArea = chartPanel.viewport.view as JTextArea
        
        val statusCodes = result.getStatusCodeDistribution()
        if (statusCodes.isEmpty()) {
            textArea.text = "No status code data available"
            return
        }
        
        val chart = StringBuilder()
        chart.append("Status Code Distribution\n")
        chart.append("=" .repeat(30)).append("\n\n")
        
        val maxCount = statusCodes.values.maxOrNull() ?: 1
        statusCodes.entries.sortedBy { it.key }.forEach { (code, count) ->
            val percentage = (count * 100.0) / result.totalRequests
            val barLength = (count * 20) / maxCount
            val bar = "█".repeat(barLength)
            
            chart.append(String.format("%3d: %s (%d - %.1f%%)\n", code, bar, count, percentage))
        }
        
        textArea.text = chart.toString()
    }
    
    // Public API methods
    
    /**
     * Check if concurrent execution is enabled
     */
    fun isConcurrentExecutionEnabled(): Boolean {
        return enableConcurrentCheckBox.isSelected
    }
    
    /**
     * Set callback for execution completion
     */
    fun setOnExecutionCallback(callback: (ConcurrentExecutionResult) -> Unit) {
        onExecutionCallback = callback
    }
    
    fun setOnConcurrentStateChangeListener(listener: (Boolean) -> Unit) {
        onConcurrentStateChange = listener
    }
    
    /**
     * Get the current request configuration (to be provided by parent)
     */
    private var requestConfigProvider: (() -> RequestConfig?)? = null
    
    fun setRequestConfigProvider(provider: () -> RequestConfig?) {
        requestConfigProvider = provider
    }
    
    private fun getCurrentRequestConfig(): RequestConfig? {
        return requestConfigProvider?.invoke()
    }
    
    /**
     * Clear results and reset UI
     */
    fun clearResults() {
        executionResults = null
        enhancedResults = null
        resultsPanel.isVisible = false
        clearValidationError()
        hideProgressMetrics()
        updateUIState()
        revalidate()
        repaint()
    }
    
    /**
     * Get current execution results
     */
    fun getExecutionResults(): ConcurrentExecutionResult? {
        return executionResults
    }
    
    /**
     * Check if execution is currently running
     */
    fun isExecutionRunning(): Boolean {
        return currentExecution?.isActive == true
    }
    
    /**
     * Export results to file (Requirements 5.4, 7.1-7.5)
     */
    private fun exportResults() {
        val results = enhancedResults ?: executionResults ?: return
        
        val fileChooser = JFileChooser()
        fileChooser.dialogTitle = "Export Concurrent Execution Results"
        fileChooser.selectedFile = java.io.File("concurrent_results_${System.currentTimeMillis()}.txt")
        
        val result = fileChooser.showSaveDialog(this)
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                val file = fileChooser.selectedFile
                val content = buildExportContent(results)
                file.writeText(content)
                
                JOptionPane.showMessageDialog(
                    this,
                    "Results exported successfully to:\n${file.absolutePath}",
                    "Export Successful",
                    JOptionPane.INFORMATION_MESSAGE
                )
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to export results: ${e.message}",
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }
    
    /**
     * Build export content from results
     */
    private fun buildExportContent(results: Any): String {
        val sb = StringBuilder()
        
        when (results) {
            is com.httppal.util.EnhancedConcurrentResult -> {
                sb.appendLine("=".repeat(80))
                sb.appendLine("CONCURRENT EXECUTION RESULTS - ENHANCED")
                sb.appendLine("=".repeat(80))
                sb.appendLine()
                
                // Summary
                sb.appendLine("SUMMARY")
                sb.appendLine("-".repeat(80))
                sb.appendLine(results.getSummary())
                sb.appendLine()
                
                // Request Statistics
                sb.appendLine("REQUEST STATISTICS")
                sb.appendLine("-".repeat(80))
                sb.appendLine("Total Requests:       ${integerFormat.format(results.getTotalRequests())}")
                sb.appendLine("Successful Requests:  ${integerFormat.format(results.getSuccessfulRequests())}")
                sb.appendLine("Failed Requests:      ${integerFormat.format(results.getFailedRequests())}")
                sb.appendLine("Success Rate:         ${decimalFormat.format(results.getSuccessRate())}%")
                sb.appendLine("Failure Rate:         ${decimalFormat.format(results.getFailureRate())}%")
                sb.appendLine()
                
                // Response Time Statistics
                sb.appendLine("RESPONSE TIME STATISTICS")
                sb.appendLine("-".repeat(80))
                sb.appendLine("Average:              ${results.getAverageResponseTime().toMillis()} ms")
                sb.appendLine("Minimum:              ${results.getMinResponseTime().toMillis()} ms")
                sb.appendLine("Maximum:              ${results.getMaxResponseTime().toMillis()} ms")
                sb.appendLine()
                
                // Percentiles
                sb.appendLine("PERCENTILES")
                sb.appendLine("-".repeat(80))
                sb.appendLine("P50 (Median):         ${results.percentiles.p50.toMillis()} ms")
                sb.appendLine("P95:                  ${results.percentiles.p95.toMillis()} ms")
                sb.appendLine("P99:                  ${results.percentiles.p99.toMillis()} ms")
                sb.appendLine()
                
                // Throughput
                sb.appendLine("THROUGHPUT")
                sb.appendLine("-".repeat(80))
                sb.appendLine("Requests per Second:  ${decimalFormat.format(results.rps)}")
                val throughput = results.basicResult.getThroughputStats()
                sb.appendLine("Bytes per Second:     ${integerFormat.format(throughput.bytesPerSecond.toLong())}")
                sb.appendLine("Total Bytes:          ${integerFormat.format(throughput.totalBytes)}")
                sb.appendLine("Avg Response Size:    ${integerFormat.format(throughput.averageResponseSize)} bytes")
                sb.appendLine()
                
                // Error Breakdown
                if (results.errorBreakdown.values.sum() > 0) {
                    sb.appendLine("ERROR BREAKDOWN")
                    sb.appendLine("-".repeat(80))
                    results.errorBreakdown.entries
                        .filter { it.value > 0 }
                        .sortedByDescending { it.value }
                        .forEach { (errorType, count) ->
                            val percentage = if (results.getFailedRequests() > 0) {
                                (count.toDouble() / results.getFailedRequests()) * 100.0
                            } else 0.0
                            sb.appendLine("${errorType.name.padEnd(30)} ${count.toString().padStart(6)} (${decimalFormat.format(percentage)}%)")
                        }
                    sb.appendLine()
                }
                
                // Execution Details
                sb.appendLine("EXECUTION DETAILS")
                sb.appendLine("-".repeat(80))
                sb.appendLine("Thread Count:         ${results.basicResult.threadCount}")
                sb.appendLine("Total Duration:       ${results.getTotalDuration().toMillis()} ms")
                sb.appendLine("Start Time:           ${results.basicResult.startTime}")
                sb.appendLine("End Time:             ${results.basicResult.endTime}")
                sb.appendLine()
            }
            
            is ConcurrentExecutionResult -> {
                sb.appendLine("=".repeat(80))
                sb.appendLine("CONCURRENT EXECUTION RESULTS")
                sb.appendLine("=".repeat(80))
                sb.appendLine()
                
                // Basic statistics
                sb.appendLine("REQUEST STATISTICS")
                sb.appendLine("-".repeat(80))
                sb.appendLine("Total Requests:       ${integerFormat.format(results.totalRequests)}")
                sb.appendLine("Successful Requests:  ${integerFormat.format(results.successfulRequests)}")
                sb.appendLine("Failed Requests:      ${integerFormat.format(results.failedRequests)}")
                sb.appendLine("Success Rate:         ${decimalFormat.format(results.getSuccessRate())}%")
                sb.appendLine()
                
                sb.appendLine("RESPONSE TIME STATISTICS")
                sb.appendLine("-".repeat(80))
                sb.appendLine("Average:              ${results.averageResponseTime.toMillis()} ms")
                sb.appendLine("Minimum:              ${results.minResponseTime.toMillis()} ms")
                sb.appendLine("Maximum:              ${results.maxResponseTime.toMillis()} ms")
                sb.appendLine()
            }
        }
        
        sb.appendLine("=".repeat(80))
        sb.appendLine("End of Report")
        sb.appendLine("=".repeat(80))
        
        return sb.toString()
    }
}

/**
 * Configuration for concurrent execution
 */
data class ConcurrentConfig(
    val threadCount: Int,
    val iterations: Int,
    val rampUpPeriod: Int
)