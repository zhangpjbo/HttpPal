package com.httppal.service

import com.httppal.model.RequestConfig
import com.httppal.model.RequestTemplate

/**
 * Service for managing request templates
 */
interface RequestTemplateService {
    
    /**
     * Get all available templates (built-in + custom)
     */
    fun getAllTemplates(): List<RequestTemplate>
    
    /**
     * Get built-in templates
     */
    fun getBuiltInTemplates(): List<RequestTemplate>
    
    /**
     * Get custom templates
     */
    fun getCustomTemplates(): List<RequestTemplate>
    
    /**
     * Get template by ID
     */
    fun getTemplateById(templateId: String): RequestTemplate?
    
    /**
     * Create new custom template
     */
    fun createTemplate(template: RequestTemplate): Boolean
    
    /**
     * Update existing template
     */
    fun updateTemplate(template: RequestTemplate): Boolean
    
    /**
     * Delete template
     */
    fun deleteTemplate(templateId: String): Boolean
    
    /**
     * Apply template to create RequestConfig
     */
    fun applyTemplate(templateId: String): RequestConfig?
    
    /**
     * Create template from current request
     */
    fun createTemplateFromRequest(
        request: RequestConfig,
        name: String,
        description: String?
    ): RequestTemplate
    
    /**
     * Check if template name exists
     */
    fun isTemplateNameExists(name: String): Boolean
    
    /**
     * Mark template as used
     */
    fun markTemplateAsUsed(templateId: String): Boolean
}
