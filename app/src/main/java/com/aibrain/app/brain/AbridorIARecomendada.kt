package com.aibrain.app.brain

import android.content.Context
import com.aibrain.app.util.abrirUrlNoNavegador

/**
 * Fase 17.17 — Abrir a IA recomendada (Fase 17.13) diretamente pelo Custom
 * Tabs, reaproveitando [abrirUrlNoNavegador] extraído do botão "Abrir IA"
 * da Fase 5.2 (mesma configuração de cores/toolbar, sem duplicar código).
 */

/**
 * Abre o site da IA recomendada em [sessao.recomendacaoIA] (Fase 17.13) via
 * Custom Tabs. Não faz nada (retorna false) se a sessão ainda não tem
 * recomendação ou se a IA recomendada não tem `site` cadastrado.
 */
fun abrirIARecomendadaNoNavegador(context: Context, sessao: SessaoConstrutorPrompt): Boolean {
    val ia = sessao.recomendacaoIA?.melhorOpcao ?: return false
    if (ia.site.isBlank()) return false
    abrirUrlNoNavegador(context, ia.site)
    return true
}
