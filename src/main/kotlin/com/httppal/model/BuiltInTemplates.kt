package com.httppal.model

/**
 * Built-in request templates
 */
object BuiltInTemplates {
    
    val BLANK = RequestTemplate(
        id = "blank",
        name = "空白请求",
        description = "创建一个空白的 GET 请求",
        method = HttpMethod.GET,
        urlTemplate = null,
        headers = emptyMap(),
        body = null,
        isBuiltIn = true
    )
    
    val JSON_POST = RequestTemplate(
        id = "json-post",
        name = "JSON POST",
        description = "创建一个 JSON POST 请求",
        method = HttpMethod.POST,
        urlTemplate = null,
        headers = mapOf("Content-Type" to "application/json"),
        body = "{\n  \n}",
        isBuiltIn = true
    )
    
    val FORM_POST = RequestTemplate(
        id = "form-post",
        name = "表单 POST",
        description = "创建一个表单 POST 请求",
        method = HttpMethod.POST,
        urlTemplate = null,
        headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
        body = null,
        isBuiltIn = true
    )
    
    val REST_API = RequestTemplate(
        id = "rest-api",
        name = "REST API",
        description = "创建一个标准 REST API 请求",
        method = HttpMethod.GET,
        urlTemplate = null,
        headers = mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json"
        ),
        body = null,
        isBuiltIn = true
    )
    
    val JSON_PUT = RequestTemplate(
        id = "json-put",
        name = "JSON PUT",
        description = "创建一个 JSON PUT 请求",
        method = HttpMethod.PUT,
        urlTemplate = null,
        headers = mapOf("Content-Type" to "application/json"),
        body = "{\n  \n}",
        isBuiltIn = true
    )
    
    val JSON_PATCH = RequestTemplate(
        id = "json-patch",
        name = "JSON PATCH",
        description = "创建一个 JSON PATCH 请求",
        method = HttpMethod.PATCH,
        urlTemplate = null,
        headers = mapOf("Content-Type" to "application/json"),
        body = "{\n  \n}",
        isBuiltIn = true
    )
    
    /**
     * Get all built-in templates
     */
    fun getAll(): List<RequestTemplate> = listOf(
        BLANK,
        JSON_POST,
        FORM_POST,
        REST_API,
        JSON_PUT,
        JSON_PATCH
    )
    
    /**
     * Get template by ID
     */
    fun getById(id: String): RequestTemplate? {
        return getAll().find { it.id == id }
    }
}
