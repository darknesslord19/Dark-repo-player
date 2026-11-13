package com.streambox.app

import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.streambox.app.ui.isWide
import com.streambox.app.ui.SettingsPageScreen
import com.streambox.app.ui.RepoPageScreen
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import com.streambox.app.ui.SideNavBar
import com.streambox.app.ui.NavTab
import com.streambox.app.ui.FloatingNavBar
import com.streambox.app.ui.CategoryScreen
import com.streambox.app.ui.Bg
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.streambox.app.plugins.RepoManager
import com.streambox.app.ui.AppTheme
import com.streambox.app.ui.CsDetailScreen
import com.streambox.app.ui.ExtensionsScreen
import com.streambox.app.ui.HomeScreen
import com.streambox.app.ui.IptvListScreen
import com.streambox.app.ui.IptvScreen
import com.streambox.app.ui.Nav
import com.streambox.app.ui.Route
import com.streambox.app.ui.SearchScreen
import com.streambox.app.ui.SettingsScreen
import com.streambox.app.ui.TmdbDetailScreen
import kotlinx.coroutines.launch

private val tabs = listOf(
    NavTab(Route.Home, "Ana Sayfa", Icons.Default.Home),
    NavTab(Route.Search, "Ara", Icons.Default.Search),
    NavTab(Route.Iptv, "Darknes IPTV", Icons.Default.LiveTv),
    NavTab(Route.Extensions, "Eklentiler", Icons.Default.Extension),
    NavTab(Route.Settings, "Ayarlar", Icons.Default.Settings),
)

class MainActivity : androidx.appcompat.app.AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.lagradost.cloudstream3.CommonActivity.setActivityInstance(this)
        handleIntent(intent)
        setContent { AppTheme { com.streambox.app.remote.RemoteFocus.Register(this); Root() } }
    }

    override fun onResume() {
        super.onResume()
        com.lagradost.cloudstream3.CommonActivity.setActivityInstance(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** cloudstreamrepo://… bağlantısıyla açıldıysa repoyu ekle. */
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        if (!data.startsWith("cloudstreamrepo://")) return
        (application as StreamBoxApp).scope.launch {
            runCatching { RepoManager.addRepo(this@MainActivity, data) }
                .onFailure { AppLog.e("Bağlantıdan repo eklenemedi", it) }
        }
        Nav.tab(Route.Extensions)
    }
}

@Composable
private fun isTv(): Boolean {
    val ctx = LocalContext.current
    val ui = ctx.getSystemService(UiModeManager::class.java)
    return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION || LocalConfiguration.current.screenWidthDp >= 840
}

@Composable
private fun Root() {
    com.streambox.app.remote.RemoteNav.Hook()
    // Profilli giriş açıksa önce "Kim izliyor?"
    if (!Profiles.chosen) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Bg)) { com.streambox.app.ui.ProfilePickerScreen() }
        return
    }
    val route = Nav.current
    BackHandler(enabled = Nav.stack.size > 1) { Nav.back() }
    // Alt sekmelerden birindeyken geri tuşu ana sayfaya döner
    BackHandler(enabled = Nav.stack.size == 1 && route != Route.Home) { Nav.tab(Route.Home) }

    val rootTab = Nav.stack.first()
    val tv = isTv()

    val content: @Composable (Modifier) -> Unit = { m ->
        // Ana sayfa vitrini durum çubuğunun altına uzanır; diğer ekranlar onun altından başlar
        Box(if (route == Route.Home) m else m.statusBarsPadding()) {
            when (route) {
                Route.Home -> HomeScreen()
                Route.Search -> SearchScreen()
                Route.Iptv -> IptvScreen()
                Route.Extensions -> ExtensionsScreen()
                Route.Settings -> SettingsScreen()
                is Route.TmdbDetail -> TmdbDetailScreen(route.item)
                is Route.CsDetail -> CsDetailScreen(route.card)
                is Route.IptvList -> IptvListScreen(route.playlist)
                is Route.Category -> CategoryScreen(route)
                is Route.SettingsPage -> SettingsPageScreen(route.page)
                is Route.RepoPage -> RepoPageScreen(route)
            }
        }
    }

    // Yatay ekranda kanal listesi tam ekran açılır (kendi kategori menüsü var)
    val immersive = route is Route.IptvList && isWide()
    val insets = Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout))

    if (immersive) {
        Box(Modifier.fillMaxSize().background(Bg).statusBarsPadding().then(insets)) { content(Modifier.fillMaxSize()) }
    } else if (tv) {
        Row(Modifier.fillMaxSize().background(Bg).then(insets)) {
            Box(Modifier.statusBarsPadding()) { SideNavBar(tabs, rootTab) { Nav.tab(it) } }
            content(Modifier.weight(1f).fillMaxSize())
        }
    } else {
        Box(Modifier.fillMaxSize().background(Bg)) {
            content(Modifier.fillMaxSize())
            FloatingNavBar(tabs, rootTab, { Nav.tab(it) }, Modifier.align(Alignment.BottomCenter))
        }
    }
}
