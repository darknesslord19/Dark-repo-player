package com.streambox.app.remote

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.streambox.app.ActivityTracker
import com.streambox.app.Backup
import com.streambox.app.BuildConfig
import com.streambox.app.MainActivity
import com.streambox.app.Tmdb
import com.streambox.app.TmdbItem
import com.streambox.app.WatchHistory
import com.streambox.app.iptv.IptvStore
import com.streambox.app.iptv.toPlayItem
import com.streambox.app.player.PlayItem
import com.streambox.app.player.PlayerActivity
import com.streambox.app.restartApp
import com.streambox.app.ui.Nav
import com.streambox.app.ui.Route
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Kumanda / senkron API uçları. */
object RemoteApi {
    private val main = Handler(Looper.getMainLooper())

    /** Ana iş parçacığında çalıştırıp sonucu bekler. */
    private fun <T> onMain(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var out: T? = null
        val latch = CountDownLatch(1)
        main.post { try { out = block() } catch (t: Throwable) { com.streambox.app.AppLog.e("Kumanda", t) } finally { latch.countDown() } }
        latch.await(4, TimeUnit.SECONDS)
        return out
    }

    private fun player() = PlayerActivity.current?.get()?.takeIf { !it.isFinishing }

    fun hello(ctx: Context): JSONObject = JSONObject()
        .put("app", "StreamBox").put("device", Backup.deviceName(ctx)).put("version", BuildConfig.VERSION_NAME)
        .put("tv", ctx.packageManager.hasSystemFeature("android.software.leanback"))

    fun handle(ctx: Context, r: Req): Res = when (r.path) {
        "/api/state" -> Res.json(state(ctx).toString())
        "/api/key" -> { key(ctx, r.q("code") ?: ""); Res.ok() }
        "/api/seek" -> {
            val ms = r.q("ms")?.toLongOrNull() ?: 0
            onMain { player()?.player?.seekTo(ms) }; Res.ok()
        }
        "/api/volume" -> {
            val v = r.q("v")?.toIntOrNull() ?: 0
            audio(ctx).setStreamVolume(AudioManager.STREAM_MUSIC, v, AudioManager.FLAG_SHOW_UI); Res.ok()
        }
        "/api/playlists" -> Res.json(playlists().toString())
        "/api/playlists/add" -> addPlaylist(r)
        "/api/playlists/remove" -> {
            val p = IptvStore.playlists.firstOrNull { it.id == r.q("id") } ?: return Res.err("Hesap yok")
            onMain { IptvStore.remove(p) }; Res.ok()
        }
        "/api/channels" -> Res.json(channels(r.q("pl"), r.q("q") ?: "", r.q("group")).toString())
        "/api/play/channel" -> playChannel(ctx, r.q("pl") ?: "", r.q("url") ?: "")
        "/api/play/url" -> {
            val url = r.q("url")?.trim().orEmpty()
            if (!url.startsWith("http")) return Res.err("Geçerli bir adres gir")
            start(ctx, listOf(PlayItem(r.q("title")?.ifBlank { null } ?: "Telefondan gönderildi", url, emptyMap())), 0); Res.ok()
        }
        "/api/search" -> {
            val q = r.q("q").orEmpty()
            val list = runBlocking { runCatching { Tmdb.search(q) }.getOrDefault(emptyList()) }
            Res.json(JSONArray(list.map { tmdbJson(it) }).toString())
        }
        "/api/open/tmdb" -> {
            val o = JSONObject(r.text)
            val item = TmdbItem(o.getInt("id"), o.getString("type"), o.getString("title"), o.optString("poster").ifBlank { null },
                o.optString("backdrop").ifBlank { null }, o.optString("overview"), o.optString("year").ifBlank { null },
                o.optDouble("rating").takeIf { !it.isNaN() && it > 0 })
            front(ctx) { Nav.go(Route.TmdbDetail(item)) }; Res.ok()
        }
        "/api/tvsearch" -> { val q = r.q("q").orEmpty(); front(ctx) { Nav.search(q) }; Res.ok() }
        "/api/history" -> Res.json(JSONArray(WatchHistory.entries.toList().map {
            JSONObject().put("key", it.key).put("title", it.title).put("subtitle", it.subtitle ?: "").put("poster", it.poster ?: "")
                .put("progress", it.progress.toDouble()).put("kind", it.kind)
        }).toString())
        "/api/history/open" -> {
            val e = WatchHistory.entries.firstOrNull { it.key == r.q("key") } ?: return Res.err("Kayıt yok")
            front(ctx) { com.streambox.app.ui.openHistory(e) }; Res.ok()
        }
        "/api/backup" -> Res(200, "application/json; charset=utf-8", Backup.export(ctx).toString().toByteArray(),
            mapOf("Content-Disposition" to "attachment; filename=\"streambox-yedek.json\""))
        "/api/restore" -> {
            val data = JSONObject(r.text)
            Backup.restore(ctx, data)
            main.postDelayed({ restartApp(ctx) }, 1500)
            Res.ok()
        }
        else -> Res.err("Bilinmeyen istek", 404)
    }

