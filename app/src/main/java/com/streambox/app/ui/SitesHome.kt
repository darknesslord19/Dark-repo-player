package com.streambox.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streambox.app.AppLog
import com.streambox.app.sites.Site
import com.streambox.app.sites.SiteManager
import com.streambox.app.sites.SiteResolver

/** "Darknes Siteler" uygulamasının ana sayfası: siteler + her sitenin son eklenenleri. */
@Composable
fun SitesHomeScreen() {
    val sites = SiteManager.sites.toList()
    LazyColumn(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A0507), Bg, Bg))),
        contentPadding = PaddingValues(bottom = BottomSpace)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(20.dp, 16.dp, 20.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(com.streambox.app.R.drawable.logo_dl), null, Modifier.size(44.dp).clip(CircleShape))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Row {
                        Text("Darknes", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text("Siteler", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFFE50914))
                    }
                    Text("${sites.size} site · reklamsız", color = Muted, fontSize = 13.sp)
                }
            }
        }
        item { ContinueWatchingRow() }
        item {
            Text("Sitelerim", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp, 14.dp, 20.dp, 8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sites, key = { it.id }) { st -> SiteTile(st) }
                item { AddSiteTile() }
            }
        }
        if (sites.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Language, null, tint = Muted, modifier = Modifier.size(56.dp))
                Text("Henüz site yok", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text("Film/dizi sitesinin adresini ekle; afişleri burada görünür, reklamsız izlersin.", color = Muted, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
        items(sites.filter { it.onHome }, key = { "row-" + it.id }) { st -> SiteLatestRow(st) }
    }
}

@Composable
private fun SiteTile(st: Site) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Column(
        Modifier.width(118.dp).scale(if (focused) 1.06f else 1f).clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF15171C))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color(0x22FFFFFF), RoundedCornerShape(18.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current) { Nav.go(Route.SiteCatalog(st.id)) }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF23272F)), contentAlignment = Alignment.Center) {
            if (st.icon != null) AsyncImage(st.icon, null, modifier = Modifier.size(32.dp))
            else Text(st.name.take(1).uppercase(), color = Color(0xFFE50914), fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        Text(st.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(st.host, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AddSiteTile() {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Column(
        Modifier.width(118.dp).height(124.dp).scale(if (focused) 1.06f else 1f).clip(RoundedCornerShape(18.dp))
            .border(2.dp, if (focused) Color.White else Color(0x44FFFFFF), RoundedCornerShape(18.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current) { Nav.go(Route.SettingsPage("sites")) },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(34.dp))
        Text("Site ekle", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Sitenin ana sayfasındaki ilk raf (son eklenenler). */
@Composable
private fun SiteLatestRow(st: Site) {
    val context = LocalContext.current
    var row by remember(st.id) { mutableStateOf<UiRow?>(null) }
    var failed by remember(st.id) { mutableStateOf(false) }
    LaunchedEffect(st.id) {
        runCatching { SiteManager.rows(st) }.onSuccess { rows ->
            val items = rows.flatMap { it.items }.distinctBy { it.url }.take(30)
            if (items.isEmpty()) failed = true
            else row = UiRow(st.name, items.map { it ->
                UiCard("site-" + it.url, it.title, it.poster, listOfNotNull(it.rating?.let { r -> "★ $r" }, it.subtitle).joinToString(" · ").ifBlank { null },
                    imageHeaders = mapOf("Referer" to st.url + "/")) { SiteResolver.open(context, st, it) }
            })
        }.onFailure { AppLog.e("Site yüklenemedi: ${st.name}", it); failed = true }
    }
    Column(Modifier.padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(st.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            Text("Tümü ›", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { Nav.go(Route.SiteCatalog(st.id)) }.padding(8.dp))
        }
        val r = row
        when {
            r != null -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(r.cards, key = { it.key }) { PosterCard(it) }
            }
            failed -> Text("Afişler alınamadı · Tümü'ne dokunup doğrulayabilirsin", color = Muted, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            else -> Loading()
        }
    }
}
