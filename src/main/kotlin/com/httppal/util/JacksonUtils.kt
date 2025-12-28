package com.httppal.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule

object JacksonUtils {

    val mapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    fun mapToJson(data: Map<String, Any?>): String {
        return mapper.writeValueAsString(data)
    }

    fun jsonToMap(json: String): Map<String, Any> {
        return mapper.readValue(json, object : TypeReference<Map<String, Any>>() {})
    }

    fun isValidJson(json: String): Boolean {
        return try {
            mapper.readTree(json)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    // Generic JSON serialization
    fun toJson(obj: Any): String {
        return mapper.writeValueAsString(obj)
    }
    
    // Generic JSON deserialization
    inline fun <reified T> fromJson(json: String): T {
        return mapper.readValue(json, T::class.java)
    }
}