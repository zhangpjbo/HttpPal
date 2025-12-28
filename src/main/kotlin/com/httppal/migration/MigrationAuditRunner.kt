package com.httppal.migration

import java.io.File

/**
 * Command-line runner for the migration audit tool
 */
object MigrationAuditRunner {

    @JvmStatic
    fun main(args: Array<String>) {
        val projectRoot = if (args.isNotEmpty()) {
            File(args[0])
        } else {
            File(System.getProperty("user.dir"))
        }

        val tool = MigrationAuditTool()
        val report = tool.auditProject(projectRoot)
        val reportText = tool.generateReport(report)

        // Optionally save to file
        val reportFile = File(projectRoot, "migration-audit-report.txt")
        reportFile.writeText(reportText)
    }
}
