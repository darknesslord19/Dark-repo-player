package com.streambox.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streambox.app.AppLog
import com.streambox.app.StreamBoxApp
import com.streambox.app.nuvio.NuvioRepoManager
import com.streambox.app.nuvio.NuvioScraper
import com.streambox.app.plugins.InstalledPlugin
import com.streambox.app.plugins.PluginManager
import com.streambox.app.plugins.RepoManager
import com.streambox.app.plugins.RepoPlugin
import com.streambox.app.restartApp
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ renkler / yardımcılar

private val ECard = Color(0xFF1A1D24)
private val EStroke = Color(0xFF2A2F3A)
private val Green = Color(0xFF34C759)
private val Red = Color(0xFFFF6B6B)
private val Orange = Color(0xFFFFB020)
private val Gold = Color(0xFFE2C48F)

private fun flagOf(lang: String?): String = when (lang?.lowercase()?.take(2)) {
    "tr" -> "🇹🇷"; "en" -> "🇬🇧"; "de" -> "🇩🇪"; "fr" -> "🇫🇷"; "es" -> "🇪🇸"; "it" -> "🇮🇹"; "pt" -> "🇵🇹"
    "ar" -> "🇸🇦"; "ru" -> "🇷🇺"; "ja" -> "🇯🇵"; "ko" -> "🇰🇷"; "zh" -> "🇨🇳"; "hi" -> "🇮🇳"; "id" -> "🇮🇩"; "vi" -> "🇻🇳"
    "th" -> "🇹🇭"; "pl" -> "🇵🇱"; "nl" -> "🇳🇱"; else -> "🌐"
}

private fun langName(lang: String?): String = when (lang?.lowercase()?.take(2)) {
    "tr" -> "Türkçe"; "en" -> "İngilizce"; "de" -> "Almanca"; "fr" -> "Fransızca"; "es" -> "İspanyolca"; "it" -> "İtalyanca"
    "pt" -> "Portekizce"; "ar" -> "Arapça"; "ru" -> "Rusça"; "ja" -> "Japonca"; "ko" -> "Korece"; "zh" -> "Çince"
    "hi" -> "Hintçe"; "id" -> "Endonezce"; null, "" -> "Çok dilli"; else -> lang.uppercase()
}

private fun sizeText(bytes: Long?): String? = when {
    bytes == null || bytes <= 0 -> null
    bytes < 1024 * 1024 -> "${bytes / 1024} kB"
    else -> "%.1f MB".format(bytes / 1048576f)
}

/** CloudStream TvType adlarına göre sekmeler. */
private val repoTabs = listOf(
    "Tümü" to emptySet(),
    "Filmler" to setOf("Movie", "AnimeMovie"),
    "Diziler" to setOf("TvSeries", "Cartoon"),
    "Animeler" to setOf("Anime", "AnimeMovie", "OVA"),
    "Asya dizileri" to setOf("AsianDrama"),
    "Canlı" to setOf("Live"),
    "Belgesel" to setOf("Documentary"),
    "Diğer" to setOf("Others", "Music", "AudioBook", "Audio", "Podcast", "CustomMedia", "Torrent", "NSFW"),
)

/** Yuvarlak çerçeveli ikon düğmesi; kumandayla seçilebilir. */
@Composable
private fun RoundAction(icon: ImageVector, desc: String, filled: Boolean = false, tint: Color = Color.White, size: Dp = 44.dp, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Box(
        Modifier.size(size).clip(RoundedCornerShape(14.dp))
            .background(if (filled) Gold else if (focused) Color(0xFF2A3042) else Color.Transparent)
            .border(1.5.dp, if (focused) Color.White else if (filled) Color.Transparent else Color(0xFF4A4F5A), RoundedCornerShape(14.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = if (filled) Color(0xFF1A1A1A) else tint, modifier = Modifier.size(size * 0.46f)) }
}

/** Satır sonundaki çıplak ikon düğmesi. */
@Composable
private fun BareAction(icon: ImageVector, desc: String, tint: Color = Color.White, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (focused) Color(0xFF2A3042) else Color.Transparent)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** Eklenti/depo ikonu: görsel yoksa yapboz. */
@Composable
private fun PluginIcon(url: String?, size: Dp = 36.dp, fallback: ImageVector = Icons.Default.Extension) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Color(0xFF262A33)), contentAlignment = Alignment.Center) {
            Icon(fallback, null, tint = Color(0xFFC8CBD0), modifier = Modifier.size(size * 0.6f))
        }
        if (!url.isNullOrBlank()) AsyncImage(url, null, modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)))
    }
}

