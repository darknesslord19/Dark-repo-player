package com.lagradost.cloudstream3

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment

/**
 * CloudStream uygulamasındaki CloudStreamApp'in eklentilerin kullandığı kısmı
 * (bağlam ve getKey/setKey). Eklentiler yalnızca Companion üzerinden çağırır.
 */
class CloudStreamApp {
    companion object {
        val context: Context? get() = KeyStore.ctx()

        fun getActivity(context: Context?): Activity? = when (context) {
            null -> null
            is Activity -> context
            is ContextWrapper -> getActivity(context.baseContext)
            else -> null
        }

        fun <T> getKeyClass(path: String, valueType: Class<T>): T? = KeyStore.get(context, path, valueType, null)
        fun <T> setKeyClass(path: String, value: T) = KeyStore.set(context, path, value)
        fun removeKeys(folder: String): Int? = KeyStore.removeAll(context, folder)
        fun <T> setKey(path: String, value: T) = KeyStore.set(context, path, value)
        fun <T> setKey(folder: String, path: String, value: T) = KeyStore.set(context, KeyStore.folder(folder, path), value)
        fun <T> getKey(path: String, defVal: T?): T? = KeyStore.get(context, path, null, defVal)
        fun <T> getKey(path: String): T? = KeyStore.get(context, path, null, null)
        fun <T> getKey(folder: String, path: String): T? = KeyStore.get(context, KeyStore.folder(folder, path), null, null)
        fun <T> getKey(folder: String, path: String, defVal: T?): T? = KeyStore.get(context, KeyStore.folder(folder, path), null, defVal)
        fun getKeys(folder: String): List<String> = KeyStore.keys(context, folder)
        fun removeKey(folder: String, path: String) = KeyStore.remove(context, KeyStore.folder(folder, path))
        fun removeKey(path: String) = KeyStore.remove(context, path)

        fun openBrowser(url: String, fallbackWebview: Boolean = false, fragment: Fragment? = null) {
            val c = context ?: return
            runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }

        fun openBrowser(url: String, activity: Activity?) {
            runCatching { (activity ?: context)?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
