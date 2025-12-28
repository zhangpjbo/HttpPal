package com.httppal.action

import com.httppal.service.EndpointDiscoveryService
import com.httppal.util.HttpPalBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiMethod
import com.intellij.icons.AllIcons

/**
 * Action for testing API endpoints from context menu
 * Enhanced version that discovers endpoints and opens HttpPal
 */
class TestEndpointAction : AnAction(
    "Test with HttpPal",
    "Test API endpoint using HttpPal",
    AllIcons.RunConfigurations.TestState.Run
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        
        if (editor != null && psiFile != null) {
            // Context menu from editor - try to find endpoint at cursor
            testEndpointFromEditor(e, editor, psiFile)
        } else {
            // General action - just open HttpPal
            openHttpPalForTesting(e)
        }
    }
    
    private fun testEndpointFromEditor(e: AnActionEvent, editor: com.intellij.openapi.editor.Editor, psiFile: PsiFile) {
        val project = e.project ?: return
        
        try {
            val endpointDiscoveryService = project.service<EndpointDiscoveryService>()
            val endpoints = endpointDiscoveryService.getEndpointsForFile(psiFile)
            
            if (endpoints.isEmpty()) {
                Messages.showInfoMessage(
                    project,
                    HttpPalBundle.message("dialog.test.endpoint.no.endpoints"),
                    HttpPalBundle.message("dialog.test.endpoint.title")
                )
                return
            }
            
            // Find endpoint at cursor position
            val caretOffset = editor.caretModel.offset
            val element = psiFile.findElementAt(caretOffset)
            val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
            
            val targetEndpoint = if (method != null) {
                endpoints.find { it.methodName == method.name }
            } else {
                endpoints.firstOrNull()
            }
            
            if (targetEndpoint == null) {
                val endpointList = endpoints.joinToString("\n") { "• ${it.method} ${it.path} (${it.methodName})" }
                Messages.showInfoMessage(
                    project,
                    HttpPalBundle.message("dialog.test.endpoint.no.cursor", endpointList),
                    HttpPalBundle.message("dialog.test.endpoint.title")
                )
                return
            }
            
            // Open HttpPal and show endpoint info
            val toolWindowManager = ToolWindowManager.getInstance(project)
            val toolWindow = toolWindowManager.getToolWindow("HttpPal")
            
            toolWindow?.let { tw ->
                tw.show()
                tw.activate {
                    Messages.showInfoMessage(
                        project,
                        HttpPalBundle.message("dialog.test.endpoint.found", 
                            targetEndpoint.method, 
                            targetEndpoint.path,
                            targetEndpoint.className,
                            targetEndpoint.methodName),
                        HttpPalBundle.message("dialog.test.endpoint.title")
                    )
                }
            }
            
        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                HttpPalBundle.message("dialog.test.endpoint.error.message", ex.message ?: "Unknown error"),
                HttpPalBundle.message("dialog.test.endpoint.error.title")
            )
        }
    }
    
    private fun openHttpPalForTesting(e: AnActionEvent) {
        val project = e.project ?: return
        
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("HttpPal")
        
        toolWindow?.let { tw ->
            tw.show()
            tw.activate {
                Messages.showInfoMessage(
                    project,
                    HttpPalBundle.message("dialog.test.endpoint.info"),
                    HttpPalBundle.message("dialog.test.endpoint.title")
                )
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}