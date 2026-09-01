package com.aibrain.app.brain

import android.webkit.WebView

/**
 * Ponto único para adaptadores específicos de pré-preenchimento.
 * Vazio por segurança até existir suporte confirmado para uma IA e seus seletores.
 */
object PrefillAdapterRegistry {
    @Suppress("UNUSED_PARAMETER")
    fun tryPrefill(_webView: WebView, _prompt: String): Boolean = false
}
