package com.httppal.service

import com.httppal.model.RequestHistoryEntry
import com.httppal.model.FavoriteRequest
import com.httppal.service.listener.HistoryEventListener
import java.time.Instant
import java.time.LocalDate

/**
 * Service for managing request history
 * Provides advanced functionality for searching, filtering, and managing request history
 */
interface HistoryService {
    
    /**
     * Get all history entries
     */
    fun getAllHistory(): List<RequestHistoryEntry>
    
    /**
     * Get history entries with pagination
     */
    fun getHistoryPage(page: Int, pageSize: Int): HistoryPage
    
    /**
     * Add entry to history
     */
    fun addToHistory(entry: RequestHistoryEntry)
    
    /**
     * Remove specific entry from history
     */
    fun removeFromHistory(entryId: String): Boolean
    
    /**
     * Clear all history (but preserve favorites)
     */
    fun clearHistory()
    
    /**
     * Clear history older than specified days
     */
    fun clearHistoryOlderThan(days: Int): Int
    
    /**
     * Search history by query
     */
    fun searchHistory(query: String): List<RequestHistoryEntry>
    
    /**
     * Search history with filters
     */
    fun searchHistoryWithFilters(filters: HistoryFilters): List<RequestHistoryEntry>
    
    /**
     * Get history entries by date range
     */
    fun getHistoryByDateRange(startDate: LocalDate, endDate: LocalDate): List<RequestHistoryEntry>
    
    /**
     * Get history entries by HTTP method
     */
    fun getHistoryByMethod(method: String): List<RequestHistoryEntry>
    
    /**
     * Get history entries by status code range
     */
    fun getHistoryByStatusCode(minStatus: Int, maxStatus: Int): List<RequestHistoryEntry>
    
    /**
     * Get successful requests only
     */
    fun getSuccessfulRequests(): List<RequestHistoryEntry>
    
    /**
     * Get failed requests only
     */
    fun getFailedRequests(): List<RequestHistoryEntry>
    
    /**
     * Get history entries by environment
     */
    fun getHistoryByEnvironment(environment: String): List<RequestHistoryEntry>
    
    /**
     * Get recently executed requests
     */
    fun getRecentHistory(limit: Int = 20): List<RequestHistoryEntry>
    
    /**
     * Get frequently executed requests (by URL pattern)
     */
    fun getFrequentRequests(limit: Int = 10): List<FrequentRequest>
    
    /**
     * Get history statistics
     */
    fun getHistoryStatistics(): HistoryStatistics
    
    /**
     * Get daily statistics for a date range
     */
    fun getDailyStatistics(startDate: LocalDate, endDate: LocalDate): List<DailyStatistics>
    
    /**
     * Check if entry exists in favorites
     */
    fun isEntryInFavorites(entryId: String): Boolean
    
    /**
     * Get similar requests (same URL pattern)
     */
    fun getSimilarRequests(entry: RequestHistoryEntry, limit: Int = 5): List<RequestHistoryEntry>
    
    /**
     * Export history to JSON string
     */
    fun exportHistory(): String
    
    /**
     * Export history with date range
     */
    fun exportHistoryByDateRange(startDate: LocalDate, endDate: LocalDate): String
    
    /**
     * Get history entry by ID
     */
    fun getHistoryEntryById(entryId: String): RequestHistoryEntry?
    
    /**
     * Cleanup history based on settings (remove old entries, keep favorites)
     */
    fun performHistoryCleanup(): HistoryCleanupResult
    
    /**
     * Get duplicate requests (same method and URL)
     */
    fun getDuplicateRequests(): Map<String, List<RequestHistoryEntry>>
    
    /**
     * Add event listener for history changes
     * @param listener The listener to add
     */
    fun addEventListener(listener: HistoryEventListener)
    
    /**
     * Remove event listener
     * @param listener The listener to remove
     */
    fun removeEventListener(listener: HistoryEventListener)
}

/**
 * Paginated history result
 */
data class HistoryPage(
    val entries: List<RequestHistoryEntry>,
    val page: Int,
    val pageSize: Int,
    val totalEntries: Int,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

/**
 * Filters for history search
 */
data class HistoryFilters(
    val query: String? = null,
    val methods: List<String> = emptyList(),
    val statusCodes: List<Int> = emptyList(),
    val environments: List<String> = emptyList(),
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val successfulOnly: Boolean = false,
    val failedOnly: Boolean = false,
    val minResponseTime: Long? = null, // milliseconds
    val maxResponseTime: Long? = null  // milliseconds
)

/**
 * Frequent request pattern
 */
data class FrequentRequest(
    val urlPattern: String,
    val method: String,
    val count: Int,
    val lastExecuted: Instant,
    val averageResponseTime: Long?, // milliseconds
    val successRate: Double // 0.0 to 1.0
)

/**
 * Overall history statistics
 */
data class HistoryStatistics(
    val totalRequests: Int,
    val successfulRequests: Int,
    val failedRequests: Int,
    val averageResponseTime: Long?, // milliseconds
    val requestsByMethod: Map<String, Int>,
    val requestsByStatusCode: Map<Int, Int>,
    val requestsByEnvironment: Map<String, Int>,
    val oldestEntry: Instant?,
    val newestEntry: Instant?,
    val totalResponseSize: Long, // bytes
    val averageResponseSize: Long // bytes
)

/**
 * Daily statistics
 */
data class DailyStatistics(
    val date: LocalDate,
    val totalRequests: Int,
    val successfulRequests: Int,
    val failedRequests: Int,
    val averageResponseTime: Long?, // milliseconds
    val uniqueEndpoints: Int
)

/**
 * Result of history cleanup operation
 */
data class HistoryCleanupResult(
    val removedEntries: Int,
    val preservedFavorites: Int,
    val totalEntriesAfterCleanup: Int
)