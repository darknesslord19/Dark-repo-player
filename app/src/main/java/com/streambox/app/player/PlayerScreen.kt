package com.streambox.app.player

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

private val Accent = Color(0xFF4F8CFF)
private val Scrim = Color(0xCC000000)

private fun fmt(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Kumandayla seçilebilen yuvarlak ikon düğmesi. */
@Composable
private fun CtrlButton(
    icon: ImageVector, desc: String, size: Dp = 44.dp, iconSize: Dp = 26.dp,
    filled: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    filled -> Accent
                    focused -> Color.White.copy(alpha = 0.25f)
                    else -> Color.Black.copy(alpha = 0.25f)
                }
            )
            .then(if (focused) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(iconSize)) }
}

/** Alt çubuktaki yazılı düğme (ikon + etiket). */
@Composable
private fun PillButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
            .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(50)) else Modifier)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(label, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 7.dp))
    }
}

private enum class Sheet { NONE, TRACKS, SPEED, SOURCES }

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(act: PlayerActivity) {
    val ui = act.ui
    com.streambox.app.remote.RemoteNav.Hook()
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var toast by remember { mutableStateOf<String?>(null) }
    var gesture by remember { mutableStateOf<Pair<ImageVector, Float>?>(null) }
    var seekFlash by remember { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val playFocus = remember { FocusRequester() }

    // Konum güncelleme
    LaunchedEffect(Unit) {
        while (true) {
            act.player?.let {
                if (dragging == null) ui.position = it.currentPosition
                ui.duration = it.duration.coerceAtLeast(0)
                ui.liveStream = it.isCurrentMediaItemLive
                ui.buffered = it.bufferedPosition
            }
            delay(400)
        }
    }
    // Otomatik gizleme
    LaunchedEffect(ui.controlsVisible, ui.lastInteraction, ui.playing, sheet) {
        if (ui.controlsVisible && ui.playing && sheet == Sheet.NONE) {
            delay(4000)
            if (System.currentTimeMillis() - ui.lastInteraction >= 3900) ui.controlsVisible = false
        }
    }
    LaunchedEffect(ui.controlsVisible) {
        if (ui.controlsVisible && !ui.locked) runCatching { playFocus.requestFocus() }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(1500); toast = null } }
    LaunchedEffect(seekFlash) { if (seekFlash != null) { delay(700); seekFlash = null } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = act.player
                    setKeepContentOnPlayerReset(true)
                }
            },
            update = { it.resizeMode = ui.resizeMode },
            modifier = Modifier.fillMaxSize()
        )

        if (ui.inPip) return@Box

        // Dokunma alanı: tek dokunuş göster/gizle, çift dokunuş ileri/geri, kaydırma ses/parlaklık
        Box(
            Modifier.fillMaxSize()
                .pointerInput(ui.locked) {
                    detectTapGestures(
                        onTap = {
                            if (ui.controlsVisible) ui.controlsVisible = false else act.showControls()
                        },
                        onDoubleTap = { o ->
                            if (ui.locked) return@detectTapGestures
                            if (o.x < size.width / 2) { act.seekBy(-10_000); seekFlash = "« 10 sn" }
                            else { act.seekBy(10_000); seekFlash = "10 sn »" }
                        }
                    )
                }
                .pointerInput(ui.locked) {
                    if (ui.locked) return@pointerInput
                    var left = true
                    detectVerticalDragGestures(
                        onDragStart = { o -> left = o.x < size.width / 2 },
                        onDragEnd = { gesture = null },
                        onDragCancel = { gesture = null },
                    ) { _, dy ->
                        val delta = -dy / size.height * 1.4f
                        gesture = if (left) Icons.Default.Brightness6 to act.changeBrightness(delta)
                        else Icons.Default.VolumeUp to act.changeVolume(delta)
                    }
                }
        )

        // Arabelleğe alma
        if (ui.buffering && ui.error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = Color.White, strokeWidth = 3.dp)
        }

        // Ses / parlaklık göstergesi
        gesture?.let { (icon, v) ->
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 40.dp).clip(RoundedCornerShape(50)).background(Scrim)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = Color.White)
                Box(Modifier.padding(start = 10.dp).width(140.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(0.3f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(v).background(Accent))
                }
                Text("${(v * 100).toInt()}%", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
            }
        }
        seekFlash?.let {
            Text(it, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Scrim).padding(16.dp, 10.dp))
        }

        // Kilitliyken yalnızca kilit düğmesi
        if (ui.locked) {
            AnimatedVisibility(ui.controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterStart)) {
                CtrlButton(Icons.Default.Lock, "Kilidi aç", size = 52.dp, modifier = Modifier.padding(start = 24.dp)) {
                    ui.locked = false; act.showControls(); toast = "Kilit açıldı"
                }
            }
        }

        AnimatedVisibility(ui.controlsVisible && !ui.locked, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                // Üst ve alt karartma
                Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(170.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))))

                // Üst çubuk
                val item = act.current()
                // IPTV'deki film/dizi dosyalarının süresi bellidir: ileri-geri ve çubuk gösterilir
                val canSeek = !item.live || (ui.duration > 0 && !ui.liveStream)
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CtrlButton(Icons.AutoMirrored.Filled.ArrowBack, "Geri") { act.finish() }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(item.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(item.label, ui.videoSize.ifBlank { null },
                            if (!canSeek) "CANLI" else null).joinToString(" · ")
                        if (sub.isNotBlank()) Text(sub, color = Color(0xFFB8BCC2), fontSize = 12.sp, maxLines = 1)
                    }
                    CtrlButton(Icons.Default.PictureInPictureAlt, "Küçük pencere", iconSize = 22.dp) { act.enterPip() }
                }

                // Orta düğmeler
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(46.dp)) {
                    if (act.items.size > 1) CtrlButton(Icons.Default.SkipPrevious, "Önceki", size = 54.dp, iconSize = 32.dp) { act.playIndex(ui.index - 1) }
                    if (canSeek) CtrlButton(Icons.Default.Replay10, "10 sn geri", size = 70.dp, iconSize = 46.dp) { act.seekBy(-10_000) }
                    if (ui.error != null) {
                        CtrlButton(Icons.Default.Refresh, "Tekrar dene", size = 84.dp, iconSize = 46.dp, filled = true,
                            modifier = Modifier.focusRequester(playFocus)) { act.retry() }
                    } else if (!ui.buffering || ui.playing) {
                        CtrlButton(if (ui.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (ui.playing) "Duraklat" else "Oynat", size = 84.dp, iconSize = 50.dp, filled = true,
                            modifier = Modifier.focusRequester(playFocus)) { act.togglePlay() }
                    } else Spacer(Modifier.size(84.dp).focusRequester(playFocus).focusable())
                    if (canSeek) CtrlButton(Icons.Default.Forward10, "10 sn ileri", size = 70.dp, iconSize = 46.dp) { act.seekBy(10_000) }
                    if (act.items.size > 1) CtrlButton(Icons.Default.SkipNext, "Sonraki", size = 54.dp, iconSize = 32.dp) { act.playIndex(ui.index + 1) }
                }

                ui.error?.let {
                    Text(it, color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center).padding(top = 130.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCCB3261E)).padding(12.dp, 8.dp))
                }

                // Alt çubuk
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp)) {
                    if (canSeek && ui.duration > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(fmt(dragging?.toLong() ?: ui.position), color = Color.White, fontSize = 13.sp)
                            val seekSrc = remember { MutableInteractionSource() }
                            val seekFocused by seekSrc.collectIsFocusedAsState()
                            Slider(
                                interactionSource = seekSrc,
                                value = (dragging ?: ui.position.toFloat()).coerceIn(0f, ui.duration.toFloat()),
                                onValueChange = { dragging = it; act.touch() },
                                onValueChangeFinished = { dragging?.let { act.player?.seekTo(it.toLong()) }; dragging = null },
                                valueRange = 0f..ui.duration.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = if (seekFocused) Color.White else Accent, activeTrackColor = Accent,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                                    .then(if (seekFocused) Modifier.border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(50)) else Modifier)
                                    // Kumanda: ◀ ▶ 10 sn (basılı tutunca 30 sn), OK oynat/duraklat
                                    .onPreviewKeyEvent { e ->
                                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        val step = if (e.nativeKeyEvent.repeatCount > 2) 30_000L else 10_000L
                                        when (e.key) {
                                            Key.DirectionLeft -> { act.seekBy(-step); act.touch(); true }
                                            Key.DirectionRight -> { act.seekBy(step); act.touch(); true }
                                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { act.togglePlay(); act.touch(); true }
                                            else -> false
                                        }
                                    }
                            )
                            Text("-" + fmt(ui.duration - (dragging?.toLong() ?: ui.position)), color = Color.White, fontSize = 13.sp)
                        }
                    } else if (!canSeek) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF3B30)))
                            Text("CANLI YAYIN", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        val w = Modifier.weight(1f)
                        PillButton(Icons.Default.LockOpen, "Kilitle", w) { ui.locked = true; act.showControls(); toast = "Ekran kilitlendi" }
                        PillButton(Icons.Default.AspectRatio, "Ekran", w) { toast = act.cycleResize(); act.touch() }
                        if (canSeek) PillButton(Icons.Default.Speed, if (ui.speed == 1f) "Hız" else "${ui.speed}x", w) { sheet = Sheet.SPEED }
                        PillButton(Icons.Default.ClosedCaption, "Ses / Altyazı", w) { sheet = Sheet.TRACKS }
                        if (act.items.size > 1) PillButton(Icons.Default.VideoLibrary, if (item.live) "Kanallar" else "Kaynaklar", w) { sheet = Sheet.SOURCES }
                    }
                }
            }
        }

        toast?.let {
            Text(it, color = Color.White, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp).clip(RoundedCornerShape(50))
                    .background(Scrim).padding(16.dp, 8.dp))
        }
    }

    when (sheet) {
        Sheet.SPEED -> OptionDialog("Oynatma hızı", listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).map { s ->
            Option(if (s == 1f) "Normal" else "${s}x", null, ui.speed == s) { act.setSpeed(s) }
        }) { sheet = Sheet.NONE }
        Sheet.SOURCES -> OptionDialog(if (act.current().live) "Kanallar" else "Kaynaklar", act.items.mapIndexed { i, it ->
            Option(it.label ?: it.title, if (it.label != null) it.title else null, i == ui.index) { act.playIndex(i) }
        }, scrollTo = ui.index) { sheet = Sheet.NONE }
        Sheet.TRACKS -> OptionDialog("Ses, altyazı ve kalite", trackOptions(act)) { sheet = Sheet.NONE }
        Sheet.NONE -> {}
    }
}

