package com.streambox.app.iptv

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import com.streambox.app.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String,
    val headers: Map<String, String>,
)

data class Playlist(val id: String, val name: String, val url: String?, val onHome: Boolean = true) {
    val kind: String get() = when {
        url == null -> "Dosya"
        url.contains("get.php?username=") -> "Xtream"
        else -> "M3U"
    }
}

/** Liste özeti: kanal ve kategori sayısı. */
data class PlaylistStats(val channels: Int, val groups: Int)

object M3u {
    private val ATTR = Regex("([\\w-]+)=\"([^\"]*)\"")

    /** M3U / M3U8 listesini kanallara ayırır; group-title ve #EXTGRP kategorileri okunur. */
    fun parse(text: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var name: String? = null
        var attrs = emptyMap<String, String>()
        var group: String? = null
        val headers = mutableMapOf<String, String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> {}
                line.startsWith("#EXTINF", true) -> {
                    val body = line.substringAfter(':')
                    var inQ = false
                    var cut = -1
                    for (i in body.indices) {
                        if (body[i] == '"') inQ = !inQ else if (body[i] == ',' && !inQ) { cut = i; break }
                    }
                    val meta = if (cut >= 0) body.substring(0, cut) else body
                    attrs = ATTR.findAll(meta).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name = if (cut >= 0) body.substring(cut + 1).trim() else null
                    group = null
                }
                line.startsWith("#EXTGRP:", true) -> group = line.substringAfter(':').trim()
                line.startsWith("#EXTVLCOPT:", true) -> {
                    val opt = line.substringAfter(':')
                    val k = opt.substringBefore('=').lowercase(); val v = opt.substringAfter('=')
                    when (k) {
                        "http-user-agent" -> headers["User-Agent"] = v
                        "http-referrer", "http-referer" -> headers["Referer"] = v
                        "http-origin" -> headers["Origin"] = v
                    }
                }
                line.startsWith("#") -> {}
                else -> {
                    var url = line
                    // "url|User-Agent=...&Referer=..." biçimi (Kodi)
                    if (url.contains('|')) {
                        url.substringAfter('|').split('&').forEach { p ->
                            val k = p.substringBefore('='); val v = Uri.decode(p.substringAfter('=', ""))
                            if (k.isNotBlank() && v.isNotBlank()) headers[k] = v
                        }
                        url = url.substringBefore('|')
                    }
                    attrs["user-agent"]?.let { headers["User-Agent"] = it }
                    attrs["http-referrer"]?.let { headers["Referer"] = it }
                    out += Channel(
                        name = name?.ifBlank { null } ?: attrs["tvg-name"] ?: url.substringAfterLast('/'),
                        url = url,
                        logo = attrs["tvg-logo"]?.ifBlank { null },
                        group = attrs["group-title"]?.ifBlank { null } ?: group ?: "Diğer",
                        headers = headers.toMap(),
                    )
                    name = null; attrs = emptyMap(); group = null; headers.clear()
                }
            }
        }
        return out
    }
}

object IptvStore {
    val playlists = mutableStateListOf<Playlist>()
    /** Favori kanallar (adres). */
    val favorites = mutableStateListOf<String>()
    /** Son izlenen kanallar (adres), en yenisi başta. */
    val recents = mutableStateListOf<String>()
    private lateinit var appContext: Context
    private fun dir() = File(appContext.filesDir, "iptv").apply { mkdirs() }

    fun init(context: Context) {
        appContext = context.applicationContext
        val arr = JSONArray(appContext.getSharedPreferences("iptv", 0).getString("list", "[]"))
        playlists.clear()
        for (i in 0 until arr.length()) arr.getJSONObject(i).let {
            playlists += Playlist(it.getString("id"), it.getString("name"), it.optString("url").ifBlank { null }, it.optBoolean("onHome", true))
        }
        val prefs = appContext.getSharedPreferences("iptv", 0)
        favorites.clear(); favorites.addAll(JSONArray(prefs.getString("favorites", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } })
        recents.clear(); recents.addAll(JSONArray(prefs.getString("recents", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } })
    }

    private fun saveLists() {
        appContext.getSharedPreferences("iptv", 0).edit()
            .putString("favorites", JSONArray(favorites.toList()).toString())
            .putString("recents", JSONArray(recents.toList()).toString())
            .apply()
    }

