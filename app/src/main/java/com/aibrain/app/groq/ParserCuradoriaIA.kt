package com.aibrain.app.groq

/**
 * Fase 18.7 — Uma sugestão de nova IA extraída da resposta da Groq.
 * [categoriaSugerida] é texto livre (o que a Groq respondeu) — a Fase 18.8
 * decide como usar isso ao pré-preencher a inserção manual no catálogo.
 */
data class SugestaoIA(
    val nome: String,
    val site: String,
    val categoriaSugerida: String,
    // Fase 26 — descrição curta opcional; o prompt de sistema continua pedindo
    // só 3 campos, então ela entra quando a Groq responder no formato estendido
    val descricao: String = ""
)

/**
 * Fase 18.7 — Parser da resposta estruturada da Groq (formato fixo definido
 * em [PromptCuradoriaIA], Fase 18.6: uma sugestão por linha, campos
 * separados por " | " — `NOME | SITE | CATEGORIA_SUGERIDA`).
 *
 * Nunca lança exceção: linhas malformadas (campos faltando, separador
 * ausente) são simplesmente ignoradas em vez de derrubar a tela do
 * Assistente — o curador só vê as sugestões que deram certo.
 */
object ParserCuradoriaIA {

    private const val SEPARADOR = "|"
    // Fase 18.7 — formato base: `NOME | SITE | CATEGORIA_SUGERIDA`
    private const val CAMPOS_ESPERADOS = 3
    // Fase 26 — formato estendido (com descrição), aceito quando presente:
    // `NOME | SITE | CATEGORIA_SUGERIDA | DESCRICAO`
    private const val CAMPOS_ESTENDIDOS = 4

    /** Retorna a lista de sugestões válidas; vazia se não houver nenhuma ou a resposta for malformada. */
    fun parsear(respostaGroq: String): List<SugestaoIA> {
        return respostaGroq
            .lineSequence()
            .mapNotNull { parsearLinha(it) }
            .toList()
    }

    private fun parsearLinha(linha: String): SugestaoIA? {
        val texto = linha.trim()
        if (texto.isEmpty()) return null

        val campos = texto.split(SEPARADOR).map { it.trim() }
        if (campos.size != CAMPOS_ESPERADOS && campos.size != CAMPOS_ESTENDIDOS) return null

        val (nome, site, categoria) = campos
        if (nome.isEmpty() || site.isEmpty() || categoria.isEmpty()) return null

        return if (campos.size == CAMPOS_ESTENDIDOS) {
            SugestaoIA(
                nome = nome,
                site = site,
                categoriaSugerida = categoria,
                descricao = campos[3]
            )
        } else {
            SugestaoIA(nome = nome, site = site, categoriaSugerida = categoria)
        }
    }
}
