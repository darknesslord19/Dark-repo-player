package com.streambox.app.ui

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val Gold = Color(0xFFF5C518)
private val ChipBg = Color(0xFF1E222B)

/** Dakikayı "1 sa 52 dk" biçimine çevirir. */
fun fmtRuntime(min: Int?): String? = when {
    min == null || min <= 0 -> null
    min >= 60 -> "${min / 60} sa ${min % 60} dk"
    else -> "$min dk"
}

/** Detay sayfası üst bölümü: tam ekran arka plan, afiş, başlık, künye ve türler. */
@Composable
fun DetailHero(
    title: String,
    tagline: String?,
    poster: String?,
    backdrop: String?,
    headers: Map<String, String>,
    source: String?,
    facts: List<String>,
    rating: Double?,
    genres: List<String>,
) {
    val wide = isWide()
    val bgImage = backdrop ?: poster
    Box(Modifier.fillMaxWidth().height(if (wide) 470.dp else 540.dp)) {
        if (bgImage != null) AsyncImage(
            imageModel(bgImage, headers), null, contentScale = ContentScale.Crop,
            alignment = if (backdrop == null) Alignment.TopCenter else Alignment.Center,
            modifier = Modifier.fillMaxSize()
        )
        // karartmalar
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xB3000000), 0.25f to Color.Transparent, 0.55f to Color(0x660F1115), 1f to Bg)))
        if (wide) Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Bg, 0.55f to Color(0xCC0F1115), 1f to Color.Transparent)))
        if (backdrop == null) Box(Modifier.fillMaxSize().background(Color(0x33000000)))

        // üst çubuk: geri + kaynak rozeti
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Filled.ArrowBack) { Nav.back() }
            Spacer(Modifier.weight(1f))
            if (source != null) Text(
                source, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x80000000))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Row(
            Modifier.align(if (wide) Alignment.BottomStart else Alignment.BottomCenter)
                .padding(horizontal = if (wide) 36.dp else 20.dp).padding(bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (wide && poster != null) {
                AsyncImage(
                    imageModel(poster, headers), title, contentScale = ContentScale.Crop,
                    modifier = Modifier.width(170.dp).aspectRatio(2f / 3f).shadow(18.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.width(26.dp))
            }
            Column(
                Modifier.widthIn(max = 640.dp),
                horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                if (!wide && poster != null && backdrop != null) {
                    AsyncImage(
                        imageModel(poster, headers), title, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(112.dp).aspectRatio(2f / 3f).shadow(16.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Text(
                    title, fontSize = if (wide) 36.sp else 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = if (wide) 40.sp else 34.sp,
                    color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                )
                if (!tagline.isNullOrBlank()) Text(
                    tagline, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = Color(0xFFB8BCC4),
                    textAlign = if (wide) TextAlign.Start else TextAlign.Center, modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(10.dp))
                FactsLine(rating, facts)
                if (genres.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    GenreChips(genres.take(4), center = !wide)
                }
            }
        }
    }
}

@Composable
private fun FactsLine(rating: Double?, facts: List<String>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (rating != null && rating > 0) {
            Row(
                Modifier.clip(RoundedCornerShape(6.dp)).background(Gold).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Star, null, tint = Color.Black, modifier = Modifier.size(13.dp))
                Text("%.1f".format(rating), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 2.dp))
            }
        }
        facts.forEachIndexed { i, f ->
            if (i > 0 || (rating != null && rating > 0)) Box(Modifier.size(4.dp).clip(CircleShape).background(Color(0xFF8E939B)))
            Text(f, color = Color(0xFFE4E6EA), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun GenreChips(genres: List<String>, center: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp, if (center) Alignment.CenterHorizontally else Alignment.Start)) {
        genres.forEach { g ->
            Text(
                g, fontSize = 11.sp, color = Color(0xFFD5D8DD), maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(if (focused) Accent else Color(0x80000000))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White) }
}

/** Büyük oynat düğmesi (+ kaldığın yer çubuğu) ve altındaki ikincil eylemler. */
@Composable
fun PlayActions(
    label: String,
    progress: Float?,
    progressText: String?,
    onPlay: (() -> Unit)?,
    actions: List<Triple<ImageVector, String, () -> Unit>>,
) {
    val wide = isWide()
    Column(Modifier.fillMaxWidth().padding(horizontal = if (wide) 36.dp else 20.dp).padding(top = 14.dp)) {
        if (onPlay != null) {
            val src = remember { MutableInteractionSource() }
            val focused by src.collectIsFocusedAsState()
            Row(
                Modifier.then(if (wide) Modifier.widthIn(min = 260.dp) else Modifier.fillMaxWidth()).height(54.dp)
                    .scale(if (focused) 1.03f else 1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (focused) Color.White else Accent)
                    .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onPlay)
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(28.dp))
                Text(label, color = if (focused) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
            }
            if (progress != null) {
                Row(Modifier.then(if (wide) Modifier.width(260.dp) else Modifier.fillMaxWidth()).padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Accent))
                    }
                    if (progressText != null) Text(progressText, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
        if (actions.isNotEmpty()) Row(
            Modifier.padding(top = 16.dp).then(if (wide) Modifier else Modifier.fillMaxWidth()),
            horizontalArrangement = if (wide) Arrangement.spacedBy(14.dp) else Arrangement.SpaceEvenly,
        ) { actions.forEach { (icon, text, click) -> ActionButton(icon, text, click) } }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Column(
        Modifier.width(96.dp).clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color(0xFF22304D) else Color.Transparent)
            .then(if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(ChipBg).border(1.dp, Color(0x1FFFFFFF), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(text, fontSize = 11.sp, color = Color(0xFFC8CBD0), maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

/** "Konu" bölümü: dokununca açılıp kapanır. */
@Composable
fun Synopsis(text: String?) {
    if (text.isNullOrBlank()) return
    var open by remember { mutableStateOf(false) }
    val wide = isWide()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = if (wide) 36.dp else 20.dp).padding(top = 18.dp)
            .widthIn(max = 900.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { open = !open }
            .animateContentSize()
    ) {
        SectionLabel("Konu", Modifier)
        Text(text, color = Color(0xFFC8CBD0), fontSize = 14.sp, lineHeight = 21.sp,
            maxLines = if (open) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        if (!open && text.length > 180) Text("Devamını oku", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier.padding(start = if (isWide()) 36.dp else 20.dp, top = 22.dp, bottom = 10.dp), trailing: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Accent))
        Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White, modifier = Modifier.padding(start = 8.dp))
        if (trailing != null) Text(trailing, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Sezon sekmeleri. */
@Composable
fun SeasonTabs(seasons: List<Pair<Int, String>>, selected: Int?, onSelect: (Int) -> Unit) {
    if (seasons.size < 2) return
    LazyRow(contentPadding = PaddingValues(horizontal = if (isWide()) 36.dp else 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(seasons) { (n, name) ->
            val src = remember { MutableInteractionSource() }
            val focused by src.collectIsFocusedAsState()
            val sel = n == selected
            Text(
                name, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                color = if (sel) Color.White else Color(0xFFB8BCC4),
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (sel) Accent else Chip)
                    .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(50)) else Modifier)
                    .clickable(interactionSource = src, indication = LocalIndication.current) { onSelect(n) }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            )
        }
    }
}

/** Bölüm kartı: küçük görsel + numara + kaldığın yer çubuğu + özet. */
@Composable
fun EpisodeCard(
    number: Int?, title: String, overview: String?, image: String?, headers: Map<String, String>,
    runtime: Int?, progress: Float?, onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val wide = isWide()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (wide) 28.dp else 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color(0xFF1C2638) else Color.Transparent)
            .then(if (focused) Modifier.border(BorderStroke(2.dp, Accent), RoundedCornerShape(14.dp)) else Modifier)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(if (wide) 190.dp else 140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Panel), contentAlignment = Alignment.Center) {
            if (image != null) AsyncImage(imageModel(image, headers), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color(0x33000000)))
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x99000000)).border(1.dp, Color(0x55FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            if (progress != null) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x55FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Accent))
            }
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (number != null) Text("$number", color = Accent, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(end = 8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
            }
            fmtRuntime(runtime)?.let { Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)) }
            if (!overview.isNullOrBlank()) Text(overview, color = Color(0xFFA5AAB2), fontSize = 12.sp, lineHeight = 17.sp,
                maxLines = if (wide) 3 else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Oyuncular: yuvarlak fotoğraf + ad + karakter. */
@Composable
fun CastRow(cast: List<Triple<String, String?, String?>>) {
    if (cast.isEmpty()) return
    SectionLabel("Oyuncular")
    LazyRow(contentPadding = PaddingValues(horizontal = if (isWide()) 36.dp else 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(cast) { (name, role, image) ->
            Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(70.dp).clip(CircleShape).background(ChipBg), contentAlignment = Alignment.Center) {
                    if (image != null) AsyncImage(image, name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Default.Person, null, tint = Muted, modifier = Modifier.size(34.dp))
                }
                Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                if (!role.isNullOrBlank()) Text(role, fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Benzer içerikler rafı. */
@Composable
fun SimilarRow(cards: List<UiCard>) {
    if (cards.isEmpty()) return
    SectionLabel("Benzer içerikler")
    LazyRow(contentPadding = PaddingValues(horizontal = if (isWide()) 36.dp else 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(cards) { PosterCard(it) }
    }
}

/** Başlığı paylaşma penceresi. */
@Composable
fun rememberShare(): (String) -> Unit {
    val context = LocalContext.current
    return { text ->
        runCatching {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Paylaş")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
