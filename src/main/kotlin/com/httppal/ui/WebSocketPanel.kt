package com.httppal.ui

import com.httppal.model.*
import com.httppal.service.WebSocketService
import com.httppal.util.ErrorHandler
import com.httppal.util.HttpPalBundle
import com.httppal.util.LoggingUtils
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.text.DefaultCaret
import java.time.Instant

/**
 * WebSocket panel for managing WebSocket connections and message communication
 * Implements requirements 4.1, 4.2, 4.3, 4.4, 4.5
 */
class WebSocketPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val webSocketService = service<WebSocketService>()
    
    // UI Components
    private val urlField = JBTextField()
    private val headersTable = JBTable()
    private val connectButton = JButton(HttpPalBundle.message("websocket.connect.button"))
    private val disconnectButton = JButton(HttpPalBundle.message("websocket.disconnect.button"))
    private val statusLabel = JBLabel(HttpPalBundle.message("websocket.status.disconnected"))
    private val messageHistoryArea = JBTextArea()
    private val messageInputField = JBTextField()
    private val sendButton = JButton(HttpPalBundle.message("websocket.send.button"))
    private val clearHistoryButton = JButton(HttpPalBundle.message("history.clear.button"))
    
    // State
    private var currentConnectionId: String? = null
    private var currentConnection: WebSocketConnection? = null
    private val messageHistory = mutableListOf<WebSocketMessage>()
    
    // Time formatter for message timestamps
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    
    init {
        initializeComponents()
        setupEventHandlers()
        updateUIState()
    }
    
    private fun initializeComponents() {
        border = JBUI.Borders.empty(10)
        
        // Main layout
        val mainPanel = JPanel(BorderLayout())
        
        // Connection configuration panel
        val configPanel = createConnectionConfigPanel()
        mainPanel.add(configPanel, BorderLayout.NORTH)
        
        // Message communication panel
        val messagePanel = createMessagePanel()
        mainPanel.add(messagePanel, BorderLayout.CENTER)
        
        add(mainPanel, BorderLayout.CENTER)
    }
    
    private fun createConnectionConfigPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(Color.LIGHT_GRAY, 0, 0, 1, 0),
            JBUI.Borders.empty(0, 0, 10, 0)
        )
        
        // Title
        val titleLabel = JBLabel(HttpPalBundle.message("toolwindow.tab.websocket"))
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        val titlePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        titlePanel.add(titleLabel)
        panel.add(titlePanel)
        
        // URL input
        val urlPanel = JPanel(BorderLayout())
        urlPanel.add(JBLabel(HttpPalBundle.message("websocket.url.label")), BorderLayout.WEST)
        urlField.columns = 40
        urlField.text = "ws://localhost:8080/websocket"
        urlPanel.add(urlField, BorderLayout.CENTER)
        panel.add(urlPanel)
        
        // Add some spacing
        panel.add(Box.createVerticalStrut(10))
        
        // Headers configuration
        val headersPanel = createHeadersPanel()
        panel.add(headersPanel)
        
        // Add some spacing
        panel.add(Box.createVerticalStrut(10))
        
        // Status and controls
        val statusControlPanel = JPanel(BorderLayout())
        
        // Status display
        val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        statusPanel.add(JBLabel(HttpPalBundle.message("response.status.label")))
        statusLabel.font = statusLabel.font.deriveFont(Font.BOLD)
        statusPanel.add(statusLabel)
        statusControlPanel.add(statusPanel, BorderLayout.WEST)
        
        // Connection controls
        val controlsPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        connectButton.preferredSize = Dimension(100, 30)
        disconnectButton.preferredSize = Dimension(100, 30)
        disconnectButton.isEnabled = false
        controlsPanel.add(connectButton)
        controlsPanel.add(disconnectButton)
        statusControlPanel.add(controlsPanel, BorderLayout.EAST)
        
        panel.add(statusControlPanel)
        
        return panel
    }
    
    private fun createHeadersPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        // Headers label
        panel.add(JBLabel(HttpPalBundle.message("request.headers.label")), BorderLayout.NORTH)
        
        // Headers table
        val headersModel = DefaultTableModel(arrayOf("Name", "Value"), 0)
        headersTable.model = headersModel
        headersTable.preferredScrollableViewportSize = Dimension(400, 80)
        headersTable.fillsViewportHeight = true
        
        // Add default headers
        headersModel.addRow(arrayOf("", ""))
        
        val headersScrollPane = JBScrollPane(headersTable)
        panel.add(headersScrollPane, BorderLayout.CENTER)
        
        // Headers controls
        val headersControlsPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val addHeaderButton = JButton(HttpPalBundle.message("headers.add.button"))
        val removeHeaderButton = JButton(HttpPalBundle.message("headers.remove.button"))
        
        addHeaderButton.addActionListener {
            headersModel.addRow(arrayOf("", ""))
        }
        
        removeHeaderButton.addActionListener {
            val selectedRow = headersTable.selectedRow
            if (selectedRow >= 0 && headersModel.rowCount > 1) {
                headersModel.removeRow(selectedRow)
            }
        }
        
        headersControlsPanel.add(addHeaderButton)
        headersControlsPanel.add(removeHeaderButton)
        panel.add(headersControlsPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun createMessagePanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        // Split pane for message history and input
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT)
        splitPane.resizeWeight = 0.8
        
        // Message history panel
        val historyPanel = createMessageHistoryPanel()
        splitPane.topComponent = historyPanel
        
        // Message input panel
        val inputPanel = createMessageInputPanel()
        splitPane.bottomComponent = inputPanel
        
        panel.add(splitPane, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createMessageHistoryPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        // Title and controls
        val titlePanel = JPanel(BorderLayout())
        val titleLabel = JBLabel(HttpPalBundle.message("history.title"))
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 12f)
        titlePanel.add(titleLabel, BorderLayout.WEST)
        
        clearHistoryButton.addActionListener {
            clearMessageHistory()
        }
        titlePanel.add(clearHistoryButton, BorderLayout.EAST)
        
        panel.add(titlePanel, BorderLayout.NORTH)
        
        // Message history area
        messageHistoryArea.isEditable = false
        messageHistoryArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        messageHistoryArea.text = "WebSocket messages will appear here...\n"
        messageHistoryArea.background = Color.WHITE
        
        // Auto-scroll to bottom
        val caret = messageHistoryArea.caret as DefaultCaret
        caret.updatePolicy = DefaultCaret.ALWAYS_UPDATE
        
        val scrollPane = JBScrollPane(messageHistoryArea)
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createMessageInputPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(Color.LIGHT_GRAY, 1, 0, 0, 0),
            JBUI.Borders.empty(10, 0, 0, 0)
        )
        
        // Title
        val titleLabel = JBLabel(HttpPalBundle.message("websocket.send.button"))
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 12f)
        panel.add(titleLabel, BorderLayout.NORTH)
        
        // Message input
        val inputPanel = JPanel(BorderLayout())
        inputPanel.border = JBUI.Borders.empty(5, 0, 0, 0)
        
        messageInputField.columns = 40
        messageInputField.toolTipText = "Enter message to send (Press Enter to send)"
        inputPanel.add(messageInputField, BorderLayout.CENTER)
        
        sendButton.preferredSize = Dimension(80, 30)
        sendButton.isEnabled = false
        inputPanel.add(sendButton, BorderLayout.EAST)
        
        panel.add(inputPanel, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun setupEventHandlers() {
        // Connect button
        connectButton.addActionListener {
            connectWebSocket()
        }
        
        // Disconnect button
        disconnectButton.addActionListener {
            disconnectWebSocket()
        }
        
        // Send button
        sendButton.addActionListener {
            sendMessage()
        }
        
        // Enter key in message input field
        messageInputField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && sendButton.isEnabled) {
                    sendMessage()
                }
            }
        })
        
        // Enter key in URL field
        urlField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && connectButton.isEnabled) {
                    connectWebSocket()
                }
            }
        })
    }
    
    private fun connectWebSocket() {
        ErrorHandler.withErrorHandling("Connect WebSocket", project, this) {
            val url = urlField.text.trim()
            if (url.isEmpty()) {
                ErrorHandler.handleValidationErrors(listOf("WebSocket URL cannot be empty"), this)
                return@withErrorHandling
            }
            
            if (!isValidWebSocketUrl(url)) {
                ErrorHandler.handleValidationErrors(
                    listOf("Invalid WebSocket URL format. Use ws:// or wss://"), 
                    this
                )
                return@withErrorHandling
            }
            
            LoggingUtils.logWebSocketEvent("connect_attempt", url)
            
            // Get headers from table
            val headers = getHeadersFromTable()
            
            LoggingUtils.logWebSocketEvent(
                "connect_with_headers", 
                url, 
                mapOf("headerCount" to headers.size)
            )
            
            // Connect using WebSocket service
            val connectionId = webSocketService.connect(url, headers)
            currentConnectionId = connectionId
            
            // Set up listeners
            setupWebSocketListeners(connectionId)
            
            // Update UI state
            updateConnectionStatus(ConnectionStatus.CONNECTING)
            addMessageToHistory("Connecting to $url...")
            
            ErrorHandler.showInfo("Connecting to WebSocket...", project, url)
        }
    }
    
    private fun disconnectWebSocket() {
        currentConnectionId?.let { connectionId ->
            ErrorHandler.withErrorHandling("Disconnect WebSocket", project, this) {
                LoggingUtils.logWebSocketEvent("disconnect_attempt", urlField.text)
                webSocketService.disconnect(connectionId)
                addMessageToHistory("Disconnecting...")
                ErrorHandler.showInfo("WebSocket disconnected", project)
            }
        }
    }
    
    private fun sendMessage() {
        val message = messageInputField.text.trim()
        if (message.isEmpty()) {
            return
        }
        
        currentConnectionId?.let { connectionId ->
            ErrorHandler.withErrorHandling("Send WebSocket message", project, this) {
                LoggingUtils.logWebSocketEvent(
                    "send_message", 
                    urlField.text, 
                    mapOf("messageLength" to message.length)
                )
                
                val success = webSocketService.sendMessage(connectionId, message)
                if (success) {
                    messageInputField.text = ""
                    LoggingUtils.logWebSocketEvent("message_sent", urlField.text)
                } else {
                    ErrorHandler.handleError(
                        "Failed to send message",
                        project = project,
                        component = this,
                        severity = ErrorHandler.ErrorSeverity.WARNING
                    )
                }
            }
        }
    }
    
    private fun setupWebSocketListeners(connectionId: String) {
        // Message listener
        webSocketService.addMessageListener(connectionId) { message ->
            SwingUtilities.invokeLater {
                addWebSocketMessageToHistory(message)
            }
        }
        
        // Status listener
        webSocketService.addStatusListener(connectionId) { status ->
            SwingUtilities.invokeLater {
                updateConnectionStatus(status)
                
                // Update current connection
                currentConnection = webSocketService.getConnection(connectionId)
                
                when (status) {
                    ConnectionStatus.CONNECTED -> {
                        addMessageToHistory("Connected successfully!")
                    }
                    ConnectionStatus.DISCONNECTED -> {
                        addMessageToHistory("Disconnected")
                    }
                    ConnectionStatus.ERROR -> {
                        val connection = webSocketService.getConnection(connectionId)
                        val errorMsg = connection?.errorMessage ?: "Unknown error"
                        addMessageToHistory("Connection error: $errorMsg")
                    }
                    else -> {}
                }
            }
        }
    }
    
    private fun updateConnectionStatus(status: ConnectionStatus) {
        statusLabel.text = when (status) {
            ConnectionStatus.DISCONNECTED -> "Disconnected"
            ConnectionStatus.CONNECTING -> HttpPalBundle.message("websocket.status.connecting")
            ConnectionStatus.CONNECTED -> HttpPalBundle.message("websocket.status.connected")
            ConnectionStatus.ERROR -> HttpPalBundle.message("websocket.status.error")
        }
        
        // Update button states
        connectButton.isEnabled = status in listOf(ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR)
        disconnectButton.isEnabled = status in listOf(ConnectionStatus.CONNECTING, ConnectionStatus.CONNECTED)
        sendButton.isEnabled = status == ConnectionStatus.CONNECTED
        
        // Update status label color
        statusLabel.foreground = when (status) {
            ConnectionStatus.CONNECTED -> Color.GREEN.darker()
            ConnectionStatus.CONNECTING -> Color.ORANGE.darker()
            ConnectionStatus.ERROR -> Color.RED.darker()
            ConnectionStatus.DISCONNECTED -> Color.GRAY.darker()
        }
    }
    
    private fun addWebSocketMessageToHistory(message: WebSocketMessage) {
        messageHistory.add(message)
        
        val timestamp = message.getFormattedTimestamp()
        val direction = if (message.direction == MessageDirection.SENT) "→" else "←"
        val content = message.getFormattedContent()
        
        val messageText = "[$timestamp] $direction $content\n"
        messageHistoryArea.append(messageText)
        
        // Auto-scroll to bottom
        messageHistoryArea.caretPosition = messageHistoryArea.document.length
    }
    
    private fun addMessageToHistory(message: String) {
        val timestamp = formatTimestamp(Instant.now())
        val messageText = "[$timestamp] $message\n"
        messageHistoryArea.append(messageText)
        
        // Auto-scroll to bottom
        messageHistoryArea.caretPosition = messageHistoryArea.document.length
    }
    
    private fun clearMessageHistory() {
        messageHistory.clear()
        messageHistoryArea.text = "Message history cleared.\n"
    }
    
    private fun getHeadersFromTable(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val model = headersTable.model as DefaultTableModel
        
        for (i in 0 until model.rowCount) {
            val name = model.getValueAt(i, 0)?.toString()?.trim() ?: ""
            val value = model.getValueAt(i, 1)?.toString()?.trim() ?: ""
            
            if (name.isNotEmpty() && value.isNotEmpty()) {
                headers[name] = value
            }
        }
        
        return headers
    }
    
    private fun isValidWebSocketUrl(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            uri.scheme in listOf("ws", "wss")
        } catch (e: Exception) {
            false
        }
    }
    
    private fun showError(message: String) {
        JOptionPane.showMessageDialog(
            this,
            message,
            HttpPalBundle.message("error.title.general"),
            JOptionPane.ERROR_MESSAGE
        )
    }
    
    private fun updateUIState() {
        updateConnectionStatus(ConnectionStatus.DISCONNECTED)
    }
    
    // Public methods for external interaction
    
    /**
     * Set WebSocket URL programmatically
     */
    fun setWebSocketUrl(url: String) {
        urlField.text = url
    }
    
    /**
     * Get current WebSocket URL
     */
    fun getWebSocketUrl(): String {
        return urlField.text.trim()
    }
    
    /**
     * Set connection headers programmatically
     */
    fun setConnectionHeaders(headers: Map<String, String>) {
        val model = headersTable.model as DefaultTableModel
        
        // Clear existing headers
        model.rowCount = 0
        
        // Add provided headers
        headers.forEach { (name, value) ->
            model.addRow(arrayOf(name, value))
        }
        
        // Add empty row for new headers
        model.addRow(arrayOf("", ""))
    }
    
    /**
     * Get current connection headers
     */
    fun getConnectionHeaders(): Map<String, String> {
        return getHeadersFromTable()
    }
    
    /**
     * Get current connection status
     */
    fun getCurrentConnectionStatus(): ConnectionStatus? {
        return currentConnectionId?.let { webSocketService.getConnectionStatus(it) }
    }
    
    /**
     * Get current connection details
     */
    fun getCurrentConnection(): WebSocketConnection? {
        return currentConnection
    }
    
    /**
     * Get message history
     */
    fun getMessageHistory(): List<WebSocketMessage> {
        return messageHistory.toList()
    }
    
    /**
     * Check if WebSocket is connected
     */
    fun isConnected(): Boolean {
        return getCurrentConnectionStatus() == ConnectionStatus.CONNECTED
    }

    fun formatTimestamp(
        timestamp: Instant =  Instant.now()
    ): String {
        val zoneId: ZoneId = ZoneId.systemDefault()
        return timeFormatter.format(timestamp.atZone(zoneId))
    }
}