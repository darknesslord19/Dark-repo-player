package com.streambox.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.streambox.app.AppLog
import com.streambox.app.WatchHistory
import com.streambox.app.sites.SiteCategory
import com.streambox.app.sites.SiteItem
import com.streambox.app.sites.SiteManager
import com.streambox.app.sites.SiteResolver

private val CatRed = Color(0xFFE50914)
private val Gold = Color(0xFFF5C518)

/** Sitenin afiş kataloğu: kategori filtreleri, ızgara görünüm, sayfa sayfa yükleme. */
@Composable
fun SiteCatalogScreen(route: Route.SiteCatalog) {
    val context = LocalContext.current
    val site = SiteManager.sites.firstOrNull { it.id == route.siteId }
    if (site == null) { Message("Site bulunamadı"); return }
    val items = remember(route) { mutableStateListOf<SiteItem>() }
    var next by remember(route) { mutableStateOf<String?>(route.url ?: site.url) }
    var loading by remember(route) { mutableStateOf(false) }
    var error by remember(route) { mutableStateOf<String?>(null) }
    var cats by remember(site.id) { mutableStateOf<List<SiteCategory>>(emptyList()) }
    var menu by remember { mutableStateOf<String?>(null) }
    val grid = rememberLazyGridState()

    LaunchedEffect(site.id) { cats = runCatching { SiteManager.categories(site) }.getOrDefault(emptyList()) }

    suspend fun loadMore() {
        val url = next ?: return
        if (loading) return
        loading = true; error = null
        try {
            val (list, nxt) = SiteManager.listing(url)
            val fresh = list.filter { n -> items.none { it.url == n.url } }
            items += fresh
            next = if (fresh.isEmpty()) null else nxt
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            AppLog.e("Site kataloğu: $url", t)
            if (items.isEmpty()) error = AppLog.describe(t)
            next = null
        }
        loading = false
    }
    LaunchedEffect(route) { loadMore() }
    // Sona yaklaşınca sonraki sayfa
    val nearEnd by remember { derivedStateOf { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= grid.layoutInfo.totalItemsCount - 6 } == true } }
    LaunchedEffect(nearEnd, next) { if (nearEnd && next != null && items.isNotEmpty()) loadMore() }

    val groups = cats.groupBy { it.group }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A0507), Bg, Bg)))) {
        // Başlık
        Row(Modifier.fillMaxWidth().padding(14.dp, 10.dp, 14.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SquareBtn(Icons.AutoMirrored.Filled.ArrowBack) { Nav.back() }
            Box(Modifier.padding(start = 12.dp).size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A0A0D))
                .border(1.dp, CatRed.copy(alpha = 0.6f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                if (site.icon != null) AsyncImage(site.icon, null, modifier = Modifier.size(28.dp))
                else Text(site.name.take(1), color = CatRed, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(route.title ?: "Yeni eklenenler", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${site.name} · film ve dizi kataloğu", color = Muted, fontSize = 13.sp, maxLines = 1)
            }
            SquareBtn(Icons.Default.Language) { com.streambox.app.sites.SiteBrowserActivity.open(context, route.url ?: site.url, site.name) }
        }
        // Filtreler
        if (groups.isNotEmpty()) LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groups.keys.toList()) { g ->
                Box {
                    FilterBtn("$g ▾", selected = route.title != null && groups[g]?.any { it.url == route.url } == true) { menu = g }
                    DropdownMenu(expanded = menu == g, onDismissRequest = { menu = null }, modifier = Modifier.heightIn(max = 420.dp)) {
                        groups[g].orEmpty().forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = {
                                menu = null
                                Nav.back(); Nav.go(Route.SiteCatalog(site.id, c.name, c.url))
                            })
                        }
                    }
                }
            }
            if (route.url != null) item { FilterBtn("Temizle", false) { Nav.back(); Nav.go(Route.SiteCatalog(site.id)) } }
        }

        when {
            error != null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Message("Yüklenemedi: $error")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { SiteManager.clearCache(); com.streambox.app.cf.CfVerify.open(context, route.url ?: site.url); Nav.back(); Nav.go(route) }) { Text("Siteyi doğrula") }
                    OutlinedButton(onClick = { com.streambox.app.sites.SiteBrowserActivity.open(context, route.url ?: site.url, site.name) }) { Text("Tarayıcıda aç") }
                }
            }
            items.isEmpty() && loading -> Loading()
            items.isEmpty() -> Message("Bu sayfada afiş bulunamadı")
            else -> LazyVerticalGrid(
                GridCells.Adaptive(if (isWide()) 170.dp else 150.dp), state = grid,
                contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, BottomSpace),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.url }) { it -> CatalogPoster(it, site.url) { SiteResolver.open(context, site, it) } }
                if (loading) item(span = { GridItemSpan(maxLineSpan) }) { Loading("Daha fazla yükleniyor…") }
            }
        }
    }
}

@Composable
private fun SquareBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Box(
        Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF15171C))
            .border(1.dp, if (focused) Color.White else Color(0xFF2A2D35), RoundedCornerShape(16.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White) }
}

@Composable
private fun FilterBtn(label: String, selected: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Text(
        label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) CatRed.copy(alpha = 0.35f) else Color(0xFF140B0C))
            .border(1.dp, if (focused) Color.White else if (selected) CatRed else Color(0xFF3A2427), RoundedCornerShape(12.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp)
    )
}

/** Izgara afişi: puan rozeti, alt kararma üzerinde başlık, izlendiyse ilerleme çubuğu. */
@Composable
private fun CatalogPoster(item: SiteItem, referer: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val progress = WatchHistory.entry("site|${item.url.substringBefore('#')}")?.progress?.takeIf { it > 0f }
    Box(
        Modifier.fillMaxWidth().aspectRatio(2f / 3f).scale(if (focused) 1.05f else 1f)
            .clip(RoundedCornerShape(18.dp)).background(Color(0xFF15171C))
            .border(BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0x22FFFFFF)), RoundedCornerShape(18.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
    ) {
        AsyncImage(imageModel(item.poster, mapOf("Referer" to "$referer/")), item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xEE000000))))
        item.rating?.let {
            Row(Modifier.padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Gold).padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.StarBorder, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                Text(it, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 3.dp))
            }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp)) {
            Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
            item.subtitle?.let { Text(it, color = Color(0xFFB8BCC4), fontSize = 11.sp) }
            if (progress != null) Box(Modifier.padding(top = 6.dp).fillMaxWidth(0.8f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x55FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(CatRed))
            }
        }
    }
}

/** "Video aranıyor…" penceresi (doğrudan oynatma sırasında). */
@Composable
fun SiteResolveOverlay() {
    val item = SiteResolver.active ?: return
    Dialog(onDismissRequest = { SiteResolver.cancel() }) {
        com.streambox.app.remote.RemoteNav.Hook()
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF171A21)).padding(18.dp).widthIn(max = 420.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.poster != null) AsyncImage(item.poster, null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.padding(horizontal = 14.dp).weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = CatRed)
                    Text(SiteResolver.status, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
            IconButton(onClick = { SiteResolver.cancel() }) { Icon(Icons.Default.Close, "İptal") }
        }
    }
}
