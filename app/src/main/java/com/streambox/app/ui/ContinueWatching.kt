package com.streambox.app.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import coil.compose.AsyncImage
import com.lagradost.cloudstream3.CommonActivity
import com.streambox.app.HistoryEntry
import com.streambox.app.StreamBoxApp
import com.streambox.app.TmdbItem
import com.streambox.app.WatchHistory
import com.streambox.app.player.PlayItem
import com.streambox.app.player.PlayerActivity
import com.streambox.app.plugins.CsBridge
import com.streambox.app.plugins.CsCard

/** Ana sayfadaki "Kaldığın yerden izle" rafı: IPTV + CloudStream + Nuvio/TMDB. */
@Composable
fun ContinueWatchingRow() {
    val list = WatchHistory.entries
    if (list.isEmpty()) return
    val context = LocalContext.current
    Column(Modifier.padding(top = 10.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Kaldığın yerden izle", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text("Basılı tut: kaldır", fontSize = 11.sp, color = Muted)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list.toList(), key = { it.key }) { e ->
                ContinueCard(e,
                    onClick = { openHistory(e) },
                    onLongClick = {
                        WatchHistory.remove(e)
                        Toast.makeText(context, "\"${e.title}\" listeden kaldırıldı", Toast.LENGTH_SHORT).show()
                    })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(e: HistoryEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val isIptv = e.kind == "iptv"
    val backdrop = e.meta.optString("backdrop").ifBlank { null }
    val image = if (isIptv) e.poster else backdrop ?: e.poster
    Column(
        Modifier.width(200.dp).scale(if (focused) 1.05f else 1f).clip(RoundedCornerShape(12.dp))
            .combinedClickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick, onLongClick = onLongClick)
            .padding(2.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1F232C))
                .then(if (focused) Modifier.border(BorderStroke(3.dp, Accent), RoundedCornerShape(10.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) AsyncImage(
                model = image, contentDescription = e.title,
                contentScale = if (isIptv) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(if (isIptv) 22.dp else 0.dp),
            ) else Text(e.title.take(2).uppercase(), color = Muted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            // alt karartma
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000)))))
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            // tür etiketi
            Text(
                when (e.kind) { "iptv" -> "CANLI"; "cs" -> e.meta.optString("api").uppercase(); else -> "NUVIO" },
                fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (isIptv) Color(0xFFE53935) else Color(0xCC000000)).padding(horizontal = 5.dp, vertical = 2.dp)
            )
            if (e.duration > 0) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(e.progress).background(Accent))
                }
                Text(remaining(e), fontSize = 10.sp, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 8.dp))
            }
        }
        Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
            color = if (focused) Color.White else Color(0xFFD0D3D8), modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp))
        e.subtitle?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(horizontal = 2.dp)) }
    }
}

private fun remaining(e: HistoryEntry): String {
    val left = ((e.duration - e.position) / 60_000).coerceAtLeast(1)
    return if (left >= 60) "${left / 60} sa ${left % 60} dk kaldı" else "$left dk kaldı"
}

/** Geçmiş kaydını açar: IPTV doğrudan oynar, diğerleri detay sayfasına gider. */
fun openHistory(e: HistoryEntry) {
    val m = e.meta
    val ctx = CommonActivity.activity ?: StreamBoxApp.instance ?: return
    when (e.kind) {
        "iptv" -> {
            val h = m.optJSONObject("headers")
            val headers = buildMap { h?.keys()?.forEach { k -> put(k, h.optString(k)) } }
            PlayerActivity.start(ctx, listOf(PlayItem(
                e.title, m.optString("url"), headers, live = true, label = m.optString("group"),
                resumeKey = e.key, meta = m.toString(),
            )), 0)
        }
        "cs" -> {
            val api = CsBridge.providers().firstOrNull { it.name == m.optString("api") }
            if (api == null) Toast.makeText(ctx, "\"${m.optString("api")}\" eklentisi yüklü değil", Toast.LENGTH_SHORT).show()
            else Nav.go(Route.CsDetail(CsCard(api, e.title, m.optString("url"), e.poster)))
        }
        "tmdb" -> Nav.go(Route.TmdbDetail(TmdbItem(
            m.optInt("id"), m.optString("type"), e.title, e.poster, m.optString("backdrop").ifBlank { null },
            m.optString("overview"), m.optString("year").ifBlank { null }, m.optDouble("rating").takeIf { !it.isNaN() && it > 0 },
        )))
    }
}
