package com.httppal.service

import com.httppal.model.*

/**
 * Service for exporting requests to Postman Collection format
 */
interface PostmanExportService {
    
    /**
     * Export requests to Postman Collection
     */
    fun exportToPostman(
        requests: List<RequestConfig>,
        collectionName: String,
        options: PostmanExportOptions
    ): ExportResult
    
    /**
     * Export history entries to Postman Collection
     */
    fun exportHistoryToPostman(
        entries: List<RequestHistoryEntry>,
        collectionName: String,
        options: PostmanExportOptions
    ): ExportResult
    
    /**
     * Export favorites to Postman Collection
     */
    fun exportFavoritesToPostman(
        favorites: List<FavoriteRequest>,
        collectionName: String,
        options: PostmanExportOptions
    ): ExportResult
    
    /**
     * Generate Postman Collection JSON
     */
    fun generatePostmanCollection(
        items: List<PostmanItem>,
        collectionName: String,
        description: String?
    ): String
}
