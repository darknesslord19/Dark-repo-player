package com.streambox.app.sites

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.streambox.app.AppLog
import org.json.JSONObject

/**
 * Sayfanın TÜM çerçevelerine (başka sitedeki iframe oynatıcılar dahil) en başta bir betik ekler:
 * - XHR/fetch yanıtlarına bakar: içerik HLS/DASH ise (adresinde .m3u8 olmasa bile) bildirir,
 * - JW Player / video.js / <video> kaynaklarını okur,
 * - [auto] açıksa oynatıcıyı kendiliğinden başlatır (görünmez "doğrudan oynat" modu).
 * Bulunan adres, istendiği çerçevenin adresiyle (Referer) birlikte [onFound]'a gelir.
 */
object Sniff {
    private fun script(auto: Boolean) = """
(function(){
  if (window.__dbxSniff) return; window.__dbxSniff = 1;
  var AUTO = $auto;
  var sent = {};
  function abs(u){ try { return new URL(u, location.href).href; } catch(e) { return null; } }
  function report(u, why, kind, subs, body, finalUrl){
    try {
      if (!u || typeof u !== 'string') return;
      if (u.indexOf('blob:') === 0 || u.indexOf('data:') === 0) return;
      u = abs(u); if (!u) return;
      var key = u + (subs && subs.length ? '#s' : '') + (body ? '#b' : '');
      if (sent[key]) return; sent[key] = 1;
      DbxSniff.postMessage(JSON.stringify({url: u, page: location.href, why: why, kind: kind || '', subs: subs || [],
        body: body || '', finalUrl: finalUrl ? abs(finalUrl) : ''}));
    } catch(e) {}
  }
  // Reklam (preroll/VAST) videolarını ayıkla
  // Yalnızca açıkça reklam olan videolar (preroll / VAST / reklam kutusu); sayfa genelindeki "ads" sınıflarına bakılmaz
  var adRe = /(preroll|vast|(^|[\s_-])ima([\s_-]|$)|(^|[\s_-])ads?[-_]?(video|player|container|overlay|wrap|box|slot)([\s_-]|$)|be-ad)/i;
  function isAdEl(el){
    for (var n = el, d = 0; n && d < 3; n = n.parentElement, d++) {
      var c = (typeof n.className === 'string' ? n.className : '') + ' ' + (n.id || '');
      if (adRe.test(c)) return true;
    }
    return false;
  }
  function isManifest(ct, text){
    ct = (ct || '').toLowerCase();
    if (ct.indexOf('mpegurl') >= 0 || ct.indexOf('dash+xml') >= 0) return true;
    return typeof text === 'string' && (text.indexOf('#EXTM3U') === 0 || text.indexOf('<MPD') >= 0);
  }
  // XHR
  var open = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function(m, u){ this.__dbxUrl = u; return open.apply(this, arguments); };
  var send = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function(){
    var x = this;
    x.addEventListener('load', function(){
      try {
        var full = (x.responseType === '' || x.responseType === 'text') ? (x.responseText || '') : null;
        var t = full ? full.slice(0, 64) : null;
        var ct = x.getResponseHeader('content-type') || '';
        if (isManifest(ct, t)) report(x.__dbxUrl || x.responseURL, 'xhr', (ct.indexOf('dash') >= 0 || (t && t.indexOf('<MPD') >= 0)) ? 'dash' : 'hls',
          null, (full && full.length < 3000000) ? full : '', x.responseURL);
      } catch(e) {}
    });
    return send.apply(this, arguments);
  };
  // fetch
  if (window.fetch) {
    var f = window.fetch;
    window.fetch = function(){
      var p = f.apply(this, arguments);
      try {
        p.then(function(r){
          try {
            var ct = r.headers.get('content-type');
            if (isManifest(ct, null) || !ct || ct.indexOf('text') >= 0 || ct.indexOf('octet') >= 0)
              r.clone().text().then(function(t){
                if (isManifest(ct, t.slice(0, 64))) report(r.url, 'fetch', (t.indexOf('<MPD') >= 0 || (ct || '').indexOf('dash') >= 0) ? 'dash' : 'hls', null, t.length < 3000000 ? t : '', r.url);
              }).catch(function(){});
          } catch(e) {}
        }).catch(function(){});
      } catch(e) {}
      return p;
    };
  }
  // Oynatıcılar
  var tries = 0;
  var timer = setInterval(function(){
    tries++;
    try {
      if (window.jwplayer) {
        var j = window.jwplayer();
        if (j && j.getPlaylistItem) {
          var it = j.getPlaylistItem();
          if (it) {
            var subs = (it.tracks || []).filter(function(t){ return t && t.file && (!t.kind || t.kind === 'captions' || t.kind === 'subtitles'); })
              .map(function(t){ return {file: abs(t.file), label: t.label || t.language || 'Altyazı'}; });
            var srcs = (it.sources && it.sources.length) ? it.sources : [{file: it.file, type: it.type}];
            srcs.forEach(function(s){
              var ty = String(s.type || it.type || '');
              var kind = /hls|m3u8|mpegurl/i.test(ty) ? 'hls' : (/dash|mpd/i.test(ty) ? 'dash' : '');
              report(s.file, 'jw', kind, subs);
            });
          }
          if (AUTO && j.getState && j.getState() !== 'playing') { try { j.setMute(true); j.play(); } catch(e) {} }
        }
      }
      if (window.videojs && window.videojs.getPlayers) {
        var ps = window.videojs.getPlayers();
        for (var k in ps) { try { report(ps[k].currentSrc(), 'videojs'); if (AUTO) { ps[k].muted(true); ps[k].play(); } } catch(e) {} }
      }
      document.querySelectorAll('video, video source').forEach(function(v){
        if (isAdEl(v)) return;
        report(v.currentSrc || v.src, 'video');
        if (AUTO && v.tagName === 'VIDEO' && v.paused) { v.muted = true; var r = v.play(); if (r && r.catch) r.catch(function(){}); }
      });
      if (tries === 2 || tries === 8) {
        // Sayfa kaynağında açıkça yazan video adresleri (script içindeki file:/source: vb.)
        var html = document.documentElement ? document.documentElement.innerHTML : '';
        var re = /https?:\\?\/\\?\/[^"'\s<>()]+?\.(m3u8|mp4|mkv|webm|mpd)(\?[^"'\s<>()]*)?/gi, m, n = 0;
        while ((m = re.exec(html)) && n < 20) { n++; var u = m[0].replace(/\\\//g, '/'); if (!/thumb|preview|trailer|fragman|sprite/i.test(u)) report(u, 'html', /m3u8/i.test(m[1]) ? 'hls' : (/mpd/i.test(m[1]) ? 'dash' : '')); }
      }
      if (AUTO && !window.__dbxWatch && tries >= 2) {
        // Detay sayfasındaki "Hemen İzle / İzle / 1. Bölüm" düğmesine bir kez bas
        var cand = Array.prototype.slice.call(document.querySelectorAll('a, button'));
        var w = cand.filter(function(e){
          var t = (e.innerText || '').trim().toLowerCase();
          return t.length < 30 && /^(▶\s*)?(hemen izle|şimdi izle|izlemeye başla|izle|filmi izle|bölümü izle|1\. bölüm|watch now|play now|watch|oynat)$/.test(t) && e.offsetParent !== null;
        })[0];
        if (w) { window.__dbxWatch = 1; try { w.click(); } catch(x) {} }
      }
      if (AUTO && tries % 3 === 1) {
        ['.jw-display-icon-container','.vjs-big-play-button','.plyr__control--overlaid','.play-button','.btn-play','.play',
         '[class*=play-btn]','[class*=playbtn]','.fp-play','#play','.player-poster','[aria-label=Play]'].forEach(function(s){
          var e = document.querySelector(s); if (e) { try { e.click(); } catch(x) {} }
        });
      }
    } catch(e) {}
    if (tries > 40) clearInterval(timer);
  }, 750);
})();
"""

