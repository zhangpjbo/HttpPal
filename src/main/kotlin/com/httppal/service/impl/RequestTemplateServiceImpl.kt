package com.httppal.service.impl

import com.httppal.model.BuiltInTemplates
import com.httppal.model.RequestConfig
import com.httppal.model.RequestTemplate
import com.httppal.service.RequestTemplateService
import com.httppal.util.LoggingUtils
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.time.Instant
import java.util.*

/**
 * Implementation of RequestTemplateService with persistent storage
 */
@Service
@State(
    name = "HttpPalRequestTemplates",
    storages = [Storage("httppal-request-templates.xml")]
)
class RequestTemplateServiceImpl : RequestTemplateService, PersistentStateComponent<RequestTemplateServiceImpl.State> {
    
    private var myState = State()
    
    // Lazy-loaded cache for custom templates
    private val customTemplatesCache: List<RequestTemplate> by lazy {
        myState.customTemplates.map { it.toRequestTemplate() }
    }
    
    // Flag to invalidate cache when templates are modified
    private var cacheInvalidated = false
    
    data class State(
        var customTemplates: MutableList<SerializableTemplate> = mutableListOf()
    )
    
    /**
     * Serializable version of RequestTemplate for persistence
     */
    data class SerializableTemplate(
        var id: String = "",
        var name: String = "",
        var description: String? = null,
        var method: String = "GET",
        var urlTemplate: String? = null,
        var headers: MutableMap<String, String> = mutableMapOf(),
        var body: String? = null,
        var createdAt: Long = 0,
        var lastUsed: Long? = null,
        var useCount: Int = 0
    ) {
        fun toRequestTemplate(): RequestTemplate {
            return RequestTemplate(
                id = id,
                name = name,
                description = description,
                method = com.httppal.model.HttpMethod.valueOf(method),
                urlTemplate = urlTemplate,
                headers = headers.toMap(),
                body = body,
                isBuiltIn = false,
                createdAt = Instant.ofEpochMilli(createdAt),
                lastUsed = lastUsed?.let { Instant.ofEpochMilli(it) },
                useCount = useCount
            )
        }
        
        companion object {
            fun fromRequestTemplate(template: RequestTemplate): SerializableTemplate {
                return SerializableTemplate(
                    id = template.id,
                    name = template.name,
                    description = template.description,
                    method = template.method.name,
                    urlTemplate = template.urlTemplate,
                    headers = template.headers.toMutableMap(),
                    body = template.body,
                    createdAt = template.createdAt.toEpochMilli(),
                    lastUsed = template.lastUsed?.toEpochMilli(),
                    useCount = template.useCount
                )
            }
        }
    }
    
    override fun getState(): State = myState
    
    override fun loadState(state: State) {
        myState = state
        cacheInvalidated = true // Invalidate cache when state is loaded
    }
    
    override fun getAllTemplates(): List<RequestTemplate> {
        return getBuiltInTemplates() + getCustomTemplates()
    }
    
    override fun getBuiltInTemplates(): List<RequestTemplate> {
        return BuiltInTemplates.getAll()
    }
    
    override fun getCustomTemplates(): List<RequestTemplate> {
        // If cache is invalidated, reload from state
        return if (cacheInvalidated) {
            cacheInvalidated = false
            myState.customTemplates.map { it.toRequestTemplate() }
        } else {
            customTemplatesCache
        }
    }
    
    override fun getTemplateById(templateId: String): RequestTemplate? {
        // Check built-in templates first
        BuiltInTemplates.getById(templateId)?.let { return it }
        
        // Check custom templates
        return myState.customTemplates
            .find { it.id == templateId }
            ?.toRequestTemplate()
    }
    
    override fun createTemplate(template: RequestTemplate): Boolean {
        return try {
            if (template.isBuiltIn) {
                LoggingUtils.logWarning("Cannot create built-in template")
                return false
            }
            
            if (isTemplateNameExists(template.name)) {
                LoggingUtils.logWarning("Template name already exists: ${template.name}")
                return false
            }
            
            val errors = template.validate()
            if (errors.isNotEmpty()) {
                LoggingUtils.logWarning("Template validation failed: ${errors.joinToString(", ")}")
                return false
            }
            
            myState.customTemplates.add(SerializableTemplate.fromRequestTemplate(template))
            cacheInvalidated = true // Invalidate cache after modification
            LoggingUtils.logInfo("Created template: ${template.name}")
            true
        } catch (e: Exception) {
            LoggingUtils.logError("Failed to create template", e)
            false
        }
    }
    
    override fun updateTemplate(template: RequestTemplate): Boolean {
        return try {
            if (template.isBuiltIn) {
                LoggingUtils.logWarning("Cannot update built-in template")
                return false
            }
            
            val index = myState.customTemplates.indexOfFirst { it.id == template.id }
            if (index == -1) {
                LoggingUtils.logWarning("Template not found: ${template.id}")
                return false
            }
            
            val errors = template.validate()
            if (errors.isNotEmpty()) {
                LoggingUtils.logWarning("Template validation failed: ${errors.joinToString(", ")}")
                return false
            }
            
            myState.customTemplates[index] = SerializableTemplate.fromRequestTemplate(template)
            cacheInvalidated = true // Invalidate cache after modification
            LoggingUtils.logInfo("Updated template: ${template.name}")
            true
        } catch (e: Exception) {
            LoggingUtils.logError("Failed to update template", e)
            false
        }
    }
    
    override fun deleteTemplate(templateId: String): Boolean {
        return try {
            // Cannot delete built-in templates
            if (BuiltInTemplates.getById(templateId) != null) {
                LoggingUtils.logWarning("Cannot delete built-in template")
                return false
            }
            
            val removed = myState.customTemplates.removeIf { it.id == templateId }
            if (removed) {
                cacheInvalidated = true // Invalidate cache after modification
                LoggingUtils.logInfo("Deleted template: $templateId")
            } else {
                LoggingUtils.logWarning("Template not found: $templateId")
            }
            removed
        } catch (e: Exception) {
            LoggingUtils.logError("Failed to delete template", e)
            false
        }
    }
    
    override fun applyTemplate(templateId: String): RequestConfig? {
        val template = getTemplateById(templateId) ?: return null
        markTemplateAsUsed(templateId)
        return template.toRequestConfig()
    }
    
    override fun createTemplateFromRequest(
        request: RequestConfig,
        name: String,
        description: String?
    ): RequestTemplate {
        return RequestTemplate(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            method = request.method,
            urlTemplate = request.url,
            headers = request.headers,
            body = request.body,
            isBuiltIn = false,
            createdAt = Instant.now()
        )
    }
    
    override fun isTemplateNameExists(name: String): Boolean {
        return getAllTemplates().any { it.name.equals(name, ignoreCase = true) }
    }
    
    override fun markTemplateAsUsed(templateId: String): Boolean {
        return try {
            // Cannot mark built-in templates as used (they don't track usage)
            if (BuiltInTemplates.getById(templateId) != null) {
                return true
            }
            
            val index = myState.customTemplates.indexOfFirst { it.id == templateId }
            if (index == -1) {
                return false
            }
            
            val template = myState.customTemplates[index]
            template.lastUsed = Instant.now().toEpochMilli()
            template.useCount++
            cacheInvalidated = true // Invalidate cache after modification
            
            LoggingUtils.logDebug("Marked template as used: $templateId")
            true
        } catch (e: Exception) {
            LoggingUtils.logError("Failed to mark template as used", e)
            false
        }
    }
}