private data class Option(val title: String, val subtitle: String?, val selected: Boolean, val header: Boolean = false, val onClick: () -> Unit)

@OptIn(UnstableApi::class)
private fun trackOptions(act: PlayerActivity): List<Option> {
    val p = act.player ?: return emptyList()
    val out = mutableListOf<Option>()
    fun section(type: Int, title: String, offLabel: String?) {
        val groups = act.ui.tracks.groups.filter { it.type == type && it.isSupported }
        if (groups.isEmpty() && offLabel == null) return
        out += Option(title, null, false, header = true) {}
        val disabled = p.trackSelectionParameters.disabledTrackTypes.contains(type)
        val anyOverride = p.trackSelectionParameters.overrides.keys.any { it.type == type }
        if (offLabel != null) {
            out += Option(offLabel, null, disabled) {
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type, true).build()
            }
        } else {
            out += Option("Otomatik", null, !anyOverride && !disabled) {
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
                    .setTrackTypeDisabled(type, false).build()
            }
        }
        groups.forEach { g ->
            for (i in 0 until g.length) {
                val f = g.getTrackFormat(i)
                val name = when (type) {
                    C.TRACK_TYPE_VIDEO -> listOfNotNull(
                        if (f.height > 0) "${f.height}p" else null,
                        if (f.bitrate > 0) "%.1f Mbps".format(f.bitrate / 1_000_000f) else null
                    ).joinToString(" · ").ifBlank { "Video ${i + 1}" }
                    else -> f.label ?: f.language?.let { java.util.Locale(it).displayLanguage.replaceFirstChar { c -> c.uppercase() } }
                        ?: "${if (type == C.TRACK_TYPE_AUDIO) "Ses" else "Altyazı"} ${i + 1}"
                }
                out += Option(name, if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) "${f.channelCount} kanal" else null,
                    !disabled && anyOverride && g.isTrackSelected(i)) {
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(type, false)
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
                }
            }
        }
    }
    section(C.TRACK_TYPE_AUDIO, "Ses", null)
    section(C.TRACK_TYPE_TEXT, "Altyazı", "Kapalı")
    section(C.TRACK_TYPE_VIDEO, "Görüntü kalitesi", null)
    return out
}

