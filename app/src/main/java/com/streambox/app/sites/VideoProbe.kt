package com.streambox.app.sites

import com.streambox.app.AppLog
import com.streambox.app.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Yakalanan video adreslerini oynatmadan önce hızlıca yoklar (hepsi aynı anda, adres başına birkaç saniye):
 * - açılıyor mu (404/403 olanlar elenir),
 * - türü ne (HLS / DASH / MP4 / MKV …, adrese değil içeriğe bakılır),
 * - süresi ne kadar (HLS'de bölüm sürelerinin toplamı; düz dosyada boyuttan tahmin).
 * Reklam ve fragmanlar kısa olduğu için en uzun çalışan video seçilir.
 */
object VideoProbe {
    data class Result(
        val video: SniffedVideo,
        val ok: Boolean,
        val kind: String,          // HLS, DASH, MP4, MKV, WEBM, TS, ?
        val seconds: Double?,      // bilinen ya da tahmini süre
        val estimated: Boolean,
    ) {
        /** Sıralama puanı: çalışan > uzun > yayın listesi. */
        val score: Double get() = when {
            !ok -> -1.0
            else -> (seconds ?: 600.0) + (if (kind == "HLS" || kind == "DASH") 1.0 else 0.0)
        }
        val label: String get() {
            val d = seconds?.let { fmt(it) + if (estimated) "~" else "" }
            val host = runCatching { URI(video.url).host }.getOrNull() ?: ""
            return listOfNotNull(kind, d, host).joinToString(" · ")
        }
    }

    private val client by lazy {
        Net.client.newBuilder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()
    }

    fun fmt(sec: Double): String {
        val m = (sec / 60).toInt()
        return if (m >= 60) "${m / 60} sa ${m % 60} dk" else if (m >= 1) "$m dk" else "${sec.toInt()} sn"
    }

    private fun get(url: String, headers: Map<String, String>, range: Boolean): Triple<Int, String?, ByteArray>? = runCatching {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> if (!k.equals("Range", true)) b.header(k, v) }
        if (range) b.header("Range", "bytes=0-65535")
        client.newCall(b.build()).execute().use { r ->
            val total = r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: r.body?.contentLength()
            val bytes = r.body?.source()?.let { src -> src.request(65536); src.buffer.readByteArray(minOf(src.buffer.size, 65536)) } ?: ByteArray(0)
            Triple(r.code, (total ?: -1).toString() + "|" + (r.header("Content-Type") ?: ""), bytes)
        }
    }.getOrNull()

    /** HLS listesinin süresi: medya listesiyse EXTINF toplamı, ana listeyse ilk alt listeninki. */
    private fun hlsSeconds(text: String, base: String, headers: Map<String, String>, depth: Int = 0): Double? {
        if (text.contains("#EXTINF")) {
            return Regex("#EXTINF:([0-9.]+)").findAll(text).sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 }
        }
        if (depth > 1 || !text.contains("#EXT-X-STREAM-INF")) return null
        val variant = text.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return null
        val abs = runCatching { URI(base).resolve(variant).toString() }.getOrNull() ?: return null
        val sub = ManifestCache.get(abs)?.let { String(it) }
            ?: get(abs, headers, false)?.takeIf { it.first in 200..299 }?.let { String(it.third) }?.also { ManifestCache.put(abs, null, it) }
            ?: return null
        return hlsSeconds(sub, abs, headers, depth + 1)
    }

    private fun probeOne(v: SniffedVideo): Result {
        // Tarayıcının zaten aldığı liste varsa internete gitmeden incele
        ManifestCache.get(v.url)?.let { b ->
            val t = String(b)
            return if (t.startsWith("#EXTM3U")) Result(v, true, "HLS", hlsSeconds(t, v.url, v.headers), false)
            else Result(v, true, "DASH", Regex("mediaPresentationDuration=\"PT(?:(\\d+)H)?(?:(\\d+)M)?(?:([\\d.]+)S)?").find(t)?.let { m ->
                (m.groupValues[1].toDoubleOrNull() ?: 0.0) * 3600 + (m.groupValues[2].toDoubleOrNull() ?: 0.0) * 60 + (m.groupValues[3].toDoubleOrNull() ?: 0.0)
            }, false)
        }
        val r = get(v.url, v.headers, true) ?: return Result(v, false, "?", null, false)
        val (code, meta, bytes) = r
        if (code !in 200..299) return Result(v, false, "HTTP $code", null, false)
        val total = meta?.substringBefore('|')?.toLongOrNull()?.takeIf { it > 0 }
        val ct = meta?.substringAfter('|').orEmpty().lowercase()
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        return when {
            head.startsWith("#EXTM3U") -> {
                val text = String(bytes)
                ManifestCache.put(v.url, null, text)
                Result(v, true, "HLS", hlsSeconds(text, v.url, v.headers), false)
            }
            head.contains("<MPD") -> Result(v, true, "DASH", null, false)
            head.contains("<html", true) || ct.contains("text/html") -> Result(v, false, "HTML", null, false)
            else -> {
                val kind = when {
                    head.length > 8 && head.substring(4, 8) == "ftyp" -> "MP4"
                    bytes.size > 4 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() -> if (head.contains("webm")) "WEBM" else "MKV"
                    bytes.isNotEmpty() && bytes[0] == 0x47.toByte() -> "TS"
                    ct.contains("mp4") -> "MP4"
                    else -> "VIDEO"
                }
                // Düz dosyada süre: boyuttan tahmin (~1,2 Mbit/sn). Reklamlar birkaç MB, filmler yüzlerce MB.
                Result(v, true, kind, total?.let { it * 8.0 / 1_200_000 }, true)
            }
        }
    }

    /** Hepsini aynı anda yoklar; en iyi sırada döndürür (çalışmayanlar sonda). */
    suspend fun rank(videos: List<SniffedVideo>, timeoutMs: Long = 9_000): List<Result> = withContext(Dispatchers.IO) {
        if (videos.isEmpty()) return@withContext emptyList()
        val results = coroutineScope {
            videos.map { v -> async { withTimeoutOrNull(timeoutMs) { probeOne(v) } ?: Result(v, false, "zaman aşımı", null, false) } }.awaitAll()
        }
        val sorted = results.sortedByDescending { it.score }
        AppLog.i("Video yoklaması: " + sorted.joinToString(" | ") { (if (it.ok) "✓ " else "✗ ") + it.label })
        // Hiçbiri çalışmadıysa yine de sırayı bozmadan dene (yoklama engellenmiş olabilir)
        if (sorted.none { it.ok }) results else sorted
    }
}
