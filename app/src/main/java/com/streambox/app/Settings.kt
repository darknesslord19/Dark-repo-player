package com.streambox.app

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Settings {
    /** Varsayılan TMDB anahtarı. Ayarlar ekranından değiştirilebilir. */
    const val DEFAULT_TMDB_KEY = "a60812355b356abc3af771f9d8476450"

    private lateinit var appContext: Context
    private val prefs get() = appContext.getSharedPreferences("settings", 0)

    var tmdbKey by mutableStateOf(DEFAULT_TMDB_KEY)
        private set
    var language by mutableStateOf("tr-TR")
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        tmdbKey = prefs.getString("tmdbKey", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_TMDB_KEY
        language = prefs.getString("language", "tr-TR") ?: "tr-TR"
    }

    fun updateTmdbKey(v: String) {
        tmdbKey = v.trim().ifBlank { DEFAULT_TMDB_KEY }
        prefs.edit().putString("tmdbKey", tmdbKey).apply()
    }

    fun updateLanguage(v: String) {
        language = v.trim().ifBlank { "tr-TR" }
        prefs.edit().putString("language", language).apply()
    }
}

/** Uygulamayı yeniden başlatır (yeni/güncellenen plugin'ler devreye girsin diye). */
fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
