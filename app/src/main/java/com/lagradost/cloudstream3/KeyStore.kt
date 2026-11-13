package com.lagradost.cloudstream3

import android.content.Context
import android.content.SharedPreferences
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule

/**
 * CloudStream'in getKey/setKey depolamasının karşılığı. Değerler JSON olarak
 * "folder/path" anahtarıyla SharedPreferences'a yazılır (CloudStream ile aynı düzen).
 */
internal object KeyStore {
    val mapper: JsonMapper = JsonMapper.builder().addModule(kotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

    fun ctx(): Context? = com.streambox.app.StreamBoxApp.instance
    fun prefs(c: Context? = ctx()): SharedPreferences? = c?.getSharedPreferences("rebuild_preference", Context.MODE_PRIVATE)
    fun folder(folder: String, path: String) = "$folder/$path"

    fun <T> set(c: Context?, path: String, value: T) {
        prefs(c)?.edit()?.putString(path, mapper.writeValueAsString(value))?.apply()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(c: Context?, path: String, cls: Class<T>?, def: T?): T? {
        val raw = prefs(c)?.getString(path, null) ?: return def
        return runCatching {
            when {
                cls != null -> mapper.readValue(raw, cls)
                def != null -> mapper.readValue(raw, def!!::class.java) as T
                else -> mapper.readValue(raw, Any::class.java) as T
            }
        }.getOrNull() ?: def
    }

    fun keys(c: Context?, folder: String): List<String> =
        prefs(c)?.all?.keys?.filter { it.startsWith(folder) } ?: emptyList()

    fun remove(c: Context?, path: String) { prefs(c)?.edit()?.remove(path)?.apply() }

    fun removeAll(c: Context?, folder: String): Int {
        val k = keys(c, folder)
        prefs(c)?.edit()?.apply { k.forEach { remove(it) } }?.apply()
        return k.size
    }
}

/** utils paketindeki DataStore'un KeyStore'a erişimi (KeyStore internal kalsın diye). */
object KeyStoreAccess {
    fun mapper(): JsonMapper = KeyStore.mapper
    fun keys(c: Context?, folder: String) = KeyStore.keys(c, folder)
    fun remove(c: Context?, path: String) = KeyStore.remove(c, path)
    fun removeAll(c: Context?, folder: String) = KeyStore.removeAll(c, folder)
    fun <T> set(c: Context?, path: String, value: T) = KeyStore.set(c, path, value)
    fun <T> get(c: Context?, path: String, cls: Class<T>?, def: T?): T? = KeyStore.get(c, path, cls, def)
}
