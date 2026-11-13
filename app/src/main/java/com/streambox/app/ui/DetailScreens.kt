package com.streambox.app.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streambox.app.AppLog
import com.streambox.app.Tmdb
import com.streambox.app.TmdbDetails
import com.streambox.app.TmdbEpisode
import com.streambox.app.TmdbItem
import com.streambox.app.nuvio.NuvioRepoManager
import com.streambox.app.onMain
import com.streambox.app.player.PlayItem
import com.streambox.app.player.PlaySub
import com.streambox.app.player.PlayerActivity
import com.streambox.app.plugins.CsApi
import com.streambox.app.plugins.CsCard
import com.streambox.app.plugins.CsDetails
import com.streambox.app.plugins.CsEpisode

/** Bulunan bir yayın seçeneği (hangi kaynaktan gelirse gelsin). */
data class StreamOption(val source: String, val label: String, val detail: String?, val item: PlayItem)

/** Üst kısım: arka plan görseli + afiş + başlık + açıklama. */
@Composable
private fun Header(title: String, poster: String?, backdrop: String?, meta: String, plot: String?, headers: Map<String, String> = emptyMap()) {
    Box(Modifier.fillMaxWidth()) {
        if (backdrop != null) {
            AsyncImage(imageModel(backdrop, headers), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(220.dp))
            Box(Modifier.fillMaxWidth().height(220.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Bg))))
        }
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = if (backdrop != null) 120.dp else 16.dp)) {
            if (poster != null) {
                AsyncImage(
                    imageModel(poster, headers), title, contentScale = ContentScale.Crop,
                    modifier = Modifier.width(110.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))
                )
            }
            Column(Modifier.padding(start = 14.dp).align(Alignment.Bottom)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 13.sp)
            }
        }
    }
    if (!plot.isNullOrBlank()) {
        Text(plot, color = Color(0xFFC8CBD0), fontSize = 14.sp, maxLines = 6, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
    }
}

