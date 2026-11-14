package com.streambox.app.sites

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.streambox.app.AppLog
import com.streambox.app.player.PlayItem
import com.streambox.app.player.PlayerActivity
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.launch

/**
 * "Doğrudan oynat": sayfayı görünmez bir tarayıcıda (reklamlar engellenmiş halde) açar,
 * oynatıcıyı kendiliğinden başlatır, yüklenen video adresini yakalayıp DarknesBox oynatıcısında açar.
 * Sayfadaki oynatıcı başka bir adreste (iframe) ise o adres ayrıca açılıp denenir.
 */
object SiteResolver {
    /** Ekranda "Video aranıyor…" gösterilen içerik (null: boşta). */
    var active by mutableStateOf<SiteItem?>(null)
        private set
    var status by mutableStateOf("")
        private set

    private var web: WebView? = null
    private var found = mutableListOf<SniffedVideo>()
    private var finishJob: Runnable? = null
    private var streamScheduled = false
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Sayfadaki oynat düğmelerine basar, iframe adreslerini döndürür. */
    private const val CLICK_JS = """
(function(){
  try {
    var sel = ['.play','.play-button','.btn-play','.vjs-big-play-button','.jw-display-icon-container','.plyr__control--overlaid',
      '[class*=play-btn]','[class*=playbtn]','[class*=oynat]','button[aria-label*=Play]','.fp-play','#play','.player-poster','.dooplay_player_option',
      '.video-play','.watch-btn','[data-play]'];
    sel.forEach(function(s){ var e = document.querySelector(s); if (e) { try { e.click(); } catch(x){} } });
    var v = document.querySelector('video'); if (v) { v.muted = true; var p = v.play(); if (p && p.catch) p.catch(function(){}); }
  } catch(e) {}
  var f = []; document.querySelectorAll('iframe').forEach(function(i){
    var s = i.getAttribute('src') || i.getAttribute('data-src') || i.getAttribute('data-lazy-src');
    if (s) { try { f.push(new URL(s, location.href).href); } catch(e) {} }
  });
  return JSON.stringify(f);
})();
"""

    fun cancel() { main.post { cancel0() } }

