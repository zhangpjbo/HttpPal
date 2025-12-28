package com.httppal.service.listener

import com.httppal.model.RequestHistoryEntry

/**
 * Listener interface for history change events
 * Allows UI components to react to history modifications in real-time
 */
interface HistoryEventListener {
    
    /**
     * Called when a new history entry is added
     * @param entry The history entry that was added
     */
    fun onHistoryAdded(entry: RequestHistoryEntry)
    
    /**
     * Called when a history entry is removed
     * @param entryId The ID of the history entry that was removed
     */
    fun onHistoryRemoved(entryId: String)
    
    /**
     * Called when all history entries are cleared
     */
    fun onHistoryCleared()
}
