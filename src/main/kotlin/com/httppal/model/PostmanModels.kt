package com.httppal.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Postman Collection v2.1 data models
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PostmanCollection(
    val info: PostmanInfo,
    val item: List<PostmanItem>,
    val variable: List<PostmanVariable>? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PostmanInfo(
    val name: String,
    val description: String? = null,
    val schema: String = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    @JsonProperty("_postman_id")
    val postmanId: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PostmanItem(
    val name: String,
    val request: PostmanRequest? = null,
    val item: List<PostmanItem>? = null, // 子文件夹
    val description: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PostmanRequest(
    val method: String,
    val header: List<PostmanHeader>? = null,
    val body: PostmanBody? = null,
    val url: PostmanUrl,
    val description: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PostmanUrl(
    val raw: String,
    val protocol: String? = null,
    val host: List<String>? = null,
    val path: List<String>? = null,
    val query: List<PostmanQueryParam>? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PostmanHeader(
    val key: String,
    val value: String,
    val disabled: Boolean? = false,
    val description: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PostmanBody(
    val mode: String, // raw, formdata, urlencoded, etc.
    val raw: String? = null,
    val options: Map<String, Any>? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PostmanQueryParam(
    val key: String,
    val value: String,
    val disabled: Boolean? = false,
    val description: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PostmanVariable(
    val key: String,
    val value: String,
    val type: String? = "string"
)

/**
 * Postman export options
 */
data class PostmanExportOptions(
    val applyEnvironment: Boolean = true,
    val resolveVariables: Boolean = false,
    val includeHeaders: Boolean = true,
    val includeBody: Boolean = true,
    val preserveFolderStructure: Boolean = true,
    val environment: Environment? = null
)

/**
 * Export result
 */
data class ExportResult(
    val success: Boolean,
    val filePath: String? = null,
    val exportedCount: Int = 0,
    val errors: List<String> = emptyList()
)

