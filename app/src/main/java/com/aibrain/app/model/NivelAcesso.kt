package com.aibrain.app.model

/**
 * Fase 15.1 — Classificação de acesso em 3 níveis, usada pelo filtro rápido
 * de Acesso (Fase 15.4) e exibida na listagem/detalhe.
 *
 * O modelo [IA] já tinha só o booleano `gratuita` ("tem uma forma gratuita
 * de uso"), que continua existindo sem mudança de semântica (usado por
 * RecomendadorIA para "alternativas gratuitas" e por DetalheIAActivity).
 * [NivelAcesso] refina esse booleano, distinguindo ferramentas 100% gratuitas
 * das freemium (free + camada paga) — ambas contam como `gratuita = true`,
 * mas só a primeira é [GRATUITA].
 */
enum class NivelAcesso(val chave: String, val emoji: String, val rotulo: String) {
    GRATUITA("gratuita", "\uD83D\uDFE2", "Gratuita"),
    FREEMIUM("freemium", "\uD83D\uDFE1", "Freemium"),
    PAGA("paga", "\uD83D\uDD34", "Paga");

    companion object {
        fun porChave(chave: String?): NivelAcesso? = entries.firstOrNull { it.chave == chave }
    }
}
