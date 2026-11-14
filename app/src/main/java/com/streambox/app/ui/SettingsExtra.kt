package com.streambox.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.streambox.app.Backup
import com.streambox.app.Profiles
import com.streambox.app.StreamBoxApp
import com.streambox.app.remote.Peer
import com.streambox.app.remote.RemoteServer
import com.streambox.app.remote.Sync
import com.streambox.app.restartApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Metni QR koda çevirir. */
fun qrBitmap(text: String, size: Int = 640): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val px = IntArray(size * size) { i -> if (m[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (focused) Color(0xFF222B3D) else Color.Transparent)
            .clickable(interactionSource = src, indication = LocalIndication.current) { onChange(!checked) }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, color = Muted, fontSize = 12.sp) }
        }
        Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
    }
}

// ------------------------------------------------------------------ Telefon kumandası (QR)

@Composable
fun RemotePage() {
    val context = LocalContext.current
    val url = remember(RemoteServer.running, RemoteServer.pin, RemoteServer.port) { RemoteServer.url(withPin = true) }
    SubPage("Telefon kumandası", "Telefonun kamerasıyla QR kodu okut") {
        Block("Kumanda sunucusu", "Telefon ve bu cihaz aynı Wi-Fi ağında olmalı. Telefonda uygulama gerekmez; tarayıcıda açılır.") {
            SwitchRow("Telefondan kontrol", if (RemoteServer.running) "Açık · port ${RemoteServer.port}" else "Kapalı",
                RemoteServer.enabled) { RemoteServer.setEnabledState(it) }
        }
        if (RemoteServer.running) {
            var full by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { while (true) { full = com.streambox.app.remote.RemoteAccessibilityService.isEnabled(context); kotlinx.coroutines.delay(1000) } }
            Block("Tüm cihazı kontrol et",
                "Uygulama öndeyken yön tuşları her zaman çalışır. TV ana ekranında ve diğer uygulamalarda da yön tuşları, OK, geri ve ana ekran " +
                "çalışsın istersen erişilebilirlik ayarlarından \"DarknesBox telefon kumandası\" hizmetini aç.") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (full) Icons.Default.CheckCircle else Icons.Default.Info, null, tint = if (full) Color(0xFF34C759) else Color(0xFFFF9F0A))
                    Text(if (full) "Açık: tüm cihaz kontrol edilebilir" else "Kapalı: yalnızca DarknesBox içinde",
                        modifier = Modifier.padding(start = 10.dp).weight(1f), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { toast(context, "Erişilebilirlik ayarları açılamadı") }
                }) { Icon(Icons.Default.Accessibility, null); Text(if (full) "Erişilebilirlik ayarları" else "Erişilebilirlikte aç", Modifier.padding(start = 6.dp)) }
            }
            Block("QR kod ile bağlan") {
                if (url == null) Text("Wi-Fi bağlantısı bulunamadı.", color = Color(0xFFFF9F0A))
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    val bmp = remember(url) { qrBitmap(url).asImageBitmap() }
                    Image(bmp, "QR", Modifier.size(200.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(8.dp))
                    Column(Modifier.padding(start = 20.dp).weight(1f)) {
                        Text("1. Telefonun kamerasını aç", color = Color(0xFFC8CBD0), fontSize = 14.sp)
                        Text("2. QR kodu okut, bağlantıya dokun", color = Color(0xFFC8CBD0), fontSize = 14.sp)
                        Text("3. Kumanda tarayıcıda açılır", color = Color(0xFFC8CBD0), fontSize = 14.sp)
                        Spacer(Modifier.height(14.dp))
                        Text("Adres", color = Muted, fontSize = 12.sp)
                        Text(RemoteServer.url() ?: "", fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text("PIN", color = Muted, fontSize = 12.sp)
                        Text(RemoteServer.pin.chunked(3).joinToString(" "), fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Accent)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { RemoteServer.regeneratePin(); toast(context, "Yeni PIN oluşturuldu; eski bağlantılar kapanır") }) {
                    Icon(Icons.Default.Refresh, null); Text("PIN'i yenile", Modifier.padding(start = 6.dp))
                }
            }
            Block("Telefondan neler yapılır?") {
                listOf(
                    Icons.Default.SettingsRemote to "Yön tuşları, OK, geri, ana ekran, ses ve sessiz",
                    Icons.Default.PlayCircle to "Oynat/duraklat, ±10 sn, ilerleme çubuğu, önceki/sonraki kanal",
                    Icons.Default.Search to "Film/dizi ara, TV'de detayını aç; kanal ara ve oynat",
                    Icons.Default.LiveTv to "IPTV hesabı ekle (M3U / Xtream) ya da sil",
                    Icons.Default.Backup to "Yedeği telefona indir, telefondan geri yükle",
                ).forEach { (ic, t) ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(ic, null, tint = Accent, modifier = Modifier.size(20.dp))
                        Text(t, color = Color(0xFFC8CBD0), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Yedekleme

@Composable
fun BackupPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<JSONObject?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val data = Backup.export(context).toString(2)
                    context.contentResolver.openOutputStream(uri)!!.use { it.write(data.toByteArray()) }
                }
            }.onSuccess { toast(context, "Yedek kaydedildi") }.onFailure { toast(context, "Kaydedilemedi: ${it.message}") }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { JSONObject(context.contentResolver.openInputStream(uri)!!.bufferedReader().readText()) }
            }.onSuccess { pendingRestore = it }.onFailure { toast(context, "Yedek okunamadı: ${it.message}") }
        }
    }
    SubPage("Yedekleme", "Ayarlar, repolar, eklentiler, IPTV hesapları, profiller") {
        Block("Yedekle", "Tek bir .json dosyası oluşturur. Eklentiler geri yüklenince kaynaklarından yeniden indirilir.") {
            Button(onClick = { create.launch("streambox-yedek-${System.currentTimeMillis() / 1000}.json") }) {
                Icon(Icons.Default.Save, null); Text("Dosyaya yedekle", Modifier.padding(start = 6.dp))
            }
        }
        Block("Geri yükle", "Bu cihazdaki ayarların yerine yedektekiler gelir ve uygulama yeniden başlar.") {
            OutlinedButton(onClick = { open.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                Icon(Icons.Default.Restore, null); Text("Dosyadan geri yükle", Modifier.padding(start = 6.dp))
            }
        }
        Block("Diğer yollar") {
            SettingRow(Icons.Default.QrCode2, Accent, "Telefondan yedekle", "Telefon kumandasındaki Yedek sekmesi") { Nav.go(Route.SettingsPage("remote")) }
            SettingRow(Icons.Default.Wifi, Color(0xFF34C759), "Wi-Fi senkronizasyon", "Başka bir DarknesBox'a gönder / ondan al") { Nav.go(Route.SettingsPage("sync")) }
        }
    }
    pendingRestore?.let { data ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Yedek geri yüklensin mi?") },
            text = { com.streambox.app.remote.RemoteNav.Hook();  Text("${data.optString("device")} · ${Backup.summary(data)}\nMevcut ayarların yerine geçecek.") },
            confirmButton = {
                Button(onClick = {
                    runCatching { Backup.restore(context, data) }
                        .onSuccess { restartApp(context) }.onFailure { toast(context, "Geri yüklenemedi: ${it.message}") }
                    pendingRestore = null
                }) { Text("Geri yükle") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Vazgeç") } },
        )
    }
}

// ------------------------------------------------------------------ Wi-Fi senkronizasyon

@Composable
fun SyncPage() {
    val context = LocalContext.current
    var target by remember { mutableStateOf<Peer?>(null) }
    var manual by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        Sync.startDiscovery(context)
        onDispose { Sync.stopDiscovery() }
    }
    SubPage("Wi-Fi senkronizasyon", "Aynı ağdaki DarknesBox cihazları arasında") {
        Block("Bu cihaz", "Diğer cihazdan bağlanırken bu PIN sorulur.") {
            if (!RemoteServer.running) {
                Text("Senkron için \"Telefon kumandası\" açık olmalı.", color = Color(0xFFFF9F0A), fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { RemoteServer.setEnabledState(true) }) { Text("Aç") }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Devices, null, tint = Accent)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(Backup.deviceName(context), fontWeight = FontWeight.SemiBold)
                    Text("${RemoteServer.localIp() ?: "?"}:${RemoteServer.port}", color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Text(RemoteServer.pin.chunked(3).joinToString(" "), fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Accent)
            }
        }
        Block("Ağdaki cihazlar", "Diğer cihazda da DarknesBox açık ve kumanda etkin olmalı.") {
            if (Sync.peers.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Aranıyor…", color = Muted, modifier = Modifier.padding(start = 10.dp))
            }
            Sync.peers.forEach { p ->
                SettingRow(Icons.Default.Tv, Color(0xFF34C759), p.name, p.address) { target = p }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { manual = true }) { Icon(Icons.Default.Edit, null); Text("Adresi elle gir", Modifier.padding(start = 6.dp)) }
        }
    }
    if (manual) {
        var addr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manual = false },
            title = { Text("Cihaz adresi") },
            text = { com.streambox.app.remote.RemoteNav.Hook();  OutlinedTextField(addr, { addr = it.trim() }, label = { Text("ör. 192.168.1.20:8686") }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    val a = if (addr.contains(':')) addr else "$addr:${RemoteServer.PORT}"
                    target = Peer(a.substringBefore(':'), a.substringBefore(':'), a.substringAfter(':').toIntOrNull() ?: RemoteServer.PORT)
                    manual = false
                }, enabled = addr.isNotBlank()) { Text("Devam") }
            },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("Vazgeç") } },
        )
    }
    target?.let { SyncDialog(it) { target = null } }
}

