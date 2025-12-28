package com.httppal.action

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Action group for HttpPal toolbar actions
 * Provides a collection of commonly used actions for the toolbar
 */
class HttpPalToolbarActionGroup : ActionGroup() {
    
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(
            OpenHttpPalToolWindowAction(),
            RefreshEndpointsAction(),
            OpenEnvironmentManagerAction(),
            OpenWebSocketAction(),
            OpenSettingsAction()
        )
    }
}