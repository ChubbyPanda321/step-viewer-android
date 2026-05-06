package com.stepviewer.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Manages app locale override, persisting to SharedPreferences
 * so it can be read synchronously in [android.app.Activity.attachBaseContext].
 */
object LocaleHelper {

    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LANG = "app_language"
    const val DEFAULT_LANG = "zh"

    fun getLanguageCode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANG, DEFAULT_LANG) ?: DEFAULT_LANG
    }

    fun setLanguageCode(context: Context, code: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, code)
            .apply()
    }

    /**
     * Wrap the base context with the configured locale so all resources
     * resolve to the correct language.
     */
    fun wrapWithLocale(base: Context): Context {
        val langCode = getLanguageCode(base)
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
