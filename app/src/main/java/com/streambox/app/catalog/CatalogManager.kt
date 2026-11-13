package com.streambox.app.catalog

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.streambox.app.AppLog
import com.streambox.app.Net
import com.streambox.app.Tmdb
import com.streambox.app.TmdbItem
import com.streambox.app.onMain
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Stremio katalog eklentisi içindeki tek bir katalog. */
data class CatalogDef(val type: String, val id: String, val name: String, val hasSearch: Boolean, val genres: List<String>)

/** Stremio katalog eklentisi (manifest.json: catalogs + resources). */
data class CatalogAddon(val url: String, val name: String, val logo: String?, val catalogs: List<CatalogDef>)

/** Katalogdan gelen içerik (id: "tt…" ya da "tmdb:…"). */
data class CatalogItem(
    val id: String, val type: String, val name: String,
    val poster: String?, val background: String?, val description: String?, val year: String?,
)

object CatalogManager {
    val addons = mutableStateListOf<CatalogAddon>()
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        val arr = JSONArray(appContext.getSharedPreferences("catalogs", 0).getString("list", "[]"))
        addons.clear()
        for (i in 0 until arr.length()) runCatching { addons += parse(arr.getJSONObject(i).getString("url"), arr.getJSONObject(i).getJSONObject("manifest")) }
    }

    private val raw = mutableMapOf<String, JSONObject>()

    private fun save() {
        val arr = JSONArray()
        addons.forEach { a -> raw[a.url]?.let { arr.put(JSONObject().put("url", a.url).put("manifest", it)) } }
        appContext.getSharedPreferences("catalogs", 0).edit().putString("list", arr.toString()).apply()
    }

    /** stremio://… ya da …/manifest.json → eklentinin kök adresi. */
    fun base(input: String): String {
        var s = input.trim()
        if (s.startsWith("stremio://")) s = "https://" + s.removePrefix("stremio://")
        if (!s.startsWith("http")) s = "https://$s"
        return s.removeSuffix("/").removeSuffix("/manifest.json").removeSuffix("/")
    }

    /** JSON bir Stremio katalog manifesti mi? */
    fun isCatalogManifest(o: JSONObject) = o.has("catalogs") && (o.has("resources") || o.has("types"))

    private fun parse(url: String, o: JSONObject): CatalogAddon {
        raw[url] = o
        val cats = mutableListOf<CatalogDef>()
        val arr = o.optJSONArray("catalogs") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val extra = c.optJSONArray("extra")
            var search = false
            val genres = mutableListOf<String>()
            if (extra != null) for (j in 0 until extra.length()) {
                val e = extra.getJSONObject(j)
                if (e.optString("name") == "search") search = true
                if (e.optString("name") == "genre") e.optJSONArray("options")?.let { op -> for (k in 0 until op.length()) genres += op.getString(k) }
            }
            // eski biçim: extraSupported
            c.optJSONArray("extraSupported")?.let { es -> for (j in 0 until es.length()) if (es.getString(j) == "search") search = true }
            cats += CatalogDef(c.optString("type"), c.optString("id"), c.optString("name", c.optString("id")), search, genres)
        }
        return CatalogAddon(url, o.optString("name", url), o.optString("logo").ifBlank { null }, cats)
    }

    /** Eklentiyi ekler; manifest kökte ya da /manifest.json altında olabilir. */
    suspend fun add(input: String): CatalogAddon {
        val b = base(input)
        val text = runCatching { Net.getText("$b/manifest.json") }.getOrNull()?.takeIf { it.trim().startsWith("{") }
            ?: Net.getText(b)
        val o = JSONObject(text)
        if (!isCatalogManifest(o)) throw IllegalArgumentException("Bu bir katalog eklentisi değil")
        val addon = parse(b, o)
        onMain {
            addons.removeAll { it.url == b }
            addons += addon
            save()
        }
        AppLog.i("Katalog eklendi: ${addon.name} (${addon.catalogs.size} katalog)")
        return addon
    }

    fun remove(a: CatalogAddon) { addons.remove(a); raw.remove(a.url); save() }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8").replace("+", "%20")

    /** Bir katalogdan içerik getirir. */
    suspend fun items(addon: CatalogAddon, cat: CatalogDef, skip: Int = 0, search: String? = null, genre: String? = null): List<CatalogItem> {
        val extras = listOfNotNull(
            search?.let { "search=${enc(it)}" },
            genre?.let { "genre=${enc(it)}" },
            if (skip > 0) "skip=$skip" else null,
        )
        val url = "${addon.url}/catalog/${cat.type}/${enc(cat.id)}" + (if (extras.isEmpty()) "" else "/" + extras.joinToString("&")) + ".json"
        val o = JSONObject(Net.getText(url))
        val metas = o.optJSONArray("metas") ?: return emptyList()
        return (0 until metas.length()).mapNotNull { i ->
            val m = metas.optJSONObject(i) ?: return@mapNotNull null
            val name = m.optString("name").ifBlank { return@mapNotNull null }
            CatalogItem(
                id = m.optString("id"), type = m.optString("type", cat.type), name = name,
                poster = m.optString("poster").ifBlank { null }, background = m.optString("background").ifBlank { null },
                description = m.optString("description").ifBlank { null },
                year = (m.optString("releaseInfo").ifBlank { m.optString("year") }).take(4).ifBlank { null },
            )
        }
    }

    /** Katalog içeriğini TMDB kaydına çevirir (Nuvio eklentileri TMDB kimliğiyle çalışır). */
    suspend fun toTmdb(item: CatalogItem): TmdbItem? {
        val type = if (item.type == "series" || item.type == "tv") "tv" else "movie"
        val id = item.id
        return runCatching {
            when {
                id.startsWith("tmdb:") -> Tmdb.details(type, id.removePrefix("tmdb:").substringBefore(':').toInt()).item
                id.startsWith("tt") -> Tmdb.findImdb(id.substringBefore(':'), type)
                else -> Tmdb.search(item.name).firstOrNull { it.type == type }
            }
        }.onFailure { AppLog.e("Katalog → TMDB: ${item.name}", it) }.getOrNull()
    }
}
