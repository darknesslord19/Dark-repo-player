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

    override fun onCreate() {
        super.onCreate()
        instance = this
        Settings.init(this)
        AppIcon.init(this)
        WatchHistory.init(this)
        RepoManager.init(this)
        PluginManager.init(this)
        NuvioRepoManager.init(this)
        IptvStore.init(this)
        com.streambox.app.catalog.CatalogManager.init(this)
        runCatching { CsBridge.init(this) }.onFailure { AppLog.e("CloudStream başlatılamadı", it) }
        scope.launch { PluginManager.loadAll(this@StreamBoxApp) }
    }
}
