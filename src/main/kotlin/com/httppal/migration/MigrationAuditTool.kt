package com.httppal.migration

import java.io.File

/**
 * Main migration audit tool that orchestrates scanning and report generation
 */
class MigrationAuditTool {

    private val kotlinScanner = KotlinCodeScanner()
    private val pluginXmlScanner = PluginXmlScanner()

    /**
     * Run a complete audit on a project directory
     */
    fun auditProject(projectRoot: File): MigrationAuditReport {
        // Scan Kotlin source files
        val srcDir = File(projectRoot, "src")
        val fileEntries = if (srcDir.exists()) {
            kotlinScanner.scanDirectory(srcDir)
        } else {
            emptyList()
        }

        // Scan plugin.xml
        val configIssues = pluginXmlScanner.findAndScanPluginXml(projectRoot)

        // Categorize components
        val serviceComponents = mutableListOf<ComponentAuditEntry>()
        val actionComponents = mutableListOf<ComponentAuditEntry>()
        val uiComponents = mutableListOf<ComponentAuditEntry>()
        val testComponents = mutableListOf<ComponentAuditEntry>()

        fileEntries.forEach { entry ->
            val componentEntry = createComponentEntry(entry)
            when (entry.componentType) {
                ComponentType.SERVICE -> serviceComponents.add(componentEntry)
                ComponentType.ACTION -> actionComponents.add(componentEntry)
                ComponentType.UI -> uiComponents.add(componentEntry)
                ComponentType.TEST -> testComponents.add(componentEntry)
                else -> {} // Unknown components not categorized
            }
        }

        return MigrationAuditReport(
            totalFiles = fileEntries.size,
            filesNeedingUpdate = fileEntries,
            serviceComponents = serviceComponents,
            actionComponents = actionComponents,
            uiComponents = uiComponents,
            testComponents = testComponents,
            configurationIssues = configIssues
        )
    }

    private fun createComponentEntry(fileEntry: FileAuditEntry): ComponentAuditEntry {
        val fileName = File(fileEntry.filePath).nameWithoutExtension
        val primaryIssue = fileEntry.issues.firstOrNull()
        
        return ComponentAuditEntry(
            name = fileName,
            filePath = fileEntry.filePath,
            currentApi = primaryIssue?.currentCode ?: "Unknown",
            targetApi = primaryIssue?.suggestedCode ?: "Unknown",
            migrationComplexity = calculateComplexity(fileEntry.issues)
        )
    }

    private fun calculateComplexity(issues: List<ApiIssue>): MigrationComplexity {
        val errorCount = issues.count { it.severity == IssueSeverity.ERROR }
        val totalIssues = issues.size

        return when {
            errorCount >= 5 || totalIssues >= 10 -> MigrationComplexity.HIGH
            errorCount >= 2 || totalIssues >= 5 -> MigrationComplexity.MEDIUM
            else -> MigrationComplexity.LOW
        }
    }

    /**
     * Generate a human-readable report
     */
    fun generateReport(auditReport: MigrationAuditReport): String {
        val sb = StringBuilder()
        
        sb.appendLine("=" .repeat(80))
        sb.appendLine("IntelliJ Platform Gradle Plugin 2.x Migration Audit Report")
        sb.appendLine("=" .repeat(80))
        sb.appendLine()
        
        sb.appendLine("Summary:")
        sb.appendLine("  Total files needing update: ${auditReport.totalFiles}")
        sb.appendLine("  Service components: ${auditReport.serviceComponents.size}")
        sb.appendLine("  Action components: ${auditReport.actionComponents.size}")
        sb.appendLine("  UI components: ${auditReport.uiComponents.size}")
        sb.appendLine("  Test components: ${auditReport.testComponents.size}")
        sb.appendLine("  Configuration issues: ${auditReport.configurationIssues.size}")
        sb.appendLine()

        if (auditReport.serviceComponents.isNotEmpty()) {
            sb.appendLine("-" .repeat(80))
            sb.appendLine("Service Components:")
            sb.appendLine("-" .repeat(80))
            auditReport.serviceComponents.forEach { component ->
                sb.appendLine("  ${component.name} (${component.migrationComplexity})")
                sb.appendLine("    File: ${component.filePath}")
                sb.appendLine()
            }
        }

        if (auditReport.actionComponents.isNotEmpty()) {
            sb.appendLine("-" .repeat(80))
            sb.appendLine("Action Components:")
            sb.appendLine("-" .repeat(80))
            auditReport.actionComponents.forEach { component ->
                sb.appendLine("  ${component.name} (${component.migrationComplexity})")
                sb.appendLine("    File: ${component.filePath}")
                sb.appendLine()
            }
        }

        if (auditReport.uiComponents.isNotEmpty()) {
            sb.appendLine("-" .repeat(80))
            sb.appendLine("UI Components:")
            sb.appendLine("-" .repeat(80))
            auditReport.uiComponents.forEach { component ->
                sb.appendLine("  ${component.name} (${component.migrationComplexity})")
                sb.appendLine("    File: ${component.filePath}")
                sb.appendLine()
            }
        }

        if (auditReport.testComponents.isNotEmpty()) {
            sb.appendLine("-" .repeat(80))
            sb.appendLine("Test Components:")
            sb.appendLine("-" .repeat(80))
            auditReport.testComponents.forEach { component ->
                sb.appendLine("  ${component.name} (${component.migrationComplexity})")
                sb.appendLine("    File: ${component.filePath}")
                sb.appendLine()
            }
        }

        if (auditReport.configurationIssues.isNotEmpty()) {
            sb.appendLine("-" .repeat(80))
            sb.appendLine("Configuration Issues:")
            sb.appendLine("-" .repeat(80))
            auditReport.configurationIssues.forEach { issue ->
                sb.appendLine("  ${issue.element}:")
                sb.appendLine("    Issue: ${issue.issue}")
                sb.appendLine("    Suggestion: ${issue.suggestion}")
                sb.appendLine()
            }
        }

        sb.appendLine("-" .repeat(80))
        sb.appendLine("Detailed Issues by File:")
        sb.appendLine("-" .repeat(80))
        auditReport.filesNeedingUpdate.forEach { fileEntry ->
            sb.appendLine()
            sb.appendLine("File: ${fileEntry.filePath}")
            sb.appendLine("Type: ${fileEntry.componentType}")
            sb.appendLine("Issues:")
            fileEntry.issues.forEach { issue ->
                sb.appendLine("  Line ${issue.lineNumber} [${issue.severity}] ${issue.issueType}:")
                sb.appendLine("    Current:  ${issue.currentCode}")
                sb.appendLine("    Suggested: ${issue.suggestedCode}")
            }
        }

        sb.appendLine()
        sb.appendLine("=" .repeat(80))
        sb.appendLine("End of Report")
        sb.appendLine("=" .repeat(80))

        return sb.toString()
    }
}
