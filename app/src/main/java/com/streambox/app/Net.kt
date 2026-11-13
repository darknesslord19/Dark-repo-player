package com.streambox.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** DNS over HTTPS seçenekleri (Türkiye'deki DNS engellerini aşmak için). */
enum class DnsProvider(val label: String, val url: String?, val bootstrap: List<String>) {
    NONE("Kapalı (sistem DNS'i)", null, emptyList()),
    CLOUDFLARE("Cloudflare", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    GOOGLE("Google", "https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4")),
    ADGUARD("AdGuard", "https://unfiltered.adguard-dns.com/dns-query", listOf("94.140.14.140", "94.140.14.141")),
    QUAD9("Quad9", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
}

object Net {
    /**
     * Eklentilerle aynı tarayıcı kimliği. Bazı siteler video bağlantısını bu kimliğe bağlar;
     * oynatıcı farklı bir kimlik gönderirse 404 alınır. Bu yüzden CloudStream'in kendi değeri kullanılır.
     */
    val USER_AGENT: String by lazy {
        runCatching {
            Class.forName("com.lagradost.cloudstream3.MainAPIKt").getField("USER_AGENT").get(null) as String
        }.getOrNull()
            ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    /** CloudStream gibi: hatalı/süresi geçmiş sertifikalı sitelere de bağlan. */
    private fun OkHttpClient.Builder.ignoreAllSSLErrors(): OkHttpClient.Builder {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustAll), SecureRandom()) }
        sslSocketFactory(ctx.socketFactory, trustAll)
        hostnameVerifier { _, _ -> true }
        return this
    }

    private fun build(): OkHttpClient {
        val base = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .ignoreAllSSLErrors()
            .addInterceptor(com.streambox.app.cf.CfVerify.interceptor)
        val provider = runCatching { DnsProvider.valueOf(Settings.dns) }.getOrDefault(DnsProvider.NONE)
        if (provider.url != null) {
            runCatching {
                val doh = DnsOverHttps.Builder()
                    .client(base.build())
                    .url(provider.url.toHttpUrl())
                    .bootstrapDnsHosts(provider.bootstrap.map { InetAddress.getByName(it) })
                    .includeIPv6(false)
                    .build()
                base.dns(doh)
                AppLog.i("DNS over HTTPS: ${provider.label}")
            }.onFailure { AppLog.e("DoH kurulamadı", it) }
        }
        return base.build()
    }

    /** Uygulamanın tek HTTP istemcisi: eklentiler, oynatıcı ve Nuvio aynı ayarı kullanır. */
    @Volatile
    var client: OkHttpClient = build()
        private set

    /** DNS ayarı değişince istemciyi yeniden kur. */
    fun rebuild() { client = build() }

    private fun req(url: String, headers: Map<String, String> = emptyMap()) =
        Request.Builder().url(url).header("User-Agent", USER_AGENT)
            .apply { headers.forEach { (k, v) -> header(k, v) } }.build()

    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        client.newCall(req(url, headers)).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}: $url")
            r.body?.string() ?: ""
        }
    }

    suspend fun download(url: String, to: File) = withContext(Dispatchers.IO) {
        client.newCall(req(url)).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}: $url")
            val tmp = File(to.parentFile, to.name + ".part")
            r.body!!.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (to.exists()) { to.setWritable(true); to.delete() }
            if (!tmp.renameTo(to)) throw IOException("Dosya kaydedilemedi: $to")
        }
    }
}
