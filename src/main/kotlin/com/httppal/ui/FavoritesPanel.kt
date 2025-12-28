package com.httppal.ui

import com.httppal.model.FavoriteRequest
import com.httppal.service.FavoritesService
import com.httppal.service.ParseResult
import com.httppal.util.HttpPalBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Panel for displaying and managing favorite requests
 */
class FavoritesPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val favoritesService = service<FavoritesService>()
    
    // UI Components
    private val searchField = JBTextField()
    private val addButton = JButton(HttpPalBundle.message("favorites.panel.add.button"))
    private val importButton = JButton(HttpPalBundle.message("favorites.panel.import.button"))
    private val exportButton = JButton(HttpPalBundle.message("favorites.panel.export.button"))
    
    private val folderTree: Tree
    private val treeModel: DefaultTreeModel
    private val rootNode = DefaultMutableTreeNode(HttpPalBundle.message("favorites.tree.all"))
    
    private val favoritesTable: JBTable
    private val tableModel: DefaultTableModel
    
    private var currentFolder: String? = null
    
    // Callbacks
    private var onLoadRequestCallback: ((FavoriteRequest) -> Unit)? = null
    private var onAddCurrentCallback: (() -> Unit)? = null
    
    init {
        // Create tree
        treeModel = DefaultTreeModel(rootNode)
        folderTree = Tree(treeModel)
        folderTree.isRootVisible = true
        
        // Create table
        val columnNames = arrayOf(
            HttpPalBundle.message("favorites.table.column.name"),
            HttpPalBundle.message("favorites.table.column.method"),
            HttpPalBundle.message("favorites.table.column.url"),
            HttpPalBundle.message("favorites.table.column.use.count"),
            HttpPalBundle.message("favorites.table.column.last.used")
        )
        
        tableModel = object : DefaultTableModel(columnNames, 0) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
        
        favoritesTable = JBTable(tableModel)
        favoritesTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        
        // Setup UI
        setupTopPanel()
        setupCenterPanel()
        
        // Load initial data
        loadFolders()
        loadFavorites()
    }
    
    private fun setupTopPanel() {
        val topPanel = JPanel(BorderLayout())
        topPanel.border = JBUI.Borders.empty(5)
        
        // Search panel
        val searchPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        searchField.emptyText.text = HttpPalBundle.message("favorites.panel.search.placeholder")
        searchField.preferredSize = Dimension(300, 30)
        searchPanel.add(searchField)
        
        // Button panel
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        buttonPanel.add(addButton)
        buttonPanel.add(importButton)
        buttonPanel.add(exportButton)
        
        topPanel.add(searchPanel, BorderLayout.WEST)
        topPanel.add(buttonPanel, BorderLayout.EAST)
        
        add(topPanel, BorderLayout.NORTH)
        
        // Setup listeners
        addButton.addActionListener { onAddCurrentCallback?.invoke() }
        importButton.addActionListener { importFavorites() }
        exportButton.addActionListener { exportFavorites() }
    }
    
    private fun setupCenterPanel() {
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
        splitPane.resizeWeight = 0.3
        
        // Left: Folder tree
        val treeScrollPane = JBScrollPane(folderTree)
        treeScrollPane.preferredSize = Dimension(200, 0)
        splitPane.leftComponent = treeScrollPane
        
        // Right: Favorites table
        val tableScrollPane = JBScrollPane(favoritesTable)
        splitPane.rightComponent = tableScrollPane
        
        add(splitPane, BorderLayout.CENTER)
        
        // Tree selection listener
        folderTree.addTreeSelectionListener {
            val node = folderTree.lastSelectedPathComponent as? DefaultMutableTreeNode
            if (node != null && node != rootNode) {
                currentFolder = node.userObject as String
                loadFavorites()
            } else {
                currentFolder = null
                loadFavorites()
            }
        }
        
        // Double-click to load
        favoritesTable.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    loadSelectedFavorite()
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    // Show context menu
                    showContextMenu(e.x, e.y)
                }
            }
        })
    }
    
    private fun showContextMenu(x: Int, y: Int) {
        val popupMenu = JPopupMenu()
        
        val selectedRow = favoritesTable.rowAtPoint(java.awt.Point(x, y))
        if (selectedRow >= 0) {
            favoritesTable.setRowSelectionInterval(selectedRow, selectedRow)
            
            val loadItem = JMenuItem(HttpPalBundle.message("favorites.context.load"))
            loadItem.addActionListener {
                loadSelectedFavorite()
            }
            popupMenu.add(loadItem)
            
            val editItem = JMenuItem(HttpPalBundle.message("favorites.context.edit"))
            editItem.addActionListener {
                editSelectedFavorite()
            }
            popupMenu.add(editItem)
            
            val deleteItem = JMenuItem(HttpPalBundle.message("favorites.context.delete"))
            deleteItem.addActionListener {
                deleteSelectedFavorite()
            }
            popupMenu.add(deleteItem)
            
            val duplicateItem = JMenuItem(HttpPalBundle.message("favorites.context.duplicate"))
            duplicateItem.addActionListener {
                duplicateSelectedFavorite()
            }
            popupMenu.add(duplicateItem)
            
            val copyUrlItem = JMenuItem(HttpPalBundle.message("favorites.context.copy.url"))
            copyUrlItem.addActionListener {
                copySelectedUrl()
            }
            popupMenu.add(copyUrlItem)
            
            val moveToFolderItem = JMenuItem(HttpPalBundle.message("favorites.context.move.to.folder"))
            moveToFolderItem.addActionListener {
                moveToFolder()
            }
            popupMenu.add(moveToFolderItem)
        }
        
        if (popupMenu.componentCount > 0) {
            popupMenu.show(favoritesTable, x, y)
        }
    }
    
    private fun editSelectedFavorite() {
        val selectedRow = favoritesTable.selectedRow
        if (selectedRow >= 0) {
            try {
                val favorites = if (currentFolder != null) {
                    favoritesService.getFavoritesByFolder(currentFolder)
                } else {
                    favoritesService.getAllFavorites()
                }
                
                if (selectedRow < favorites.size) {
                    val favorite = favorites[selectedRow]
                    
                    // Create edit dialog
                    val nameField = JTextField(favorite.name, 20)
                    val descriptionField = JTextArea("", 3, 20)
                    val folderCombo = JComboBox<String>()
                    
                    descriptionField.lineWrap = true
                    descriptionField.wrapStyleWord = true
                    
                    // Load folders
                    val allFolders = favoritesService.getAllFolders()
                    folderCombo.addItem(HttpPalBundle.message("favorites.tree.uncategorized")) // For null/unassigned folder
                    allFolders.forEach { folder -> folderCombo.addItem(folder) }
                    
                    // Add option to create new folder
                    folderCombo.addItem(HttpPalBundle.message("favorites.tree.create.folder"))
                    
                    // Select current folder
                    if (favorite.folder != null) {
                        folderCombo.selectedItem = favorite.folder
                    } else {
                        folderCombo.selectedItem = HttpPalBundle.message("favorites.tree.uncategorized")
                    }
                    
                    val panel = JPanel(GridBagLayout())
                    val gbc = GridBagConstraints()
                    gbc.insets = JBUI.insets(5)
                    gbc.anchor = GridBagConstraints.WEST
                    
                    gbc.gridx = 0; gbc.gridy = 0
                    panel.add(JBLabel(HttpPalBundle.message("favorites.dialog.name.label")), gbc)
                    gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
                    panel.add(nameField, gbc)
                    
                    gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
                    panel.add(JBLabel(HttpPalBundle.message("favorites.dialog.folder.label")), gbc)
                    gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
                    panel.add(folderCombo, gbc)
                    
                    gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
                    panel.add(JBLabel(HttpPalBundle.message("favorite.description.label")), gbc)
                    gbc.gridx = 1; gbc.gridy = 2; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0
                    panel.add(JScrollPane(descriptionField), gbc)
                    
                    val result = JOptionPane.showConfirmDialog(
                        this,
                        panel,
                        HttpPalBundle.message("favorites.dialog.edit.title"),
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE
                    )
                    
                    if (result == JOptionPane.OK_OPTION) {
                        var selectedFolder = folderCombo.selectedItem as? String
                        
                        // Check if user wants to create a new folder
                        if (selectedFolder == HttpPalBundle.message("favorites.tree.create.folder")) {
                            val newFolderName = JOptionPane.showInputDialog(
                                this,
                                HttpPalBundle.message("folder.dialog.name.label"),
                                HttpPalBundle.message("folder.dialog.create.title"),
                                JOptionPane.QUESTION_MESSAGE
                            )
                            
                            if (!newFolderName.isNullOrBlank()) {
                                selectedFolder = newFolderName
                                // Add to combo box so it's available for future use
                                folderCombo.addItem(selectedFolder)
                                folderCombo.selectedItem = selectedFolder
                            } else {
                                return // User cancelled folder creation
                            }
                        }
                        
                        // If user selected 'uncategorized', set folder to null
                        if (selectedFolder == HttpPalBundle.message("favorites.tree.uncategorized")) {
                            selectedFolder = null
                        }
                        
                        val updatedFavorite = favorite.copy(
                            name = nameField.text.trim().ifBlank { favorite.name },
                            folder = selectedFolder
                        )
                        
                        val success = favoritesService.updateFavorite(updatedFavorite)
                        if (success) {
                            JOptionPane.showMessageDialog(
                                this,
                                HttpPalBundle.message("success.favorite.updated", updatedFavorite.name),
                                HttpPalBundle.message("dialog.success"),
                                JOptionPane.INFORMATION_MESSAGE
                            )
                            loadFolders()  // Reload folders since favorite count might change
                            loadFavorites()  // Reload favorites list
                        } else {
                            JOptionPane.showMessageDialog(
                                this,
                                HttpPalBundle.message("error.favorite.update.failed", "Failed to update favorite"),
                                HttpPalBundle.message("error.title.general"),
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    HttpPalBundle.message("error.favorite.update.failed", e.message ?: "Unknown error"),
                    HttpPalBundle.message("error.title.general"),
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }
    
    private fun deleteSelectedFavorite() {
        val selectedRow = favoritesTable.selectedRow
        if (selectedRow >= 0) {
            try {
                val favorites = if (currentFolder != null) {
                    favoritesService.getFavoritesByFolder(currentFolder)
                } else {
                    favoritesService.getAllFavorites()
                }
                
                if (selectedRow < favorites.size) {
                    val favorite = favorites[selectedRow]
                    
                    val result = JOptionPane.showConfirmDialog(
                        this,
                        HttpPalBundle.message("confirm.favorite.delete.message", favorite.name),
                        HttpPalBundle.message("confirm.favorite.delete.title"),
                        JOptionPane.YES_NO_OPTION
                    )
                    
                    if (result == JOptionPane.YES_OPTION) {
                        val success = favoritesService.removeFavorite(favorite.id)
                        if (success) {
                            JOptionPane.showMessageDialog(
                                this,
                                HttpPalBundle.message("success.favorite.deleted", favorite.name),
                                HttpPalBundle.message("dialog.success"),
                                JOptionPane.INFORMATION_MESSAGE
                            )
                            loadFolders()  // Reload folders since favorite count might change
                            loadFavorites()  // Reload favorites list
                        } else {
                            JOptionPane.showMessageDialog(
                                this,
                                HttpPalBundle.message("error.favorite.delete.failed", "Failed to delete favorite"),
                                HttpPalBundle.message("error.title.general"),
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    HttpPalBundle.message("error.favorite.delete.failed", e.message ?: "Unknown error"),
                    HttpPalBundle.message("error.title.general"),
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }
    
    private fun duplicateSelectedFavorite() {
        val selectedRow = favoritesTable.selectedRow
        if (selectedRow >= 0) {
            try {
                val favorites = if (currentFolder != null) {
                    favoritesService.getFavoritesByFolder(currentFolder)
                } else {
                    favoritesService.getAllFavorites()
                }
                
                if (selectedRow < favorites.size) {
                    val originalFavorite = favorites[selectedRow]
                    
                    val newNameObj = JOptionPane.showInputDialog(
                        this,
                        "Enter new name for the duplicate:",
                        "Duplicate Favorite",
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        null,
                        "${originalFavorite.name} (Copy)"
                    )
                    
                    val newName = if (newNameObj != null) newNameObj.toString() else null
                    
                    if (!newName.isNullOrBlank()) {
                        val duplicate = favoritesService.duplicateFavorite(originalFavorite.id, newName)
                        if (duplicate != null) {
                            JOptionPane.showMessageDialog(
                                this,
                                HttpPalBundle.message("success.favorite.added", duplicate.name),
                                HttpPalBundle.message("dialog.success"),
                                JOptionPane.INFORMATION_MESSAGE
                            )
                            loadFolders()  // Reload folders since favorite count might change
                            loadFavorites()  // Reload favorites list
                        } else {
                            JOptionPane.showMessageDialog(
                                this,
                                HttpPalBundle.message("error.favorite.add.failed", "Failed to duplicate favorite"),
                                HttpPalBundle.message("error.title.general"),
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    HttpPalBundle.message("error.favorite.add.failed", e.message ?: "Unknown error"),
                    HttpPalBundle.message("error.title.general"),
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }
    
    private fun moveToFolder() {
        val selectedRow = favoritesTable.selectedRow
        if (selectedRow >= 0) {
            try {
                val favorites = if (currentFolder != null) {
                    favoritesService.getFavoritesByFolder(currentFolder)
                } else {
                    favoritesService.getAllFavorites()
                }
                
                if (selectedRow < favorites.size) {
                    val favorite = favorites[selectedRow]
                    
                    // Create folder selection dialog
                    val folderCombo = JComboBox<String>()
                    
                    // Load all folders
                    val allFolders = favoritesService.getAllFolders()
                    folderCombo.addItem(HttpPalBundle.message("favorites.tree.uncategorized")) // For null/unassigned folder
                    allFolders.forEach { folder ->
                        if (folder != favorite.folder) { // Don't add current folder again
                            folderCombo.addItem(folder)
                        }
                    }
                    
                    // Add option to create new folder
                    folderCombo.addItem(HttpPalBundle.message("favorites.tree.create.folder"))
                    
                    val result = JOptionPane.showConfirmDialog(
                        this,
                        arrayOf(HttpPalBundle.message("favorites.dialog.folder.label"), folderCombo),
                        HttpPalBundle.message("favorites.context.move.to.folder"),
                        JOptionPane.OK_CANCEL_OPTION
                    )
                    
                    if (result == JOptionPane.OK_OPTION) {
                        var selectedFolder = folderCombo.selectedItem as? String
                        
                        // Check if user wants to create a new folder
                        if (selectedFolder == HttpPalBundle.message("favorites.tree.create.folder")) {
                            val newFolderName = JOptionPane.showInputDialog(
                                this,
                                HttpPalBundle.message("folder.dialog.name.label"),
                                HttpPalBundle.message("folder.dialog.create.title"),
                                JOptionPane.QUESTION_MESSAGE
                            )
                            
                            if (!newFolderName.isNullOrBlank()) {
                                selectedFolder = newFolderName
                            } else {
                                return // User cancelled folder creation
                            }
                        }
                        
                        // If user selected 'uncategorized', set folder to null
                        if (selectedFolder == HttpPalBundle.message("favorites.tree.uncategorized")) {
                            selectedFolder = null
                        }
                        
                        val success = favoritesService.moveFavoriteToFolder(favorite.id, selectedFolder)
                        if (success) {
                            JOptionPane.showMessageDialog(
                                this,
                                HttpPalBundle.message("success.favorite.updated", favorite.name),
                                HttpPalBundle.message("dialog.success"),
                                JOptionPane.INFORMATION_MESSAGE
                            )
                            loadFolders()  // Reload folders since favorite count might change
                            loadFavorites()  // Reload favorites list
                        } else {
                            JOptionPane.showMessageDialog(
                                this,
                                HttpPalBundle.message("error.favorite.update.failed", "Failed to move favorite"),
                                HttpPalBundle.message("error.title.general"),
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    HttpPalBundle.message("error.favorite.update.failed", e.message ?: "Unknown error"),
                    HttpPalBundle.message("error.title.general"),
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }
    
    private fun copySelectedUrl() {
        val selectedRow = favoritesTable.selectedRow
        if (selectedRow >= 0) {
            try {
                val favorites = if (currentFolder != null) {
                    favoritesService.getFavoritesByFolder(currentFolder)
                } else {
                    favoritesService.getAllFavorites()
                }
                
                if (selectedRow < favorites.size) {
                    val favorite = favorites[selectedRow]
                    
                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    val selection = java.awt.datatransfer.StringSelection(favorite.request.url)
                    clipboard.setContents(selection, null)
                    
                    // Show a simple notification that URL was copied
                    javax.swing.JOptionPane.showMessageDialog(
                        this,
                        "URL copied to clipboard",
                        "Info",
                        javax.swing.JOptionPane.INFORMATION_MESSAGE
                    )
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    private fun loadFolders() {
        rootNode.removeAllChildren()
        
        try {
            val folders = favoritesService.getAllFolders()
            folders.forEach { folder ->
                rootNode.add(DefaultMutableTreeNode(folder))
            }
            
            // Add uncategorized
            rootNode.add(DefaultMutableTreeNode(HttpPalBundle.message("favorites.tree.uncategorized")))
            
            treeModel.reload()
            folderTree.expandRow(0)
            
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun loadFavorites() {
        tableModel.rowCount = 0
        
        try {
            val favorites = if (currentFolder != null) {
                favoritesService.getFavoritesByFolder(currentFolder)
            } else {
                favoritesService.getAllFavorites()
            }
            
            favorites.forEach { favorite ->
                val row = arrayOf(
                    favorite.name,
                    favorite.request.method.name,
                    truncateUrl(favorite.request.url),
                    favorite.useCount.toString(),
                    favorite.lastUsed?.toString()?.substring(0, 10) ?: "Never"
                )
                tableModel.addRow(row)
            }
            
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                HttpPalBundle.message("error.favorite.add.failed", e.message ?: "Unknown error"),
                HttpPalBundle.message("error.title.general"),
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
    
    private fun loadSelectedFavorite() {
        val selectedRow = favoritesTable.selectedRow
        if (selectedRow >= 0) {
            try {
                val favorites = if (currentFolder != null) {
                    favoritesService.getFavoritesByFolder(currentFolder)
                } else {
                    favoritesService.getAllFavorites()
                }
                
                val favorite = favorites[selectedRow]
                onLoadRequestCallback?.invoke(favorite)
                
                // Mark as used
                favoritesService.markFavoriteAsUsed(favorite.id)
                loadFavorites()
                
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    private fun exportFavorites() {
        // Show export format menu
        val popupMenu = JPopupMenu()
        
        val exportNativeItem = JMenuItem(HttpPalBundle.message("favorites.export.native"))
        exportNativeItem.addActionListener {
            exportFavoritesNative()
        }
        popupMenu.add(exportNativeItem)
        
        val exportPostmanItem = JMenuItem(HttpPalBundle.message("favorites.export.postman"))
        exportPostmanItem.addActionListener {
            exportFavoritesPostman()
        }
        popupMenu.add(exportPostmanItem)
        
        val exportJMeterItem = JMenuItem(HttpPalBundle.message("favorites.export.jmeter"))
        exportJMeterItem.addActionListener {
            exportFavoritesJMeter()
        }
        popupMenu.add(exportJMeterItem)
        
        // Show menu below export button
        val location = exportButton.locationOnScreen
        popupMenu.setLocation(location.x, location.y + exportButton.height)
        popupMenu.isVisible = true
    }
    
    private fun exportFavoritesNative() {
        try {
            val json = favoritesService.exportFavorites()
            val fileChooser = JFileChooser()
            fileChooser.selectedFile = java.io.File("favorites.json")
            
            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                fileChooser.selectedFile.writeText(json)
                JOptionPane.showMessageDialog(
                    this,
                    HttpPalBundle.message("success.favorites.exported", fileChooser.selectedFile.absolutePath),
                    HttpPalBundle.message("dialog.success"),
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                HttpPalBundle.message("error.favorites.export.failed", e.message ?: "Unknown error"),
                HttpPalBundle.message("error.title.general"),
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
    
    private fun exportFavoritesPostman() {
        try {
            // Get selected favorites or all favorites
            val favorites = getSelectedOrAllFavorites()
            
            if (favorites.isEmpty()) {
                JOptionPane.showMessageDialog(
                    this,
                    HttpPalBundle.message("error.favorites.export.empty"),
                    HttpPalBundle.message("error.title.general"),
                    JOptionPane.WARNING_MESSAGE
                )
                return
            }
            
            // Show export dialog
            val dialog = PostmanExportDialog(project, favorites.size) { filePath, options ->
                try {
                    val postmanService = project.getService(com.httppal.service.PostmanExportService::class.java)
                    val result = postmanService.exportFavoritesToPostman(favorites, "HttpPal Favorites", options)
                    
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
    
    private fun exportFavoritesJMeter() {
        try {
            // Get selected favorites or all favorites
            val favorites = getSelectedOrAllFavorites()
            
            if (favorites.isEmpty()) {
                JOptionPane.showMessageDialog(
                    this,
                    HttpPalBundle.message("error.favorites.export.empty"),
                    HttpPalBundle.message("error.title.general"),
                    JOptionPane.WARNING_MESSAGE
                )
                return
            }
            
            // Get current environment for JMeter export
            val environmentService = project.service<com.httppal.service.EnvironmentService>()
            val currentEnvironment = environmentService.getCurrentEnvironment()
            
            // Show JMeter export dialog
            val dialog = com.httppal.ui.JMeterExportDialog(
                project,
                favorites.map { it.request },
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
    
    private fun importFavorites() {
        // Show import format menu
        val popupMenu = JPopupMenu()
        
        val importNativeItem = JMenuItem(HttpPalBundle.message("favorites.import.native"))
        importNativeItem.addActionListener {
            importFavoritesNative()
        }
        popupMenu.add(importNativeItem)
        
        val importPostmanItem = JMenuItem(HttpPalBundle.message("favorites.import.postman"))
        importPostmanItem.addActionListener {
            importFavoritesPostman()
        }
        popupMenu.add(importPostmanItem)
        
        // Show menu below import button
        val location = importButton.locationOnScreen
        popupMenu.setLocation(location.x, location.y + importButton.height)
        popupMenu.isVisible = true
    }
    
    private fun importFavoritesNative() {
        try {
            val fileChooser = JFileChooser()
            fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json")
            
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                val json = fileChooser.selectedFile.readText()
                val success = favoritesService.importFavorites(json)
                
                if (success.success) {
                    refresh()
                    JOptionPane.showMessageDialog(
                        this,
                        HttpPalBundle.message("success.favorites.imported"),
                        HttpPalBundle.message("dialog.success"),
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } else {
                    JOptionPane.showMessageDialog(
                        this,
                        HttpPalBundle.message("error.favorites.import.failed", "Validation failed"),
                        HttpPalBundle.message("error.title.general"),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                HttpPalBundle.message("error.favorites.import.failed", e.message ?: "Unknown error"),
                HttpPalBundle.message("error.title.general"),
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
    
    private fun importFavoritesPostman() {
        try {
            val fileChooser = JFileChooser()
            fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Postman Collection", "json")
            
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                val postmanService = project.getService(com.httppal.service.PostmanImportService::class.java)
                
                // Parse collection first
                val json = fileChooser.selectedFile.readText()
                val parseResult = postmanService.parsePostmanCollection(json)
                
                if (parseResult is com.httppal.service.ParseResult.Failure) {
                    JOptionPane.showMessageDialog(
                        this,
                        HttpPalBundle.message("error.postman.import.parse.failed", parseResult.errors.joinToString("\n")),
                        HttpPalBundle.message("error.title.general"),
                        JOptionPane.ERROR_MESSAGE
                    )
                    return
                }
                
                val collection = (parseResult as ParseResult.Success).data
                
                // Show preview dialog
                val dialog = PostmanImportDialog(project, collection) { options ->
                    // Import after user confirms in the dialog with selected options
                    SwingUtilities.invokeLater {
                        try {
                            val result = postmanService.importFromPostman(fileChooser.selectedFile.absolutePath, options)
                            
                            if (result is com.httppal.service.ImportResult && result.success) {
                                refresh()
                                
                                val message = buildString {
                                    append(HttpPalBundle.message("success.postman.imported", result.importedCount))
                                    if (result.skippedCount > 0) {
                                        append("\n")
                                        append(HttpPalBundle.message("info.postman.import.skipped", result.skippedCount))
                                    }
                                    if (result.errors.isNotEmpty()) {
                                        append("\n\n")
                                        append("Import Errors:")
                                        append("\n")
                                        append(result.errors.take(5).joinToString("\n"))
                                    }
                                }
                                
                                JOptionPane.showMessageDialog(
                                    this,
                                    message,
                                    HttpPalBundle.message("dialog.success"),
                                    JOptionPane.INFORMATION_MESSAGE
                                )
                            } else {
                                // Handle case where result is not ImportResult or success is false
                                val errorMessage = if (result is com.httppal.service.ImportResult) {
                                    result.errors.joinToString("\n")
                                } else {
                                    "Unknown error occurred during import"
                                }
                                JOptionPane.showMessageDialog(
                                    this,
                                    HttpPalBundle.message("error.postman.import.failed", errorMessage),
                                    HttpPalBundle.message("error.title.general"),
                                    JOptionPane.ERROR_MESSAGE
                                )
                            }
                        } catch (e: Exception) {
                            JOptionPane.showMessageDialog(
                                this,
                                HttpPalBundle.message("error.postman.import.failed", e.message ?: "Unknown error"),
                                HttpPalBundle.message("error.title.general"),
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    }
                }
                
                dialog.show()
            }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                HttpPalBundle.message("error.postman.import.failed", e.message ?: "Unknown error"),
                HttpPalBundle.message("error.title.general"),
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
    
    private fun getSelectedOrAllFavorites(): List<FavoriteRequest> {
        val selectedRows = favoritesTable.selectedRows
        
        return if (selectedRows.isNotEmpty()) {
            // Export selected favorites
            val favorites = if (currentFolder != null) {
                favoritesService.getFavoritesByFolder(currentFolder)
            } else {
                favoritesService.getAllFavorites()
            }
            selectedRows.filter { it >= 0 && it < favorites.size }.map { favorites[it] }
        } else {
            // Export all favorites
            favoritesService.getAllFavorites()
        }
    }
    
    private fun truncateUrl(url: String): String {
        return if (url.length > 50) {
            url.substring(0, 47) + "..."
        } else {
            url
        }
    }
    
    fun setOnLoadRequestCallback(callback: (FavoriteRequest) -> Unit) {
        onLoadRequestCallback = callback
    }
    
    fun setOnAddCurrentCallback(callback: () -> Unit) {
        onAddCurrentCallback = callback
    }
    
    fun refresh() {
        loadFolders()
        loadFavorites()
    }
}