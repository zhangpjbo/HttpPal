package com.httppal.service

import com.github.curiousoddman.rgxgen.RgxGen
import com.httppal.util.LoggingUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * 正则表达式数据生成器
 * 使用rgxgen库根据正则表达式生成符合pattern的字符串
 */
class PatternGenerator {

    private val cache = ConcurrentHashMap<String, RgxGen>()

    /**
     * 根据正则表达式生成匹配的字符串
     * @param pattern 正则表达式
     * @return 生成的字符串，失败返回null
     */
    fun generate(pattern: String): String? {
        return try {
            val generator = cache.getOrPut(pattern) {
                RgxGen.parse(pattern)
            }
            generator.generate()
        } catch (e: Exception) {
            LoggingUtils.logWarning("Failed to generate value for pattern: $pattern, error: ${e.message}")
            null
        }
    }

    /**
     * 验证字符串是否匹配模式
     * @param value 待验证的字符串
     * @param pattern 正则表达式
     * @return 是否匹配
     */
    fun matches(value: String, pattern: String): Boolean {
        return try {
            value.matches(Regex(pattern))
        } catch (e: Exception) {
            LoggingUtils.logWarning("Invalid regex pattern: $pattern, error: ${e.message}")
            false
        }
    }

    /**
     * 生成符合pattern的值，最多尝试maxAttempts次
     * @param pattern 正则表达式
     * @param maxAttempts 最大尝试次数
     * @return 生成的字符串，失败返回null
     */
    fun generateWithRetry(pattern: String, maxAttempts: Int = 5): String? {
        repeat(maxAttempts) {
            generate(pattern)?.let { value ->
                if (matches(value, pattern)) {
                    return value
                }
            }
        }
        LoggingUtils.logWarning("Failed to generate valid value after $maxAttempts attempts for pattern: $pattern")
        return null
    }

    /**
     * 清空缓存
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Int = cache.size
}