    @SuppressLint("SetJavaScriptEnabled")
    fun play(context: Context, site: Site, item: SiteItem) {
        main.post {
            cancel0()
            active = item
            status = "Sayfa açılıyor…"
            found = mutableListOf()
            val app = context.applicationContext
            val wv = WebView(app)
            web = wv
            wv.resumeTimers(); wv.onResume()
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.mediaPlaybackRequiresUserGesture = false
            wv.settings.blockNetworkImage = true
            wv.settings.javaScriptCanOpenWindowsAutomatically = false
            wv.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
            val iframes = ArrayDeque<String>()
            val tried = HashSet<String>()
            var currentPage = item.url

            wv.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                    val u = req.url.toString()
                    if (AdBlock.isAd(u)) return AdBlock.empty
                    HeaderBook.put(u, req.requestHeaders)
                    if (VideoSniffer.isVideo(u)) main.post { onVideo(context, site, item, u, req.requestHeaders ?: emptyMap(), currentPage) }
                    return null
                }
                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                    val u = req.url.toString()
                    return !u.startsWith("http") || AdBlock.isAd(u)
                }
                override fun onPageFinished(view: WebView, url: String) {
                    if (web !== view) return
                    status = "Video aranıyor…"
                    // Oynatıcının yüklenmesi için kısa bekle, oynat düğmelerine bas, iframe'leri topla
                    listOf(800L, 2500L, 4500L).forEach { d ->
                        main.postDelayed({
                            if (web !== view || found.isNotEmpty()) return@postDelayed
                            view.evaluateJavascript(CLICK_JS) { r ->
                                runCatching { JSONArray(JSONArray("[$r]").getString(0)) }.getOrNull()?.let { a ->
                                    for (i in 0 until a.length()) a.getString(i).takeIf { it.startsWith("http") && it !in tried && !AdBlock.isAd(it) }?.let { iframes += it; tried += it }
                                }
                            }
                        }, d)
                    }
                    // Sayfada video çıkmadıysa oynatıcı iframe'ini doğrudan aç
                    main.postDelayed({
                        if (web !== view || found.isNotEmpty()) return@postDelayed
                        val next = iframes.removeFirstOrNull() ?: return@postDelayed
                        status = "Oynatıcı açılıyor…"
                        val ref = currentPage
                        currentPage = next
                        view.loadUrl(next, mapOf("Referer" to ref))
                    }, 5000)
                }
            }
            Sniff.install(wv, auto = true) { hit ->
                onVideo(context, site, item, hit.url, Sniff.headers(hit.page.ifBlank { currentPage }, wv.settings.userAgentString, hit.url),
                    hit.page.ifBlank { currentPage }, hit.kind, hit.subs)
            }
            wv.loadUrl(item.url, mapOf("Referer" to site.url + "/"))
            // Süre doldu: bulunanı oynat ya da sayfayı tarayıcıda aç
            val timeout = Runnable {
                if (web !== wv) return@Runnable
                if (found.isNotEmpty()) start(context, site, item) else {
                    AppLog.i("Doğrudan oynatma: video bulunamadı → tarayıcı (${item.url})")
                    Toast.makeText(app, "Video otomatik bulunamadı, sayfa açılıyor", Toast.LENGTH_SHORT).show()
                    cancel0()
                    SiteBrowserActivity.open(context, item.url, item.title, item.poster)
                }
            }
            main.postDelayed(timeout, 30_000)
        }
    }

    private fun cancel0() {
        finishJob?.let { main.removeCallbacks(it) }; finishJob = null; streamScheduled = false; waitedForBody = 0
        web?.let { it.stopLoading(); it.destroy() }
        web = null
        active = null
    }

    private fun onVideo(context: Context, site: Site, item: SiteItem, url: String, req: Map<String, String>, page: String,
                        kindHint: String = "", subs: List<Pair<String, String>> = emptyList()) {
        if (web == null) return
        found.indexOfFirst { it.url == url }.takeIf { it >= 0 }?.let { i ->
            val v = found[i]
            if ((kindHint.isNotBlank() && !v.isStream) || (subs.isNotEmpty() && v.subs.isEmpty()))
                found[i] = v.copy(label = if (kindHint.isNotBlank()) kindHint.uppercase() + " · " + v.label.substringAfter(" · ") else v.label, subs = subs.ifEmpty { v.subs })
            return
        }
        if (url.contains(".m3u8", true) && found.any { it.url.substringBefore('?').substringBeforeLast('/') == url.substringBefore('?').substringBeforeLast('/') }) return
        val h = mutableMapOf<String, String>()
        req.forEach { (k, v) -> if (k.lowercase() in setOf("referer", "origin", "user-agent")) h[k] = v }
        if (h.keys.none { it.equals("Referer", true) }) h["Referer"] = page
        if (h.keys.none { it.equals("User-Agent", true) }) web?.settings?.userAgentString?.let { h["User-Agent"] = it }
        CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { h["Cookie"] = it }
        val kind = when {
            kindHint == "hls" -> "HLS"; kindHint == "dash" -> "DASH"
            url.contains(".m3u8", true) || url.contains("master.txt", true) -> "HLS"; url.contains(".mpd", true) -> "DASH"; else -> "MP4"
        }
        val v = SniffedVideo(url, h, "$kind · ${runCatching { java.net.URI(url).host }.getOrNull() ?: ""}", subs)
        found += v
        status = "Video bulundu"
        // Yayın listesi geldiyse 1,2 sn içinde; yalnızca MP4 varsa (reklam olabilir) en çok 5 sn bekle.
        // Zamanlayıcı yalnızca ilk yayın listesinde bir kez öne çekilir; yeni adresler gelmeye devam etse de ertelenmez.
        val stream = v.isStream
        if (finishJob == null || (stream && !streamScheduled)) {
            if (stream) streamScheduled = true
            finishJob?.let { main.removeCallbacks(it) }
            val r = Runnable { start(context, site, item) }
            finishJob = r
            main.postDelayed(r, if (stream) 1200L else 5000L)
        }
    }

    private var waitedForBody = 0

    private fun start(context: Context, site: Site, item: SiteItem) {
        // Tarayıcı listeyi henüz indirmediyse (tek kullanımlık adreslerde gerekli) biraz daha bekle
        if (found.any { it.isStream } && found.none { ManifestCache.has(it.url) } && waitedForBody < 3 && web != null) {
            waitedForBody++
            val r = Runnable { start(context, site, item) }
            finishJob = r
            main.postDelayed(r, 1000)
            return
        }
        waitedForBody = 0
        val list = found.toList()
        if (list.isEmpty()) { cancel0(); return }
        status = "Videolar kontrol ediliyor (${list.size})…"
        val wv = web
        com.streambox.app.StreamBoxApp.instance!!.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            // Hepsini aynı anda yokla: açılmayanlar elenir, en uzun (asıl film) önce gelir
            val ranked = VideoProbe.rank(list, 7_000)
            if (web !== wv) return@launch // iptal edildi
            cancel0()
            val meta = JSONObject().put("kind", "site").put("title", item.title).put("subtitle", site.name)
                .put("poster", item.poster ?: "").put("url", item.url).toString()
            val items = ranked.map { r ->
                val v = r.video
                PlayItem(item.title, v.url, v.headers,
                    kind = when (r.kind) { "HLS" -> PlayItem.Kind.HLS; "DASH" -> PlayItem.Kind.DASH; "MP4", "MKV", "WEBM", "TS" -> PlayItem.Kind.PROGRESSIVE; else -> if (v.isStream) PlayItem.Kind.HLS else PlayItem.Kind.AUTO },
                    subtitles = v.subs.map { (l, u) -> com.streambox.app.player.PlaySub(l, u) },
                    label = (if (r.ok) "" else "✗ ") + r.label, resumeKey = "site|${item.url.substringBefore('#')}", meta = meta)
            }
            val ctx = com.streambox.app.ActivityTracker.top?.get() ?: context
            runCatching { PlayerActivity.start(ctx, items, 0) }.onFailure {
                AppLog.e("Oynatıcı açılamadı", it)
                Toast.makeText(context.applicationContext, "Oynatıcı açılamadı: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Ayara göre: doğrudan oynat ya da reklamsız tarayıcıda aç. */
    fun open(context: Context, site: Site?, item: SiteItem) {
        if (site?.directPlay == true) play(context, site, item)
        else SiteBrowserActivity.open(context, item.url, item.title, item.poster)
    }
}
