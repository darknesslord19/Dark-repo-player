package com.streambox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.streambox.app.AppLog
import com.streambox.app.Tmdb
import com.streambox.app.TmdbItem
import com.streambox.app.plugins.CsApi
import com.streambox.app.plugins.CsBridge
import com.streambox.app.plugins.CsCard
import com.streambox.app.plugins.PluginManager

fun TmdbItem.toCard() = UiCard(
    key = "tmdb-$type-$id", title = title, image = poster,
    subtitle = listOfNotNull(year, if (type == "tv") "Dizi" else "Film").joinToString(" · "),
) { Nav.go(Route.TmdbDetail(this)) }

fun CsCard.toCard() = UiCard(key = "cs-${api.name}-$url", title = name, image = poster, subtitle = null) {
    Nav.go(Route.CsDetail(this))
}

private const val TMDB = "__tmdb__"

@Composable
fun HomeScreen() {
    // PluginManager.results değiştikçe (plugin yüklendikçe) kaynak listesi güncellenir
    val loadedCount = PluginManager.results.size
    val providers = remember(loadedCount) { CsBridge.providers().filter { it.hasMainPage } }
    var selected by rememberSaveable { mutableStateOf(TMDB) }
    var rows by remember { mutableStateOf<List<UiRow>?>(Nav.homeCache[selected]) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selected) {
        error = null
        val cached = Nav.homeCache[selected]
        if (cached != null) { rows = cached; return@LaunchedEffect }
        rows = null
        try {
            val result = if (selected == TMDB) {
                Tmdb.home().map { (t, l) -> UiRow(t, l.map { it.toCard() }) }
            } else {
                val api = providers.firstOrNull { it.name == selected }
                if (api == null) emptyList() else CsApi.mainPage(api).map { r -> UiRow(r.title, r.items.map { it.toCard() }) }
            }
            Nav.homeCache[selected] = result
            rows = result
        } catch (e: Exception) {
            AppLog.e("Ana sayfa yüklenemedi ($selected)", e)
            error = AppLog.describe(e)
            rows = emptyList()
        }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { FilterChip(selected = selected == TMDB, onClick = { selected = TMDB }, label = { Text("Keşfet (TMDB)") }) }
                items(providers, key = { it.name }) { api ->
                    FilterChip(selected = selected == api.name, onClick = { selected = api.name }, label = { Text(api.name) })
                }
            }
        }
        val r = rows
        when {
            r == null -> item { Loading() }
            error != null -> item { Message("Yüklenemedi: $error") }
            r.isEmpty() && selected == TMDB -> item { Message("Katalog boş. TMDB anahtarını Ayarlar'dan kontrol et.") }
            r.isEmpty() -> item { Message("Bu kaynağın ana sayfası boş.") }
            else -> items(r) { PosterRow(it) }
        }
        if (providers.isEmpty() && selected == TMDB) {
            item { Message("CloudStream kaynaklarının afişleri için Eklentiler'den bir repo ekle ve plugin kur.") }
        }
    }
}