    /** Çerçeveler arası betik destekleniyorsa kurar; desteklenmiyorsa false (eski yöntemle devam edilir). */
    /** Yakalanan video: [kind] "hls" / "dash" / "" (bilinmiyor), [subs] (ad, adres). */
    data class Hit(val url: String, val page: String, val kind: String, val subs: List<Pair<String, String>>)

    /** Tek kullanımlık liste adresleri: tarayıcının aldığı içerik saklanır, oynatıcı onu kullanır. */
    private fun cacheBody(o: JSONObject) {
        val body = o.optString("body")
        if (body.isNotBlank()) ManifestCache.put(o.optString("url"), o.optString("finalUrl").ifBlank { null }, body)
    }

    fun install(web: WebView, auto: Boolean, onFound: (Hit) -> Unit): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false
        return runCatching {
            WebViewCompat.addWebMessageListener(web, "DbxSniff", setOf("*")) { _, message, _, _, _ ->
                val o = runCatching { JSONObject(message.data ?: "") }.getOrNull() ?: return@addWebMessageListener
                val url = o.optString("url")
                cacheBody(o)
                if (url.startsWith("http") && !AdBlock.isAd(url) && !url.contains("googlevideo", true)) {
                    AppLog.i("Video yakalandı (${o.optString("why")}${o.optString("kind").let { k -> if (k.isBlank()) "" else ", $k" }}): $url")
                    val subs = o.optJSONArray("subs")?.let { a ->
                        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { s -> s.optString("label", "Altyazı") to s.optString("file") } }
                            .filter { it.second.startsWith("http") }
                    }.orEmpty()
                    onFound(Hit(url, o.optString("page"), o.optString("kind"), subs))
                }
            }
            WebViewCompat.addDocumentStartJavaScript(web, script(auto), setOf("*"))
            true
        }.onFailure { AppLog.e("Video yakalayıcı kurulamadı", it) }.getOrDefault(false)
    }

    /** Sitenin kendi süs videosu mu (ör. /site.webm, /bg.mp4): aynı sitede, kök klasörde kısa dosya. */
    fun isDecor(url: String, pageUrl: String): Boolean {
        val u = Uri.parse(url); val p = Uri.parse(pageUrl)
        fun root(h: String?) = (h ?: "").removePrefix("www.").split('.').takeLast(2).joinToString(".")
        if (root(u.host) != root(p.host)) return false
        val segs = u.pathSegments ?: return false
        return segs.size <= 1 && Regex("(?i)\\.(webm|mp4)$").containsMatchIn(u.path ?: "")
    }

    /** Video isteği için başlıklar: istendiği çerçeve Referer/Origin olur. */
    fun headers(page: String, ua: String?, cookieUrl: String): Map<String, String> {
        val h = mutableMapOf<String, String>()
        if (page.startsWith("http")) {
            h["Referer"] = page
            Uri.parse(page).let { u -> if (u.scheme != null && u.host != null) h["Origin"] = "${u.scheme}://${u.host}" }
        }
        // Tarayıcı gibi görün (bazı video sunucuları bu başlıklar yoksa 404/403 döner)
        h["Accept"] = "*/*"
        h["Accept-Language"] = "tr-TR,tr;q=0.9,en;q=0.8"
        h["Sec-Fetch-Dest"] = "empty"
        h["Sec-Fetch-Mode"] = "cors"
        h["Sec-Fetch-Site"] = if (Uri.parse(page).host == Uri.parse(cookieUrl).host) "same-origin" else "cross-site"
        ua?.let { h["User-Agent"] = it }
        // Tarayıcı bu adresi gerçekten istediyse onun gönderdiği başlıklar esas alınır
        HeaderBook.get(cookieUrl)?.forEach { (k, v) ->
            h.keys.firstOrNull { it.equals(k, true) }?.let { h.remove(it) }
            h[k] = v
        }
        android.webkit.CookieManager.getInstance().getCookie(cookieUrl)?.takeIf { it.isNotBlank() }?.let { h["Cookie"] = it }
        return h
    }
}
