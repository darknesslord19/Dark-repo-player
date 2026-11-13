package com.streambox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.streambox.app.AppLog
import com.streambox.app.Tmdb
import com.streambox.app.plugins.CsApi
import com.streambox.app.plugins.CsBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun SearchScreen() {
    var query by rememberSaveable { mutableStateOf("") }
    val rows = remember { mutableStateListOf<UiRow>() }
    var running by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }

    fun run(q: String) {
        if (q.isBlank()) return
        job?.cancel()
        rows.clear()
        val providers = CsBridge.providers()
        val catalogs = com.streambox.app.catalog.CatalogManager.addons.flatMap { a -> a.catalogs.filter { it.hasSearch }.map { a to it } }
        val sites = com.streambox.app.sites.SiteManager.sites.toList()
        running = 1 + providers.size + catalogs.size + sites.size
        job = scope.launch {
            launch {
                runCatching { Tmdb.search(q) }
                    .onSuccess { l -> if (l.isNotEmpty()) rows.add(0, UiRow("Keşfet (TMDB · Nuvio)", l.map { it.toCard() })) }
                    .onFailure { AppLog.e("TMDB arama", it) }
                running--
            }
            catalogs.forEach { (a, c) ->
                launch {
                    runCatching { com.streambox.app.catalog.CatalogManager.items(a, c, search = q) }
                        .onSuccess { l -> if (l.isNotEmpty()) rows.add(UiRow("${a.name} · ${c.name} (${if (c.type == "series") "Dizi" else "Film"})", l.map { it.toCard() })) }
                        .onFailure { AppLog.e("${a.name} arama", it) }
                    running--
                }
            }
            sites.forEach { st ->
                launch {
                    runCatching { com.streambox.app.sites.SiteManager.search(st, q) }
                        .onSuccess { l ->
                            if (l.isNotEmpty()) rows.add(UiRow("${st.name} · site", l.map { it ->
                                UiCard("site-" + it.url, it.title, it.poster, it.subtitle, imageHeaders = mapOf("Referer" to st.url + "/")) {
                                    val ctx = com.lagradost.cloudstream3.CommonActivity.activity ?: com.streambox.app.StreamBoxApp.instance!!
                                    com.streambox.app.sites.SiteResolver.open(ctx, st, it)
                                }
                            }))
                        }
                        .onFailure { AppLog.e("${st.name} arama", it) }
                    running--
                }
            }
            providers.forEach { api ->
                launch {
                    runCatching { CsApi.search(api, q) }
                        .onSuccess { l -> if (l.isNotEmpty()) rows.add(UiRow(api.name, l.map { it.toCard() })) }
                        .onFailure { AppLog.e("${api.name} arama hatası", it) }
                    running--
                }
            }
        }
    }

    LaunchedEffect(Nav.pendingQuery) {
        Nav.pendingQuery?.let { query = it; Nav.pendingQuery = null; run(it) }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = BottomSpace)) {
        item {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Film, dizi, anime ara…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { run(query) }),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { run(query) }) { Text("Ara") }
            }
        }
        if (running > 0) item { Loading("Aranıyor… ($running kaynak kaldı)") }
        items(rows) { PosterRow(it) }
        if (running == 0 && rows.isEmpty() && query.isNotBlank()) item { Message("Sonuç yok") }
    }
}
