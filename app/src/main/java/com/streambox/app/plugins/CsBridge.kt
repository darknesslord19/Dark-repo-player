package com.streambox.app.plugins

import android.content.Context
import com.lagradost.cloudstream3.MainAPI
import com.streambox.app.AppLog
import com.streambox.app.Net
import java.lang.ref.WeakReference

/**
 * CloudStream library ile uygulama arasındaki köprü.
 *
 * Library'nin iç adları sürümden sürüme değişebildiği için burada yansıma (reflection)
 * kullanılıyor: bir ad bulunamazsa derleme bozulmaz, sadece günlüğe yazılır.
 */
object CsBridge {

    /** Plugin'lerin registerMainAPI ile eklediği tüm kaynaklar. */
    fun providers(): List<MainAPI> {
        val holder = instanceOf("com.lagradost.cloudstream3.APIHolder") ?: return emptyList()
        for (getter in listOf("getApis", "getAllProviders")) {
            val m = holder.javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 } ?: continue
            val col = m.invoke(holder) as? Collection<*> ?: continue
            val list = synchronized(col) { col.toList() }.filterIsInstance<MainAPI>()
            if (list.isNotEmpty() || getter == "getAllProviders") return list.distinctBy { it.name + it.mainUrl }
        }
        return emptyList()
    }

    /** Library'ye uygulama bağlamını (Context) ve HTTP istemcisini ver. */
    fun init(context: Context) {
        val app = context.applicationContext
        var contextSet = false
        for (cn in listOf(
            "com.lagradost.api.ContextHelperKt",
            "com.lagradost.api.ContextHelper_androidKt",
            "com.lagradost.api.ContextHelperAndroidKt",
            "com.lagradost.cloudstream3.CloudStreamApp",
            "com.lagradost.cloudstream3.CloudStreamApp\$Companion",
        )) {
            val cls = runCatching { Class.forName(cn) }.getOrNull() ?: continue
            val target: Any? = instanceOf(cn)
            for (m in cls.methods.filter { (it.name == "setContext" || it.name == "setContextInternal") && it.parameterCount == 1 }) {
                val p = m.parameterTypes[0]
                val arg: Any = if (WeakReference::class.java.isAssignableFrom(p)) WeakReference(app) else app
                if (!p.isInstance(arg)) continue
                runCatching { m.invoke(target, arg); contextSet = true }
                    .onFailure { AppLog.e("setContext başarısız: $cn", it) }
            }
        }
        AppLog.i(if (contextSet) "CloudStream bağlamı ayarlandı" else "CloudStream setContext bulunamadı (çoğu plugin için sorun değil)")

        // NiceHttp "app" nesnesine kendi OkHttp istemcimizi ver
        for (cn in listOf("com.lagradost.cloudstream3.MainAPIKt", "com.lagradost.cloudstream3.MainActivityKt")) {
            val cls = runCatching { Class.forName(cn) }.getOrNull() ?: continue
            val getApp = cls.methods.firstOrNull { it.name == "getApp" && it.parameterCount == 0 } ?: continue
            val requests = runCatching { getApp.invoke(null) }.getOrNull() ?: continue
            val setter = requests.javaClass.methods.firstOrNull { it.name == "setBaseClient" && it.parameterCount == 1 }
            if (setter != null) {
                runCatching { setter.invoke(requests, Net.client) }
                    .onSuccess { AppLog.i("HTTP istemcisi bağlandı") }
                    .onFailure { AppLog.e("baseClient ayarlanamadı", it) }
            }
            break
        }
    }

    /** Kotlin object (INSTANCE) ya da companion nesnesini döndürür. */
    private fun instanceOf(className: String): Any? = runCatching {
        val cls = Class.forName(className)
        runCatching { cls.getField("INSTANCE").get(null) }.getOrNull()
            ?: runCatching { cls.getField("Companion").get(null) }.getOrNull()
    }.getOrNull()
}
