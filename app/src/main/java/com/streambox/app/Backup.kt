package com.streambox.app

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Yedekleme / geri yükleme ve cihazlar arası senkron için tek paket:
 * ayarlar, repolar, eklenti listesi, Nuvio, IPTV hesapları (+ dosyadan eklenen listeler),
 * kataloglar, profiller (+ resimleri) ve izleme geçmişi.
 * Eklenti dosyaları taşınmaz; geri yüklenince kaynaklarından yeniden indirilir.
 */
object Backup {
    /** Taşınmayan tercih dosyaları (kumanda PIN'i, WebView vb.). */
    private val skip = setOf("remote", "WebViewChromiumPrefs")

    private fun prefsDir(context: Context) = File(context.applicationInfo.dataDir, "shared_prefs")

    fun export(context: Context): JSONObject {
        val prefs = JSONObject()
        prefsDir(context).listFiles()?.filter { it.name.endsWith(".xml") }?.forEach { f ->
            val name = f.name.removeSuffix(".xml")
            if (name in skip || name.startsWith("androidx") || name.startsWith("com.google")) return@forEach
            val o = JSONObject()
            context.getSharedPreferences(name, 0).all.forEach { (k, v) ->
                val t = when (v) {
                    is String -> "s"; is Int -> "i"; is Long -> "l"; is Float -> "f"; is Boolean -> "b"
                    is Set<*> -> "ss"; else -> return@forEach
                }
                o.put(k, JSONObject().put("t", t).put("v", if (v is Set<*>) JSONArray(v.toList()) else v))
            }
            prefs.put(name, o)
        }
        val files = JSONObject()
        // Dosyadan eklenen IPTV listeleri (adresi olmayanlar) ve profil resimleri
        com.streambox.app.iptv.IptvStore.playlists.filter { it.url == null }.forEach { p ->
            val f = File(context.filesDir, "iptv/${p.id}.m3u")
            if (f.exists() && f.length() < 8_000_000) files.put("iptv/${p.id}.m3u", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
        }
        File(context.filesDir, "profiles").listFiles()?.forEach { f ->
            files.put("profiles/${f.name}", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
        }
        return JSONObject()
            .put("app", "DarknesBox").put("version", 1)
            .put("device", deviceName(context)).put("created", System.currentTimeMillis())
            .put("prefs", prefs).put("files", files)
    }

    /** Paketi uygular. Ardından uygulama yeniden başlatılmalı. */
    fun restore(context: Context, data: JSONObject) {
        require(data.optString("app") in setOf("StreamBox", "DarknesBox")) { "Bu bir DarknesBox yedeği değil" }
        val prefs = data.getJSONObject("prefs")
        prefs.keys().forEach { name ->
            if (name in skip) return@forEach
            val o = prefs.getJSONObject(name)
            val e = context.getSharedPreferences(name, 0).edit().clear()
            o.keys().forEach { k ->
                val item = o.getJSONObject(k)
                when (item.getString("t")) {
                    "s" -> e.putString(k, item.getString("v"))
                    "i" -> e.putInt(k, item.getInt("v"))
                    "l" -> e.putLong(k, item.getLong("v"))
                    "f" -> e.putFloat(k, item.getDouble("v").toFloat())
                    "b" -> e.putBoolean(k, item.getBoolean("v"))
                    "ss" -> e.putStringSet(k, item.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
                }
            }
            e.commit()
        }
        val files = data.optJSONObject("files") ?: JSONObject()
        files.keys().forEach { path ->
            if (path.contains("..")) return@forEach
            val f = File(context.filesDir, path)
            f.parentFile?.mkdirs()
            f.writeBytes(Base64.decode(files.getString(path), Base64.NO_WRAP))
        }
        // Profil resim yolları başka cihazdan geldiyse bu cihazın yoluna çevir
        val pp = context.getSharedPreferences("profiles", 0)
        pp.getString("list", null)?.let { raw ->
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val img = o.optString("image")
                if (img.isNotBlank()) o.put("image", File(File(context.filesDir, "profiles"), File(img).name).absolutePath)
            }
            pp.edit().putString("list", arr.toString()).commit()
        }
        AppLog.i("Yedek geri yüklendi (${prefs.length()} bölüm)")
    }

    /** Kısa özet: kaç repo / eklenti / hesap var. */
    fun summary(data: JSONObject): String {
        val p = data.optJSONObject("prefs") ?: return ""
        fun count(file: String, key: String) = runCatching { JSONArray(p.getJSONObject(file).getJSONObject(key).getString("v")).length() }.getOrDefault(0)
        return listOf(
            "${count("plugins", "installed")} eklenti",
            "${count("iptv", "list")} IPTV hesabı",
            "${count("profiles", "list")} profil",
        ).joinToString(" · ")
    }

    fun deviceName(context: Context): String =
        runCatching { android.provider.Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
}
