package com.streambox.app.ui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streambox.app.AppLog
import com.streambox.app.StreamBoxApp
import com.streambox.app.nuvio.NuvioRepoManager
import com.streambox.app.plugins.InstalledPlugin
import com.streambox.app.plugins.PluginManager
import com.streambox.app.plugins.Repo
import com.streambox.app.plugins.RepoManager
import com.streambox.app.plugins.RepoPlugin
import com.streambox.app.restartApp
import kotlinx.coroutines.launch

private val Card = Color(0xFF171A21)
private val CardHi = Color(0xFF1D2129)
private val Green = Color(0xFF34C759)
private val Red = Color(0xFFFF6B6B)
private val Orange = Color(0xFFFFB020)

// ------------------------------------------------------------------ ortak parçalar

/** Kumandayla seçilebilen, ipucu yazılı yuvarlak ikon düğmesi. */
@Composable
private fun IconAction(icon: ImageVector, desc: String, tint: Color = Color.White, bg: Color = Color(0xFF23262E), size: Dp = 38.dp, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Box(
        Modifier.size(size).clip(CircleShape).background(if (focused) Accent else bg)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = if (focused) Color.White else tint, modifier = Modifier.size(size * 0.52f)) }
}

/** Uygulama ikonu; yoksa baş harf. */
@Composable
private fun Logo(url: String?, name: String, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Color(0xFF262A33)), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = Accent, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
        if (!url.isNullOrBlank()) AsyncImage(url, null, modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)))
    }
}

/** Küçük renkli etiket. */
@Composable
private fun Badge(text: String, color: Color) {
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 2.dp))
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Color(0xFFB8BCC2),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) Accent else Color(0xFF1F232C))
            .border(1.5.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(50))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp))
}

@Composable
private fun SearchField(value: String, hint: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, singleLine = true, placeholder = { Text(hint, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
        trailingIcon = { if (value.isNotEmpty()) IconAction(Icons.Default.Close, "Temizle", size = 30.dp) { onChange("") } },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color(0xFF2A2F3A), unfocusedContainerColor = Card, focusedContainerColor = Card),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

/** Adres girip ekleme kartı. */
@Composable
private fun AddCard(hint: String, busy: Boolean, extra: (@Composable () -> Unit)? = null, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 8.dp).clip(RoundedCornerShape(18.dp)).background(Card).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            text, { text = it }, singleLine = true, placeholder = { Text(hint, fontSize = 13.sp, maxLines = 1) },
            leadingIcon = { Icon(Icons.Default.Link, null, tint = Muted) }, shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) { onAdd(text); text = "" } }),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Accent),
            modifier = Modifier.weight(1f)
        )
        Box(Modifier.padding(start = 6.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(38.dp).padding(8.dp), strokeWidth = 2.dp)
            else IconAction(Icons.Default.Add, "Ekle", bg = Accent, size = 46.dp) { if (text.isNotBlank()) { onAdd(text); text = "" } }
        }
        extra?.let { Box(Modifier.padding(start = 6.dp)) { it() } }
    }
}

/** Üstteki iki sekmeli seçici. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp, 16.dp, 4.dp).clip(RoundedCornerShape(16.dp)).background(Card).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val src = remember { MutableInteractionSource() }
            val focused by src.collectIsFocusedAsState()
            val bg by animateColorAsState(if (i == selected) Accent else if (focused) Color(0xFF262A33) else Color.Transparent, label = "seg")
            Text(label, color = Color.White, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(bg)
                    .clickable(interactionSource = src, indication = null) { onSelect(i) }.padding(vertical = 10.dp)
                    .then(Modifier), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** Başlık + sağda ikon düğmeleri. */