/** Büyük kart kabı. */
@Composable
private fun ECardBox(selected: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(shape)
            .background(if (selected) Color(0xFF232A3A) else ECard)
            .border(1.5.dp, if (focused) Color.White else if (selected) Accent else Color.Transparent, shape)
            .then(if (onClick != null) Modifier.clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) { content() }
}

@Composable
private fun Confirm(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color(0xFF1A1D24),
        title = { Text(title) }, text = { Text(text, color = Color(0xFFC8CBD0)) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = Red, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

/** Sayfa başlığı: geri, başlık, sağda düğmeler. */
@Composable
private fun PageHeader(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(8.dp, 12.dp, 12.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) BareAction(Icons.AutoMirrored.Filled.ArrowBack, "Geri", onClick = onBack)
        else Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp))
        actions()
    }
}

/** Alt çizgili sekmeler (YÜKLÜ / DEPOLAR). */
@Composable
private fun UnderlineTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF15171C))) {
        tabs.forEachIndexed { i, t ->
            val src = remember { MutableInteractionSource() }
            val focused by src.collectIsFocusedAsState()
            val color by animateColorAsState(if (i == selected) Color.White else Color(0xFF8E939B), label = "tab")
            Column(
                Modifier.weight(1f).background(if (focused) Color(0xFF1E2230) else Color.Transparent)
                    .clickable(interactionSource = src, indication = LocalIndication.current) { onSelect(i) }
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(t.uppercase(), color = color, fontSize = 17.sp, fontWeight = FontWeight.Medium, letterSpacing = 3.sp)
                Spacer(Modifier.height(14.dp))
                Box(Modifier.width(110.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (i == selected) Color(0xFFE53935) else Color.Transparent))
            }
        }
    }
}

/** Yatay metin sekmeleri (Filmler, Diziler …). */
@Composable
private fun TextTabs(tabs: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.fillMaxWidth().background(Color(0xFF15171C))) {
        items(tabs.size) { i ->
            val src = remember { MutableInteractionSource() }
            val focused by src.collectIsFocusedAsState()
            val (label, count) = tabs[i]
            Column(
                Modifier.clip(RoundedCornerShape(10.dp)).background(if (focused) Color(0xFF1E2230) else Color.Transparent)
                    .clickable(interactionSource = src, indication = LocalIndication.current) { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 15.sp, color = if (i == selected) Color.White else Color(0xFFB8BCC2),
                        fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Medium)
                    if (count > 0) Text(" $count", fontSize = 13.sp, color = Muted)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(28.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (i == selected) Gold else Color.Transparent))
            }
        }
    }
}

// ================================================================== Eklentiler (YÜKLÜ / DEPOLAR)

