package com.streambox.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streambox.app.AppLog
import com.streambox.app.BuildConfig
import com.streambox.app.DnsProvider
import com.streambox.app.Settings
import com.streambox.app.Tmdb
import com.streambox.app.iptv.IptvStore
import com.streambox.app.nuvio.NuvioRepoManager
import com.streambox.app.plugins.PluginManager
import com.streambox.app.restartApp
import kotlinx.coroutines.launch

private val SCard = Color(0xFF171A21)
private val SLine = Color(0xFF22262E)

// ------------------------------------------------------------------ ortak parçalar

/** Ayar satırı: renkli ikon kutusu, başlık, açıklama, sağda ok. Kumandayla seçilebilir. */
@Composable
internal fun SettingRow(icon: ImageVector, tint: Color, title: String, subtitle: String?, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Row(
        Modifier.fillMaxWidth()
            .background(if (focused) Color(0xFF222B3D) else Color.Transparent)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 13.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted)
    }
}

/** Başlıklı, köşeleri yuvarlak ayar grubu. */
@Composable
internal fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Text(title.uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(24.dp, 22.dp, 24.dp, 8.dp))
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(SCard)) { content() }
}

/** Satırlar arasındaki ince çizgi. */
@Composable
internal fun GroupDivider() = Box(Modifier.fillMaxWidth().padding(start = 70.dp).height(1.dp).background(SLine))

/** Alt sayfa iskeleti: geri düğmesi + başlık + içerik. */
@Composable
internal fun SubPage(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomSpace)) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp, 10.dp, 16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).clickable { Nav.back() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White)
                }
                Column(Modifier.padding(start = 6.dp)) {
                    Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    subtitle?.let { Text(it, color = Muted, fontSize = 13.sp) }
                }
            }
        }
        item { Column(Modifier.fillMaxWidth().widthIn(max = 900.dp)) { content() } }
    }
}

/** Kart içinde başlıklı blok. */
@Composable
internal fun Block(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp).clip(RoundedCornerShape(20.dp)).background(SCard).padding(18.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        hint?.let { Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** Seçenek çipi. */
@Composable
internal fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Color(0xFFC8CBD0),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) Accent else Color(0xFF23272F))
            .border(1.5.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(50))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp))
}

/** Radyo düğmeli seçenek satırı. */
@Composable
internal fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color(0xFF222B3D) else if (selected) Color(0xFF1B2335) else Color.Transparent)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Accent))
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            subtitle?.let { Text(it, color = Muted, fontSize = 12.sp) }
        }
    }
}

internal fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

private val langNames = linkedMapOf(
    "" to "Kapalı", "tr" to "Türkçe", "en" to "İngilizce", "de" to "Almanca", "fr" to "Fransızca",
    "es" to "İspanyolca", "ar" to "Arapça", "ja" to "Japonca", "ko" to "Korece", "ru" to "Rusça",
)

private val resizeNames = linkedMapOf(0 to "Sığdır", 4 to "Yakınlaştır", 3 to "Uzat")
private val uaNames = linkedMapOf(
    "AUTO" to ("Otomatik (önerilen)" to "Önce ExoPlayer; sunucu reddederse sırayla diğerlerini dener"),
    "EXO" to ("ExoPlayer" to "Çoğu IPTV uygulamasının kullandığı kimlik"),
    "VLC" to ("VLC" to "Bazı sunucular ister, bazıları engeller"),
    "BROWSER" to ("Tarayıcı" to "Masaüstü Chrome gibi görünür"),
    "OKHTTP" to ("Uygulama" to "Sade okhttp kimliği"),
)

