package com.httppal.action

import com.httppal.service.EndpointDiscoveryService
import com.httppal.model.DiscoveredEndpoint
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiFile
import com.intellij.icons.AllIcons
import javax.swing.Icon

/**
 * Quick action to test endpoints with a popup selection
 * Shows a list of available endpoints when multiple are found
 */
class QuickTestEndpointAction : AnAction(
    "Quick Test Endpoint",
    "Quick test API endpoint with popup selection",
    AllIcons.Actions.Lightning
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        
        try {
            val endpointDiscoveryService = project.service<EndpointDiscoveryService>()
            val endpoints = endpointDiscoveryService.getEndpointsForFile(psiFile)
            
            when {
                endpoints.isEmpty() -> {
                    HttpPalActionUtils.showHttpPalInfo(
                        project,
                        "Quick Test",
                        "No API endpoints found in this file.\n" +
                                "HttpPal can discover Spring MVC and JAX-RS endpoints."
                    )
                }
                endpoints.size == 1 -> {
                    // Single endpoint - test directly
                    testEndpoint(project, endpoints.first())
                }
                else -> {
                    // Multiple endpoints - show popup selection
                    showEndpointSelectionPopup(e,project, endpoints)
                }
            }
            
        } catch (ex: Exception) {
            HttpPalActionUtils.showHttpPalError(
                project,
                "Quick Test Error",
                "Failed to discover endpoints: ${ex.message}"
            )
        }
    }
    
    private fun showEndpointSelectionPopup(e: AnActionEvent,project: com.intellij.openapi.project.Project, endpoints: List<DiscoveredEndpoint>) {
        val popupStep = object : BaseListPopupStep<DiscoveredEndpoint>(
            "Select Endpoint to Test",
            endpoints
        ) {
            override fun getTextFor(value: DiscoveredEndpoint): String {
                return "${value.method} ${value.path} (${value.methodName})"
            }
            
            override fun getIconFor(value: DiscoveredEndpoint): Icon? {
                return when (value.method.name) {
                    "GET" -> AllIcons.Nodes.Method
                    "POST" -> AllIcons.Nodes.NewParameter
                    "PUT" -> AllIcons.Actions.Edit
                    "DELETE" -> AllIcons.Actions.Cancel
                    "PATCH" -> AllIcons.Actions.Diff
                    else -> AllIcons.Nodes.Method
                }
            }
            
            override fun onChosen(selectedValue: DiscoveredEndpoint, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    testEndpoint(project, selectedValue)
                }
                return PopupStep.FINAL_CHOICE
            }
        }
        
        val popup = JBPopupFactory.getInstance().createListPopup(popupStep)
        popup.showInBestPositionFor(e.getData(CommonDataKeys.EDITOR) ?: return)
    }
    
    private fun testEndpoint(project: com.intellij.openapi.project.Project, endpoint: DiscoveredEndpoint) {
        HttpPalActionUtils.openHttpPalToolWindow(project) {
            HttpPalActionUtils.showHttpPalInfo(
                project,
                "Endpoint Loaded",
                HttpPalActionUtils.formatEndpointInfo(
                    endpoint.method.name,
                    endpoint.path,
                    endpoint.className,
                    endpoint.methodName
                ) + "\n\nThe endpoint has been loaded in HttpPal. Configure any additional parameters and click 'Send' to test it."
            )
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        
        // Only show this action in Java/Kotlin files
        e.presentation.isEnabledAndVisible = project != null && 
                psiFile != null && 
                HttpPalActionUtils.isJavaOrKotlinFile(psiFile.name)
    }
}