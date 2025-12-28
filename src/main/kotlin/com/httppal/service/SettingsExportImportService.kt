package com.httppal.service

import com.httppal.model.Environment
import com.httppal.settings.HttpPalSettings
import com.httppal.settings.ProjectSettings
import com.httppal.settings.SettingsValidator
import com.httppal.util.JacksonUtils
import com.httppal.util.MapUtils.safeMapOf
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Service for handling settings export and import operations
 */
@Service
class SettingsExportImportService {
    
    data class ExportResult(
        val success: Boolean,
        val filePath: String? = null,
        val error: String? = null
    )
    
    data class ImportResult(
        val success: Boolean,
        val importedSettings: Int = 0,
        val skippedSettings: Int = 0,
        val errors: List<String> = emptyList()
    )
    
    /**
     * Export application-level settings to a file
     */
    fun exportApplicationSettings(exportPath: String): ExportResult {
        return try {
            val settings = HttpPalSettings.getInstance()
            val exportData = settings.exportSettings()
            
            // Add metadata
            val enrichedData = exportData.toMutableMap()
            enrichedData["exportType"] = "application"
            enrichedData["exportVersion"] = "1.0"
            enrichedData["exportTimestamp"] = System.currentTimeMillis()
            enrichedData["pluginVersion"] = getPluginVersion()
            
            val jsonContent = JacksonUtils.mapToJson(enrichedData)
            val exportFile = File(exportPath, "httppal-app-settings-${System.currentTimeMillis()}.json")
            exportFile.writeText(jsonContent)
            
            ExportResult(true, exportFile.absolutePath)
        } catch (e: Exception) {
            ExportResult(false, error = "Failed to export application settings: ${e.message}")
        }
    }
    
    /**
     * Export project-level settings to a file
     */
    fun exportProjectSettings(project: Project, exportPath: String): ExportResult {
        return try {
            val projectSettings = ProjectSettings.getInstance(project)
            val stats = projectSettings.getStatistics()
            
            val exportData = safeMapOf(
                "exportType" to "project",
                "exportVersion" to "1.0",
                "exportTimestamp" to System.currentTimeMillis(),
                "pluginVersion" to getPluginVersion(),
                "projectName" to project.name,
                "projectPath" to project.basePath,
                // Project settings
                "manualEndpoints" to projectSettings.getManualEndpoints(),
                "discoveryEnabled" to projectSettings.isDiscoveryEnabled(),
                "autoRefreshEnabled" to projectSettings.isAutoRefreshEnabled(),
                "excludedPackages" to projectSettings.getExcludedPackages(),
                "includedPackages" to projectSettings.getIncludedPackages(),
                "environments" to projectSettings.getEnvironments(),
                "currentEnvironmentId" to projectSettings.getCurrentEnvironmentId(),
                // Statistics
                "statistics" to stats
            )
            
            val jsonContent = JacksonUtils.mapToJson(exportData)
            val exportFile = File(exportPath, "httppal-project-settings-${project.name}-${System.currentTimeMillis()}.json")
            exportFile.writeText(jsonContent)
            
            ExportResult(true, exportFile.absolutePath)
        } catch (e: Exception) {
            ExportResult(false, error = "Failed to export project settings: ${e.message}")
        }
    }
    
    /**
     * Export both application and project settings to a single file
     */
    fun exportAllSettings(project: Project, exportPath: String): ExportResult {
        return try {
            val appSettings = HttpPalSettings.getInstance()
            val projectSettings = ProjectSettings.getInstance(project)
            
            val exportData = safeMapOf(
                "exportType" to "complete",
                "exportVersion" to "1.0",
                "exportTimestamp" to System.currentTimeMillis(),
                "pluginVersion" to getPluginVersion(),
                "projectName" to project.name,
                "projectPath" to project.basePath,
                // Application settings
                "applicationSettings" to appSettings.exportSettings(),
                // Project settings
                "projectSettings" to mapOf(
                    "manualEndpoints" to projectSettings.getManualEndpoints(),
                    "discoveryEnabled" to projectSettings.isDiscoveryEnabled(),
                    "autoRefreshEnabled" to projectSettings.isAutoRefreshEnabled(),
                    "excludedPackages" to projectSettings.getExcludedPackages(),
                    "includedPackages" to projectSettings.getIncludedPackages(),
                    "environments" to projectSettings.getEnvironments(),
                    "currentEnvironmentId" to projectSettings.getCurrentEnvironmentId()
                ),
                // Combined statistics
                "statistics" to mapOf(
                    "application" to appSettings.getStatistics(),
                    "project" to projectSettings.getStatistics()
                )
            )
            
            val jsonContent = JacksonUtils.mapToJson(exportData)
            val exportFile = File(exportPath, "httppal-complete-settings-${project.name}-${System.currentTimeMillis()}.json")
            exportFile.writeText(jsonContent)
            
            ExportResult(true, exportFile.absolutePath)
        } catch (e: Exception) {
            ExportResult(false, error = "Failed to export complete settings: ${e.message}")
        }
    }
    