// ------------------------------------------------------------------ ana ayarlar

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val errors = AppLog.lines.count { it.contains("  E  ") || it.contains(" E  ") }
    val dnsLabel = runCatching { DnsProvider.valueOf(Settings.dns).label }.getOrDefault("Kapalı")

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomSpace)) {
        item {
            Column(Modifier.padding(24.dp, 18.dp, 24.dp, 4.dp)) {
                Text("Ayarlar", fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text("DarknesBox ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 13.sp)
            }
        }
        item {
            // Etkin profil kartı
            val prof = com.streambox.app.Profiles.current
            val src = remember { MutableInteractionSource() }
            val focused by src.collectIsFocusedAsState()
            Row(
                Modifier.fillMaxWidth().padding(16.dp, 14.dp, 16.dp, 0.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (focused) Color(0xFF222B3D) else SCard)
                    .clickable(interactionSource = src, indication = LocalIndication.current) { Nav.go(Route.SettingsPage("profiles")) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileAvatar(prof, 56.dp, 14.dp)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(prof.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${com.streambox.app.Profiles.list.size} profil · Profilli giriş " + if (com.streambox.app.Profiles.askOnStart) "açık" else "kapalı",
                        color = Muted, fontSize = 13.sp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted)
            }
        }
        item {
            SettingGroup("Cihazlar") {
                SettingRow(Icons.Default.QrCode2, Accent, "Telefon kumandası",
                    if (com.streambox.app.remote.RemoteServer.running) "QR kodla bağlan · PIN ${com.streambox.app.remote.RemoteServer.pin}" else "Kapalı") {
                    Nav.go(Route.SettingsPage("remote"))
                }
                GroupDivider()
                SettingRow(Icons.Default.Wifi, Color(0xFF34C759), "Wi-Fi senkronizasyon", "Aynı ağdaki DarknesBox'larla eşitle") {
                    Nav.go(Route.SettingsPage("sync"))
                }
                GroupDivider()
                SettingRow(Icons.Default.Backup, Color(0xFFFF9F0A), "Yedekleme", "Dosyaya yedekle / geri yükle") {
                    Nav.go(Route.SettingsPage("backup"))
                }
            }
            SettingGroup("İçerik") {
                SettingRow(Icons.Default.Language, Color(0xFF5AC8FA), "Siteler",
                    "${com.streambox.app.sites.SiteManager.sites.size} site · reklamsız tarayıcı") { Nav.go(Route.SettingsPage("sites")) }
                GroupDivider()
                SettingRow(Icons.Default.Movie, Color(0xFF01B4E4), "TMDB ayarları", "Katalog ve afişler · ${Settings.language}") {
                    Nav.go(Route.SettingsPage("tmdb"))
                }
                GroupDivider()
                SettingRow(Icons.Default.Extension, Color(0xFFB28CFF), "Eklentiler",
                    "${PluginManager.installed.size} CloudStream · ${NuvioRepoManager.scrapers.count { it.enabled }} Nuvio") {
                    Nav.tab(Route.Extensions)
                }
            }
            SettingGroup("Bağlantı") {
                SettingRow(Icons.Default.Dns, Color(0xFF34C759), "DNS ayarları", dnsLabel) { Nav.go(Route.SettingsPage("dns")) }
            }
            SettingGroup("Oynatma") {
                SettingRow(Icons.Default.PlayCircle, Accent, "Oynatıcı ayarları",
                    "Altyazı: ${langNames[Settings.subLang] ?: Settings.subLang} · ${resizeNames[Settings.resizeMode] ?: "Sığdır"}") {
                    Nav.go(Route.SettingsPage("player"))
                }
                GroupDivider()
                SettingRow(Icons.Default.LiveTv, Color(0xFFFF9F0A), "IPTV ayarları",
                    "${IptvStore.playlists.size} hesap · Kimlik: ${uaNames[Settings.iptvUa]?.first ?: "Otomatik"}") {
                    Nav.go(Route.SettingsPage("iptv"))
                }
            }
            SettingGroup("Görünüm") {
                SettingRow(Icons.Default.Palette, Color(0xFFB28CFF), "Uygulama ikonu", com.streambox.app.AppIcon.current.label) {
                    Nav.go(Route.SettingsPage("icon"))
                }
            }
            SettingGroup("Uygulama") {
                SettingRow(Icons.AutoMirrored.Filled.List, Color(0xFFFF6B6B), "Günlük",
                    "${AppLog.lines.size} kayıt" + if (errors > 0) " · $errors hata" else "") { Nav.go(Route.SettingsPage("log")) }
                GroupDivider()
                SettingRow(Icons.Default.DeleteSweep, Color(0xFF8E8E93), "Önbelleği temizle", "Ana sayfa rafları ve görseller", trailing = {}) {
                    Nav.homeCache.clear()
                    runCatching { coil.Coil.imageLoader(context).memoryCache?.clear() }
                    toast(context, "Önbellek temizlendi")
                }
                GroupDivider()
                SettingRow(Icons.Default.RestartAlt, Color(0xFF8E8E93), "Yeniden başlat", "Eklentileri yeniden yükler", trailing = {}) {
                    restartApp(context)
                }
                GroupDivider()
                SettingRow(Icons.Default.Info, Color(0xFF8E8E93), "Hakkında", "Sürüm ${BuildConfig.VERSION_NAME}") {
                    Nav.go(Route.SettingsPage("about"))
                }
            }
        }
    }
}

/** Ayar alt sayfaları. */
@Composable
fun SettingsPageScreen(page: String) {
    when (page) {
        "tmdb" -> TmdbPage()
        "dns" -> DnsPage()
        "player" -> PlayerPage()
        "iptv" -> IptvPage()
        "log" -> LogPage()
        "icon" -> IconPage()
        "remote" -> RemotePage()
        "backup" -> BackupPage()
        "sync" -> SyncPage()
        "profiles" -> ProfilesPage()
        "sites" -> SitesPage()
        else -> AboutPage()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TmdbPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(Settings.tmdbKey) }
    var lang by remember { mutableStateOf(Settings.language) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Boolean?>(null) }

    SubPage("TMDB ayarları", "Keşfet kataloğu, afişler ve Nuvio eklentileri") {
        Block("API anahtarı", "themoviedb.org hesabından alınan v3 anahtarı ya da v4 erişim jetonu.") {
            OutlinedTextField(key, { key = it; result = null }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Color.White))
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    Settings.updateTmdbKey(key); key = Settings.tmdbKey; Nav.homeCache.clear()
                    testing = true; result = null
                    scope.launch {
                        result = runCatching { Tmdb.search("matrix") }.isSuccess
                        testing = false
                    }
                }) { Text("Kaydet ve dene") }
                Choice("Varsayılan", false) { key = Settings.DEFAULT_TMDB_KEY; result = null }
                when {
                    testing -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    result == true -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF34C759)); Text(" Çalışıyor", color = Color(0xFF34C759))
                    }
                    result == false -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = Color(0xFFFF6B6B)); Text(" Geçersiz", color = Color(0xFFFF6B6B))
                    }
                }
            }
        }
        Block("Katalog dili", "Başlıklar ve açıklamalar bu dilde gelir.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("tr-TR" to "Türkçe", "en-US" to "English", "de-DE" to "Deutsch", "fr-FR" to "Français",
                    "es-ES" to "Español", "ar-SA" to "العربية", "ru-RU" to "Русский").forEach { (code, label) ->
                    Choice(label, lang == code) { lang = code; Settings.updateLanguage(code); Nav.homeCache.clear(); toast(context, "Dil: $label") }
                }
            }
        }
    }
}

