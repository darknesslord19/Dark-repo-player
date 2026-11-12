package com.streambox.app

import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.streambox.app.ui.Panel
import com.streambox.app.ui.Route
import com.streambox.app.ui.SearchScreen
import com.streambox.app.ui.SettingsScreen
import com.streambox.app.ui.TmdbDetailScreen
import kotlinx.coroutines.launch

private data class Tab(val route: Route, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Route.Home, "Ana Sayfa", Icons.Default.Home),
    Tab(Route.Search, "Ara", Icons.Default.Search),
    Tab(Route.Iptv, "IPTV", Icons.Default.LiveTv),
    Tab(Route.Extensions, "Eklentiler", Icons.Default.Extension),
    Tab(Route.Settings, "Ayarlar", Icons.Default.Settings),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { AppTheme { Root() } }
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
    val route = Nav.current
    BackHandler(enabled = Nav.stack.size > 1) { Nav.back() }
    // Alt sekmelerden birindeyken geri tuşu ana sayfaya döner
    BackHandler(enabled = Nav.stack.size == 1 && route != Route.Home) { Nav.tab(Route.Home) }

    val rootTab = Nav.stack.first()
    val tv = isTv()

    val content: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            when (route) {
                Route.Home -> HomeScreen()
                Route.Search -> SearchScreen()
                Route.Iptv -> IptvScreen()
                Route.Extensions -> ExtensionsScreen()
                Route.Settings -> SettingsScreen()
                is Route.TmdbDetail -> TmdbDetailScreen(route.item)
                is Route.CsDetail -> CsDetailScreen(route.card)
                is Route.IptvList -> IptvListScreen(route.playlist)
            }
        }
    }

    if (tv) {
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            NavigationRail(containerColor = Panel) {
                tabs.forEach { t ->
                    NavigationRailItem(
                        selected = rootTab == t.route, onClick = { Nav.tab(t.route) },
                        icon = { Icon(t.icon, t.label) }, label = { Text(t.label) }
                    )
                }
            }
            content(Modifier.weight(1f).fillMaxSize())
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = rootTab == t.route, onClick = { Nav.tab(t.route) },
                            icon = { Icon(t.icon, t.label) }, label = { Text(t.label) }
                        )
                    }
                }
            }
        ) { pad -> content(Modifier.fillMaxSize().padding(pad)) }
    }
}
