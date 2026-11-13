package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.fasterxml.jackson.databind.json.JsonMapper

/** CloudStream DataStore karşılığı (Context.getKey / setKey uzantıları). */
data class Editor(val editor: SharedPreferences.Editor) {
    fun <T> setKeyRaw(path: String, value: T) {
        when (value) {
            is Boolean -> editor.putBoolean(path, value)
            is Int -> editor.putInt(path, value)
            is String -> editor.putString(path, value)
            is Float -> editor.putFloat(path, value)
            is Long -> editor.putLong(path, value)
            else -> editor.putString(path, value?.let { DataStore.mapper.writeValueAsString(it) })
        }
    }
    fun apply() { editor.apply() }
}

object DataStore {
    private val ks = com.lagradost.cloudstream3.KeyStoreAccess
    val mapper: JsonMapper get() = ks.mapper()

    fun Context.getSharedPrefs(): SharedPreferences = getSharedPreferences("rebuild_preference", Context.MODE_PRIVATE)
    fun getFolderName(folder: String, path: String): String = "$folder/$path"
    fun Context.editor(isEditingAppSettings: Boolean = false): Editor =
        Editor((if (isEditingAppSettings) getDefaultSharedPrefs() else getSharedPrefs()).edit())
    @Suppress("DEPRECATION")
    fun Context.getDefaultSharedPrefs(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
    fun Context.getKeys(folder: String): List<String> = ks.keys(this, folder)
    fun Context.removeKey(folder: String, path: String) = ks.remove(this, getFolderName(folder, path))
    fun Context.containsKey(folder: String, path: String): Boolean = getSharedPrefs().contains(getFolderName(folder, path))
    fun Context.containsKey(path: String): Boolean = getSharedPrefs().contains(path)
    fun Context.removeKey(path: String) = ks.remove(this, path)
    fun Context.removeKeys(folder: String): Int = ks.removeAll(this, folder)
    fun <T> Context.setKey(path: String, value: T) = ks.set(this, path, value)
    fun <T> Context.getKey(path: String, valueType: Class<T>): T? = ks.get(this, path, valueType, null)
    fun <T> Context.setKey(folder: String, path: String, value: T) = ks.set(this, getFolderName(folder, path), value)
    @Suppress("UNCHECKED_CAST")
    fun <T> String.toKotlinObject(): T = mapper.readValue(this, Any::class.java) as T
    fun <T> String.toKotlinObject(valueType: Class<T>): T = mapper.readValue(this, valueType)
    fun <T> Context.getKey(path: String, defVal: T?): T? = ks.get(this, path, null, defVal)
    fun <T> Context.getKey(path: String): T? = ks.get(this, path, null, null)
    fun <T> Context.getKey(folder: String, path: String): T? = ks.get(this, getFolderName(folder, path), null, null)
    fun <T> Context.getKey(folder: String, path: String, defVal: T?): T? = ks.get(this, getFolderName(folder, path), null, defVal)
}
