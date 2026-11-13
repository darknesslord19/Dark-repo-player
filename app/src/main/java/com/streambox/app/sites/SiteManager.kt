package com.streambox.app.sites

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.streambox.app.AppLog
import com.streambox.app.Net
import com.streambox.app.onMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder

/** Kullanıcının eklediği film/dizi sitesi. [search]: arama adresi kalıbı ({q} yerine sorgu). */
data class Site(
    val id: String,
    val name: String,
    val url: String,
    val icon: String? = null,
    val search: String? = null,
    val onHome: Boolean = true,
    /** Afişe basınca sayfayı göstermeden videoyu bulup doğrudan oynatıcıyı aç. */
    val directPlay: Boolean = false,
) {
    val host: String get() = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
}

/** Siteden çekilen afiş. */
data class SiteItem(val title: String, val url: String, val poster: String?, val subtitle: String? = null, val rating: String? = null)
/** Sitenin menüsündeki kategori (tür, yıl, ülke …). */
data class SiteCategory(val name: String, val url: String, val group: String)
data class SiteRow(val title: String, val items: List<SiteItem>)

object SiteManager {
    val sites = mutableStateListOf<Site>()
    /** Başka siteye yönlendirmeleri (reklam sayfaları) engelle. */
    var blockRedirects by mutableStateOf(true); private set
    /** Açılır pencereleri engelle. */
    var blockPopups by mutableStateOf(true); private set
    /** Sayfada video bulununca hemen oynatmayı öner. */
    var autoVideo by mutableStateOf(true); private set

    private lateinit var appContext: Context
    private val prefs get() = appContext.getSharedPreferences("sites", 0)

    fun init(context: Context) {
        appContext = context.applicationContext
        sites.clear()
        val arr = JSONArray(prefs.getString("list", "[]"))
        for (i in 0 until arr.length()) arr.getJSONObject(i).let {
            sites += Site(it.getString("id"), it.getString("name"), it.getString("url"), it.optString("icon").ifBlank { null },
                it.optString("search").ifBlank { null }, it.optBoolean("onHome", true), it.optBoolean("directPlay", false))
        }
        blockRedirects = prefs.getBoolean("blockRedirects", true)
        blockPopups = prefs.getBoolean("blockPopups", true)
        autoVideo = prefs.getBoolean("autoVideo", true)
    }

