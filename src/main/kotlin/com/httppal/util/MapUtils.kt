package com.httppal.util

object MapUtils {


    fun <K> safeMapOf(vararg pairs: Pair<K, *>): Map<K, Any> {
        // 使用 LinkedHashMap 手动构建 Map
        val resultMap = LinkedHashMap<K, Any>()

        // 遍历所有键值对
        for ((key, value) in pairs) {
            // 只有当 value 不是 null 时才放入结果 Map
            if (value != null) {
                // value 的类型是 Any，可以直接放入声明为 Any 的 Map 中
                resultMap[key] = value
            }
        }

        // LinkedHashMap 本身就是 Map 接口的实现
        return resultMap
    }
}