@Composable
private fun DnsPage() {
    val context = LocalContext.current
    SubPage("DNS ayarları", "Engelli sitelere erişim") {
        Block("DNS over HTTPS", "Türkiye'de erişim engeli olan kaynaklar açılmıyorsa bir sağlayıcı seç. Değişiklik hemen uygulanır.") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val desc = mapOf(
                    DnsProvider.NONE to "Operatörün DNS'i (engeller geçerli)",
                    DnsProvider.CLOUDFLARE to "Hızlı, önerilen",
                    DnsProvider.GOOGLE to "Google Public DNS",
                    DnsProvider.ADGUARD to "Filtresiz AdGuard",
                    DnsProvider.QUAD9 to "Güvenlik odaklı",
                )
                DnsProvider.entries.forEach { d ->
                    RadioRow(d.label, desc[d], Settings.dns == d.name) {
                        Settings.updateDns(d.name); Nav.homeCache.clear(); toast(context, "DNS: ${d.label}")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerPage() {
    SubPage("Oynatıcı ayarları") {
        Block("Altyazı dili", "Yayında bu dilde altyazı varsa otomatik açılır.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                langNames.forEach { (code, name) -> Choice(name, Settings.subLang == code) { Settings.updateSubLang(code) } }
            }
        }
        Block("Ses dili", "Birden çok ses varsa önce bu dil seçilir.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                langNames.forEach { (code, name) ->
                    Choice(if (code.isEmpty()) "Otomatik" else name, Settings.audioLang == code) { Settings.updateAudioLang(code) }
                }
            }
        }
        Block("Varsayılan ekran oranı") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                resizeNames.forEach { (mode, name) -> Choice(name, Settings.resizeMode == mode) { Settings.updateResizeMode(mode) } }
            }
        }
    }
}

