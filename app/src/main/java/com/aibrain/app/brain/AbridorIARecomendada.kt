package com.aibrain.app.brain

import android.content.Context
import com.aibrain.app.browser.BrowserActivity

/**
 * Fase 17.17 — Abrir a IA recomendada (Fase 17.13) diretamente do Prompt
 * Builder.
 * Fase 21.8 — passa a abrir o navegador interno ([BrowserActivity]) em vez
 * de Custom Tabs: se já houver abas abertas, cria uma nova aba (launchMode
 * singleTask) em vez de substituir a atual.
 */

/**
 * Abre o site da IA recomendada em [sessao.recomendacaoIA] (Fase 17.13) no
 * navegador interno. Não faz nada (retorna false) se a sessão ainda não tem
 * recomendação ou se a IA recomendada não tem `site` cadastrado.
 */
fun abrirIARecomendadaNoNavegador(context: Context, sessao: SessaoConstrutorPrompt): Boolean {
    val ia = sessao.recomendacaoIA?.melhorOpcao ?: return false
    if (ia.site.isBlank()) return false
    context.startActivity(BrowserActivity.criarIntent(context, ia.nome, ia.site, ia.logo))
    return true
}
