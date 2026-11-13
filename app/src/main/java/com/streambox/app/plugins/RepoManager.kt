package com.streambox.app.plugins

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.streambox.app.AppLog
import com.streambox.app.Net
import com.streambox.app.onMain
import org.json.JSONArray
import org.json.JSONObject

/** repo.json içeriği (CloudStream biçimi). */
data class Repo(
    val url: String,
    val name: String,
    val description: String = "",
    val iconUrl: String? = null,
    val pluginLists: List<String> = emptyList(),
)

/** plugins.json içindeki tek bir plugin kaydı (CloudStream biçimi). */
data class RepoPlugin(
    val url: String,
    val name: String,
    val internalName: String,
    val version: Int,
    val status: Int,          // 0 kapalı, 1 çalışıyor, 2 yavaş, 3 beta
    val description: String?,
    val authors: List<String>,
    val language: String?,
    val tvTypes: List<String>,
    val iconUrl: String?,
    val fileSize: Long?,
    val repoUrl: String,
)

object RepoManager {
    private const val PREFS = "repos"
    val repos = mutableStateListOf<Repo>()
    /** Repo adresi → eklenti listesi (ekranlar arasında paylaşılır). */
    val pluginCache = androidx.compose.runtime.mutableStateMapOf<String, List<RepoPlugin>>()

    suspend fun pluginsOf(repo: Repo, refresh: Boolean = false): List<RepoPlugin> {
        if (!refresh) pluginCache[repo.url]?.let { return it }
        val list = fetchPlugins(repo)
        onMain { pluginCache[repo.url] = list }
        return list
    }

    fun init(context: Context) {
        val raw = context.getSharedPreferences(PREFS, 0).getString("list", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Repo(
                url = o.getString("url"),
                name = o.optString("name", o.getString("url")),
                description = o.optString("description"),
                iconUrl = o.optStringOrNull("iconUrl"),
                pluginLists = o.optJSONArray("pluginLists").strings(),
            )
        }
        repos.clear(); repos.addAll(list)
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        repos.forEach { r ->
            arr.put(JSONObject().apply {
                put("url", r.url); put("name", r.name); put("description", r.description)
                put("iconUrl", r.iconUrl); put("pluginLists", JSONArray(r.pluginLists))
            })
        }
        context.getSharedPreferences(PREFS, 0).edit().putString("list", arr.toString()).apply()
    }

    /**
     * Kullanıcının yazdığını gerçek bir adrese çevirir:
     * - cloudstreamrepo://… → https://…
     * - github.com/.../blob/... → raw.githubusercontent.com/...
     * - nokta/eğik çizgi içermeyen kısa kod → https://cutt.ly/<kod> (CloudStream kısa kodları)
     */
    fun normalize(input: String): String {
        var s = input.trim()
        if (s.startsWith("cloudstreamrepo://")) s = "https://" + s.removePrefix("cloudstreamrepo://")
        if (!s.contains('.') && !s.contains('/')) return "https://cutt.ly/$s"
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        val gh = Regex("^https://github\\.com/([^/]+)/([^/]+)/blob/(.+)$").find(s)
        if (gh != null) {
            val (u, r, rest) = gh.destructured
            s = "https://raw.githubusercontent.com/$u/$r/$rest"
        }
        return s
    }

    /** Adresi indirir: repo.json ise repo, doğrudan plugins.json ise tek listeli repo üretir. */
    suspend fun fetchRepo(input: String): Repo {
        val url = normalize(input)
        val text = Net.getText(url).trim()
        return if (text.startsWith("[")) {
            Repo(url = url, name = url.substringAfterLast('/'), pluginLists = listOf(url))
        } else {
            val o = JSONObject(text)
            val lists = o.optJSONArray("pluginLists").strings()
            if (lists.isEmpty()) throw IllegalArgumentException("Bu bir repo.json değil (pluginLists yok)")
            Repo(
                url = url,
                name = o.optString("name", url),
                description = o.optString("description"),
                iconUrl = o.optStringOrNull("iconUrl"),
                pluginLists = lists,
            )
        }
    }

    suspend fun addRepo(context: Context, input: String): Repo {
        val repo = fetchRepo(input)
        onMain {
            repos.removeAll { it.url == repo.url }
            repos.add(repo)
            save(context)
        }
        AppLog.i("Repo eklendi: ${repo.name} (${repo.pluginLists.size} liste)")
        return repo
    }

    fun removeRepo(context: Context, repo: Repo) {
        repos.remove(repo)
        save(context)
    }

    suspend fun fetchPlugins(repo: Repo): List<RepoPlugin> {
        val out = mutableListOf<RepoPlugin>()
        for (listUrl in repo.pluginLists) {
            try {
                val arr = JSONArray(Net.getText(listUrl))
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out += RepoPlugin(
                        url = o.getString("url"),
                        name = o.optString("name", o.optString("internalName")),
                        internalName = o.optString("internalName", o.optString("name")),
                        version = o.optInt("version", 0),
                        status = o.optInt("status", 1),
                        description = o.optStringOrNull("description"),
                        authors = o.optJSONArray("authors").strings(),
                        language = o.optStringOrNull("language"),
                        tvTypes = o.optJSONArray("tvTypes").strings(),
                        iconUrl = o.optStringOrNull("iconUrl")
                            ?.replace("%size%", "64")?.replace("%exact_size%", "64"),
                        fileSize = if (o.has("fileSize")) o.optLong("fileSize") else null,
                        repoUrl = repo.url,
                    )
                }
            } catch (e: Exception) {
                AppLog.e("Plugin listesi okunamadı: $listUrl", e)
            }
        }
        return out.sortedBy { it.name.lowercase() }
    }
}

internal fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
