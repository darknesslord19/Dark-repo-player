package com.streambox.app.sites

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Reklam ve izleyici engelleyici: bilinen reklam ağları + adres kalıpları + gizleme CSS'i. */
object AdBlock {
    private val hosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com", "googletagmanager.com",
        "googletagservices.com", "adservice.google.com", "pagead2.googlesyndication.com", "imasdk.googleapis.com",
        "popads.net", "popcash.net", "propellerads.com", "propellerclick.com", "adsterra.com", "adsterratools.com",
        "exoclick.com", "exosrv.com", "juicyads.com", "trafficjunky.net", "trafficstars.com", "tsyndicate.com",
        "hilltopads.net", "hilltopads.com", "clickadu.com", "adcash.com", "admaven.com", "ad-maven.com", "a-ads.com",
        "onclickads.net", "onclckads.net", "onclkads.com", "popunder.net", "poptm.com", "pemsrv.com", "realsrv.com",
        "mgid.com", "taboola.com", "outbrain.com", "revcontent.com", "zergnet.com", "adnxs.com", "rubiconproject.com",
        "pubmatic.com", "openx.net", "criteo.com", "criteo.net", "amazon-adsystem.com", "adform.net", "smartadserver.com",
        "yandex.ru/ads", "an.yandex.ru", "mc.yandex.ru", "adfox.ru", "bidvertiser.com", "infolinks.com", "media.net",
        "vidoomy.com", "adskeeper.com", "adskeeper.co.uk", "monetag.com", "galaksion.com", "richads.com", "evadav.com",
        "pushground.com", "rollerads.com", "dtscout.com", "dtscdn.com", "acint.net", "ufouxbwn.com", "hotjar.com",
        "scorecardresearch.com", "quantserve.com", "histats.com", "statcounter.com", "yllix.com", "adspyglass.com",
        "bongacams.com", "chaturbate.com", "stripchat.com", "livejasmin.com", "1xbet.com", "melbet.com", "mostbet.com",
        "betwinner.com", "pin-up.bet", "bets10.com", "nesine.com", "sportingbet.com", "betboo.com", "casino.com",
        "ad.plus", "adtelligent.com", "spotxchange.com", "springserve.com", "aniview.com", "vdo.ai", "adsco.re",
        "cloudfront.net/ads", "disqusads.com", "zeusadx.com", "adplus.com.tr", "admatic.com.tr", "reklamstore.com",
        "reklamaction.com", "ademsrv.com", "adtr.co", "medyanet.net", "mobiteasy.com", "adsbygoogle", "highperformanceformat.com",
        "profitablegatecpm.com", "profitableratecpm.com", "effectivegatecpm.com", "highcpmgate.com", "cpmgatenetwork.com",
        "displayvertising.com", "toprevenuegate.com", "adsessionserv.com", "whos.amung.us", "mydirtyhobby.com",
    )
    private val patterns = Regex(
        "(?i)(/ads?/|/ad[sx]?\\.|[/._-]adserv|/banners?/|/popunder|/popup|/pop\\.js|/prebid|/vast[/.?]|/vpaid|" +
        "/advert|[?&]ad_?id=|/adframe|/afs/|/sponsor|/track(ing)?\\.|/analytics\\.js|/gtag/js|/clickunder)"
    )

    fun isAd(url: String): Boolean {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = (u.host ?: "").lowercase()
        if (hosts.any { host == it || host.endsWith(".$it") || (it.contains('/') && url.contains(it)) }) return true
        // Video adresleri asla engellenmesin
        if (VideoSniffer.isVideo(url)) return false
        return patterns.containsMatchIn(u.path ?: "") || patterns.containsMatchIn(u.encodedQuery ?: "")
    }

    val empty: WebResourceResponse get() = WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))

    /** Kalan reklam kutularını gizleyen CSS + açılır pencere/yönlendirme engelleyen betik. */
    const val COSMETIC_JS = """
(function(){
  if (window.__dbx) return; window.__dbx = 1;
  var css = 'ins.adsbygoogle,[id^="google_ads"],[id*="-ad-"],[class*="adsbox"],[class*="ad-container"],[class*="ads-container"],' +
    '[class*="advert"],[class*="banner-ad"],[id*="banner"],[class*="reklam"],[id*="reklam"],[class*="popup"],[id*="popup"],' +
    '[class*="sticky-ad"],[class*="overlay-ad"],iframe[src*="ads"],iframe[src*="banner"],div[style*="z-index: 2147483647"],' +
    'a[href*="bet"],a[href*="casino"]{display:none!important;visibility:hidden!important}';
  var s = document.createElement('style'); s.textContent = css; (document.head||document.documentElement).appendChild(s);
  window.open = function(){ return null; };
  document.addEventListener('click', function(e){
    var a = e.target && e.target.closest ? e.target.closest('a[target=_blank]') : null;
    if (a && a.host && a.host !== location.host) { e.preventDefault(); e.stopPropagation(); }
  }, true);
})();
"""
}

