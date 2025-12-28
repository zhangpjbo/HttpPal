package com.httppal.service.impl

import com.httppal.model.RequestHistoryEntry
import com.httppal.service.*
import com.httppal.service.listener.HistoryEventListener
import com.httppal.settings.HttpPalSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * Implementation of HistoryService
 */
@Service
class HistoryServiceImpl : HistoryService {

    private val logger = Logger.getInstance(HistoryServiceImpl::class.java)

    private val settings: HttpPalSettings
        get() = HttpPalSettings.getInstance()
    
    private val favoritesService: FavoritesService
        get() = FavoritesServiceImpl.getInstance()
    
    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        // 手动注册 JavaTimeModule 以支持 Instant, Duration 等类型
        // 不使用 findAndRegisterModules() 以避免 IntelliJ 插件环境中的类加载器冲突
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
    
    // Event listeners for history changes
    private val eventListeners = mutableListOf<HistoryEventListener>()
    
    override fun getAllHistory(): List<RequestHistoryEntry> {
        val history = settings.getHistory()
        logger.info("Retrieved ${history.size} history entries from settings")
        return history
    }
    
    override fun getHistoryPage(page: Int, pageSize: Int): HistoryPage {
        logger.debug("Getting history page: page=$page, pageSize=$pageSize")
        val allHistory = settings.getHistory()
        val totalEntries = allHistory.size
        val totalPages = ceil(totalEntries.toDouble() / pageSize).toInt()
        val startIndex = (page - 1) * pageSize
        val endIndex = minOf(startIndex + pageSize, totalEntries)
        
        val entries = if (startIndex < totalEntries) {
            allHistory.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
        
        logger.info("Returning history page: page=$page, entries=${entries.size}, totalEntries=$totalEntries, totalPages=$totalPages")
        
        return HistoryPage(
            entries = entries,
            page = page,
            pageSize = pageSize,
            totalEntries = totalEntries,
            totalPages = totalPages,
            hasNext = page < totalPages,
            hasPrevious = page > 1
        )
    }
    
    override fun addToHistory(entry: RequestHistoryEntry) {
        logger.info("HistoryService.addToHistory() called: id=${entry.id}, url=${entry.request.url}, timestamp=${entry.timestamp}")
        
        // Validate entry
        if (entry.id.isBlank()) {
            logger.error("History entry validation failed: ID is blank")
            throw IllegalArgumentException("History entry ID cannot be blank")
        }
        if (entry.request.url.isBlank()) {
            logger.error("History entry validation failed: URL is blank")
            throw IllegalArgumentException("History entry URL cannot be blank")
        }
        
        logger.debug("History entry validation passed")
        
        // Add to settings
        settings.addToHistory(entry)
        
        // Notify event listeners
        logger.debug("Triggering history added event for entry: id=${entry.id}")
        notifyHistoryAdded(entry)
        
        // Get updated count
        val totalCount = settings.getHistory().size
        logger.info("History entry added successfully and event notified: id=${entry.id}, total entries now: $totalCount")
    }
    
    override fun removeFromHistory(entryId: String): Boolean {
        return try {
            logger.info("Removing history entry: id=$entryId")
            settings.removeFromHistory(entryId)
            
            // Notify event listeners
            logger.debug("Triggering history removed event for entry: id=$entryId")
            notifyHistoryRemoved(entryId)
            
            logger.info("History entry removed successfully and event notified: id=$entryId")
            true
        } catch (e: Exception) {
            logger.error("Failed to remove history entry: id=$entryId, error=${e.message}", e)
            false
        }
    }
    
    override fun clearHistory() {
        logger.info("Clearing all history")
        settings.clearHistory()
        
        // Notify event listeners
        logger.debug("Triggering history cleared event")
        notifyHistoryCleared()
        
        logger.info("History cleared successfully and event notified")
    }
    
    override fun clearHistoryOlderThan(days: Int): Int {
        val cutoffDate = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val allHistory = settings.getHistory()
        val toRemove = allHistory.filter { it.timestamp.isBefore(cutoffDate) }
        
        toRemove.forEach { entry ->
            settings.removeFromHistory(entry.id)
        }
        
        return toRemove.size
    }
    
    override fun searchHistory(query: String): List<RequestHistoryEntry> {
        if (query.isBlank()) {
            return getAllHistory()
        }
        
        return settings.searchHistory(query)
    }
    
    override fun searchHistoryWithFilters(filters: HistoryFilters): List<RequestHistoryEntry> {
        var results = getAllHistory()
        
        // Apply query filter
        filters.query?.let { query ->
            if (query.isNotBlank()) {
                results = results.filter { it.matchesSearch(query) }
            }
        }
        
        // Apply method filter
        if (filters.methods.isNotEmpty()) {
            results = results.filter { entry ->
                filters.methods.contains(entry.request.method.name)
            }
        }
        
        // Apply status code filter
        if (filters.statusCodes.isNotEmpty()) {
            results = results.filter { entry ->
                entry.response?.statusCode in filters.statusCodes
            }
        }
        
        // Apply environment filter
        if (filters.environments.isNotEmpty()) {
            results = results.filter { entry ->
                entry.environment in filters.environments
            }
        }
        
        // Apply date range filter
        filters.startDate?.let { startDate ->
            val startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            results = results.filter { it.timestamp.isAfter(startInstant) || it.timestamp == startInstant }
        }
        
        filters.endDate?.let { endDate ->
            val endInstant = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            results = results.filter { it.timestamp.isBefore(endInstant) }
        }
        
        // Apply success/failure filters
        if (filters.successfulOnly) {
            results = results.filter { it.wasSuccessful() }
        } else if (filters.failedOnly) {
            results = results.filter { it.hasFailed() }
        }
        
        // Apply response time filters
        filters.minResponseTime?.let { minTime ->
            results = results.filter { entry ->
                entry.getResponseTimeMs()?.let { it >= minTime } ?: false
            }
        }
        
        filters.maxResponseTime?.let { maxTime ->
            results = results.filter { entry ->
                entry.getResponseTimeMs()?.let { it <= maxTime } ?: false
            }
        }
        
        return results
    }
    
    override fun getHistoryByDateRange(startDate: LocalDate, endDate: LocalDate): List<RequestHistoryEntry> {
        val startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endInstant = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        
        return settings.getHistoryByTimeRange(startInstant, endInstant)
    }
    
    override fun getHistoryByMethod(method: String): List<RequestHistoryEntry> {
        return getAllHistory().filter { it.request.method.name.equals(method, ignoreCase = true) }
    }
    
    override fun getHistoryByStatusCode(minStatus: Int, maxStatus: Int): List<RequestHistoryEntry> {
        return getAllHistory().filter { entry ->
            entry.response?.statusCode?.let { it in minStatus..maxStatus } ?: false
        }
    }
    
    override fun getSuccessfulRequests(): List<RequestHistoryEntry> {
        return getAllHistory().filter { it.wasSuccessful() }
    }
    
    override fun getFailedRequests(): List<RequestHistoryEntry> {
        return getAllHistory().filter { it.hasFailed() }
    }
    
    override fun getHistoryByEnvironment(environment: String): List<RequestHistoryEntry> {
        return getAllHistory().filter { it.environment == environment }
    }
    
    override fun getRecentHistory(limit: Int): List<RequestHistoryEntry> {
        return getAllHistory().take(limit)
    }
    
    override fun getFrequentRequests(limit: Int): List<FrequentRequest> {
        val history = getAllHistory()
        
        return history
            .groupBy { "${it.request.method.name} ${extractUrlPattern(it.request.url)}" }
            .map { (pattern, entries) ->
                val method = entries.first().request.method.name
                val urlPattern = extractUrlPattern(entries.first().request.url)
                val successfulEntries = entries.filter { it.wasSuccessful() }
                val responseTimes = entries.mapNotNull { it.getResponseTimeMs() }
                
                FrequentRequest(
                    urlPattern = urlPattern,
                    method = method,
                    count = entries.size,
                    lastExecuted = entries.maxOf { it.timestamp },
                    averageResponseTime = if (responseTimes.isNotEmpty()) {
                        responseTimes.average().toLong()
                    } else null,
                    successRate = if (entries.isNotEmpty()) {
                        successfulEntries.size.toDouble() / entries.size
                    } else 0.0
                )
            }
            .sortedByDescending { it.count }
            .take(limit)
    }
    
    override fun getHistoryStatistics(): HistoryStatistics {
        val history = getAllHistory()
        val successful = history.filter { it.wasSuccessful() }
        val failed = history.filter { it.hasFailed() }
        val responseTimes = history.mapNotNull { it.getResponseTimeMs() }
        val responseSizes = history.mapNotNull { it.getResponseSize() }
        
        return HistoryStatistics(
            totalRequests = history.size,
            successfulRequests = successful.size,
            failedRequests = failed.size,
            averageResponseTime = if (responseTimes.isNotEmpty()) {
                responseTimes.average().toLong()
            } else null,
            requestsByMethod = history.groupBy { it.request.method.name }
                .mapValues { it.value.size },
            requestsByStatusCode = history.mapNotNull { it.response?.statusCode }
                .groupBy { it }
                .mapValues { it.value.size },
            requestsByEnvironment = history.mapNotNull { it.environment }
                .groupBy { it }
                .mapValues { it.value.size },
            oldestEntry = history.minOfOrNull { it.timestamp },
            newestEntry = history.maxOfOrNull { it.timestamp },
            totalResponseSize = responseSizes.sum().toLong(),
            averageResponseSize = if (responseSizes.isNotEmpty()) {
                responseSizes.average().toLong()
            } else 0L
        )
    }
    
    override fun getDailyStatistics(startDate: LocalDate, endDate: LocalDate): List<DailyStatistics> {
        val history = getHistoryByDateRange(startDate, endDate)
        
        return generateSequence(startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endDate) }
            .map { date ->
                val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
                val dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
                
                val dayEntries = history.filter { entry ->
                    entry.timestamp.isAfter(dayStart) || entry.timestamp == dayStart &&
                    entry.timestamp.isBefore(dayEnd)
                }
                
                val successful = dayEntries.filter { it.wasSuccessful() }
                val failed = dayEntries.filter { it.hasFailed() }
                val responseTimes = dayEntries.mapNotNull { it.getResponseTimeMs() }
                val uniqueEndpoints = dayEntries.map { it.request.url }.distinct().size
                
                DailyStatistics(
                    date = date,
                    totalRequests = dayEntries.size,
                    successfulRequests = successful.size,
                    failedRequests = failed.size,
                    averageResponseTime = if (responseTimes.isNotEmpty()) {
                        responseTimes.average().toLong()
                    } else null,
                    uniqueEndpoints = uniqueEndpoints
                )
            }
            .toList()
    }
    
