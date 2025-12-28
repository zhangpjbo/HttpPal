package com.httppal.service

import com.httppal.model.*

/**
 * Service for importing requests from Postman Collection format
 */
interface PostmanImportService {
    
    /**
     * Import from Postman Collection file
     */
    fun importFromPostman(filePath: String): ImportResult
    
    /**
     * Import from Postman Collection with options
     */
    fun importFromPostman(filePath: String, options: PostmanImportOptions): ImportResult
    
    /**
     * Parse Postman Collection JSON
     */
    fun parsePostmanCollection(json: String): ParseResult<PostmanCollection>
    
    /**
     * Convert Postman Item to RequestConfig
     */
    fun convertToRequestConfig(item: PostmanItem): RequestConfig
    
    /**
     * Convert Postman Item to FavoriteRequest
     */
    fun convertToFavorite(
        item: PostmanItem,
        folder: String?
    ): FavoriteRequest
    
    /**
     * Validate Postman Collection format
     */
    fun validatePostmanCollection(json: String): ValidationResult
}

/**
 * Options for Postman import
 */
data class PostmanImportOptions(
    val importToFavorites: Boolean = true,
    val preserveFolderStructure: Boolean = true
)