package com.streambox.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streambox.app.AppLog
import com.streambox.app.nuvio.NuvioRepoManager
import com.streambox.app.plugins.PluginManager
import com.streambox.app.plugins.Repo
import com.streambox.app.plugins.RepoManager
import com.streambox.app.plugins.RepoPlugin
import com.streambox.app.restartApp
import kotlinx.coroutines.launch

@Composable
private fun Item(title: String, subtitle: String?, icon: String? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(8.dp))
            .background(Panel).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) AsyncImage(icon, null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f).padding(start = if (icon != null) 10.dp else 0.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

@Composable
private fun AddBar(hint: String, busy: Boolean, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text(hint, fontSize = 13.sp) }, modifier = Modifier.weight(1f))
        Button(onClick = { if (text.isNotBlank()) { onAdd(text); text = "" } }, enabled = !busy) { Text(if (busy) "…" else "Ekle") }
    }
}

@Composable
fun ExtensionsScreen() {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("CloudStream (.cs3)") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Nuvio") })
        }
        if (tab == 0) CloudStreamTab() else NuvioTab()
    }
}

private val statusText = mapOf(0 to "Kapalı", 1 to "", 2 to "Yavaş", 3 to "Beta")

@Composable
private fun CloudStreamTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var openRepo by remember { mutableStateOf<Repo?>(null) }
    val repoPlugins = remember { mutableStateMapOf<String, List<RepoPlugin>>() }
    val installing = remember { mutableStateMapOf<String, Boolean>() }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { PluginManager.installLocal(context, uri) }
                .onSuccess { msg = "Dosya kuruldu" }.onFailure { msg = "Kurulamadı: ${it.message}"; AppLog.e("Yerel kurulum", it) }
        }
    }

    fun loadRepo(r: Repo) {
        openRepo = if (openRepo == r) null else r
        if (openRepo != null && repoPlugins[r.url] == null) scope.launch {
            repoPlugins[r.url] = runCatching { RepoManager.fetchPlugins(r) }.getOrDefault(emptyList())
        }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            AddBar("repo.json adresi, cloudstreamrepo:// ya da kısa kod", busy) { input ->
                busy = true
                scope.launch {
                    runCatching { RepoManager.addRepo(context, input) }
                        .onSuccess { msg = "Repo eklendi: ${it.name}"; loadRepo(it) }
                        .onFailure { msg = "Eklenemedi: ${it.message}"; AppLog.e("Repo ekleme", it) }
                    busy = false
                }
            }
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickFile.launch(arrayOf("*/*")) }) { Text("Telefondan .cs3 kur") }
                if (PluginManager.needsRestart) Button(onClick = { restartApp(context) }) { Text("Yeniden başlat") }
            }
            msg?.let { Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(14.dp, 8.dp)) }
        }

        item { SectionTitle("Repolar") }
        if (RepoManager.repos.isEmpty()) item { Message("Henüz repo yok. Yukarıya bir CloudStream repo adresi yapıştır.") }
        RepoManager.repos.forEach { repo ->
            item(key = "repo-" + repo.url) {
                Item(repo.name, repo.description.ifBlank { repo.url }, repo.iconUrl) {
                    TextButton(onClick = { loadRepo(repo) }) { Text(if (openRepo == repo) "Kapat" else "Eklentiler") }
                    TextButton(onClick = { RepoManager.removeRepo(context, repo) }) { Text("Sil", color = Color(0xFFFF6B6B)) }
                }
            }
            if (openRepo == repo) {
                val list = repoPlugins[repo.url]
                if (list == null) item { Loading() }
                else if (list.isEmpty()) item { Message("Bu repoda eklenti bulunamadı") }
                else items(list) { p ->
                    val inst = PluginManager.find(p.internalName)
                    val sub = listOfNotNull(
                        "v${p.version}", p.language, p.tvTypes.take(3).joinToString(", ").ifBlank { null },
                        statusText[p.status]?.ifBlank { null }, p.description
                    ).joinToString(" · ")
                    Item(p.name, sub, p.iconUrl) {
                        when {
                            installing[p.internalName] == true -> Text("…")
                            inst == null -> Button(onClick = {
                                installing[p.internalName] = true
                                scope.launch {
                                    runCatching { PluginManager.install(context, p) }
                                        .onFailure { msg = "${p.name} kurulamadı: ${it.message}"; AppLog.e("Kurulum", it) }
                                    installing[p.internalName] = false
                                }
                            }) { Text("Kur") }
                            inst.version < p.version -> OutlinedButton(onClick = {
                                installing[p.internalName] = true
                                scope.launch {
                                    runCatching { PluginManager.install(context, p) }
                                    installing[p.internalName] = false
                                }
                            }) { Text("Güncelle") }
                            else -> Text("Kurulu", color = Accent, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        item { SectionTitle("Kurulu eklentiler (${PluginManager.installed.size})") }
        items(PluginManager.installed, key = { "inst-" + it.internalName }) { p ->
            val r = PluginManager.results[p.internalName]
            val status = when {
                !p.enabled -> "Devre dışı"
                r == null -> "Bekliyor"
                r.ok -> "✔ ${r.message}"
                else -> "✘ ${r.message}"
            }
            Item(p.name, "v${p.version} · $status", null) {
                Switch(checked = p.enabled, onCheckedChange = { PluginManager.setEnabled(context, p, it) })
                TextButton(onClick = { PluginManager.uninstall(context, p) }) { Text("Sil", color = Color(0xFFFF6B6B)) }
            }
        }
    }
}

@Composable
private fun NuvioTab() {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            AddBar("manifest.json adresi ya da kullanıcı/repo", busy) { input ->
                busy = true
                scope.launch {
                    runCatching { NuvioRepoManager.addRepo(input) }
                        .onSuccess { msg = "Eklendi: ${it.name}" }
                        .onFailure { msg = "Eklenemedi: ${it.message}"; AppLog.e("Nuvio repo", it) }
                    busy = false
                }
            }
            Text(
                "Nuvio eklentileri TMDB kimliğiyle çalışır: Ana sayfadaki Keşfet afişlerinden bir film/dizi açıp \"Yayınları bul\"a bas.",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp)
            )
            msg?.let { Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(14.dp, 8.dp)) }
        }
        item { SectionTitle("Repolar") }
        if (NuvioRepoManager.repos.isEmpty()) item { Message("Henüz Nuvio reposu yok.") }
        items(NuvioRepoManager.repos, key = { "nr-" + it.url }) { repo ->
            val count = NuvioRepoManager.scrapers.count { it.repoUrl == repo.url }
            Item(repo.name, "$count eklenti · ${repo.author.ifBlank { repo.url }}") {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { NuvioRepoManager.addRepo(repo.url) }
                            .onSuccess { msg = "Güncellendi: ${it.name}" }.onFailure { msg = "Güncellenemedi: ${it.message}" }
                    }
                }) { Text("Yenile") }
                TextButton(onClick = { NuvioRepoManager.removeRepo(repo) }) { Text("Sil", color = Color(0xFFFF6B6B)) }
            }
        }
        item { SectionTitle("Eklentiler (${NuvioRepoManager.scrapers.count { it.enabled }} etkin)") }
        items(NuvioRepoManager.scrapers) { s ->
            val sub = listOfNotNull(
                s.version.ifBlank { null }?.let { "v$it" }, s.language,
                s.supportedTypes.joinToString(", ") { if (it == "tv") "dizi" else if (it == "movie") "film" else it },
                s.description.ifBlank { null }
            ).joinToString(" · ")
            Item(s.name, sub, s.logo) {
                Switch(checked = s.enabled, onCheckedChange = { NuvioRepoManager.setEnabled(s, it) })
            }
        }
    }
}
