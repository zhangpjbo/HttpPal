package com.httppal.util

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.thisLogger

/**
 * Utility for checking version compatibility
 */
object VersionCompatibility {
    
    private val logger = thisLogger()
    
    /**
     * Check if current IntelliJ version is compatible
     */
    fun checkIntelliJCompatibility(): Boolean {
        val appInfo = ApplicationInfo.getInstance()
        val buildNumber = appInfo.build.baselineVersion
        
        logger.info("IntelliJ IDEA version: ${appInfo.fullVersion}, build: ${appInfo.build}")
        
        // Check if build number is >= 251 (2025.1)
        val isCompatible = buildNumber >= 251
        
        if (!isCompatible) {
            logger.warn("HttpPal requires IntelliJ IDEA 2025.1 or later. Current version: ${appInfo.fullVersion}")
        } else {
            logger.info("IntelliJ IDEA version is compatible (${appInfo.fullVersion})")
        }
        
        return isCompatible
    }
    
    /**
     * Check if current JDK version is compatible
     */
    fun checkJdkCompatibility(): Boolean {
        val javaVersion = System.getProperty("java.version")
        val javaVendor = System.getProperty("java.vendor")
        
        logger.info("Java version: $javaVersion, vendor: $javaVendor")
        
        // Check if Java version is >= 17
        val majorVersion = try {
            val versionParts = javaVersion.split(".")
            if (versionParts[0] == "1") {
                versionParts[1].toInt()
            } else {
                versionParts[0].toInt()
            }
        } catch (e: Exception) {
            logger.warn("Could not parse Java version: $javaVersion", e)
            return false
        }
        
        val isCompatible = majorVersion >= 17
        
        if (!isCompatible) {
            logger.warn("HttpPal requires JDK 17 or later. Current version: $javaVersion")
        } else {
            logger.info("JDK version is compatible ($javaVersion)")
        }
        
        return isCompatible
    }
    
    /**
     * Get supported IntelliJ version range
     */
    fun getSupportedVersionRange(): String {
        return "2025.1 - 2025.2"
    }
    
    /**
     * Check if specific IntelliJ version is supported
     */
    fun isVersionSupported(buildNumber: Int): Boolean {
        return buildNumber in 251..252
    }
    
    /**
     * Perform all compatibility checks
     */
    fun checkAllCompatibility(): Boolean {
        val intellijOk = checkIntelliJCompatibility()
        val jdkOk = checkJdkCompatibility()
        
        val result = intellijOk && jdkOk
        
        if (result) {
            logger.info("All compatibility checks passed")
        } else {
            logger.error("Compatibility checks failed - IntelliJ: $intellijOk, JDK: $jdkOk")
        }
        
        return result
    }
}