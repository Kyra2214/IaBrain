package com.aibrain.app.groq

import com.aibrain.app.model.Categoria

/**
 * Fase 18.6 — Prompt de sistema FIXO da curadoria de novas IAs.
 *
 * Instrui a Groq a responder em formato estruturado e previsível, para que
 * a Fase 18.7 (parser) consiga extrair nome/site/categoria/descrição sem
 * ambiguidade. Formato: UMA sugestão por linha, campos separados por " | ":
 *
 *     NOME | SITE | CATEGORIA_SUGERIDA | DESCRICAO_CURTA
 *
 * Sem numeração, sem markdown, sem texto fora desse formato — qualquer
 * explicação adicional da Groq é descartada pela Fase 18.7.
 *
 * Fase 26 — a categoria sugerida pode ser NOVA (fora do conjunto fixo de
 * categorias do app, como "Saúde Mental"); nesse caso a IA entra no catálogo
 * e a nova categoria ganha automaticamente uma aba/chip próprio na tela
 * principal. Por isso o prompt lista as categorias existentes e instrui a
 * Groq a usar uma delas OU propor uma nova apenas quando nenhuma se encaixar.
 *
 * Recebe [nomesJaNoCatalogo] (Fase 18.6) para instruir a Groq a NUNCA
 * sugerir IAs que já existem no `ia_catalogo.json` local — a curadoria
 * só faz sentido para candidatas novas.
 */
object PromptCuradoriaIA {

    private const val FORMATO_LINHA = "NOME | SITE | CATEGORIA_SUGERIDA | DESCRICAO_CURTA"

    fun construir(nomesJaNoCatalogo: List<String>): String {
        val listaExistentes = if (nomesJaNoCatalogo.isEmpty()) {
            "(nenhuma ainda)"
        } else {
            nomesJaNoCatalogo.joinToString(", ")
        }
        // Fase 26 — categorias fixas do app (Fase 3.1/20.2/20.5).
        val categoriasExistentes = Categoria.entries.joinToString(", ") { it.rotulo }

        return """
            Você é um assistente de curadoria para o app AI Brain, um catálogo de inteligências artificiais.
            Quando o usuário pedir para listar ou sugerir IAs, responda APENAS com sugestões de IAs reais que ainda NÃO estejam nesta lista de IAs já cadastradas: $listaExistentes.

            Responda em texto simples, UMA sugestão por linha, exatamente neste formato, sem numeração e sem markdown:
            $FORMATO_LINHA

            Sobre a CATEGORIA_SUGERIDA: use uma das categorias existentes do app ($categoriasExistentes) sempre que a IA se encaixar bem em uma delas. Só proponha uma categoria nova (ex.: "Saúde Mental") quando a IA claramente pertencer a um tema que nenhuma categoria existente cobre.
            Sobre a DESCRICAO_CURTA: escreva em português uma descrição de no máximo 2 frases dizendo o que a IA faz e para que ela serve.

            Não inclua nenhuma linha de introdução, explicação ou conclusão — apenas as linhas de sugestão. Se não houver nenhuma sugestão nova para o pedido, responda com uma única linha vazia.
        """.trimIndent()
    }
}
