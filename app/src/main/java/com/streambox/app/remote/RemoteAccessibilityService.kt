package com.streambox.app.remote

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Telefon kumandasının tuşlarını uygulama DIŞINDA da çalıştırır (TV ana ekranı, diğer uygulamalar).
 * Android 13+ sistem yön tuşu eylemlerini kullanır; eski sürümlerde odağı ekrandaki öğeler arasında taşır.
 */
class RemoteAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: RemoteAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, RemoteAccessibilityService::class.java).flattenToString()
            return list.split(':').any { it.equals(me, true) }
        }
    }

    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    override fun onUnbind(intent: android.content.Intent?): Boolean { if (instance === this) instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** Kumanda kodunu uygular; başarılıysa true. */
    fun perform(code: String): Boolean = when (code) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "menu" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "up", "down", "left", "right", "ok" ->
            if (Build.VERSION.SDK_INT >= 33) performGlobalAction(
                when (code) {
                    "up" -> GLOBAL_ACTION_DPAD_UP; "down" -> GLOBAL_ACTION_DPAD_DOWN
                    "left" -> GLOBAL_ACTION_DPAD_LEFT; "right" -> GLOBAL_ACTION_DPAD_RIGHT
                    else -> GLOBAL_ACTION_DPAD_CENTER
                }
            ) else moveFocus(code)
        else -> false
    }

    /** Eski Android: odaklı öğeden verilen yöndeki öğeye geç ya da ona tıkla. */
    private fun moveFocus(code: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (code == "ok") {
            var n = focused
            while (n != null && !n.isClickable) n = n.parent
            return n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        }
        if (focused == null) {
            // Hiçbir şey odaklı değil: ilk odaklanabilir öğeyi seç
            return firstFocusable(root)?.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ?: false
        }
        val dir = when (code) { "up" -> View.FOCUS_UP; "down" -> View.FOCUS_DOWN; "left" -> View.FOCUS_LEFT; else -> View.FOCUS_RIGHT }
        val next = focused.focusSearch(dir) ?: return false
        return next.performAction(AccessibilityNodeInfo.ACTION_FOCUS) || next.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
    }

    private fun firstFocusable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isFocusable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> firstFocusable(c)?.let { return it } }
        return null
    }
}
