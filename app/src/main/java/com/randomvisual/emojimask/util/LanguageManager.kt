package com.randomvisual.emojimask.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LanguageManager {
    private const val PREFS_NAME = "emoji_mask_prefs"
    private const val KEY_LANG = "key_app_language"

    const val LANG_VI = "vi"
    const val LANG_EN = "en"

    fun getSavedLanguage(context: Context): String {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_LANG, LANG_VI) ?: LANG_VI
    }

    fun saveLanguage(context: Context, langCode: String) {
        getPrefs(context).edit().putString(KEY_LANG, langCode).apply()
    }

    fun applySavedLocale(context: Context): Context {
        val langCode = getSavedLanguage(context)
        return updateLocale(context, langCode)
    }

    fun updateLocale(context: Context, langCode: String): Context {
        val locale = Locale(langCode)
        Locale.setDefault(locale)

        val res = context.resources
        val config = Configuration(res.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
            return context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
            return context
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
