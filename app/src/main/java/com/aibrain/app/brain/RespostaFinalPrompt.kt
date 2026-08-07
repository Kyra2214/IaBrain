package com.aibrain.app.brain

import com.aibrain.app.model.MensagemChat

/**
 * Fase 17.14 — Resposta do assistente sempre enxuta: entrega **somente** o
 * prompt final + uma breve explicação, sem textos longos — última regra do
 * fluxo fixo (Fase 17.6), fechando a sequência iniciada na Fase 17.7.
 *
 * A "breve explicação" é no máximo 2 linhas: a IA recomendada e o motivo
 * (Fase 17.13), mais o aviso de fallback (Fase 17.10) só quando ele existir.
 * Nunca reexplica o template, a categoria ou o processo de busca/perguntas —
 * isso ficaria longo e é informação interna do fluxo, não do resultado.
 */

/**
 * Monta o texto final da resposta a partir de uma sessão já concluída
 * ([EstagioConstrutorPrompt.PROMPT_ENTREGUE] com `promptFinal` preenchido —
 * Fase 17.11). Retorna null se a sessão ainda não chegou lá.
 */
fun montarRespostaFinal(sessao: SessaoConstrutorPrompt): String? {
    if (sessao.estagio != EstagioConstrutorPrompt.PROMPT_ENTREGUE) return null
    val promptFinal = sessao.promptFinal ?: return null

    val explicacao = buildList {
        (sessao.motivoRecomendacaoIA ?: sessao.iaDestino)?.let { add("Recomendado: $it") }
        sessao.avisoFallback?.let { add(it) }
    }.joinToString("\n")

    return if (explicacao.isBlank()) promptFinal else "$promptFinal\n\n$explicacao"
}

/** Empacota [montarRespostaFinal] como [MensagemChat] do assistente (Fase 17.2), pronta para o histórico da sessão. */
fun gerarMensagemRespostaFinal(sessao: SessaoConstrutorPrompt): MensagemChat? =
    montarRespostaFinal(sessao)?.let { MensagemChat(texto = it, deUsuario = false) }
