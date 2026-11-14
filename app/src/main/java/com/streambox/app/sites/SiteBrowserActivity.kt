package com.streambox.app.sites

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.streambox.app.WatchHistory
import com.streambox.app.player.PlayItem
import com.streambox.app.player.PlayerActivity
import com.streambox.app.ui.Accent
import com.streambox.app.ui.AppTheme
import com.streambox.app.ui.Muted
import org.json.JSONObject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.net.URI

/** Sayfada yakalanan video. */
data class SniffedVideo(val url: String, val headers: Map<String, String>, val label: String, val subs: List<Pair<String, String>> = emptyList()) {
    val isStream get() = label.startsWith("HLS") || label.startsWith("DASH")
}

/**
 * Eklenen siteleri reklamsız açan tarayıcı: reklam ağlarını ve başka sitelere yönlendirmeleri engeller,
 * açılır pencereleri kapatır, sayfadaki videoyu yakalayıp DarknesBox oynatıcısında açar.
 */
class SiteBrowserActivity : AppCompatActivity() {
    companion object {
        fun open(context: Context, url: String, title: String? = null, poster: String? = null) {
            val i = Intent(context, SiteBrowserActivity::class.java).putExtra("url", url).putExtra("title", title).putExtra("poster", poster)
            if (context !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }

    private val videos = mutableStateListOf<SniffedVideo>()
    private var pageTitle by mutableStateOf("")
    private var pageUrl by mutableStateOf("")
    private var loading by mutableStateOf(true)
    private var blocked by mutableIntStateOf(0)
    private var offer by mutableStateOf<SniffedVideo?>(null)
    private var showList by mutableStateOf(false)
    private var customView by mutableStateOf<View?>(null)
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var offeredFor: String? = null
    private lateinit var web: WebView
    private var poster: String? = null

    private fun siteOf(u: String) = runCatching { URI(u).host?.lowercase()?.removePrefix("www.") }.getOrNull() ?: ""
    /** "a.b.example.com" → "example.com" (aynı sitenin alt adresleri serbest). */
    private fun root(host: String): String = host.split('.').takeLast(2).joinToString(".")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start = intent.getStringExtra("url") ?: run { finish(); return }
        pageTitle = intent.getStringExtra("title") ?: siteOf(start)
        poster = intent.getStringExtra("poster")
        pageUrl = start
        val startRoot = root(siteOf(start))

        web = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = false
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            isFocusable = true; isFocusableInTouchMode = true

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                    val u = req.url.toString()
                    if (AdBlock.isAd(u)) { runOnUiThread { blocked++ }; return AdBlock.empty }
                    if (VideoSniffer.isVideo(u)) found(u, req.requestHeaders ?: emptyMap())
                    return null
                }

                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                    val u = req.url.toString()
                    if (!u.startsWith("http")) return true // intent://, market:// vb. reklam uygulama yönlendirmeleri
                    if (AdBlock.isAd(u)) { blocked++; return true }
                    if (req.isForMainFrame && SiteManager.blockRedirects && root(siteOf(u)) != startRoot && root(siteOf(u)) != root(siteOf(pageUrl))) {
                        blocked++
                        Toast.makeText(this@SiteBrowserActivity, "Başka siteye yönlendirme engellendi", Toast.LENGTH_SHORT).show()
                        return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    loading = true; pageUrl = url
                    view.evaluateJavascript(AdBlock.COSMETIC_JS, null)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    loading = false; pageUrl = url
                    view.title?.takeIf { it.isNotBlank() && !it.startsWith("http") }?.let { pageTitle = it }
                    view.evaluateJavascript(AdBlock.COSMETIC_JS, null)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                    if (SiteManager.blockPopups) { blocked++; return false }
                    return false
                }
                override fun onShowCustomView(view: View, callback: CustomViewCallback) { customView = view; customCallback = callback }
                override fun onHideCustomView() { customView = null; customCallback?.onCustomViewHidden(); customCallback = null }
            }
        }
        // Tüm çerçevelerde (iframe oynatıcılar dahil) video adresini yakala
        Sniff.install(web, auto = false) { hit ->
            found(hit.url, Sniff.headers(hit.page.ifBlank { pageUrl }, web.settings.userAgentString, hit.url), framed = true, kindHint = hit.kind, subs = hit.subs)
        }
        web.loadUrl(start)

        setContent { AppTheme { Screen() } }
    }

    /** Video adresi yakalandı: listeye ekle, gerekirse oynatmayı öner. */
    private fun found(url: String, reqHeaders: Map<String, String>, framed: Boolean = false, kindHint: String = "", subs: List<Pair<String, String>> = emptyList()) {
        runOnUiThread {
            videos.indexOfFirst { it.url == url }.takeIf { it >= 0 }?.let { i ->
                // Aynı video daha ayrıntılı bilgiyle geldiyse (tür / altyazı) güncelle
                val v = videos[i]
                if ((kindHint.isNotBlank() && !v.isStream) || (subs.isNotEmpty() && v.subs.isEmpty()))
                    videos[i] = v.copy(label = if (kindHint.isNotBlank()) kindHint.uppercase() + " · " + siteOf(url) else v.label, subs = subs.ifEmpty { v.subs })
                return@runOnUiThread
            }
            // Aynı yayının parça listelerini tekrar ekleme
            if (url.contains(".m3u8", true) && videos.any { it.url.substringBefore('?').substringBeforeLast('/') == url.substringBefore('?').substringBeforeLast('/') }) return@runOnUiThread
            val h = mutableMapOf<String, String>()
            reqHeaders.forEach { (k, v) -> if (framed || k.lowercase() in setOf("referer", "origin", "user-agent")) h[k] = v }
            if (h.keys.none { it.equals("Referer", true) }) h["Referer"] = pageUrl
            if (h.keys.none { it.equals("User-Agent", true) }) h["User-Agent"] = web.settings.userAgentString
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { h["Cookie"] = it }
            val kind = when {
                kindHint == "hls" -> "HLS"
                kindHint == "dash" -> "DASH"
                url.contains(".m3u8", true) || url.contains("master.txt", true) -> "HLS"
                url.contains(".mpd", true) -> "DASH"
                else -> url.substringBefore('?').substringAfterLast('.', "video").uppercase().take(4)
            }
            val v = SniffedVideo(url, h, "$kind · ${siteOf(url)}", subs)
            videos += v
            if (SiteManager.autoVideo && offeredFor != pageUrl && customView == null && (v.isStream || framed)) { offeredFor = pageUrl; offer = v }
        }
    }

    private var probing by mutableStateOf(false)

    private fun play(index: Int) {
        if (probing) return
        val title = pageTitle.ifBlank { siteOf(pageUrl) }
        val meta = JSONObject().put("kind", "site").put("title", title).put("subtitle", siteOf(pageUrl))
            .put("poster", poster ?: "").put("url", pageUrl).toString()
        val chosen = videos.getOrNull(index)
        val list = videos.toList()
        probing = true
        lifecycleScope.launch {
            // Hepsini aynı anda yokla; kullanıcının seçtiği çalışıyorsa önce o, yoksa en uzun çalışan
            val ranked = VideoProbe.rank(list, 7_000)
            probing = false
            val ordered = ranked.sortedByDescending { if (it.video == chosen && it.ok && list.size > 1 && index > 0) Double.MAX_VALUE else it.score }
            val items = ordered.map { r ->
                val v = r.video
                PlayItem(title, v.url, v.headers,
                    kind = when (r.kind) { "HLS" -> PlayItem.Kind.HLS; "DASH" -> PlayItem.Kind.DASH; "MP4", "MKV", "WEBM", "TS" -> PlayItem.Kind.PROGRESSIVE; else -> if (v.isStream) PlayItem.Kind.HLS else PlayItem.Kind.AUTO },
                    subtitles = v.subs.map { (l, u) -> com.streambox.app.player.PlaySub(l, u) },
                    label = (if (r.ok) "" else "✗ ") + r.label, resumeKey = "site|${pageUrl.substringBefore('#')}", meta = meta)
            }
            web.onPause()
            PlayerActivity.start(this@SiteBrowserActivity, items, 0)
        }
    }

    override fun onResume() { super.onResume(); if (::web.isInitialized) web.onResume() }
    override fun onPause() { if (::web.isInitialized && customView == null) web.onPause(); super.onPause() }
    override fun onDestroy() { if (::web.isInitialized) { web.stopLoading(); web.destroy() }; super.onDestroy() }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen() {
        com.streambox.app.remote.RemoteNav.Hook()
        BackHandler {
            when {
                customView != null -> web.webChromeClient?.onHideCustomView()
                web.canGoBack() -> { web.goBack(); videos.clear(); offeredFor = null }
                else -> finish()
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // Üst çubuk
                Row(Modifier.fillMaxWidth().background(Color(0xFF12151B)).padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (web.canGoBack()) { web.goBack(); videos.clear() } else finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(pageTitle, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, null, tint = Color(0xFF34C759), modifier = Modifier.size(12.dp))
                            Text(" ${siteOf(pageUrl)} · $blocked reklam engellendi", color = Muted, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                    IconButton(onClick = { videos.clear(); offeredFor = null; web.reload() }) { Icon(Icons.Default.Refresh, "Yenile", tint = Color.White) }
                    if (videos.isNotEmpty()) Button(
                        onClick = { if (videos.size == 1) play(0) else showList = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent), contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Text(if (videos.size == 1) "Oynat" else "Videolar ${videos.size}", Modifier.padding(start = 4.dp))
                    }
                }
                if (loading || probing) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Accent)
                if (probing) Text("Videolar kontrol ediliyor…", color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF1C2638)).padding(horizontal = 12.dp, vertical = 4.dp))
                AndroidView({ web }, Modifier.fillMaxSize())
            }
            customView?.let { v ->
                AndroidView({ FrameLayout(it).apply { setBackgroundColor(android.graphics.Color.BLACK) } }, Modifier.fillMaxSize(),
                    update = { f -> if (v.parent == null) { f.removeAllViews(); f.addView(v, FrameLayout.LayoutParams(-1, -1)) } })
            }
        }

        offer?.let { v ->
            AlertDialog(
                onDismissRequest = { offer = null },
                icon = { Icon(Icons.Default.Movie, null, tint = Accent) },
                title = { Text("Video bulundu") },
                text = { com.streambox.app.remote.RemoteNav.Hook();  Text("Reklamsız izlemek için DarknesBox oynatıcısında açılsın mı?\n${v.label}", color = Color(0xFFC8CBD0)) },
                confirmButton = { Button(onClick = { offer = null; play(videos.indexOf(v).coerceAtLeast(0)) }) { Text("Oynat") } },
                dismissButton = { TextButton(onClick = { offer = null }) { Text("Sayfada kal") } },
            )
        }
        if (showList) ModalBottomSheet(onDismissRequest = { showList = false }, containerColor = Color(0xFF16181E)) {
            com.streambox.app.remote.RemoteNav.Hook()
            Text("Sayfada bulunan videolar", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 10.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 30.dp)) {
                itemsIndexed(videos.toList()) { i, v ->
                    Row(
                        Modifier.fillMaxWidth().clickable { showList = false; play(i) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                        }
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text("Video ${i + 1} · ${v.label}", fontWeight = FontWeight.SemiBold)
                            Text(v.url, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
