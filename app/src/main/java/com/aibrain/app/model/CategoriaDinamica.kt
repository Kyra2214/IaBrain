package com.aibrain.app.model

/**
 * Fase 26 — Categorias dinâmicas (criadas na curadoria, Fase 18).
 *
 * O enum [Categoria] continua sendo o conjunto FIXO de categorias do
 * catálogo (chips de filtro da tela principal, AI Brain etc.), mas agora
 * uma IA adicionada pela curadoria pode ter uma categoria que NÃO existe
 * no enum — e essa categoria nova deve ganhar uma aba/chip própria.
 *
 * [CategoriaDinamica] resolve a exibição dessas categorias:
 * - [rotulo] devolve o rótulo exibível de qualquer chave de categoria:
 *   se existir no enum [Categoria], usa o emoji + rótulo fixo;
 *   se for uma categoria nova, capitaliza a chave (ex.: "saude mental"
 *   vira "Saúde Mental" — com acentos se vierem assim da Groq).
 * - [rotuloCurto] é a mesma coisa, sem emoji (usado no chip de categoria
 *   do card de sugestão e na tela de detalhes, que já exibem a chave).
 */
object CategoriaDinamica {

    /** Rótulo exibível de uma chave de categoria, com emoji se for categoria fixa. */
    fun rotulo(chave: String): String {
        Categoria.porChave(chave)?.let { return "${it.emoji} ${it.rotulo}" }
        return capitalizar(chave)
    }

    /** Rótulo exibível de uma chave de categoria, sem emoji (categoria fixa ou nova). */
    fun rotuloCurto(chave: String): String {
        Categoria.porChave(chave)?.let { return it.rotulo }
        return capitalizar(chave)
    }

    /** Capitaliza a primeira letra de cada palavra, preservando acentos. */
    private fun capitalizar(texto: String): String = texto
        .trim()
        .lowercase()
        .split(" ")
        .filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
