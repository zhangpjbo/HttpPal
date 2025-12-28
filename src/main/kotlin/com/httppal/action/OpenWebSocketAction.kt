package com.httppal.action

import com.httppal.ui.HttpPalToolWindow
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.icons.AllIcons

/**
 * Action to open WebSocket tab in HttpPal tool window
 * Provides quick access to WebSocket functionality
 */
class OpenWebSocketAction : AnAction(
    "Open WebSocket",
    "Open WebSocket connection interface",
    AllIcons.Debugger.ThreadStates.Socket
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("HttpPal")
        
        toolWindow?.let { tw ->
            tw.show()
            tw.activate {
                // Get the tool window content and navigate to WebSocket tab
                val content = tw.contentManager.getContent(0)
                val toolWindowComponent = content?.component
                
                // If we can access the HttpPalToolWindow instance, navigate to WebSocket tab
                // This would require storing a reference to the tool window instance
                // For now, just activate the tool window
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}