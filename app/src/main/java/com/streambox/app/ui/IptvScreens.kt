package com.streambox.app.ui

import com.streambox.app.iptv.toPlayItem
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.streambox.app.AppLog
import com.streambox.app.iptv.Channel
import com.streambox.app.iptv.IptvStore
import com.streambox.app.iptv.Playlist
import com.streambox.app.player.PlayItem
import com.streambox.app.player.PlayerActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Hesap kutusu renkleri (koyu degrade + parlak vurgu). */
private val tilePalettes = listOf(
    listOf(Color(0xFF1B1433), Color(0xFF3A1F6E)),
    listOf(Color(0xFF2A0F12), Color(0xFF6B1A1C)),
    listOf(Color(0xFF0E2430), Color(0xFF155E75)),
    listOf(Color(0xFF13261A), Color(0xFF1F6B3A)),
    listOf(Color(0xFF2E1D0B), Color(0xFF8A4B12)),
    listOf(Color(0xFF101B3A), Color(0xFF2449A8)),
)

private fun paletteOf(p: Playlist) = tilePalettes[(p.id.hashCode() and 0x7fffffff) % tilePalettes.size]

/** Yatay ekran (TV / telefon yan) mı? */
@Composable
fun isWide(): Boolean {
    val c = LocalConfiguration.current
    return c.screenWidthDp > c.screenHeightDp && c.screenWidthDp >= 600
}

// ======================================================================= Hesaplar

/** Renkli kısaltma kutusu. */
@Composable
private fun AccountAvatar(p: Playlist, size: androidx.compose.ui.unit.Dp = 54.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(paletteOf(p), start = Offset(0f, 0f), end = Offset(160f, 160f))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.09f), radius = this.size.width * 0.42f, center = Offset(this.size.width * 0.95f, this.size.height * 0.02f))
        }
        Text(p.name.filter { !it.isWhitespace() }.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.34f).sp)
    }
}

@Composable
private fun SmallChip(text: String, bg: Color, fg: Color) {
    Text(text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp))
}

/** Hesap satırı: kısaltma, ad, kanal/kategori sayısı, etiketler ve ⋮ düğmesi. */
@Composable
private fun AccountRow(p: Playlist, onOpen: () -> Unit, onMore: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val stats = IptvStore.stats[p.id]
    LaunchedEffect(p.id) { runCatching { IptvStore.loadStats(p) } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp).clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF171A21))
            .border(1.5.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccountAvatar(p)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stats?.let { "%,d kanal · %d kategori".format(it.channels, it.groups).replace(',', '.') } ?: "Yükleniyor…",
                color = Muted, fontSize = 12.5.sp, maxLines = 1)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallChip(p.kind.uppercase(), Color(0xFF23272F), Color(0xFFC8CBD0))
                if (p.onHome) SmallChip("ANA SAYFADA", Accent.copy(alpha = 0.18f), Color(0xFF9DBDFF))
                else SmallChip("GİZLİ", Color(0xFF23272F), Color(0xFF6E737B))
            }
        }
        val msrc = remember { MutableInteractionSource() }
        val mfocused by msrc.collectIsFocusedAsState()
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (mfocused) Color(0xFF2A3042) else Color.Transparent)
                .border(1.5.dp, if (mfocused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                .clickable(interactionSource = msrc, indication = LocalIndication.current, onClick = onMore),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.MoreVert, "Hesap menüsü", tint = Color(0xFFC8CBD0)) }
    }
}

