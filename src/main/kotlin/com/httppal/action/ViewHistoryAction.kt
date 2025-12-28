package com.httppal.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.icons.AllIcons

/**
 * Action to open the History & Favorites tab in HttpPal
 * Provides quick access to request history
 */
class ViewHistoryAction : AnAction(
    "View Request History",
    "View HTTP request history in HttpPal",
    AllIcons.Vcs.History
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("HttpPal")
        
        toolWindow?.let { tw ->
            tw.show()
            tw.activate {
                // Navigate to History & Favorites tab (index 2)
                // This would require access to the HttpPalToolWindow instance
                // For now, just activate the tool window
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}