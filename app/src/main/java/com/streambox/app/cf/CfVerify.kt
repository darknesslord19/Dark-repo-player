package com.streambox.app.cf

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.streambox.app.AppLog
import com.streambox.app.ui.AppTheme
import com.streambox.app.ui.Muted
import okhttp3.Interceptor
import okhttp3.Response
import java.net.URI

/**
 * Cloudflare doğrulaması: gizli tarayıcının geçemediği "insan mısın" sayfaları (Turnstile) için
 * siteyi görünür açar; kullanıcı doğrulayınca alınan cf_clearance çerezi ve tarayıcı kimliği
 * o sitenin TÜM isteklerine (eklentiler dahil) eklenir.
 */
object CfVerify {
    private lateinit var appContext: Context
    /** Doğrulanmış site → tarayıcı kimliği (çerez bu kimliğe bağlıdır). */
    private val verified = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun init(context: Context) {
        appContext = context.applicationContext
        appContext.getSharedPreferences("cf", 0).all.forEach { (k, v) -> (v as? String)?.let { verified[k] = it } }
    }

    fun root(host: String) = host.lowercase().removePrefix("www.").split('.').takeLast(2).joinToString(".")

    internal fun markVerified(url: String, ua: String) {
        val h = runCatching { URI(url).host }.getOrNull() ?: return
        verified[root(h)] = ua
        appContext.getSharedPreferences("cf", 0).edit().putString(root(h), ua).apply()
        AppLog.i("Cloudflare doğrulandı: ${root(h)}")
    }

    /** Hata metni Cloudflare engeli gibi görünüyor mu (doğrula düğmesi göstermek için). */
    fun looksBlocked(error: String?): Boolean {
        val e = error?.lowercase() ?: return false
        return listOf("403", "503", "cloudflare", "attention required", "just a moment", "cf-", "challenge").any { e.contains(it) }
    }

    fun open(context: Context, url: String) {
        val i = Intent(context, CfVerifyActivity::class.java).putExtra("url", url)
        if (context !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    /** Net.client'a eklenir: doğrulanmış sitelere çerez + aynı tarayıcı kimliği. */
    val interceptor = Interceptor { chain ->
        val req = chain.request()
        val ua = verified[root(req.url.host)]
        if (ua == null) return@Interceptor chain.proceed(req)
        val cookies = runCatching { CookieManager.getInstance().getCookie(req.url.toString()) }.getOrNull()
        val b = req.newBuilder().header("User-Agent", ua)
        if (!cookies.isNullOrBlank()) {
            val old = req.header("Cookie")
            b.header("Cookie", if (old.isNullOrBlank()) cookies else "$old; $cookies")
        }
        val res: Response = chain.proceed(b.build())
        // Çerez süresi dolduysa kullanıcıya tekrar sorulsun
        if (res.code == 403 && res.header("Server")?.contains("cloudflare", true) == true) {
            verified.remove(root(req.url.host))
            runCatching { appContext.getSharedPreferences("cf", 0).edit().remove(root(req.url.host)).apply() }
        }
        res
    }
}

class CfVerifyActivity : AppCompatActivity() {
    private var status by mutableStateOf("Sayfa açılıyor…")
    private lateinit var web: WebView
    private var done = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: run { finish(); return }
        CookieManager.getInstance().setAcceptCookie(true)
        web = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, u: String) { check(u) }
            }
        }
        web.loadUrl(url)
        // Doğrulama arka planda da tamamlanabilir: her saniye kontrol et
        val tick = object : Runnable {
            override fun run() { if (!done) { check(web.url ?: url); web.postDelayed(this, 1000) } }
        }
        web.postDelayed(tick, 1500)
        setContent { AppTheme { Screen() } }
    }

    private fun check(u: String) {
        if (done) return
        val title = web.title.orEmpty()
        val challenge = title.contains("Just a moment", true) || title.contains("Attention Required", true) ||
            title.contains("Bir dakika", true) || title.contains("Checking", true)
        val cookies = CookieManager.getInstance().getCookie(u).orEmpty()
        status = if (challenge) "Doğrulama bekleniyor… Gerekirse kutucuğa dokun." else "Sayfa açıldı"
        if (!challenge && cookies.contains("cf_clearance")) {
            done = true
            CookieManager.getInstance().flush()
            CfVerify.markVerified(u, web.settings.userAgentString)
            Toast.makeText(this, "Doğrulandı, eklenti yeniden deneniyor", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() { if (::web.isInitialized) web.destroy(); super.onDestroy() }

    @Composable
    private fun Screen() {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF12151B)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, tint = Color(0xFFFF9F0A))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("Cloudflare doğrulaması", fontWeight = FontWeight.Bold)
                    Text(status, color = Muted, fontSize = 12.sp)
                }
                TextButton(onClick = {
                    // Kullanıcı sayfanın açıldığını görüyorsa çerez olmasa da kimliği kaydet
                    CfVerify.markVerified(web.url ?: "", web.settings.userAgentString); finish()
                }) { Text("Bitti") }
                IconButton(onClick = { finish() }) { Icon(Icons.Default.Close, "Kapat") }
            }
            AndroidView({ web }, Modifier.fillMaxSize())
        }
    }
}
