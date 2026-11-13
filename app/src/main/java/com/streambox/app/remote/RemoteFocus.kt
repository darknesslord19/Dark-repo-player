package com.streambox.app.remote

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import java.util.WeakHashMap

/** Her ekranın odak yöneticisi: telefon kumandası yön tuşlarını doğrudan buradan uygular. */
object RemoteFocus {
    private val managers = WeakHashMap<Activity, FocusManager>()

    @Composable
    fun Register(activity: Activity) {
        val fm = LocalFocusManager.current
        DisposableEffect(activity, fm) {
            synchronized(managers) { managers[activity] = fm }
            onDispose { synchronized(managers) { if (managers[activity] === fm) managers.remove(activity) } }
        }
    }

    fun of(activity: Activity): FocusManager? = synchronized(managers) { managers[activity] }

    /** Yön tuşu: odak yoksa ilk öğeyi seçer. */
    fun move(activity: Activity, dir: FocusDirection): Boolean {
        val fm = of(activity) ?: return false
        return runCatching { fm.moveFocus(dir) || fm.moveFocus(FocusDirection.Next) }.getOrDefault(false)
    }
}
