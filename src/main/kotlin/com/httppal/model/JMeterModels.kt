package com.httppal.model

/**
 * JMeter test plan data model
 */
data class JMeterTestPlan(
    val name: String,
    val threadGroups: List<JMeterThreadGroup>,
    val globalHeaders: Map<String, String> = emptyMap(),
    val baseUrl: String? = null,
    val testPlanProperties: Map<String, String> = emptyMap()
) {
    /**
     * Validate the test plan
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (name.isBlank()) {
            errors.add("Test plan name cannot be empty")
        }
        
        if (threadGroups.isEmpty()) {
            errors.add("Test plan must contain at least one thread group")
        }
        
        // Validate each thread group
        threadGroups.forEachIndexed { index, threadGroup ->
            val threadGroupErrors = threadGroup.validate()
            threadGroupErrors.forEach { error ->
                errors.add("Thread group $index: $error")
            }
        }
        
        return errors
    }
}

/**
 * JMeter thread group configuration
 */
data class JMeterThreadGroup(
    val name: String,
    val threadCount: Int,
    val rampUpPeriod: Int = 1,
    val loopCount: Int = 1,
    val httpSamplers: List<JMeterHttpSampler>
) {
    /**
     * Validate the thread group
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (name.isBlank()) {
            errors.add("Thread group name cannot be empty")
        }
        
        if (threadCount < 1) {
            errors.add("Thread count must be at least 1")
        }
        
        if (threadCount > 1000) {
            errors.add("Thread count cannot exceed 1000")
        }
        
        if (rampUpPeriod < 0) {
            errors.add("Ramp-up period cannot be negative")
        }
        
        if (loopCount < 1) {
            errors.add("Loop count must be at least 1")
        }
        
        if (httpSamplers.isEmpty()) {
            errors.add("Thread group must contain at least one HTTP sampler")
        }
        
        // Validate each HTTP sampler
        httpSamplers.forEachIndexed { index, sampler ->
            val samplerErrors = sampler.validate()
            samplerErrors.forEach { error ->
                errors.add("HTTP sampler $index: $error")
            }
        }
        
        return errors
    }
}

/**
 * JMeter HTTP sampler configuration
 */
data class JMeterHttpSampler(
    val name: String,
    val method: HttpMethod,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val parameters: List<JMeterParameter> = emptyList(),
    val assertions: List<JMeterAssertion> = emptyList()
) {
    /**
     * Validate the HTTP sampler
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (name.isBlank()) {
            errors.add("HTTP sampler name cannot be empty")
        }
        
        if (path.isBlank()) {
            errors.add("HTTP sampler path cannot be empty")
        }
        
        // Validate headers
        headers.forEach { (headerName, _) ->
            if (headerName.isBlank()) {
                errors.add("Header name cannot be empty")
            }
            if (headerName.contains(":") || headerName.contains("\n") || headerName.contains("\r")) {
                errors.add("Header name '$headerName' contains invalid characters")
            }
        }
        
        // Validate parameters
        parameters.forEachIndexed { index, parameter ->
            val paramErrors = parameter.validate()
            paramErrors.forEach { error ->
                errors.add("Parameter $index: $error")
            }
        }
        
        // Validate assertions
        assertions.forEachIndexed { index, assertion ->
            val assertionErrors = assertion.validate()
            assertionErrors.forEach { error ->
                errors.add("Assertion $index: $error")
            }
        }
        
        return errors
    }
}

/**
 * JMeter parameter configuration
 */
data class JMeterParameter(
    val name: String,
    val value: String,
    val type: JMeterParameterType
) {
    /**
     * Validate the parameter
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (name.isBlank()) {
            errors.add("Parameter name cannot be empty")
        }
        
        return errors
    }
}

/**
 * JMeter assertion configuration
 */
data class JMeterAssertion(
    val type: JMeterAssertionType,
    val pattern: String,
    val field: JMeterAssertionField = JMeterAssertionField.RESPONSE_CODE
) {
    /**
     * Validate the assertion
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (pattern.isBlank()) {
            errors.add("Assertion pattern cannot be empty")
        }
        
        // Validate pattern based on type
        when (type) {
            JMeterAssertionType.RESPONSE_CODE -> {
                if (!pattern.matches(Regex("\\d{3}"))) {
                    errors.add("Response code pattern must be a 3-digit number")
                }
            }
            JMeterAssertionType.RESPONSE_MESSAGE -> {
                // No specific validation for response message
            }
            JMeterAssertionType.RESPONSE_DATA -> {
                // No specific validation for response data
            }
        }
        
        return errors
    }
}

/**
 * JMeter parameter types
 */
enum class JMeterParameterType {
    QUERY, POST, PATH
}

/**
 * JMeter assertion types
 */
enum class JMeterAssertionType {
    RESPONSE_CODE, RESPONSE_MESSAGE, RESPONSE_DATA
}

/**
 * JMeter assertion fields
 */
enum class JMeterAssertionField {
    RESPONSE_CODE, RESPONSE_MESSAGE, RESPONSE_DATA, RESPONSE_HEADERS
}