package com.httppal.service

import com.httppal.model.Environment

/**
 * Service for managing environments (dev, staging, production, etc.)
 * Handles environment creation, editing, deletion, and switching
 */
interface EnvironmentService {
    
    /**
     * Get all available environments
     */
    fun getAllEnvironments(): List<Environment>
    
    /**
     * Get environment by ID
     */
    fun getEnvironmentById(id: String): Environment?
    
    /**
     * Get environment by name
     */
    fun getEnvironmentByName(name: String): Environment?
    
    /**
     * Get currently active environment
     */
    fun getCurrentEnvironment(): Environment?
    
    /**
     * Create a new environment
     * @param environment The environment to create
     * @return The created environment with validation applied
     * @throws IllegalArgumentException if environment validation fails
     */
    fun createEnvironment(environment: Environment): Environment
    
    /**
     * Update an existing environment
     * @param environment The environment to update
     * @return The updated environment
     * @throws IllegalArgumentException if environment validation fails or environment not found
     */
    fun updateEnvironment(environment: Environment): Environment
    
    /**
     * Delete an environment
     * @param environmentId The ID of the environment to delete
     * @return true if environment was deleted, false if not found
     */
    fun deleteEnvironment(environmentId: String): Boolean
    
    /**
     * Switch to a different environment
     * @param environmentId The ID of the environment to switch to, or null to deactivate all
     * @return The activated environment, or null if deactivated
     */
    fun switchToEnvironment(environmentId: String?): Environment?
    
    /**
     * Validate environment configuration
     * @param environment The environment to validate
     * @return List of validation errors, empty if valid
     */
    fun validateEnvironment(environment: Environment): List<String>
    
    /**
     * Apply environment settings to a request URL
     * @param path The request path
     * @param environment The environment to apply (uses current if null)
     * @return The full URL with environment base URL applied
     */
    fun buildUrlWithEnvironment(path: String, environment: Environment? = null): String
    
    /**
     * Get effective global headers for current environment
     * Combines application global headers with environment-specific headers
     * @param environment The environment to get headers for (uses current if null)
     * @return Map of effective headers
     */
    fun getEffectiveGlobalHeaders(environment: Environment? = null): Map<String, String>
    
    /**
     * Check if an environment name is already in use
     * @param name The name to check
     * @param excludeId Optional environment ID to exclude from check (for updates)
     * @return true if name is already in use
     */
    fun isEnvironmentNameInUse(name: String, excludeId: String? = null): Boolean
    
    /**
     * Get environment statistics
     * @return Map containing statistics about environments
     */
    fun getEnvironmentStatistics(): Map<String, Any>
    
    /**
     * Add listener for environment changes
     * @param listener Function to call when environments change
     */
    fun addEnvironmentChangeListener(listener: (List<Environment>) -> Unit)
    
    /**
     * Remove environment change listener
     * @param listener The listener to remove
     */
    fun removeEnvironmentChangeListener(listener: (List<Environment>) -> Unit)
}