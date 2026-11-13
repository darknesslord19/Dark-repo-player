package com.streambox.app.ui

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

// ======================================================================= Hesap seç

@Composable
private fun AccountTile(p: Playlist, editing: Boolean, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val colors = paletteOf(p)
    val shape = RoundedCornerShape(26.dp)
    Box(Modifier.size(150.dp).scale(if (focused) 1.06f else 1f)) {
        Box(
            Modifier.fillMaxSize().clip(shape)
                .background(Brush.linearGradient(colors, start = Offset(0f, 0f), end = Offset(400f, 400f)))
                .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
                .clickable(interactionSource = src, indication = LocalIndication.current) { if (editing) onRename() else onClick() },
            contentAlignment = Alignment.Center
        ) {
            // Süs daireleri
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White.copy(alpha = 0.07f), radius = size.width * 0.26f, center = Offset(size.width * 0.74f, size.height * 0.30f))
                drawCircle(Color.White.copy(alpha = 0.06f), radius = size.width * 0.13f, center = Offset(size.width * 0.26f, size.height * 0.72f))
                drawCircle(Color.White.copy(alpha = 0.12f), radius = size.width * 0.03f, center = Offset(size.width * 0.14f, size.height * 0.20f))
                drawCircle(Color.White.copy(alpha = 0.10f), radius = size.width * 0.035f, center = Offset(size.width * 0.86f, size.height * 0.64f))
            }
            Text(p.name.uppercase(), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                lineHeight = 23.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp))
        }
        if (editing) {
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(30.dp).clip(CircleShape).background(Color(0xE6FF453A))
                .clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, "Sil", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(Color(0x99000000)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Edit, "Adını değiştir", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier.size(150.dp).scale(if (focused) 1.06f else 1f).clip(shape).background(Color(0xFF24211E))
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Default.Add, "Hesap ekle", tint = Color.White, modifier = Modifier.size(56.dp)) }
}

/** IPTV giriş ekranı: hesap (liste) seç, + ile ekle, kalemle düzenle. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IptvScreen() {
    var editing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }

    Box(Modifier.fillMaxSize().background(Color(0xFF16130F))) {
        // Kalem: düzenleme modu
        Box(Modifier.align(Alignment.TopEnd).padding(16.dp).size(48.dp).clip(CircleShape)
            .background(if (editing) Accent else Color.Transparent)
            .clickable { editing = !editing }, contentAlignment = Alignment.Center) {
            Icon(if (editing) Icons.Default.Check else Icons.Default.Edit, if (editing) "Bitti" else "Düzenle",
                tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 70.dp, bottom = BottomSpace),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(if (isWide()) 10.dp else 120.dp))
            Text(if (editing) "Hesapları düzenle" else "Hesap Seç", fontSize = 30.sp, fontWeight = FontWeight.Medium, color = Color.White)
            Spacer(Modifier.height(28.dp))
            FlowRow(
                Modifier.widthIn(max = 900.dp).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                IptvStore.playlists.forEach { p ->
                    AccountTile(p, editing, onClick = { Nav.go(Route.IptvList(p)) },
                        onRename = { renaming = p }, onDelete = { deleting = p })
                }
                if (!editing) AddTile { adding = true }
            }
            if (editing) Text("Ad değiştirmek için kutuya dokun", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 20.dp))
        }
    }

    if (adding) AddAccountDialog { adding = false }
    renaming?.let { p ->
        var name by remember(p) { mutableStateOf(p.name) }
        SimpleDialog("Adını değiştir", onDismiss = { renaming = null }, confirm = "Kaydet", onConfirm = { IptvStore.rename(p, name); renaming = null }) {
            OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
    deleting?.let { p ->
        SimpleDialog("Silinsin mi?", onDismiss = { deleting = null }, confirm = "Sil", danger = true,
            onConfirm = { IptvStore.remove(p); deleting = null }) {
            Text("\"${p.name}\" hesabı ve kanal listesi silinecek.", color = Color(0xFFC8CBD0))
        }
    }
}

/** Basit koyu pencere. */
@Composable
private fun SimpleDialog(
    title: String, onDismiss: () -> Unit, confirm: String, danger: Boolean = false, enabled: Boolean = true,
    onConfirm: () -> Unit, content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF1C1A17)).padding(22.dp)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(16.dp))
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

/** Hesap ekleme: M3U adresi, Xtream Codes ya da dosya. */
@Composable
private fun AddAccountDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
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
    SimpleDialog("Yeni hesap", onDismiss = onDismiss, confirm = if (busy) "Yükleniyor…" else if (mode == 2) "Dosya seç" else "Ekle",
        enabled = ready && !busy,
        onConfirm = {
            if (mode == 2) { pick.launch(arrayOf("*/*")); return@SimpleDialog }
            val target = if (mode == 0) url.trim() else IptvStore.xtreamUrl(server, user, pass)
            busy = true; error = null
            scope.launch {
                runCatching { IptvStore.addUrl(name.ifBlank { if (mode == 1) user else "" }, target) }
                    .onSuccess { onDismiss() }
                    .onFailure { error = "Yüklenemedi: ${it.message}"; AppLog.e("IPTV hesap ekleme", it) }
                busy = false
            }
        }) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF2A2621)).padding(4.dp)) {
            listOf("M3U adresi", "Xtream", "Dosya").forEachIndexed { i, t ->
                Text(t, color = Color.White, fontSize = 14.sp, fontWeight = if (mode == i) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (mode == i) Accent else Color.Transparent)
                        .clickable { mode = i }.padding(vertical = 9.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Hesap adı") }, modifier = Modifier.fillMaxWidth())
        when (mode) {
            0 -> OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("M3U / M3U8 adresi") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            1 -> {
                OutlinedTextField(server, { server = it }, singleLine = true, label = { Text("Sunucu (http://adres:port)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(user, { user = it }, singleLine = true, label = { Text("Kullanıcı adı") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(pass, { pass = it }, singleLine = true, label = { Text("Şifre") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            else -> Text("Telefondaki .m3u / .m3u8 dosyasını seç.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        }
        if (busy) Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("Liste indiriliyor…", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
        }
        error?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
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
        val list = visible.map { PlayItem(it.name, it.url, it.headers, live = true, label = it.group) }
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
                    Text("Canlı TV", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
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
                    Text("Canlı TV", fontSize = 24.sp, fontWeight = FontWeight.Black)
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
