package com.aibrain.app.groq

/**
 * Fase 18.6 — Prompt de sistema FIXO da curadoria de novas IAs.
 *
 * Instrui a Groq a responder em formato estruturado e previsível, para que
 * a Fase 18.7 (parser) consiga extrair nome/site/categoria sem ambiguidade.
 * Formato: UMA sugestão por linha, campos separados por " | ":
 *
 *     NOME | SITE | CATEGORIA_SUGERIDA
 *
 * Sem numeração, sem markdown, sem texto fora desse formato — qualquer
 * explicação adicional da Groq é descartada pela Fase 18.7. A categoria
 * sugerida é livre (texto curto em português); a Fase 18.8 é quem decide
 * como mapear/exibir isso ao adicionar manualmente ao catálogo.
 *
 * Recebe [nomesJaNoCatalogo] (Fase 18.6) para instruir a Groq a NUNCA
 * sugerir IAs que já existem no `ia_catalogo.json` local — a curadoria
 * só faz sentido para candidatas novas.
 */
object PromptCuradoriaIA {

    private const val FORMATO_LINHA = "NOME | SITE | CATEGORIA_SUGERIDA"

    fun construir(nomesJaNoCatalogo: List<String>): String {
        val listaExistentes = if (nomesJaNoCatalogo.isEmpty()) {
            "(nenhuma ainda)"
        } else {
            nomesJaNoCatalogo.joinToString(", ")
        }

        return """
            Você é um assistente de curadoria para o app AI Brain, um catálogo de inteligências artificiais.
            Quando o usuário pedir para listar ou sugerir IAs, responda APENAS com sugestões de IAs reais que ainda NÃO estejam nesta lista de IAs já cadastradas: $listaExistentes.

            Responda em texto simples, UMA sugestão por linha, exatamente neste formato, sem numeração e sem markdown:
            $FORMATO_LINHA

            Não inclua nenhuma linha de introdução, explicação ou conclusão — apenas as linhas de sugestão. Se não houver nenhuma sugestão nova para o pedido, responda com uma única linha vazia.
        """.trimIndent()
    }
}
