package com.streambox.app

import android.app.Application
import com.streambox.app.iptv.IptvStore
import com.streambox.app.nuvio.NuvioRepoManager
import com.streambox.app.plugins.CsBridge
import com.streambox.app.plugins.PluginManager
import com.streambox.app.plugins.RepoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StreamBoxApp : Application(), coil.ImageLoaderFactory {
    companion object {
        /** Uygulama bağlamı (WebView gerektiren uyumluluk sınıfları için). */
        @Volatile var instance: StreamBoxApp? = null
            private set
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Afişler de eklentilerle aynı ağ ayarlarıyla (DoH, SSL, tarayıcı kimliği) indirilir. */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .okHttpClient {
            Net.client.newBuilder().addInterceptor { chain ->
                val r = chain.request()
                chain.proceed(if (r.header("User-Agent") == null) r.newBuilder().header("User-Agent", Net.USER_AGENT).build() else r)
            }.build()
        }
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()

    /** Çökme olursa nedenini dosyaya yazar; bir sonraki açılışta Günlük'te gösterilir. */
    private fun installCrashLog() {
        val f = java.io.File(filesDir, "last_crash.txt")
        if (f.exists()) {
            runCatching { AppLog.e("Önceki açılışta uygulama çöktü:\n" + f.readText().take(3000)) }
            f.delete()
        }
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { f.writeText("[${t.name}] " + android.util.Log.getStackTraceString(e)) }
            prev?.uncaughtException(t, e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashLog()
        Settings.init(this)
        AppIcon.init(this)
        com.streambox.app.cf.CfVerify.init(this)
        Profiles.init(this)
        WatchHistory.init(this)
        registerActivityLifecycleCallbacks(ActivityTracker)
        RepoManager.init(this)
        PluginManager.init(this)
        NuvioRepoManager.init(this)
        IptvStore.init(this)
        com.streambox.app.catalog.CatalogManager.init(this)
        com.streambox.app.sites.SiteManager.init(this)
        if (!BuildConfig.SITES_ONLY) {
            runCatching { CsBridge.init(this) }.onFailure { AppLog.e("CloudStream başlatılamadı", it) }
            scope.launch { PluginManager.loadAll(this@StreamBoxApp) }
        }
        com.streambox.app.remote.RemoteServer.startIfEnabled(this)
    }
}

/** Öndeki etkinliği izler (telefon kumandası tuşları buraya gönderilir). */
object ActivityTracker : android.app.Application.ActivityLifecycleCallbacks {
    @Volatile var top: java.lang.ref.WeakReference<android.app.Activity>? = null
    /** Uygulamanın bir ekranı şu an önde mi. */
    @Volatile var foreground = false
    override fun onActivityResumed(a: android.app.Activity) { top = java.lang.ref.WeakReference(a); foreground = true }
    override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
    override fun onActivityStarted(a: android.app.Activity) {}
    override fun onActivityPaused(a: android.app.Activity) { if (top?.get() === a) foreground = false }
    override fun onActivityStopped(a: android.app.Activity) {}
    override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
    override fun onActivityDestroyed(a: android.app.Activity) { if (top?.get() === a) top = null }
}
