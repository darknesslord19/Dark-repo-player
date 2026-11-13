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
    /** DNS over HTTPS sağlayıcısı (DnsProvider adı). */
    var dns by mutableStateOf("CLOUDFLARE")
        private set
    /** Tercih edilen altyazı dili ("" = kapalı). */
    var subLang by mutableStateOf("tr")
        private set
    /** Tercih edilen ses dili ("" = otomatik). */
    var audioLang by mutableStateOf("tr")
        private set
    /** Varsayılan ekran oranı: 0 sığdır, 4 yakınlaştır, 3 uzat (AspectRatioFrameLayout değerleri). */
    var resizeMode by mutableStateOf(0)
        private set
    /** IPTV kimliği: AUTO (sırayla dene), EXO, VLC, BROWSER, OKHTTP. */
    var iptvUa by mutableStateOf("AUTO")
        private set

    fun uaFor(key: String): String = when (key) {
        "VLC" -> "VLC/3.0.20 LibVLC/3.0.20"
        "BROWSER" -> Net.USER_AGENT
        "OKHTTP" -> "okhttp/4.12.0"
        else -> "StreamBox/1.0 (Linux;Android ${android.os.Build.VERSION.RELEASE}) ExoPlayerLib/1.8.0"
    }

    /** Denenecek IPTV kimlikleri: seçilen önce, diğerleri yedek. */
    val iptvUserAgents: List<String>
        get() {
            val order = listOf("EXO", "VLC", "BROWSER", "OKHTTP")
            val first = if (iptvUa in order) listOf(iptvUa) else emptyList()
            return (first + order).distinct().map { uaFor(it) }
        }

    val iptvUserAgent: String get() = iptvUserAgents.first()

    /** Ana sayfada seçili kaynak (kaynak adı ya da "__tmdb__"). */
    var homeSource by mutableStateOf("__tmdb__")
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        tmdbKey = prefs.getString("tmdbKey", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_TMDB_KEY
        language = prefs.getString("language", "tr-TR") ?: "tr-TR"
        homeSource = prefs.getString("homeSource", "__tmdb__") ?: "__tmdb__"
        dns = prefs.getString("dns", "CLOUDFLARE") ?: "CLOUDFLARE"
        subLang = prefs.getString("subLang", "tr") ?: "tr"
        audioLang = prefs.getString("audioLang", "tr") ?: "tr"
        resizeMode = prefs.getInt("resizeMode", 0)
        iptvUa = prefs.getString("iptvUa2", "AUTO") ?: "AUTO"
    }

    fun updateTmdbKey(v: String) {
        tmdbKey = v.trim().ifBlank { DEFAULT_TMDB_KEY }
        prefs.edit().putString("tmdbKey", tmdbKey).apply()
    }

    fun updateSubLang(v: String) { subLang = v; prefs.edit().putString("subLang", v).apply() }
    fun updateAudioLang(v: String) { audioLang = v; prefs.edit().putString("audioLang", v).apply() }
    fun updateResizeMode(v: Int) { resizeMode = v; prefs.edit().putInt("resizeMode", v).apply() }
    fun updateIptvUa(v: String) { iptvUa = v; prefs.edit().putString("iptvUa2", v).apply() }

    fun updateDns(v: String) {
        dns = v
        prefs.edit().putString("dns", v).apply()
        Net.rebuild()
        com.streambox.app.plugins.CsBridge.init(appContext)
    }

    fun updateHomeSource(v: String) {
        homeSource = v
        prefs.edit().putString("homeSource", v).apply()
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
