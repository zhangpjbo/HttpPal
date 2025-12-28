package com.httppal.ui

import com.httppal.model.RequestHistoryEntry
import com.httppal.service.HistoryFilters
import com.httppal.service.HistoryPage
import com.httppal.service.HistoryService
import com.httppal.service.listener.HistoryEventListener
import com.httppal.util.HttpPalBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.table.DefaultTableModel
import kotlin.math.ceil

/**
 * Panel for displaying and managing request history
 */
class HistoryPanel(private val project: Project, private val statusLabel: JBLabel? = null) : JPanel(BorderLayout()), HistoryEventListener {
    
    private val logger = Logger.getInstance(HistoryPanel::class.java)
    private val historyService = service<HistoryService>()
    
    // UI Components
    private val searchField = JBTextField()
    private val filterButton = JButton(HttpPalBundle.message("history.panel.filter.button"))
    private val clearButton = JButton(HttpPalBundle.message("history.panel.clear.button"))
    private val exportButton = JButton(HttpPalBundle.message("history.panel.export.button"))
    
    private val historyTable: JBTable
    private val tableModel: DefaultTableModel
    
    private val statsPanel = JPanel(FlowLayout(FlowLayout.LEFT))
    private val totalLabel = JBLabel()
    private val successLabel = JBLabel()
    private val failedLabel = JBLabel()
    private val avgTimeLabel = JBLabel()
    
    // Pagination
    private var currentPage = 1
    private var pageSize = 50
    private val pageSizeCombo = JComboBox(arrayOf(25, 50, 100, 200))
    private val pageLabel = JBLabel()
    private val prevButton = JButton("◀")
    private val nextButton = JButton("▶")
    
    // Filters
    private var currentFilters: HistoryFilters? = null
    
    // Callbacks
    private var onLoadRequestCallback: ((RequestHistoryEntry) -> Unit)? = null
    
    init {
        logger.info("Initializing HistoryPanel")
        
        // Set minimum size for the panel
        minimumSize = Dimension(500, 300)
        preferredSize = Dimension(800, 600)
        
        // Create table
        val columnNames = arrayOf(
            HttpPalBundle.message("history.table.column.timestamp"),
            HttpPalBundle.message("history.table.column.method"),
            HttpPalBundle.message("history.table.column.url"),
            HttpPalBundle.message("history.table.column.status"),
            HttpPalBundle.message("history.table.column.response.time"),
            HttpPalBundle.message("history.table.column.environment")
        )
        
        tableModel = object : DefaultTableModel(columnNames, 0) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
        
        historyTable = JBTable(tableModel)
        historyTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        
        // Setup UI
        setupTopPanel()
        setupCenterPanel()
        setupBottomPanel()
        
        // Register as event listener
        logger.info("Registering HistoryPanel as event listener")
        historyService.addEventListener(this)
        logger.debug("HistoryPanel registered as event listener successfully")
        
        // Load initial data
        logger.info("HistoryPanel initialized, loading initial history data")
        loadHistory()
    }
    
