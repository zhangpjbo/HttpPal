package com.httppal.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

/**
 * Data class representing UI layout configuration
 * Stores positions and sizes of UI components for persistence
 */
data class UILayout(
    val splitPanePositions: Map<String, Int> = emptyMap(),
    val selectedTab: Int = 0,
    val columnWidths: Map<String, Int> = emptyMap()
) {
    companion object {
        // Default layout values
        const val DEFAULT_MAIN_SPLIT_POSITION = 250
        const val DEFAULT_REQUEST_RESPONSE_SPLIT_POSITION = 400
        const val DEFAULT_SELECTED_TAB = 0
        
        fun default(): UILayout {
            return UILayout(
                splitPanePositions = mapOf(
                    "mainSplitPane" to DEFAULT_MAIN_SPLIT_POSITION,
                    "requestResponseSplitPane" to DEFAULT_REQUEST_RESPONSE_SPLIT_POSITION
                ),
                selectedTab = DEFAULT_SELECTED_TAB,
                columnWidths = emptyMap()
            )
        }
    }
}

/**
 * Interface for layout persistence operations
 * Defines methods for saving and loading UI layout configurations
 */
interface LayoutPersistence {
    /**
     * Save the current UI layout
     * @param layout The layout configuration to save
     */
    fun saveLayout(layout: UILayout)
    
    /**
     * Load the saved UI layout
     * @return The saved layout configuration, or null if no layout has been saved
     */
    fun loadLayout(): UILayout?
    
    /**
     * Reset layout to default values
     */
    fun resetLayout()
}

/**
 * Project-level implementation of LayoutPersistence using PersistentStateComponent
 * Stores UI layout preferences per project
 */
@Service(Service.Level.PROJECT)
@State(
    name = "HttpPalLayoutSettings",
    storages = [Storage("httppal-layout.xml")]
)
class LayoutPersistenceImpl : PersistentStateComponent<LayoutPersistenceImpl.State>, LayoutPersistence {
    
    data class State(
        var splitPanePositions: MutableMap<String, Int> = mutableMapOf(),
        var selectedTab: Int = 0,
        var columnWidths: MutableMap<String, Int> = mutableMapOf()
    )
    
    private var myState = State()
    
    override fun getState(): State = myState
    
    override fun loadState(state: State) {
        myState = state
    }
    
    override fun saveLayout(layout: UILayout) {
        myState.splitPanePositions.clear()
        myState.splitPanePositions.putAll(layout.splitPanePositions)
        myState.selectedTab = layout.selectedTab
        myState.columnWidths.clear()
        myState.columnWidths.putAll(layout.columnWidths)
    }
    
    override fun loadLayout(): UILayout? {
        // Return null if no layout has been saved (empty state)
        if (myState.splitPanePositions.isEmpty() && myState.columnWidths.isEmpty() && myState.selectedTab == 0) {
            return null
        }
        
        return UILayout(
            splitPanePositions = myState.splitPanePositions.toMap(),
            selectedTab = myState.selectedTab,
            columnWidths = myState.columnWidths.toMap()
        )
    }
    
    override fun resetLayout() {
        myState.splitPanePositions.clear()
        myState.selectedTab = 0
        myState.columnWidths.clear()
    }
    
    companion object {
        fun getInstance(project: Project): LayoutPersistenceImpl {
            return project.getService(LayoutPersistenceImpl::class.java)
        }
    }
}
