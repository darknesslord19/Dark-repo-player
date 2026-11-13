package com.streambox.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lagradost.cloudstream3.MainAPI
import com.streambox.app.AppLog
import com.streambox.app.Settings
import com.streambox.app.Tmdb
import com.streambox.app.TmdbItem
import com.streambox.app.plugins.CsApi
import com.streambox.app.plugins.CsBridge
import com.streambox.app.plugins.CsCard
import com.streambox.app.plugins.PluginManager
import com.streambox.app.plugins.RepoManager
import com.streambox.app.nuvio.NuvioRepoManager
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.streambox.app.catalog.CatalogManager

fun TmdbItem.toCard() = UiCard(
    key = "tmdb-$type-$id", title = title, image = poster,
    subtitle = listOfNotNull(year, if (type == "tv") "Dizi" else "Film").joinToString(" · "),
    backdrop = backdrop, overview = overview,
) { Nav.go(Route.TmdbDetail(this)) }

fun CsCard.toCard() = UiCard(key = "cs-${api.name}-$url", title = name, image = poster, imageHeaders = posterHeaders) {
    Nav.go(Route.CsDetail(this))
}

const val TMDB_SOURCE = "__tmdb__"

/** Katalog içeriği kartı: tıklanınca TMDB kaydına çevrilip detay sayfası açılır. */
fun com.streambox.app.catalog.CatalogItem.toCard() = UiCard(
    key = "cat-$type-$id", title = name, image = poster,
    subtitle = listOfNotNull(year, if (type == "series") "Dizi" else "Film").joinToString(" · "),
    backdrop = background, overview = description,
) { openCatalogItem(this) }

