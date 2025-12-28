package com.httppal.migration

import java.io.File

/**
 * Scanner for Kotlin code files to identify 1.x API usage patterns
 */
class KotlinCodeScanner {

    /**
     * Scan a Kotlin file for 1.x API usage patterns
     */
    fun scanFile(file: File): FileAuditEntry? {
        if (!file.exists() || !file.name.endsWith(".kt")) {
            return null
        }

        val issues = mutableListOf<ApiIssue>()
        val lines = file.readLines()

        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            
            // Check for ServiceManager usage (1.x pattern)
            if (line.contains("ServiceManager.getService")) {
                issues.add(
                    ApiIssue(
                        lineNumber = lineNumber,
                        currentCode = line.trim(),
                        suggestedCode = line.replace("ServiceManager.getService", "service"),
                        issueType = IssueType.SERVICE_MANAGER_USAGE,
                        severity = IssueSeverity.ERROR
                    )
                )
            }

            // Check for ApplicationManager.getApplication().getService (1.x pattern)
            if (line.contains("ApplicationManager.getApplication().getService")) {
                issues.add(
                    ApiIssue(
                        lineNumber = lineNumber,
                        currentCode = line.trim(),
                        suggestedCode = line.replace(
                            "ApplicationManager.getApplication().getService",
                            "service"
                        ),
                        issueType = IssueType.APPLICATION_MANAGER_USAGE,
                        severity = IssueSeverity.ERROR
                    )
                )
            }

            // Check for ContentFactory.SERVICE.getInstance() (1.x pattern)
            if (line.contains("ContentFactory.SERVICE.getInstance()")) {
                issues.add(
                    ApiIssue(
                        lineNumber = lineNumber,
                        currentCode = line.trim(),
                        suggestedCode = line.replace(
                            "ContentFactory.SERVICE.getInstance()",
                            "ContentFactory.getInstance()"
                        ),
                        issueType = IssueType.CONTENT_FACTORY,
                        severity = IssueSeverity.WARNING
                    )
                )
            }

            // Check for old test base classes
            if (line.contains("LightPlatformCodeInsightFixtureTestCase")) {
                issues.add(
                    ApiIssue(
                        lineNumber = lineNumber,
                        currentCode = line.trim(),
                        suggestedCode = line.replace(
                            "LightPlatformCodeInsightFixtureTestCase",
                            "BasePlatformTestCase"
                        ),
                        issueType = IssueType.TEST_BASE_CLASS,
                        severity = IssueSeverity.WARNING
                    )
                )
            }

            // Check for deprecated package imports
            if (line.contains("import") && line.contains("com.intellij.openapi.components.ServiceManager")) {
                issues.add(
                    ApiIssue(
                        lineNumber = lineNumber,
                        currentCode = line.trim(),
                        suggestedCode = "// Remove this import and use service<T>() instead",
                        issueType = IssueType.PACKAGE_CHANGE,
                        severity = IssueSeverity.ERROR
                    )
                )
            }
        }

        if (issues.isEmpty()) {
            return null
        }

        val componentType = determineComponentType(file)
        return FileAuditEntry(
            filePath = file.path,
            componentType = componentType,
            issues = issues
        )
    }

    /**
     * Scan multiple files
     */
    fun scanFiles(files: List<File>): List<FileAuditEntry> {
        return files.mapNotNull { scanFile(it) }
    }

    /**
     * Scan a directory recursively
     */
    fun scanDirectory(directory: File): List<FileAuditEntry> {
        if (!directory.exists() || !directory.isDirectory) {
            return emptyList()
        }

        val kotlinFiles = mutableListOf<File>()
        collectKotlinFiles(directory, kotlinFiles)
        return scanFiles(kotlinFiles)
    }

    private fun collectKotlinFiles(directory: File, result: MutableList<File>) {
        directory.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> collectKotlinFiles(file, result)
                file.name.endsWith(".kt") -> result.add(file)
            }
        }
    }

    private fun determineComponentType(file: File): ComponentType {
        val path = file.path.lowercase()
        return when {
            path.contains("/service/") -> ComponentType.SERVICE
            path.contains("/action/") -> ComponentType.ACTION
            path.contains("/ui/") -> ComponentType.UI
            path.contains("/test/") -> ComponentType.TEST
            else -> ComponentType.UNKNOWN
        }
    }
}
