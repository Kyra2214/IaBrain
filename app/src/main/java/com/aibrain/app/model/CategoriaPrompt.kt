package com.aibrain.app.model

/**
 * Conjunto fixo de categorias da Biblioteca de Prompts (Fase 16).
 * Mesmo padrão de [Categoria] (Fase 3.1): [chave] é o valor usado no
 * prompts_biblioteca.json, [emoji] + [rotulo] são usados na exibição.
 *
 * Categorias independentes das de [Categoria] (catálogo de IAs) — um prompt
 * é sobre uma tarefa a realizar, não sobre uma IA em si.
 */
enum class CategoriaPrompt(val chave: String, val emoji: String, val rotulo: String) {
    PROGRAMACAO("programacao", "\uD83D\uDCBB", "Programação"),
    VIDEO("video", "\uD83C\uDFA5", "Vídeo"),
    IMAGEM("imagem", "\uD83D\uDDBC\uFE0F", "Imagem"),
    DESIGN("design", "\uD83C\uDFA8", "Design"),
    ESCRITA("escrita", "\u270D\uFE0F", "Escrita"),
    ESTUDOS("estudos", "\uD83D\uDCDA", "Estudos"),
    MARKETING("marketing", "\uD83D\uDCCA", "Marketing"),
    REDES_SOCIAIS("redes_sociais", "\uD83D\uDCF1", "Redes Sociais"),
    NEGOCIOS("negocios", "\uD83D\uDCBC", "Negócios"),
    DOCUMENTOS("documentos", "\uD83D\uDCC4", "Documentos"),
    TRADUCAO("traducao", "\uD83C\uDF0D", "Tradução"),
    VOZ("voz", "\uD83C\uDF99\uFE0F", "Voz"),
    MUSICA("musica", "\uD83C\uDFB5", "Música"),
    AUTOMACAO("automacao", "\u2699\uFE0F", "Automação"),
    ENGENHARIA_DE_PROMPT("engenharia_de_prompt", "\uD83E\uDDE9", "Engenharia de Prompt");

    companion object {
        /** Busca uma categoria de prompt pela chave usada no JSON (ex: "imagem"). */
        fun porChave(chave: String): CategoriaPrompt? = entries.find { it.chave == chave }
    }
}
