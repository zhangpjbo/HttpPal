package com.httppal.util

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe debouncer utility for delaying execution of actions.
 * 
 * This class provides a mechanism to delay the execution of an action until
 * a specified delay period has passed without any new invocations. This is
 * particularly useful for handling rapid file changes or user input events.
 * 
 * Example usage:
 * ```
 * val debouncer = Debouncer(delayMs = 500)
 * 
 * // Multiple rapid calls
 * debouncer.debounce { processFileChange() }
 * debouncer.debounce { processFileChange() }
 * debouncer.debounce { processFileChange() }
 * 
 * // Only the last call will execute after 500ms
 * ```
 * 
 * @property delayMs The delay in milliseconds before executing the action
 */
class Debouncer(private val delayMs: Long) {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val debounceJob = AtomicReference<Job?>(null)
    
    /**
     * Debounce an action. If called multiple times within the delay period,
     * only the last action will be executed after the delay.
     * 
     * This method is thread-safe and can be called from multiple threads.
     * 
     * @param action The action to execute after the delay
     */
    fun debounce(action: () -> Unit) {
        // Cancel the previous job if it exists
        debounceJob.getAndSet(null)?.cancel()
        
        // Create a new job
        val newJob = scope.launch {
            delay(delayMs)
            action()
        }
        
        // Store the new job
        debounceJob.set(newJob)
    }
    
    /**
     * Cancel any pending debounced action.
     * 
     * This method is thread-safe and can be called from multiple threads.
     */
    fun cancel() {
        debounceJob.getAndSet(null)?.cancel()
    }
    
    /**
     * Reset the debouncer by canceling any pending action.
     * This is an alias for [cancel] for better semantic clarity.
     */
    fun reset() {
        cancel()
    }
    
    /**
     * Check if there is a pending debounced action.
     * 
     * @return true if there is a pending action, false otherwise
     */
    fun isPending(): Boolean {
        return debounceJob.get()?.isActive == true
    }
    
    /**
     * Shutdown the debouncer and cancel all pending actions.
     * After calling this method, the debouncer should not be used anymore.
     */
    fun shutdown() {
        cancel()
        scope.cancel()
    }
}