@Composable
private fun SectionHeader(title: String, count: String? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(20.dp, 18.dp, 14.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        count?.let { Text(it, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp)) }
        Box(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
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

// ------------------------------------------------------------------ ekran

@Composable
fun ExtensionsScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column {
        Row(Modifier.padding(20.dp, 12.dp, 20.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Extension, null, tint = Accent, modifier = Modifier.size(26.dp))
            Text("Eklentiler", fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 10.dp))
        }
        Segmented(
            listOf("CloudStream · ${PluginManager.installed.size}", "Nuvio · ${NuvioRepoManager.scrapers.count { it.enabled }}"),
            tab
        ) { tab = it }
        if (tab == 0) CloudStreamTab() else NuvioTab()
    }
}

private val statusLabel = mapOf(0 to ("Kapalı" to Red), 2 to ("Yavaş" to Orange), 3 to ("Beta" to Accent))

@Composable
private fun CloudStreamTab() {
    val context = LocalContext.current
    val appScope = (context.applicationContext as StreamBoxApp).scope
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val repoPlugins = remember { mutableStateMapOf<String, List<RepoPlugin>>() }
    val installing = remember { mutableStateMapOf<String, Boolean>() }
    var repoQuery by rememberSaveable { mutableStateOf("") }
    var installedQuery by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var showError by remember { mutableStateOf<String?>(null) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { PluginManager.installLocal(context, uri) }
                .onSuccess { msg = "Dosya kuruldu" }.onFailure { msg = "Kurulamadı: ${it.message}"; AppLog.e("Yerel kurulum", it) }
        }
    }

    suspend fun plugins(r: Repo): List<RepoPlugin> =
        repoPlugins[r.url] ?: runCatching { RepoManager.fetchPlugins(r) }.getOrDefault(emptyList()).also { repoPlugins[r.url] = it }

    fun toggle(r: Repo) {
        val open = expanded[r.url] != true
        expanded[r.url] = open
        if (open) scope.launch { plugins(r) }
    }

    val installed = PluginManager.installed
    val ok = installed.count { PluginManager.results[it.internalName]?.ok == true }
    val bad = installed.count { PluginManager.results[it.internalName]?.ok == false }
    val off = installed.count { !it.enabled }

    LazyColumn(contentPadding = PaddingValues(bottom = BottomSpace)) {
        item {
            AddCard("repo.json, cloudstreamrepo:// ya da kısa kod", busy, extra = {
                IconAction(Icons.Default.FileOpen, "Telefondan .cs3 kur", size = 46.dp) { pickFile.launch(arrayOf("*/*")) }
            }) { input ->
                busy = true
                scope.launch {
                    runCatching { RepoManager.addRepo(context, input) }
                        .onSuccess { msg = "Repo eklendi: ${it.name}"; expanded[it.url] = true; plugins(it) }
                        .onFailure { msg = "Eklenemedi: ${it.message}"; AppLog.e("Repo ekleme", it) }
                    busy = false
                }
            }
            msg?.let { Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(22.dp, 2.dp)) }

            // Durum özeti
            Row(
                Modifier.fillMaxWidth().padding(16.dp, 10.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF1B2540), Card))).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Stat("${installed.size}", "Kurulu", Color.White)
                Stat("$ok", "Çalışıyor", Green)
                Stat("$bad", "Hatalı", Red)
                Stat("$off", "Kapalı", Muted)
                if (PluginManager.needsRestart) IconAction(Icons.Default.RestartAlt, "Yeniden başlat", bg = Accent, size = 44.dp) { restartApp(context) }
            }
            if (PluginManager.needsRestart) Text("Değişikliklerin tam uygulanması için yeniden başlat.", color = Orange, fontSize = 12.sp,
                modifier = Modifier.padding(22.dp, 0.dp))

            // Toplu kurulum ilerlemesi
            PluginManager.bulk?.let { b ->
                Column(Modifier.fillMaxWidth().padding(16.dp, 10.dp).clip(RoundedCornerShape(16.dp)).background(CardHi).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, null, tint = Accent)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text("Kuruluyor ${b.done + 1} / ${b.total}", fontWeight = FontWeight.SemiBold)
                            Text(b.current, color = Muted, fontSize = 12.sp, maxLines = 1)
                        }
                        IconAction(Icons.Default.Close, "Durdur", size = 34.dp) { PluginManager.cancelBulk() }
                    }
                    LinearProgressIndicator(progress = { (b.done + 1f) / b.total }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(5.dp).clip(RoundedCornerShape(3.dp)))
                }
            }
        }

        item { SectionHeader("Repolar", "${RepoManager.repos.size}") }
        if (RepoManager.repos.isEmpty()) item { Message("Henüz repo yok. Yukarıya bir CloudStream repo adresi yapıştır.") }
        RepoManager.repos.forEach { repo ->
            val open = expanded[repo.url] == true
            val list = repoPlugins[repo.url]
            item(key = "repo-" + repo.url) {
                val fromRepo = installed.filter { it.repoUrl == repo.url }
                Column(Modifier.fillMaxWidth().padding(16.dp, 5.dp).clip(RoundedCornerShape(18.dp)).background(Card)) {
                    Row(Modifier.fillMaxWidth().clickable { toggle(repo) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Logo(repo.iconUrl, repo.name, 48.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(repo.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(repo.description.ifBlank { repo.url }, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Badge("${fromRepo.size} kurulu", Green)
                                list?.let { Badge("${it.size} eklenti", Accent) }
                            }
                        }
                        Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Muted)
                    }
                    // İkon düğmeleri: hepsini kur, hepsini kaldır, yenile, repoyu sil
                    Row(Modifier.fillMaxWidth().padding(14.dp, 0.dp, 14.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconAction(Icons.Default.CloudDownload, "Hepsini kur", tint = Accent) {
                            scope.launch {
                                val all = plugins(repo)
                                PluginManager.installAll(context, appScope, all)
                                msg = "${repo.name}: toplu kurulum başladı"
                            }
                        }
                        IconAction(Icons.Default.DeleteSweep, "Hepsini kaldır", tint = Red) {
                            confirm = "${repo.name} reposundan kurulu ${fromRepo.size} eklenti kaldırılsın mı?" to {
                                PluginManager.uninstallAll(context, fromRepo)
                            }
                        }
                        IconAction(Icons.Default.Refresh, "Listeyi yenile") {
                            repoPlugins.remove(repo.url); expanded[repo.url] = true; scope.launch { plugins(repo) }
                        }
                        Box(Modifier.weight(1f))
                        IconAction(Icons.Default.Delete, "Repoyu sil", tint = Red) {
                            confirm = "${repo.name} reposu silinsin mi? (Kurulu eklentiler kalır)" to { RepoManager.removeRepo(context, repo) }
                        }
                    }
                }
            }
            if (open) {
                if (list == null) item { Loading() }
                else {
                    item { SearchField(repoQuery, "Bu repoda ara…") { repoQuery = it } }
                    val shown = list.filter { repoQuery.isBlank() || it.name.contains(repoQuery, true) }
                    items(shown) { p -> RepoPluginRow(p, installing[p.internalName] == true) { action ->
                        when (action) {
                            "install" -> {
                                installing[p.internalName] = true
                                scope.launch {
                                    runCatching { PluginManager.install(context, p) }
                                        .onFailure { msg = "${p.name} kurulamadı: ${it.message}"; AppLog.e("Kurulum", it) }
                                    installing[p.internalName] = false
                                }
                            }
                            "remove" -> PluginManager.find(p.internalName)?.let { PluginManager.uninstall(context, it) }
                        }
                    } }
                    if (shown.isEmpty()) item { Message("Eşleşen eklenti yok") }
                }
            }
        }

        item {
            SectionHeader("Kurulu", "${installed.size}") {
                if (installed.isNotEmpty()) IconAction(Icons.Default.DeleteSweep, "Hepsini kaldır", tint = Red) {
                    confirm = "Kurulu ${installed.size} eklentinin hepsi kaldırılsın mı?" to { PluginManager.uninstallAll(context, installed.toList()) }
                }
            }
            if (installed.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val labels = listOf("Tümü ${installed.size}", "Çalışan $ok", "Hatalı $bad", "Kapalı $off")
                    items(labels.size) { i -> FilterPill(labels[i], filter == i) { filter = i } }
                }
                SearchField(installedQuery, "Kurulu eklentilerde ara…") { installedQuery = it }
            }
        }
        val shownInstalled = installed.filter { p ->
            val r = PluginManager.results[p.internalName]
            val passFilter = when (filter) { 1 -> r?.ok == true; 2 -> r?.ok == false; 3 -> !p.enabled; else -> true }
            passFilter && (installedQuery.isBlank() || p.name.contains(installedQuery, true))
        }
        items(shownInstalled, key = { "inst-" + it.internalName }) { p ->
            InstalledRow(p, onError = { showError = it }) { PluginManager.uninstall(context, p) }
        }
        if (installed.isEmpty()) item { Message("Henüz eklenti kurulmadı.") }
    }

    confirm?.let { (text, action) -> Confirm("Emin misin?", text, "Kaldır", action) { confirm = null } }
    showError?.let { err ->
        AlertDialog(onDismissRequest = { showError = null }, containerColor = Color(0xFF1A1D24),
            title = { Text("Yükleme hatası") }, text = { Text(err, color = Color(0xFFC8CBD0), fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { showError = null }) { Text("Tamam") } })
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(value: String, label: String, color: Color) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun RepoPluginRow(p: RepoPlugin, installing: Boolean, onAction: (String) -> Unit) {
    val inst = PluginManager.find(p.internalName)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(CardHi).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Logo(p.iconUrl, p.name, 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Badge("v${p.version}", Muted)
                p.language?.let { Badge(it.uppercase(), Accent) }
                statusLabel[p.status]?.let { (t, c) -> Badge(t, c) }
                p.tvTypes.firstOrNull()?.let { Badge(it, Color(0xFFB28CFF)) }
            }
            p.description?.let { Text(it, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp)) }
        }
        when {
            installing -> CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 2.dp)
            inst == null -> IconAction(Icons.Default.Download, "Kur", tint = Color.White, bg = Accent) { onAction("install") }
            inst.version < p.version -> IconAction(Icons.Default.Upgrade, "Güncelle", tint = Color.White, bg = Orange) { onAction("install") }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, "Kurulu", tint = Green, modifier = Modifier.padding(end = 6.dp))
                IconAction(Icons.Default.Delete, "Kaldır", tint = Red, size = 34.dp) { onAction("remove") }
            }
        }
    }
}

