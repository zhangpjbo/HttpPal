package com.httppal.ui

import com.httppal.model.EndpointParameter
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.table.AbstractTableModel

/**
 * Panel for editing path parameters extracted from URL templates.
 * Automatically detects {param} patterns and provides input fields for values.
 */
class PathParametersPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val tableModel = PathParametersTableModel()
    private val table = JBTable(tableModel)
    private val emptyLabel = JBLabel("No path parameters in URL")
    
    // Callback when parameters change
    var onParametersChanged: ((Map<String, String>) -> Unit)? = null
    
    // Regex to match path parameters like {id}, {userId}, etc.
    private val pathParamPattern = Regex("\\{([^}]+)\\}")
    
    init {
        setupUI()
        setupTable()
        updateVisibility()
    }
    
    private fun setupUI() {
        border = JBUI.Borders.empty(5, 0)
        
        // Title label for better section separation
        val titlePanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        titlePanel.isOpaque = false
        val titleLabel = JBLabel("Path Parameters")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 12f)
        titleLabel.foreground = com.intellij.ui.JBColor.namedColor("Label.infoForeground", com.intellij.ui.JBColor.gray)
        titlePanel.add(titleLabel)
        add(titlePanel, BorderLayout.NORTH)
        
        // Content panel with CardLayout to switch between table and empty message
        val contentPanel = JPanel(CardLayout())
        contentPanel.isOpaque = false
        
        // Table view - use a more reasonable preferred height and ensure it can stretch
        val tableScrollPane = JBScrollPane(table)
        tableScrollPane.preferredSize = Dimension(-1, 120) // Give it a bit more height
        tableScrollPane.minimumSize = Dimension(-1, 80)
        tableScrollPane.border = JBUI.Borders.customLine(com.intellij.ui.JBColor.border(), 1, 0, 1, 0)
        contentPanel.add(tableScrollPane, "table")
        
        // Empty message view
        val emptyPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        emptyPanel.isOpaque = false
        emptyLabel.foreground = com.intellij.ui.JBColor.GRAY
        emptyLabel.font = emptyLabel.font.deriveFont(Font.ITALIC)
        emptyPanel.add(emptyLabel)
        contentPanel.add(emptyPanel, "empty")
        
        add(contentPanel, BorderLayout.CENTER)
    }
    
    private fun setupTable() {
        table.fillsViewportHeight = true
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.rowHeight = 28
        
        // Configure column widths
        val columnModel = table.columnModel
        columnModel.getColumn(0).preferredWidth = 100  // Parameter name
        columnModel.getColumn(1).preferredWidth = 200  // Value
        columnModel.getColumn(2).preferredWidth = 80   // Type
        columnModel.getColumn(3).preferredWidth = 60   // Required
        columnModel.getColumn(4).preferredWidth = 150  // Description
        
        // Make parameter name column non-editable (display only)
        // Value column is editable
    }
    
    /**
     * Update panel from URL - extract path parameters
     */
    fun updateFromUrl(url: String) {
        val matches = pathParamPattern.findAll(url)
        val paramNames = matches.map { it.groupValues[1] }.toList()
        
        // Preserve existing values/metadata for params that still exist
        val existingParams = tableModel.getAllParameters().associateBy { it.name }
        
        tableModel.clear()
        paramNames.forEach { name ->
            val existing = existingParams[name]
            tableModel.addRow(PathParameter(
                name = name, 
                value = existing?.value ?: "",
                description = existing?.description ?: "",
                required = existing?.required ?: true,
                type = existing?.type ?: "String"
            ))
        }
        
        updateVisibility()
    }
    
    /**
     * Get current path parameters
     */
    fun getParameters(): Map<String, String> {
        return tableModel.getParameters()
    }
    
    /**
     * Set parameter values
     */
    fun setParameters(params: Map<String, String>) {
        // Log before setting
        com.httppal.util.LoggingUtils.logWithContext(
            com.httppal.util.LoggingUtils.LogLevel.DEBUG,
            "Setting path parameter values",
            mapOf(
                "paramCount" to params.size,
                "params" to params.keys.joinToString(", "),
                "values" to params.values.joinToString(", "),
                "existingParams" to tableModel.getAllParameters().map { it.name }.joinToString(", ")
            )
        )

        var anyUpdated = false
        params.forEach { (name, value) ->
            val updated = tableModel.setParameterValue(name, value)
            if (updated) anyUpdated = true
        }

        // Log after setting
        val currentParams = getParameters()
        com.httppal.util.LoggingUtils.logWithContext(
            com.httppal.util.LoggingUtils.LogLevel.DEBUG,
            "Path parameter values set",
            mapOf(
                "resultParams" to currentParams.keys.joinToString(", "),
                "resultValues" to currentParams.values.joinToString(", ")
            )
        )

        // Force update visibility to ensure panel is shown when parameters exist
        updateVisibility()

        // Force table refresh
        table.revalidate()
        table.repaint()

        // Trigger callback only if at least one parameter was updated
        if (anyUpdated) {
            onParametersChanged?.invoke(getParameters())
        }
    }

    /**
     * Set parameters from list of EndpointParameter objects (to include descriptions)
     * Merges with existing parameters to preserve values and metadata
     */
    fun setParametersList(params: List<EndpointParameter>) {
        // Use merge logic instead of clear and rebuild
        params.forEach { param ->
            tableModel.updateOrAddParameter(
                PathParameter(
                    name = param.name,
                    value = param.defaultValue ?: "",
                    description = param.description ?: "",
                    required = param.required,
                    type = param.dataType ?: "String"
                )
            )
        }
        
        // Update visibility
        if (params.isNotEmpty()) {
            updateVisibility()
        }
    }
    
    /**
     * Apply path parameters to a URL template
     * Replaces {param} with actual values
     */
    fun applyToUrl(urlTemplate: String): String {
        var result = urlTemplate
        val params = getParameters()
        val replacedParams = mutableListOf<String>()
        val missingParams = mutableListOf<String>()
        
        // Log input
        com.httppal.util.LoggingUtils.logWithContext(
            com.httppal.util.LoggingUtils.LogLevel.DEBUG,
            "Applying path parameters to URL",
            mapOf(
                "urlTemplate" to urlTemplate,
                "availableParams" to params.keys.joinToString(", "),
                "paramValues" to params.entries.joinToString(", ") { "${it.key}=${it.value}" }
            )
        )
        
        getParameters().forEach { (name, value) ->
            val placeholder = "{$name}"
            if (urlTemplate.contains(placeholder)) {
                if (value.isNotBlank()) {
                    result = result.replace(placeholder, value)
                    replacedParams.add(name)
                } else {
                    missingParams.add(name)
                }
            }
        }
        
        // Log warning if any parameters are missing
        if (missingParams.isNotEmpty()) {
            com.httppal.util.LoggingUtils.logWithContext(
                com.httppal.util.LoggingUtils.LogLevel.WARN,
                "Path parameters with empty values were not replaced",
                mapOf(
                    "missingParams" to missingParams.joinToString(", "),
                    "urlTemplate" to urlTemplate
                )
            )
        }
        
        // Log successful replacements at DEBUG level
        if (replacedParams.isNotEmpty()) {
            com.httppal.util.LoggingUtils.logWithContext(
                com.httppal.util.LoggingUtils.LogLevel.DEBUG,
                "Path parameters successfully applied to URL",
                mapOf(
                    "replacedCount" to replacedParams.size,
                    "replacedParams" to replacedParams.joinToString(", "),
                    "originalUrl" to urlTemplate,
                    "resultUrl" to result
                )
            )
        } else {
            com.httppal.util.LoggingUtils.logWithContext(
                com.httppal.util.LoggingUtils.LogLevel.WARN,
                "No path parameters were replaced in URL",
                mapOf(
                    "urlTemplate" to urlTemplate,
                    "availableParams" to params.keys.joinToString(", ")
                )
            )
        }
        
        return result
    }
    
    /**
     * Check if all required path parameters have values
     */
    fun hasAllValues(): Boolean {
        return tableModel.getAllParameters().all { it.value.isNotBlank() }
    }
    
    /**
     * Get list of missing parameter names
     */
    fun getMissingParameters(): List<String> {
        return tableModel.getAllParameters()
            .filter { it.value.isBlank() }
            .map { it.name }
    }
    
    private fun updateVisibility() {
        val contentPanel = (getComponent(1) as? JPanel) ?: return
        val cardLayout = contentPanel.layout as? CardLayout ?: return
        
        if (tableModel.rowCount > 0) {
            cardLayout.show(contentPanel, "table")
            isVisible = true
        } else {
            cardLayout.show(contentPanel, "empty")
            // Hide entire panel when no path params
            isVisible = false
        }
        
        revalidate()
        repaint()
    }
    
    /**
     * Data class for path parameter
     */
    data class PathParameter(
        val name: String,
        var value: String = "",
        var description: String = "",
        var required: Boolean = true,
        var type: String = "String"
    )
    
    /**
     * Table model for path parameters
     */
    private inner class PathParametersTableModel : AbstractTableModel() {
        private val columns = arrayOf("Parameter", "Value", "Type", "Required", "Description")
        private val data = mutableListOf<PathParameter>()
        
        override fun getRowCount(): Int = data.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]
        
        override fun getColumnClass(columnIndex: Int): Class<*> {
            return if (columnIndex == 3) Boolean::class.java else String::class.java
        }
        
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            // Only value column is editable
            return columnIndex == 1
        }
        
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            val row = data[rowIndex]
            return when (columnIndex) {
                0 -> row.name // User requested remove {}
                1 -> row.value
                2 -> row.type
                3 -> row.required
                4 -> row.description
                else -> null
            }
        }
        
        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex == 1) {
                data[rowIndex].value = aValue?.toString() ?: ""
                fireTableCellUpdated(rowIndex, columnIndex)
                onParametersChanged?.invoke(getParameters())
            }
        }
        
        fun addRow(param: PathParameter) {
            data.add(param)
            fireTableRowsInserted(data.size - 1, data.size - 1)
        }
        
        fun clear() {
            val size = data.size
            data.clear()
            if (size > 0) {
                fireTableRowsDeleted(0, size - 1)
            }
        }
        
        fun setParameterValue(name: String, value: String): Boolean {
            val param = data.find { it.name == name }
            if (param != null) {
                // Only update the value field, preserve all other metadata
                param.value = value
                val index = data.indexOf(param)
                fireTableCellUpdated(index, 1)
                return true
            } else {
                // Log warning if parameter not found
                com.httppal.util.LoggingUtils.logWithContext(
                    com.httppal.util.LoggingUtils.LogLevel.WARN,
                    "Attempted to set value for non-existent path parameter",
                    mapOf("parameterName" to name, "value" to value)
                )
                return false
            }
        }
        
        fun getParameters(): Map<String, String> {
            return data.associate { it.name to it.value }
        }
        
        fun getParameterValue(name: String): String {
            return data.find { it.name == name }?.value ?: ""
        }

        fun updateOrAddParameter(param: PathParameter) {
            val existingIndex = data.indexOfFirst { it.name == param.name }
            if (existingIndex != -1) {
                val existing = data[existingIndex]
                // Preserve existing value and description if they are not blank
                data[existingIndex] = existing.copy(
                    value = if (existing.value.isNotBlank()) existing.value else param.value,
                    description = if (existing.description.isNotBlank()) existing.description else param.description,
                    required = param.required,
                    type = param.type
                )
                fireTableRowsUpdated(existingIndex, existingIndex)
            } else {
                data.add(param)
                fireTableRowsInserted(data.size - 1, data.size - 1)
            }
        }

        fun getAllParameters(): List<PathParameter> = data.toList()
    }
}