/** Sayfa içinde istenen video adreslerini yakalar (m3u8 / mp4 / mpd …). */
object VideoSniffer {
    private val video = Regex("(?i)(\\.m3u8|\\.mp4|\\.mkv|\\.webm|\\.mpd|/manifest|/playlist\\.m3u|master\\.txt|/hls/|\\.ts\\?|/video\\.mp4)")
    private val skip = Regex("(?i)(\\.ts($|\\?)|\\.m4s|\\.aac|/seg-|segment|\\.jpg|\\.png|\\.gif|\\.vtt|\\.srt|thumb|preview|\\.js($|\\?)|\\.css)")
    fun isVideo(url: String): Boolean = video.containsMatchIn(url) && !skip.containsMatchIn(url.substringBefore('?').takeLast(40)) ||
        url.contains(".m3u8", true)
}

/** Gizli tarayıcıda sayfayı açıp JavaScript ile oluşan HTML'i döndürür (Cloudflare/JS siteleri için). */
object SiteRenderer {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun render(context: Context, url: String, waitMs: Long = 4500): String? = withContext(kotlinx.coroutines.Dispatchers.Main) {
        val wv = WebView(context.applicationContext)
        try {
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.blockNetworkImage = true
            wv.layout(0, 0, 1280, 2400)
            // Tarayıcının kendi kimliği: Cloudflare doğrulaması (cf_clearance) bu kimliğe bağlıdır
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
            withTimeoutOrNull(30_000) {
                suspendCancellableCoroutine { cont ->
                    var done = false
                    wv.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? =
                            if (AdBlock.isAd(req.url.toString())) AdBlock.empty else null
                        override fun onPageFinished(view: WebView, u: String?) {
                            if (done) return
                            // Tembel yüklenen afişler için sayfayı aşağı kaydır
                            view.evaluateJavascript("window.scrollTo(0, document.body.scrollHeight/2); setTimeout(function(){window.scrollTo(0, document.body.scrollHeight)}, 800);", null)
                            view.postDelayed({
                                // Cloudflare "bekleyin" sayfasıysa bir sonraki yüklemeyi bekle
                                view.evaluateJavascript("document.title") { t ->
                                    if (t.contains("Just a moment", true) || t.contains("Bir dakika", true)) return@evaluateJavascript
                                    view.evaluateJavascript("document.documentElement.outerHTML") { html ->
                                        if (done) return@evaluateJavascript
                                        done = true
                                        val s = runCatching { org.json.JSONArray("[$html]").getString(0) }.getOrNull()
                                        if (cont.isActive) cont.resume(s)
                                    }
                                }
                            }, waitMs)
                        }
                    }
                    wv.loadUrl(url)
                }
            }
        } finally {
            delay(50)
            wv.stopLoading(); wv.destroy()
        }
    }
}
