package com.aibrain.app.util

import com.aibrain.app.model.IA

/**
 * Fase 15.2 — Helper de idioma por filtro, a partir de [IA.idiomas] já existente
 * (Fase 2.1). Reduz a lista livre de códigos de idioma (ex: ["en", "pt"]) a um
 * dos 3 grupos usados no filtro rápido do AI Brain (Fase 15.4).
 */
enum class FiltroIdioma(val emoji: String, val rotulo: String) {
    PORTUGUES("\uD83C\uDDE7\uD83C\uDDF7", "Português"),
    INGLES("\uD83C\uDDFA\uD83C\uDDF8", "Inglês"),
    MULTILINGUE("\uD83C\uDF0D", "Multilíngue");
}

/**
 * Regra de classificação:
 * - 3 ou mais idiomas suportados → [FiltroIdioma.MULTILINGUE]
 * - suporta português ("pt") → [FiltroIdioma.PORTUGUES]
 * - caso contrário → [FiltroIdioma.INGLES] (idioma-base do catálogo)
 */
fun IA.filtroIdioma(): FiltroIdioma = when {
    idiomas.size >= 3 -> FiltroIdioma.MULTILINGUE
    idiomas.contains("pt") -> FiltroIdioma.PORTUGUES
    else -> FiltroIdioma.INGLES
}
