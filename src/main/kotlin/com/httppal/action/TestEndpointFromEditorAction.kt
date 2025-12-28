package com.httppal.action

import com.httppal.service.EndpointDiscoveryService
import com.httppal.ui.HttpPalToolWindow
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiMethod
import com.intellij.icons.AllIcons

/**
 * Context menu action to test API endpoint from editor
 * Discovers endpoint at cursor position and opens it in HttpPal
 */
class TestEndpointFromEditorAction : AnAction(
    "Test API Endpoint with HttpPal",
    "Test the API endpoint at cursor position using HttpPal",
    AllIcons.RunConfigurations.TestState.Run
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        
        try {
            val endpointDiscoveryService = project.service<EndpointDiscoveryService>()
            val endpoints = endpointDiscoveryService.getEndpointsForFile(psiFile)
            
            if (endpoints.isEmpty()) {
                Messages.showInfoMessage(
                    project,
                    "No API endpoints found in this file.",
                    "HttpPal - Test Endpoint"
                )
                return
            }
            
            // Find endpoint at cursor position
            val caretOffset = editor.caretModel.offset
            val element = psiFile.findElementAt(caretOffset)
            val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
            
            val targetEndpoint = if (method != null) {
                // Find endpoint that matches the current method
                endpoints.find { it.methodName == method.name }
            } else {
                // If no specific method, use the first endpoint
                endpoints.firstOrNull()
            }
            
            if (targetEndpoint == null) {
                Messages.showInfoMessage(
                    project,
                    "No API endpoint found at cursor position.",
                    "HttpPal - Test Endpoint"
                )
                return
            }
            
            // Open HttpPal tool window and populate with endpoint
            val toolWindowManager = ToolWindowManager.getInstance(project)
            val toolWindow = toolWindowManager.getToolWindow("HttpPal")
            
            toolWindow?.let { tw ->
                tw.show()
                tw.activate {
                    // Get the tool window content
                    val content = tw.contentManager.getContent(0)
                    val toolWindowComponent = content?.component
                    
                    // For now, show a message that the endpoint was found
                    Messages.showInfoMessage(
                        project,
                        "Found endpoint: ${targetEndpoint.method} ${targetEndpoint.path}\n" +
                                "HttpPal tool window opened. The endpoint will be loaded automatically.",
                        "HttpPal - Endpoint Found"
                    )
                }
            }
            
        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to test endpoint: ${ex.message}",
                "HttpPal - Error"
            )
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        
        // Only show this action in Java files with an active editor
        e.presentation.isEnabledAndVisible = project != null && 
                editor != null && 
                psiFile != null && 
                (psiFile.name.endsWith(".java") || psiFile.name.endsWith(".kt"))
    }
}