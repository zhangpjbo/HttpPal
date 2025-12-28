package com.httppal.action

import com.httppal.service.EndpointDiscoveryService
import com.httppal.service.FavoritesService
import com.httppal.model.FavoriteRequest
import com.httppal.model.RequestConfig
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiMethod
import com.intellij.icons.AllIcons
import java.time.Instant
import java.util.UUID

/**
 * Context menu action to add discovered endpoint to favorites
 * Allows quick saving of endpoints for later use
 */
class AddEndpointToFavoritesAction : AnAction(
    "Add to HttpPal Favorites",
    "Add this API endpoint to HttpPal favorites",
    AllIcons.Toolwindows.ToolWindowFavorites
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        
        try {
            val endpointDiscoveryService = project.service<EndpointDiscoveryService>()
            val favoritesService = service<FavoritesService>()
            val endpoints = endpointDiscoveryService.getEndpointsForFile(psiFile)
            
            if (endpoints.isEmpty()) {
                Messages.showInfoMessage(
                    project,
                    "No API endpoints found in this file.",
                    "HttpPal - Add to Favorites"
                )
                return
            }
            
            // Find endpoint at cursor position
            val caretOffset = editor.caretModel.offset
            val element = psiFile.findElementAt(caretOffset)
            val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
            
            val targetEndpoint = if (method != null) {
                endpoints.find { it.methodName == method.name }
            } else {
                endpoints.firstOrNull()
            }
            
            if (targetEndpoint == null) {
                Messages.showInfoMessage(
                    project,
                    "No API endpoint found at cursor position.",
                    "HttpPal - Add to Favorites"
                )
                return
            }
            
            // Convert discovered endpoint to request config
            val requestConfig = RequestConfig(
                method = targetEndpoint.method,
                url = targetEndpoint.path,
                headers = emptyMap(),
                body = null
            )
            
            // Create favorite request
            val favoriteRequest = FavoriteRequest(
                id = UUID.randomUUID().toString(),
                name = "${targetEndpoint.method} ${targetEndpoint.path}",
                request = requestConfig,
                tags = listOf("discovered", targetEndpoint.className),
                createdAt = Instant.now()
            )
            
            // Add to favorites
            favoritesService.addFavorite(favoriteRequest)
            
            Messages.showInfoMessage(
                project,
                "Endpoint added to favorites: ${targetEndpoint.method} ${targetEndpoint.path}",
                "HttpPal - Added to Favorites"
            )
            
        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to add endpoint to favorites: ${ex.message}",
                "HttpPal - Error"
            )
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        
        // Only show this action in Java files with an active editor
        e.presentation.isEnabledAndVisible = project != null && 
                editor != null && 
                psiFile != null && 
                (psiFile.name.endsWith(".java") || psiFile.name.endsWith(".kt"))
    }
}