package com.streambox.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Uygulama ikonu seçenekleri (manifestteki activity-alias'lar). */
enum class AppIcon(val alias: String, val label: String, val logo: Int?) {
    DL("LauncherDL", "DL Player", R.drawable.logo_dl),
    PURPLE("LauncherPurple", "Mor", R.drawable.logo_purple),
    GREEN("LauncherGreen", "Yeşil", R.drawable.logo_green),
    CLASSIC("LauncherClassic", "Klasik", null);

    companion object {
        var current by mutableStateOf(PURPLE)
            private set

        fun init(context: Context) {
            current = runCatching { valueOf(context.getSharedPreferences("settings", 0).getString("appIcon", "PURPLE")!!) }
                .getOrDefault(PURPLE)
        }

        /** Ana ekran ikonunu değiştirir: seçilen alias açılır, diğerleri kapanır. */
        fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            entries.forEach { e ->
                val cn = ComponentName(context.packageName, "${context.packageName}.${e.alias}")
                pm.setComponentEnabledSetting(
                    cn,
                    if (e == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            current = icon
            context.getSharedPreferences("settings", 0).edit().putString("appIcon", icon.name).apply()
        }
    }
}
