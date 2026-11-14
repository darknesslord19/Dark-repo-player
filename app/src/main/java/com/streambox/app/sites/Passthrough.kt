package com.streambox.app.sites

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.streambox.app.AppLog
import com.streambox.app.Net
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.SequenceInputStream
import java.util.concurrent.TimeUnit

/**
 * Yayın listesi (m3u8/mpd) isteklerini tarayıcı yerine biz yaparız ve sonucu tarayıcıya veririz.
 * Böylece:
 *  - tek kullanımlık liste adresleri yalnızca bir kez istenir ve içeriği saklanır (oynatıcı aynı kopyayı kullanır),
 *  - oynatıcı listeyi ayrı bir iş parçacığında (worker) yükleyen sayfalarda da liste yakalanır.
 * Bir sorun olursa null döner; tarayıcı isteği her zamanki gibi kendisi yapar.
 */
object Passthrough {
    private val listLike = Regex("(?i)(\\.m3u8|\\.mpd|master|playlist|/manifest|/hls/|/list/|\\.txt(\\?|$)|/m3u8/)")
    private val notList = Regex("(?i)\\.(ts|m4s|aac|mp4|mkv|webm|jpe?g|png|gif|webp|js|css|vtt|srt|woff2?|ico|svg)(\\?|$)")

    private val client by lazy {
        Net.client.newBuilder().followRedirects(true).connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    }

    fun handle(req: WebResourceRequest, onList: (url: String, finalUrl: String, kind: String) -> Unit): WebResourceResponse? {
        if (!req.method.equals("GET", true)) return null
        val url = req.url.toString()
        if (!url.startsWith("http") || !listLike.containsMatchIn(url) || notList.containsMatchIn(url.substringBefore('?').takeLast(12) + "?")) return null
        return runCatching {
            val b = Request.Builder().url(url)
            req.requestHeaders?.forEach { (k, v) -> if (!k.equals("Range", true) && !k.equals("Accept-Encoding", true)) b.header(k, v) }
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { b.header("Cookie", it) }
            val res = client.newCall(b.build()).execute()
            val body = res.body ?: run { res.close(); return null }
            val src = body.source()
            src.request(4L * 1024 * 1024)
            val n = minOf(src.buffer.size, 4L * 1024 * 1024)
            val head = src.buffer.snapshot(minOf(n, 64).toInt()).utf8()
            val isList = head.startsWith("#EXTM3U") || head.contains("<MPD")
            val first: ByteArray
            val stream = if (isList) {
                first = src.buffer.readByteArray(n)
                res.close()
                ManifestCache.put(url, res.request.url.toString(), String(first))
                onList(url, res.request.url.toString(), if (head.contains("<MPD")) "dash" else "hls")
                ByteArrayInputStream(first)
            } else {
                // Liste değil (ör. kılık değiştirmiş video parçası): akışı olduğu gibi aktar
                SequenceInputStream(ByteArrayInputStream(src.buffer.readByteArray(n)), src.inputStream())
            }
            val headers = mutableMapOf<String, String>()
            res.headers.names().forEach { name ->
                if (name.lowercase() !in setOf("content-encoding", "content-length", "transfer-encoding", "connection")) headers[name] = res.header(name) ?: ""
            }
            // Başka siteden istenen listeler için tarayıcı izin başlığını ister
            val origin = req.requestHeaders?.entries?.firstOrNull { it.key.equals("Origin", true) }?.value
            headers["Access-Control-Allow-Origin"] = origin ?: "*"
            if (origin != null) headers["Access-Control-Allow-Credentials"] = "true"
            val ct = res.header("Content-Type") ?: if (isList) "application/vnd.apple.mpegurl" else "application/octet-stream"
            val mime = ct.substringBefore(';').trim()
            val reason = res.message.ifBlank { if (res.code in 200..299) "OK" else "Error" }
            WebResourceResponse(mime, "utf-8", res.code, reason, headers, stream)
        }.onFailure { AppLog.e("Liste aktarılamadı: $url", it) }.getOrNull()
    }
}
