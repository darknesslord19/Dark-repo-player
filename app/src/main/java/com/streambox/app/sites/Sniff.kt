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
  function report(u, why, kind, subs){
    try {
      if (!u || typeof u !== 'string') return;
      if (u.indexOf('blob:') === 0 || u.indexOf('data:') === 0) return;
      u = abs(u); if (!u) return;
      var key = u + (subs && subs.length ? '#s' : '');
      if (sent[key]) return; sent[key] = 1;
      DbxSniff.postMessage(JSON.stringify({url: u, page: location.href, why: why, kind: kind || '', subs: subs || []}));
    } catch(e) {}
  }
  // Reklam (preroll/VAST) videolarını ayıkla
  var adRe = /(^|[\s_-])(ad|ads|advert|preroll|vast|ima|sponsor)([\s_-]|$)/i;
  function isAdEl(el){
    for (var n = el, d = 0; n && d < 8; n = n.parentElement, d++) {
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
        var t = (x.responseType === '' || x.responseType === 'text') ? (x.responseText || '').slice(0, 64) : null;
        var ct = x.getResponseHeader('content-type') || '';
        if (isManifest(ct, t)) report(x.responseURL || x.__dbxUrl, 'xhr', (ct.indexOf('dash') >= 0 || (t && t.indexOf('<MPD') >= 0)) ? 'dash' : 'hls');
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
            if (isManifest(ct, null)) report(r.url, 'fetch', (ct || '').indexOf('dash') >= 0 ? 'dash' : 'hls');
            else if (!ct || ct.indexOf('text') >= 0) r.clone().text().then(function(t){ if (isManifest('', t.slice(0, 64))) report(r.url, 'fetch', t.indexOf('<MPD') >= 0 ? 'dash' : 'hls'); }).catch(function(){});
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

    fun install(web: WebView, auto: Boolean, onFound: (Hit) -> Unit): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false
        return runCatching {
            WebViewCompat.addWebMessageListener(web, "DbxSniff", setOf("*")) { _, message, _, _, _ ->
                val o = runCatching { JSONObject(message.data ?: "") }.getOrNull() ?: return@addWebMessageListener
                val url = o.optString("url")
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

    /** Video isteği için başlıklar: istendiği çerçeve Referer/Origin olur. */
    fun headers(page: String, ua: String?, cookieUrl: String): Map<String, String> {
        val h = mutableMapOf<String, String>()
        if (page.startsWith("http")) {
            h["Referer"] = page
            Uri.parse(page).let { u -> if (u.scheme != null && u.host != null) h["Origin"] = "${u.scheme}://${u.host}" }
        }
        ua?.let { h["User-Agent"] = it }
        android.webkit.CookieManager.getInstance().getCookie(cookieUrl)?.takeIf { it.isNotBlank() }?.let { h["Cookie"] = it }
        return h
    }
}
