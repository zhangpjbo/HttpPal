package com.httppal.service

import com.httppal.model.*

/**
 * Service for exporting HTTP requests and test scenarios to Apache JMeter .jmx format
 */
interface JMeterExportService {
    
    /**
     * Export a single HTTP request to a JMeter test plan
     * 
     * @param request The HTTP request configuration to export
     * @param environment Optional environment settings to apply
     * @return JMeter test plan containing the single request
     */
    fun exportSingleRequest(request: RequestConfig, environment: Environment?): JMeterTestPlan
    
    /**
     * Export a concurrent execution scenario to a JMeter test plan
     * 
     * @param request The HTTP request configuration to execute concurrently
     * @param threadCount Number of concurrent threads
     * @param iterations Number of iterations per thread
     * @param environment Optional environment settings to apply
     * @return JMeter test plan with thread group configuration
     */
    fun exportConcurrentScenario(
        request: RequestConfig, 
        threadCount: Int, 
        iterations: Int, 
        environment: Environment?
    ): JMeterTestPlan
    
    /**
     * Export multiple HTTP requests to a structured JMeter test plan
     * 
     * @param requests List of HTTP request configurations to export
     * @param environment Optional environment settings to apply
     * @return JMeter test plan with organized test elements
     */
    fun exportMultipleRequests(requests: List<RequestConfig>, environment: Environment?): JMeterTestPlan
    
    /**
     * Generate JMX file content from a JMeter test plan
     * 
     * @param testPlan The JMeter test plan to convert
     * @return JMX file content as XML string
     */
    fun generateJmxFile(testPlan: JMeterTestPlan): String
    
    /**
     * Save a JMeter test plan to a .jmx file
     * 
     * @param testPlan The JMeter test plan to save
     * @param filePath Path where to save the .jmx file
     * @throws Exception if file cannot be written
     */
    fun saveJmxFile(testPlan: JMeterTestPlan, filePath: String)
}