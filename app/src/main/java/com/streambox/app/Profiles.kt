package com.streambox.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Bir izleyici profili. [avatar]: hazır avatar sırası; [image]: kullanıcının seçtiği fotoğraf (dosya yolu). */
data class Profile(val id: String, val name: String, val avatar: Int, val image: String? = null)

object Profiles {
    const val DEFAULT_ID = "main"
    const val MAX = 10

    val list = mutableStateListOf<Profile>()
    /** Açılışta "Kim izliyor?" ekranı gösterilsin mi. */
    var askOnStart by mutableStateOf(false)
        private set
    var currentId by mutableStateOf(DEFAULT_ID)
        private set
    /** Bu oturumda profil seçildi mi (seçim ekranı bir kez gösterilir). */
    var chosen by mutableStateOf(false)

    val current: Profile get() = list.firstOrNull { it.id == currentId } ?: list.first()

    private lateinit var appContext: Context
    private val prefs get() = appContext.getSharedPreferences("profiles", 0)

    fun init(context: Context) {
        appContext = context.applicationContext
        list.clear()
        val arr = JSONArray(prefs.getString("list", "[]"))
        for (i in 0 until arr.length()) arr.getJSONObject(i).let {
            list += Profile(it.getString("id"), it.getString("name"), it.optInt("avatar"), it.optString("image").ifBlank { null })
        }
        if (list.isEmpty()) list += Profile(DEFAULT_ID, "Ben", 0)
        askOnStart = prefs.getBoolean("askOnStart", false)
        currentId = prefs.getString("current", DEFAULT_ID)?.takeIf { id -> list.any { it.id == id } } ?: list.first().id
        chosen = !askOnStart
    }

    private fun save() {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("avatar", it.avatar).put("image", it.image ?: "")) }
        prefs.edit().putString("list", arr.toString()).putString("current", currentId).putBoolean("askOnStart", askOnStart).apply()
    }

    fun updateAskOnStart(v: Boolean) { askOnStart = v; save() }

    /** Profile geç: izleme geçmişi o profile ait olanla değişir. */
    fun select(p: Profile) {
        currentId = p.id
        chosen = true
        save()
        WatchHistory.init(appContext)
    }

    fun add(name: String, avatar: Int, image: String? = null): Profile? {
        if (list.size >= MAX) return null
        val p = Profile(System.currentTimeMillis().toString(36), name.trim().ifBlank { "Profil ${list.size + 1}" }, avatar, image)
        list += p; save()
        return p
    }

    fun update(p: Profile) {
        val i = list.indexOfFirst { it.id == p.id }
        if (i >= 0) { list[i] = p; save() }
    }

    fun remove(p: Profile) {
        if (list.size <= 1) return
        list.removeAll { it.id == p.id }
        p.image?.let { File(it).delete() }
        appContext.getSharedPreferences("history_${p.id}", 0).edit().clear().apply()
        if (currentId == p.id) select(list.first()) else save()
    }

    /** Galeriden seçilen resmi küçültüp uygulama içine kopyalar. */
    fun importImage(context: Context, uri: Uri, profileId: String): String? = runCatching {
        val src = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, 320, 320, true)
        val dir = File(context.filesDir, "profiles").apply { mkdirs() }
        val f = File(dir, "$profileId-${System.currentTimeMillis()}.jpg")
        f.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        f.absolutePath
    }.onFailure { AppLog.e("Profil resmi alınamadı", it) }.getOrNull()
}
