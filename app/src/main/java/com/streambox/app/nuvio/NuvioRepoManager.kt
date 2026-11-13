package com.streambox.app.nuvio

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.streambox.app.AppLog
import com.streambox.app.Net
import com.streambox.app.onMain
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL

data class NuvioRepo(val url: String, val name: String, val description: String = "", val author: String = "")

data class NuvioScraper(
    val repoUrl: String,
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val fileUrl: String,
    val supportedTypes: List<String>,
    val logo: String?,
    val language: String?,
    val enabled: Boolean,
) {
    val key get() = (repoUrl.hashCode().toUInt().toString(16)) + "_" + id
}

object NuvioRepoManager {
    private const val PREFS = "nuvio"
    val repos = mutableStateListOf<NuvioRepo>()
    val scrapers = mutableStateListOf<NuvioScraper>()
    private lateinit var appContext: Context

    private fun dir() = File(appContext.filesDir, "nuvio").apply { mkdirs() }
    private fun codeFile(s: NuvioScraper) = File(dir(), s.key.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".js")

    fun init(context: Context) {
        appContext = context.applicationContext
        val p = appContext.getSharedPreferences(PREFS, 0)
        val r = JSONArray(p.getString("repos", "[]"))
        repos.clear()
        for (i in 0 until r.length()) r.getJSONObject(i).let {
            repos += NuvioRepo(it.getString("url"), it.optString("name"), it.optString("description"), it.optString("author"))
        }
        val s = JSONArray(p.getString("scrapers", "[]"))
        scrapers.clear()
        for (i in 0 until s.length()) s.getJSONObject(i).let {
            scrapers += NuvioScraper(
                repoUrl = it.getString("repoUrl"), id = it.getString("id"), name = it.optString("name"),
                description = it.optString("description"), version = it.optString("version"),
                fileUrl = it.getString("fileUrl"),
                supportedTypes = it.optJSONArray("supportedTypes").toList(),
                logo = it.optString("logo").ifBlank { null }, language = it.optString("language").ifBlank { null },
                enabled = it.optBoolean("enabled", true),
            )
        }
    }

    private fun save() {
        val r = JSONArray()
        repos.forEach { r.put(JSONObject().put("url", it.url).put("name", it.name).put("description", it.description).put("author", it.author)) }
        val s = JSONArray()
        scrapers.forEach {
            s.put(JSONObject().put("repoUrl", it.repoUrl).put("id", it.id).put("name", it.name)
                .put("description", it.description).put("version", it.version).put("fileUrl", it.fileUrl)
                .put("supportedTypes", JSONArray(it.supportedTypes)).put("logo", it.logo ?: "")
                .put("language", it.language ?: "").put("enabled", it.enabled))
        }
        appContext.getSharedPreferences(PREFS, 0).edit().putString("repos", r.toString()).putString("scrapers", s.toString()).apply()
    }

    /** owner/repo, github sayfası, raw adres ya da doğrudan manifest.json kabul eder. */
    fun normalize(input: String): String {
        var s = input.trim().removeSuffix("/")
        if (Regex("^[\\w.-]+/[\\w.-]+$").matches(s)) return "https://raw.githubusercontent.com/$s/refs/heads/main/manifest.json"
        if (!s.startsWith("http")) s = "https://$s"
        Regex("^https://github\\.com/([^/]+)/([^/]+)(?:/(?:tree|blob)/([^/]+)(/.*)?)?$").find(s)?.let { m ->
            val (u, r, branch, rest) = m.destructured
            val path = if (rest.endsWith(".json")) rest else "$rest/manifest.json"
            return "https://raw.githubusercontent.com/$u/$r/refs/heads/${branch.ifBlank { "main" }}$path"
        }
        if (!s.endsWith(".json")) s += "/manifest.json"
        return s
    }

