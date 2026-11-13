package com.streambox.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * "Kaldığın yerden izle" kaydı.
 * [key]: aynı içerik/bölüm için sabit anahtar (kaynak değişse de aynı kalır).
 * [meta]: yeniden açmak için gereken bilgiler (kind: iptv | cs | tmdb).
 */
data class HistoryEntry(
    val key: String,
    val title: String,
    val subtitle: String?,
    val poster: String?,
    val position: Long,
    val duration: Long,
    val updated: Long,
    val meta: JSONObject,
) {
    val kind: String get() = meta.optString("kind")
    val progress: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

object WatchHistory {
    val entries = mutableStateListOf<HistoryEntry>()
    private lateinit var appContext: Context
    private const val MAX = 40

    fun init(context: Context) {
        appContext = context.applicationContext
        val arr = JSONArray(appContext.getSharedPreferences("history", 0).getString("list", "[]"))
        entries.clear()
        for (i in 0 until arr.length()) runCatching {
            val o = arr.getJSONObject(i)
            entries += HistoryEntry(
                o.getString("key"), o.getString("title"), o.optString("subtitle").ifBlank { null }, o.optString("poster").ifBlank { null },
                o.optLong("position"), o.optLong("duration"), o.optLong("updated"), o.getJSONObject("meta"),
            )
        }
    }

    private fun save() {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().put("key", e.key).put("title", e.title).put("subtitle", e.subtitle ?: "").put("poster", e.poster ?: "")
                .put("position", e.position).put("duration", e.duration).put("updated", e.updated).put("meta", e.meta))
        }
        appContext.getSharedPreferences("history", 0).edit().putString("list", arr.toString()).apply()
    }

    /** Kayıtlı konum (kaldığın yerden başlatmak için). */
    fun positionFor(key: String?): Long {
        val e = entries.firstOrNull { it.key == key } ?: return 0
        if (e.position < 5_000) return 0
        if (e.duration > 0 && e.position > e.duration - 20_000) return 0
        return e.position
    }

    /** Oynatıcıdan çağrılır: konumu kaydeder; sona gelindiyse kaydı siler. */
    fun update(key: String?, metaJson: String?, position: Long, duration: Long) {
        if (key == null || metaJson == null) return
        val meta = runCatching { JSONObject(metaJson) }.getOrNull() ?: return
        onMain {
            entries.removeAll { it.key == key }
            val finished = duration > 0 && position > duration * 0.95
            if (!finished) {
                entries.add(0, HistoryEntry(
                    key, meta.optString("title"), meta.optString("subtitle").ifBlank { null }, meta.optString("poster").ifBlank { null },
                    position.coerceAtLeast(0), duration.coerceAtLeast(0), System.currentTimeMillis(), meta,
                ))
                while (entries.size > MAX) entries.removeAt(entries.lastIndex)
            }
            save()
        }
    }

    fun entry(key: String): HistoryEntry? = entries.firstOrNull { it.key == key }
    /** Bir içeriğin en son izlenen bölümü/kaydı (anahtar öneki ile). */
    fun latest(prefix: String): HistoryEntry? = entries.firstOrNull { it.key.startsWith(prefix) }

    fun remove(e: HistoryEntry) { entries.remove(e); save() }
    fun clear() { entries.clear(); save() }

    // ---- Meta oluşturucular

    fun iptvMeta(name: String, url: String, logo: String?, group: String, headers: Map<String, String>): String =
        JSONObject().put("kind", "iptv").put("title", name).put("subtitle", group).put("poster", logo ?: "")
            .put("url", url).put("group", group).put("headers", JSONObject(headers as Map<*, *>)).toString()

    fun csMeta(api: String, url: String, title: String, episode: String?, poster: String?): String =
        JSONObject().put("kind", "cs").put("api", api).put("url", url).put("title", title)
            .put("subtitle", episode ?: "").put("poster", poster ?: "").toString()

    fun tmdbMeta(item: TmdbItem, episode: String?): String =
        JSONObject().put("kind", "tmdb").put("id", item.id).put("type", item.type).put("title", item.title)
            .put("subtitle", episode ?: "").put("poster", item.poster ?: "").put("backdrop", item.backdrop ?: "")
            .put("overview", item.overview).put("year", item.year ?: "").put("rating", item.rating ?: 0.0).toString()
}