fun openCatalogItem(item: com.streambox.app.catalog.CatalogItem) {
    val app = com.streambox.app.StreamBoxApp.instance ?: return
    app.scope.launch {
        val t = com.streambox.app.catalog.CatalogManager.toTmdb(item)
        com.streambox.app.onMain {
            if (t != null) Nav.go(Route.TmdbDetail(t))
            else android.widget.Toast.makeText(app, "\"${item.name}\" TMDB'de bulunamadı", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

/** Kaynak seçicide kategori çipleri: CloudStream TvType adlarına göre. */
private val categories = listOf(
    "Tümü" to emptySet(),
    "Filmler" to setOf("Movie", "AnimeMovie"),
    "Diziler" to setOf("TvSeries", "Cartoon"),
    "Anime" to setOf("Anime", "AnimeMovie", "OVA"),
    "Asya dizileri" to setOf("AsianDrama"),
    "Canlı TV" to setOf("Live"),
    "Belgesel" to setOf("Documentary"),
    "Diğer" to setOf("Others", "Music", "AudioBook", "Audio", "Podcast", "CustomMedia", "Torrent", "NSFW"),
)

/** Kaynak adı → eklentinin geldiği repo adı. */
private fun providerRepoMap(): Map<String, String> {
    val repoNames = RepoManager.repos.associate { it.url to it.name }
    val out = mutableMapOf<String, String>()
    PluginManager.installed.forEach { p ->
        val repo = p.repoUrl?.let { repoNames[it] } ?: if (p.repoUrl == null) "Yerel eklentiler" else "Diğer"
        PluginManager.results[p.internalName]?.providers?.forEach { out[it] = repo }
    }
    return out
}

@Composable
fun HomeScreen() {
    val loadedCount = PluginManager.results.size
    val providers = remember(loadedCount) { CsBridge.providers().filter { it.hasMainPage } }
    val selected = Settings.homeSource
    val isNuvio = selected.startsWith("nuvio:")
    // Nuvio seçimleri TMDB kataloğunu kullanır (eklentiler TMDB kimliğiyle çalışır)
    val catalogKey = if (isNuvio) TMDB_SOURCE else selected
    val api = providers.firstOrNull { it.name == selected }
    var rows by remember(catalogKey) { mutableStateOf(Nav.homeCache[catalogKey]) }
    var error by remember(catalogKey) { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf(false) }

    LaunchedEffect(catalogKey, api) {
        if (Nav.homeCache[catalogKey] != null) { rows = Nav.homeCache[catalogKey]; return@LaunchedEffect }
        if (isNuvio && selected != "nuvio:*" && NuvioRepoManager.repos.none { "nuvio:" + it.url == selected }) {
            Settings.updateHomeSource(TMDB_SOURCE); return@LaunchedEffect
        }
        val catalog = if (selected.startsWith("catalog:")) CatalogManager.addons.firstOrNull { "catalog:" + it.url == selected } else null
        if (selected.startsWith("catalog:") && catalog == null) { Settings.updateHomeSource(TMDB_SOURCE); return@LaunchedEffect }
        if (catalog != null) {
            rows = null; error = null
            val result = kotlinx.coroutines.coroutineScope {
                catalog.catalogs.map { c ->
                    async {
                        runCatching { CatalogManager.items(catalog, c) }.onFailure { AppLog.e("Katalog ${c.name}", it) }.getOrNull()
                            ?.takeIf { it.isNotEmpty() }?.let { first ->
                                val title = c.name + " · " + if (c.type == "series") "Diziler" else if (c.type == "movie") "Filmler" else c.type
                                UiRow(title, first.map { it.toCard() }) { page ->
                                    CatalogManager.items(catalog, c, skip = (page - 1) * first.size).map { it.toCard() }
                                }
                            }
                    }
                }.mapNotNull { it.await() }
            }
            Nav.homeCache[catalogKey] = result
            rows = result
            return@LaunchedEffect
        }
        if (catalogKey != TMDB_SOURCE && api == null) {
            // eklentiler henüz yükleniyor olabilir
            if (loadedCount < PluginManager.installed.count { it.enabled }) return@LaunchedEffect
            Settings.updateHomeSource(TMDB_SOURCE); return@LaunchedEffect
        }
        rows = null; error = null
        try {
            val result = if (api == null) {
                Tmdb.home().map { shelf ->
                    UiRow(shelf.title, shelf.items.map { it.toCard() }) { page -> Tmdb.page(shelf, page).map { it.toCard() } }
                }
            } else {
                CsApi.mainPage(api).map { r ->
                    UiRow(r.title, r.items.map { it.toCard() }) { page -> CsApi.mainPageMore(api, r, page).map { it.toCard() } }
                }
            }
            Nav.homeCache[catalogKey] = result
            rows = result
        } catch (e: Throwable) {
            AppLog.e("Ana sayfa yüklenemedi ($catalogKey)", e)
            error = AppLog.describe(e)
            rows = emptyList()
        }
    }

    val sourceLabel = when {
        selected == TMDB_SOURCE -> "Keşfet"
        selected == "nuvio:*" -> "Nuvio"
        isNuvio -> NuvioRepoManager.repos.firstOrNull { "nuvio:" + it.url == selected }?.name ?: "Nuvio"
        selected.startsWith("catalog:") -> CatalogManager.addons.firstOrNull { "catalog:" + it.url == selected }?.name ?: "Katalog"
        else -> selected
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomSpace)) {
            val r = rows
            val heroCards = r?.firstOrNull()?.cards?.take(6).orEmpty()
            item {
                if (heroCards.isNotEmpty()) Hero(heroCards)
                else Spacer(Modifier.statusBarsPadding().height(72.dp))
            }
            when {
                r == null -> item { Loading() }
                error != null -> item { Message("Yüklenemedi: $error") }
                r.isEmpty() -> item {
                    Message(if (api == null) "Katalog boş. TMDB anahtarını Ayarlar'dan kontrol et." else "Bu kaynağın ana sayfası boş.")
                }
                else -> items(r) { PosterRow(it) }
            }
            if (providers.isEmpty()) item {
                Message("CloudStream kaynaklarının afişleri için Eklentiler'den repo ekleyip eklenti kur.")
            }
        }

        // Üst çubuk: solda logo, sağda kaynak seçici
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                .statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Stream", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("Box", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Accent)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { Nav.tab(Route.Search) }) { Icon(Icons.Default.Search, "Ara", tint = Color.White) }
            SourcePill(sourceLabel, selected == TMDB_SOURCE || isNuvio || selected.startsWith("catalog:")) { picker = true }
        }
    }

    if (picker) SourcePicker(providers, selected, onDismiss = { picker = false }) {
        Settings.updateHomeSource(it); picker = false
    }
}

@Composable
private fun SourcePill(label: String, discover: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Row(
        Modifier
            .widthIn(max = 210.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xCC14161B))
            .border(1.dp, if (focused) Accent else Color(0x33FFFFFF), RoundedCornerShape(50))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (discover) Icons.Default.Explore else Icons.Default.Extension, null, tint = Accent, modifier = Modifier.size(18.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp).weight(1f, fill = false))
        Icon(Icons.Default.ExpandMore, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** Büyük vitrin: ilk raftaki içerikler sırayla değişir. */
@Composable
private fun Hero(cards: List<UiCard>) {
    var index by remember(cards) { mutableIntStateOf(0) }
    LaunchedEffect(cards) {
        while (cards.size > 1) { delay(7000); index = (index + 1) % cards.size }
    }
    val wide = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val height = if (wide) 380.dp else 540.dp
    Box(Modifier.fillMaxWidth().height(height)) {
        Crossfade(targetState = cards[index.coerceIn(0, cards.size - 1)], animationSpec = tween(700), label = "hero") { c ->
            val img = if (wide) c.backdrop ?: c.image else c.image?.replace("/w342/", "/w780/") ?: c.backdrop
            Box(Modifier.fillMaxSize()) {
                if (img != null) AsyncImage(imageModel(img, c.imageHeaders), c.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0f to Color(0x66000000), 0.25f to Color.Transparent, 0.6f to Color(0x660F1115), 1f to Bg)))
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp, 0.dp, 20.dp, 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(c.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 32.sp)
                    c.subtitle?.let { Text(it, color = Color(0xFFD0D3D8), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        HeroButton("Oynat", Icons.Default.PlayArrow, primary = true, onClick = c.onClick)
                        HeroButton("Detaylar", Icons.Default.Info, primary = false, onClick = c.onClick)
                    }
                }
            }
        }
        // Sayfa noktaları
        if (cards.size > 1) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            cards.indices.forEach { i ->
                Box(Modifier.size(if (i == index) 16.dp else 5.dp, 5.dp).clip(CircleShape)
                    .background(if (i == index) Accent else Color(0x66FFFFFF)))
            }
        }
    }
}

