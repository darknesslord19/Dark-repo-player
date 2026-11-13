package com.lagradost.cloudstream3

import android.content.Context

/** Eski eklentilerin kullandığı AcraApplication.getKey/setKey/context karşılığı. */
class AcraApplication {
    companion object {
        val context: Context? get() = KeyStore.ctx()
        fun removeKeys(folder: String): Int? = KeyStore.removeAll(context, folder)
        fun <T> setKey(path: String, value: T) = KeyStore.set(context, path, value)
        fun <T> setKey(folder: String, path: String, value: T) = KeyStore.set(context, KeyStore.folder(folder, path), value)
        fun <T> getKey(path: String, defVal: T?): T? = KeyStore.get(context, path, null, defVal)
        fun <T> getKey(path: String): T? = KeyStore.get(context, path, null, null)
        fun <T> getKey(folder: String, path: String): T? = KeyStore.get(context, KeyStore.folder(folder, path), null, null)
        fun <T> getKey(folder: String, path: String, defVal: T?): T? = KeyStore.get(context, KeyStore.folder(folder, path), null, defVal)
    }
}
