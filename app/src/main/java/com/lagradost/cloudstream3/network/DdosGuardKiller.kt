package com.lagradost.cloudstream3.network

import com.streambox.app.Net
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * CloudStream DdosGuardKiller karşılığı: DDoS-Guard korumalı sitelerde
 * koruma çerezlerini alıp isteği tekrarlar.
 */
class DdosGuardKiller(private val alwaysBypass: Boolean) : Interceptor {
    val savedCookiesMap: MutableMap<String, Map<String, String>> = mutableMapOf()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        if (alwaysBypass) return chain.proceed(withCookies(request, cookiesFor(request)))
        savedCookiesMap[host]?.let { return chain.proceed(withCookies(request, it)) }
        val response = chain.proceed(request)
        val server = response.header("server")?.lowercase() ?: ""
        if (response.code == 403 && server.contains("ddos-guard")) {
            response.close()
            return chain.proceed(withCookies(request, cookiesFor(request)))
        }
        return response
    }

    private fun cookiesFor(request: Request): Map<String, String> {
        val host = request.url.host
        savedCookiesMap[host]?.let { return it }
        val cookies = runCatching {
            val js = Net.client.newCall(Request.Builder().url("https://check.ddos-guard.net/check.js").build()).execute().use { it.body?.string() ?: "" }
            val path = Regex("'(.*?)'").findAll(js).map { it.groupValues[1] }.firstOrNull { it.startsWith("/") } ?: return@runCatching emptyMap()
            val url = request.url.newBuilder().encodedPath(path).query(null).build()
            Net.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                r.headers("set-cookie").associate { c -> c.substringBefore("=") to c.substringAfter("=").substringBefore(";") }
            }
        }.getOrDefault(emptyMap())
        if (cookies.isNotEmpty()) savedCookiesMap[host] = cookies
        return cookies
    }

    private fun withCookies(request: Request, cookies: Map<String, String>): Request {
        if (cookies.isEmpty()) return request
        val existing = request.header("Cookie")?.let { "$it; " } ?: ""
        return request.newBuilder().header("Cookie", existing + cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }).build()
    }
}