    private fun save() {
        val arr = JSONArray()
        sites.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url).put("icon", it.icon ?: "")
                .put("search", it.search ?: "").put("onHome", it.onHome).put("directPlay", it.directPlay))
        }
        prefs.edit().putString("list", arr.toString()).putBoolean("blockRedirects", blockRedirects)
            .putBoolean("blockPopups", blockPopups).putBoolean("autoVideo", autoVideo).apply()
    }

    fun updateBlockRedirects(v: Boolean) { blockRedirects = v; save() }
    fun updateBlockPopups(v: Boolean) { blockPopups = v; save() }
    fun updateAutoVideo(v: Boolean) { autoVideo = v; save() }

    fun normalize(input: String): String {
        var s = input.trim()
        if (!s.startsWith("http")) s = "https://$s"
        return s.trimEnd('/')
    }

    /** Siteyi ekler: ad, simge ve arama biçimi sayfadan otomatik bulunur. */
    suspend fun add(input: String, name: String?): Site {
        val url = normalize(input)
        val doc = runCatching { fetch(url) }.getOrNull()
        val title = name?.takeIf { it.isNotBlank() }
            ?: doc?.selectFirst("meta[property=og:site_name]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc?.title()?.substringBefore(" - ")?.substringBefore(" | ")?.trim()?.takeIf { it.isNotBlank() }
            ?: URI(url).host.removePrefix("www.")
        val icon = doc?.let { d ->
            d.selectFirst("link[rel~=(?i)apple-touch-icon]")?.absUrl("href")?.ifBlank { null }
                ?: d.selectFirst("link[rel~=(?i)icon]")?.absUrl("href")?.ifBlank { null }
        } ?: "$url/favicon.ico"
        val search = doc?.let { guessSearch(it, url) } ?: "$url/?s={q}"
        val site = Site(System.currentTimeMillis().toString(36), title.take(40), url, icon, search)
        withContext(Dispatchers.Main) { sites.removeAll { it.url == url }; sites += site; save() }
        AppLog.i("Site eklendi: ${site.name} (${site.url})")
        return site
    }

    fun update(s: Site) { val i = sites.indexOfFirst { it.id == s.id }; if (i >= 0) { sites[i] = s; save() } }
    fun remove(s: Site) { sites.removeAll { it.id == s.id }; save() }

    /** Sayfadaki arama formundan arama adresini tahmin eder. */
    private fun guessSearch(d: Document, base: String): String? {
        val input = d.selectFirst("form input[type=search], form input[name=s], form input[name=q], form input[name=search], form input[name=query]")
            ?: return null
        val form = input.parents().firstOrNull { it.tagName() == "form" } ?: return null
        val action = form.absUrl("action").ifBlank { base }
        if (form.attr("method").equals("post", true)) return null
        val name = input.attr("name").ifBlank { "s" }
        return action + (if (action.contains('?')) "&" else "?") + "$name={q}"
    }

    // ------------------------------------------------------------------ afiş çekme

    private suspend fun fetch(url: String): Document = withContext(Dispatchers.IO) {
        val html = Net.getText(url, mapOf("Referer" to "$url/", "Accept-Language" to "tr-TR,tr;q=0.9"))
        Jsoup.parse(html, url)
    }

    /** Statik HTML yetmezse (Cloudflare / JavaScript ile yüklenen siteler) gizli tarayıcıda açıp dener. */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Document>>()

    private suspend fun load(url: String): Document {
        cache[url]?.let { (t, d) -> if (System.currentTimeMillis() - t < 5 * 60_000) return d }
        return loadFresh(url).also { cache[url] = System.currentTimeMillis() to it }
    }

    private suspend fun loadFresh(url: String): Document {
        val first = runCatching { fetch(url) }.getOrNull()
        if (first != null && extract(first).flatMap { it.items }.size >= 6) return first
        val html = SiteRenderer.render(appContext, url)
        return if (html != null) Jsoup.parse(html, url) else first ?: throw java.io.IOException("Site açılamadı")
    }

    suspend fun rows(site: Site): List<SiteRow> = extract(load(site.url))

    suspend fun search(site: Site, q: String): List<SiteItem> {
        val tpl = site.search ?: "${site.url}/?s={q}"
        val doc = load(tpl.replace("{q}", URLEncoder.encode(q, "UTF-8")))
        return extract(doc).flatMap { it.items }.distinctBy { it.url }
    }

    private val badImg = Regex("(?i)(logo|icon|avatar|banner|sprite|loading|placeholder|blank|pixel|ads?[-_/]|gravatar|emoji|flag)")
    private val badLink = Regex("(?i)(/tag/|/category/|/kategori/|/tur/|/genre/|/yil/|/year/|/oyuncu/|/actor/|/author/|/page/|/feed|#|javascript:|/wp-login|/iletisim|/contact|facebook\\.|twitter\\.|instagram\\.|t\\.me/)")

    private fun imgSrc(img: Element): String? {
        for (a in listOf("data-src", "data-lazy-src", "data-original", "data-srcset", "srcset", "src")) {
            val v = img.attr(a).trim()
            if (v.isBlank() || v.startsWith("data:")) continue
            val first = v.split(",").first().trim().split(" ").first()
            val abs = img.absUrl(a).takeIf { it.isNotBlank() && a != "srcset" && a != "data-srcset" }
                ?: runCatching { URI(img.baseUri()).resolve(first).toString() }.getOrNull()
            if (abs != null && !badImg.containsMatchIn(abs)) return abs
        }
        // arka plan resmi olarak verilmiş afişler
        val style = img.attr("style")
        Regex("url\\(['\"]?([^'\")]+)").find(style)?.groupValues?.get(1)?.let { return runCatching { URI(img.baseUri()).resolve(it).toString() }.getOrNull() }
        return null
    }

    private fun titleOf(a: Element, img: Element?): String? {
        val t = listOf(
            img?.attr("alt"), img?.attr("title"), a.attr("title"),
            a.selectFirst("h2, h3, h4, .title, .name, [class*=title], [class*=name]")?.text(),
            a.parent()?.selectFirst("h2, h3, h4, .title, [class*=title]")?.text(),
            a.text(),
        ).firstOrNull { !it.isNullOrBlank() && it.length in 2..120 }?.trim() ?: return null
        return t.replace(Regex("(?i)\\s*(izle|full hd|türkçe dublaj|altyazılı|hd)\\s*$"), "").trim().ifBlank { t }
    }

    /** Sayfadaki "bağlantı + resim" çiftlerini afiş olarak toplar; başlıklara göre raflara ayırır. */
    fun extract(doc: Document): List<SiteRow> {
        val base = runCatching { URI(doc.location()).host?.removePrefix("www.") }.getOrNull()
        val rows = linkedMapOf<String, MutableList<SiteItem>>()
        val seen = HashSet<String>()
        var section = "Son eklenenler"
        for (el in doc.body()?.allElements ?: return emptyList()) {
            when {
                el.tagName() in setOf("h1", "h2", "h3") || el.hasClass("section-title") || el.hasClass("widget-title") -> {
                    val t = el.text().trim()
                    if (t.length in 3..60 && el.selectFirst("img") == null) section = t
                }
                el.tagName() == "a" -> {
                    val href = el.absUrl("href")
                    if (href.isBlank() || badLink.containsMatchIn(href) || href in seen) continue
                    val host = runCatching { URI(href).host?.removePrefix("www.") }.getOrNull()
                    if (base != null && host != null && host != base) continue
                    val img = el.selectFirst("img") ?: el.parent()?.takeIf { it.select("a").size == 1 }?.selectFirst("img")
                    val poster = img?.let { imgSrc(it) } ?: el.selectFirst("[style*=url]")?.let { imgSrc(it) } ?: continue
                    val title = titleOf(el, img) ?: continue
                    seen += href
                    val card = el.parent() ?: el
                    val year = (el.selectFirst("[class*=year], [class*=date]") ?: card.selectFirst("[class*=year], [class*=date]"))?.text()
                        ?.let { Regex("(19|20)\\d{2}").find(it)?.value }
                    val rating = (el.selectFirst("[class*=imdb], [class*=rating], [class*=puan], [class*=score], [class*=vote]")
                        ?: card.selectFirst("[class*=imdb], [class*=rating], [class*=puan], [class*=score], [class*=vote]"))?.text()
                        ?.let { Regex("\\b(10|[0-9])([.,][0-9])?\\b").find(it)?.value?.replace(',', '.') }
                    rows.getOrPut(section) { mutableListOf() } += SiteItem(title, href, poster, year, rating)
                }
            }
        }
        // Küçük rafları birleştir
        val big = rows.filter { it.value.size >= 4 }.map { SiteRow(it.key, it.value) }
        val rest = rows.filter { it.value.size < 4 }.flatMap { it.value }
        return if (big.isEmpty()) listOf(SiteRow("Son eklenenler", rest)).filter { it.items.isNotEmpty() }
        else big + (if (rest.size >= 4) listOf(SiteRow("Diğer", rest)) else emptyList())
    }

    // ------------------------------------------------------------------ katalog görünümü

    private val catGroups = listOf(
        "Tür" to Regex("(?i)/(tur|turler|tür|genre|genres|kategori|category|categories|film-kategori)/"),
        "Yıl" to Regex("(?i)(/(yil|year|yillar|release)/|/(19|20)\\d{2}/?$)"),
        "Ülke" to Regex("(?i)/(ulke|ülke|country|countries)/"),
        "Platform" to Regex("(?i)/(platform|kanal|network|studio)/"),
    )

    /** Sitenin menüsündeki tür / yıl / ülke bağlantıları. */
    suspend fun categories(site: Site): List<SiteCategory> {
        val doc = load(site.url)
        val base = site.host
        val out = linkedMapOf<String, SiteCategory>()
        for (a in doc.select("a[href]")) {
            val href = a.absUrl("href").substringBefore('#').trimEnd('/')
            if (href.isBlank() || href in out) continue
            val host = runCatching { URI(href).host?.removePrefix("www.") }.getOrNull()
            if (host != base) continue
            val name = a.text().trim().replace(Regex("\\s*\\(\\d+\\)$"), "")
            if (name.length !in 2..28 || a.selectFirst("img") != null) continue
            val group = catGroups.firstOrNull { it.second.containsMatchIn("$href/") }?.first ?: continue
            out[href] = SiteCategory(name, href, group)
        }
        return out.values.toList()
    }

    /** Liste sayfası: afişler + sonraki sayfanın adresi. */
    suspend fun listing(url: String): Pair<List<SiteItem>, String?> {
        val doc = load(url)
        val items = extract(doc).flatMap { it.items }.distinctBy { it.url }
        val next = doc.selectFirst("link[rel=next], a[rel=next], a.next, .next a, a.nextpostslink, .pagination a:contains(›), .pagination a:contains(»), a:contains(Sonraki)")
            ?.let { it.absUrl("href").ifBlank { null } }
            ?: Regex("/page/(\\d+)/?").find(url)?.let { m -> url.replace(m.value, "/page/${m.groupValues[1].toInt() + 1}/") }
            ?: if (items.size >= 12) url.trimEnd('/') + "/page/2/" else null
        return items to next?.takeIf { it != url }
    }
}