@Composable
private fun HeroButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Row(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (primary) Accent else Color(0x33FFFFFF))
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Alttan açılan kaynak seçici: arama, kategori çipleri, repo grupları. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcePicker(providers: List<MainAPI>, selected: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var category by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    val repoOf = remember(providers) { providerRepoMap() }

    val filtered = providers.filter { p ->
        val types = categories[category].second
        (types.isEmpty() || p.supportedTypes.any { it.name in types }) &&
            (query.isBlank() || p.name.contains(query, true))
    }
    val groups = filtered.groupBy { repoOf[it.name] ?: "CloudStream" }.toSortedMap()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Color(0xFF16181E),
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3A3F49))) }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(20.dp, 8.dp, 12.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Kaynak seç", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${providers.size} kaynak", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.clip(CircleShape).background(Color(0xFF23262E))) {
                    Icon(Icons.Default.Close, "Kapat")
                }
            }
            OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Kaynak ara…") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories.size) { i ->
                    val count = if (i == 0) providers.size else providers.count { p -> p.supportedTypes.any { it.name in categories[i].second } }
                    Chip("${categories[i].first} $count", category == i) { category = i }
                }
            }
            LazyColumn(Modifier.fillMaxWidth().height(520.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    SourceRow("Keşfet", "TMDB kataloğu · tüm Nuvio eklentileri", Icons.Default.Explore, selected == TMDB_SOURCE) { onPick(TMDB_SOURCE) }
                }
                // Stremio katalog eklentileri
                val cats = CatalogManager.addons.filter { query.isBlank() || it.name.contains(query, true) }
                if (cats.isNotEmpty() && category == 0) {
                    item(key = "g-catalog") {
                        Row(Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF23262E)), contentAlignment = Alignment.Center) {
                                Text("K", color = Color(0xFFFFB020), fontWeight = FontWeight.Bold)
                            }
                            Text("KATALOGLAR", fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp,
                                modifier = Modifier.padding(start = 12.dp).weight(1f))
                            Text("${cats.size}", color = Muted, fontSize = 13.sp)
                        }
                    }
                    items(cats) { a ->
                        SourceRow(a.name, "Katalog · ${a.catalogs.size} liste · Nuvio ile oynatılır", Icons.Default.Explore,
                            selected == "catalog:" + a.url) { onPick("catalog:" + a.url) }
                    }
                }
                // Nuvio repoları: TMDB kataloğu + yalnızca o reponun eklentileriyle yayın arama
                val nuvioRepos = NuvioRepoManager.repos.filter { query.isBlank() || it.name.contains(query, true) }
                if (nuvioRepos.isNotEmpty() && category == 0) {
                    item(key = "g-nuvio") {
                        Row(Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF23262E)), contentAlignment = Alignment.Center) {
                                Text("N", color = Color(0xFF34C759), fontWeight = FontWeight.Bold)
                            }
                            Text("NUVIO", fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp,
                                modifier = Modifier.padding(start = 12.dp).weight(1f))
                            Text("${NuvioRepoManager.scrapers.count { it.enabled }} eklenti", color = Muted, fontSize = 13.sp)
                        }
                    }
                    item(key = "nuvio-all") {
                        SourceRow("Tüm Nuvio eklentileri", "TMDB kataloğu · ${NuvioRepoManager.scrapers.count { it.enabled }} etkin eklenti",
                            Icons.Default.Extension, selected == "nuvio:*") { onPick("nuvio:*") }
                    }
                    items(nuvioRepos) { r ->
                        val n = NuvioRepoManager.scrapers.count { it.repoUrl == r.url && it.enabled }
                        SourceRow(r.name, "Nuvio · $n etkin eklenti", Icons.Default.Extension, selected == "nuvio:" + r.url) { onPick("nuvio:" + r.url) }
                    }
                }
                groups.forEach { (repo, list) ->
                    val isCollapsed = collapsed[repo] == true
                    item(key = "g-$repo") {
                        Row(
                            Modifier.fillMaxWidth().clickable { collapsed[repo] = !isCollapsed }.padding(20.dp, 16.dp, 20.dp, 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF23262E)), contentAlignment = Alignment.Center) {
                                Text(repo.take(1).uppercase(), color = Accent, fontWeight = FontWeight.Bold)
                            }
                            Text(repo.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp,
                                modifier = Modifier.padding(start = 12.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${list.size}", color = Muted, fontSize = 13.sp)
                            Icon(Icons.Default.ExpandMore, null, tint = Muted,
                                modifier = Modifier.padding(start = 6.dp).then(if (isCollapsed) Modifier else Modifier))
                        }
                    }
                    if (!isCollapsed) items(list) { p ->
                        val types = p.supportedTypes.map { it.name }
                        val kind = categories.drop(1).firstOrNull { c -> types.any { it in c.second } }?.first
                        SourceRow(p.name, listOfNotNull(kind, p.lang.uppercase().takeIf { it.isNotBlank() }).joinToString(" · "),
                            null, selected == p.name) { onPick(p.name) }
                    }
                }
                if (filtered.isEmpty() && providers.isNotEmpty()) item { Message("Bu filtrede kaynak yok") }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        color = if (selected) Color.White else Color(0xFFC8CBD0),
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Accent else Color(0xFF23262E))
            .border(1.5.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(50))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun SourceRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, selected: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp).clip(RoundedCornerShape(12.dp))
            .background(when { focused -> Color(0xFF22304D); selected -> Color(0xFF1C2433); else -> Color.Transparent })
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Accent))
        if (icon != null) Icon(icon, null, tint = Accent, modifier = Modifier.padding(start = 8.dp).size(20.dp))
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1)
        }
    }
}