@Composable
private fun OptionDialog(title: String, options: List<Option>, scrollTo: Int = 0, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    val selectedIndex = options.indexOfFirst { it.selected && !it.header }.coerceAtLeast(0)
    Dialog(onDismissRequest = onDismiss) {
        com.streambox.app.remote.RemoteNav.Hook()
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xF21A1D24)).padding(vertical = 14.dp)
        ) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 10.dp))
            if (options.isEmpty()) Text("Seçenek yok", color = Color(0xFF9AA0A6), modifier = Modifier.padding(20.dp))
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0))
            LazyColumn(Modifier.height((options.size * 52).coerceAtMost(420).dp), state = listState) {
                itemsIndexed(options) { i, o ->
                    if (o.header) {
                        Text(o.title.uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                            modifier = Modifier.padding(20.dp, 12.dp, 20.dp, 4.dp))
                    } else {
                        val src = remember { MutableInteractionSource() }
                        val focused by src.collectIsFocusedAsState()
                        Row(
                            Modifier.fillMaxWidth()
                                .then(if (i == selectedIndex) Modifier.focusRequester(first) else Modifier)
                                .background(if (focused) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable(interactionSource = src, indication = null) { o.onClick(); onDismiss() }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(o.title, color = if (o.selected) Accent else Color.White, fontSize = 15.sp,
                                    fontWeight = if (o.selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                o.subtitle?.let { Text(it, color = Color(0xFF9AA0A6), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                            if (o.selected) Icon(Icons.Default.Check, null, tint = Accent)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