    override fun isEntryInFavorites(entryId: String): Boolean {
        val entry = getHistoryEntryById(entryId) ?: return false
        val favorites = favoritesService.getAllFavorites()
        
        return favorites.any { favorite ->
            favorite.request.method == entry.request.method &&
            favorite.request.url == entry.request.url &&
            favorite.request.headers == entry.request.headers &&
            favorite.request.body == entry.request.body
        }
    }
    
    override fun getSimilarRequests(entry: RequestHistoryEntry, limit: Int): List<RequestHistoryEntry> {
        val urlPattern = extractUrlPattern(entry.request.url)
        
        return getAllHistory()
            .filter { it.id != entry.id }
            .filter { extractUrlPattern(it.request.url) == urlPattern }
            .filter { it.request.method == entry.request.method }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    override fun exportHistory(): String {
        return try {
            val history = getAllHistory()
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(history)
        } catch (e: Exception) {
            "{\"error\": \"Failed to export history: ${e.message}\"}"
        }
    }
    
    override fun exportHistoryByDateRange(startDate: LocalDate, endDate: LocalDate): String {
        return try {
            val history = getHistoryByDateRange(startDate, endDate)
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(history)
        } catch (e: Exception) {
            "{\"error\": \"Failed to export history: ${e.message}\"}"
        }
    }
    
    override fun getHistoryEntryById(entryId: String): RequestHistoryEntry? {
        return getAllHistory().find { it.id == entryId }
    }
    
    override fun performHistoryCleanup(): HistoryCleanupResult {
        val allHistory = getAllHistory()
        val maxSize = settings.getMaxHistorySize()
        val favorites = favoritesService.getAllFavorites()
        
        // Create set of favorite request signatures for quick lookup
        val favoriteSignatures = favorites.map { favorite ->
            "${favorite.request.method.name}:${favorite.request.url}:${favorite.request.headers}:${favorite.request.body}"
        }.toSet()
        
        var removedCount = 0
        var preservedFavorites = 0
        
        if (allHistory.size > maxSize) {
            // Sort by timestamp (oldest first) but keep favorites
            val sortedHistory = allHistory.sortedBy { it.timestamp }
            val toRemoveCount = allHistory.size - maxSize
            
            var removed = 0
            for (entry in sortedHistory) {
                if (removed >= toRemoveCount) break
                
                val signature = "${entry.request.method.name}:${entry.request.url}:${entry.request.headers}:${entry.request.body}"
                
                if (signature in favoriteSignatures) {
                    preservedFavorites++
                } else {
                    settings.removeFromHistory(entry.id)
                    removedCount++
                    removed++
                }
            }
        }
        
        val finalHistorySize = getAllHistory().size
        
        return HistoryCleanupResult(
            removedEntries = removedCount,
            preservedFavorites = preservedFavorites,
            totalEntriesAfterCleanup = finalHistorySize
        )
    }
    
    override fun getDuplicateRequests(): Map<String, List<RequestHistoryEntry>> {
        return getAllHistory()
            .groupBy { "${it.request.method.name}:${it.request.url}" }
            .filter { it.value.size > 1 }
    }
    
    /**
     * Extract URL pattern by removing query parameters and path variables
     */
    private fun extractUrlPattern(url: String): String {
        return try {
            val baseUrl = url.split("?")[0] // Remove query parameters
            // Replace numeric path segments with placeholders
            baseUrl.split("/")
                .map { segment ->
                    if (segment.matches(Regex("\\d+"))) "{id}" else segment
                }
                .joinToString("/")
        } catch (e: Exception) {
            url
        }
    }
    
    // Event listener management
    override fun addEventListener(listener: HistoryEventListener) {
        synchronized(eventListeners) {
            if (!eventListeners.contains(listener)) {
                eventListeners.add(listener)
                logger.debug("Event listener added: ${listener.javaClass.simpleName}, total listeners: ${eventListeners.size}")
            } else {
                logger.warn("Event listener already registered: ${listener.javaClass.simpleName}")
            }
        }
    }
    
    override fun removeEventListener(listener: HistoryEventListener) {
        synchronized(eventListeners) {
            val removed = eventListeners.remove(listener)
            if (removed) {
                logger.debug("Event listener removed: ${listener.javaClass.simpleName}, remaining listeners: ${eventListeners.size}")
            } else {
                logger.warn("Event listener not found for removal: ${listener.javaClass.simpleName}")
            }
        }
    }
    
    // Event notification methods
    private fun notifyHistoryAdded(entry: RequestHistoryEntry) {
        logger.debug("Notifying ${eventListeners.size} listeners of history addition: id=${entry.id}")
        
        synchronized(eventListeners) {
            eventListeners.toList() // Create a copy to avoid concurrent modification
        }.forEach { listener ->
            try {
                listener.onHistoryAdded(entry)
                logger.debug("Listener notified successfully: ${listener.javaClass.simpleName}")
            } catch (e: Exception) {
                logger.error("Error notifying listener ${listener.javaClass.simpleName} of history addition: ${e.message}", e)
                // Continue notifying other listeners even if one fails
            }
        }
    }
    
    private fun notifyHistoryRemoved(entryId: String) {
        logger.debug("Notifying ${eventListeners.size} listeners of history removal: id=$entryId")
        
        synchronized(eventListeners) {
            eventListeners.toList() // Create a copy to avoid concurrent modification
        }.forEach { listener ->
            try {
                listener.onHistoryRemoved(entryId)
                logger.debug("Listener notified successfully: ${listener.javaClass.simpleName}")
            } catch (e: Exception) {
                logger.error("Error notifying listener ${listener.javaClass.simpleName} of history removal: ${e.message}", e)
                // Continue notifying other listeners even if one fails
            }
        }
    }
    
    private fun notifyHistoryCleared() {
        logger.debug("Notifying ${eventListeners.size} listeners of history clear")
        
        synchronized(eventListeners) {
            eventListeners.toList() // Create a copy to avoid concurrent modification
        }.forEach { listener ->
            try {
                listener.onHistoryCleared()
                logger.debug("Listener notified successfully: ${listener.javaClass.simpleName}")
            } catch (e: Exception) {
                logger.error("Error notifying listener ${listener.javaClass.simpleName} of history clear: ${e.message}", e)
                // Continue notifying other listeners even if one fails
            }
        }
    }
    
    companion object {
        fun getInstance(): HistoryService {
            return ApplicationManager.getApplication().getService(HistoryService::class.java)
        }
    }
}