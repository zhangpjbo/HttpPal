package com.httppal.util

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.awt.Component
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

/**
 * Centralized error handling and user notification utility
 */
object ErrorHandler {
    
    val logger = thisLogger()
    private const val NOTIFICATION_GROUP_ID = "HttpPal.Plugin"
    
    /**
     * Error severity levels
     */
    enum class ErrorSeverity {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }
    
    /**
     * Error recovery action
     */
    data class RecoveryAction(
        val title: String,
        val action: () -> Unit
    )
    
    /**
     * Comprehensive error information
     */
    data class ErrorInfo(
        val message: String,
        val details: String? = null,
        val cause: Throwable? = null,
        val severity: ErrorSeverity = ErrorSeverity.ERROR,
        val recoveryActions: List<RecoveryAction> = emptyList(),
        val userFriendlyMessage: String? = null
    )
    
    /**
     * Handle an error with comprehensive logging and user notification
     */
    fun handleError(
        error: ErrorInfo,
        project: Project? = null,
        component: Component? = null,
        showDialog: Boolean = true
    ) {
        // Log the error
        logError(error)
        
        // Show user notification
        if (showDialog) {
            showErrorToUser(error, project, component)
        }
        
        // Show IDE notification
        showIdeNotification(error, project)
    }
    
    /**
     * Handle a simple error with message
     */
    fun handleError(
        message: String,
        cause: Throwable? = null,
        project: Project? = null,
        component: Component? = null,
        severity: ErrorSeverity = ErrorSeverity.ERROR
    ) {
        val errorInfo = ErrorInfo(
            message = message,
            cause = cause,
            severity = severity,
            userFriendlyMessage = makeUserFriendly(message, cause)
        )
        handleError(errorInfo, project, component)
    }
    
    /**
     * Handle validation errors
     */
    fun handleValidationErrors(
        errors: List<String>,
        component: Component? = null,
        title: String = "Validation Error"
    ) {
        if (errors.isEmpty()) return
        
        val message = if (errors.size == 1) {
            errors.first()
        } else {
            "Multiple validation errors:\n${errors.joinToString("\n• ", "• ")}"
        }
        
        val errorInfo = ErrorInfo(
            message = "Validation failed",
            details = message,
            severity = ErrorSeverity.WARNING,
            userFriendlyMessage = message
        )
        
        logError(errorInfo)
        showValidationErrorDialog(message, title, component)
    }
    
    /**
     * Show success notification
     */
    fun showSuccess(
        message: String,
        project: Project? = null,
        details: String? = null
    ) {
        logger.info("Success: $message")
        
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(message, details ?: "", NotificationType.INFORMATION)
        
        notification.notify(project)
    }
    
    /**
     * Show warning notification
     */
    fun showWarning(
        message: String,
        project: Project? = null,
        details: String? = null
    ) {
        logger.warn("Warning: $message")
        
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(message, details ?: "", NotificationType.WARNING)
        
        notification.notify(project)
    }
    
    /**
     * Show info notification
     */
    fun showInfo(
        message: String,
        project: Project? = null,
        details: String? = null
    ) {
        logger.info("Info: $message")
        
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(message, details ?: "", NotificationType.INFORMATION)
        
        notification.notify(project)
    }
    
    /**
     * Execute with error handling
     */
    inline fun <T> withErrorHandling(
        operation: String,
        project: Project? = null,
        component: Component? = null,
        crossinline action: () -> T
    ): T? {
        return try {
            logger.debug("Starting operation: $operation")
            val result = action()
            logger.debug("Operation completed successfully: $operation")
            result
        } catch (e: Exception) {
            handleError(
                message = "Operation failed: $operation",
                cause = e,
                project = project,
                component = component
            )
            null
        }
    }
    
    /**
     * Execute with error handling and recovery
     */
    inline fun <T> withErrorHandlingAndRecovery(
        operation: String,
        project: Project? = null,
        component: Component? = null,
        crossinline recoveryAction: () -> T,
        crossinline action: () -> T
    ): T? {
        return try {
            logger.debug("Starting operation: $operation")
            val result = action()
            logger.debug("Operation completed successfully: $operation")
            result
        } catch (e: Exception) {
            logger.warn("Operation failed, attempting recovery: $operation", e)
            try {
                val recoveryResult = recoveryAction()
                logger.info("Recovery successful for operation: $operation")
                showWarning("Operation recovered after initial failure", project, operation)
                recoveryResult
            } catch (recoveryException: Exception) {
                handleError(
                    ErrorInfo(
                        message = "Operation failed and recovery failed: $operation",
                        cause = e,
                        details = "Recovery error: ${recoveryException.message}",
                        severity = ErrorSeverity.CRITICAL
                    ),
                    project,
                    component
                )
                null
            }
        }
    }
    
