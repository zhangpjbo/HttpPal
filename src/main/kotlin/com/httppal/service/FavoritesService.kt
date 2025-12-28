package com.httppal.service

import com.httppal.model.FavoriteRequest
import com.httppal.model.RequestConfig
import com.httppal.model.RequestHistoryEntry
import java.time.Instant

/**
 * Service for managing favorite requests
 * Provides advanced functionality for organizing, searching, and managing favorites
 */
interface FavoritesService {
    
    /**
     * Get all favorite requests
     */
    fun getAllFavorites(): List<FavoriteRequest>
    
    /**
     * Get favorites by folder
     */
    fun getFavoritesByFolder(folder: String?): List<FavoriteRequest>
    
    /**
     * Get all folders used in favorites
     */
    fun getAllFolders(): List<String>
    
    /**
     * Add a new favorite request
     */
    fun addFavorite(favorite: FavoriteRequest): Boolean
    
    /**
     * Update an existing favorite
     */
    fun updateFavorite(favorite: FavoriteRequest): Boolean
    
    /**
     * Remove a favorite by ID
     */
    fun removeFavorite(favoriteId: String): Boolean
    
    /**
     * Remove all favorites in a folder
     */
    fun removeFavoritesByFolder(folder: String): Int
    
    /**
     * Search favorites by query
     */
    fun searchFavorites(query: String): List<FavoriteRequest>
    
    /**
     * Search favorites by tags
     */
    fun searchFavoritesByTags(tags: List<String>): List<FavoriteRequest>
    
    /**
     * Get favorites sorted by usage (most used first)
     */
    fun getFavoritesByUsage(): List<FavoriteRequest>
    
    /**
     * Get recently used favorites
     */
    fun getRecentlyUsedFavorites(limit: Int = 10): List<FavoriteRequest>
    
    /**
     * Mark a favorite as used (increment usage count and update last used time)
     */
    fun markFavoriteAsUsed(favoriteId: String): Boolean
    
    /**
     * Create favorite from history entry
     */
    fun createFavoriteFromHistory(historyEntry: RequestHistoryEntry, name: String, folder: String? = null): FavoriteRequest
    
    /**
     * Create favorite from request config
     */
    fun createFavoriteFromRequest(request: RequestConfig, name: String, folder: String? = null): FavoriteRequest
    
    /**
     * Duplicate a favorite with a new name
     */
    fun duplicateFavorite(favoriteId: String, newName: String): FavoriteRequest?
    
    /**
     * Move favorite to different folder
     */
    fun moveFavoriteToFolder(favoriteId: String, folder: String?): Boolean
    
    /**
     * Rename folder (updates all favorites in that folder)
     */
    fun renameFolder(oldFolder: String, newFolder: String): Int
    
    /**
     * Get favorite by ID
     */
    fun getFavoriteById(favoriteId: String): FavoriteRequest?
    
    /**
     * Check if favorite name exists in folder
     */
    fun isFavoriteNameExists(name: String, folder: String?): Boolean
    
    /**
     * Get statistics about favorites
     */
    fun getFavoritesStatistics(): FavoritesStatistics
    
    /**
     * Export favorites to JSON string
     */
    fun exportFavorites(): String
    
    /**
     * Import favorites from JSON string
     */
    fun importFavorites(json: String): ImportResult
}

/**
 * Statistics about favorites collection
 */
data class FavoritesStatistics(
    val totalFavorites: Int,
    val totalFolders: Int,
    val mostUsedFavorite: FavoriteRequest?,
    val recentlyAddedCount: Int, // Added in last 7 days
    val averageUseCount: Double,
    val favoritesByMethod: Map<String, Int>
)

/**
 * Result of importing favorites
 */
data class ImportResult(
    val success: Boolean,
    val importedCount: Int,
    val skippedCount: Int,
    val errors: List<String>
)