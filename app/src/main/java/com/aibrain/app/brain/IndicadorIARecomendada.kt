package com.aibrain.app.brain

import com.aibrain.app.model.IA

/**
 * Fase 17.13 — Indicação da IA recomendada para aquele prompt específico
 * (reaproveitando o `RecomendadorIA` da Fase 9 — [List<IA>.recomendar],
 * sem duplicar a lógica de detecção/ranking já existente).
 *
 * Usa o mesmo texto do usuário já processado na Fase 17.7 para rodar o
 * mecanismo da Fase 9 sobre o catálogo de IAs do app, obtendo a IA
 * (`melhorOpcao`) com melhor nota na categoria correspondente — o "por quê"
 * (breve) vem diretamente da nota real do catálogo (Fase 4.1), não de texto
 * inventado.
 */

/** Roda o `RecomendadorIA` (Fase 9) sobre o [catalogo] usando o texto já capturado na sessão. */
fun recomendarIAParaSessao(catalogo: List<IA>, sessao: SessaoConstrutorPrompt): RecomendacaoIA =
    catalogo.recomendar(sessao.textoUsuario)

/** Motivo breve: nome da IA + nota na categoria detectada pela Fase 9, ou null sem recomendação. */
private fun motivoBreve(recomendacao: RecomendacaoIA): String? {
    val ia = recomendacao.melhorOpcao ?: return null
    val categoria = recomendacao.categoriaDetectada ?: return ia.nome
    val nota = ia.notas[categoria.chave]
    return if (nota != null) {
        "${ia.nome} — nota $nota em ${categoria.rotulo}"
    } else {
        ia.nome
    }
}

/**
 * Fase 17.6 — preenche [SessaoConstrutorPrompt.recomendacaoIA] e
 * [SessaoConstrutorPrompt.motivoRecomendacaoIA] a partir do catálogo de IAs,
 * sem alterar [SessaoConstrutorPrompt.promptFinal] nem o estágio.
 */
fun avancarRecomendacaoIA(sessao: SessaoConstrutorPrompt, catalogo: List<IA>): SessaoConstrutorPrompt {
    val recomendacao = recomendarIAParaSessao(catalogo, sessao)
    return sessao.copy(
        recomendacaoIA = recomendacao,
        motivoRecomendacaoIA = motivoBreve(recomendacao)
    )
}