@Composable
private fun InstalledRow(p: InstalledPlugin, onError: (String) -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val r = PluginManager.results[p.internalName]
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(Card)
            .clickable(enabled = r?.ok == false) { r?.let { onError(it.message) } }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Logo(p.iconUrl, p.name, 42.dp)
            val dot = when { !p.enabled -> Muted; r == null -> Orange; r.ok -> Green; else -> Red }
            Box(Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape).background(Card).padding(2.dp).clip(CircleShape).background(dot))
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Badge("v${p.version}", Muted)
                when {
                    !p.enabled -> Badge("Kapalı", Muted)
                    r == null -> Badge("Bekliyor", Orange)
                    r.ok -> Text(r.providers.joinToString(", ").ifBlank { "Yüklendi" }, color = Green, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null, tint = Red, modifier = Modifier.size(14.dp))
                        Text(" Hata · ayrıntı için dokun", color = Red, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
        Switch(checked = p.enabled, onCheckedChange = { PluginManager.setEnabled(context, p, it) },
            colors = SwitchDefaults.colors(checkedTrackColor = Accent))
        Box(Modifier.padding(start = 8.dp)) { IconAction(Icons.Default.Delete, "Kaldır", tint = Red, size = 34.dp, onClick = onRemove) }
    }
}

@Composable
private fun NuvioTab() {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    LazyColumn(contentPadding = PaddingValues(bottom = BottomSpace)) {
        item {
            AddCard("manifest.json adresi ya da kullanıcı/repo", busy) { input ->
                busy = true
                scope.launch {
                    runCatching { NuvioRepoManager.addRepo(input) }
                        .onSuccess { msg = "Eklendi: ${it.name}" }
                        .onFailure { msg = "Eklenemedi: ${it.message}"; AppLog.e("Nuvio repo", it) }
                    busy = false
                }
            }
            Text("Nuvio eklentileri TMDB ile çalışır: Ana sayfada Keşfet'ten bir film ya da dizi aç → \"Yayınları bul\".",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(22.dp, 2.dp))
            msg?.let { Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(22.dp, 6.dp)) }
        }
        item {
            SectionHeader("Repolar", "${NuvioRepoManager.repos.size}") {
                if (NuvioRepoManager.scrapers.isNotEmpty()) {
                    IconAction(Icons.Default.ToggleOn, "Hepsini aç", tint = Green) { NuvioRepoManager.setAllEnabled(null, true) }
                    IconAction(Icons.Default.ToggleOff, "Hepsini kapat", tint = Muted) { NuvioRepoManager.setAllEnabled(null, false) }
                }
            }
        }
        if (NuvioRepoManager.repos.isEmpty()) item { Message("Henüz Nuvio reposu yok.") }
        items(NuvioRepoManager.repos, key = { "nr-" + it.url }) { repo ->
            val list = NuvioRepoManager.scrapers.filter { it.repoUrl == repo.url }
            val on = list.count { it.enabled }
            Column(Modifier.fillMaxWidth().padding(16.dp, 5.dp).clip(RoundedCornerShape(18.dp)).background(Card).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Logo(list.firstOrNull { it.logo != null }?.logo, repo.name, 46.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(repo.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(repo.author.ifBlank { repo.url }, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Badge("$on / ${list.size} açık", if (on > 0) Green else Muted)
                        }
                    }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconAction(Icons.Default.ToggleOn, "Hepsini aç", tint = Green) { NuvioRepoManager.setAllEnabled(repo.url, true) }
                    IconAction(Icons.Default.ToggleOff, "Hepsini kapat", tint = Muted) { NuvioRepoManager.setAllEnabled(repo.url, false) }
                    IconAction(Icons.Default.Refresh, "Yenile") {
                        scope.launch {
                            runCatching { NuvioRepoManager.addRepo(repo.url) }
                                .onSuccess { msg = "Güncellendi: ${it.name}" }.onFailure { msg = "Güncellenemedi: ${it.message}" }
                        }
                    }
                    Box(Modifier.weight(1f))
                    IconAction(Icons.Default.Delete, "Repoyu sil", tint = Red) {
                        confirm = "${repo.name} reposu ve ${list.size} eklentisi silinsin mi?" to { NuvioRepoManager.removeRepo(repo) }
                    }
                }
            }
        }
        if (NuvioRepoManager.scrapers.isNotEmpty()) item {
            SectionHeader("Eklentiler", "${NuvioRepoManager.scrapers.count { it.enabled }} / ${NuvioRepoManager.scrapers.size} açık")
            SearchField(query, "Eklenti ara…") { query = it }
        }
        items(NuvioRepoManager.scrapers.filter { query.isBlank() || it.name.contains(query, true) }) { s ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(Card).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Logo(s.logo, s.name, 40.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(s.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (s.version.isNotBlank()) Badge("v${s.version}", Muted)
                        s.language?.takeIf { it.isNotBlank() }?.let { Badge(it.uppercase().take(12), Accent) }
                        if ("movie" in s.supportedTypes) Badge("Film", Color(0xFFB28CFF))
                        if ("tv" in s.supportedTypes) Badge("Dizi", Color(0xFF5AC8FA))
                    }
                }
                Switch(checked = s.enabled, onCheckedChange = { NuvioRepoManager.setEnabled(s, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent))
            }
        }
    }
    confirm?.let { (text, action) -> Confirm("Emin misin?", text, "Sil", action) { confirm = null } }
}
