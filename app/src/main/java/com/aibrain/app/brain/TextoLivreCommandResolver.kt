package com.aibrain.app.brain

/** Mapeia apenas intenções claras para slugs existentes; ambiguidade retorna null. */
object TextoLivreIntent {
    fun commandFor(text: String): String? {
        val t = text.lowercase()
        return when {
            listOf("debug", "corrigir código", "corrigir codigo", "erro no código", "erro no codigo").any(t::contains) -> "/debug"
            listOf("teste", "testar código", "testar codigo", "casos de teste").any(t::contains) -> "/test"
            listOf("revisão técnica", "revisao tecnica", "code review", "revisar código", "revisar codigo").any(t::contains) -> "/review"
            listOf("pesquisar", "pesquisa", "fontes", "evidências", "evidencias").any(t::contains) -> "/research"
            listOf("criar aplicativo", "criar um aplicativo", "criar app", "criar um app", "desenvolver aplicativo", "desenvolver um aplicativo", "desenvolver app", "implementar", "programar", "código", "codigo").any(t::contains) -> "/implement"
            else -> null
        }
    }
}
