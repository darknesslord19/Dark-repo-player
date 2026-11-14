package com.streambox.app.sites

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.net.URI

/**
 * Sayfanın tarayıcıda zaten indirdiği yayın listeleri (m3u8 / mpd).
 * Bazı oynatıcılar listeyi tek kullanımlık adresle verir: tarayıcı aldıktan sonra aynı adres 404 döner.
 * Oynatıcı bu adresi isteyince internete gitmeden buradaki kopya verilir.
 */
object ManifestCache {
    private const val MAX = 60
    private val map = object : LinkedHashMap<String, Pair<Long, ByteArray>>(16, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Pair<Long, ByteArray>>?) = size > MAX
    }

    private fun key(u: String) = u.substringBefore('#')

    /** [url]: istenen adres, [finalUrl]: yönlendirme sonrası adres (göreli yollar buna göre çözülür). */
    fun put(url: String, finalUrl: String?, body: String) {
        if (!body.startsWith("#EXTM3U") && !body.contains("<MPD")) return
        val base = finalUrl?.takeIf { it.startsWith("http") } ?: url
        val text = if (body.startsWith("#EXTM3U")) absolutize(body, base) else body
        val bytes = text.toByteArray()
        synchronized(map) {
            map[key(url)] = System.currentTimeMillis() to bytes
            if (finalUrl != null && finalUrl.startsWith("http")) map[key(finalUrl)] = System.currentTimeMillis() to bytes
        }
    }

    fun get(url: String): ByteArray? = synchronized(map) {
        val e = map[key(url)] ?: return null
        if (System.currentTimeMillis() - e.first > 3 * 3600_000) { map.remove(key(url)); return null }
        e.second
    }

    fun has(url: String) = get(url) != null

    /** HLS listesindeki göreli adresleri tam adrese çevirir (liste başka adresten sunulacağı için). */
    private fun absolutize(m3u: String, base: String): String {
        val b = runCatching { URI(base) }.getOrNull() ?: return m3u
        fun abs(p: String) = runCatching { b.resolve(p.trim()).toString() }.getOrDefault(p)
        val uriAttr = Regex("URI=\"([^\"]+)\"")
        return m3u.lines().joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                line.startsWith("#") -> uriAttr.replace(line) { m -> "URI=\"" + abs(m.groupValues[1]) + "\"" }
                else -> abs(line)
            }
        }
    }
}

/** Önce [ManifestCache]'e bakan, yoksa internete giden veri kaynağı. */
@UnstableApi
class CachedManifestDataSource(private val upstream: DataSource) : DataSource {
    private var current: DataSource? = null
    private val listeners = mutableListOf<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener); listeners += transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        val cached = ManifestCache.get(dataSpec.uri.toString())
        val ds = if (cached != null) ByteArrayDataSource(cached).also { d -> listeners.forEach { d.addTransferListener(it) } } else upstream
        current = ds
        return ds.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = current!!.read(buffer, offset, length)
    override fun getUri(): Uri? = current?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()
    override fun close() { current?.close(); current = null }

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = CachedManifestDataSource(upstream.createDataSource())
    }
}

/** Tarayıcının video/liste isteklerinde gönderdiği gerçek başlıklar (Referer, Origin, özel başlıklar). */
object HeaderBook {
    private val map = object : LinkedHashMap<String, Map<String, String>>(64, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Map<String, String>>?) = size > 300
    }
    private val skip = setOf("range", "accept-encoding", "content-length", "host", "connection")

    fun put(url: String, headers: Map<String, String>?) {
        if (headers.isNullOrEmpty()) return
        synchronized(map) { map[url.substringBefore('#')] = headers.filterKeys { it.lowercase() !in skip } }
    }

    fun get(url: String): Map<String, String>? = synchronized(map) { map[url.substringBefore('#')] }
}
