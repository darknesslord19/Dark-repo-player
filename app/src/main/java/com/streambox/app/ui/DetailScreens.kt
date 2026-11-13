package com.streambox.app.ui

import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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

/** Yayın seçenekleri penceresi: sonuçlar geldikçe listeye eklenir. */
@Composable
fun StreamsDialog(title: String, options: List<StreamOption>, status: String?, busy: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (status != null) Text(status, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                if (!busy && options.isEmpty()) Text("Yayın bulunamadı.", color = Muted)
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(options) { i, o ->
                        EpisodeRow(o.label, listOfNotNull(o.source, o.detail).joinToString(" · "), null) {
                            val all = options.map { op ->
                                op.item.copy(label = listOfNotNull(op.source, op.label, op.detail).distinct().joinToString(" · "))
                            }
                            PlayerActivity.start(context, all, i)
                        }
                    }
                }
            }
        }
    )
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
    // Eklentileri çalıştır
    LaunchedEffect(request) {
        val (s, e) = request ?: return@LaunchedEffect
        options.clear()
        val total = NuvioRepoManager.scrapers.count { it.enabled && item.type in it.supportedTypes }
        if (total == 0) { status = "Etkin Nuvio eklentisi yok. Eklentiler > Nuvio'dan repo ekle."; busy = false; return@LaunchedEffect }
        var done = 0
        busy = true
        status = "0 / $total eklenti tamamlandı"
        NuvioRepoManager.findStreams(context, item.id, item.type, s, e) { scraper, list, err ->
            done++
            list?.forEach { st ->
                options += StreamOption(
                    source = scraper.name,
                    label = listOfNotNull(st.quality, st.name.takeIf { n -> n != scraper.name }).joinToString(" · ").ifBlank { st.name },
                    detail = listOfNotNull(st.title.takeIf { it.isNotBlank() }, st.size).joinToString(" · ").ifBlank { null },
                    item = PlayItem(dialogTitle ?: item.title, st.url, st.headers),
                )
            }
            if (err != null) AppLog.e("${scraper.name}: $err")
            status = "$done / $total eklenti tamamlandı · ${options.size} yayın"
        }
        busy = false
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomSpace)) {
        item {
            val d = details
            val meta = listOfNotNull(
                item.year, if (item.type == "tv") "Dizi" else "Film",
                item.rating?.let { "★ %.1f".format(it) }, d?.runtime?.let { "$it dk" },
                d?.genres?.takeIf { it.isNotEmpty() }?.joinToString(", ")
            ).joinToString(" · ")
            Header(item.title, item.poster, item.backdrop, meta, d?.item?.overview?.ifBlank { null } ?: item.overview)
        }
        item {
            Row(Modifier.padding(16.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.type == "movie") {
                    Button(onClick = { dialogTitle = item.title; request = null; request = Pair(null, null) }) {
                        Icon(Icons.Default.PlayArrow, null); Text("Yayınları bul", Modifier.padding(start = 6.dp))
                    }
                }
                OutlinedButton(onClick = { Nav.search(item.title) }) {
                    Icon(Icons.Default.Search, null); Text("CloudStream'de ara", Modifier.padding(start = 6.dp))
                }
            }
        }
        if (item.type == "tv") {
            val seasons = details?.seasons.orEmpty()
            if (seasons.isNotEmpty()) item {
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons) { s -> FilterChip(selected = s.number == season, onClick = { season = s.number }, label = { Text(s.name) }) }
                }
            }
            val eps = episodes
            if (details == null || eps == null) item { Loading() }
            else items(eps) { ep ->
                EpisodeRow("${ep.number}. ${ep.name}", ep.overview, ep.still) {
                    dialogTitle = "${item.title} S${season}B${ep.number}"
                    request = null
                    request = Pair(season, ep.number)
                }
            }
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
                                subs.toList()),
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomSpace)) {
        val d = details
        when {
            failed != null -> item { Message("Açılamadı: $failed") }
            d == null -> item { Loading() }
            else -> {
                item {
                    val meta = listOfNotNull(card.api.name, d.year?.toString(), d.tags.take(4).joinToString(", ").ifBlank { null }).joinToString(" · ")
                    Header(d.title, d.poster ?: card.poster, d.backdrop, meta, d.plot, d.headers + card.posterHeaders)
                }
                val playData = d.playData
                if (playData != null) item {
                    Button(onClick = { target = null; target = d.title to playData }, modifier = Modifier.padding(16.dp, 10.dp)) {
                        Icon(Icons.Default.PlayArrow, null); Text("Oynat", Modifier.padding(start = 6.dp))
                    }
                }
                val seasons = d.episodes.mapNotNull { it.season }.distinct().sorted()
                if (seasons.size > 1) item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons) { s -> FilterChip(selected = s == season, onClick = { season = s }, label = { Text("$s. Sezon") }) }
                    }
                }
                val eps: List<CsEpisode> = if (seasons.size > 1) d.episodes.filter { it.season == season } else d.episodes
                items(eps) { ep ->
                    val label = listOfNotNull(ep.episode?.let { "$it." }, ep.title).joinToString(" ")
                    EpisodeRow(label, ep.season?.let { "$it. Sezon" }, ep.poster) {
                        target = null; target = "${d.title} · ${ep.title}" to ep.data
                    }
                }
            }
        }
    }

    target?.let { (title, _) ->
        StreamsDialog(title, options, if (busy) "Bağlantılar aranıyor… (${options.size})" else "${options.size} bağlantı", busy) { target = null }
    }
}
