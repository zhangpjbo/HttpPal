package com.httppal.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.httppal.model.*
import com.httppal.service.MockDataGeneratorService
import com.httppal.service.PatternGenerator
import com.httppal.service.SmartFieldNameProvider
import com.httppal.util.LoggingUtils
import com.httppal.util.PerformanceMonitor
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import net.datafaker.Faker
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.Collections
import kotlin.random.Random

/**
 * Implementation of MockDataGeneratorService using Datafaker
 * 使用 Datafaker 实现 mock 数据生成
 *
 * 优化特性：
 * - 智能字段名识别（根据字段名自动选择合适的数据）
 * - 正则表达式支持（根据pattern生成符合规则的数据）
 * - LRU缓存机制（避免重复生成相同类型的对象）
 * - 循环引用检测（防止对象循环依赖导致栈溢出）
 * - 配置化参数（支持自定义生成策略）
 */
@Service(Service.Level.PROJECT)
class MockDataGeneratorServiceImpl(private val project: Project) : MockDataGeneratorService {

    // 配置
    private val config = MockGeneratorConfig.DEFAULT

    // 核心组件
    private val faker = Faker(config.locale)
    private val smartFieldProvider = SmartFieldNameProvider(faker)
    private val patternGenerator = PatternGenerator()
    private val objectMapper = ObjectMapper()
    private val random = Random.Default

