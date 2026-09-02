package com.aibrain.app.brain

import android.content.Context
import com.aibrain.app.browser.BrowserActivity

/** Abre a recomendação do Prompt Builder usando o mesmo contrato do Brain. */
suspend fun abrirIARecomendadaNoNavegador(context: Context, sessao: SessaoConstrutorPrompt): Boolean {
    val ia = sessao.recomendacaoIA?.melhorOpcao ?: return false
    val contrato = IAOpenContract(
        selectedAIId = ia.id,
        selectedAIName = ia.nome,
        officialResolvedUrl = null,
        urlStatus = UrlResolutionStatus.NOT_FOUND,
        generatedPrompt = sessao.promptFinal.orEmpty()
    )
    val resolvido = IAUrlResolver(context.applicationContext).resolve(contrato)
    if (resolvido.urlStatus != UrlResolutionStatus.RESOLVED || resolvido.officialResolvedUrl == null) return false
    context.startActivity(BrowserActivity.criarIntent(context, resolvido))
    return true
}
