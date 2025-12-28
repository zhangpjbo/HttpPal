package com.httppal.action

import com.httppal.model.RequestConfig
import com.httppal.service.HttpPalService
import com.httppal.ui.JMeterExportDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.icons.AllIcons

/**
 * Action for exporting requests to JMeter
 * Opens JMeter export dialog with current request or favorites
 */
class ExportToJMeterAction : AnAction(
    "Export to JMeter",
    "Export HTTP requests to Apache JMeter .jmx file",
    AllIcons.ToolbarDecorator.Export
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val httpPalService = service<HttpPalService>()
        
        // Try to get current request from tool window
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("HttpPal")
        
        var currentRequest: RequestConfig? = null
        
        // Try to get current request from the tool window content
        toolWindow?.let { tw ->
            // For now, we'll show a dialog to select from favorites or create a new request
            // In a full implementation, we would get the current request from the UI
        }
        
        // Get available requests (favorites for now, could be extended)
        val favorites = httpPalService.getFavorites()
        val availableRequests = favorites.map { it.request }
        
        if (availableRequests.isEmpty()) {
            // Show dialog to create a request first
            Messages.showInfoMessage(
                project,
                "No requests available for export.\n\n" +
                        "Please create and save some requests in HttpPal first, then you can export them to JMeter.\n\n" +
                        "To create requests:\n" +
                        "1. Open HttpPal tool window\n" +
                        "2. Configure your HTTP request\n" +
                        "3. Add it to favorites\n" +
                        "4. Use this export action",
                "HttpPal - No Requests Available"
            )
            
            // Open HttpPal tool window
            toolWindow?.show()
            return
        }
        
        // Get current environment
        val currentEnvironment = httpPalService.getCurrentEnvironment()
        
        // Show export dialog with available requests
        val dialog = JMeterExportDialog(project, availableRequests, currentEnvironment)
        dialog.show()
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
        
        // Update description based on availability
        if (project != null) {
            val httpPalService = service<HttpPalService>()
            val favoritesCount = httpPalService.getFavorites().size
            
            e.presentation.description = if (favoritesCount > 0) {
                "Export $favoritesCount saved request(s) to Apache JMeter .jmx file"
            } else {
                "Export HTTP requests to Apache JMeter .jmx file (no requests available)"
            }
        }
    }
}