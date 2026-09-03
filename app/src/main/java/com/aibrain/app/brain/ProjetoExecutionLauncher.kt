package com.aibrain.app.brain

import android.content.Context
import com.aibrain.app.browser.BrowserActivity

/** Resolve a IA pelo contrato único antes de abrir o navegador interno. */
suspend fun abrirExecucaoProjeto(context: Context, iaId: String, prompt: String): Boolean {
    val contrato = IAOpenContract(iaId, "IA do projeto", null, UrlResolutionStatus.NOT_FOUND, prompt)
    val resolvido = IAUrlResolver(context.applicationContext).resolve(contrato)
    val url = resolvido.officialResolvedUrl ?: return false
    context.startActivity(BrowserActivity.criarIntent(context, resolvido.copy(officialResolvedUrl = url)))
    return true
}
