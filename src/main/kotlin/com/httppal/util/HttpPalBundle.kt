package com.httppal.util

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.*

/**
 * Internationalization bundle for HttpPal plugin
 * Provides localized messages for UI components and user-facing text
 */
object HttpPalBundle : DynamicBundle("messages.HttpPalBundle") {
    
    /**
     * Get localized message by key
     */
    @Nls
    fun message(@PropertyKey(resourceBundle = "messages.HttpPalBundle") key: String, vararg params: Any): String {
        return getMessage(key, *params)
    }
    
    /**
     * Get localized message by key with default fallback
     */
    @Nls
    fun getMessageOrDefault(@PropertyKey(resourceBundle = "messages.HttpPalBundle") key: String, defaultValue: String, vararg params: Any): String {
        return try {
            getMessage(key, *params)
        } catch (e: Exception) {
            if (params.isNotEmpty()) {
                String.format(defaultValue, *params)
            } else {
                defaultValue
            }
        }
    }
    
    /**
     * Check if a key exists in the bundle
     */
    fun hasKey(@PropertyKey(resourceBundle = "messages.HttpPalBundle") key: String): Boolean {
        return try {
            getMessage(key)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get current locale
     */
    fun getCurrentLocale(): Locale {
        return Locale.getDefault()
    }
    
    /**
     * Get supported locales
     */
    fun getSupportedLocales(): List<Locale> {
        return listOf(
            Locale.ENGLISH,
            Locale.SIMPLIFIED_CHINESE,
            Locale.TRADITIONAL_CHINESE,
            Locale.JAPANESE
        )
    }
    
    /**
     * Check if current locale is supported
     */
    fun isCurrentLocaleSupported(): Boolean {
        val currentLocale = getCurrentLocale()
        return getSupportedLocales().any { 
            it.language == currentLocale.language && 
            (it.country.isEmpty() || it.country == currentLocale.country)
        }
    }
    
    /**
     * Get locale display name
     */
    fun getLocaleDisplayName(locale: Locale): String {
        return when {
            locale.language == "zh" && locale.country == "CN" -> "简体中文"
            locale.language == "zh" && locale.country == "TW" -> "繁體中文"
            locale.language == "ja" -> "日本語"
            else -> "English"
        }
    }
}

/**
 * Extension function for easy access to localized messages
 */
@Nls
fun String.localized(vararg params: Any): String {
    return HttpPalBundle.message(this, *params)
}

/**
 * Extension function for localized messages with default fallback
 */
@Nls
fun String.localizedOrDefault(defaultValue: String, vararg params: Any): String {
    return HttpPalBundle.getMessageOrDefault(this, defaultValue, *params)
}