@Composable
private fun SyncDialog(peer: Peer, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (busy == null) onClose() },
        title = { Text(peer.name) },
        text = { com.streambox.app.remote.RemoteNav.Hook(); 
            Column {
                Text("Diğer cihazın PIN'ini gir (Ayarlar › Wi-Fi senkronizasyon ya da Telefon kumandası).", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(6) }, label = { Text("PIN") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                busy?.let { Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text(it, Modifier.padding(start = 10.dp))
                } }
                error?.let { Text(it, color = Color(0xFFFF453A), modifier = Modifier.padding(top = 10.dp)) }
                Spacer(Modifier.height(14.dp))
                Text("• Ondan al: o cihazdaki repolar, eklentiler, IPTV hesapları, profiller ve ayarlar buraya gelir (buradakilerin yerine).\n" +
                    "• Ona gönder: buradakiler o cihaza gider; o cihaz yeniden başlar.", color = Color(0xFFA5AAB2), fontSize = 12.sp)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = pin.length == 6 && busy == null, onClick = {
                    busy = "Gönderiliyor…"; error = null
                    scope.launch {
                        runCatching { Sync.push(context, peer.address, pin) }
                            .onSuccess { toast(context, "Gönderildi; ${peer.name} yeniden başlıyor"); onClose() }
                            .onFailure { error = it.message }
                        busy = null
                    }
                }) { Icon(Icons.Default.Upload, null); Text("Ona gönder", Modifier.padding(start = 4.dp)) }
                Button(enabled = pin.length == 6 && busy == null, onClick = {
                    busy = "Alınıyor…"; error = null
                    scope.launch {
                        runCatching { Sync.pull(context, peer.address, pin) }
                            .onSuccess { toast(context, "Alındı: $it · yeniden başlatılıyor"); restartApp(context) }
                            .onFailure { error = it.message }
                        busy = null
                    }
                }) { Icon(Icons.Default.Download, null); Text("Ondan al", Modifier.padding(start = 4.dp)) }
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = busy == null) { Text("Kapat") } },
    )
}