@Composable
fun ExtensionsScreen() {
    val context = LocalContext.current
    var search by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val wide = isWide()

    // Depoya bağlı olmayan (dosyadan kurulan ya da deposu silinen) eklentiler
    val repoUrls = RepoManager.repos.map { it.url }.toSet()
    val local = PluginManager.installed.filter { it.repoUrl == null || it.repoUrl !in repoUrls }
    val cs = RepoManager.repos.filter { query.isBlank() || it.name.contains(query, true) }
    val nv = NuvioRepoManager.repos.filter { query.isBlank() || it.name.contains(query, true) }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Eklentiler", null) {
            if (PluginManager.needsRestart) BareAction(Icons.Default.RestartAlt, "Yeniden başlat", tint = Orange) { restartApp(context) }
            BareAction(if (search) Icons.Default.Close else Icons.Default.Search, "Ara") { search = !search; if (!search) query = "" }
            AddRepoButton { adding = true }
        }
        if (search) OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Depo ara…") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))

        // Özet
        val ok = PluginManager.installed.count { PluginManager.results[it.internalName]?.ok == true }
        Text(
            "${RepoManager.repos.size + NuvioRepoManager.repos.size} depo · ${PluginManager.installed.size} CloudStream eklentisi" +
                (if (PluginManager.installed.size - ok > 0) " (${PluginManager.installed.size - ok} sorunlu)" else "") +
                " · ${NuvioRepoManager.scrapers.count { it.enabled }} Nuvio eklentisi",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(16.dp, 2.dp, 16.dp, 6.dp)
        )

        PluginManager.bulk?.let { b ->
            Row(Modifier.fillMaxWidth().padding(12.dp, 4.dp).clip(RoundedCornerShape(14.dp)).background(ECard).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kuruluyor ${b.done + 1} / ${b.total} · ${b.current}", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator(progress = { (b.done + 1f) / b.total },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(RoundedCornerShape(2.dp)), color = Gold)
                }
                BareAction(Icons.Default.Close, "Durdur") { PluginManager.cancelBulk() }
            }
        }

        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(if (wide) 380.dp else 600.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = BottomSpace),
            modifier = Modifier.fillMaxSize()
        ) {
            if (cs.isEmpty() && nv.isEmpty() && local.isEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Message("Henüz depo yok. Sağ üstteki \"Depo ekle\" ile CloudStream ya da Nuvio deposu ekle.")
                }
            }
            gridItems(cs, key = { "cs-" + it.url }) { r ->
                val inst = PluginManager.installed.count { it.repoUrl == r.url }
                RepoCard(r.name, "CloudStream · $inst kurulu" + (RepoManager.pluginCache[r.url]?.size?.let { " / $it" } ?: ""), r.iconUrl,
                    onOpen = { Nav.go(Route.RepoPage("cs", r.url)) }) {
                    confirm = "\"${r.name}\" deposu silinsin mi? (Kurulu eklentiler kalır)" to { RepoManager.removeRepo(context, r) }
                }
            }
            gridItems(nv, key = { "nv-" + it.url }) { r ->
                val list = NuvioRepoManager.scrapers.filter { it.repoUrl == r.url }
                RepoCard(r.name, "Nuvio · ${list.count { it.enabled }} açık / ${list.size}", list.firstOrNull { it.logo != null }?.logo,
                    onOpen = { Nav.go(Route.RepoPage("nuvio", r.url)) }) {
                    confirm = "\"${r.name}\" Nuvio deposu silinsin mi?" to { NuvioRepoManager.removeRepo(r) }
                }
            }
            if (local.isNotEmpty()) gridItems(listOf(Unit), key = { "local" }) {
                RepoCard("Yerel eklentiler", "Depoya bağlı olmayan · ${local.size} eklenti", null,
                    onOpen = { Nav.go(Route.RepoPage("local", "")) }, onDelete = null)
            }
        }
    }

    if (adding) AddRepoDialog(onDismiss = { adding = false }) {}
    confirm?.let { (text, action) -> Confirm("Emin misin?", text, "Evet", action) { confirm = null } }
}

/** Izgara için items kısayolu. */
private fun <T> androidx.compose.foundation.lazy.grid.LazyGridScope.gridItems(
    list: List<T>, key: ((T) -> Any)? = null, content: @Composable (T) -> Unit,
) = items(list.size, key = key?.let { k -> { i: Int -> k(list[i]) } }) { content(list[it]) }

/** Üstteki "Depo ekle" düğmesi. */
@Composable
private fun AddRepoButton(onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Row(
        Modifier.padding(start = 4.dp).clip(RoundedCornerShape(12.dp)).background(Gold)
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Add, null, tint = Color(0xFF1A1A1A), modifier = Modifier.size(18.dp))
        Text("Depo ekle", color = Color(0xFF1A1A1A), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun KindChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) Gold else Color(0xFF23262E))
            .border(1.5.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(50))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (selected) Color(0xFF1A1A1A) else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text("  $count", color = if (selected) Color(0xFF3A3A3A) else Muted, fontSize = 14.sp)
    }
}

