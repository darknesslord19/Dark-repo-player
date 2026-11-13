package com.streambox.app.nuvio

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.streambox.app.AppLog
import com.streambox.app.Net
import com.streambox.app.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Bir Nuvio eklentisinin döndürdüğü yayın. */
data class NuvioStream(
    val scraper: String,
    val name: String,
    val title: String,
    val url: String,
    val quality: String?,
    val size: String?,
    val headers: Map<String, String>,
)

/**
 * Nuvio JavaScript eklentilerini gizli bir WebView içinde çalıştırır.
 * HTTP istekleri JS'den Android'e (OkHttp) aktarılır: CORS engeli yoktur, başlıklar serbesttir.
 */
@SuppressLint("SetJavaScriptEnabled")
object NuvioRuntime {
    private val main = Handler(Looper.getMainLooper())
    /** Eklentilerin HTTP istekleri için geniş havuz (eski 6 iş parçacığı dar boğazdı). */
    private val io = Executors.newFixedThreadPool(48)
    /** Takılan siteler eklentiyi bekletmesin: istek başına üst sınır. */
    private val fetchClient by lazy { Net.client.newBuilder().callTimeout(25, java.util.concurrent.TimeUnit.SECONDS).build() }
    private val fetchClientNoRedirect by lazy { fetchClient.newBuilder().followRedirects(false).followSslRedirects(false).build() }
    private var web: WebView? = null
    private var ready = CompletableDeferred<Unit>()
    private val calls = ConcurrentHashMap<Int, CompletableDeferred<String>>()
    private val defined = ConcurrentHashMap<String, String>() // id -> kod özeti
    private val callSeq = AtomicInteger()

    private suspend fun ensure(context: Context) {
        withContext(Dispatchers.Main) {
            if (web == null) create(context.applicationContext)
        }
        ready.await()
    }