/** ⋮ menüsü: ana sayfada göster, yenile, ad değiştir, sil. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(p: Playlist, onDismiss: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val scope = rememberCoroutineScope()
    val current = IptvStore.playlists.firstOrNull { it.id == p.id } ?: p
    val stats = IptvStore.stats[p.id]
    var refreshing by remember { mutableStateOf(false) }

    @Composable
    fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, title: String, sub: String?, danger: Boolean = false,
                 trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
        val src = remember { MutableInteractionSource() }
        val focused by src.collectIsFocusedAsState()
        Row(
            Modifier.fillMaxWidth().background(if (focused) Color(0xFF222B3D) else Color.Transparent)
                .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, fontSize = 15.sp, color = if (danger) Color(0xFFFF5A52) else Color.White)
                sub?.let { Text(it, fontSize = 12.sp, color = Muted) }
            }
            trailing?.invoke()
        }
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16181E),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(42.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3A3F49))) }
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(20.dp, 4.dp, 20.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
                AccountAvatar(current, 46.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(current.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(current.kind + (stats?.let { " · ${it.channels} kanal · ${it.groups} kategori" } ?: ""), fontSize = 12.sp, color = Muted)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22262E)))
            SheetRow(Icons.Default.Home, Accent, "Ana sayfada göster", "Kaynak seçicide \"Darknes IPTV\" altında", trailing = {
                androidx.compose.material3.Switch(current.onHome, { IptvStore.setOnHome(current, it) },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Accent))
            }) { IptvStore.setOnHome(current, !current.onHome) }
            if (current.url != null) SheetRow(Icons.Default.Refresh, Color(0xFF34C759), "Listeyi yenile",
                if (refreshing) "Yenileniyor…" else "Kanalları sunucudan yeniden indir") {
                if (!refreshing) {
                    refreshing = true
                    scope.launch {
                        runCatching { IptvStore.channels(current, refresh = true); IptvStore.loadStats(current) }
                        Nav.homeCache.remove("iptv:" + current.id)
                        refreshing = false
                    }
                }
            }
            SheetRow(Icons.Default.Edit, Color(0xFFFF9F0A), "Adını değiştir", null, onClick = onRename)
            SheetRow(Icons.Default.Delete, Color(0xFFFF5A52), "Hesabı sil", null, danger = true, onClick = onDelete)
        }
    }
}

/** IPTV giriş ekranı: hesap satırları, tüm hesaplarda arama, Devam et şeridi. */
@Composable
fun IptvScreen() {
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Playlist?>(null) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val wide = isWide()

    // Arama için tüm hesapların kanalları (bellekte önbellekli)
    var all by remember { mutableStateOf<List<Channel>>(emptyList()) }
    LaunchedEffect(IptvStore.playlists.size) {
        all = IptvStore.playlists.flatMap { p -> runCatching { IptvStore.channels(p) }.getOrDefault(emptyList()) }
    }
    val byUrl = remember(all) { all.associateBy { it.url } }
    val results = remember(all, query) {
        if (query.trim().length < 2) emptyList() else all.filter { it.name.contains(query.trim(), true) }.take(300)
    }
    val totalChannels = IptvStore.playlists.sumOf { IptvStore.stats[it.id]?.channels ?: 0 }

    fun play(list: List<Channel>, i: Int) {
        IptvStore.markWatched(list[i].url)
        PlayerActivity.start(context, list.map { it.toPlayItem() }, i)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 140.dp else 104.dp),
        contentPadding = PaddingValues(bottom = BottomSpace),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> androidx.compose.foundation.lazy.grid.GridItemSpan =
            { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
        // Başlık
        item(span = full) {
            Row(Modifier.fillMaxWidth().padding(18.dp, 14.dp, 16.dp, 0.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Darknes IPTV", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (IptvStore.playlists.isEmpty()) "IPTV hesabı ekleyerek başla"
                        else "${IptvStore.playlists.size} hesap" + if (totalChannels > 0) " · %,d kanal".format(totalChannels).replace(',', '.') else "",
                        color = Muted, fontSize = 12.5.sp
                    )
                }
                val src = remember { MutableInteractionSource() }
                val focused by src.collectIsFocusedAsState()
                Row(
                    Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(Accent)
                        .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable(interactionSource = src, indication = LocalIndication.current) { adding = true }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("Hesap ekle", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        // Arama
        if (IptvStore.playlists.isNotEmpty()) item(span = full) {
            val clear: (@Composable () -> Unit)? = if (query.isNotEmpty()) {
                { Icon(Icons.Default.Close, "Temizle", tint = Muted, modifier = Modifier.clip(CircleShape).clickable { query = "" }.padding(8.dp)) }
            } else null
            OutlinedTextField(
                query, { query = it }, singleLine = true, placeholder = { Text("Tüm hesaplarda kanal ara…", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                trailingIcon = clear,
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color(0xFF171A21), focusedContainerColor = Color(0xFF171A21), unfocusedBorderColor = Color(0xFF262A33)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp)
            )
        }

        if (query.trim().length >= 2) {
            // Arama sonuçları
            item(span = full) { SectionLabel("SONUÇLAR · ${results.size}") }
            if (results.isEmpty()) item(span = full) { Message("Eşleşen kanal yok") }
            items(results.size) { i ->
                Box(Modifier.padding(horizontal = 4.dp)) {
                    ChannelCard(results[i], results[i].url in IptvStore.favorites, onClick = { play(results, i) },
                        onLongClick = { IptvStore.toggleFavorite(results[i].url) })
                }
            }
        } else {
            // Hesaplar
            if (IptvStore.playlists.isEmpty()) {
                item(span = full) {
                    Column(Modifier.fillMaxWidth().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(72.dp).clip(CircleShape).background(Accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.LiveTv, null, tint = Accent, modifier = Modifier.size(34.dp))
                        }
                        Text("Henüz hesap yok", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 14.dp))
                        Text("M3U adresi, Xtream bilgileri ya da dosya ile ekleyebilirsin.", color = Muted, fontSize = 13.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp, 4.dp))
                    }
                }
            } else {
                item(span = full) { SectionLabel("HESAPLAR") }
                item(span = full) {
                    // Telefonda tek, TV'de iki sütun
                    val rows = if (wide) IptvStore.playlists.chunked(2) else IptvStore.playlists.map { listOf(it) }
                    Column {
                        rows.forEach { pair ->
                            Row(Modifier.fillMaxWidth()) {
                                pair.forEach { p ->
                                    Box(Modifier.weight(1f)) { AccountRow(p, onOpen = { Nav.go(Route.IptvList(p)) }, onMore = { menu = p }) }
                                }
                                if (pair.size == 1 && wide) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) AddAccountDialog { adding = false }
    menu?.let { p ->
        AccountSheet(p, onDismiss = { menu = null },
            onRename = { menu = null; renaming = p },
            onDelete = { menu = null; deleting = p })
    }
    renaming?.let { p ->
        var name by remember(p) { mutableStateOf(p.name) }
        SimpleDialog("Adını değiştir", onDismiss = { renaming = null }, confirm = "Kaydet", onConfirm = { IptvStore.rename(p, name); renaming = null }) {
            OutlinedTextField(name, { name = it }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        }
    }
    deleting?.let { p ->
        SimpleDialog("Hesap silinsin mi?", onDismiss = { deleting = null }, confirm = "Sil", danger = true,
            onConfirm = { IptvStore.remove(p); deleting = null }) {
            Text("\"${p.name}\" hesabı ve kanal listesi silinecek.", color = Color(0xFFC8CBD0))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold, color = Muted,
        modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 4.dp))
}

/** Basit koyu pencere. */
@Composable
private fun SimpleDialog(
    title: String, onDismiss: () -> Unit, confirm: String, danger: Boolean = false, enabled: Boolean = true,
    onConfirm: () -> Unit, content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF16181E))
            .border(1.dp, Color(0xFF2A2F3A), RoundedCornerShape(24.dp)).padding(22.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(14.dp))
            content()
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Vazgeç", color = Color(0xFFC8CBD0)) }
                Button(onClick = onConfirm, enabled = enabled,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = if (danger) Color(0xFFE5483E) else Accent)) {
                    Text(confirm, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private data class AccountKind(val title: String, val desc: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)

private val accountKinds = listOf(
    AccountKind("M3U adresi", "Liste bağlantısı (.m3u / .m3u8)", Icons.Default.Link, Accent),
    AccountKind("Xtream Codes", "Sunucu, kullanıcı adı ve şifre", Icons.Default.Dns, Color(0xFFFF9F0A)),
    AccountKind("Dosya", "Telefondaki .m3u dosyası", Icons.Default.FolderOpen, Color(0xFF34C759)),
)

/** Hesap ekleme penceresi. */
@Composable
private fun AddAccountDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var mode by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { IptvStore.addFile(context, uri) }
                .onSuccess { p -> if (name.isNotBlank()) IptvStore.rename(p, name); onDismiss() }
                .onFailure { error = "Okunamadı: ${it.message}" }
            busy = false
        }
    }
    val ready = when (mode) { 0 -> url.isNotBlank(); 1 -> server.isNotBlank() && user.isNotBlank() && pass.isNotBlank(); else -> true }
    fun submit() {
        if (!ready || busy) return
        if (mode == 2) { pick.launch(arrayOf("*/*")); return }
        val target = if (mode == 0) url.trim() else IptvStore.xtreamUrl(server, user, pass)
        busy = true; error = null
        scope.launch {
            runCatching { IptvStore.addUrl(name.ifBlank { if (mode == 1) user else "" }, target) }
                .onSuccess { onDismiss() }
                .onFailure { error = "Liste yüklenemedi: ${it.message}"; AppLog.e("IPTV hesap ekleme", it) }
            busy = false
        }
    }

    @Composable
    fun Field(value: String, onChange: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
              password: Boolean = false, paste: Boolean = false) {
        val trailing: (@Composable () -> Unit)? = when {
                password -> ({ Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Göster", tint = Muted,
                    modifier = Modifier.clip(CircleShape).clickable { showPass = !showPass }.padding(8.dp)) })
                paste -> ({ Text("Yapıştır", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { clipboard.getText()?.text?.let { onChange(it.trim()) } }
                        .padding(horizontal = 10.dp, vertical = 6.dp)) })
                else -> null
            }
        OutlinedTextField(
            value, { onChange(it); error = null }, singleLine = true, label = { Text(label) },
            leadingIcon = { Icon(icon, null, tint = Muted) },
            trailingIcon = trailing,
            visualTransformation = if (password && !showPass) androidx.compose.ui.text.input.PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None,
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF16181E))
            .border(1.dp, Color(0xFF2A2F3A), RoundedCornerShape(24.dp)).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LiveTv, null, tint = Accent)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Yeni hesap", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("IPTV listesi ekle", color = Muted, fontSize = 12.sp)
                }
                Box(Modifier.size(38.dp).clip(CircleShape).clickable { if (!busy) onDismiss() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, "Kapat", tint = Color.White)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C1F26)).padding(4.dp)) {
                listOf("M3U", "Xtream", "Dosya").forEachIndexed { i, t ->
                    val src = remember { MutableInteractionSource() }
                    val focused by src.collectIsFocusedAsState()
                    Text(t, color = if (mode == i) Color.White else Color(0xFFC8CBD0), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (mode == i) Accent else if (focused) Color(0xFF2A3042) else Color.Transparent)
                            .clickable(interactionSource = src, indication = LocalIndication.current) { mode = i; error = null }
                            .padding(vertical = 9.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Spacer(Modifier.height(4.dp))
            Field(name, { name = it }, "Hesap adı", Icons.Default.Edit)
            when (mode) {
                0 -> Field(url, { url = it }, "M3U / M3U8 adresi", Icons.Default.Link, paste = true)
                1 -> {
                    Field(server, { server = it }, "Sunucu (http://adres:port)", Icons.Default.Dns, paste = true)
                    Field(user, { user = it }, "Kullanıcı adı", Icons.Default.Person)
                    Field(pass, { pass = it }, "Şifre", Icons.Default.Lock, password = true)
                }
                else -> {}
            }
            Text(
                when (mode) {
                    0 -> "IPTV sağlayıcının verdiği .m3u / .m3u8 bağlantısı. Liste eklendikten sonra kanallar kategorilere ayrılır."
                    1 -> "Sunucu, kullanıcı adı ve şifre IPTV sağlayıcının verdiği bilgilerdir. Liste eklendikten sonra kanallar kategorilere ayrılır."
                    else -> "Telefondaki .m3u / .m3u8 dosyasını seçeceksin."
                },
                color = Muted, fontSize = 11.5.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp)
            )
            error?.let {
                Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)).background(Color(0x1FFF6B6B)).padding(10.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (ready && !busy) Accent else Color(0xFF2A2D35))
                    .clickable(enabled = ready && !busy) { submit() },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Text("Liste indiriliyor…", modifier = Modifier.padding(start = 10.dp))
                } else Text(if (mode == 2) "Dosya seç" else "Hesabı ekle", color = if (ready) Color.White else Muted,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Text("Vazgeç", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp))
                    .clickable { if (!busy) onDismiss() }.padding(vertical = 10.dp))
        }
    }
}

