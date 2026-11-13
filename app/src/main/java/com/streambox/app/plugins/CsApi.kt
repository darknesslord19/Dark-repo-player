package com.streambox.app.plugins

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * CloudStream API'sini kullanan tüm çağrılar bu dosyada.
 * CloudStream sürümü değişip derleme hata verirse düzeltilecek tek yer burası.
 */

data class CsCard(val api: MainAPI, val name: String, val url: String, val poster: String?, val posterHeaders: Map<String, String> = emptyMap())
/** Bir ana sayfa rafı; [reqName]/[reqData] sonraki sayfaları getirmek için. */
data class CsRow(val title: String, val items: List<CsCard>, val reqName: String = "", val reqData: String = "", val horizontal: Boolean = false)
data class CsEpisode(val data: String, val title: String, val season: Int?, val episode: Int?, val poster: String?)
data class CsDetails(
    val headers: Map<String, String>,
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val plot: String?,
    val year: Int?,
    val tags: List<String>,
    val playData: String?,           // film / canlı yayın
    val episodes: List<CsEpisode>,   // dizi / anime
)
data class CsLink(val name: String, val url: String, val referer: String, val quality: Int, val headers: Map<String, String>, val isHls: Boolean, val isDash: Boolean)
data class CsSub(val lang: String, val url: String)

object CsApi {
    private fun card(api: MainAPI, r: SearchResponse) = CsCard(api, r.name, r.url, r.posterUrl, imageHeaders(api, r))

    /**
     * Afiş başlıkları: eklentinin bildirdiği posterHeaders + sitenin Referer'ı.
     * (posterHeaders yansımayla okunur; sürüm farkında derleme bozulmasın.)
     */
    @Suppress("UNCHECKED_CAST")
    fun imageHeaders(api: MainAPI, item: Any?): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val base = api.mainUrl.trimEnd('/')
        if (base.startsWith("http")) out["Referer"] = "$base/"
        runCatching {
            val m = item?.javaClass?.methods?.firstOrNull { it.name == "getPosterHeaders" && it.parameterCount == 0 }
            (m?.invoke(item) as? Map<String, String>)?.let { out.putAll(it) }
        }
        return out
    }

    /** Ana sayfa rafları (en fazla [maxRows] tane). */
    suspend fun mainPage(api: MainAPI, maxRows: Int = 6): List<CsRow> = withContext(Dispatchers.IO) {
        if (!api.hasMainPage) return@withContext emptyList()
        val rows = mutableListOf<CsRow>()
        for (mp in api.mainPage.take(maxRows)) {
            val res = try {
                api.getMainPage(1, MainPageRequest(mp.name, mp.data, mp.horizontalImages))
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                com.streambox.app.AppLog.e("${api.name} ana sayfa (${mp.name}) hatası", t)
                null
            } ?: continue
            for (list in res.items) {
                if (list.list.isNotEmpty()) rows += CsRow(list.name, list.list.map { card(api, it) }, mp.name, mp.data, mp.horizontalImages)
            }
        }
        rows
    }

    /** Bir rafın [page]. sayfası. */
    suspend fun mainPageMore(api: MainAPI, row: CsRow, page: Int): List<CsCard> = withContext(Dispatchers.IO) {
        val res = api.getMainPage(page, MainPageRequest(row.reqName, row.reqData, row.horizontal)) ?: return@withContext emptyList()
        val list = res.items.firstOrNull { it.name == row.title } ?: res.items.firstOrNull() ?: return@withContext emptyList()
        list.list.map { card(api, it) }
    }

    suspend fun search(api: MainAPI, query: String): List<CsCard> = withContext(Dispatchers.IO) {
        api.search(query)?.map { card(api, it) } ?: emptyList()
    }

    suspend fun load(api: MainAPI, url: String): CsDetails? = withContext(Dispatchers.IO) {
        val r: LoadResponse = api.load(url) ?: return@withContext null
        val eps: List<Episode> = when (r) {
            is TvSeriesLoadResponse -> r.episodes
            is AnimeLoadResponse -> r.episodes.values.flatten()
            else -> emptyList()
        }
        val play = when (r) {
            is MovieLoadResponse -> r.dataUrl
            is LiveStreamLoadResponse -> r.dataUrl
            else -> null
        }
        CsDetails(
            headers = imageHeaders(api, r),
            title = r.name,
            poster = r.posterUrl,
            backdrop = r.backgroundPosterUrl,
            plot = r.plot,
            year = r.year,
            tags = r.tags ?: emptyList(),
            playData = play,
            episodes = eps.map {
                CsEpisode(it.data, it.name ?: "Bölüm ${it.episode ?: "?"}", it.season, it.episode, it.posterUrl)
            },
        )
    }

    /** Bağlantıları bulur; her bulunan bağlantı [onLink] ile hemen bildirilir. */
    suspend fun loadLinks(api: MainAPI, data: String, onLink: (CsLink) -> Unit, onSub: (CsSub) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            api.loadLinks(data, false, { s -> onSub(CsSub(s.lang, s.url)) }) { l: ExtractorLink ->
                onLink(
                    CsLink(
                        name = l.name,
                        url = l.url,
                        referer = l.referer,
                        quality = l.quality,
                        headers = l.headers,
                        isHls = l.type == ExtractorLinkType.M3U8,
                        isDash = l.type == ExtractorLinkType.DASH,
                    )
                )
            }
        }
}
