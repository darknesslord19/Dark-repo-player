package com.lagradost.cloudstream3.network

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.streambox.app.AppLog
import com.streambox.app.StreamBoxApp
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * CloudStream uygulamasındaki CloudflareKiller'ın karşılığı (eklentiler bu adla arar).
 * Cloudflare doğrulama sayfası çıkarsa sayfayı gizli bir WebView'da açar,
 * cf_clearance çerezini alır ve isteği o çerezle tekrarlar.
 */
class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        private val ERROR_CODES = listOf(403, 503)
        private val CLOUDFLARE_SERVERS = listOf("cloudflare-nginx", "cloudflare")

        fun parseCookieMap(cookie: String): Map<String, String> =
            cookie.split(";").mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i).trim() to it.substring(i + 1).trim()
            }.toMap()

        /** Çözülmüş alan adları için WebView'ın kullanıcı aracısı (çerez buna bağlıdır). */
        @Volatile private var webViewUa: String? = null
    }

    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    private fun host(url: String) = runCatching { URI(url).host }.getOrNull() ?: url

    /** Bu adres için kaydedilmiş Cloudflare çerezlerini ve UA'yı başlık olarak verir. */
    fun getCookieHeaders(url: String): Headers {
        val map = mutableMapOf<String, String>()
        webViewUa?.let { map["user-agent"] = it }
        savedCookies[host(url)]?.let { c -> map["Cookie"] = c.entries.joinToString("; ") { "${it.key}=${it.value}" } }
        return map.toHeaders()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        savedCookies[host]?.let { return chain.proceed(withCookies(request, it)) }

        val response = chain.proceed(request)
        val server = response.header("Server")?.lowercase() ?: ""
        if (response.code !in ERROR_CODES || CLOUDFLARE_SERVERS.none { server.contains(it) }) return response

        val cookies = solve(request.url.toString()) ?: return response
        response.close()
        savedCookies[host] = cookies
        return chain.proceed(withCookies(request, cookies))
    }

    private fun withCookies(request: Request, cookies: Map<String, String>): Request {
        val existing = request.header("Cookie")?.let { parseCookieMap(it) } ?: emptyMap()
        val all = existing + cookies
        return request.newBuilder()
            .header("Cookie", all.entries.joinToString("; ") { "${it.key}=${it.value}" })
            .apply { webViewUa?.let { header("User-Agent", it) } }
            .build()
    }

    /** Sayfayı WebView'da açıp cf_clearance çerezini bekler (en çok 30 sn). */
    @SuppressLint("SetJavaScriptEnabled")
    private fun solve(url: String): Map<String, String>? {
        val context = StreamBoxApp.instance ?: return null
        if (Looper.myLooper() == Looper.getMainLooper()) return null // ana iş parçacığını kilitlememek için
        AppLog.i("Cloudflare doğrulaması çözülüyor: ${host(url)}")
        val main = Handler(Looper.getMainLooper())
        val done = CountDownLatch(1)
        var result: Map<String, String>? = null
        var web: WebView? = null
        val cm = CookieManager.getInstance()

        main.post {
            runCatching {
                webViewUa = WebSettings.getDefaultUserAgent(context)
                cm.setAcceptCookie(true)
                web = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = webViewUa
                    cm.setAcceptThirdPartyCookies(this, true)
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
            }.onFailure { AppLog.e("CloudflareKiller WebView açılamadı", it); done.countDown() }
        }
        val poll = object : Runnable {
            var tries = 0
            override fun run() {
                val c = cm.getCookie(url)
                if (c != null && c.contains("cf_clearance")) {
                    result = parseCookieMap(c); done.countDown(); return
                }
                if (++tries < 60) main.postDelayed(this, 500) else done.countDown()
            }
        }
        main.postDelayed(poll, 1500)
        done.await(35, TimeUnit.SECONDS)
        main.post { web?.stopLoading(); web?.destroy() }
        if (result == null) AppLog.e("Cloudflare doğrulaması çözülemedi: ${host(url)}")
        return result
    }
}