// ======================================================================= Kanallar

/** Kanal kartı: afişse tam doldurur (2:3), kanal logosuysa ortalar ve adı altta gösterir. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelCard(ch: Channel, fav: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    var poster by remember(ch.logo) { mutableStateOf<Boolean?>(null) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.fillMaxWidth()
            .aspectRatio(2f / 3f)
            .scale(if (focused) 1.06f else 1f)
            .clip(shape)
            .background(Color(0xFF1C1F26))
            .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier.border(1.dp, Color(0x14FFFFFF), shape))
            .combinedClickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick, onLongClick = onLongClick)
    ) {
        if (ch.logo != null) {
            AsyncImage(
                model = ch.logo, contentDescription = ch.name,
                contentScale = if (poster == true) ContentScale.Crop else ContentScale.Fit,
                onSuccess = { st -> val d = st.result.drawable; poster = d.intrinsicHeight > d.intrinsicWidth * 1.15f },
                onError = { poster = false },
                modifier = Modifier.fillMaxSize().padding(if (poster == true) 0.dp else 18.dp)
            )
        }
        if (poster != true) {
            Box(
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
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).background(Color(0x99000000), CircleShape).padding(3.dp))
    }
}

/** Telefon dikey: üstte yatay sekme düğmesi. */
@Composable
private fun TabPill(label: String, count: Int, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.clip(shape)
            .background(if (selected) Color(0xFF1F2A44) else Color(0xFF171A21))
            .border(if (selected || focused) 2.dp else 1.dp, if (selected || focused) Color.White else Color(0x14FFFFFF), shape)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let { Icon(it, null, tint = if (selected) Color.White else Color(0xFFC8CBD0), modifier = Modifier.size(18.dp)) }
        Text(label, color = if (selected) Color(0xFFBFD3FF) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, modifier = Modifier.padding(start = if (icon != null) 6.dp else 0.dp))
        Text("  $count", color = Muted, fontSize = 15.sp)
    }
}