/** "15 eklenti / 15 yüklü · 0 yüklenmedi" satırı + sağda düğmeler. */
@Composable
private fun SummaryRow(title: String, subtitle: String, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF15171C)).padding(16.dp, 10.dp, 12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 13.sp, color = Color(0xFFB8BCC2))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
    }
}

@Composable
private fun RepoCard(name: String, subtitle: String, icon: String?, onOpen: () -> Unit, onDelete: (() -> Unit)?) {
    ECardBox(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PluginIcon(icon, 40.dp, Icons.Default.FolderSpecial)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onDelete != null) BareAction(Icons.Default.Delete, "Depoyu sil", tint = Color(0xFFB8BCC2), onClick = onDelete)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted)
        }
    }
}

@Composable
private fun InstalledCard(p: InstalledPlugin, onOpen: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val r = PluginManager.results[p.internalName]
    val settings = PluginManager.settingsOf(context, p)
    ECardBox(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                PluginIcon(p.iconUrl)
                val dot = when { !p.enabled -> Muted; r == null -> Orange; r.ok -> Green; else -> Red }
                Box(Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape).background(ECard).padding(2.dp).clip(CircleShape).background(dot))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull("v${p.version}", sizeText(PluginManager.sizeOf(context, p))).joinToString("  "),
                    fontSize = 13.sp, color = Color(0xFFB8BCC2))
                Text(
                    when { !p.enabled -> "Devre dışı"; r == null -> "Bekliyor"; r.ok -> r.providers.joinToString(", ").ifBlank { "Yüklendi" }; else -> "Hata · ayrıntı için dokun" },
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = when { !p.enabled -> Muted; r == null -> Orange; r.ok -> Color(0xFF9BE3B0); else -> Red }
                )
            }
            if (settings != null) BareAction(Icons.Default.Tune, "Ayarlar") { openPluginSettings(context, settings) }
            BareAction(Icons.Default.Delete, "Kaldır", onClick = onDelete)
        }
    }
}

