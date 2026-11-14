package com.streambox.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streambox.app.BuildConfig
import com.streambox.app.sites.Site
import com.streambox.app.sites.SiteBrowserActivity
import kotlinx.coroutines.launch

private val PCard = Color(0xFF15181F)
private val PLine = Color(0xFF232731)
private val SiteRed = Color(0xFFE50914)

/** Siteler yönetimi: ekleme, eklenen siteler ve reklam engelleme ayarları. */
@Composable
fun SitesPage() {
    val context = LocalContext.current
    val sm = com.streambox.app.sites.SiteManager
    var editing by remember { mutableStateOf<Site?>(null) }

    SubPage("Siteler", "${sm.sites.size} site · reklamsız tarayıcı ve oynatıcı") {
        HeroCard(sm.sites.size, sm.sites.count { it.directPlay })
        AddSiteCard()

        SectionHead("Eklenen siteler", if (sm.sites.isEmpty()) null else "${sm.sites.size}")
        if (sm.sites.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(16.dp, 4.dp).clip(RoundedCornerShape(20.dp)).background(PCard)
                .border(1.dp, PLine, RoundedCornerShape(20.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.TravelExplore, null, tint = Muted, modifier = Modifier.size(40.dp))
                Text("Henüz site eklenmedi", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Text("Yukarıdan bir film/dizi sitesinin adresini ekle.", color = Muted, fontSize = 13.sp)
            }
        } else sm.sites.forEach { st -> SiteCard(st, onEdit = { editing = st }) }

        SectionHead("Reklam engelleme", null)
        Column(Modifier.fillMaxWidth().padding(16.dp, 4.dp).clip(RoundedCornerShape(20.dp)).background(PCard)
            .border(1.dp, PLine, RoundedCornerShape(20.dp))) {
            ToggleRow(Icons.Default.Block, Color(0xFFFF453A), "Yönlendirmeleri engelle", "Tıklayınca başka siteye atan reklamlar", sm.blockRedirects) { sm.updateBlockRedirects(it) }
            Divider()
            ToggleRow(Icons.Default.OpenInNew, Color(0xFFFF9F0A), "Açılır pencereleri engelle", "Yeni sekme ya da pencere açan reklamlar", sm.blockPopups) { sm.updateBlockPopups(it) }
            Divider()
            ToggleRow(Icons.Default.Movie, Color(0xFF34C759), "Videoyu otomatik yakala", "Video bulununca oynatıcıda açmayı önerir", sm.autoVideo) { sm.updateAutoVideo(it) }
        }
        Row(Modifier.padding(24.dp, 10.dp, 24.dp, 0.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Shield, null, tint = Color(0xFF34C759), modifier = Modifier.size(16.dp).padding(top = 2.dp))
            Text("Bilinen reklam ağları, bahis ve yetişkin reklamları ile izleyiciler her zaman engellenir.",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
    editing?.let { st -> SiteEditSheet(st) { editing = null } }
}

@Composable
private fun HeroCard(count: Int, direct: Int) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 8.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3A0A0E), Color(0xFF1A1D26))))
            .border(1.dp, SiteRed.copy(alpha = 0.35f), RoundedCornerShape(24.dp)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(SiteRed.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Language, null, tint = SiteRed, modifier = Modifier.size(30.dp))
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text("Sitelerini reklamsız izle", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
            Text("Afişler otomatik çekilir, video bulunup oynatıcıda açılır.", color = Color(0xFFB8BCC4), fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$count", fontWeight = FontWeight.Black, fontSize = 26.sp, color = Color.White)
            Text(if (direct > 0) "$direct doğrudan" else "site", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionHead(title: String, trailing: String?) {
    Row(Modifier.fillMaxWidth().padding(24.dp, 20.dp, 24.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, fontSize = 12.sp, color = Muted) }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().padding(start = 64.dp).height(1.dp).background(PLine))

/** Adres kutusu + içinde "Ekle" düğmesi; ad isteğe bağlı olarak açılır. */
@Composable
private fun AddSiteCard() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val sm = com.streambox.app.sites.SiteManager
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var showName by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun add() {
        if (url.isBlank() || busy) return
        busy = true; error = null
        scope.launch {
            runCatching { sm.add(url, name) }
                .onSuccess { toast(context, "\"${it.name}\" eklendi"); url = ""; name = ""; showName = false; Nav.homeCache.remove("site:" + it.id) }
                .onFailure { error = "Eklenemedi: ${it.message}" }
            busy = false
        }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp).clip(RoundedCornerShape(20.dp)).background(PCard)
        .border(1.dp, PLine, RoundedCornerShape(20.dp)).padding(16.dp)) {
        Text("Site ekle", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            if (BuildConfig.SITES_ONLY) "Adresi yaz; ad, simge ve arama kendiliğinden bulunur."
            else "Adresi yaz; ad, simge ve arama kendiliğinden bulunur. Afişler ana sayfada \"SİTELER\" altında çıkar.",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF0F1116))
                .border(1.dp, if (error != null) Color(0xFFFF453A) else PLine, RoundedCornerShape(16.dp)).padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Link, null, tint = Muted, modifier = Modifier.size(20.dp))
            TextField(
                value = url, onValueChange = { url = it.trim(); error = null },
                placeholder = { Text("siteadi.com", color = Color(0xFF5E636E)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f)
            )
            if (url.isEmpty()) TextButton(onClick = {
                clipboard.getText()?.text?.trim()?.takeIf { it.contains('.') }?.let { url = it } ?: toast(context, "Panoda adres yok")
            }) { Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(16.dp)); Text("Yapıştır", Modifier.padding(start = 4.dp), fontSize = 13.sp) }
            else Button(
                onClick = { add() }, enabled = !busy, shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Text(if (busy) "Bakılıyor" else "Ekle", Modifier.padding(start = 6.dp), fontSize = 14.sp)
            }
        }
        error?.let { Text(it, color = Color(0xFFFF453A), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
        AnimatedVisibility(showName) {
            OutlinedTextField(name, { name = it }, label = { Text("Görünecek ad") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }
        if (!showName) Text("+ Ad belirle (isteğe bağlı)", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp).clip(RoundedCornerShape(6.dp)).clickable { showName = true }.padding(4.dp))
    }
}

/** Eklenen site kartı: simge, ad, adres, durum rozetleri ve hızlı eylemler. */
@Composable
private fun SiteCard(st: Site, onEdit: () -> Unit) {
    val context = LocalContext.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Column(
        Modifier.fillMaxWidth().padding(16.dp, 5.dp).scale(if (focused) 1.01f else 1f).clip(RoundedCornerShape(20.dp))
            .background(if (focused) Color(0xFF1C2433) else PCard)
            .border(BorderStroke(if (focused) 2.dp else 1.dp, if (focused) Accent else PLine), RoundedCornerShape(20.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current) { Nav.go(Route.SiteCatalog(st.id)) }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF23272F)), contentAlignment = Alignment.Center) {
                if (st.icon != null) AsyncImage(st.icon, null, modifier = Modifier.size(30.dp))
                else Text(st.name.take(1).uppercase(), color = SiteRed, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(st.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(st.host, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Badge(if (st.directPlay) "Doğrudan oynat" else "Tarayıcıda aç", if (st.directPlay) Color(0xFF34C759) else Color(0xFF8E939B))
            if (st.onHome && !BuildConfig.SITES_ONLY) Badge("Ana sayfada", Accent)
            if (st.search != null) Badge("Arama", Color(0xFFB28CFF))
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionChip(Icons.Default.GridView, "Katalog", Modifier.weight(1f)) { Nav.go(Route.SiteCatalog(st.id)) }
            ActionChip(Icons.Default.Public, "Tarayıcı", Modifier.weight(1f)) { SiteBrowserActivity.open(context, st.url, st.name) }
            ActionChip(Icons.Default.Tune, "Düzenle", Modifier.weight(1f), onEdit)
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp))
}

@Composable
private fun ActionChip(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (focused) Accent else Color(0xFF1F232C))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, tint: Color, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Row(
        Modifier.fillMaxWidth().background(if (focused) Color(0xFF1C2433) else Color.Transparent)
            .clickable(interactionSource = src, indication = LocalIndication.current) { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
    }
}

/** Site düzenleme: alttan açılan panel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteEditSheet(st: Site, onClose: () -> Unit) {
    val context = LocalContext.current
    val sm = com.streambox.app.sites.SiteManager
    var n by remember(st) { mutableStateOf(st.name) }
    var search by remember(st) { mutableStateOf(st.search ?: "") }
    var home by remember(st) { mutableStateOf(st.onHome) }
    var direct by remember(st) { mutableStateOf(st.directPlay) }
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color(0xFF16181E)) {
        com.streambox.app.remote.RemoteNav.Hook()
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF23272F)), contentAlignment = Alignment.Center) {
                    if (st.icon != null) AsyncImage(st.icon, null, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Siteyi düzenle", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(st.host, color = Muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(n, { n = it }, label = { Text("Ad") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(search, { search = it.trim() }, label = { Text("Arama adresi") }, singleLine = true,
                supportingText = { Text("{q} yerine aranan yazı gelir · ör. ${st.url}/?s={q}") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(PCard).border(1.dp, PLine, RoundedCornerShape(16.dp))) {
                ToggleRow(Icons.Default.PlayCircle, Color(0xFF34C759), "Doğrudan oynat",
                    "Afişe basınca sayfayı göstermeden videoyu bulup oynatıcıyı açar", direct) { direct = it }
                if (!BuildConfig.SITES_ONLY) {
                    Divider()
                    ToggleRow(Icons.Default.Home, Accent, "Kaynak seçicide göster", "Ana sayfadaki SİTELER bölümünde", home) { home = it }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                sm.update(st.copy(name = n.ifBlank { st.name }, search = search.ifBlank { null }, onHome = home, directPlay = direct))
                Nav.homeCache.remove("site:" + st.id); toast(context, "Kaydedildi"); onClose()
            }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("Kaydet", fontSize = 16.sp) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { if (confirmDelete) { sm.remove(st); toast(context, "Site silindi"); onClose() } else confirmDelete = true },
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFFF453A).copy(alpha = 0.6f))) {
                Icon(Icons.Default.Delete, null, tint = Color(0xFFFF453A))
                Text(if (confirmDelete) "Silmek için tekrar dokun" else "Siteyi sil", color = Color(0xFFFF453A), modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
