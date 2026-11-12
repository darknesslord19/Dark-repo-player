package com.streambox.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Uygulama içi günlük: "Günlük" sekmesinde görünür. */
object AppLog {
    val lines = mutableStateListOf<String>()
    private val main = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun i(msg: String) = add("I", msg, null)
    fun e(msg: String, t: Throwable? = null) = add("E", msg, t)

    private fun add(level: String, msg: String, t: Throwable?) {
        if (level == "E") Log.e("StreamBox", msg, t) else Log.i("StreamBox", msg)
        val text = buildString {
            append(fmt.format(Date())).append(' ').append(level).append("  ").append(msg)
            if (t != null) append("\n    ").append(describe(t))
        }
        main.post {
            lines.add(0, text)
            while (lines.size > 500) lines.removeAt(lines.lastIndex)
        }
    }

    /** Hataları anlaşılır hale getirir; özellikle eksik sınıf/metot hatalarını. */
    fun describe(t: Throwable): String {
        var root: Throwable = t
        while (root.cause != null && root.cause !== root) root = root.cause!!
        return when (root) {
            is NoClassDefFoundError, is ClassNotFoundException ->
                "Eksik sınıf: ${root.message} (plugin, uygulamada olmayan bir CloudStream sınıfı istiyor)"
            is NoSuchMethodError, is NoSuchFieldError ->
                "Uyumsuz sürüm: ${root.message} (plugin farklı bir CloudStream sürümüyle derlenmiş)"
            else -> "${root.javaClass.simpleName}: ${root.message}"
        }
    }
}

/** Ana iş parçacığında çalıştır. */
private val mainHandler = Handler(Looper.getMainLooper())
fun onMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
}
