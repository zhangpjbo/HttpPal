package com.httppal.service

import com.httppal.model.DiscoveredEndpoint

/**
 * Listener interface for endpoint change notifications
 * Implementations can register with EndpointDiscoveryService to receive updates
 * when endpoints are added, modified, or removed
 */
interface EndpointChangeListener {
    
    /**
     * Called when endpoints have changed
     * 
     * @param notification Details about what endpoints changed
     */
    fun onEndpointsChanged(notification: EndpointChangeNotification)
}

/**
 * Notification containing details about endpoint changes
 */
data class EndpointChangeNotification(
    /**
     * Endpoints that were newly discovered
     */
    val addedEndpoints: List<DiscoveredEndpoint>,
    
    /**
     * Endpoints that were modified (path, method, or parameters changed)
     */
    val modifiedEndpoints: List<DiscoveredEndpoint>,
    
    /**
     * Endpoints that were removed (no longer found in source)
     */
    val removedEndpoints: List<DiscoveredEndpoint>,
    
    /**
     * Timestamp when the change was detected
     */
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Check if there are any changes
     */
    fun hasChanges(): Boolean {
        return addedEndpoints.isNotEmpty() || 
               modifiedEndpoints.isNotEmpty() || 
               removedEndpoints.isNotEmpty()
    }
    
    /**
     * Get total number of changes
     */
    fun totalChanges(): Int {
        return addedEndpoints.size + modifiedEndpoints.size + removedEndpoints.size
    }
}
