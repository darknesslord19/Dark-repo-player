package com.streambox.app.remote

import android.app.Activity
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import com.streambox.app.player.PlayerActivity
import java.util.WeakHashMap

/**
 * Telefon kumandasının yön/OK/geri tuşlarını uygulamanın ekranlarına uygular.
 * Telefonda ekran "dokunmatik moddadır": hiçbir öğe odaklı olmadığı için sahte tuş olayları boşa gider.
 * Bu yüzden önce dokunmatik moddan çıkılır, ardından odak doğrudan Compose'un odak yöneticisiyle taşınır.
 */
object RemoteNav {
    private val managers = WeakHashMap<Activity, FocusManager>()

    /** Her ekranın kökünde çağrılır: o ekranın odak yöneticisini kaydeder. */
    @Composable
    fun Hook() {
        val act = LocalContext.current as? Activity ?: return
        val fm = LocalFocusManager.current
        DisposableEffect(act, fm) {
            synchronized(managers) { managers[act] = fm }
            onDispose { synchronized(managers) { if (managers[act] === fm) managers.remove(act) } }
        }
    }

    private fun composeView(act: Activity): View? {
        val content = act.findViewById<ViewGroup>(android.R.id.content) ?: return null
        val compose = content.getChildAt(0) ?: return null
        return (compose as? ViewGroup)?.takeIf { it.childCount > 0 }?.getChildAt(0) ?: compose
    }

    private fun dir(code: String) = when (code) {
        "up" -> FocusDirection.Up; "down" -> FocusDirection.Down
        "left" -> FocusDirection.Left; "right" -> FocusDirection.Right
        else -> null
    }

    private fun keyOf(code: String) = when (code) {
        "up" -> KeyEvent.KEYCODE_DPAD_UP; "down" -> KeyEvent.KEYCODE_DPAD_DOWN
        "left" -> KeyEvent.KEYCODE_DPAD_LEFT; "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
        "ok" -> KeyEvent.KEYCODE_DPAD_CENTER; "menu" -> KeyEvent.KEYCODE_MENU
        else -> KeyEvent.KEYCODE_UNKNOWN
    }

    private fun press(target: View, key: Int) {
        val now = SystemClock.uptimeMillis()
        target.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0))
        target.dispatchKeyEvent(KeyEvent(now, now + 40, KeyEvent.ACTION_UP, key, 0))
    }

    /** Odağı taşır; hiçbir şey odaklı değilse ilk öğeyi seçer. */
    private fun move(fm: FocusManager?, view: View, code: String) {
        val d = dir(code) ?: return
        val moved = runCatching { fm?.moveFocus(d) == true }.getOrDefault(false)
        if (moved) return
        val entered = runCatching { fm?.moveFocus(FocusDirection.Enter) == true || fm?.moveFocus(FocusDirection.Next) == true }.getOrDefault(false)
        if (!entered) press(view, keyOf(code))
    }

    /** Ana iş parçacığında çağrılmalı. Uygulandıysa true. */
    fun handle(act: Activity, code: String): Boolean {
        val view = composeView(act) ?: return false
        // Dokunmatik moddan çık ve klavye odağını Compose'a ver
        if (view.isInTouchMode || !view.hasFocus()) runCatching { view.requestFocusFromTouch() }
        val fm = synchronized(managers) { managers[act] }

        if (act is PlayerActivity) return player(act, fm, view, code)

        when (code) {
            "back" -> (act as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed() ?: act.onBackPressed()
            "up", "down", "left", "right" -> move(fm, view, code)
            "ok", "menu" -> press(view, keyOf(code))
            else -> return false
        }
        return true
    }

    private fun player(p: PlayerActivity, fm: FocusManager?, view: View, code: String): Boolean {
        val ui = p.ui
        if (code == "back") {
            if (ui.controlsVisible && !ui.locked && ui.playing) ui.controlsVisible = false else p.finish()
            return true
        }
        if (ui.locked) { p.showControls(); return true }
        if (!ui.controlsVisible) {
            // Kontroller gizliyken: sağ/sol 10 sn, diğerleri kontrolleri açar
            when (code) {
                "left" -> p.seekBy(-10_000)
                "right" -> p.seekBy(10_000)
                else -> p.showControls()
            }
            return true
        }
        p.touch()
        when (code) {
            "up", "down", "left", "right" -> move(fm, view, code)
            "ok", "menu" -> press(view, keyOf(code))
            else -> return false
        }
        return true
    }
}