@Composable
private fun IptvPage() {
    SubPage("IPTV ayarları", "${IptvStore.playlists.size} hesap") {
        Block("Oynatıcı kimliği (User-Agent)", "Kanallar açılmıyor ya da \"desteklenmeyen biçim\" hatası alıyorsan değiştir.") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                uaNames.forEach { (k, v) -> RadioRow(v.first, v.second, Settings.iptvUa == k) { Settings.updateIptvUa(k) } }
            }
        }
        Block("Listeler", "Kayıtlı ${IptvStore.playlists.size} hesap. Eklemek, silmek ya da adını değiştirmek için Darknes IPTV'deki ⋮ menüsünü kullan.") {
            Choice("Darknes IPTV'yi aç", false) { Nav.tab(Route.Iptv) }
        }
    }
}

@Composable
private fun LogPage() {
    val context = LocalContext.current
    var onlyErrors by remember { mutableStateOf(false) }
    val lines = AppLog.lines.filter { !onlyErrors || it.contains(" E  ") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomSpace)) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp, 10.dp, 16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).clickable { Nav.back() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White)
                }
                Text("Günlük", fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 6.dp).weight(1f))
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF23272F)).clickable {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("DarknesBox günlük", AppLog.lines.joinToString("\n")))
                    toast(context, "Günlük kopyalandı")
                }, contentAlignment = Alignment.Center) { Icon(Icons.Default.ContentCopy, "Kopyala", tint = Color.White, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.size(8.dp))
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF23272F)).clickable { AppLog.lines.clear() },
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.DeleteSweep, "Temizle", tint = Color(0xFFFF6B6B), modifier = Modifier.size(20.dp)) }
            }
            Row(Modifier.padding(16.dp, 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Tümü ${AppLog.lines.size}", !onlyErrors) { onlyErrors = false }
                Choice("Hatalar ${AppLog.lines.count { it.contains(" E  ") }}", onlyErrors) { onlyErrors = true }
            }
        }
        if (lines.isEmpty()) item { Message("Kayıt yok") }
        items(lines) { line ->
            val err = line.contains(" E  ")
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                .background(if (err) Color(0x1FFF6B6B) else SCard).padding(10.dp)) {
                Icon(if (err) Icons.Default.Error else Icons.Default.Info, null, tint = if (err) Color(0xFFFF8A8A) else Muted,
                    modifier = Modifier.size(16.dp).padding(top = 1.dp))
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (err) Color(0xFFFFB4B4) else Color(0xFFB8BCC2),
                    modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun AboutPage() {
    SubPage("Hakkında") {
        Block("DarknesBox ${BuildConfig.VERSION_NAME}") {
            Text("CloudStream (.cs3) ve Nuvio eklentileri, TMDB kataloğu ve IPTV listeleri için oynatıcı. " +
                "Uygulama içerik barındırmaz; eklentileri ve listeleri kullanıcı ekler.", color = Color(0xFFC8CBD0), fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Text("Kaynak kodu: github.com/darknesslord19/Dark-repo-player", color = Muted, fontSize = 13.sp)
        }
        Block("Durum") {
            Text("${PluginManager.installed.size} CloudStream eklentisi · ${NuvioRepoManager.scrapers.size} Nuvio eklentisi · " +
                "${IptvStore.playlists.size} IPTV hesabı", color = Color(0xFFC8CBD0), fontSize = 14.sp)
        }
        Block("İkonlar") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.ClosedCaption, null, tint = Muted)
                Text("Sorun bildirmek için Günlük sayfasından kayıtları kopyalayıp gönderebilirsin.", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPage() {
    val context = LocalContext.current
    SubPage("Uygulama ikonu", "Telefonun ana ekranındaki ikon") {
        Block("İkon seç", "Değişiklik birkaç saniye içinde ana ekrana yansır. Bazı başlatıcılarda kısayolu yeniden eklemen gerekebilir.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                com.streambox.app.AppIcon.entries.forEach { icon ->
                    val sel = com.streambox.app.AppIcon.current == icon
                    val src = remember { MutableInteractionSource() }
                    val focused by src.collectIsFocusedAsState()
                    Column(
                        Modifier.clip(RoundedCornerShape(22.dp))
                            .background(if (sel) Color(0xFF1B2335) else Color(0xFF1F232B))
                            .border(2.dp, if (sel || focused) Accent else Color.Transparent, RoundedCornerShape(22.dp))
                            .clickable(interactionSource = src, indication = LocalIndication.current) {
                                com.streambox.app.AppIcon.apply(context, icon); toast(context, "İkon: ${icon.label}")
                            }
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (icon.logo != null) {
                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(icon.logo), icon.label,
                                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)))
                        } else {
                            Box(Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)).background(Accent), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(52.dp))
                            }
                        }
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (sel) Icon(Icons.Default.CheckCircle, null, tint = Accent, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                            Text(icon.label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