private fun openPluginSettings(context: android.content.Context, settings: (android.content.Context) -> Unit) {
    val act = com.lagradost.cloudstream3.CommonActivity.activity ?: context
    runCatching { settings(act) }.onFailure {
        AppLog.e("Eklenti ayarları açılamadı", it)
        Toast.makeText(context, "Bu eklentinin ayarları açılamadı", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun InstalledDetailDialog(p: InstalledPlugin, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val r = PluginManager.results[p.internalName]
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color(0xFF1A1D24),
        title = { Text(p.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sürüm ${p.version} · ${sizeText(PluginManager.sizeOf(context, p)) ?: "?"}", color = Muted)
                Text(
                    when { !p.enabled -> "Durum: devre dışı"; r == null -> "Durum: yükleniyor"; r.ok -> "Kaynaklar: ${r.providers.joinToString(", ").ifBlank { "—" }}"; else -> r.message },
                    color = if (r?.ok == false) Red else Color(0xFFC8CBD0), fontSize = 14.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PowerSettingsNew, null, tint = if (p.enabled) Green else Muted)
                    Text("Etkin", modifier = Modifier.padding(start = 10.dp).weight(1f))
                    Switch(p.enabled, { PluginManager.setEnabled(context, p, it) }, colors = SwitchDefaults.colors(checkedTrackColor = Gold))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
}

@Composable
private fun ScraperCard(s: NuvioScraper) {
    ECardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PluginIcon(s.logo)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(s.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${flagOf(s.language)} ${langName(s.language)}  ${if (s.version.isNotBlank()) "v${s.version}" else ""}",
                    fontSize = 13.sp, color = Color(0xFFB8BCC2), maxLines = 1)
                Text(s.supportedTypes.joinToString(", ") { if (it == "tv") "Dizi" else if (it == "movie") "Film" else it } +
                    if (s.description.isNotBlank()) " · ${s.description}" else "",
                    fontSize = 13.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Switch(s.enabled, { NuvioRepoManager.setEnabled(s, it) }, colors = SwitchDefaults.colors(checkedTrackColor = Gold))
        }
    }
}

/** Depo ekleme: CloudStream / Nuvio / dosya. */
@Composable
private fun AddRepoDialog(onDismiss: () -> Unit, onAdded: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableIntStateOf(0) }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { PluginManager.installLocal(context, uri) }
                .onSuccess { onDismiss() }.onFailure { error = "Kurulamadı: ${it.message}" }
            busy = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color(0xFF1A1D24),
        title = { Text("Depo ekle") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF23262E)).padding(4.dp)) {
                    listOf("CloudStream", "Nuvio", ".cs3 dosyası").forEachIndexed { i, t ->
                        Text(t, color = if (kind == i) Color(0xFF1A1A1A) else Color.White, fontSize = 13.sp, textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (kind == i) Gold else Color.Transparent)
                                .clickable { kind = i; error = null }.padding(vertical = 9.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (kind < 2) {
                    OutlinedTextField(url, { url = it }, singleLine = true,
                        label = { Text(if (kind == 0) "repo.json, cloudstreamrepo:// ya da kısa kod" else "manifest.json ya da kullanıcı/repo") },
                        modifier = Modifier.fillMaxWidth())
                } else Text("Telefondaki bir .cs3 eklenti dosyasını seç.", color = Muted)
                if (busy) Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Ekleniyor…", color = Muted, modifier = Modifier.padding(start = 10.dp))
                }
                error?.let { Text(it, color = Red, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && (kind == 2 || url.isNotBlank()), onClick = {
                if (kind == 2) { pick.launch(arrayOf("*/*")); return@TextButton }
                busy = true; error = null
                scope.launch {
                    val res = runCatching {
                        if (kind == 0) RepoManager.addRepo(context, url).also { RepoManager.pluginsOf(it, refresh = true) }.url
                        else NuvioRepoManager.addRepo(url).url
                    }
                    busy = false
                    res.onSuccess { u -> onAdded(); onDismiss(); Nav.go(Route.RepoPage(if (kind == 0) "cs" else "nuvio", u)) }
                        .onFailure { error = "Eklenemedi: ${it.message}"; AppLog.e("Depo ekleme", it) }
                }
            }) { Text(if (kind == 2) "Dosya seç" else "Ekle", fontWeight = FontWeight.Bold, color = Gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

// ================================================================== Depo sayfası

@Composable
fun RepoPageScreen(route: Route.RepoPage) {
    when (route.kind) {
        "nuvio" -> NuvioRepoPage(route.url)
        "local" -> LocalPluginsPage()
        else -> CsRepoPage(route.url)
    }
}

/** Depoya bağlı olmayan kurulu eklentiler. */
@Composable
private fun LocalPluginsPage() {
    val context = LocalContext.current
    var detail by remember { mutableStateOf<InstalledPlugin?>(null) }
    var adding by remember { mutableStateOf(false) }
    val repoUrls = RepoManager.repos.map { it.url }.toSet()
    val local = PluginManager.installed.filter { it.repoUrl == null || it.repoUrl !in repoUrls }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Yerel eklentiler", onBack = { Nav.back() }) {
            BareAction(Icons.Default.FileOpen, ".cs3 dosyası kur") { adding = true }
        }
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(if (isWide()) 380.dp else 600.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = BottomSpace), modifier = Modifier.fillMaxSize()
        ) {
            if (local.isEmpty()) item { Message("Yerel eklenti yok") }
            gridItems(local, key = { "l-" + it.internalName }) { p ->
                InstalledCard(p, onOpen = { detail = p }) { PluginManager.uninstall(context, p) }
            }
        }
    }
    detail?.let { p -> InstalledDetailDialog(p) { detail = null } }
    if (adding) AddRepoDialog(onDismiss = { adding = false }) {}
}

@Composable
private fun CsRepoPage(url: String) {
    val context = LocalContext.current
    val appScope = (context.applicationContext as StreamBoxApp).scope
    val scope = rememberCoroutineScope()
    val repo = RepoManager.repos.firstOrNull { it.url == url }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var search by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    val installing = remember { mutableStateMapOf<String, Boolean>() }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<InstalledPlugin?>(null) }

    LaunchedEffect(url) {
        if (repo != null && RepoManager.pluginCache[url] == null) {
            loading = true
            runCatching { RepoManager.pluginsOf(repo) }.onFailure { AppLog.e("Depo listesi", it) }
            loading = false
        }
    }
    if (repo == null) { Message("Depo bulunamadı"); return }

    val all = RepoManager.pluginCache[url].orEmpty()
    val tabs = repoTabs.map { (label, types) ->
        label to if (types.isEmpty()) all.size else all.count { p -> p.tvTypes.any { it in types } }
    }.filterIndexed { i, (_, n) -> i == 0 || n > 0 }
    val curTypes = repoTabs.firstOrNull { it.first == tabs.getOrNull(tab)?.first }?.second ?: emptySet()
    val shown = all.filter { p ->
        (curTypes.isEmpty() || p.tvTypes.any { it in curTypes }) && (query.isBlank() || p.name.contains(query, true))
    }
    val installedCount = shown.count { PluginManager.find(it.internalName) != null }

    fun install(p: RepoPlugin) {
        installing[p.internalName] = true
        scope.launch {
            runCatching { PluginManager.install(context, p) }.onFailure { AppLog.e("Kurulum: ${p.name}", it) }
            installing[p.internalName] = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(repo.name, onBack = { Nav.back() }) {
            BareAction(if (search) Icons.Default.Close else Icons.Default.Search, "Ara") { search = !search; if (!search) query = "" }
            BareAction(Icons.Default.Refresh, "Yenile") {
                scope.launch { loading = true; runCatching { RepoManager.pluginsOf(repo, refresh = true) }; loading = false }
            }
        }
        if (search) OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Bu depoda ara…") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
        if (tabs.size > 1) TextTabs(tabs, tab.coerceIn(0, tabs.size - 1)) { tab = it }
        SummaryRow(
            if (selecting) "${selected.size} seçili" else "${shown.size} eklenti",
            "$installedCount yüklü · ${shown.size - installedCount} yüklenmedi"
        ) {
            val targets = if (selecting) shown.filter { it.internalName in selected } else shown
            RoundAction(Icons.Default.Download, if (selecting) "Seçilenleri kur" else "Hepsini kur", filled = true) {
                PluginManager.installAll(context, appScope, targets)
                if (selecting) { selecting = false; selected.clear() }
            }
            RoundAction(Icons.Default.DeleteForever, if (selecting) "Seçilenleri kaldır" else "Hepsini kaldır") {
                val inst = targets.mapNotNull { PluginManager.find(it.internalName) }
                if (inst.isNotEmpty()) confirm = "${inst.size} eklenti kaldırılsın mı?" to {
                    PluginManager.uninstallAll(context, inst); selecting = false; selected.clear()
                }
            }
            RoundAction(Icons.Default.ChecklistRtl, "Seç", tint = if (selecting) Gold else Color.White) {
                selecting = !selecting; selected.clear()
            }
        }
        PluginManager.bulk?.let { b ->
            LinearProgressIndicator(progress = { (b.done + 1f) / b.total }, color = Gold, modifier = Modifier.fillMaxWidth().height(3.dp))
        }
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(if (isWide()) 380.dp else 600.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = BottomSpace), modifier = Modifier.fillMaxSize()
        ) {
            if (loading && all.isEmpty()) item { Loading() }
            if (!loading && shown.isEmpty()) item { Message("Eklenti yok") }
            gridItems(shown) { p ->
                val inst = PluginManager.find(p.internalName)
                val isSel = p.internalName in selected
                ECardBox(selected = isSel, onClick = when {
                    selecting -> ({ if (isSel) selected.remove(p.internalName) else selected.add(p.internalName) })
                    inst != null -> ({ detail = inst })
                    else -> null
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selecting) Icon(if (isSel) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null,
                            tint = if (isSel) Gold else Muted, modifier = Modifier.padding(end = 12.dp).size(26.dp))
                        PluginIcon(p.iconUrl)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${flagOf(p.language)} ${langName(p.language)}  v${p.version}  ${sizeText(p.fileSize) ?: ""}",
                                fontSize = 13.sp, color = Color(0xFFB8BCC2), maxLines = 1)
                            if (!p.description.isNullOrBlank()) Text(p.description, fontSize = 12.sp, color = Muted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (p.status == 0) Text("Kapalı (depo sahibi devre dışı bıraktı)", fontSize = 12.sp, color = Red)
                            inst?.let { i ->
                                val r = PluginManager.results[i.internalName]
                                if (r != null && !r.ok) Text("Hata · ayrıntı için dokun", fontSize = 12.sp, color = Red)
                                if (!i.enabled) Text("Devre dışı", fontSize = 12.sp, color = Muted)
                            }
                        }
                        if (!selecting) when {
                            installing[p.internalName] == true -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = Gold)
                            inst == null -> BareAction(Icons.Default.Download, "Kur", tint = Gold) { install(p) }
                            else -> {
                                if (inst.version < p.version) BareAction(Icons.Default.Upgrade, "Güncelle", tint = Orange) { install(p) }
                                PluginManager.settingsOf(context, inst)?.let { st -> BareAction(Icons.Default.Tune, "Ayarlar") { openPluginSettings(context, st) } }
                                BareAction(Icons.Default.Delete, "Kaldır") { PluginManager.uninstall(context, inst) }
                            }
                        }
                    }
                }
            }
        }
    }
    confirm?.let { (text, action) -> Confirm("Emin misin?", text, "Kaldır", action) { confirm = null } }
    detail?.let { p -> InstalledDetailDialog(p) { detail = null } }
}

