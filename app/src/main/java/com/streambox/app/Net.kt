package com.streambox.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object Net {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

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
