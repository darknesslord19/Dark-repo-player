package com.streambox.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), contentPadding = PaddingValues(12.dp),
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

/** Bir listenin kanalları, kategorilere (group-title) göre. */
@Composable
fun IptvListScreen(playlist: Playlist) {
    val context = LocalContext.current
    var channels by remember { mutableStateOf<List<Channel>?>(null) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(playlist, refresh) {
        channels = null
        channels = runCatching { IptvStore.channels(playlist, refresh > 0) }.getOrDefault(emptyList())
    }
    val all = channels
    val groups = remember(all) { all?.map { it.group }?.distinct() ?: emptyList() }
    val visible = remember(all, group, query) {
        all.orEmpty().filter { (group == null || it.group == group) && (query.isBlank() || it.name.contains(query, true)) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(playlist.name, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (playlist.url != null) TextButton(onClick = { refresh++ }) { Text("Yenile") }
        }
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Kanal ara…") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        LazyRow(contentPadding = PaddingValues(12.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(group == null, { group = null }, label = { Text("Tümü (${all?.size ?: 0})") }) }
            items(groups) { g ->
                val n = all.orEmpty().count { it.group == g }
                FilterChip(group == g, { group = g }, label = { Text("$g ($n)") })
            }
        }
        if (all == null) Loading()
        else if (visible.isEmpty()) Message("Kanal yok")
        else LazyVerticalGrid(
            columns = GridCells.Adaptive(118.dp), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(visible) { ch ->
                PosterCard(UiCard(ch.url, ch.name, ch.logo, ch.group) {
                    PlayerActivity.start(context, PlayItem(ch.name, ch.url, ch.headers, live = true))
                }, ratio = 1f)
            }
        }
    }
}
