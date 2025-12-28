package com.httppal.service.listener

import com.httppal.service.EndpointDiscoveryService
import com.httppal.util.Debouncer
import com.httppal.util.LoggingUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Enhanced file change listener that provides intelligent file change handling
 * with debouncing and incremental scanning support.
 * 
 * This listener monitors file changes in the project and automatically updates
 * the endpoint list when Java or Kotlin files are modified. It uses a debouncing
 * mechanism to batch multiple rapid changes together, improving performance.
 * 
 * Features:
 * - File type filtering (only Java/Kotlin files)
 * - Debouncing to batch rapid changes
 * - Incremental scanning (only changed files)
 * - Background thread processing
 * - Thread-safe file tracking
 * 
 * @property project The IntelliJ project instance
 * @property discoveryService The endpoint discovery service for scanning files
 * @property debounceDelayMs The debounce delay in milliseconds (default: 500ms)
 */
class EnhancedFileChangeListener(
    private val project: Project,
    private val discoveryService: EndpointDiscoveryService,
    debounceDelayMs: Long = 500
) : BulkFileListener {
    
    private val debouncer = Debouncer(delayMs = debounceDelayMs)
    private val changedFiles = ConcurrentHashMap.newKeySet<VirtualFile>()
    
    /**
     * Supported file extensions for endpoint discovery
     */
    private val supportedExtensions = setOf("java", "kt")
    
    /**
     * Called after a batch of file events have been processed.
     * This method filters relevant files and schedules them for processing.
     * 
     * @param events List of file events that occurred
     */
    override fun after(events: List<VFileEvent>) {
        // Filter events to only include Java/Kotlin files
        val relevantFiles = events
            .mapNotNull { it.file }
            .filter { isRelevantFile(it) }
        
        if (relevantFiles.isEmpty()) {
            return
        }
        
        LoggingUtils.logWithContext(
            LoggingUtils.LogLevel.DEBUG,
            "File change events detected",
            mapOf<String, Any>(
                "totalEvents" to events.size,
                "relevantFiles" to relevantFiles.size
            )
        )
        
        // Add files to the pending set
        changedFiles.addAll(relevantFiles)
        
        // Use debouncer to batch process changes
        debouncer.debounce {
            processFileChanges()
        }
    }
    
    /**
     * Check if a file is relevant for endpoint discovery.
     * Only Java and Kotlin files are considered relevant.
     * 
     * @param file The virtual file to check
     * @return true if the file should be processed, false otherwise
     */
    private fun isRelevantFile(file: VirtualFile): Boolean {
        return file.extension in supportedExtensions && file.isValid
    }
    
    /**
     * Process all pending file changes.
     * This method runs in a background thread and performs incremental scanning
     * of changed files.
     */
    private fun processFileChanges() {
        // Get snapshot of files to process and clear the set
        val filesToProcess = changedFiles.toList()
        changedFiles.clear()
        
        if (filesToProcess.isEmpty()) {
            return
        }
        
        LoggingUtils.logWithContext(
            LoggingUtils.LogLevel.DEBUG,
            "Processing file changes",
            mapOf<String, Any>("fileCount" to filesToProcess.size)
        )
        
        // Process files in background thread
        ApplicationManager.getApplication().executeOnPooledThread {
            ReadAction.run<RuntimeException> {
                processFilesInReadAction(filesToProcess)
            }
        }
    }
    
    /**
     * Process files within a read action.
     * This method performs the actual incremental scanning of changed files.
     * 
     * @param files List of files to process
     */
    private fun processFilesInReadAction(files: List<VirtualFile>) {
        val psiManager = PsiManager.getInstance(project)
        var processedCount = 0
        var errorCount = 0
        
        files.forEach { virtualFile ->
            try {
                if (!virtualFile.isValid) {
                    LoggingUtils.logWithContext(
                        LoggingUtils.LogLevel.DEBUG,
                        "Skipping invalid file",
                        mapOf<String, Any>("file" to virtualFile.name)
                    )
                    return@forEach
                }
                
                val psiFile = psiManager.findFile(virtualFile)
                if (psiFile != null) {
                    // Perform incremental scan on this file
                    discoveryService.scanFile(psiFile)
                    processedCount++
                    
                    LoggingUtils.logWithContext(
                        LoggingUtils.LogLevel.DEBUG,
                        "Scanned file for endpoint changes",
                        mapOf<String, Any>("file" to virtualFile.name)
                    )
                } else {
                    LoggingUtils.logWithContext(
                        LoggingUtils.LogLevel.DEBUG,
                        "Could not find PSI file",
                        mapOf<String, Any>("file" to virtualFile.name)
                    )
                }
            } catch (e: Exception) {
                errorCount++
                LoggingUtils.logWithContext(
                    LoggingUtils.LogLevel.WARN,
                    "Error processing file change",
                    mapOf<String, Any>(
                        "file" to virtualFile.name,
                        "error" to (e.message ?: "Unknown error")
                    ),
                    e
                )
            }
        }
        
        LoggingUtils.logWithContext(
            LoggingUtils.LogLevel.INFO,
            "File change processing completed",
            mapOf<String, Any>(
                "totalFiles" to files.size,
                "processed" to processedCount,
                "errors" to errorCount
            )
        )
    }
    
    /**
     * Cancel any pending file processing.
     * This should be called when the listener is being disposed.
     */
    fun cancel() {
        debouncer.cancel()
        changedFiles.clear()
    }
    
    /**
     * Shutdown the listener and clean up resources.
     * This should be called when the listener is no longer needed.
     */
    fun shutdown() {
        debouncer.shutdown()
        changedFiles.clear()
    }
}