/** Bölüm satırı; kumandayla seçilebilir. */
@Composable
private fun EpisodeRow(title: String, subtitle: String?, image: String?, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0xFF22304D) else Panel)
            .then(if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (image != null) {
            AsyncImage(image, null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(110.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)))
        } else {
            Icon(Icons.Default.PlayArrow, null, tint = Accent, modifier = Modifier.padding(8.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Kalite rozeti metni ve rengi. */
private fun qualityBadge(o: StreamOption): Pair<String, Color> {
    val q = qualityScore(o.label + " " + (o.detail ?: ""))
    return when {
        q >= 2160 -> "4K" to Color(0xFFE2C48F)
        q >= 1440 -> "2K" to Color(0xFFE2C48F)
        q >= 1080 -> "1080p" to Color(0xFF4F8CFF)
        q >= 720 -> "720p" to Color(0xFF34C759)
        q > 0 -> "${q}p" to Color(0xFF8E939B)
        else -> "AUTO" to Color(0xFF8E939B)
    }
}

/** Yayın seçme paneli: kaynak ve kalite filtreleri, renkli kalite rozetleri; sonuçlar geldikçe eklenir. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StreamsDialog(title: String, options: List<StreamOption>, status: String?, busy: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var source by remember { mutableStateOf<String?>(null) }
    var quality by remember { mutableStateOf<String?>(null) }
    val sources = options.groupingBy { it.source }.eachCount().toList().sortedByDescending { it.second }
    val qualities = options.map { qualityBadge(it).first }.distinct()
        .sortedByDescending { listOf("AUTO", "480p", "720p", "1080p", "2K", "4K").indexOf(it) }
    val shown = options.filter { (source == null || it.source == source) && (quality == null || qualityBadge(it).first == quality) }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF14161B),
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(44.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3A3F49))) }
    ) {
        com.streambox.app.remote.RemoteNav.Hook()
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            // Başlık
            Row(Modifier.fillMaxWidth().padding(20.dp, 10.dp, 12.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kaynaklar", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF23262E)).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, "Kapat", tint = Color.White) }
            }
            // Durum
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                else Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF34C759), modifier = Modifier.size(18.dp))
                Text(status ?: "", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(2.dp), color = Accent, trackColor = Color(0xFF23262E))

            // Filtreler: kalite + kaynak
            if (options.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterPill("Tümü ${options.size}", source == null && quality == null) { source = null; quality = null } }
                    items(qualities) { q -> FilterPill(q, quality == q) { quality = if (quality == q) null else q } }
                    items(sources) { (src, n) -> FilterPill("$src $n", source == src) { source = if (source == src) null else src } }
                }
            }

            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                if (shown.isEmpty()) item {
                    Message(if (busy) "Kaynaklar aranıyor…" else "Yayın bulunamadı.")
                }
                itemsIndexed(shown) { i, o ->
                    StreamRow(o) {
                        val all = shown.map { op ->
                            op.item.copy(label = listOfNotNull(op.source, op.label, op.detail).distinct().joinToString(" · "))
                        }
                        PlayerActivity.start(context, all, i)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        color = if (selected) Color.White else Color(0xFFC8CBD0),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) Accent else Color(0xFF1F232B))
            .border(1.5.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(50))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp))
}

/** Tek yayın satırı: solda kalite rozeti, ortada kaynak ve açıklama, sağda oynat. */
@Composable
private fun StreamRow(o: StreamOption, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val (badge, color) = qualityBadge(o)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp))
            .background(if (focused) Color(0xFF22304D) else Color(0xFF1A1D24))
            .border(1.5.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(width = 58.dp, height = 40.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Text(badge, color = color, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(o.source, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(o.label, color = Color(0xFFC8CBD0), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            o.detail?.let { Text(it, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (focused) Accent else Color(0xFF262A33)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, "Oynat", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

// ------------------------------------------------------------ TMDB (Nuvio)

@Composable
fun TmdbDetailScreen(item: TmdbItem) {
    val context = LocalContext.current
    var details by remember { mutableStateOf<TmdbDetails?>(null) }
    var season by remember { mutableStateOf(1) }
    var episodes by remember { mutableStateOf<List<TmdbEpisode>?>(null) }
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    val options = remember { mutableStateListOf<StreamOption>() }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var request by remember { mutableStateOf<Pair<Int?, Int?>?>(null) }

    LaunchedEffect(item) {
        details = runCatching { Tmdb.details(item.type, item.id) }.onFailure { AppLog.e("TMDB detay", it) }.getOrNull()
        details?.seasons?.firstOrNull()?.let { season = it.number }
    }
    LaunchedEffect(details, season) {
        if (item.type == "tv" && details != null) {
            episodes = null
            episodes = runCatching { Tmdb.season(item.id, season) }.getOrDefault(emptyList())
        }
    }
    // Eklentileri çalıştır (ana sayfada bir Nuvio reposu seçiliyse yalnızca onunkiler)
    LaunchedEffect(request) {
        val (s, e) = request ?: return@LaunchedEffect
        options.clear()
        val repo = nuvioRepoFilter()
        val total = NuvioRepoManager.active(item.type, repo).size
        if (total == 0) { status = "Etkin Nuvio eklentisi yok. Eklentiler > Nuvio'dan repo ekle."; busy = false; return@LaunchedEffect }
        var done = 0
        var failed = 0
        busy = true
        status = "0 / $total eklenti"
        NuvioRepoManager.findStreams(context, item.id, item.type, s, e, repo) { scraper, list, err ->
            done++
            if (err != null || list == null) failed++
            list?.forEach { st ->
                options += StreamOption(
                    source = scraper.name,
                    label = listOfNotNull(st.quality, st.name.takeIf { n -> n != scraper.name }).joinToString(" · ").ifBlank { st.name },
                    detail = listOfNotNull(st.title.takeIf { it.isNotBlank() }, st.size).joinToString(" · ").ifBlank { null },
                    item = PlayItem(dialogTitle ?: item.title, st.url, st.headers,
                        resumeKey = "tmdb|${item.type}|${item.id}|${s ?: 0}|${e ?: 0}",
                        meta = com.streambox.app.WatchHistory.tmdbMeta(item, if (s != null) "S${s} · B${e}" else null)),
                )
            }
            // en yüksek kalite üstte
            val sorted = options.sortedByDescending { qualityScore(it.label + " " + (it.detail ?: "")) }
            options.clear(); options.addAll(sorted)
            status = "$done / $total eklenti · ${options.size} yayın" + if (failed > 0) " · $failed sonuç vermedi" else ""
        }
        busy = false
        status = "${options.size} yayın · $total eklenti tarandı" + if (failed > 0) " · $failed sonuç vermedi" else ""
    }

    val share = rememberShare()
    val last = com.streambox.app.WatchHistory.latest("tmdb|${item.type}|${item.id}|")
    fun playEpisode(s: Int, e: Int) { dialogTitle = "${item.title} S${s}B${e}"; request = null; request = Pair(s, e) }
    fun playMovie() { dialogTitle = item.title; request = null; request = Pair(null, null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomSpace)) {
        val d = details
        item {
            DetailHero(
                title = item.title, tagline = d?.tagline, poster = item.poster, backdrop = item.backdrop, headers = emptyMap(),
                source = "Nuvio",
                facts = listOfNotNull(item.year, if (item.type == "tv") "Dizi" else "Film", fmtRuntime(d?.runtime),
                    d?.seasons?.size?.takeIf { item.type == "tv" && it > 0 }?.let { "$it sezon" }),
                rating = item.rating, genres = d?.genres.orEmpty(),
            )
        }
        item {
            // Oynat / Devam et
            val parts = last?.key?.split("|")
            val lastS = parts?.getOrNull(3)?.toIntOrNull()?.takeIf { it > 0 }
            val lastE = parts?.getOrNull(4)?.toIntOrNull()?.takeIf { it > 0 }
            val firstEp = episodes?.firstOrNull()
            val label = when {
                item.type == "movie" -> if (last != null) "Devam et" else "Oynat"
                lastS != null && lastE != null -> "Devam et · S$lastS B$lastE"
                firstEp != null -> "$season. Sezon ${firstEp.number}. Bölüm"
                else -> "Bölümler yükleniyor"
            }
            val onPlay: (() -> Unit)? = when {
                item.type == "movie" -> ({ playMovie() })
                lastS != null && lastE != null -> ({ playEpisode(lastS, lastE) })
                firstEp != null -> ({ playEpisode(season, firstEp.number) })
                else -> null
            }
            val actions = buildList<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>> {
                if (last != null && onPlay != null) add(Triple(androidx.compose.material.icons.Icons.Default.Replay, "Baştan başla") {
                    com.streambox.app.WatchHistory.remove(last); onPlay()
                })
                add(Triple(Icons.Default.Search, "Kaynaklarda ara") { Nav.search(item.title) })
                add(Triple(androidx.compose.material.icons.Icons.Default.Share, "Paylaş") {
                    share("${item.title}${item.year?.let { " ($it)" } ?: ""} — https://www.themoviedb.org/${item.type}/${item.id}")
                })
            }
            PlayActions(label, last?.progress?.takeIf { it > 0f }, last?.let { remainingText(it) }, onPlay ?: { }, actions)
        }
        item { Synopsis(d?.item?.overview?.ifBlank { null } ?: item.overview) }
        if (item.type == "tv") {
            val seasons = d?.seasons.orEmpty()
            val eps = episodes
            item {
                SectionLabel("Bölümler", trailing = eps?.let { "${it.size} bölüm" })
                SeasonTabs(seasons.map { it.number to it.name }, season) { season = it }
                Spacer(Modifier.height(8.dp))
            }
            if (d == null || eps == null) item { Loading() }
            else items(eps) { ep ->
                EpisodeCard(ep.number, ep.name, ep.overview, ep.still, emptyMap(), ep.runtime,
                    com.streambox.app.WatchHistory.entry("tmdb|tv|${item.id}|$season|${ep.number}")?.progress?.takeIf { it > 0f }) {
                    playEpisode(season, ep.number)
                }
            }
        }
        if (d != null) {
            item { CastRow(d.cast.map { Triple(it.name, it.role, it.image) }) }
            item { SimilarRow(d.recommendations.map { it.toCard() }) }
        }
    }

    if (dialogTitle != null && request != null) {
        StreamsDialog(dialogTitle!!, options, status, busy) { request = null; dialogTitle = null }
    }
}

// ------------------------------------------------------------ CloudStream

@Composable
fun CsDetailScreen(card: CsCard) {
    var details by remember { mutableStateOf<CsDetails?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var season by remember { mutableStateOf<Int?>(null) }
    var target by remember { mutableStateOf<Pair<String, String>?>(null) } // (başlık, data)
    val options = remember { mutableStateListOf<StreamOption>() }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(card) {
        try {
            details = CsApi.load(card.api, card.url) ?: run { failed = "Kaynak boş yanıt verdi"; null }
            season = details?.episodes?.mapNotNull { it.season }?.minOrNull()
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            AppLog.e("${card.api.name} load hatası", t); failed = AppLog.describe(t)
        }
    }
    LaunchedEffect(target) {
        val (title, data) = target ?: return@LaunchedEffect
        options.clear()
        busy = true
        val subs = mutableListOf<PlaySub>()
        try {
            CsApi.loadLinks(card.api, data,
                onLink = { l ->
                    onMain {
                        val headers = l.headers.toMutableMap()
                        if (l.referer.isNotBlank() && headers.keys.none { it.equals("Referer", true) }) headers["Referer"] = l.referer
                        options += StreamOption(
                            source = card.api.name,
                            label = l.name,
                            detail = if (l.quality > 0) "${l.quality}p" else null,
                            item = PlayItem(title, l.url, headers,
                                when { l.isHls -> PlayItem.Kind.HLS; l.isDash -> PlayItem.Kind.DASH; else -> PlayItem.Kind.AUTO },
                                subs.toList(),
                                resumeKey = "cs|${card.api.name}|${card.url}|" + csEpId(details?.episodes?.firstOrNull { it.data == data }),
                                meta = com.streambox.app.WatchHistory.csMeta(card.api.name, card.url, details?.title ?: card.name,
                                    title.substringAfter(" · ", "").ifBlank { null }, details?.poster ?: card.poster)),
                        )
                    }
                },
                onSub = { s -> synchronized(subs) { subs += PlaySub(s.lang, s.url) } })
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            AppLog.e("${card.api.name} loadLinks hatası", t)
        }
        // altyazılar sonradan gelmiş olabilir: hepsine ekle
        val all = synchronized(subs) { subs.toList() }
        if (all.isNotEmpty()) options.replaceAll { it.copy(item = it.item.copy(subtitles = all)) }
        busy = false
    }

    val share = rememberShare()
    val prefix = "cs|${card.api.name}|${card.url}|"
    val last = com.streambox.app.WatchHistory.latest(prefix)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomSpace)) {
        val d = details
        val seasons = d?.episodes?.mapNotNull { it.season }?.distinct()?.sorted().orEmpty()
        val eps: List<CsEpisode> = when {
            d == null -> emptyList()
            seasons.size > 1 -> d.episodes.filter { it.season == season }
            else -> d.episodes
        }
        item {
            DetailHero(
                title = d?.title ?: card.name, tagline = null,
                poster = d?.poster ?: card.poster, backdrop = d?.backdrop,
                headers = (d?.headers ?: emptyMap()) + card.posterHeaders,
                source = card.api.name,
                facts = listOfNotNull(d?.year?.toString(), d?.typeLabel, fmtRuntime(d?.duration),
                    d?.episodes?.size?.takeIf { it > 0 }?.let { "$it bölüm" }),
                rating = null, genres = d?.tags.orEmpty(),
            )
        }
        when {
            failed != null -> item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Message("Açılamadı: $failed")
                    androidx.compose.material3.OutlinedButton(onClick = {
                        com.streambox.app.cf.CfVerify.open(context, card.api.mainUrl)
                    }) { Text("Cloudflare / site doğrula") }
                }
            }
            d == null -> item { Loading() }
            else -> {
                item {
                    val title = d.title
                    val lastId = last?.key?.removePrefix(prefix)
                    val lastEp = lastId?.let { id -> d.episodes.firstOrNull { csEpId(it) == id } }
                    val firstEp = eps.firstOrNull() ?: d.episodes.firstOrNull()
                    val playData = d.playData
                    val (label, onPlay) = when {
                        playData != null -> (if (last != null) "Devam et" else "Oynat") to { target = null; target = title to playData }
                        lastEp != null -> "Devam et · ${listOfNotNull(lastEp.season?.let { "S$it" }, lastEp.episode?.let { "B$it" }).joinToString(" ").ifBlank { lastEp.title }}" to
                            { target = null; target = "$title · ${lastEp.title}" to lastEp.data }
                        firstEp != null -> "Oynat · ${listOfNotNull(firstEp.season?.let { "$it. Sezon" }, firstEp.episode?.let { "$it. Bölüm" }).joinToString(" ").ifBlank { firstEp.title }}" to
                            { target = null; target = "$title · ${firstEp.title}" to firstEp.data }
                        else -> "Oynatılabilir içerik yok" to null
                    }
                    val actions = buildList<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>> {
                        if (last != null && onPlay != null) add(Triple(androidx.compose.material.icons.Icons.Default.Replay, "Baştan başla") {
                            com.streambox.app.WatchHistory.remove(last); onPlay()
                        })
                        add(Triple(Icons.Default.Search, "Diğer kaynaklar") { Nav.search(title) })
                        add(Triple(androidx.compose.material.icons.Icons.Default.Share, "Paylaş") { share("$title — ${card.url}") })
                    }
                    PlayActions(label, last?.progress?.takeIf { it > 0f }, last?.let { remainingText(it) }, onPlay, actions)
                }
                item { Synopsis(d.plot) }
                if (d.episodes.isNotEmpty()) {
                    item {
                        SectionLabel("Bölümler", trailing = "${eps.size} bölüm")
                        SeasonTabs(seasons.map { it to "$it. Sezon" }, season) { season = it }
                        Spacer(Modifier.height(8.dp))
                    }
                    items(eps) { ep ->
                        EpisodeCard(ep.episode, ep.title, null, ep.poster, d.headers + card.posterHeaders, null,
                            com.streambox.app.WatchHistory.entry(prefix + csEpId(ep))?.progress?.takeIf { it > 0f }) {
                            target = null; target = "${d.title} · ${ep.title}" to ep.data
                        }
                    }
                }
                item { CastRow(d.actors.map { Triple(it.first, null, it.second) }) }
                item { SimilarRow(d.recommendations.map { it.toCard() }) }
            }
        }
    }

    target?.let { (title, _) ->
        StreamsDialog(title, options, if (busy) "Bağlantılar aranıyor… (${options.size})" else "${options.size} bağlantı", busy) { target = null }
    }
}

/**
 * CloudStream bölümü için kalıcı kimlik. Eklentilerin "data" değeri her açılışta değişebildiğinden
 * (oturum/token) sezon-bölüm numarası kullanılır; film için "film".
 */
fun csEpId(ep: CsEpisode?): String = when {
    ep == null -> "film"
    ep.season != null && ep.episode != null -> "s${ep.season}e${ep.episode}"
    ep.episode != null -> "e${ep.episode}"
    else -> "t:" + ep.title
}

/** "23 dk kaldı" metni. */
fun remainingText(e: com.streambox.app.HistoryEntry): String? {
    if (e.duration <= 0) return null
    val left = ((e.duration - e.position) / 60_000).coerceAtLeast(1)
    return if (left >= 60) "${left / 60} sa ${left % 60} dk kaldı" else "$left dk kaldı"
}

/** Ana sayfada seçili Nuvio reposu (yoksa null: tüm etkin eklentiler). */
fun nuvioRepoFilter(): String? =
    com.streambox.app.Settings.homeSource.takeIf { it.startsWith("nuvio:") && it != "nuvio:*" }?.removePrefix("nuvio:")

/** Kalite etiketinden sıralama puanı (4K > 1080 > 720 …). */
fun qualityScore(text: String): Int {
    val t = text.lowercase()
    if ("2160" in t || "4k" in t || "uhd" in t) return 2160
    return Regex("(\\d{3,4})p").find(t)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}
