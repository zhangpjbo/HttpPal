package com.httppal.action

import com.httppal.ui.EnvironmentManagementDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.icons.AllIcons

/**
 * Action to open the environment management dialog
 * Provides quick access to environment configuration
 */
class OpenEnvironmentManagerAction : AnAction(
    "Manage Environments",
    "Open environment management dialog",
    AllIcons.General.Settings
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val dialog = EnvironmentManagementDialog(project)
        dialog.show()
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}