    private fun setupTopPanel() {
        val topPanel = JPanel(BorderLayout())
        topPanel.border = JBUI.Borders.empty(5, 10, 5, 10)
        topPanel.minimumSize = Dimension(400, 40)
        
        // Search panel - left side
        val searchPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        searchField.emptyText.text = HttpPalBundle.message("history.panel.search.placeholder")
        searchField.minimumSize = Dimension(200, 28)
        searchField.preferredSize = Dimension(250, 28)
        
        // Add search field listener to update filters when search text changes
        searchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) {
                handleSearchUpdate()
            }
            
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) {
                handleSearchUpdate()
            }
            
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {
                handleSearchUpdate()
            }
            
            private fun handleSearchUpdate() {
                // If we have active filters, we should update the current filters with the new search query
                if (currentFilters != null) {
                    val updatedFilters = currentFilters?.copy(query = searchField.text.trim())
                    currentFilters = updatedFilters
                    currentPage = 1  // Reset to first page when search changes
                    loadHistory()
                }
            }
        })
        
        searchPanel.add(searchField)
        searchPanel.add(filterButton)
        
        // Add a clear filters button when filters are active
        val clearFiltersButton = JButton(HttpPalBundle.message("history.filter.clear.button"))
        clearFiltersButton.isVisible = currentFilters != null
        clearFiltersButton.addActionListener {
            resetFilters()
            clearFiltersButton.isVisible = false
        }
        searchPanel.add(clearFiltersButton)
        
        // Button panel - right side
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0))
        buttonPanel.add(exportButton)
        buttonPanel.add(clearButton)
        
        topPanel.add(searchPanel, BorderLayout.WEST)
        topPanel.add(buttonPanel, BorderLayout.EAST)
        
        add(topPanel, BorderLayout.NORTH)
        
        // Setup listeners
        clearButton.addActionListener { clearHistory() }
        exportButton.addActionListener { exportHistory() }
        filterButton.addActionListener {
            showFilterDialog()
            clearFiltersButton.isVisible = currentFilters != null
        }
    }
    
    private fun setupCenterPanel() {
        val centerPanel = JPanel(BorderLayout())
        centerPanel.minimumSize = Dimension(400, 200)
        
        // Table with optimized scroll pane
        historyTable.fillsViewportHeight = true
        historyTable.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        
        // Set column widths for better display
        historyTable.columnModel.getColumn(0).preferredWidth = 80  // Timestamp
        historyTable.columnModel.getColumn(1).preferredWidth = 60  // Method
        historyTable.columnModel.getColumn(2).preferredWidth = 300 // URL
        historyTable.columnModel.getColumn(3).preferredWidth = 60  // Status
        historyTable.columnModel.getColumn(4).preferredWidth = 100 // Response Time
        historyTable.columnModel.getColumn(5).preferredWidth = 100 // Environment
        
        val scrollPane = JBScrollPane(historyTable)
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        scrollPane.minimumSize = Dimension(400, 150)
        
        centerPanel.add(scrollPane, BorderLayout.CENTER)
        
        // Stats panel
        statsPanel.border = JBUI.Borders.empty(5, 10, 5, 10)
        statsPanel.minimumSize = Dimension(400, 30)
        updateStatsPanel()
        centerPanel.add(statsPanel, BorderLayout.SOUTH)
        
        add(centerPanel, BorderLayout.CENTER)
        
        // Double-click to load
        historyTable.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    loadSelectedRequest()
                } else if (java.awt.event.MouseEvent.BUTTON3 == e.button) { // Right-click
                    showContextMenu(e.x, e.y)
                }
            }
        })
    }
    
    private fun showContextMenu(x: Int, y: Int) {
        val selectedRow = historyTable.selectedRow
        if (selectedRow < 0) return
        
        val popupMenu = JPopupMenu()
        
        val loadRequestItem = JMenuItem(HttpPalBundle.message("history.context.load"))
        loadRequestItem.addActionListener {
            loadSelectedRequest()
        }
        popupMenu.add(loadRequestItem)
        
        val addToFavoritesItem = JMenuItem(HttpPalBundle.message("history.context.add.to.favorites"))
        addToFavoritesItem.addActionListener {
            addToFavorites()
        }
        popupMenu.add(addToFavoritesItem)
        
        val deleteItem = JMenuItem(HttpPalBundle.message("history.context.delete"))
        deleteItem.addActionListener {
            deleteSelectedEntry()
        }
        popupMenu.add(deleteItem)
        
        val copyUrlItem = JMenuItem(HttpPalBundle.message("history.context.copy.url"))
        copyUrlItem.addActionListener {
            copySelectedUrl()
        }
        popupMenu.add(copyUrlItem)
        
        val viewDetailsItem = JMenuItem(HttpPalBundle.message("history.context.view.details"))
        viewDetailsItem.addActionListener {
            viewSelectedDetails()
        }
        popupMenu.add(viewDetailsItem)
        
        popupMenu.show(historyTable, x, y)
    }
    
    private fun addToFavorites() {
        val selectedRow = historyTable.selectedRow
        if (selectedRow >= 0) {
            try {
                val page = historyService.getHistoryPage(currentPage, pageSize)
                val entry = page.entries[selectedRow]
                
                // Create favorite from history entry
                val favoritesService = service<com.httppal.service.FavoritesService>()
                val favorite = favoritesService.createFavoriteFromHistory(
                    entry, 
                    name = "${entry.request.method} ${entry.request.url.substringAfterLast("/")}"
                )
                
                if (favoritesService.addFavorite(favorite)) {
                    com.intellij.openapi.ui.Messages.showInfoMessage(
                        this,
                        HttpPalBundle.message("success.favorite.added", favorite.name),
                        HttpPalBundle.message("dialog.success")
                    )
                } else {
                    com.intellij.openapi.ui.Messages.showErrorDialog(
                        this,
                        HttpPalBundle.message("error.favorite.add.failed", "Validation failed"),
                        HttpPalBundle.message("error.title.general")
                    )
                }
            } catch (e: Exception) {
                com.intellij.openapi.ui.Messages.showErrorDialog(
                    this,
                    HttpPalBundle.message("error.favorite.add.failed", e.message ?: "Unknown error"),
                    HttpPalBundle.message("error.title.general")
                )
            }
        }
    }
    
    private fun deleteSelectedEntry() {
        val selectedRow = historyTable.selectedRow
        if (selectedRow >= 0) {
            try {
                val page = historyService.getHistoryPage(currentPage, pageSize)
                val entry = page.entries[selectedRow]
                
                val result = JOptionPane.showConfirmDialog(
                    this,
                    HttpPalBundle.message("confirm.history.delete.message"),
                    HttpPalBundle.message("confirm.history.delete.title"),
                    JOptionPane.YES_NO_OPTION
                )
                
                if (result == JOptionPane.YES_OPTION) {
                    historyService.removeFromHistory(entry.id)
                    loadHistory()
                }
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    HttpPalBundle.message("error.history.delete.failed", e.message ?: "Unknown error"),
                    HttpPalBundle.message("error.title.general"),
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }
    
    private fun copySelectedUrl() {
        val selectedRow = historyTable.selectedRow
        if (selectedRow >= 0) {
            try {
                val page = historyService.getHistoryPage(currentPage, pageSize)
                val entry = page.entries[selectedRow]
                
                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                val selection = java.awt.datatransfer.StringSelection(entry.request.url)
                clipboard.setContents(selection, null)
                
                statusLabel?.text = "URL copied to clipboard"
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    private fun viewSelectedDetails() {
        val selectedRow = historyTable.selectedRow
        if (selectedRow >= 0) {
            try {
                val page = historyService.getHistoryPage(currentPage, pageSize)
                val entry = page.entries[selectedRow]
                
                // Show details dialog
                val detailsDialog = object : com.intellij.openapi.ui.DialogWrapper(project) {
                    init {
                        title = "Request Details"
                        init()
                    }
                    
                    override fun createCenterPanel(): JComponent {
                        val panel = JPanel(BorderLayout())
                        panel.preferredSize = Dimension(600, 400)
                        
                        val textArea = JBTextArea()
                        textArea.text = buildString {
                            appendLine("URL: ${entry.request.url}")
                            appendLine("Method: ${entry.request.method}")
                            appendLine("Headers: ${entry.request.headers}")
                            appendLine("Body: ${entry.request.body}")
                            appendLine("--- Response ---")
                            if (entry.response != null) {
                                appendLine("Status: ${entry.response.statusCode} ${entry.response.statusText}")
                                appendLine("Response Time: ${entry.response.responseTime}")
                                appendLine("Headers: ${entry.response.headers}")
                                appendLine("Body: ${entry.response.body}")
                            } else {
                                appendLine("No response")
                            }
                            if (entry.error != null) {
                                appendLine("--- Error ---")
                                appendLine("Error: ${entry.error}")
                            }
                        }
                        textArea.isEditable = false
                        
                        val scrollPane = JBScrollPane(textArea)
                        panel.add(scrollPane, BorderLayout.CENTER)
                        
                        return panel
                    }
                }
                
                detailsDialog.show()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    private fun setupBottomPanel() {
        val bottomPanel = JPanel(FlowLayout(FlowLayout.CENTER, 10, 5))
        bottomPanel.border = JBUI.Borders.empty(5, 10, 5, 10)
        bottomPanel.minimumSize = Dimension(400, 40)
        
        bottomPanel.add(JBLabel(HttpPalBundle.message("history.pagination.page.size")))
        pageSizeCombo.selectedItem = pageSize
        pageSizeCombo.minimumSize = Dimension(80, 28)
        bottomPanel.add(pageSizeCombo)
        
        prevButton.minimumSize = Dimension(40, 28)
        bottomPanel.add(prevButton)
        
        pageLabel.minimumSize = Dimension(100, 28)
        bottomPanel.add(pageLabel)
        
        nextButton.minimumSize = Dimension(40, 28)
        bottomPanel.add(nextButton)
        
        add(bottomPanel, BorderLayout.SOUTH)
        
        // Pagination listeners
        pageSizeCombo.addActionListener {
            pageSize = pageSizeCombo.selectedItem as Int
            currentPage = 1
            loadHistory()
        }
        
        prevButton.addActionListener {
            if (currentPage > 1) {
                currentPage--
                loadHistory()
            }
        }
        
        nextButton.addActionListener {
            currentPage++
            loadHistory()
        }
    }
    
    private fun loadHistory() {
        logger.info("Loading history from HistoryService...")
        
        try {
            val page = if (currentFilters != null) {
                // Apply filters if they exist, incorporating search query if present
                val filtersWithQuery = if (searchField.text.isNotBlank()) {
                    currentFilters?.copy(query = searchField.text.trim()) ?: HistoryFilters(query = searchField.text.trim())
                } else {
                    currentFilters
                }
                
                val filteredEntries = historyService.searchHistoryWithFilters(filtersWithQuery ?: HistoryFilters())
                // Apply pagination to filtered results
                val startIndex = (currentPage - 1) * pageSize
                val endIndex = minOf(startIndex + pageSize, filteredEntries.size)
                val paginatedEntries = if (startIndex < filteredEntries.size) {
                    filteredEntries.subList(startIndex, endIndex)
                } else {
                    emptyList()
                }
                HistoryPage(
                    entries = paginatedEntries,
                    page = currentPage,
                    pageSize = pageSize,
                    totalEntries = filteredEntries.size,
                    totalPages = if (pageSize > 0) ceil(filteredEntries.size.toDouble() / pageSize).toInt() else 0,
                    hasNext = startIndex + pageSize < filteredEntries.size,
                    hasPrevious = currentPage > 1
                )
            } else {
                // Apply search query if filters are not active
                if (searchField.text.isNotBlank()) {
                    val query = searchField.text.trim()
                    val searchResults = historyService.searchHistory(query)
                    val startIndex = (currentPage - 1) * pageSize
                    val endIndex = minOf(startIndex + pageSize, searchResults.size)
                    val paginatedEntries = if (startIndex < searchResults.size) {
                        searchResults.subList(startIndex, endIndex)
                    } else {
                        emptyList()
                    }
                    HistoryPage(
                        entries = paginatedEntries,
                        page = currentPage,
                        pageSize = pageSize,
                        totalEntries = searchResults.size,
                        totalPages = if (pageSize > 0) ceil(searchResults.size.toDouble() / pageSize).toInt() else 0,
                        hasNext = startIndex + pageSize < searchResults.size,
                        hasPrevious = currentPage > 1
                    )
                } else {
                    // Load all history with pagination
                    historyService.getHistoryPage(currentPage, pageSize)
                }
            }
            
            logger.info("Received history page: entries=${page.entries.size}, totalEntries=${page.totalEntries}, page=${page.page}")
            
            if (page.entries.isEmpty()) {
                logger.info("History is empty, showing empty state")
                showEmptyState()
                return
            }
            
            // Clear table
            tableModel.rowCount = 0
            
            // Add entries
            page.entries.forEach { entry ->
                val row = arrayOf(
                    entry.timestamp.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    entry.request.method.name,
                    truncateUrl(entry.request.url),
                    entry.response?.statusCode?.toString() ?: "N/A",
                    entry.getResponseTimeMs()?.toString() ?: "N/A",
                    entry.environment ?: ""
                )
                tableModel.addRow(row)
            }
            
            logger.debug("Added ${page.entries.size} rows to history table")
            
            // Update pagination
            pageLabel.text = HttpPalBundle.message("history.pagination.page", currentPage, page.totalPages)
            prevButton.isEnabled = page.hasPrevious
            nextButton.isEnabled = page.hasNext
            
            // Update stats
            updateStatsPanel()
            
            logger.info("History loaded successfully")
            
        } catch (e: Exception) {
            logger.error("Failed to load history: ${e.message}", e)
            JOptionPane.showMessageDialog(
                this,
                HttpPalBundle.message("error.history.load.failed", e.message ?: "Unknown error"),
                HttpPalBundle.message("error.title.general"),
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
    
    private fun updateStatsPanel() {
        statsPanel.removeAll()
        
        try {
            val stats = historyService.getHistoryStatistics()
            
            totalLabel.text = "${HttpPalBundle.message("history.stats.total")}: ${stats.totalRequests}"
            successLabel.text = "${HttpPalBundle.message("history.stats.successful")}: ${stats.successfulRequests}"
            failedLabel.text = "${HttpPalBundle.message("history.stats.failed")}: ${stats.failedRequests}"
            avgTimeLabel.text = "${HttpPalBundle.message("history.stats.avg.time")}: ${stats.averageResponseTime ?: 0}ms"
            
            statsPanel.add(totalLabel)
            statsPanel.add(Box.createHorizontalStrut(20))
            statsPanel.add(successLabel)
            statsPanel.add(Box.createHorizontalStrut(20))
            statsPanel.add(failedLabel)
            statsPanel.add(Box.createHorizontalStrut(20))
            statsPanel.add(avgTimeLabel)
            
        } catch (e: Exception) {
            // Ignore stats errors
        }
        
        statsPanel.revalidate()
        statsPanel.repaint()
    }
    
    private fun loadSelectedRequest() {
        val selectedRow = historyTable.selectedRow
        if (selectedRow >= 0) {
            try {
                val page = historyService.getHistoryPage(currentPage, pageSize)
                val entry = page.entries[selectedRow]
                onLoadRequestCallback?.invoke(entry)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    private fun clearHistory() {
        val result = JOptionPane.showConfirmDialog(
            this,
            HttpPalBundle.message("confirm.history.clear.message"),
            HttpPalBundle.message("confirm.history.clear.title"),
            JOptionPane.YES_NO_OPTION
        )
        
        if (result == JOptionPane.YES_OPTION) {
            try {
                historyService.clearHistory()
                loadHistory()
                JOptionPane.showMessageDialog(
                    this,
                    HttpPalBundle.message("success.history.cleared"),
                    HttpPalBundle.message("dialog.success"),
                    JOptionPane.INFORMATION_MESSAGE
                )
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    HttpPalBundle.message("error.history.clear.failed", e.message ?: "Unknown error"),
                    HttpPalBundle.message("error.title.general"),
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }
    
    private fun exportHistory() {
        // Show export format menu
        val popupMenu = JPopupMenu()
        
        val exportNativeItem = JMenuItem(HttpPalBundle.message("history.export.native"))
        exportNativeItem.addActionListener {
            exportHistoryNative()
        }
        popupMenu.add(exportNativeItem)
        
        val exportPostmanItem = JMenuItem(HttpPalBundle.message("history.export.postman"))
        exportPostmanItem.addActionListener {
            exportHistoryPostman()
        }
        popupMenu.add(exportPostmanItem)
        
        val exportJMeterItem = JMenuItem(HttpPalBundle.message("history.export.jmeter"))
        exportJMeterItem.addActionListener {
            exportHistoryJMeter()
        }
        popupMenu.add(exportJMeterItem)
        
        // Show menu below export button
        val location = exportButton.locationOnScreen
        popupMenu.setLocation(location.x, location.y + exportButton.height)
        popupMenu.isVisible = true
    }
    
    private fun exportHistoryNative() {
        try {
            val json = historyService.exportHistory()
            val fileChooser = JFileChooser()
            fileChooser.selectedFile = java.io.File("history.json")
            
            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                fileChooser.selectedFile.writeText(json)
                JOptionPane.showMessageDialog(
                    this,
                    HttpPalBundle.message("success.history.exported", fileChooser.selectedFile.absolutePath),
                    HttpPalBundle.message("dialog.success"),
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                HttpPalBundle.message("error.history.export.failed", e.message ?: "Unknown error"),
                HttpPalBundle.message("error.title.general"),
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
    
    private fun exportHistoryPostman() {
        try {
            // Get selected entries or all entries
            val entries = getSelectedOrAllEntries()
            
            if (entries.isEmpty()) {
                JOptionPane.showMessageDialog(
                    this,
                    HttpPalBundle.message("error.history.export.empty"),
                    HttpPalBundle.message("error.title.general"),
                    JOptionPane.WARNING_MESSAGE
                )
                return
            }
            
            // Show export dialog
            val dialog = PostmanExportDialog(project, entries.size) { filePath, options ->
                try {
                    val postmanService = project.getService(com.httppal.service.PostmanExportService::class.java)
                    val result = postmanService.exportHistoryToPostman(entries, "HttpPal History", options)
                    
                    if (result.success) {
                        // Save to specified path
                        val sourceFile = java.io.File(result.filePath!!)
                        val targetFile = java.io.File(filePath)
                        sourceFile.copyTo(targetFile, overwrite = true)
                        sourceFile.delete()
                        
                        JOptionPane.showMessageDialog(
                            this,
                            HttpPalBundle.message("success.postman.exported", result.exportedCount, targetFile.absolutePath),
                            HttpPalBundle.message("dialog.success"),
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    } else {
                        JOptionPane.showMessageDialog(
                            this,
                            HttpPalBundle.message("error.postman.export.failed", result.errors.joinToString("\n")),
                            HttpPalBundle.message("error.title.general"),
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(
                        this,
                        HttpPalBundle.message("error.postman.export.failed", e.message ?: "Unknown error"),
                        HttpPalBundle.message("error.title.general"),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
            
            dialog.show()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                HttpPalBundle.message("error.postman.export.failed", e.message ?: "Unknown error"),
                HttpPalBundle.message("error.title.general"),
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
    
    private fun exportHistoryJMeter() {
        try {
            // Get selected entries or all entries
            val entries = getSelectedOrAllEntries()
            
            if (entries.isEmpty()) {
                JOptionPane.showMessageDialog(
                    this,
                    HttpPalBundle.message("error.history.export.empty"),
                    HttpPalBundle.message("error.title.general"),
                    JOptionPane.WARNING_MESSAGE
                )
                return
            }
            
            // Show JMeter export dialog
            val environmentService = project.service<com.httppal.service.EnvironmentService>()
            val currentEnvironment = environmentService.getCurrentEnvironment()
            
            val dialog = com.httppal.ui.JMeterExportDialog(
                project,
                entries.map { it.request },
                currentEnvironment
            )
            
            dialog.show()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                HttpPalBundle.message("error.jmeter.export.failed", e.message ?: "Unknown error"),
                HttpPalBundle.message("error.title.general"),
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
    
    private fun getSelectedOrAllEntries(): List<RequestHistoryEntry> {
        val selectedRows = historyTable.selectedRows
        
        return if (selectedRows.isNotEmpty()) {
            // Export selected entries
            val page = historyService.getHistoryPage(currentPage, pageSize)
            selectedRows.map { page.entries[it] }
        } else {
            // Export all entries
            historyService.getAllHistory()
        }
    }
    
    private fun truncateUrl(url: String): String {
        return if (url.length > 60) {
            url.substring(0, 57) + "..."
        } else {
            url
        }
    }
    
    /**
     * Show empty state message when history is empty
     */
    private fun showEmptyState() {
        logger.info("Showing empty state for history")
        
        // Clear table
        tableModel.rowCount = 0
        
        // Add a single row with empty state message
        val emptyMessage = HttpPalBundle.message("history.panel.no.entries")
        tableModel.addRow(arrayOf(emptyMessage, "", "", "", "", ""))
        
        // Update stats to show zero
        totalLabel.text = "${HttpPalBundle.message("history.stats.total")}: 0"
        successLabel.text = "${HttpPalBundle.message("history.stats.successful")}: 0"
        failedLabel.text = "${HttpPalBundle.message("history.stats.failed")}: 0"
        avgTimeLabel.text = "${HttpPalBundle.message("history.stats.avg.time")}: N/A"
        
        logger.debug("Empty state displayed")
    }
    
    fun setOnLoadRequestCallback(callback: (RequestHistoryEntry) -> Unit) {
        onLoadRequestCallback = callback
    }
    
    fun refresh() {
        loadHistory()
    }
    
    /**
     * Dispose method to clean up resources
     * Should be called when the panel is no longer needed
     */
    fun dispose() {
        logger.info("Disposing HistoryPanel, removing event listener")
        historyService.removeEventListener(this)
        logger.debug("HistoryPanel event listener removed successfully")
    }
    
    // HistoryEventListener implementation
    override fun onHistoryAdded(entry: RequestHistoryEntry) {
        logger.info("Received history added event: id=${entry.id}, url=${entry.request.url}")
        
        // Ensure UI updates happen on EDT thread
        if (ApplicationManager.getApplication().isDispatchThread) {
            logger.debug("Already on EDT thread, refreshing directly")
            refresh()
        } else {
            logger.debug("Not on EDT thread, scheduling refresh on EDT")
            ApplicationManager.getApplication().invokeLater {
                refresh()
            }
        }
    }
    
    override fun onHistoryRemoved(entryId: String) {
        logger.info("Received history removed event: id=$entryId")
        
        // Ensure UI updates happen on EDT thread
        if (ApplicationManager.getApplication().isDispatchThread) {
            logger.debug("Already on EDT thread, refreshing directly")
            refresh()
        } else {
            logger.debug("Not on EDT thread, scheduling refresh on EDT")
            ApplicationManager.getApplication().invokeLater {
                refresh()
            }
        }
    }
    
    override fun onHistoryCleared() {
        logger.info("Received history cleared event")
        
        // Ensure UI updates happen on EDT thread
        if (ApplicationManager.getApplication().isDispatchThread) {
            logger.debug("Already on EDT thread, refreshing directly")
            refresh()
        } else {
            logger.debug("Not on EDT thread, scheduling refresh on EDT")
            ApplicationManager.getApplication().invokeLater {
                refresh()
            }
        }
    }
    
    private fun showFilterDialog() {
        val filterDialog = object : com.intellij.openapi.ui.DialogWrapper(project) {
            private val methodCheckBoxes = mapOf(
                "GET" to JCheckBox("GET"),
                "POST" to JCheckBox("POST"),
                "PUT" to JCheckBox("PUT"),
                "DELETE" to JCheckBox("DELETE"),
                "PATCH" to JCheckBox("PATCH"),
                "HEAD" to JCheckBox("HEAD"),
                "OPTIONS" to JCheckBox("OPTIONS")
            )
            
            private val statusField = JBTextField()
            private val environmentField = JBTextField()
            private val startDateField = JBTextField()
            private val endDateField = JBTextField()
            private val successfulOnlyCheckBox = JCheckBox(HttpPalBundle.message("history.filter.successful.only"))
            private val failedOnlyCheckBox = JCheckBox(HttpPalBundle.message("history.filter.failed.only"))
            
            init {
                title = HttpPalBundle.message("history.filter.title")
                setOKButtonText(HttpPalBundle.message("history.filter.apply"))
                init()
            }
            
            override fun createCenterPanel(): JComponent {
                val panel = JPanel(GridBagLayout())
                val gbc = GridBagConstraints().apply {
                    anchor = GridBagConstraints.WEST
                    insets = JBUI.insets(5)
                }
                
                // HTTP Method filter
                gbc.gridx = 0
                gbc.gridy = 0
                panel.add(JLabel(HttpPalBundle.message("history.filter.method.label")), gbc)
                
                gbc.gridx = 1
                val methodPanel = JPanel(FlowLayout(FlowLayout.LEFT))
                methodCheckBoxes.values.forEach { methodPanel.add(it) }
                panel.add(methodPanel, gbc)
                
                // Status code filter
                gbc.gridx = 0
                gbc.gridy = 1
                panel.add(JLabel(HttpPalBundle.message("history.filter.status.label")), gbc)
                
                gbc.gridx = 1
                panel.add(statusField, gbc)
                
                // Environment filter
                gbc.gridx = 0
                gbc.gridy = 2
                panel.add(JLabel(HttpPalBundle.message("history.filter.environment.label")), gbc)
                
                gbc.gridx = 1
                panel.add(environmentField, gbc)
                
                // Date range filter
                gbc.gridx = 0
                gbc.gridy = 3
                panel.add(JLabel("${HttpPalBundle.message("history.filter.date.from")} / ${HttpPalBundle.message("history.filter.date.to")}"), gbc)
                
                gbc.gridx = 1
                val datePanel = JPanel(FlowLayout(FlowLayout.LEFT))
                datePanel.add(startDateField.apply { toolTipText = "Format: YYYY-MM-DD" })
                datePanel.add(JLabel(" to "))
                datePanel.add(endDateField.apply { toolTipText = "Format: YYYY-MM-DD" })
                panel.add(datePanel, gbc)
                
                // Success/failure filter
                gbc.gridx = 0
                gbc.gridy = 4
                val successPanel = JPanel(FlowLayout(FlowLayout.LEFT))
                successPanel.add(successfulOnlyCheckBox)
                successPanel.add(failedOnlyCheckBox)
                panel.add(successPanel, gbc)
                
                // Reset button
                gbc.gridx = 0
                gbc.gridy = 5
                val resetButton = JButton(HttpPalBundle.message("history.filter.reset"))
                resetButton.addActionListener {
                    resetFilters()
                }
                panel.add(resetButton, gbc)
                
                return panel
            }
            
            private fun resetFilters() {
                methodCheckBoxes.values.forEach { it.isSelected = false }
                statusField.text = ""
                environmentField.text = ""
                startDateField.text = ""
                endDateField.text = ""
                successfulOnlyCheckBox.isSelected = false
                failedOnlyCheckBox.isSelected = false
            }
            
            override fun doOKAction() {
                // Apply filters
                val selectedMethods = methodCheckBoxes.filter { it.value.isSelected }.keys.toList()
                
                val statusCodes = statusField.text.trim()
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it > 0 }
                
                val environments = environmentField.text.trim()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                
                var startDate: java.time.LocalDate? = null
                var endDate: java.time.LocalDate? = null
                
                if (startDateField.text.trim().isNotEmpty()) {
                    try {
                        startDate = java.time.LocalDate.parse(startDateField.text.trim())
                    } catch (e: Exception) {
                        JOptionPane.showMessageDialog(
                            this@HistoryPanel,
                            "Invalid start date format. Use YYYY-MM-DD",
                            "Invalid Date",
                            JOptionPane.ERROR_MESSAGE
                        )
                        return
                    }
                }
                
                if (endDateField.text.trim().isNotEmpty()) {
                    try {
                        endDate = java.time.LocalDate.parse(endDateField.text.trim())
                    } catch (e: Exception) {
                        JOptionPane.showMessageDialog(
                            this@HistoryPanel,
                            "Invalid end date format. Use YYYY-MM-DD",
                            "Invalid Date",
                            JOptionPane.ERROR_MESSAGE
                        )
                        return
                    }
                }
                
                // Create filters object
                currentFilters = HistoryFilters(
                    query = if (searchField.text.isNotBlank()) searchField.text.trim() else null,
                    methods = selectedMethods,
                    statusCodes = statusCodes,
                    environments = environments,
                    startDate = startDate,
                    endDate = endDate,
                    successfulOnly = successfulOnlyCheckBox.isSelected,
                    failedOnly = failedOnlyCheckBox.isSelected
                )
                
                // Reset to first page when applying filters
                currentPage = 1
                loadHistory()
                
                super.doOKAction()
            }
        }
        
        filterDialog.show()
    }
    
    private fun resetFilters() {
        currentFilters = null
        currentPage = 1
        loadHistory()
    }
}
