package com.streambox.app.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.mutableStateListOf
import com.streambox.app.AppLog
import com.streambox.app.Backup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Aynı Wi-Fi'de bulunan başka bir StreamBox. */
data class Peer(val name: String, val host: String, val port: Int) {
    val address get() = "$host:$port"
}

/** Aynı ağdaki StreamBox cihazlarını bulur; yedeği alır ya da gönderir. */
object Sync {
    val peers = mutableStateListOf<Peer>()
    private var nsd: NsdManager? = null
    private var listener: NsdManager.DiscoveryListener? = null
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).build()

    fun startDiscovery(context: Context) {
        if (listener != null) return
        peers.clear()
        val m = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) { AppLog.e("Cihaz arama başlatılamadı: $e") }
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) { synchronized(pending) { pending += info }; next(m) }
            override fun onServiceLost(info: NsdServiceInfo) {
                com.streambox.app.onMain { peers.removeAll { it.name == info.serviceName } }
            }
        }
        runCatching { m.discoverServices(RemoteServer.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
            .onFailure { AppLog.e("Cihaz arama", it); return }
        nsd = m; listener = l
    }

    fun stopDiscovery() {
        runCatching { listener?.let { nsd?.stopServiceDiscovery(it) } }
        listener = null
    }

    /** Eski NSD sürümü aynı anda tek çözümlemeye izin verir: sırayla. */
    @Suppress("DEPRECATION")
    private fun next(m: NsdManager) {
        val info = synchronized(pending) {
            if (resolving || pending.isEmpty()) return
            resolving = true
            pending.removeFirst()
        }
        m.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(i: NsdServiceInfo, e: Int) { synchronized(pending) { resolving = false }; next(m) }
            override fun onServiceResolved(i: NsdServiceInfo) {
                val host = i.host?.hostAddress
                val self = RemoteServer.localIp()
                if (host != null && !(host == self && i.port == RemoteServer.port)) {
                    val peer = Peer(i.serviceName.removePrefix("StreamBox · "), host, i.port)
                    com.streambox.app.onMain { peers.removeAll { it.address == peer.address }; peers += peer }
                }
                synchronized(pending) { resolving = false }
                next(m)
            }
        })
    }

    private fun base(address: String) = "http://" + address.removePrefix("http://").trimEnd('/')

    /** Karşı cihazın bilgisi (PIN gerekmez) — elle girilen adresi doğrulamak için. */
    suspend fun hello(address: String): JSONObject = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(base(address) + "/api/hello").build()).execute().use {
            if (!it.isSuccessful) error("Cihaz yanıt vermedi (${it.code})")
            JSONObject(it.body!!.string())
        }
    }

    /** Karşı cihazdaki her şeyi bu cihaza alır (bu cihazdakilerin yerine geçer). */
    suspend fun pull(context: Context, address: String, pin: String): String = withContext(Dispatchers.IO) {
        val data = http.newCall(Request.Builder().url(base(address) + "/api/backup").header("X-Pin", pin).build()).execute().use {
            if (it.code == 401) error("PIN hatalı")
            if (!it.isSuccessful) error("Alınamadı (${it.code})")
            JSONObject(it.body!!.string())
        }
        Backup.restore(context, data)
        Backup.summary(data)
    }

    /** Bu cihazdaki her şeyi karşı cihaza gönderir (o cihaz yeniden başlar). */
    suspend fun push(context: Context, address: String, pin: String) = withContext(Dispatchers.IO) {
        val body = Backup.export(context).toString().toRequestBody("application/json".toMediaType())
        http.newCall(Request.Builder().url(base(address) + "/api/restore").header("X-Pin", pin).post(body).build()).execute().use {
            if (it.code == 401) error("PIN hatalı")
            if (!it.isSuccessful) error("Gönderilemedi (${it.code})")
        }
    }
}
