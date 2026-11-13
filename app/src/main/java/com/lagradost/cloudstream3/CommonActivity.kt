package com.lagradost.cloudstream3

import android.app.Activity
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.widget.Toast
import java.lang.ref.WeakReference

/** CloudStream'deki CommonActivity'nin eklentilerin kullandığı kısmı (etkinlik, ekran boyutu, toast). */
object CommonActivity {
    const val TAG = "COMPACT"
    private var activityRef: WeakReference<Activity>? = null

    val activity: Activity? get() = activityRef?.get()
    fun setActivityInstance(newActivity: Activity?) { activityRef = newActivity?.let { WeakReference(it) } }

    val displayMetrics: DisplayMetrics get() = Resources.getSystem().displayMetrics
    val screenWidth: Int get() = displayMetrics.widthPixels.coerceAtMost(displayMetrics.heightPixels)
    val screenHeight: Int get() = displayMetrics.widthPixels.coerceAtLeast(displayMetrics.heightPixels)
    val screenWidthWithOrientation: Int get() = displayMetrics.widthPixels
    val screenHeightWithOrientation: Int get() = displayMetrics.heightPixels

    private fun toast(text: String?, duration: Int?) {
        val c = activity ?: KeyStore.ctx() ?: return
        if (text.isNullOrBlank()) return
        Handler(Looper.getMainLooper()).post {
            runCatching { Toast.makeText(c, text, if (duration == Toast.LENGTH_LONG) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show() }
        }
    }

    fun showToast(message: Int, duration: Int? = null) = toast(runCatching { KeyStore.ctx()?.getString(message) }.getOrNull(), duration)
    fun showToast(message: String?, duration: Int? = null) = toast(message, duration)
    fun showToast(act: Activity?, message: Int, duration: Int? = null) = toast(runCatching { act?.getString(message) }.getOrNull(), duration)
    fun showToast(act: Activity?, message: String?, duration: Int? = null) = toast(message, duration)
}
