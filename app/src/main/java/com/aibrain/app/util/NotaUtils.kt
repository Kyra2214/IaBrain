package com.aibrain.app.util

import com.aibrain.app.model.Categoria
import com.aibrain.app.model.IA

/**
 * Fase 4.1 — Cálculo/leitura de nota por categoria.
 *
 * As notas já vêm prontas no ia_catalogo.json (campo "notas"),
 * então aqui não há cálculo pesado: é a camada que interpreta
 * esses valores de forma segura e conveniente para o resto do app.
 */

/** Retorna a nota da IA numa categoria específica, ou null se ela não atua nessa categoria. */
fun IA.notaPara(categoria: Categoria): Int? = notas[categoria.chave]

/** Retorna a nota da IA numa categoria pela chave (ex: "codigo"), ou null se não houver. */
fun IA.notaPara(chaveCategoria: String): Int? = notas[chaveCategoria]

/** Média das notas da IA em todas as categorias em que ela atua. Retorna 0.0 se não tiver notas. */
fun IA.notaMedia(): Double {
    if (notas.isEmpty()) return 0.0
    return notas.values.average()
}

/** Retorna a categoria (chave) em que a IA tem a maior nota, ou null se não tiver notas. */
fun IA.categoriaForte(): String? = notas.maxByOrNull { it.value }?.key

/**
 * Fase 4.2 — Ranking.
 * Aqui o cálculo passa a operar sobre o CONJUNTO de IAs (não mais uma só),
 * por isso fica separado da Fase 4.1 acima.
 */

/**
 * Ranking geral: ordena o conjunto de IAs pela nota média (maior primeiro).
 * Usado nas seções "Ranking" / "IAs populares" da tela inicial.
 */
fun List<IA>.rankingGeral(): List<IA> =
    sortedByDescending { it.notaMedia() }

/**
 * Ranking por categoria: retorna apenas as IAs que atuam na categoria informada,
 * ordenadas pela nota naquela categoria (maior primeiro).
 */
fun List<IA>.rankingPorCategoria(categoria: Categoria): List<IA> =
    mapNotNull { ia -> ia.notaPara(categoria)?.let { nota -> ia to nota } }
        .sortedByDescending { it.second }
        .map { it.first }

/**
 * Top N de uma categoria — usado no fluxo do AI Brain (Fase 8/9):
 * "melhor IA", "segunda opção", "alternativas gratuitas".
 */
fun List<IA>.topPorCategoria(categoria: Categoria, quantidade: Int = 3): List<IA> =
    rankingPorCategoria(categoria).take(quantidade)

/**
 * Fase 15.3 — Helper de faixa de avaliação, reaproveitando [notaMedia] (Fase 4.1).
 * Usado pelo filtro rápido de Avaliação do AI Brain (Fase 15.4): cada faixa é um
 * limiar mínimo — a IA "atende" a faixa se sua nota média for maior ou igual a ela.
 */
enum class FaixaAvaliacao(val notaMinima: Double, val rotulo: String) {
    DEZ(10.0, "\u2B50 10"),
    NOVE_MAIS(9.0, "\u2B50 9+"),
    OITO_MAIS(8.0, "\u2B50 8+"),
    SETE_MAIS(7.0, "\u2B50 7+");
}

/** true se a nota média da IA atende (é maior ou igual a) o limiar da [faixa]. */
fun IA.atendeFaixa(faixa: FaixaAvaliacao): Boolean = notaMedia() >= faixa.notaMinima
