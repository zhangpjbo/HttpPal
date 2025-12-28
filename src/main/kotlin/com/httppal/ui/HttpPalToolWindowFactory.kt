package com.httppal.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for creating HttpPal tool window with proper lifecycle management
 * Implements requirement 3.1: Display ToolWindow with request configuration interface
 */
class HttpPalToolWindowFactory : ToolWindowFactory {
    
    companion object {
        // Store tool window instances for lifecycle management
        private val toolWindowInstances = mutableMapOf<Project, HttpPalToolWindow>()
    }
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Create HttpPal tool window instance
        val httpPalToolWindow = HttpPalToolWindow(project)
        
        // Store instance for lifecycle management
        toolWindowInstances[project] = httpPalToolWindow
        
        // Create content with proper title and configuration
        val content = ContentFactory.getInstance().createContent(
            httpPalToolWindow.getContent(),
            "", // Empty title as the tool window itself has the title
            false // Not closeable
        )
        
        // Configure content properties
        content.isCloseable = false
        content.isPinnable = true
        
        // Add content to tool window
        toolWindow.contentManager.addContent(content)
        
        // Configure tool window properties
        toolWindow.setAvailable(true, null)
        toolWindow.setToHideOnEmptyContent(false)
        
        // Set up disposal handling for proper cleanup
        toolWindow.contentManager.addContentManagerListener(object : com.intellij.ui.content.ContentManagerListener {
            override fun contentRemoved(event: com.intellij.ui.content.ContentManagerEvent) {
                // Clean up when content is removed
                toolWindowInstances.remove(project)
            }
        })
    }
    
    /**
     * Get the HttpPal tool window instance for a project
     * Used for external interaction with the tool window
     */
    fun getToolWindowInstance(project: Project): HttpPalToolWindow? {
        return toolWindowInstances[project]
    }
    
    override fun shouldBeAvailable(project: Project): Boolean {
        // Tool window should be available for all projects
        return true
    }
}