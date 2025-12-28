package com.httppal.migration

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Scanner for plugin.xml configuration to identify 2.x compatibility issues
 */
class PluginXmlScanner {

    /**
     * Scan plugin.xml for configuration issues
     */
    fun scanPluginXml(file: File): List<ConfigurationIssue> {
        if (!file.exists() || file.name != "plugin.xml") {
            return emptyList()
        }

        val issues = mutableListOf<ConfigurationIssue>()

        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(file)
            document.documentElement.normalize()

            // Check service declarations
            val serviceElements = document.getElementsByTagName("applicationService")
            for (i in 0 until serviceElements.length) {
                val element = serviceElements.item(i) as? Element ?: continue
                checkServiceDeclaration(element, "applicationService", issues)
            }

            val projectServiceElements = document.getElementsByTagName("projectService")
            for (i in 0 until projectServiceElements.length) {
                val element = projectServiceElements.item(i) as? Element ?: continue
                checkServiceDeclaration(element, "projectService", issues)
            }

            // Check action declarations
            val actionElements = document.getElementsByTagName("action")
            for (i in 0 until actionElements.length) {
                val element = actionElements.item(i) as? Element ?: continue
                checkActionDeclaration(element, issues)
            }

            // Check extension declarations
            val extensionElements = document.getElementsByTagName("extensions")
            for (i in 0 until extensionElements.length) {
                val element = extensionElements.item(i) as? Element ?: continue
                checkExtensionDeclaration(element, issues)
            }

        } catch (e: Exception) {
            issues.add(
                ConfigurationIssue(
                    file = file.path,
                    element = "root",
                    issue = "Failed to parse plugin.xml: ${e.message}",
                    suggestion = "Ensure plugin.xml is valid XML"
                )
            )
        }

        return issues
    }

    private fun checkServiceDeclaration(element: Element, tagName: String, issues: MutableList<ConfigurationIssue>) {
        val serviceInterface = element.getAttribute("serviceInterface")
        val serviceImplementation = element.getAttribute("serviceImplementation")

        // Check if attributes are in 2.x preferred order (implementation before interface)
        val elementString = element.toString()
        if (serviceInterface.isNotEmpty() && serviceImplementation.isNotEmpty()) {
            // In 2.x, serviceImplementation should come before serviceInterface
            val interfaceIndex = elementString.indexOf("serviceInterface")
            val implementationIndex = elementString.indexOf("serviceImplementation")
            
            if (interfaceIndex >= 0 && implementationIndex >= 0 && interfaceIndex < implementationIndex) {
                issues.add(
                    ConfigurationIssue(
                        file = "plugin.xml",
                        element = tagName,
                        issue = "Service attributes in 1.x order (interface before implementation)",
                        suggestion = "Reorder to: serviceImplementation=\"$serviceImplementation\" serviceInterface=\"$serviceInterface\""
                    )
                )
            }
        }

        // Check for missing required attributes
        if (serviceImplementation.isEmpty()) {
            issues.add(
                ConfigurationIssue(
                    file = "plugin.xml",
                    element = tagName,
                    issue = "Missing serviceImplementation attribute",
                    suggestion = "Add serviceImplementation attribute"
                )
            )
        }
    }

    private fun checkActionDeclaration(element: Element, issues: MutableList<ConfigurationIssue>) {
        val actionClass = element.getAttribute("class")
        
        if (actionClass.isEmpty()) {
            issues.add(
                ConfigurationIssue(
                    file = "plugin.xml",
                    element = "action",
                    issue = "Action missing class attribute",
                    suggestion = "Add class attribute to action declaration"
                )
            )
        }
    }

    private fun checkExtensionDeclaration(element: Element, issues: MutableList<ConfigurationIssue>) {
        val defaultExtensionNs = element.getAttribute("defaultExtensionNs")
        
        // Check for proper namespace declaration
        if (defaultExtensionNs.isEmpty()) {
            issues.add(
                ConfigurationIssue(
                    file = "plugin.xml",
                    element = "extensions",
                    issue = "Extensions missing defaultExtensionNs attribute",
                    suggestion = "Add defaultExtensionNs=\"com.intellij\" for IntelliJ extensions"
                )
            )
        }
    }

    /**
     * Scan for plugin.xml files in a directory
     */
    fun findAndScanPluginXml(directory: File): List<ConfigurationIssue> {
        val pluginXmlFile = findPluginXml(directory)
        return if (pluginXmlFile != null) {
            scanPluginXml(pluginXmlFile)
        } else {
            emptyList()
        }
    }

    private fun findPluginXml(directory: File): File? {
        if (!directory.exists() || !directory.isDirectory) {
            return null
        }

        directory.listFiles()?.forEach { file ->
            when {
                file.name == "plugin.xml" -> return file
                file.isDirectory -> {
                    val found = findPluginXml(file)
                    if (found != null) return found
                }
            }
        }

        return null
    }
}
