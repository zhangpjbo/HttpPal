package com.httppal.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.icons.AllIcons

/**
 * Action to open the HttpPal tool window
 * Provides quick access to the main plugin interface
 */
class OpenHttpPalToolWindowAction : AnAction(
    "Open HttpPal",
    "Open the HttpPal tool window",
    AllIcons.Toolwindows.ToolWindowRun
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("HttpPal")
        
        toolWindow?.let {
            it.show()
            it.activate(null)
        }
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