    suspend fun addRepo(input: String): NuvioRepo {
        val url = normalize(input)
        val text = Net.getText(url).trim()
        if (!text.startsWith("{")) throw IllegalArgumentException("Bu bir Nuvio manifest.json değil")
        val o = JSONObject(text)
        val arr = o.optJSONArray("scrapers") ?: throw IllegalArgumentException("manifest.json içinde scrapers listesi yok")
        val repo = NuvioRepo(url, o.optString("name", url), o.optString("description"), o.optString("author"))
        val old = scrapers.filter { it.repoUrl == url }.associateBy { it.id }
        val list = mutableListOf<NuvioScraper>()
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val id = s.optString("id")
            val file = s.optString("filename")
            if (id.isBlank() || file.isBlank()) continue
            val types = s.optJSONArray("supportedTypes").toList().ifEmpty { listOf("movie", "tv") }
            val disabled = s.optJSONArray("disabledPlatforms").toList().map { it.lowercase() }
            if ("android" in disabled) continue
            list += NuvioScraper(
                repoUrl = url, id = id, name = s.optString("name", id),
                description = s.optString("description"), version = s.optString("version"),
                fileUrl = URL(URL(url), file).toString(),
                supportedTypes = types, logo = s.optString("logo").ifBlank { null },
                language = s.opt("contentLanguage")?.toString()?.trim('[', ']')?.replace("\"", ""),
                enabled = old[id]?.enabled ?: s.optBoolean("enabled", true),
            )
        }
        onMain {
            repos.removeAll { it.url == url }; repos += repo
            scrapers.removeAll { it.repoUrl == url }; scrapers += list
            save()
        }
        // eski kodları sil, yenileri gerektiğinde indirilir
        list.forEach { codeFile(it).delete() }
        AppLog.i("Nuvio repo eklendi: ${repo.name} (${list.size} eklenti)")
        return repo
    }

    fun removeRepo(repo: NuvioRepo) {
        scrapers.filter { it.repoUrl == repo.url }.forEach { codeFile(it).delete() }
        scrapers.removeAll { it.repoUrl == repo.url }
        repos.remove(repo)
        save()
    }

    fun setEnabled(s: NuvioScraper, enabled: Boolean) {
        val i = scrapers.indexOf(s)
        if (i >= 0) { scrapers[i] = s.copy(enabled = enabled); save() }
    }

    /** Bir repodaki (ya da [repoUrl] null ise tüm) eklentileri açar/kapatır. */
    fun setAllEnabled(repoUrl: String?, enabled: Boolean) {
        for (i in scrapers.indices) {
            val s = scrapers[i]
            if (repoUrl == null || s.repoUrl == repoUrl) scrapers[i] = s.copy(enabled = enabled)
        }
        save()
    }

    private suspend fun code(s: NuvioScraper): String {
        val f = codeFile(s)
        if (f.exists() && f.length() > 0) return f.readText()
        val text = Net.getText(s.fileUrl)
        f.writeText(text)
        return text
    }

    /**
     * Etkin tüm eklentileri paralel çalıştırır. [onResult] her eklenti bittiğinde çağrılır
     * (ekranda sonuçlar geldikçe görünsün diye).
     */
    suspend fun findStreams(
        context: Context,
        tmdbId: Int,
        type: String,
        season: Int?,
        episode: Int?,
        onResult: (NuvioScraper, List<NuvioStream>?, String?) -> Unit,
    ) = coroutineScope {
        val active = scrapers.filter { it.enabled && type in it.supportedTypes }
        active.map { s ->
            async {
                try {
                    val code = code(s)
                    val res = withTimeoutOrNull(75_000) {
                        NuvioRuntime.getStreams(context, s, code, tmdbId, type, season, episode)
                    }
                    onMain { onResult(s, res ?: emptyList(), if (res == null) "zaman aşımı" else null) }
                } catch (e: Exception) {
                    AppLog.e("Nuvio ${s.name}: ${e.message}")
                    onMain { onResult(s, null, e.message) }
                }
            }
        }.forEach { it.await() }
    }
}

private fun JSONArray?.toList(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
