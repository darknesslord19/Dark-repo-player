package com.streambox.app.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.streambox.app.AppLog
import com.streambox.app.Net

data class PlaySub(val lang: String, val url: String)

/** Oynatılacak yayın. */
data class PlayItem(
    val title: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val kind: Kind = Kind.AUTO,
    val subtitles: List<PlaySub> = emptyList(),
    val live: Boolean = false,
    val label: String? = null,   // kaynak listesinde görünen ad (ör. "Vidmoly · 1080p")
) {
    enum class Kind { AUTO, HLS, DASH, PROGRESSIVE }
}

/** Kontrollerin okuduğu oynatıcı durumu. */
class PlayerUiState {
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(true)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var buffered by mutableLongStateOf(0L)
    var controlsVisible by mutableStateOf(true)
    var locked by mutableStateOf(false)
    var resizeMode by mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    var speed by mutableFloatStateOf(1f)
    var error by mutableStateOf<String?>(null)
    var index by mutableIntStateOf(0)
    var tracks by mutableStateOf(Tracks.EMPTY)
    var inPip by mutableStateOf(false)
    var videoSize by mutableStateOf("")
    /** Kontroller en son ne zaman kullanıldı (otomatik gizleme için). */
    var lastInteraction by mutableLongStateOf(System.currentTimeMillis())
}

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    companion object {
        @Volatile private var pendingList: List<PlayItem> = emptyList()
        @Volatile private var pendingIndex: Int = 0

        /** Tek yayın. */
        fun start(context: Context, item: PlayItem) = start(context, listOf(item), 0)

        /** Kaynak/kanal listesiyle başlat: oynatıcı içinden diğerlerine geçilebilir. */
        fun start(context: Context, items: List<PlayItem>, index: Int) {
            pendingList = items
            pendingIndex = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            context.startActivity(Intent(context, PlayerActivity::class.java))
        }
    }

    val ui = PlayerUiState()
    var items: List<PlayItem> = emptyList()
        private set
    var player: ExoPlayer? = null
        private set
    private lateinit var audio: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        items = pendingList
        if (items.isEmpty()) { finish(); return }
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        createPlayer()
        playIndex(pendingIndex)
        setContent { PlayerScreen(this) }
    }

    private fun createPlayer() {
        val p = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        p.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true
        )
        // Tercih edilen ses / altyazı dili
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(com.streambox.app.Settings.audioLang.ifBlank { null })
            .setPreferredTextLanguage(com.streambox.app.Settings.subLang.ifBlank { null })
            .build()
        ui.resizeMode = com.streambox.app.Settings.resizeMode
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { ui.playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                ui.buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) { ui.error = null; ui.duration = p.duration.coerceAtLeast(0) }
                if (state == Player.STATE_ENDED && ui.index < items.size - 1 && !current().live) {
                    // dizi/kanal listesinde sonrakine geçme yerine sadece kontrolleri göster
                    showControls()
                }
            }
            override fun onTracksChanged(tracks: Tracks) { ui.tracks = tracks }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                ui.videoSize = if (videoSize.height > 0) "${videoSize.height}p" else ""
            }
            override fun onPlayerError(error: PlaybackException) {
                val item = current()
                AppLog.e("Oynatma hatası: ${item.title} → ${error.errorCodeName}", error)
                if (item.kind == PlayItem.Kind.AUTO && lastMime == null &&
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                ) {
                    load(item.copy(kind = PlayItem.Kind.HLS), 0); return
                }
                ui.error = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Sunucu yayını reddetti (HTTP hatası)"
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Bağlantı kurulamadı"
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "Yayın biçimi desteklenmiyor"
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Cihaz bu videoyu çözemiyor"
                    else -> "Yayın açılamadı (${error.errorCodeName})"
                }
                showControls()
            }
        })
        player = p
    }

    fun current(): PlayItem = items[ui.index]

    private var lastMime: String? = null

    fun playIndex(i: Int) {
        if (i !in items.indices) return
        ui.index = i
        load(items[i], 0)
    }

    private fun load(it: PlayItem, startMs: Long) {
        val p = player ?: return
        ui.error = null
        ui.buffering = true
        val headers = it.headers.filterKeys { k -> !k.equals("User-Agent", true) }
        val ua = it.headers.entries.firstOrNull { e -> e.key.equals("User-Agent", true) }?.value
            ?: if (it.live) com.streambox.app.Settings.iptvUserAgent else Net.USER_AGENT
        val http = OkHttpDataSource.Factory(Net.client).setUserAgent(ua).setDefaultRequestProperties(headers)
        val factory = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http))

        val lower = it.url.substringBefore('?').lowercase()
        val mime = when (it.kind) {
            PlayItem.Kind.HLS -> MimeTypes.APPLICATION_M3U8
            PlayItem.Kind.DASH -> MimeTypes.APPLICATION_MPD
            PlayItem.Kind.PROGRESSIVE -> null
            PlayItem.Kind.AUTO -> when {
                lower.endsWith(".m3u8") || lower.endsWith(".m3u") || it.url.contains("m3u8", true) -> MimeTypes.APPLICATION_M3U8
                lower.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
                else -> null
            }
        }
        lastMime = mime
        val subs = it.subtitles.map { s ->
            val sm = when {
                s.url.contains(".vtt", true) -> MimeTypes.TEXT_VTT
                s.url.contains(".ass", true) || s.url.contains(".ssa", true) -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                .setMimeType(sm).setLanguage(s.lang).setLabel(s.lang).build()
        }
        val media = MediaItem.Builder().setUri(it.url)
            .apply { if (mime != null) setMimeType(mime) }
            .setSubtitleConfigurations(subs).build()
        p.setMediaSource(factory.createMediaSource(media), startMs)
        p.prepare()
        p.playWhenReady = true
    }

    fun retry() = load(current(), player?.currentPosition ?: 0)

    fun togglePlay() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
        if (p.isPlaying) p.pause() else p.play()
        touch()
    }

    fun seekBy(ms: Long) {
        val p = player ?: return
        val target = (p.currentPosition + ms).coerceIn(0, if (p.duration > 0) p.duration else Long.MAX_VALUE)
        p.seekTo(target)
        ui.position = target
        touch()
    }

    fun setSpeed(s: Float) { player?.setPlaybackSpeed(s); ui.speed = s }

    fun cycleResize(): String {
        val modes = listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT to "Sığdır",
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Yakınlaştır",
            AspectRatioFrameLayout.RESIZE_MODE_FILL to "Uzat",
        )
        val next = modes[(modes.indexOfFirst { it.first == ui.resizeMode } + 1) % modes.size]
        ui.resizeMode = next.first
        return next.second
    }

    /** Ses düzeyini (0..1) değiştirir; yeni değeri döndürür. */
    fun changeVolume(delta: Float): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
        val v = (cur + delta).coerceIn(0f, 1f)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).toInt(), 0)
        return v
    }

    /** Ekran parlaklığını (0..1) değiştirir. */
    fun changeBrightness(delta: Float): Float {
        val lp = window.attributes
        val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
        val v = (cur + delta).coerceIn(0.02f, 1f)
        lp.screenBrightness = v
        window.attributes = lp
        return v
    }

    fun touch() { ui.lastInteraction = System.currentTimeMillis() }
    fun showControls() { ui.controlsVisible = true; touch() }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature("android.software.picture_in_picture")
        ) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> { togglePlay(); showControls(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(30_000); showControls(); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-30_000); showControls(); return true }
                KeyEvent.KEYCODE_BACK -> if (ui.controlsVisible && !ui.locked && ui.playing) { ui.controlsVisible = false; return true }
            }
            // TV kumandası: kontroller gizliyken
            if (!ui.controlsVisible && !ui.locked) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(-10_000); return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(10_000); return true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN -> { showControls(); return true }
                }
            } else touch()
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == true) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ui.inPip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isInPictureInPictureMode) player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
