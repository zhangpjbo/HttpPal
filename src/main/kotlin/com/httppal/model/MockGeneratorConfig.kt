package com.httppal.model

import java.util.Locale

/**
 * Mock数据生成器配置
 * 用于控制数据生成的各项参数
 */
data class MockGeneratorConfig(
    /** 最大递归深度，防止无限递归 */
    val maxDepth: Int = 5,

    /** 集合/数组默认元素数量 */
    val defaultCollectionSize: Int = 2,

    /** 数组最小元素数量（无minItems约束时） */
    val minArraySize: Int = 1,

    /** 数组最大元素数量（无maxItems约束时） */
    val maxArraySize: Int = 5,

    /** 字符串默认最小长度 */
    val defaultMinLength: Int = 5,

    /** 字符串默认最大长度 */
    val defaultMaxLength: Int = 20,

    /** 数值默认最小值 */
    val defaultMinValue: Double = 1.0,

    /** 数值默认最大值 */
    val defaultMaxValue: Double = 100.0,

    /** Faker数据语言环境 */
    val locale: Locale = Locale.SIMPLIFIED_CHINESE,

    /** 是否启用智能字段名识别 */
    val enableSmartFieldName: Boolean = true,

    /** 是否启用缓存 */
    val enableCache: Boolean = true,

    /** 缓存最大条目数 */
    val maxCacheSize: Int = 100,

    /** 是否启用正则表达式生成 */
    val enablePatternGeneration: Boolean = true
) {
    companion object {
        /** 默认配置实例 */
        val DEFAULT = MockGeneratorConfig()

        /** 高性能配置（启用所有缓存，减少数据量） */
        val HIGH_PERFORMANCE = MockGeneratorConfig(
            enableCache = true,
            maxCacheSize = 200,
            defaultCollectionSize = 1,
            maxArraySize = 3,
            maxDepth = 3
        )

        /** 真实感配置（生成更多数据） */
        val REALISTIC = MockGeneratorConfig(
            defaultCollectionSize = 5,
            maxArraySize = 10,
            enableSmartFieldName = true,
            locale = Locale.SIMPLIFIED_CHINESE,
            maxDepth = 6
        )

        /** 英文数据配置 */
        val ENGLISH = MockGeneratorConfig(
            locale = Locale.ENGLISH,
            enableSmartFieldName = true
        )
    }
}
