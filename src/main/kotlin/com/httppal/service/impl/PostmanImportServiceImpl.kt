package com.httppal.service.impl

import com.httppal.model.*
import com.httppal.service.FavoritesService
import com.httppal.service.ImportResult
import com.httppal.service.ParseResult
import com.httppal.service.PostmanImportOptions
import com.httppal.service.PostmanImportService
import com.httppal.util.JacksonUtils
import com.httppal.util.UrlUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import java.time.Instant
import java.util.*

@Service(Service.Level.PROJECT)
class PostmanImportServiceImpl(
    private val project: Project
) : PostmanImportService {
    
    private val favoritesService: FavoritesService by lazy {
        ApplicationManager.getApplication().getService(FavoritesService::class.java)
    }
    
    override fun importFromPostman(filePath: String): ImportResult {
        // Default behavior: import to favorites with folder structure
        val defaultOptions = PostmanImportOptions(importToFavorites = true, preserveFolderStructure = true)
        return importFromPostman(filePath, defaultOptions)
    }
    
    override fun importFromPostman(filePath: String, options: PostmanImportOptions): ImportResult {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return ImportResult(
                    success = false,
                    errors = listOf("文件不存在: $filePath"),
                    importedCount = 0,
                    skippedCount = 0
                )
            }
            
            val json = file.readText()
            
            // Parse Collection
            val parseResult = parsePostmanCollection(json)
            if (parseResult is ParseResult.Failure) {
                return ImportResult(
                    success = false,
                    errors = parseResult.errors,
                    importedCount = 0,
                    skippedCount = 0
                )
            }
            
            val collection = (parseResult as ParseResult.Success).data
            
            // Convert and import
            var importedCount = 0
            var skippedCount = 0
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>() // We'll log warnings but not include them in result
            
            collection.item.forEach { item ->
                try {
                    val count = importItem(item, null, errors, warnings, options)
                    importedCount += count
                } catch (e: Exception) {
                    errors.add("导入 ${item.name} 失败: ${e.message}")
                    skippedCount++
                }
            }
            
            ImportResult(
                success = errors.isEmpty(), // Success if no errors occurred
                importedCount = importedCount,
                skippedCount = skippedCount,
                errors = errors
            )
        } catch (e: Exception) {
            ImportResult(
                success = false,
                importedCount = 0,
                skippedCount = 0,
                errors = listOf("导入失败: ${e.message}")
            )
        }
    }
    
    private fun importItem(
        item: PostmanItem,
        parentFolder: String?,
        errors: MutableList<String>,
        warnings: MutableList<String>,
        options: PostmanImportOptions
    ): Int {
        var count = 0
        
        if (item.request != null) {
            // This is a request
            try {
                // Validate URL before importing
                val url = item.request.url.raw
                if (!UrlUtils.isValidUrl(url) || !UrlUtils.isSafeUrlContent(url)) {
                    warnings.add("请求 ${item.name} 的URL不安全或格式无效，已跳过: $url")
                    return 0
                }
                
                if (options.importToFavorites) {
                    val favorite = convertToFavorite(item, if (options.preserveFolderStructure) parentFolder else null)
                    
                    // Handle name conflicts
                    var finalName = favorite.name
                    var suffix = 1
                    while (isFavoriteNameExists(finalName, if (options.preserveFolderStructure) parentFolder else null)) {
                        finalName = "${favorite.name} ($suffix)"
                        suffix++
                        if (suffix == 2) {
                            warnings.add("名称冲突: ${favorite.name} 已重命名为 $finalName")
                        }
                    }
                    
                    favoritesService.addFavorite(favorite.copy(name = finalName))
                    count++
                } else {
                    // If not importing to favorites, we could add to history or another location
                    // For now, just count as imported if we're not importing to favorites
                    count++
                }
            } catch (e: Exception) {
                errors.add("导入请求 ${item.name} 失败: ${e.message}")
            }
        } else if (item.item != null) {
            // This is a folder
            val folderName = if (options.preserveFolderStructure && parentFolder != null) {
                "$parentFolder/${item.name}"
            } else {
                item.name
            }
            
            // Recursively import sub-items
            item.item.forEach { subItem ->
                count += importItem(subItem, folderName, errors, warnings, options)
            }
        }
        
        return count
    }
    
    private fun isFavoriteNameExists(name: String, folder: String?): Boolean {
        return favoritesService.getAllFavorites()
            .any { it.name == name && it.folder == folder }
    }
    
    override fun convertToFavorite(
        item: PostmanItem,
        folder: String?
    ): FavoriteRequest {
        val request = item.request ?: throw IllegalArgumentException("Item has no request")
        
        val config = convertToRequestConfig(item)
        
        return FavoriteRequest(
            id = UUID.randomUUID().toString(),
            name = item.name,
            request = config,
            folder = folder,
            createdAt = Instant.now(),
            lastUsed = null,
            useCount = 0,
            tags = emptyList()
        )
    }
    
    override fun convertToRequestConfig(item: PostmanItem): RequestConfig {
        val request = item.request ?: throw IllegalArgumentException("Item has no request")
        
        val method = try {
            HttpMethod.valueOf(request.method.uppercase())
        } catch (e: Exception) {
            HttpMethod.GET
        }
        
        // Sanitize and normalize the URL
        var url = UrlUtils.sanitizeUrl(request.url.raw)
        url = UrlUtils.normalizeUrl(url)
        
        val headers = request.header
            ?.filter { it.disabled != true }
            ?.associate { it.key to it.value }
            ?: emptyMap()
        
        val body = request.body?.raw
        
        // Extract query parameters from URL
        val queryParams = request.url.query
            ?.filter { it.disabled != true }
            ?.associate { it.key to it.value }
            ?: emptyMap()
        
        return RequestConfig(
            method = method,
            url = url,
            headers = headers,
            body = body,
            queryParameters = queryParams
        )
    }
    
    override fun parsePostmanCollection(json: String): ParseResult<PostmanCollection> {
        return try {
            val collection = JacksonUtils.fromJson<PostmanCollection>(json)
            ParseResult.Success(collection)
        } catch (e: Exception) {
            ParseResult.Failure(listOf("解析 Postman Collection 失败: ${e.message}"))
        }
    }
    
    override fun validatePostmanCollection(json: String): ValidationResult {
        val errors = mutableListOf<String>()
        
        try {
            val tree = JacksonUtils.mapper.readTree(json)
            
            // Validate required fields
            if (!tree.has("info")) {
                errors.add("缺少 'info' 字段")
            }
            
            if (!tree.has("item")) {
                errors.add("缺少 'item' 字段")
            }
            
            // Validate schema version
            val schema = tree.get("info")?.get("schema")?.asText()
            if (schema != null && !schema.contains("v2.")) {
                errors.add("不支持的 Postman Collection 版本: $schema")
            }
            
            // Validate info.name
            val name = tree.get("info")?.get("name")?.asText()
            if (name.isNullOrBlank()) {
                errors.add("Collection 名称不能为空")
            }
        } catch (e: Exception) {
            errors.add("JSON 格式无效: ${e.message}")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.valid()
        } else {
            ValidationResult.invalid(errors)
        }
    }
}