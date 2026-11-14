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
    /** Etkinlik başına açık pencereler (ekran, alttan açılan panel, pencere…): en son açılan öndedir. */
    private class Entry(val fm: FocusManager, val view: View)
    private val stacks = WeakHashMap<Activity, MutableList<Entry>>()

    private fun findActivity(c: android.content.Context?): Activity? {
        var x = c
        while (x is android.content.ContextWrapper) { if (x is Activity) return x; x = x.baseContext }
        return null
    }

    /** Her ekranın ve her açılır panelin/pencerenin içinde çağrılır: odak yöneticisini ve penceresini kaydeder. */
    @Composable
    fun Hook() {
        val view = androidx.compose.ui.platform.LocalView.current
        val act = findActivity(LocalContext.current) ?: findActivity(view.context) ?: return
        val fm = LocalFocusManager.current
        DisposableEffect(act, fm, view) {
            val e = Entry(fm, view)
            synchronized(stacks) { stacks.getOrPut(act) { mutableListOf() } += e }
            onDispose { synchronized(stacks) { stacks[act]?.remove(e) } }
        }
    }

    private fun top(act: Activity): Entry? = synchronized(stacks) {
        stacks[act]?.lastOrNull { it.view.isAttachedToWindow }
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

    private fun press(target: View, key: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val handled = target.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0))
        target.dispatchKeyEvent(KeyEvent(now, now + 40, KeyEvent.ACTION_UP, key, 0))
        return handled
    }

    /** Odağı taşır; hiçbir şey odaklı değilse ilk öğeyi seçer. */
    private fun move(fm: FocusManager?, view: View, code: String) {
        val d = dir(code) ?: return
        // Önce gerçek tuş olarak gönder: odaklı öğe (ör. ilerleme çubuğu) tuşu kendisi kullanabilir
        if (view.hasFocus() && press(view, keyOf(code))) return
        val moved = runCatching { fm?.moveFocus(d) == true }.getOrDefault(false)
        if (moved) return
        val entered = runCatching { fm?.moveFocus(FocusDirection.Enter) == true || fm?.moveFocus(FocusDirection.Next) == true }.getOrDefault(false)
        if (!entered) press(view, keyOf(code))
    }

    /** Ana iş parçacığında çağrılmalı. Uygulandıysa true. */
    fun handle(act: Activity, code: String): Boolean {
        val entry = top(act)
        // Açık bir panel/pencere varsa tuşlar ona gider
        val inDialog = entry != null && entry.view.rootView !== act.window.decorView
        val view = entry?.view ?: composeView(act) ?: return false
        // Dokunmatik moddan çık ve klavye odağını Compose'a ver
        if (view.isInTouchMode || !view.hasFocus()) runCatching { view.requestFocusFromTouch() }
        val fm = entry?.fm

        if (inDialog) {
            when (code) {
                "back" -> press(view.rootView, KeyEvent.KEYCODE_BACK)
                "up", "down", "left", "right" -> move(fm, view, code)
                "ok", "menu" -> press(view, keyOf(code))
                else -> return false
            }
            return true
        }
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
