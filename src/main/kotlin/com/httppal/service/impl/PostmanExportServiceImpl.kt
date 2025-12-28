package com.httppal.service.impl

import com.httppal.model.*
import com.httppal.service.EnvironmentVariableResolver
import com.httppal.service.PostmanExportService
import com.httppal.util.JacksonUtils
import com.httppal.util.UrlUtils
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import java.net.URI
import java.util.*

@Service(Service.Level.PROJECT)
class PostmanExportServiceImpl(
    private val project: Project
) : PostmanExportService {
    
    private val variableResolver: EnvironmentVariableResolver by lazy {
        project.getService(EnvironmentVariableResolver::class.java)
    }
    
    override fun exportToPostman(
        requests: List<RequestConfig>,
        collectionName: String,
        options: PostmanExportOptions
    ): ExportResult {
        return try {
            val items = requests.map { request ->
                convertToPostmanItem(request, null, options)
            }
            
            val json = generatePostmanCollection(items, collectionName, null)
            
            // Save file with path validation
            val fileName = sanitizeFileName("$collectionName.postman_collection.json")
            val file = File(fileName)
            
            // Validate file path for security
            if (!UrlUtils.isSafeFilePath(file.absolutePath)) {
                return ExportResult(
                    success = false,
                    errors = listOf("文件路径不安全")
                )
            }
            
            file.writeText(json)
            
            ExportResult(
                success = true,
                filePath = file.absolutePath,
                exportedCount = requests.size
            )
        } catch (e: Exception) {
            ExportResult(
                success = false,
                errors = listOf("导出失败: ${e.message}")
            )
        }
    }
    
    override fun exportHistoryToPostman(
        entries: List<RequestHistoryEntry>,
        collectionName: String,
        options: PostmanExportOptions
    ): ExportResult {
        return try {
            val items = entries.map { entry ->
                val name = "${entry.request.method} ${entry.request.url} - ${entry.timestamp}"
                convertToPostmanItem(entry.request, name, options)
            }
            
            val json = generatePostmanCollection(items, collectionName, "Exported from HttpPal History")
            
            // Save file with path validation
            val fileName = sanitizeFileName("$collectionName.postman_collection.json")
            val file = File(fileName)
            
            // Validate file path for security
            if (!UrlUtils.isSafeFilePath(file.absolutePath)) {
                return ExportResult(
                    success = false,
                    errors = listOf("文件路径不安全")
                )
            }
            
            file.writeText(json)
            
            ExportResult(
                success = true,
                filePath = file.absolutePath,
                exportedCount = entries.size
            )
        } catch (e: Exception) {
            ExportResult(
                success = false,
                errors = listOf("导出失败: ${e.message}")
            )
        }
    }
    
    override fun exportFavoritesToPostman(
        favorites: List<FavoriteRequest>,
        collectionName: String,
        options: PostmanExportOptions
    ): ExportResult {
        return try {
            val items = if (options.preserveFolderStructure) {
                // Group by folder
                buildFolderStructure(favorites, options)
            } else {
                // Flat list
                favorites.map { favorite ->
                    convertToPostmanItem(favorite.request, favorite.name, options)
                }
            }
            
            val json = generatePostmanCollection(items, collectionName, "Exported from HttpPal Favorites")
            
            // Save file with path validation
            val fileName = sanitizeFileName("$collectionName.postman_collection.json")
            val file = File(fileName)
            
            // Validate file path for security
            if (!UrlUtils.isSafeFilePath(file.absolutePath)) {
                return ExportResult(
                    success = false,
                    errors = listOf("文件路径不安全")
                )
            }
            
            file.writeText(json)
            
            ExportResult(
                success = true,
                filePath = file.absolutePath,
                exportedCount = favorites.size
            )
        } catch (e: Exception) {
            ExportResult(
                success = false,
                errors = listOf("导出失败: ${e.message}")
            )
        }
    }
    
    override fun generatePostmanCollection(
        items: List<PostmanItem>,
        collectionName: String,
        description: String?
    ): String {
        val collection = PostmanCollection(
            info = PostmanInfo(
                name = collectionName,
                description = description,
                schema = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
                postmanId = UUID.randomUUID().toString()
            ),
            item = items
        )
        
        return JacksonUtils.toJson(collection)
    }
    
    private fun buildFolderStructure(
        favorites: List<FavoriteRequest>,
        options: PostmanExportOptions
    ): List<PostmanItem> {
        val folderMap = mutableMapOf<String?, MutableList<FavoriteRequest>>()
        
        // Group favorites by folder
        favorites.forEach { favorite ->
            folderMap.getOrPut(favorite.folder) { mutableListOf() }.add(favorite)
        }
        
        val items = mutableListOf<PostmanItem>()
        
        // Add items without folder first
        folderMap[null]?.forEach { favorite ->
            items.add(convertToPostmanItem(favorite.request, favorite.name, options))
        }
        
        // Add folders with their items
        folderMap.entries
            .filter { it.key != null }
            .sortedBy { it.key }
            .forEach { (folder, favoritesInFolder) ->
                val folderItems = favoritesInFolder.map { favorite ->
                    convertToPostmanItem(favorite.request, favorite.name, options)
                }
                
                items.add(
                    PostmanItem(
                        name = folder!!,
                        item = folderItems
                    )
                )
            }
        
        return items
    }
    
    private fun convertToPostmanItem(
        request: RequestConfig,
        name: String?,
        options: PostmanExportOptions
    ): PostmanItem {
        // Process URL
        var url = request.url
        
        // Apply environment configuration
        if (options.applyEnvironment && options.environment != null) {
            url = applyEnvironmentToUrl(url, options.environment)
        }
        
        // Resolve environment variables
        if (options.resolveVariables && options.environment != null) {
            val resolved = variableResolver.resolveVariables(url, options.environment)
            url = resolved.resolvedText
        }
        
        // Build Postman URL
        val postmanUrl = buildPostmanUrl(url, request.queryParameters)
        
        // Build headers
        val headers = if (options.includeHeaders) {
            request.headers.map { (key, value) ->
                var headerValue = value
                if (options.resolveVariables && options.environment != null) {
                    headerValue = variableResolver.resolveVariables(value, options.environment).resolvedText
                }
                PostmanHeader(key, headerValue)
            }
        } else emptyList()
        
        // Build body
        val body = if (options.includeBody && request.body != null) {
            var bodyContent = request.body
            if (options.resolveVariables && options.environment != null) {
                bodyContent = variableResolver.resolveVariables(bodyContent, options.environment).resolvedText
            }
            
            val contentType = request.getContentType()
            val bodyMode = when {
                contentType?.contains("json") == true -> "raw"
                contentType?.contains("xml") == true -> "raw"
                contentType?.contains("form-urlencoded") == true -> "urlencoded"
                contentType?.contains("form-data") == true -> "formdata"
                else -> "raw"
            }
            
            val bodyOptions = if (bodyMode == "raw") {
                mapOf("raw" to mapOf("language" to detectLanguage(contentType)))
            } else null
            
            PostmanBody(
                mode = bodyMode,
                raw = bodyContent,
                options = bodyOptions
            )
        } else null
        
        return PostmanItem(
            name = name ?: request.getDisplayName(),
            request = PostmanRequest(
                method = request.method.name,
                header = headers.ifEmpty { null },
                body = body,
                url = postmanUrl
            )
        )
    }
    
    private fun applyEnvironmentToUrl(url: String, environment: Environment): String {
        // If it's a relative path, add baseUrl
        return if (!url.startsWith("http://") && !url.startsWith("https://")) {
            val baseUrl = environment.baseUrl.trimEnd('/')
            val path = if (url.startsWith('/')) url else "/$url"
            baseUrl + path
        } else {
            url
        }
    }
    
    private fun buildPostmanUrl(url: String, queryParams: Map<String, String>): PostmanUrl {
        return try {
            val uri = URI(url)
            
            val protocol = uri.scheme
            val host = uri.host?.split(".")
            val path = uri.path?.split("/")?.filter { it.isNotEmpty() }
            
            // Parse existing query parameters
            val existingQuery = uri.query?.split("&")?.mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    PostmanQueryParam(parts[0], parts[1])
                } else null
            } ?: emptyList()
            
            // Add additional query parameters
            val additionalQuery = queryParams.map { (key, value) ->
                PostmanQueryParam(key, value)
            }
            
            val allQuery = (existingQuery + additionalQuery).ifEmpty { null }
            
            PostmanUrl(
                raw = url,
                protocol = protocol,
                host = host,
                path = path,
                query = allQuery
            )
        } catch (e: Exception) {
            // If URL parsing fails, just use raw URL
            PostmanUrl(raw = url)
        }
    }
    
    private fun detectLanguage(contentType: String?): String {
        return when {
            contentType?.contains("json") == true -> "json"
            contentType?.contains("xml") == true -> "xml"
            contentType?.contains("html") == true -> "html"
            contentType?.contains("javascript") == true -> "javascript"
            else -> "text"
        }
    }
    
    /**
     * Sanitize file name to prevent path traversal and other security issues
     */
    private fun sanitizeFileName(fileName: String): String {
        // Remove potentially dangerous characters
        var sanitized = fileName
            .replace("..", "")  // Remove directory traversal
            .replace("/", "_")  // Replace forward slashes
            .replace("\\", "_") // Replace backslashes
            .replace(":", "_")  // Replace colons (for Windows)
            .replace("*", "_")  // Replace asterisks
            .replace("?", "_")  // Replace question marks
            .replace("\"", "_") // Replace quotes
            .replace("<", "_")  // Replace less than
            .replace(">", "_")  // Replace greater than
            .replace("|", "_")  // Replace pipe
        
        // Ensure it ends with .json
        if (!sanitized.endsWith(".json")) {
            sanitized += ".json"
        }
        
        return sanitized
    }
}