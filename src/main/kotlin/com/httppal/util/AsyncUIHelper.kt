package com.httppal.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import javax.swing.SwingUtilities

/**
 * Helper class for async UI operations
 * Implements requirement 3.1, 3.4: All time-consuming operations run in background threads
 * and UI updates use invokeLater
 */
object AsyncUIHelper {
    
    /**
     * Execute a background task with progress indicator
     * Implements requirement 3.1: Time-consuming operations in background threads
     * Implements requirement 3.4: Add progress indicators
     */
    fun <T> executeWithProgress(
        project: Project,
        title: String,
        canBeCancelled: Boolean = true,
        backgroundTask: (ProgressIndicator) -> T,
        onSuccess: (T) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, canBeCancelled) {
            private var result: T? = null
            private var error: Throwable? = null
            
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.isIndeterminate = false
                    indicator.fraction = 0.0
                    result = backgroundTask(indicator)
                } catch (e: Throwable) {
                    error = e
                    LoggingUtils.logError("Background task failed: $title", e)
                }
            }
            
            override fun onSuccess() {
                result?.let { onSuccess(it) }
                error?.let { onError(it) }
            }
            
            override fun onThrowable(error: Throwable) {
                onError(error)
                LoggingUtils.logError("Background task error: $title", error)
            }
        })
    }
    
    /**
     * Execute a background task without progress indicator
     * Implements requirement 3.1: Time-consuming operations in background threads
     */
    fun <T> executeInBackground(
        backgroundTask: () -> T,
        onComplete: (T) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val result = backgroundTask()
                invokeLater {
                    onComplete(result)
                }
            } catch (e: Throwable) {
                LoggingUtils.logError("Background task failed", e)
                invokeLater {
                    onError(e)
                }
            }
        }
    }
    
    /**
     * Execute a coroutine-based background task
     * Implements requirement 3.1: Time-consuming operations in background threads
     */
    suspend fun <T> executeAsync(
        backgroundTask: suspend () -> T
    ): T = withContext(Dispatchers.IO) {
        backgroundTask()
    }
    
    /**
     * Update UI on EDT thread
     * Implements requirement 3.1: Use invokeLater to update UI
     */
    fun invokeLater(uiUpdate: () -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            try {
                uiUpdate()
            } catch (e: Throwable) {
                LoggingUtils.logError("UI update failed", e)
            }
        }
    }
    
    /**
     * Update UI on EDT thread and wait for completion
     * Implements requirement 3.1: Use invokeLater to update UI
     */
    fun invokeAndWait(uiUpdate: () -> Unit) {
        ApplicationManager.getApplication().invokeAndWait {
            try {
                uiUpdate()
            } catch (e: Throwable) {
                LoggingUtils.logError("UI update failed", e)
            }
        }
    }
    
    /**
     * Check if currently on EDT thread
     */
    fun isEDT(): Boolean {
        return SwingUtilities.isEventDispatchThread()
    }
    
    /**
     * Execute on EDT if not already on it, otherwise execute immediately
     */
    fun ensureEDT(action: () -> Unit) {
        if (isEDT()) {
            action()
        } else {
            invokeLater(action)
        }
    }
    
    /**
     * Show progress indicator in UI component
     * Implements requirement 3.4: Add progress indicators
     */
    fun showProgressIndicator(
        component: javax.swing.JComponent,
        message: String = "Loading..."
    ): ProgressIndicatorHandle {
        val progressPanel = javax.swing.JPanel(java.awt.BorderLayout())
        progressPanel.border = com.intellij.util.ui.JBUI.Borders.empty(20)
        
        val label = com.intellij.ui.components.JBLabel(message)
        label.font = label.font.deriveFont(java.awt.Font.BOLD, 12f)
        label.alignmentX = java.awt.Component.CENTER_ALIGNMENT
        progressPanel.add(label, java.awt.BorderLayout.NORTH)
        
        val progressBar = javax.swing.JProgressBar()
        progressBar.isIndeterminate = true
        progressBar.maximumSize = java.awt.Dimension(200, 20)
        progressBar.alignmentX = java.awt.Component.CENTER_ALIGNMENT
        progressPanel.add(progressBar, java.awt.BorderLayout.CENTER)
        
        // Store original component state
        val originalLayout = component.layout
        val originalComponents = component.components.toList()
        
        // Replace with progress panel
        ensureEDT {
            component.removeAll()
            component.layout = java.awt.BorderLayout()
            component.add(progressPanel, java.awt.BorderLayout.CENTER)
            component.revalidate()
            component.repaint()
        }
        
        return ProgressIndicatorHandle(component, originalLayout, originalComponents)
    }
    
    /**
     * Handle for managing progress indicator lifecycle
     */
    class ProgressIndicatorHandle(
        private val component: javax.swing.JComponent,
        private val originalLayout: java.awt.LayoutManager?,
        private val originalComponents: List<java.awt.Component>
    ) {
        /**
         * Hide progress indicator and restore original content
         */
        fun hide() {
            ensureEDT {
                component.removeAll()
                component.layout = originalLayout
                originalComponents.forEach { component.add(it) }
                component.revalidate()
                component.repaint()
            }
        }
    }
    
    /**
     * Execute multiple tasks in parallel and collect results
     * Implements requirement 3.1: Efficient background processing
     */
    suspend fun <T> executeParallel(
        tasks: List<suspend () -> T>
    ): List<T> = coroutineScope {
        tasks.map { task ->
            async(Dispatchers.IO) {
                task()
            }
        }.awaitAll()
    }
    
    /**
     * Execute task with timeout
     * Implements requirement 3.4: Handle long-running operations
     */
    suspend fun <T> executeWithTimeout(
        timeoutMillis: Long,
        task: suspend () -> T
    ): T? = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutMillis) {
                task()
            }
        } catch (e: TimeoutCancellationException) {
            LoggingUtils.logWarning("Task timed out after ${timeoutMillis}ms")
            null
        }
    }
}
