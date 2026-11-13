package com.streambox.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.streambox.app.TmdbItem
import com.streambox.app.iptv.Playlist
import com.streambox.app.plugins.CsCard

sealed interface Route {
    data object Home : Route
    data object Search : Route
    data object Iptv : Route
    data object Extensions : Route
    data object Settings : Route
    data class TmdbDetail(val item: TmdbItem) : Route
    data class CsDetail(val card: CsCard) : Route
    data class IptvList(val playlist: Playlist) : Route
    /** Eklenti deposu sayfası: kind = "cs" ya da "nuvio". */
    data class RepoPage(val kind: String, val url: String) : Route
    /** Ayar alt sayfası: tmdb, dns, player, iptv, log, about. */
    data class SettingsPage(val page: String) : Route
    /** Bir rafın tamamı; [load] sayfa numarasıyla sonraki kartları getirir (null: sadece eldekiler). */
    /** Sitenin afiş kataloğu: [url] boşsa ana sayfa; kategori seçilince o kategorinin adresi. */
    data class SiteCatalog(val siteId: String, val title: String? = null, val url: String? = null) : Route
    data class Category(val title: String, val initial: List<UiCard>, val load: (suspend (Int) -> List<UiCard>)?) : Route
}

object Nav {
    val stack = mutableStateListOf<Route>(Route.Home)
    val current get() = stack.last()

    /** Arama ekranına başka ekrandan gönderilen sorgu. */
    var pendingQuery by mutableStateOf<String?>(null)

    fun go(r: Route) { stack.add(r) }
    fun tab(r: Route) { stack.clear(); stack.add(r) }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    fun search(q: String) { pendingQuery = q; tab(Route.Search) }

    /** Ana sayfa rafları geri dönünce yeniden yüklenmesin. */
    val homeCache = mutableMapOf<String, List<UiRow>>()
}
