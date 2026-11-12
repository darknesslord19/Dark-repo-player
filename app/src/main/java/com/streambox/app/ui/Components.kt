package com.streambox.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

val Accent = Color(0xFF4F8CFF)
val Bg = Color(0xFF0F1115)
val Panel = Color(0xFF171A21)
val Muted = Color(0xFF9AA0A6)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, background = Bg, surface = Panel, surfaceVariant = Color(0xFF1F232C),
            onPrimary = Color.White, onBackground = Color(0xFFE8EAED), onSurface = Color(0xFFE8EAED),
        )
    ) { Surface(color = Bg, modifier = Modifier.fillMaxSize(), content = content) }
}

/** Ekranda gösterilecek afiş kartı. */
data class UiCard(val key: String, val title: String, val image: String?, val subtitle: String? = null, val onClick: () -> Unit)
data class UiRow(val title: String, val cards: List<UiCard>)

/** Dokunma ve kumanda (D-pad) ile seçilebilen afiş. Odaklanınca büyür ve çerçevelenir. */
@Composable
fun PosterCard(card: UiCard, width: Dp = 118.dp, ratio: Float = 2f / 3f, modifier: Modifier = Modifier) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Column(
        modifier
            .width(width)
            .scale(if (focused) 1.07f else 1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = card.onClick)
            .padding(2.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1F232C))
                .then(if (focused) Modifier.border(BorderStroke(3.dp, Accent), RoundedCornerShape(8.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (card.image != null) {
                AsyncImage(
                    model = card.image, contentDescription = card.title,
                    contentScale = if (ratio < 1f) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(if (ratio < 1f) 0.dp else 8.dp)
                )
            } else {
                Text(
                    card.title.take(2).uppercase(), color = Muted, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
            }
        }
        Text(
            card.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
            color = if (focused) Color.White else Color(0xFFD0D3D8),
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp)
        )
        card.subtitle?.let { Text(it, maxLines = 1, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(horizontal = 2.dp)) }
    }
}

/** Başlıklı, yatay kaydırmalı afiş rafı. */
@Composable
fun PosterRow(row: UiRow) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(row.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(row.cards) { PosterCard(it) }
        }
    }
}

@Composable
fun Loading(text: String = "Yükleniyor…") {
    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.width(22.dp), strokeWidth = 2.dp)
        Text(text, color = Muted, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
fun Message(text: String) {
    Text(text, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 6.dp))
}
