package com.streambox.app.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.streambox.app.AppLog
import com.streambox.app.Backup
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/** Gelen istek. */
class Req(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: ByteArray) {
    fun q(name: String) = query[name]
    val text: String get() = String(body, Charsets.UTF_8)
}

/** Giden yanıt. */
class Res(val code: Int, val type: String, val body: ByteArray, val extra: Map<String, String> = emptyMap()) {
    companion object {
        fun json(s: String, code: Int = 200) = Res(code, "application/json; charset=utf-8", s.toByteArray())
        fun ok() = json("{\"ok\":true}")
        fun err(msg: String, code: Int = 400) = json(org.json.JSONObject().put("ok", false).put("error", msg).toString(), code)
        fun html(s: String) = Res(200, "text/html; charset=utf-8", s.toByteArray())
    }
}

/**
 * Yerel ağ sunucusu: telefondan kumanda (tarayıcıyla), yedek indirme/yükleme ve
 * aynı Wi-Fi'deki DarknesBox'lar arası senkron. Her API isteği PIN ister.
 */
object RemoteServer {
    const val PORT = 8686
    const val SERVICE_TYPE = "_streambox._tcp."

    var running by mutableStateOf(false)
        private set
    var port by mutableStateOf(PORT)
        private set
    var enabled by mutableStateOf(true)
        private set
    var pin by mutableStateOf("")
        private set

    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    private var nsd: NsdManager? = null
    private var nsdListener: NsdManager.RegistrationListener? = null
    private lateinit var appContext: Context

    private fun prefs() = appContext.getSharedPreferences("remote", 0)

    fun startIfEnabled(context: Context) {
        appContext = context.applicationContext
        val p = prefs()
        enabled = p.getBoolean("enabled", true)
        pin = p.getString("pin", null) ?: newPin().also { p.edit().putString("pin", it).apply() }
        if (enabled) start()
    }

    private fun newPin() = (100000..999999).random().toString()

    fun regeneratePin() {
        pin = newPin(); prefs().edit().putString("pin", pin).apply()
    }

    fun setEnabledState(v: Boolean) {
        enabled = v; prefs().edit().putBoolean("enabled", v).apply()
        if (v) start() else stop()
    }

    @Synchronized
    fun start() {
        if (server != null) return
        val s = (PORT until PORT + 10).firstNotNullOfOrNull { p -> runCatching { ServerSocket(p) }.getOrNull() }
        if (s == null) { AppLog.e("Kumanda sunucusu açılamadı (port dolu)"); return }
        server = s
        port = s.localPort
        running = true
        Thread({
            while (!s.isClosed) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                pool.execute { handle(c) }
            }
        }, "remote-server").apply { isDaemon = true }.start()
        register()
        AppLog.i("Kumanda sunucusu: ${url() ?: "port $port"}")
    }

    @Synchronized
    fun stop() {
        runCatching { nsdListener?.let { nsd?.unregisterService(it) } }
        nsdListener = null
        runCatching { server?.close() }
        server = null
        running = false
    }

    /** Ağdaki DarknesBox'ların bu cihazı bulabilmesi için (mDNS/NSD). */
    private fun register() {
        runCatching {
            val m = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
            val info = NsdServiceInfo().apply {
                serviceName = "DarknesBox · ${Backup.deviceName(appContext)}"
                serviceType = SERVICE_TYPE
                port = this@RemoteServer.port
            }
            val l = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(i: NsdServiceInfo) {}
                override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) { AppLog.e("NSD kaydı başarısız: $e") }
                override fun onServiceUnregistered(i: NsdServiceInfo) {}
                override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
            }
            m.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
            nsd = m; nsdListener = l
        }.onFailure { AppLog.e("NSD kaydı", it) }
    }

    /** Bu cihazın yerel ağ IPv4 adresi. */
    fun localIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }.getOrNull()

    fun url(withPin: Boolean = false): String? = localIp()?.let { "http://$it:$port/" + if (withPin) "?k=$pin" else "" }

    // ------------------------------------------------------------------ HTTP

    private fun handle(socket: Socket) {
        socket.use { s ->
            runCatching {
                s.soTimeout = 30_000
                val input = BufferedInputStream(s.getInputStream())
                val req = parse(input) ?: return
                val res = try {
                    route(req)
                } catch (t: Throwable) {
                    AppLog.e("Kumanda isteği ${req.path}", t)
                    Res.err(t.message ?: t.javaClass.simpleName, 500)
                }
                write(s.getOutputStream(), res)
            }
        }
    }

    private fun readLine(i: InputStream): String? {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = i.read()
            if (c == -1) return if (b.size() == 0) null else b.toString("UTF-8")
            if (c == '\n'.code) return b.toString("UTF-8").trimEnd('\r')
            b.write(c)
            if (b.size() > 16_384) return null
        }
    }

    private fun parse(i: InputStream): Req? {
        val first = readLine(i) ?: return null
        val parts = first.split(" ")
        if (parts.size < 2) return null
        val headers = mutableMapOf<String, String>()
        while (true) {
            val l = readLine(i) ?: break
            if (l.isEmpty()) break
            val idx = l.indexOf(':')
            if (idx > 0) headers[l.substring(0, idx).trim().lowercase()] = l.substring(idx + 1).trim()
        }
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        if (len > 64_000_000) return null
        val body = ByteArray(len)
        var read = 0
        while (read < len) { val n = i.read(body, read, len - read); if (n < 0) break; read += n }
        val full = parts[1]
        val path = full.substringBefore('?')
        val query = full.substringAfter('?', "").split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        return Req(parts[0].uppercase(), path, query, headers, body)
    }

    private fun write(o: OutputStream, r: Res) {
        val status = when (r.code) { 200 -> "OK"; 401 -> "Unauthorized"; 404 -> "Not Found"; 500 -> "Internal Server Error"; else -> "Bad Request" }
        val sb = StringBuilder("HTTP/1.1 ${r.code} $status\r\n")
        sb.append("Content-Type: ${r.type}\r\nContent-Length: ${r.body.size}\r\nConnection: close\r\n")
        sb.append("Cache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\n")
        r.extra.forEach { (k, v) -> sb.append("$k: $v\r\n") }
        sb.append("\r\n")
        o.write(sb.toString().toByteArray())
        o.write(r.body)
        o.flush()
    }

    private fun route(r: Req): Res {
        if (r.method == "OPTIONS") return Res(200, "text/plain", ByteArray(0), mapOf("Access-Control-Allow-Headers" to "X-Pin, Content-Type", "Access-Control-Allow-Methods" to "GET, POST"))
        if (r.path == "/" || r.path == "/index.html") return Res.html(appContext.assets.open("remote/index.html").bufferedReader().readText())
        if (r.path == "/api/hello") return Res.json(RemoteApi.hello(appContext).toString())
        val given = r.headers["x-pin"] ?: r.q("k")
        if (given != pin) return Res.err("PIN hatalı", 401)
        return RemoteApi.handle(appContext, r)
    }
}