    /**
     * Import settings from a file
     */
    fun importSettings(importFilePath: String, project: Project? = null): ImportResult {
        return try {
            val importFile = File(importFilePath)
            if (!importFile.exists() || !importFile.isFile) {
                return ImportResult(false, errors = listOf("Import file does not exist or is not a valid file"))
            }
            
            val jsonContent = importFile.readText()
            
            // Validate JSON format
            if (!JacksonUtils.isValidJson(jsonContent)) {
                return ImportResult(false, errors = listOf("Invalid JSON format in import file"))
            }
            
            val importData = JacksonUtils.jsonToMap(jsonContent)
            
            // Validate import data structure
            val validationResult = SettingsValidator.validateImportData(importData)
            if (!validationResult.isValid) {
                return ImportResult(false, errors = validationResult.errors)
            }
            
            val exportType = importData["exportType"] as? String ?: "unknown"
            val errors = mutableListOf<String>()
            var importedCount = 0
            var skippedCount = 0
            
            when (exportType) {
                "application" -> {
                    val result = importApplicationSettings(importData)
                    importedCount += result.first
                    skippedCount += result.second
                    errors.addAll(result.third)
                }
                "project" -> {
                    if (project != null) {
                        val result = importProjectSettings(importData, project)
                        importedCount += result.first
                        skippedCount += result.second
                        errors.addAll(result.third)
                    } else {
                        errors.add("Project settings found but no project context provided")
                    }
                }
                "complete" -> {
                    // Import application settings
                    val appData = importData["applicationSettings"] as? Map<String, Any>
                    if (appData != null) {
                        val appResult = importApplicationSettings(appData)
                        importedCount += appResult.first
                        skippedCount += appResult.second
                        errors.addAll(appResult.third)
                    }
                    
                    // Import project settings if project context is available
                    if (project != null) {
                        val projectData = importData["projectSettings"] as? Map<String, Any>
                        if (projectData != null) {
                            val projResult = importProjectSettings(projectData, project)
                            importedCount += projResult.first
                            skippedCount += projResult.second
                            errors.addAll(projResult.third)
                        }
                    } else {
                        errors.add("Project settings found but no project context provided")
                    }
                }
                else -> {
                    // Try to import as legacy format (direct application settings)
                    val result = importApplicationSettings(importData)
                    importedCount += result.first
                    skippedCount += result.second
                    errors.addAll(result.third)
                }
            }
            
            ImportResult(
                success = errors.isEmpty() || importedCount > 0,
                importedSettings = importedCount,
                skippedSettings = skippedCount,
                errors = errors
            )
        } catch (e: Exception) {
            ImportResult(false, errors = listOf("Failed to import settings: ${e.message}"))
        }
    }
    
    private fun importApplicationSettings(importData: Map<String, Any>): Triple<Int, Int, List<String>> {
        val settings = HttpPalSettings.getInstance()
        val errors = mutableListOf<String>()
        var imported = 0
        var skipped = 0
        
        try {
            settings.importSettings(importData)
            imported = importData.size
        } catch (e: Exception) {
            errors.add("Failed to import application settings: ${e.message}")
            skipped = importData.size
        }
        
        return Triple(imported, skipped, errors)
    }
    
