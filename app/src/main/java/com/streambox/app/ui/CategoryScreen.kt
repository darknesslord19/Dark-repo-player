package com.streambox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streambox.app.AppLog

/** Bir rafın tüm içeriği: ızgara, aşağı kaydırdıkça sonraki sayfalar yüklenir. */
@Composable
fun CategoryScreen(route: Route.Category) {
    val cards = remember(route) { mutableStateListOf<UiCard>().apply { addAll(route.initial) } }
    val seen = remember(route) { route.initial.map { it.key }.toMutableSet() }
    var page by remember(route) { mutableIntStateOf(1) }
    var loading by remember(route) { mutableStateOf(false) }
    var ended by remember(route) { mutableStateOf(route.load == null) }
    val grid = rememberLazyGridState()

    val nearEnd by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= grid.layoutInfo.totalItemsCount - 8
        }
    }

    // İlk sayfa raftakilerle aynıysa atla; sonra kaydırdıkça devam et
    LaunchedEffect(nearEnd, page, ended, loading) {
        val load = route.load ?: return@LaunchedEffect
        if (!nearEnd || ended || loading) return@LaunchedEffect
        loading = true
        val next = page + if (page == 1 && cards.size > 0) 1 else 0
        val result = try { load(next) } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            AppLog.e("${route.title} sayfa $next", t); null
        }
        val fresh = result.orEmpty().filter { seen.add(it.key) }
        cards.addAll(fresh)
        if (result == null || fresh.isEmpty()) ended = true
        page = next + 1
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp, 6.dp, 16.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
            Text(route.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Text("${cards.size}", color = Muted, fontSize = 13.sp)
        }
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Adaptive(112.dp),
            contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, BottomSpace),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(cards) { PosterCard(it, width = 200.dp, modifier = Modifier.fillMaxWidth()) }
            if (loading) item(span = { GridItemSpan(maxLineSpan) }) { Loading() }
            if (ended && cards.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Message("İçerik yok") }
        }
    }
}
