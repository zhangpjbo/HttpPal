package com.httppal.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.icons.AllIcons

/**
 * Action to open HttpPal settings
 * Provides quick access to plugin configuration
 */
class OpenSettingsAction : AnAction(
    "HttpPal Settings",
    "Open HttpPal plugin settings",
    AllIcons.General.Settings
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            "HttpPal"
        )
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}