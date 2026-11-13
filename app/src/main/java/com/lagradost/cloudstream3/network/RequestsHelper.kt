package com.lagradost.cloudstream3.network

import android.content.Context
import com.lagradost.nicehttp.Requests
import com.streambox.app.Net
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient

/** CloudStream RequestsHelper karşılığı: eklentiler kendi istemcilerini kurarken kullanır. */
fun Requests.initClient(context: Context) {
    baseClient = Net.client
}

fun Requests.initClient(context: Context, ignoreSSL: Boolean = false) {
    baseClient = buildDefaultClient(context, ignoreSSL)
}

fun buildDefaultClient(context: Context): OkHttpClient = Net.client

fun buildDefaultClient(context: Context, ignoreSSL: Boolean = false): OkHttpClient = Net.client

/** Başlık ve çerez haritalarından OkHttp başlıkları üretir. */
fun getHeaders(headers: Map<String, String>, cookie: Map<String, String>): Headers {
    val cookieHeader = if (cookie.isEmpty()) emptyMap()
    else mapOf("Cookie" to cookie.entries.joinToString("; ") { "${it.key}=${it.value}" })
    return (headers + cookieHeader).toHeaders()
}