    private fun audio(ctx: Context) = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun state(ctx: Context): JSONObject {
        val a = audio(ctx)
        val o = JSONObject()
            .put("device", Backup.deviceName(ctx))
            .put("volume", a.getStreamVolume(AudioManager.STREAM_MUSIC))
            .put("maxVolume", a.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            .put("muted", a.isStreamMute(AudioManager.STREAM_MUSIC))
            .put("profile", com.streambox.app.Profiles.current.name)
        val p = player()
        if (p != null) onMain {
            val ex = p.player
            val item = p.items.getOrNull(p.ui.index)
            if (item != null) {
                val meta = item.meta?.let { runCatching { JSONObject(it) }.getOrNull() }
                o.put("playing", JSONObject()
                    .put("title", item.title)
                    .put("subtitle", meta?.optString("subtitle")?.ifBlank { null } ?: item.label ?: "")
                    .put("poster", meta?.optString("poster") ?: "")
                    .put("position", ex?.currentPosition ?: 0)
                    .put("duration", (ex?.duration ?: 0).let { if (it < 0) 0 else it })
                    .put("isPlaying", ex?.isPlaying == true)
                    .put("live", item.live)
                    .put("index", p.ui.index).put("count", p.items.size))
            }
        }
        return o
    }

    private val keyMap = mapOf(
        "up" to KeyEvent.KEYCODE_DPAD_UP, "down" to KeyEvent.KEYCODE_DPAD_DOWN,
        "left" to KeyEvent.KEYCODE_DPAD_LEFT, "right" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "ok" to KeyEvent.KEYCODE_DPAD_CENTER, "back" to KeyEvent.KEYCODE_BACK, "menu" to KeyEvent.KEYCODE_MENU,
    )

    private fun key(ctx: Context, code: String) {
        val a = audio(ctx)
        when (code) {
            "volup" -> a.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "voldown" -> a.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "mute" -> a.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            "home" -> onMain { player()?.finish(); Nav.tab(Route.Home) }
            "playpause" -> onMain { player()?.togglePlay() ?: send(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
            "ff" -> onMain { player()?.seekBy(10_000) }
            "rew" -> onMain { player()?.seekBy(-10_000) }
            "chup", "chdown" -> onMain {
                val p = player()
                if (p != null) p.playIndex((p.ui.index + if (code == "chup") -1 else 1).mod(p.items.size))
                else send(if (code == "chup") KeyEvent.KEYCODE_PAGE_UP else KeyEvent.KEYCODE_PAGE_DOWN)
            }
            else -> keyMap[code]?.let { k -> onMain { send(k) } }
        }
    }

    /** Öndeki ekrana tuş basışı gönderir (kumandanın tuşuna basılmış gibi). */
    private fun send(code: Int) {
        val act = ActivityTracker.top?.get() ?: return
        val now = SystemClock.uptimeMillis()
        act.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        act.dispatchKeyEvent(KeyEvent(now, now + 30, KeyEvent.ACTION_UP, code, 0))
    }

    /** Uygulamayı öne getirip ana ekranda bir işlem yapar (oynatıcı açıksa kapatır). */
    private fun front(ctx: Context, block: () -> Unit) {
        onMain {
            player()?.finish()
            block()
            if (ActivityTracker.top?.get() !is MainActivity) runCatching {
                ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
        }
    }

    private fun start(ctx: Context, items: List<PlayItem>, index: Int) {
        onMain { PlayerActivity.start(ActivityTracker.top?.get() ?: ctx, items, index) }
    }

    private fun tmdbJson(t: TmdbItem) = JSONObject().put("id", t.id).put("type", t.type).put("title", t.title)
        .put("poster", t.poster ?: "").put("backdrop", t.backdrop ?: "").put("overview", t.overview)
        .put("year", t.year ?: "").put("rating", t.rating ?: 0.0)

    private fun playlists(): JSONArray = JSONArray(IptvStore.playlists.toList().map { p ->
        runCatching { runBlocking { IptvStore.loadStats(p) } }
        val st = IptvStore.stats[p.id]
        JSONObject().put("id", p.id).put("name", p.name).put("url", p.url ?: "")
            .put("channels", st?.channels ?: 0).put("groups", st?.groups ?: 0)
    })

    private fun addPlaylist(r: Req): Res {
        val o = JSONObject(r.text)
        val url = if (o.optString("server").isNotBlank())
            IptvStore.xtreamUrl(o.getString("server"), o.optString("user"), o.optString("pass"))
        else o.optString("url").trim()
        if (!url.startsWith("http")) return Res.err("Geçerli bir adres gir")
        return runCatching { runBlocking { IptvStore.addUrl(o.optString("name"), url) } }
            .fold({ Res.json(JSONObject().put("ok", true).put("id", it.id).toString()) }, { Res.err(it.message ?: "Eklenemedi") })
    }

    private fun channels(plId: String?, q: String, group: String?): JSONObject {
        val pls = IptvStore.playlists.toList().filter { plId.isNullOrBlank() || it.id == plId }
        val out = JSONArray()
        val groups = linkedSetOf<String>()
        for (p in pls) {
            val list = runBlocking { runCatching { IptvStore.channels(p) }.getOrDefault(emptyList()) }
            if (!plId.isNullOrBlank()) list.forEach { groups += it.group }
            list.asSequence()
                .filter { group.isNullOrBlank() || it.group == group }
                .filter { q.isBlank() || it.name.contains(q, true) }
                .take(300 - out.length())
                .forEach { out.put(JSONObject().put("name", it.name).put("logo", it.logo ?: "").put("group", it.group).put("url", it.url).put("pl", p.id)) }
            if (out.length() >= 300) break
        }
        return JSONObject().put("channels", out).put("groups", JSONArray(groups.toList()))
    }

    private fun playChannel(ctx: Context, plId: String, url: String): Res {
        val p = IptvStore.playlists.firstOrNull { it.id == plId } ?: return Res.err("Hesap yok")
        val all = runBlocking { IptvStore.channels(p) }
        val ch = all.firstOrNull { it.url == url } ?: return Res.err("Kanal yok")
        val group = all.filter { it.group == ch.group }
        onMain { IptvStore.markWatched(ch.url) }
        start(ctx, group.map { it.toPlayItem() }, group.indexOf(ch).coerceAtLeast(0))
        return Res.ok()
    }
}