// ------------------------------------------------------------------ Profiller

@Composable
fun ProfilesPage() {
    var editing by remember { mutableStateOf<com.streambox.app.Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    SubPage("Profiller", "${Profiles.list.size} / ${Profiles.MAX} profil") {
        Block("Profilli giriş", "Açıkken uygulama her açılışta \"Kim izliyor?\" ekranıyla başlar. Her profilin kendi \"Kaldığın yerden izle\" listesi vardır.") {
            SwitchRow("Açılışta profil seç", if (Profiles.askOnStart) "Açık" else "Kapalı", Profiles.askOnStart) { Profiles.updateAskOnStart(it) }
        }
        Block("Profiller", "Seçmek için dokun; düzenlemek için kalem simgesine dokun.") {
            Profiles.list.forEach { p ->
                val sel = p.id == Profiles.currentId
                val src = remember { MutableInteractionSource() }
                val focused by src.collectIsFocusedAsState()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (focused) Color(0xFF222B3D) else if (sel) Color(0xFF1B2335) else Color.Transparent)
                        .clickable(interactionSource = src, indication = LocalIndication.current) { Profiles.select(p) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileAvatar(p, 48.dp, 12.dp)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        if (sel) Text("Şu an kullanılan", color = Accent, fontSize = 12.sp)
                    }
                    IconButton(onClick = { editing = p }) { Icon(Icons.Default.Edit, "Düzenle", tint = Muted) }
                }
            }
            if (Profiles.list.size < Profiles.MAX) TextButton(onClick = { creating = true }, modifier = Modifier.padding(top = 6.dp)) {
                Icon(Icons.Default.PersonAdd, null); Text("Profil ekle", Modifier.padding(start = 6.dp))
            }
        }
        Block("Profil değiştir") {
            Button(onClick = { Profiles.chosen = false }) { Icon(Icons.Default.SwitchAccount, null); Text("\"Kim izliyor?\" ekranını aç", Modifier.padding(start = 6.dp)) }
        }
    }
    if (creating) ProfileEditor(null) { creating = false }
    editing?.let { e -> ProfileEditor(e) { editing = null } }
}