@Composable
private fun NuvioRepoPage(url: String) {
    val scope = rememberCoroutineScope()
    val repo = NuvioRepoManager.repos.firstOrNull { it.url == url }
    var search by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    if (repo == null) { Message("Depo bulunamadı"); return }
    val all = NuvioRepoManager.scrapers.filter { it.repoUrl == url }
    val tabs = listOf("Tümü" to all.size, "Filmler" to all.count { "movie" in it.supportedTypes }, "Diziler" to all.count { "tv" in it.supportedTypes })
    val shown = all.filter {
        (tab == 0 || (tab == 1 && "movie" in it.supportedTypes) || (tab == 2 && "tv" in it.supportedTypes)) &&
            (query.isBlank() || it.name.contains(query, true))
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(repo.name, onBack = { Nav.back() }) {
            BareAction(if (search) Icons.Default.Close else Icons.Default.Search, "Ara") { search = !search; if (!search) query = "" }
            BareAction(Icons.Default.Refresh, "Yenile") { scope.launch { runCatching { NuvioRepoManager.addRepo(url) } } }
        }
        if (search) OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Bu depoda ara…") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
        TextTabs(tabs, tab) { tab = it }
        val on = shown.count { it.enabled }
        SummaryRow("${shown.size} eklenti", "$on açık · ${shown.size - on} kapalı") {
            RoundAction(Icons.Default.PowerSettingsNew, "Hepsini aç", filled = true) { shown.forEach { NuvioRepoManager.setEnabled(it, true) } }
            RoundAction(Icons.Default.Close, "Hepsini kapat") { shown.forEach { NuvioRepoManager.setEnabled(it, false) } }
        }
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(if (isWide()) 380.dp else 600.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = BottomSpace), modifier = Modifier.fillMaxSize()
        ) {
            if (shown.isEmpty()) item { Message("Eklenti yok") }
            gridItems(shown) { s -> ScraperCard(s) }
        }
    }
}

@Suppress("unused")
private val keepArrow = Icons.AutoMirrored.Filled.KeyboardArrowRight
