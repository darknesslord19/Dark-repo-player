package com.streambox.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streambox.app.AppLog
import com.streambox.app.iptv.Channel
import com.streambox.app.iptv.IptvStore
import com.streambox.app.iptv.Playlist
import com.streambox.app.player.PlayItem
import com.streambox.app.player.PlayerActivity
import kotlinx.coroutines.launch

/** Kayıtlı M3U / M3U8 listeleri. */
@Composable
fun IptvScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { IptvStore.addFile(context, uri) }
                .onSuccess { Nav.go(Route.IptvList(it)) }
                .onFailure { msg = "Okunamadı: ${it.message}" }
        }
    }

    LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, BottomSpace),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("IPTV listesi ekle")
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Ad (isteğe bağlı)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("M3U / M3U8 adresi") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = {
                        if (url.isBlank()) return@Button
                        busy = true
                        scope.launch {
                            runCatching { IptvStore.addUrl(name, url) }
                                .onSuccess { name = ""; url = ""; msg = null; Nav.go(Route.IptvList(it)) }
                                .onFailure { msg = "Yüklenemedi: ${it.message}"; AppLog.e("IPTV", it) }
                            busy = false
                        }
                    }) { Text(if (busy) "Yükleniyor…" else "Ekle") }
                    OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }) { Text("Dosyadan") }
                }
                msg?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp) }
                SectionTitle("Listelerim")
                if (IptvStore.playlists.isEmpty()) Message("Henüz liste yok.")
            }
        }
        items(IptvStore.playlists, key = { it.id }) { p ->
            Column {
                PosterCard(UiCard(p.id, p.name, null, p.url?.let { "Adres" } ?: "Dosya") { Nav.go(Route.IptvList(p)) },
                    width = 150.dp, ratio = 16f / 9f)
                TextButton(onClick = { IptvStore.remove(p) }) { Text("Sil", color = Color(0xFFFF6B6B), fontSize = 12.sp) }
            }
        }
    }
}

/** Kanal kartı: afişse tam doldurur (2:3), kanal logosuysa ortalar ve adı altta gösterir. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChannelCard(ch: Channel, fav: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    var poster by remember(ch.logo) { mutableStateOf<Boolean?>(null) }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth()
            .aspectRatio(2f / 3f)
            .scale(if (focused) 1.06f else 1f)
            .clip(shape)
            .background(Color(0xFF1C1F26))
            .then(if (focused) Modifier.border(3.dp, Accent, shape) else Modifier.border(1.dp, Color(0x14FFFFFF), shape))
            .combinedClickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick, onLongClick = onLongClick)
    ) {
        if (ch.logo != null) {
            coil.compose.AsyncImage(
                model = ch.logo, contentDescription = ch.name,
                contentScale = if (poster == true) ContentScale.Crop else ContentScale.Fit,
                onSuccess = { st ->
                    val d = st.result.drawable
                    poster = d.intrinsicHeight > d.intrinsicWidth * 1.15f
                },
                onError = { poster = false },
                modifier = Modifier.fillMaxSize().padding(if (poster == true) 0.dp else 18.dp)
            )
        }
        if (poster != true) {
            // Logo ya da görselsiz kanal: ad altta
            androidx.compose.foundation.layout.Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .padding(8.dp, 18.dp, 8.dp, 8.dp)
            ) {
                Text(ch.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            if (ch.logo == null) Text(ch.name.take(2).uppercase(), color = Muted, fontSize = 26.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center))
        }
        if (fav) Icon(Icons.Default.Star, "Favori", tint = Color(0xFFFFC94D),
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                .background(Color(0x99000000), CircleShape).padding(3.dp))
    }
}

/** Üstteki sekme düğmesi (Devam et / Favoriler / Tüm kanallar / kategoriler). */
@Composable
private fun TabPill(label: String, count: Int, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    Row(
        Modifier.clip(shape)
            .background(if (selected) Color(0xFF1F2A44) else Color(0xFF171A21))
            .border(if (selected || focused) 2.dp else 1.dp, if (selected || focused) Color.White else Color(0x14FFFFFF), shape)
            .clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let { Icon(it, null, tint = if (selected) Color.White else Color(0xFFC8CBD0), modifier = Modifier.size(18.dp).padding(end = 2.dp)) }
        Text(label, color = if (selected) Color(0xFFBFD3FF) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, modifier = Modifier.padding(start = if (icon != null) 6.dp else 0.dp))
        Text("  $count", color = Muted, fontSize = 15.sp)
    }
}