/** Yatay/TV: soldaki kategori satırı. */
@Composable
private fun SideCategory(label: String, count: Int, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(shape)
            .background(if (focused) Color(0xFF26324D) else if (selected) Color(0xFF181D29) else Color(0xFF13161C))
            .border(2.dp, if (selected || focused) Color.White else Color.Transparent, shape)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let { Icon(it, null, tint = Color(0xFFBFD3FF), modifier = Modifier.size(16.dp).padding(end = 2.dp)) }
        Text(label, color = if (selected) Color.White else Color(0xFFD5D8DD), fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = if (icon != null) 6.dp else 0.dp))
        Text("$count", color = Color(0xFFBFC3CA), fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun HeaderButton(label: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Text(label, color = Color(0xFFBFD3FF), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (focused) Color(0xFF26324D) else Color(0xFF171A21))
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp))
}

private const val TAB_RECENT = "__recent__"
private const val TAB_FAV = "__fav__"
private const val TAB_ALL = "__all__"

/** Bir hesabın kanalları. Yatayda soldan kategori listeli TV düzeni, dikeyde üst sekmeli düzen. */
@Composable
fun IptvListScreen(playlist: Playlist) {
    val context = LocalContext.current
    val wide = isWide()
    var channels by remember { mutableStateOf<List<Channel>?>(null) }
    var tab by rememberSaveable { mutableStateOf(TAB_ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }
    var clock by remember { mutableStateOf("") }

    LaunchedEffect(playlist, refresh) {
        channels = null
        channels = runCatching { IptvStore.channels(playlist, refresh > 0) }.getOrDefault(emptyList())
    }
    LaunchedEffect(toast) { if (toast != null) { delay(1500); toast = null } }
    LaunchedEffect(Unit) {
        val f = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) { clock = f.format(Date()); delay(15_000) }
    }

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

    val play: (Int) -> Unit = { i ->
        IptvStore.markWatched(visible[i].url)
        val list = visible.map { it.toPlayItem() }
        PlayerActivity.start(context, list, i)
    }
    val fav: (Channel) -> Unit = { ch ->
        toast = if (IptvStore.toggleFavorite(ch.url)) "★ Favorilere eklendi" else "Favorilerden çıkarıldı"
    }

    @Composable
    fun Grid(min: androidx.compose.ui.unit.Dp, padding: PaddingValues) {
        when {
            channels == null -> Loading("Kanallar yükleniyor…")
            visible.isEmpty() -> Message(when (tab) {
                TAB_RECENT -> "Henüz izlenen kanal yok."
                TAB_FAV -> "Favori yok. Bir kanala uzun basarak favorilere ekleyebilirsin."
                else -> "Kanal yok"
            })
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(min), contentPadding = padding,
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(visible) { i, ch -> ChannelCard(ch, ch.url in IptvStore.favorites, onClick = { play(i) }, onLongClick = { fav(ch) }) }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0E1015))) {
        if (wide) {
            // ---------- TV / yatay düzen
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(28.dp, 18.dp, 28.dp, 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Darknes IPTV", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Row(Modifier.clip(CircleShape).background(Color(0xFF171A21)).clickable { Nav.back() }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(paletteOf(playlist).last()))
                        Text(playlist.name, color = Color(0xFFBFD3FF), fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                        Text("  ·  Hesap değiştir", color = Color(0xFFC8CBD0), fontSize = 14.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    if (playlist.url != null) HeaderButton("Yenile") { refresh++ }
                    HeaderButton("Ayarlar") { Nav.go(Route.SettingsPage("iptv")) }
                    Text(clock, color = Color(0xFFBFD3FF), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.width(300.dp).fillMaxHeight(), contentPadding = PaddingValues(start = 24.dp, end = 8.dp, bottom = 24.dp)) {
                        item {
                            OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Ara…") },
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
                        }
                        item { SideCategory("Devam et", recent.size, tab == TAB_RECENT, Icons.Default.PlayArrow) { tab = TAB_RECENT } }
                        item { SideCategory("Favoriler", favs.size, tab == TAB_FAV, Icons.Default.Star) { tab = TAB_FAV } }
                        item { SideCategory("Tüm kanallar", all.size, tab == TAB_ALL) { tab = TAB_ALL } }
                        items(groups) { (g, n) -> SideCategory(g, n, tab == g) { tab = g } }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Grid(150.dp, PaddingValues(12.dp, 4.dp, 28.dp, 28.dp))
                    }
                }
            }
        } else {
            // ---------- Telefon dikey düzen
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp, 12.dp, 6.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Darknes IPTV", fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Row(Modifier.weight(1f, fill = false).clip(CircleShape).background(Color(0xFF171A21)).clickable { Nav.back() }
                        .padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(paletteOf(playlist).last()))
                        Text(playlist.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    if (playlist.url != null) HeaderButton("Yenile") { refresh++ }
                    HeaderButton("Ayarlar") { Nav.go(Route.SettingsPage("iptv")) }
                }
                OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Kanal, film, dizi ara…") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                LazyRow(contentPadding = PaddingValues(16.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { TabPill("Devam et", recent.size, tab == TAB_RECENT, Icons.Default.PlayArrow) { tab = TAB_RECENT } }
                    item { TabPill("Favoriler", favs.size, tab == TAB_FAV, Icons.Default.Star) { tab = TAB_FAV } }
                    item { TabPill("Tüm kanallar", all.size, tab == TAB_ALL) { tab = TAB_ALL } }
                    items(groups) { (g, n) -> TabPill(g, n, tab == g) { tab = g } }
                }
                Grid(104.dp, PaddingValues(16.dp, 6.dp, 16.dp, BottomSpace))
            }
        }
        toast?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (wide) 30.dp else 110.dp)
                .clip(CircleShape).background(Color(0xE6222731)).padding(horizontal = 18.dp, vertical = 10.dp))
        }
    }
}
