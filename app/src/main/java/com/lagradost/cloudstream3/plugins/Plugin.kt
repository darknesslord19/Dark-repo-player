package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources

/**
 * CloudStream uygulamasının Android tarafındaki Plugin sınıfının karşılığı.
 *
 * Eski/yaygın .cs3 plugin'leri `class X : Plugin() { override fun load(context: Context) }`
 * şeklinde yazılır ve bu sınıfı TAM OLARAK bu paket adıyla arar. Yeni plugin'ler ise
 * library içindeki [BasePlugin] sınıfını kullanır; o da CloudStream library bağımlılığından gelir.
 */
abstract class Plugin : BasePlugin() {

    /** Plugin yüklenirken çağrılır. Varsayılan olarak parametresiz load() çalışır. */
    open fun load(context: Context) {
        load()
    }

    /** Plugin manifest'inde requiresResources=true ise .cs3 içindeki kaynaklar. */
    var resources: Resources? = null

    /** Plugin'in kendi ayar ekranını açan fonksiyon (varsa). */
    var openSettings: ((context: Context) -> Unit)? = null

    class Manifest {
        var name: String? = null
        var pluginClassName: String? = null
        var version: Int? = null
        var requiresResources: Boolean = false
    }
}
