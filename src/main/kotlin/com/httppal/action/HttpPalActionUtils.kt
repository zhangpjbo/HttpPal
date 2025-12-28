package com.httppal.action

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.ui.Messages

/**
 * Utility class for HttpPal actions
 * Provides common functionality for action implementations
 */
object HttpPalActionUtils {
    
    /**
     * Get the HttpPal tool window for a project
     */
    fun getHttpPalToolWindow(project: Project): ToolWindow? {
        return ToolWindowManager.getInstance(project).getToolWindow("HttpPal")
    }
    
    /**
     * Open and activate the HttpPal tool window
     */
    fun openHttpPalToolWindow(project: Project, onActivated: (() -> Unit)? = null): Boolean {
        val toolWindow = getHttpPalToolWindow(project)
        
        return if (toolWindow != null) {
            toolWindow.show()
            if (onActivated != null) {
                toolWindow.activate(onActivated)
            } else {
                toolWindow.activate(null)
            }
            true
        } else {
            Messages.showErrorDialog(
                project,
                "HttpPal tool window is not available. Please ensure the plugin is properly installed.",
                "HttpPal - Error"
            )
            false
        }
    }
    
    /**
     * Check if HttpPal tool window is available
     */
    fun isHttpPalAvailable(project: Project): Boolean {
        return getHttpPalToolWindow(project) != null
    }
    
    /**
     * Navigate to a specific tab in HttpPal tool window
     */
    fun navigateToTab(project: Project, tabName: String): Boolean {
        return openHttpPalToolWindow(project) {
            // This would require access to the HttpPalToolWindow instance
            // For now, just show the tool window
        }
    }
    
    /**
     * Show an info message about HttpPal functionality
     */
    fun showHttpPalInfo(project: Project, title: String, message: String) {
        Messages.showInfoMessage(project, message, "HttpPal - $title")
    }
    
    /**
     * Show an error message about HttpPal functionality
     */
    fun showHttpPalError(project: Project, title: String, message: String) {
        Messages.showErrorDialog(project, message, "HttpPal - $title")
    }
    
    /**
     * Check if a file is a Java or Kotlin source file
     */
    fun isJavaOrKotlinFile(fileName: String): Boolean {
        return fileName.endsWith(".java") || fileName.endsWith(".kt")
    }
    
    /**
     * Format endpoint information for display
     */
    fun formatEndpointInfo(method: String, path: String, className: String, methodName: String): String {
        return "Endpoint: $method $path\nClass: $className\nMethod: $methodName"
    }
}