    private fun logError(error: ErrorInfo) {
        val logMessage = buildString {
            append("Error: ${error.message}")
            error.details?.let { append(" - Details: $it") }
        }
        
        when (error.severity) {
            ErrorSeverity.INFO -> logger.info(logMessage, error.cause)
            ErrorSeverity.WARNING -> logger.warn(logMessage, error.cause)
            ErrorSeverity.ERROR -> logger.error(logMessage, error.cause)
            ErrorSeverity.CRITICAL -> logger.error("CRITICAL: $logMessage", error.cause)
        }
    }
    
    private fun showErrorToUser(
        error: ErrorInfo,
        project: Project?,
        component: Component?
    ) {
        val userMessage = error.userFriendlyMessage ?: error.message
        val title = when (error.severity) {
            ErrorSeverity.INFO -> "Information"
            ErrorSeverity.WARNING -> "Warning"
            ErrorSeverity.ERROR -> "Error"
            ErrorSeverity.CRITICAL -> "Critical Error"
        }
        
        val messageType = when (error.severity) {
            ErrorSeverity.INFO -> JOptionPane.INFORMATION_MESSAGE
            ErrorSeverity.WARNING -> JOptionPane.WARNING_MESSAGE
            ErrorSeverity.ERROR -> JOptionPane.ERROR_MESSAGE
            ErrorSeverity.CRITICAL -> JOptionPane.ERROR_MESSAGE
        }
        
        SwingUtilities.invokeLater {
            if (error.recoveryActions.isNotEmpty()) {
                showErrorWithRecoveryOptions(userMessage, title, error.recoveryActions, component)
            } else {
                JOptionPane.showMessageDialog(
                    component,
                    userMessage,
                    title,
                    messageType
                )
            }
        }
    }
    
    private fun showErrorWithRecoveryOptions(
        message: String,
        title: String,
        recoveryActions: List<RecoveryAction>,
        component: Component?
    ) {
        val options = recoveryActions.map { it.title }.toTypedArray() + "Cancel"
        
        val choice = JOptionPane.showOptionDialog(
            component,
            message,
            title,
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.ERROR_MESSAGE,
            null,
            options,
            options.last()
        )
        
        if (choice >= 0 && choice < recoveryActions.size) {
            try {
                recoveryActions[choice].action()
            } catch (e: Exception) {
                logger.error("Recovery action failed", e)
                JOptionPane.showMessageDialog(
                    component,
                    "Recovery action failed: ${e.message}",
                    "Recovery Failed",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }
    
    private fun showIdeNotification(error: ErrorInfo, project: Project?) {
        val notificationType = when (error.severity) {
            ErrorSeverity.INFO -> NotificationType.INFORMATION
            ErrorSeverity.WARNING -> NotificationType.WARNING
            ErrorSeverity.ERROR -> NotificationType.ERROR
            ErrorSeverity.CRITICAL -> NotificationType.ERROR
        }
        
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                error.userFriendlyMessage ?: error.message,
                error.details ?: "",
                notificationType
            )
        
        notification.notify(project)
    }
    
    private fun showValidationErrorDialog(
        message: String,
        title: String,
        component: Component?
    ) {
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(
                component,
                message,
                title,
                JOptionPane.WARNING_MESSAGE
            )
        }
    }
    
    private fun makeUserFriendly(message: String, cause: Throwable?): String {
        return when {
            cause is java.net.ConnectException -> "Unable to connect to server. Please check your network connection and server availability."
            cause is java.net.SocketTimeoutException -> "Request timed out. The server may be slow or unavailable."
            cause is java.net.UnknownHostException -> "Cannot resolve server address. Please check the URL and your network connection."
            cause is javax.net.ssl.SSLException -> "SSL/TLS connection error. Please check the server's certificate."
            message.contains("validation", ignoreCase = true) -> "Please check your input and try again."
            message.contains("permission", ignoreCase = true) -> "Permission denied. Please check your access rights."
            message.contains("file", ignoreCase = true) -> "File operation failed. Please check file permissions and disk space."
            else -> message
        }
    }
}