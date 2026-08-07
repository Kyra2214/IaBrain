package com.aibrain.app.util

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.aibrain.app.R

/**
 * Fase 5.2 — Abertura de URL via Android Custom Tabs (extraído aqui na
 * Fase 17.17 para ser reaproveitado pelo botão "Abrir IA" de
 * [com.aibrain.app.view.DetalheIAActivity] E pela IA recomendada do Prompt
 * Builder — Fase 17.13/17.17 — sem duplicar a configuração de cores/toolbar).
 * Não sai do app "de vez": abre um navegador embutido, mantendo a
 * experiência integrada.
 */
fun abrirUrlNoNavegador(context: Context, url: String) {
    val cores = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(context.getColor(R.color.primary))
        .build()

    val customTabsIntent = CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(cores)
        .setShowTitle(true)
        .build()

    customTabsIntent.launchUrl(context, Uri.parse(url))
}
