package com.streambox.app.plugins

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.streambox.app.AppLog
import com.streambox.app.Net
import com.streambox.app.onMain
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/** Cihaza kurulmuş bir .cs3 plugin. */
data class InstalledPlugin(
    val internalName: String,
    val name: String,
    val version: Int,
    val fileName: String,
    val sourceUrl: String?,
    val repoUrl: String?,
    val enabled: Boolean = true,
)

/** Bir plugin'in bu oturumdaki yükleme sonucu. */
data class LoadResult(val ok: Boolean, val message: String, val providers: List<String> = emptyList())

object PluginManager {
    private const val PREFS = "plugins"

    val installed = mutableStateListOf<InstalledPlugin>()
    val results = mutableStateMapOf<String, LoadResult>()

    /** Bu oturumda yüklenmiş dosyalar (aynı dosya iki kez yüklenmesin). */
    private val loadedFiles = mutableSetOf<String>()

    /** Kurulumdan sonra uygulamayı yeniden başlatmak gerekiyor mu? */
    var needsRestart by mutableStateOf(false)

    fun dir(context: Context) = File(context.filesDir, "plugins").apply { mkdirs() }

    fun init(context: Context) {
        val raw = context.getSharedPreferences(PREFS, 0).getString("installed", "[]") ?: "[]"
        val arr = JSONArray(raw)
        installed.clear()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            installed += InstalledPlugin(
                internalName = o.getString("internalName"),
                name = o.optString("name"),
                version = o.optInt("version"),
                fileName = o.getString("fileName"),
                sourceUrl = o.optStringOrNull("sourceUrl"),
                repoUrl = o.optStringOrNull("repoUrl"),
                enabled = o.optBoolean("enabled", true),
            )
        }
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        installed.forEach { p ->
            arr.put(JSONObject().apply {
                put("internalName", p.internalName); put("name", p.name); put("version", p.version)
                put("fileName", p.fileName); put("sourceUrl", p.sourceUrl); put("repoUrl", p.repoUrl)
                put("enabled", p.enabled)
            })
        }
        context.getSharedPreferences(PREFS, 0).edit().putString("installed", arr.toString()).apply()
    }

    fun find(internalName: String) = installed.firstOrNull { it.internalName == internalName }

    /** Açılışta tüm etkin plugin'leri yükler. */
    suspend fun loadAll(context: Context) = withContext(Dispatchers.IO) {
        val list = installed.filter { it.enabled }
        AppLog.i("${list.size} plugin yükleniyor…")
        for (p in list) load(context, p)
        AppLog.i("Plugin yükleme bitti: ${CsBridge.providers().size} kaynak hazır")
    }

    private fun load(context: Context, p: InstalledPlugin) {
        val file = File(dir(context), p.fileName)
        val result = if (!file.exists()) LoadResult(false, "Dosya yok") else loadFile(context, file)
        onMain { results[p.internalName] = result }
        if (result.ok) AppLog.i("✔ ${p.name}: ${result.message}") else AppLog.e("✘ ${p.name}: ${result.message}")
    }

    /**
     * .cs3 dosyasını yükler. CloudStream'in yaptığının aynısı:
     * salt-okunur yap → PathClassLoader → manifest.json → pluginClassName → load().
     */
    private fun loadFile(context: Context, file: File): LoadResult {
        if (!loadedFiles.add(file.absolutePath)) return LoadResult(true, "Zaten yüklü")
        return try {
            // Android 14+: yazılabilir dex dosyaları yüklenemez
            file.setReadOnly()

            val manifest = ZipFile(file).use { zip ->
                if (zip.getEntry("classes.dex") == null) error("classes.dex yok, geçerli bir .cs3 değil")
                val e = zip.getEntry("manifest.json") ?: error("manifest.json yok")
                JSONObject(zip.getInputStream(e).bufferedReader().readText())
            }
            val className = manifest.optStringOrNull("pluginClassName") ?: error("pluginClassName yok")

            val before = CsBridge.providers().toSet()
            val loader = PathClassLoader(file.absolutePath, context.classLoader)
            val instance = loader.loadClass(className).getDeclaredConstructor().newInstance()

            setFilename(instance, file.absolutePath)

            when (instance) {
                is Plugin -> {
                    if (manifest.optBoolean("requiresResources", false)) {
                        instance.resources = loadResources(context, file)
                    }
                    instance.load(context)
                }
                is BasePlugin -> instance.load()
                else -> error("$className bir CloudStream plugin'i değil")
            }

            val added = (CsBridge.providers() - before).map(MainAPI::name)
            LoadResult(true, if (added.isEmpty()) "yüklendi (kaynak eklemedi)" else added.joinToString(), added)
        } catch (t: Throwable) {
            loadedFiles.remove(file.absolutePath)
            AppLog.e("Yükleme hatası: ${file.name}", t)
            LoadResult(false, AppLog.describe(t))
        }
    }

    private fun setFilename(instance: Any, path: String) {
        var c: Class<*>? = instance.javaClass
        while (c != null) {
            val f = runCatching { c.getDeclaredField("filename") }.getOrNull()
            if (f != null) { f.isAccessible = true; runCatching { f.set(instance, path) }; return }
            c = c.superclass
        }
    }

    @Suppress("DEPRECATION")
    private fun loadResources(context: Context, file: File): Resources? = runCatching {
        val am = AssetManager::class.java.getDeclaredConstructor().newInstance()
        AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(am, file.absolutePath)
        Resources(am, context.resources.displayMetrics, context.resources.configuration)
    }.onFailure { AppLog.e("Plugin kaynakları yüklenemedi", it) }.getOrNull()

    private fun safeName(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /** Repodan plugin kurar veya günceller. */
    suspend fun install(context: Context, rp: RepoPlugin) {
        val fileName = safeName(rp.internalName) + ".cs3"
        val target = File(dir(context), fileName)
        val wasLoaded = loadedFiles.contains(target.absolutePath)
        AppLog.i("İndiriliyor: ${rp.name} v${rp.version}")
        Net.download(rp.url, target)
        val entry = InstalledPlugin(rp.internalName, rp.name, rp.version, fileName, rp.url, rp.repoUrl)
        onMain {
            installed.removeAll { it.internalName == rp.internalName }
            installed += entry
            save(context)
        }
        if (wasLoaded) {
            // Eski sürüm bellekte; yenisi ancak yeniden başlatınca devreye girer
            onMain { needsRestart = true; results[rp.internalName] = LoadResult(true, "Güncellendi, yeniden başlat") }
        } else withContext(Dispatchers.IO) { load(context, entry) }
    }

    /** Telefondaki bir .cs3 dosyasını kurar. */
    suspend fun installLocal(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val display = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "plugin.cs3"
        val internal = safeName(display.substringBeforeLast('.'))
        val fileName = "$internal.cs3"
        val target = File(dir(context), fileName)
        if (target.exists()) { target.setWritable(true); target.delete() }
        context.contentResolver.openInputStream(uri)!!.use { i -> target.outputStream().use { i.copyTo(it) } }
        val name = runCatching {
            ZipFile(target).use { z -> JSONObject(z.getInputStream(z.getEntry("manifest.json")).bufferedReader().readText()).optString("name") }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: internal
        val entry = InstalledPlugin(internal, name, 0, fileName, null, null)
        onMain {
            installed.removeAll { it.internalName == internal }
            installed += entry
            save(context)
        }
        load(context, entry)
    }

    fun setEnabled(context: Context, p: InstalledPlugin, enabled: Boolean) {
        val i = installed.indexOf(p)
        if (i >= 0) { installed[i] = p.copy(enabled = enabled); save(context); needsRestart = true }
    }

    fun uninstall(context: Context, p: InstalledPlugin) {
        installed.remove(p)
        results.remove(p.internalName)
        File(dir(context), p.fileName).apply { setWritable(true); delete() }
        save(context)
        needsRestart = true
    }
}
