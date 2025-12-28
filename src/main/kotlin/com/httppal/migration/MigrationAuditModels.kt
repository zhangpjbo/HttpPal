package com.httppal.migration

/**
 * Data models for migration audit reporting
 */

data class MigrationAuditReport(
    val totalFiles: Int,
    val filesNeedingUpdate: List<FileAuditEntry>,
    val serviceComponents: List<ComponentAuditEntry>,
    val actionComponents: List<ComponentAuditEntry>,
    val uiComponents: List<ComponentAuditEntry>,
    val testComponents: List<ComponentAuditEntry>,
    val configurationIssues: List<ConfigurationIssue>
)

data class FileAuditEntry(
    val filePath: String,
    val componentType: ComponentType,
    val issues: List<ApiIssue>
)

data class ComponentAuditEntry(
    val name: String,
    val filePath: String,
    val currentApi: String,
    val targetApi: String,
    val migrationComplexity: MigrationComplexity
)

data class ApiIssue(
    val lineNumber: Int,
    val currentCode: String,
    val suggestedCode: String,
    val issueType: IssueType,
    val severity: IssueSeverity
)

data class ConfigurationIssue(
    val file: String,
    val element: String,
    val issue: String,
    val suggestion: String
)

enum class ComponentType {
    SERVICE, ACTION, UI, TEST, CONFIGURATION, UNKNOWN
}

enum class MigrationComplexity {
    LOW, MEDIUM, HIGH
}

enum class IssueType {
    DEPRECATED_API,
    PACKAGE_CHANGE,
    SERVICE_REGISTRATION,
    CONTENT_FACTORY,
    TOOL_WINDOW_MANAGER,
    TEST_BASE_CLASS,
    SERVICE_MANAGER_USAGE,
    APPLICATION_MANAGER_USAGE
}

enum class IssueSeverity {
    ERROR, WARNING, INFO
}