private const val TAB_RECENT = "__recent__"
private const val TAB_FAV = "__fav__"
private const val TAB_ALL = "__all__"

/** Bir listenin kanalları: Devam et, Favoriler, Tüm kanallar ve kategoriler (group-title). */
@Composable
fun IptvListScreen(playlist: Playlist) {
    val context = LocalContext.current
    var channels by remember { mutableStateOf<List<Channel>?>(null) }
    var tab by rememberSaveable { mutableStateOf(TAB_ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playlist, refresh) {
        channels = null
        channels = runCatching { IptvStore.channels(playlist, refresh > 0) }.getOrDefault(emptyList())
    }
    LaunchedEffect(toast) { if (toast != null) { kotlinx.coroutines.delay(1500); toast = null } }

    val all = channels.orEmpty()
    val byUrl = remember(all) { all.associateBy { it.url } }
    val groups = remember(all) { all.groupingBy { it.group }.eachCount().toList() }
    val recent = IptvStore.recents.mapNotNull { byUrl[it] }
    val favs = IptvStore.favorites.mapNotNull { byUrl[it] }
    val base = when (tab) {
        TAB_RECENT -> recent
        TAB_FAV -> favs
        TAB_ALL -> all
        else -> all.filter { it.group == tab }
    }
    val visible = remember(base, query) { if (query.isBlank()) base else base.filter { it.name.contains(query, true) } }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Başlık
            Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp, 12.dp, 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Canlı TV", fontSize = 24.sp, fontWeight = FontWeight.Black)
                Row(
                    Modifier.weight(1f, fill = false).clip(CircleShape).background(Color(0xFF171A21)).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF3B30)))
                    Text(playlist.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp))
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                if (playlist.url != null) SmallAction("Yenile") { refresh++ }
                SmallAction("Listeler") { Nav.back() }
            }
            // Arama
            OutlinedTextField(
                query, { query = it }, singleLine = true, placeholder = { Text("Kanal, film, dizi ara…") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            // Sekmeler
            LazyRow(contentPadding = PaddingValues(16.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { TabPill("Devam et", recent.size, tab == TAB_RECENT, Icons.Default.PlayArrow) { tab = TAB_RECENT } }
                item { TabPill("Favoriler", favs.size, tab == TAB_FAV, Icons.Default.Star) { tab = TAB_FAV } }
                item { TabPill("Tüm kanallar", all.size, tab == TAB_ALL) { tab = TAB_ALL } }
                items(groups) { (g, n) -> TabPill(g, n, tab == g) { tab = g } }
            }
            when {
                channels == null -> Loading("Kanallar yükleniyor…")
                visible.isEmpty() -> Message(when (tab) {
                    TAB_RECENT -> "Henüz izlenen kanal yok."
                    TAB_FAV -> "Favori yok. Bir kanala uzun basarak favorilere ekleyebilirsin."
                    else -> "Kanal yok"
                })
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(16.dp, 6.dp, 16.dp, BottomSpace),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(visible) { i, ch ->
                        ChannelCard(ch, ch.url in IptvStore.favorites,
                            onClick = {
                                IptvStore.markWatched(ch.url)
                                val list = visible.map { PlayItem(it.name, it.url, it.headers, live = true, label = it.group) }
                                PlayerActivity.start(context, list, i)
                            },
                            onLongClick = {
                                toast = if (IptvStore.toggleFavorite(ch.url)) "★ Favorilere eklendi" else "Favorilerden çıkarıldı"
                            })
                    }
                }
            }
        }
        toast?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp)
                .clip(CircleShape).background(Color(0xE6222731)).padding(horizontal = 18.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    Text(label, color = Color(0xFFBFD3FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(Color(0xFF171A21))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp))
}