    private fun create(context: Context) {
        ready = CompletableDeferred()
        defined.clear()
        val wv = WebView(context)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.blockNetworkImage = true
        wv.addJavascriptInterface(Bridge(), "NativeBridge")
        // Ekranda olmayan WebView'da zamanlayıcılar yavaşlamasın
        wv.resumeTimers()
        wv.onResume()
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (ready.isCompleted) return
                val assets = context.assets
                val crypto = runCatching { assets.open("js/crypto-js.min.js").bufferedReader().readText() }
                    .onFailure { AppLog.e("crypto-js bulunamadı", it) }.getOrDefault("")
                val prelude = assets.open("js/nuvio-prelude.js").bufferedReader().readText()
                view.evaluateJavascript(crypto) {
                    view.evaluateJavascript("globalThis.TMDB_API_KEY=${JSONObject.quote(Settings.tmdbKey)};") {
                        view.evaluateJavascript(prelude) { ready.complete(Unit) }
                    }
                }
            }
        }
        wv.loadDataWithBaseURL("https://nuvio.local/", "<!DOCTYPE html><html><body></body></html>", "text/html", "utf-8", null)
        web = wv
    }

    private suspend fun eval(js: String): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val wv = web
            if (wv == null) cont.resume(null) else wv.evaluateJavascript(js) { cont.resume(it) }
        }
    }

    /** Eklentiyi çalıştırır ve bulduğu yayınları döndürür. */
    suspend fun getStreams(
        context: Context,
        scraper: NuvioScraper,
        code: String,
        tmdbId: Int,
        type: String,
        season: Int?,
        episode: Int?,
        timeoutMs: Long = 45_000,
    ): List<NuvioStream> {
        ensure(context)
        val key = scraper.key
        val stamp = code.length.toString() + ":" + code.hashCode()
        if (defined[key] != stamp) {
            val res = eval(
                "(function(){try{globalThis.TMDB_API_KEY=${JSONObject.quote(Settings.tmdbKey)};" +
                    "return __define(${JSONObject.quote(key)}, ${JSONObject.quote(code)});}" +
                    "catch(e){return 'ERR:'+(e&&e.message||e)}})()"
            )
            val msg = res?.let { runCatching { JSONArray("[$it]").optString(0) }.getOrNull() }
            if (msg != "ok") throw IllegalStateException("Eklenti yüklenemedi: ${msg ?: "bilinmeyen hata"}")
            defined[key] = stamp
        }
        val callId = callSeq.incrementAndGet()
        val deferred = CompletableDeferred<String>()
        calls[callId] = deferred
        try {
            eval("__run($callId, ${JSONObject.quote(key)}, $tmdbId, ${JSONObject.quote(type)}, ${season ?: "null"}, ${episode ?: "null"})")
            val json = withTimeout(timeoutMs) { deferred.await() }
            return parse(scraper.name, json)
        } finally {
            calls.remove(callId)
        }
    }

    private fun parse(scraperName: String, json: String): List<NuvioStream> {
        val arr = JSONArray(json)
        val out = mutableListOf<NuvioStream>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url").ifBlank { o.optString("externalUrl") }
            if (url.isBlank() || url.startsWith("magnet:")) continue
            val headers = mutableMapOf<String, String>()
            fun addAll(h: JSONObject?) { h?.keys()?.forEach { k -> headers[k] = h.optString(k) } }
            addAll(o.optJSONObject("headers"))
            o.optJSONObject("behaviorHints")?.let { bh ->
                addAll(bh.optJSONObject("headers"))
                addAll(bh.optJSONObject("proxyHeaders")?.optJSONObject("request"))
            }
            out += NuvioStream(
                scraper = scraperName,
                name = o.optString("name").ifBlank { scraperName },
                title = o.optString("title").ifBlank { o.optString("description") },
                url = url,
                quality = o.optString("quality").takeIf { it.isNotBlank() && it != "null" },
                size = o.optString("size").takeIf { it.isNotBlank() && it != "null" },
                headers = headers,
            )
        }
        return out
    }

    /** JS tarafının çağırdığı Android fonksiyonları. */
    private class Bridge {
        @JavascriptInterface
        fun log(level: String, msg: String) {
            if (level == "E") AppLog.e("[Nuvio] $msg") else AppLog.i("[Nuvio] $msg")
        }

        @JavascriptInterface
        fun done(callId: Int, json: String) { calls[callId]?.complete(json) }

        @JavascriptInterface
        fun fail(callId: Int, msg: String) {
            calls[callId]?.completeExceptionally(RuntimeException(msg))
        }

        @JavascriptInterface
        fun fetch(id: Int, url: String, method: String, headersJson: String, body: String?, follow: Boolean) {
            io.execute {
                val result = JSONObject()
                try {
                    val headers = JSONObject(headersJson)
                    val b = Request.Builder().url(url)
                    var hasUa = false
                    var contentType: String? = null
                    headers.keys().forEach { k ->
                        val v = headers.optString(k)
                        if (k.equals("user-agent", true)) hasUa = true
                        if (k.equals("content-type", true)) contentType = v
                        runCatching { b.header(k, v) }
                    }
                    if (!hasUa) b.header("User-Agent", Net.USER_AGENT)
                    val reqBody = when {
                        body != null -> body.toRequestBody((contentType ?: "application/x-www-form-urlencoded").toMediaTypeOrNull())
                        method in setOf("POST", "PUT", "PATCH") -> ByteArray(0).toRequestBody(null)
                        else -> null
                    }
                    b.method(method, reqBody)
                    val client = if (follow) fetchClient else fetchClientNoRedirect
                    client.newCall(b.build()).execute().use { r ->
                        val h = JSONObject()
                        r.headers.names().forEach { n -> h.put(n, r.headers.values(n).joinToString(", ")) }
                        result.put("status", r.code)
                        result.put("statusText", r.message)
                        result.put("url", r.request.url.toString())
                        result.put("redirected", r.priorResponse != null)
                        result.put("headers", h)
                        result.put("body", r.body?.string() ?: "")
                    }
                } catch (e: Exception) {
                    result.put("error", e.message ?: e.javaClass.simpleName)
                }
                val js = "__fetchDone($id, $result)"
                main.post { web?.evaluateJavascript(js, null) }
            }
        }
    }
}
