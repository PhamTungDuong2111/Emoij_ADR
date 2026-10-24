package com.randomvisual.emojimask

import android.app.Application
import android.content.Context
import com.randomvisual.emojimask.util.LanguageManager

class EmojiMaskApplication : Application() {

    override fun attachBaseContext(base: Context) {
        val updatedContext = LanguageManager.applySavedLocale(base)
        super.attachBaseContext(updatedContext)
    }

    override fun onCreate() {
        super.onCreate()
    }
}
