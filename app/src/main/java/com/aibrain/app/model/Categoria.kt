package com.aibrain.app.model

/**
 * Conjunto fixo de categorias do AI Brain.
 * Fase 3.1 — primeiro "conjunto" real do app (antes só existia 1 item isolado).
 *
 * O campo [chave] é o valor usado no ia_catalogo.json (categorias/notas),
 * o [emoji] + [rotulo] são usados na exibição da interface.
 */
enum class Categoria(val chave: String, val emoji: String, val rotulo: String) {
    CONVERSA("conversa", "\uD83D\uDCAC", "Conversa"),
    CODIGO("codigo", "\uD83D\uDCBB", "Código"),
    VIDEO("video", "\uD83C\uDFA5", "Vídeo"),
    IMAGEM("imagem", "\uD83D\uDDBC\uFE0F", "Imagem"),
    DESIGN("design", "\uD83C\uDFA8", "Design"),
    MUSICA("musica", "\uD83C\uDFB5", "Música"),
    VOZ("voz", "\uD83C\uDF99\uFE0F", "Voz"),
    ESCRITA("escrita", "\u270D\uFE0F", "Escrita"),
    ESTUDOS("estudos", "\uD83D\uDCDA", "Estudos"),
    TRADUCAO("traducao", "\uD83C\uDF0D", "Tradução"),
    PRODUTIVIDADE("produtividade", "\uD83D\uDCCA", "Produtividade"),
    AGENTES_IA("agentes_ia", "\uD83E\uDD16", "Agentes IA"),
    NEGOCIOS("negocios", "\uD83D\uDCC8", "Negócios"),
    PESQUISA("pesquisa", "\uD83D\uDD0E", "Pesquisa"),
    AUTOMACAO("automacao", "\u2699\uFE0F", "Automação");

    companion object {
        /** Busca uma categoria pela chave usada no JSON (ex: "codigo"). */
        fun porChave(chave: String): Categoria? = entries.find { it.chave == chave }
    }
}
