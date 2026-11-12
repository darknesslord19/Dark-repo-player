package com.streambox.app.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
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
) {
    enum class Kind { AUTO, HLS, DASH, PROGRESSIVE }
}

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    companion object {
        /** Intent ile büyük veri taşımamak için. */
        @Volatile var pending: PlayItem? = null

        fun start(context: Context, item: PlayItem) {
            pending = item
            context.startActivity(Intent(context, PlayerActivity::class.java))
        }
    }

    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private var item: PlayItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        item = pending
        if (item == null) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        view = PlayerView(this).apply {
            setShowSubtitleButton(true)
            setShowNextButton(false)
            setShowPreviousButton(false)
            keepScreenOn = true
            setBackgroundColor(android.graphics.Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        setContentView(view)
        view.requestFocus()
        play(item!!)
    }

    private fun play(it: PlayItem) {
        val headers = it.headers.filterKeys { k -> !k.equals("User-Agent", true) }
        val ua = it.headers.entries.firstOrNull { e -> e.key.equals("User-Agent", true) }?.value ?: Net.USER_AGENT
        val http = OkHttpDataSource.Factory(Net.client)
            .setUserAgent(ua)
            .setDefaultRequestProperties(headers)
        val dataSource = DefaultDataSource.Factory(this, http)

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
        val subs = it.subtitles.map { s ->
            val sm = when {
                s.url.contains(".vtt", true) -> MimeTypes.TEXT_VTT
                s.url.contains(".ass", true) || s.url.contains(".ssa", true) -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                .setMimeType(sm).setLanguage(s.lang).setLabel(s.lang)
                .setSelectionFlags(0).build()
        }
        val media = MediaItem.Builder()
            .setUri(it.url)
            .apply { if (mime != null) setMimeType(mime) }
            .setSubtitleConfigurations(subs)
            .build()

        player?.release()
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        p.setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true
        )
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                AppLog.e("Oynatma hatası: ${it.title} → ${error.errorCodeName}", error)
                // Uzantıdan tür anlaşılamadıysa bir kez HLS olarak dene
                if (it.kind == PlayItem.Kind.AUTO && mime == null &&
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                ) {
                    play(it.copy(kind = PlayItem.Kind.HLS)); return
                }
                Toast.makeText(this@PlayerActivity, "Yayın açılamadı: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        })
        view.player = p
        p.setMediaItem(media)
        p.prepare()
        p.playWhenReady = true
        player = p
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // TV kumandası: tuşa basınca kontrolleri göster
        if (event.action == KeyEvent.ACTION_DOWN && !view.isControllerFullyVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> { view.showController(); return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { player?.seekBack(); view.showController(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { player?.seekForward(); view.showController(); return true }
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            player?.let { if (it.isPlaying) it.pause() else it.play() }; return true
        }
        return view.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player?.isPlaying == true &&
            packageManager.hasSystemFeature("android.software.picture_in_picture")
        ) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        view.useController = !isInPictureInPictureMode
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