    private fun importProjectSettings(importData: Map<String, Any>, project: Project): Triple<Int, Int, List<String>> {
        val projectSettings = ProjectSettings.getInstance(project)
        val errors = mutableListOf<String>()
        var imported = 0
        var skipped = 0
        
        try {
            // Import discovery settings
            importData["discoveryEnabled"]?.toString()?.toBooleanStrictOrNull()?.let {
                projectSettings.setDiscoveryEnabled(it)
                imported++
            }
            
            importData["autoRefreshEnabled"]?.toString()?.toBooleanStrictOrNull()?.let {
                projectSettings.setAutoRefreshEnabled(it)
                imported++
            }
            
            // Import package filters
            @Suppress("UNCHECKED_CAST")
            (importData["excludedPackages"] as? List<String>)?.let { packages ->
                packages.forEach { projectSettings.addExcludedPackage(it) }
                imported++
            }
            
            @Suppress("UNCHECKED_CAST")
            (importData["includedPackages"] as? List<String>)?.let { packages ->
                packages.forEach { projectSettings.addIncludedPackage(it) }
                imported++
            }
            
            // Import environments
            @Suppress("UNCHECKED_CAST")
            (importData["environments"] as? List<Map<String, Any>>)?.let { envList ->
                envList.forEach { envData ->
                    try {
                        val environment = Environment(
                            id = envData["id"] as? String ?: return@forEach,
                            name = envData["name"] as? String ?: return@forEach,
                            baseUrl = envData["baseUrl"] as? String ?: return@forEach,
                            globalHeaders = (envData["globalHeaders"] as? Map<String, String>) ?: emptyMap(),
                            description = envData["description"] as? String
                        )
                        projectSettings.addEnvironment(environment)
                        imported++
                    } catch (e: Exception) {
                        errors.add("Failed to import environment: ${e.message}")
                        skipped++
                    }
                }
            }
            
            // Import current environment
            importData["currentEnvironmentId"]?.toString()?.let {
                projectSettings.setCurrentEnvironmentId(it)
                imported++
            }
            
        } catch (e: Exception) {
            errors.add("Failed to import project settings: ${e.message}")
        }
        
        return Triple(imported, skipped, errors)
    }
    
    /**
     * Get plugin version for export metadata
     */
    private fun getPluginVersion(): String {
        return try {
            // Try to get version from plugin descriptor
            "1.0.0" // Fallback version
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    /**
     * Validate export file format
     */
    fun validateExportFile(filePath: String): List<String> {
        val errors = mutableListOf<String>()
        
        try {
            val file = File(filePath)
            if (!file.exists()) {
                errors.add("File does not exist")
                return errors
            }
            
            if (!file.isFile) {
                errors.add("Path is not a file")
                return errors
            }
            
            if (!file.canRead()) {
                errors.add("File is not readable")
                return errors
            }
            
            val content = file.readText()
            if (!JacksonUtils.isValidJson(content)) {
                errors.add("File does not contain valid JSON")
                return errors
            }
            
            val data = JacksonUtils.jsonToMap(content)
            val exportType = data["exportType"] as? String
            
            if (exportType == null) {
                errors.add("Export type not specified (may be legacy format)")
            } else if (exportType !in listOf("application", "project", "complete")) {
                errors.add("Unknown export type: $exportType")
            }
            
            // Check for required fields based on export type
            when (exportType) {
                "application" -> {
                    if (!data.containsKey("maxHistorySize")) {
                        errors.add("Missing required application setting: maxHistorySize")
                    }
                }
                "project" -> {
                    if (!data.containsKey("discoveryEnabled")) {
                        errors.add("Missing required project setting: discoveryEnabled")
                    }
                }
                "complete" -> {
                    if (!data.containsKey("applicationSettings")) {
                        errors.add("Missing application settings in complete export")
                    }
                    if (!data.containsKey("projectSettings")) {
                        errors.add("Missing project settings in complete export")
                    }
                }
            }
            
        } catch (e: Exception) {
            errors.add("Error validating file: ${e.message}")
        }
        
        return errors
    }
    
    companion object {
        fun getInstance(): SettingsExportImportService {
            return ApplicationManager.getApplication().getService(SettingsExportImportService::class.java)
        }
    }
}