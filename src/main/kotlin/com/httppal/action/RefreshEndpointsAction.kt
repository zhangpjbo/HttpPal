package com.httppal.action

import com.httppal.service.EndpointDiscoveryService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.intellij.icons.AllIcons

/**
 * Action to refresh discovered endpoints
 * Triggers a manual refresh of the endpoint discovery service
 */
class RefreshEndpointsAction : AnAction(
    "Refresh Endpoints",
    "Refresh the list of discovered API endpoints",
    AllIcons.Actions.Refresh
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        try {
            val endpointDiscoveryService = project.service<EndpointDiscoveryService>()
            endpointDiscoveryService.refreshEndpoints()
            
            Messages.showInfoMessage(
                project,
                "Endpoints have been refreshed successfully.",
                "HttpPal - Refresh Endpoints"
            )
        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to refresh endpoints: ${ex.message}",
                "HttpPal - Refresh Error"
            )
        }
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}