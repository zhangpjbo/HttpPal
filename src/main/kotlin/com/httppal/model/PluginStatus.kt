package com.httppal.model

/**
 * Plugin status information
 */
data class PluginStatus(
    val initialized: Boolean,
    val servicesReady: Boolean,
    val activeConnections: Int,
    val runningExecutions: Int,
    val lastError: String? = null
)