    // LRU缓存：类型全限定名 -> 生成的对象
    private val objectCache = if (config.enableCache) {
        Collections.synchronizedMap(
            object : LinkedHashMap<String, Any?>(config.maxCacheSize, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any?>?): Boolean {
                    return size > config.maxCacheSize
                }
            }
        )
    } else null

    // 循环引用检测栈（线程安全）
    private val generationStack = ThreadLocal.withInitial { mutableSetOf<String>() }
    
    override fun generateMockRequest(
        endpoint: DiscoveredEndpoint,
        schemaInfo: SchemaInfo?
    ): MockDataConfig {
        return PerformanceMonitor.measure(
            operation = "Mock Data Generation",
            threshold = 500L, // 500ms threshold for mock generation
            details = mapOf(
                "endpoint" to endpoint.path,
                "method" to endpoint.method.name,
                "hasSchema" to (schemaInfo != null),
                "paramCount" to endpoint.parameters.size
            )
        ) {
            val pathParams = mutableMapOf<String, String>()
            val queryParams = mutableMapOf<String, String>()
            val headers = mutableMapOf<String, String>()
            var body: String? = null
            
            // 生成参数数据
            endpoint.parameters.forEach { param ->
                when (param.type) {
                    ParameterType.PATH -> {
                        val value = generateValueForParameter(param)
                        pathParams[param.name] = value
                    }
                    ParameterType.QUERY -> {
                        val value = generateValueForParameter(param)
                        queryParams[param.name] = value
                    }
                    ParameterType.HEADER -> {
                        val value = generateValueForParameter(param)
                        headers[param.name] = value
                    }
                    ParameterType.BODY -> {
                        // 如果有 schemaInfo，使用它生成 body
                        body = if (schemaInfo != null) {
                            val bodyData = generateValueForSchema(schemaInfo)
                            objectMapper.writeValueAsString(bodyData)
                        } else {
                            generateDefaultBody(param)
                        }
                    }
                }
            }
            
            MockDataConfig(
                pathParameters = pathParams,
                queryParameters = queryParams,
                headers = headers,
                body = body
            )
        }
    }
    
    private fun generateValueForParameter(param: EndpointParameter): String {
        // 优先使用示例值
        param.example
            ?.takeIf { it.isNotBlank() }
            ?.trim()
            ?.let { return it }

        // 使用默认值
        param.defaultValue
            ?.takeIf { it.isNotBlank() }
            ?.trim()
            ?.let { return it }

        // 智能字段名识别（新增）
        if (config.enableSmartFieldName && param.name.isNotBlank()) {
            smartFieldProvider.generateByFieldName(param.name, param.dataType)?.let {
                return it
            }
        }

        // 检查是否是集合类型（如 List<User>）
        if (!param.qualifiedType.isNullOrBlank()) {
            val collectionValue = generateValueForCollectionType(param.qualifiedType)
            if (collectionValue != null) {
                return collectionValue
            }
        }

        // 根据数据类型生成
        return when (param.dataType?.lowercase()) {
            "integer", "int", "long" -> random.nextInt(
                config.defaultMinValue.toInt(),
                config.defaultMaxValue.toInt()
            ).toString()
            "number", "float", "double" -> random.nextDouble(
                config.defaultMinValue,
                config.defaultMaxValue
            ).toString()
            "boolean" -> random.nextBoolean().toString()
            "array", "list", "set" -> "[]"
            else -> faker.lorem().word()
        }
    }
    
    private fun generateValueForCollectionType(qualifiedType: String): String? {
        // 检查是否是集合类型，例如 java.util.List<com.example.User>
        if (qualifiedType.startsWith("java.util.List") ||
            qualifiedType.startsWith("java.util.ArrayList") ||
            qualifiedType.startsWith("java.util.Set") ||
            qualifiedType.startsWith("java.util.HashSet") ||
            qualifiedType.startsWith("java.util.Collection")) {

            // 尝试提取泛型类型
            if (qualifiedType.contains("<") && qualifiedType.contains(">")) {
                val genericType = qualifiedType.substringAfter("<").substringBefore(">")

                // 查找泛型类型对应的类
                val psiClass = JavaPsiFacade.getInstance(project).findClass(
                    genericType,
                    GlobalSearchScope.allScope(project)
                )

                if (psiClass != null) {
                    // 生成一个包含模拟对象的列表
                    val mockObjects = mutableListOf<Map<String, Any?>>()
                    // 使用配置的集合大小
                    for (i in 0 until config.defaultCollectionSize) {
                        val mockObject = generateFromPsiClass(psiClass, 0) as? Map<String, Any?>
                        if (mockObject != null) {
                            mockObjects.add(mockObject)
                        }
                    }

                    return try {
                        objectMapper.writeValueAsString(mockObjects)
                    } catch (e: Exception) {
                        "[]"
                    }
                }
            }

            return "[]"  // 默认返回空数组
        }

        return null  // 不是集合类型
    }
    
    private fun generateDefaultBody(param: EndpointParameter): String {
        // 优先使用默认值
        param.example
            ?.takeIf { it.isNotBlank() } // 仅当非空且非空白时通过
            ?.trim()
            ?.let { return it }

        // 使用默认值
        param.defaultValue
            ?.takeIf { it.isNotBlank() } // 仅当非空且非空白时通过
            ?.trim()
            ?.let { return it }
        
        // If we have a fully qualified type, try to resolve it and generate deep mock data
        if (!param.qualifiedType.isNullOrBlank() && param.qualifiedType != "java.lang.Object") {
             // 检查是否是集合类型（如 List<User>）
             val collectionValue = generateValueForCollectionType(param.qualifiedType)
             if (collectionValue != null) {
                 return collectionValue
             }
             
             // 非集合类型的普通类处理
             val psiClass = JavaPsiFacade.getInstance(project).findClass(
                 param.qualifiedType,
                 GlobalSearchScope.allScope(project)
             )
             
             if (psiClass != null) {
                 val mockData = generateFromPsiClass(psiClass, 0)
                 if (mockData != null) {
                     return try {
                         objectMapper.writeValueAsString(mockData) ?: "{}"
                     } catch (e: Exception) {
                         "{}"
                     }
                 }
             }
        }
        
        return when (param.dataType?.lowercase()) {
            "object" -> "{}"
            "array" -> "[]"
            else -> "{}"
        }
    }
    
    private fun generateFromPsiClass(psiClass: PsiClass, depth: Int): Any? {
        // 深度检查
        if (depth > config.maxDepth) {
            LoggingUtils.logWarning("Max depth ${config.maxDepth} reached for class: ${psiClass.qualifiedName}")
            return null
        }

        val qualifiedName = psiClass.qualifiedName ?: return null

        // 检查循环引用
        val stack = generationStack.get()
        if (qualifiedName in stack) {
            LoggingUtils.logWarning("Circular reference detected: $qualifiedName")
            return null
        }

        // 检查缓存
        objectCache?.get(qualifiedName)?.let {
            LoggingUtils.logInfo("Using cached data for: $qualifiedName")
            return it
        }

        try {
            // 加入循环检测栈
            stack.add(qualifiedName)

            val result = mutableMapOf<String, Any?>()

            // Iterate over fields including inherited ones
            val fields = psiClass.allFields

            for (field in fields) {
                if (field.hasModifierProperty(PsiModifier.STATIC) ||
                    field.hasModifierProperty(PsiModifier.TRANSIENT)) {
                    continue
                }

                val fieldName = field.name
                val type = field.type

                // 优先使用智能字段名识别
                val value = if (config.enableSmartFieldName) {
                    smartFieldProvider.generateByFieldName(fieldName, type.presentableText)
                        ?: generateValueForType(type, depth + 1, fieldName)
                } else {
                    generateValueForType(type, depth + 1, fieldName)
                }

                result[fieldName] = value
            }

            // 存入缓存
            objectCache?.put(qualifiedName, result)
            return result
        } finally {
            // 从循环检测栈移除
            stack.remove(qualifiedName)
        }
    }

    private fun generateValueForType(type: PsiType, depth: Int, fieldName: String? = null): Any? {
         val canonicalText = type.canonicalText

         // Handle basic types
         when (canonicalText) {
             "java.lang.String", "String" -> {
                 // 优先使用智能字段名识别
                 if (fieldName != null && config.enableSmartFieldName) {
                     smartFieldProvider.generateByFieldName(fieldName, "String")?.let { return it }
                 }
                 return faker.lorem().word()
             }
             "java.lang.Integer", "int", "Integer" -> return random.nextInt(
                 config.defaultMinValue.toInt(),
                 config.defaultMaxValue.toInt()
             )
             "java.lang.Long", "long", "Long" -> return random.nextLong(
                 config.defaultMinValue.toLong(),
                 config.defaultMaxValue.toLong()
             )
             "java.lang.Boolean", "boolean", "Boolean" -> return random.nextBoolean()
             "java.lang.Double", "double", "Double" -> return random.nextDouble(
                 config.defaultMinValue,
                 config.defaultMaxValue
             )
             "java.util.Date", "java.sql.Date", "Date" -> return LocalDate.now().toString()
             "java.math.BigDecimal" -> return random.nextDouble(
                 config.defaultMinValue,
                 config.defaultMaxValue * 10
             )
         }

         // Handle Lists/Collections
         if (type is PsiClassType) {
             val resolveResult = type.resolve()
             if (resolveResult != null) {
                 val qName = resolveResult.qualifiedName
                 if (qName != null) {
                      if (qName.startsWith("java.util.List") || qName.startsWith("java.util.Set") || qName.startsWith("java.util.Collection")) {
                          // Try to get generic type
                          val parameters = type.parameters
                          if (parameters.isNotEmpty()) {
                              val genericType = parameters[0]
                              val items = mutableListOf<Any?>()
                              // 使用配置的集合大小
                              for (i in 0 until config.defaultCollectionSize) {
                                  val item = generateValueForType(genericType, depth + 1, null)
                                  if (item != null) {
                                      items.add(item)
                                  }
                              }
                              return items
                          }
                          return emptyList<Any>()
                      }
                      if (qName.startsWith("java.util.Map")) {
                          return mapOf("key" to "value")
                      }
                     // Recursive POJO
                     if (!qName.startsWith("java.")) {
                         return generateFromPsiClass(resolveResult, depth + 1)
                     }
                 }


             }
         }

         // Handle Arrays
         if (type is PsiArrayType) {
             val items = mutableListOf<Any?>()
             for (i in 0 until config.defaultCollectionSize) {
                 val item = generateValueForType(type.componentType, depth + 1, null)
                 if (item != null) {
                     items.add(item)
                 }
             }
             return items
         }

         return null
    }

    
    override fun generateValueForSchema(schema: SchemaInfo): Any? {
        // 优先使用示例值
        if (schema.example != null) {
            return schema.example
        }
        
        // 处理枚举
        if (schema.enum != null && schema.enum.isNotEmpty()) {
            return schema.enum.random()
        }
        
        // 根据类型生成
        return when (schema.type.lowercase()) {
            "string" -> generateStringValue(schema)
            "integer", "int" -> generateIntegerValue(schema)
            "number" -> generateNumberValue(schema)
            "boolean" -> random.nextBoolean()
            "object" -> generateObjectValue(schema)
            "array" -> generateArrayValue(schema)
            else -> null
        }
    }
    
    private fun generateStringValue(schema: SchemaInfo): String {
        // 优先级1: 使用example
        schema.example?.toString()?.let { return it }

        // 优先级2: 使用pattern生成（新增）
        if (config.enablePatternGeneration && schema.pattern != null) {
            patternGenerator.generateWithRetry(schema.pattern!!)?.let { return it }
        }

        // 优先级3: 根据format生成
        val value = when (schema.format?.lowercase()) {
            "email" -> faker.internet().emailAddress()
            "uuid" -> UUID.randomUUID().toString()
            "uri", "url" -> faker.internet().url()
            "date" -> LocalDate.now().format(DateTimeFormatter.ISO_DATE)
            "date-time" -> LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            "ipv4" -> faker.internet().ipV4Address()
            "ipv6" -> faker.internet().ipV6Address()
            "hostname" -> faker.internet().domainName()
            "password" -> faker.credentials().password(8, 16, true, true)
            else -> {
                // 优先级4: 根据约束生成
                val minLength = schema.minLength ?: config.defaultMinLength
                val maxLength = schema.maxLength ?: config.defaultMaxLength
                val length = random.nextInt(minLength, maxLength.coerceAtLeast(minLength + 1))
                faker.lorem().characters(length)
            }
        }

        return value
    }
    
    private fun generateIntegerValue(schema: SchemaInfo): Int {
        val min = schema.minimum?.toInt() ?: 1
        val max = schema.maximum?.toInt() ?: 100
        
        // 处理 exclusive
        val actualMin = if (schema.exclusiveMinimum) min + 1 else min
        val actualMax = if (schema.exclusiveMaximum) max - 1 else max
        
        var value = random.nextInt(actualMin, actualMax + 1)
        
        // 处理 multipleOf
        if (schema.multipleOf != null) {
            val multiple = schema.multipleOf.toInt()
            value = (value / multiple) * multiple
        }
        
        return value
    }
    
    private fun generateNumberValue(schema: SchemaInfo): Double {
        val min = schema.minimum?.toDouble() ?: 1.0
        val max = schema.maximum?.toDouble() ?: 100.0
        
        // 处理 exclusive
        val actualMin = if (schema.exclusiveMinimum) min + 0.01 else min
        val actualMax = if (schema.exclusiveMaximum) max - 0.01 else max
        
        var value = random.nextDouble(actualMin, actualMax)
        
        // 处理 multipleOf
        if (schema.multipleOf != null) {
            val multiple = schema.multipleOf.toDouble()
            value = (value / multiple).toInt() * multiple
        }
        
        return value
    }
    
    private fun generateObjectValue(schema: SchemaInfo): Map<String, Any?> {
        val properties = schema.properties ?: return emptyMap()
        val required = schema.requiredProperties ?: emptyList()
        
        return generateObjectData(properties, required)
    }
    
    private fun generateArrayValue(schema: SchemaInfo): List<Any?> {
        val itemSchema = schema.items ?: SchemaInfo(type = "string")
        val minItems = schema.minItems
        val maxItems = schema.maxItems

        return generateArrayData(itemSchema, minItems, maxItems, schema.uniqueItems)
    }

    
    override fun generateFormattedValue(
        type: String,
        format: String?,
        constraints: Map<String, Any>
    ): Any? {
        val schema = SchemaInfo(
            type = type,
            format = format,
            minimum = constraints["minimum"] as? Number,
            maximum = constraints["maximum"] as? Number,
            minLength = constraints["minLength"] as? Int,
            maxLength = constraints["maxLength"] as? Int,
            pattern = constraints["pattern"] as? String
        )
        
        return generateValueForSchema(schema)
    }
    
    override fun generateObjectData(
        properties: Map<String, SchemaInfo>,
        required: List<String>
    ): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        
        // 只生成必需属性
        properties.forEach { (name, propSchema) ->
            if (required.contains(name)) {
                result[name] = generateValueForSchema(propSchema)
            }
        }
        
        return result
    }
    
    override fun generateArrayData(
        itemSchema: SchemaInfo,
        minItems: Int?,
        maxItems: Int?,
        uniqueItems: Boolean
    ): List<Any?> {
        // 使用配置的数组大小范围
        val min = minItems ?: config.minArraySize
        val max = maxItems ?: config.maxArraySize

        val count = random.nextInt(min, max.coerceAtLeast(min) + 1)

        return if (uniqueItems) {
            // 生成唯一元素
            val uniqueSet = mutableSetOf<Any?>()
            var attempts = 0
            val maxAttempts = count * 10  // 防止无限循环

            while (uniqueSet.size < count && attempts < maxAttempts) {
                val item = generateValueForSchema(itemSchema)
                uniqueSet.add(item)
                attempts++
            }

            if (uniqueSet.size < count) {
                LoggingUtils.logWarning("Could not generate $count unique items, only got ${uniqueSet.size}")
            }

            uniqueSet.toList()
        } else {
            // 生成普通数组
            (0 until count).map {
                generateValueForSchema(itemSchema)
            }
        }
    }
}