    /** Favoriye ekler ya da çıkarır; yeni durumu döndürür. */
    fun toggleFavorite(url: String): Boolean {
        val added = if (url in favorites) { favorites.remove(url); false } else { favorites.add(0, url); true }
        saveLists()
        return added
    }

    /** İzlenen kanalı "Devam et" listesinin başına koyar. */
    fun markWatched(url: String) {
        recents.remove(url)
        recents.add(0, url)
        while (recents.size > 60) recents.removeAt(recents.lastIndex)
        saveLists()
    }

    private fun save() {
        val arr = JSONArray()
        playlists.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url ?: "").put("onHome", it.onHome)) }
        appContext.getSharedPreferences("iptv", 0).edit().putString("list", arr.toString()).apply()
    }

    suspend fun addUrl(name: String, url: String): Playlist {
        val text = fetchWithUas(url.trim())
        if (M3u.parse(text).isEmpty()) throw IllegalArgumentException("Listede kanal bulunamadı")
        val p = Playlist(System.currentTimeMillis().toString(36), name.ifBlank { Uri.parse(url).host ?: "Liste" }, url.trim())
        withContext(Dispatchers.IO) { File(dir(), p.id + ".m3u").writeText(text) }
        withContext(Dispatchers.Main) { playlists += p; save() }
        return p
    }

    suspend fun addFile(context: Context, uri: Uri): Playlist {
        val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.bufferedReader().readText() }
        if (M3u.parse(text).isEmpty()) throw IllegalArgumentException("Listede kanal bulunamadı")
        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Liste"
        val p = Playlist(System.currentTimeMillis().toString(36), name, null)
        withContext(Dispatchers.IO) { File(dir(), p.id + ".m3u").writeText(text) }
        withContext(Dispatchers.Main) { playlists += p; save() }
        return p
    }

    /** Kanalları getirir; adresli listeler [refresh] ile yeniden indirilir. */
    suspend fun channels(p: Playlist, refresh: Boolean = false): List<Channel> = withContext(Dispatchers.IO) {
        val f = File(dir(), p.id + ".m3u")
        if (refresh) withContext(Dispatchers.Main) { stats.remove(p.id) }
        if ((refresh || !f.exists()) && p.url != null) runCatching {
            f.writeText(fetchWithUas(p.url))
        }
        if (f.exists()) M3u.parse(f.readText()) else emptyList()
    }

    /** Listeyi indirir; sunucu bir kimliği reddederse sıradakini dener. */
    private suspend fun fetchWithUas(url: String): String {
        var last: Exception? = null
        for (ua in com.streambox.app.Settings.iptvUserAgents) {
            try { return Net.getText(url, mapOf("User-Agent" to ua)) } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("Liste indirilemedi")
    }

    /** Hesap → kanal/kategori sayısı (ekranda gösterim için). */
    val stats = androidx.compose.runtime.mutableStateMapOf<String, PlaylistStats>()

    suspend fun loadStats(p: Playlist) {
        if (stats.containsKey(p.id)) return
        val list = channels(p)
        withContext(Dispatchers.Main) { stats[p.id] = PlaylistStats(list.size, list.map { it.group }.distinct().size) }
    }

    /** Ana sayfa kaynak seçicide gösterilsin mi? */
    fun setOnHome(p: Playlist, on: Boolean) {
        val i = playlists.indexOf(p)
        if (i >= 0) { playlists[i] = p.copy(onHome = on); save() }
    }

    fun rename(p: Playlist, name: String) {
        val i = playlists.indexOf(p)
        if (i >= 0 && name.isNotBlank()) { playlists[i] = p.copy(name = name.trim()); save() }
    }

    /** Xtream Codes bilgilerinden M3U adresi üretir. */
    fun xtreamUrl(server: String, user: String, pass: String): String {
        var base = server.trim().removeSuffix("/")
        if (!base.startsWith("http")) base = "http://$base"
        fun enc(v: String) = java.net.URLEncoder.encode(v.trim(), "UTF-8")
        return "$base/get.php?username=${enc(user)}&password=${enc(pass)}&type=m3u_plus&output=ts"
    }

    fun remove(p: Playlist) {
        stats.remove(p.id)
        File(dir(), p.id + ".m3u").delete()
        playlists.remove(p); save()
    }
}
