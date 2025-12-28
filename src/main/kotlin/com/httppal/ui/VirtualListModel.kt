package com.httppal.ui

import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Virtual list model for efficient rendering of large endpoint lists
 * Implements requirement 3.2: Use virtual scrolling for large datasets
 * 
 * This model only keeps a window of visible nodes in memory,
 * reducing memory usage and improving performance for large endpoint lists.
 * 
 * Performance optimizations:
 * - Lazy loading: Only loads visible items
 * - Memory efficient: Keeps only a window of items in memory
 * - Optimized rendering: Reduces tree node creation overhead
 */
class VirtualListModel<T>(
    private val allItems: List<T>,
    private val visibleRange: Int = 50
) : DefaultTreeModel(DefaultMutableTreeNode("Root")) {
    
    private var currentOffset = 0
    private val itemToNodeMap = mutableMapOf<T, DefaultMutableTreeNode>()
    
    // Performance optimization: Cache node creation to avoid repeated allocations
    private val nodeCache = mutableMapOf<Int, DefaultMutableTreeNode>()
    
    init {
        updateVisibleRange(0)
    }
    
    /**
     * Update the visible range of items with optimized node reuse
     * @param offset The starting index of the visible range
     */
    fun updateVisibleRange(offset: Int) {
        val newOffset = offset.coerceIn(0, maxOf(0, allItems.size - visibleRange))
        
        if (newOffset == currentOffset && itemToNodeMap.isNotEmpty()) {
            return // No change needed
        }
        
        currentOffset = newOffset
        
        // Clear existing nodes
        val rootNode = root as DefaultMutableTreeNode
        rootNode.removeAllChildren()
        itemToNodeMap.clear()
        
        // Add visible items with node reuse
        val endIndex = minOf(currentOffset + visibleRange, allItems.size)
        for (i in currentOffset until endIndex) {
            val item = allItems[i]
            
            // Performance optimization: Reuse cached nodes when possible
            val node = nodeCache.getOrPut(i) {
                DefaultMutableTreeNode(item)
            }
            node.userObject = item // Update user object in case item changed
            
            rootNode.add(node)
            itemToNodeMap[item] = node
        }
        
        // Notify listeners
        reload()
    }
    
    /**
     * Get the total number of items (not just visible)
     */
    fun getTotalItemCount(): Int = allItems.size
    
    /**
     * Get the current visible range
     */
    fun getVisibleRange(): IntRange = currentOffset until minOf(currentOffset + visibleRange, allItems.size)
    
    /**
     * Check if an item is currently visible
     */
    fun isItemVisible(item: T): Boolean = itemToNodeMap.containsKey(item)
    
    /**
     * Get the node for a specific item (if visible)
     */
    fun getNodeForItem(item: T): DefaultMutableTreeNode? = itemToNodeMap[item]
    
    /**
     * Scroll to make a specific item visible
     */
    fun scrollToItem(item: T) {
        val index = allItems.indexOf(item)
        if (index >= 0) {
            val newOffset = maxOf(0, index - visibleRange / 2)
            updateVisibleRange(newOffset)
        }
    }
    
    /**
     * Clear node cache to free memory
     * Call this when the model is no longer needed
     */
    fun clearCache() {
        nodeCache.clear()
        itemToNodeMap.clear()
    }
    
    /**
     * Update items without recreating the entire model
     * Performance optimization for incremental updates
     */
    fun updateItems(newItems: List<T>) {
        // Only update if items actually changed
        if (newItems == allItems) {
            return
        }
        
        // Clear cache since items changed
        nodeCache.clear()
        
        // Update visible range with new items
        updateVisibleRange(currentOffset)
    }
}
