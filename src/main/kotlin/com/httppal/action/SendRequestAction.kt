package com.httppal.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.icons.AllIcons

/**
 * Action for sending HTTP requests
 * Opens HttpPal tool window and focuses on request execution
 */
class SendRequestAction : AnAction(
    "Send HTTP Request",
    "Open HttpPal and send HTTP request",
    AllIcons.Actions.Execute
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("HttpPal")
        
        toolWindow?.let { tw ->
            tw.show()
            tw.activate {
                // Focus on the request tab and show info about sending requests
                Messages.showInfoMessage(
                    project,
                    "HttpPal is now open. Configure your request and click 'Send' to execute it.\n" +
                            "You can also discover endpoints from your code using the endpoint tree.",
                    "HttpPal - Send Request"
                )
            }
        } ?: run {
            Messages.showErrorDialog(
                project,
                "HttpPal tool window is not available. Please ensure the plugin is properly installed.",
                "HttpPal - Error"
            )
        }
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}