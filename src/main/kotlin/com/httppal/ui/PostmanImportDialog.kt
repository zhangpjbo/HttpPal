package com.httppal.ui

import com.httppal.model.PostmanCollection
import com.httppal.model.PostmanItem
import com.httppal.service.PostmanImportOptions
import com.httppal.util.HttpPalBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

/**
 * Dialog for previewing and confirming Postman import
 */
class PostmanImportDialog(
    private val project: Project,
    private val collection: PostmanCollection,
    private val onConfirm: (PostmanImportOptions) -> Unit
) : DialogWrapper(project) {
    
    private val previewTableModel = PreviewTableModel()
    private val previewTable = JBTable(previewTableModel)
    private val importToFavoritesCheckBox = JBCheckBox(
        HttpPalBundle.message("dialog.postman.import.to.favorites"),
        true
    )
    private val preserveFoldersCheckBox = JBCheckBox(
        HttpPalBundle.message("dialog.postman.import.preserve.folders"),
        true
    )
    
    init {
        title = HttpPalBundle.message("dialog.postman.import.title")
        setSize(700, 500)
        init()
        
        buildPreviewData()
    }
    
    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.border = JBUI.Borders.empty(10)
        
        // Info panel
        val infoPanel = JPanel(BorderLayout())
        infoPanel.border = JBUI.Borders.emptyBottom(10)
        
        val collectionInfo = collection.info
        val itemCount = countItems(collection.item)
        val infoText = """
            <html>
            <b>${HttpPalBundle.message("dialog.postman.import.collection.name")}:</b> ${collectionInfo.name}<br>
            ${collectionInfo.description?.let { "<b>${HttpPalBundle.message("dialog.postman.import.collection.description")}:</b> $it<br>" } ?: ""}
            <b>${HttpPalBundle.message("dialog.postman.import.item.count")}:</b> $itemCount
            </html>
        """.trimIndent()
        
        val infoLabel = JBLabel(infoText)
        infoPanel.add(infoLabel, BorderLayout.NORTH)
        
        mainPanel.add(infoPanel, BorderLayout.NORTH)
        
        // Preview table
        val tablePanel = JPanel(BorderLayout())
        tablePanel.border = JBUI.Borders.empty(10, 0)
        
        val tableLabel = JBLabel(HttpPalBundle.message("dialog.postman.import.preview.label"))
        tablePanel.add(tableLabel, BorderLayout.NORTH)
        
        previewTable.setShowGrid(true)
        previewTable.autoResizeMode = JBTable.AUTO_RESIZE_ALL_COLUMNS
        
        // Set column widths
        val columnModel = previewTable.columnModel
        columnModel.getColumn(0).preferredWidth = 200 // Name
        columnModel.getColumn(1).preferredWidth = 80  // Method
        columnModel.getColumn(2).preferredWidth = 300 // URL
        columnModel.getColumn(3).preferredWidth = 120 // Folder
        
        val scrollPane = JBScrollPane(previewTable)
        scrollPane.preferredSize = Dimension(650, 300)
        tablePanel.add(scrollPane, BorderLayout.CENTER)
        
        mainPanel.add(tablePanel, BorderLayout.CENTER)
        
        // Options panel
        val optionsPanel = JPanel()
        optionsPanel.layout = BoxLayout(optionsPanel, BoxLayout.Y_AXIS)
        optionsPanel.border = JBUI.Borders.emptyTop(10)
        
        // Enable the checkbox so users can choose where to import
        importToFavoritesCheckBox.isEnabled = true
        optionsPanel.add(importToFavoritesCheckBox)
        optionsPanel.add(preserveFoldersCheckBox)
        
        mainPanel.add(optionsPanel, BorderLayout.SOUTH)
        
        return mainPanel
    }
    
    private fun buildPreviewData() {
        val items = mutableListOf<PreviewItem>()
        collectItems(collection.item, null, items)
        previewTableModel.setItems(items)
    }
    
    private fun collectItems(
        items: List<PostmanItem>,
        parentFolder: String?,
        result: MutableList<PreviewItem>
    ) {
        items.forEach { item ->
            if (item.request != null) {
                // This is a request
                val folder = if (preserveFoldersCheckBox.isSelected) parentFolder else null
                result.add(
                    PreviewItem(
                        name = item.name,
                        method = item.request.method,
                        url = item.request.url.raw,
                        folder = folder
                    )
                )
            } else if (item.item != null) {
                // This is a folder
                val folderName = if (parentFolder != null) {
                    "$parentFolder/${item.name}"
                } else {
                    item.name
                }
                collectItems(item.item, folderName, result)
            }
        }
    }
    
    private fun countItems(items: List<PostmanItem>): Int {
        var count = 0
        items.forEach { item ->
            if (item.request != null) {
                count++
            } else if (item.item != null) {
                count += countItems(item.item)
            }
        }
        return count
    }
    
    override fun doOKAction() {
        super.doOKAction()
        val options = PostmanImportOptions(
            importToFavorites = importToFavoritesCheckBox.isSelected,
            preserveFolderStructure = preserveFoldersCheckBox.isSelected
        )
        onConfirm(options)
    }
    
    /**
     * Preview item data class
     */
    data class PreviewItem(
        val name: String,
        val method: String,
        val url: String,
        val folder: String?
    )
    
    /**
     * Table model for preview
     */
    private class PreviewTableModel : AbstractTableModel() {
        private val columnNames = arrayOf(
            HttpPalBundle.message("table.column.name"),
            HttpPalBundle.message("table.column.method"),
            HttpPalBundle.message("table.column.url"),
            HttpPalBundle.message("table.column.folder")
        )
        
        private val items = mutableListOf<PreviewItem>()
        
        fun setItems(items: List<PreviewItem>) {
            this.items.clear()
            this.items.addAll(items)
            fireTableDataChanged()
        }
        
        override fun getRowCount(): Int = items.size
        
        override fun getColumnCount(): Int = columnNames.size
        
        override fun getColumnName(column: Int): String = columnNames[column]
        
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val item = items[rowIndex]
            return when (columnIndex) {
                0 -> item.name
                1 -> item.method
                2 -> item.url
                3 -> item.folder ?: ""
                else -> ""
            }
        }
        
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
    }
}