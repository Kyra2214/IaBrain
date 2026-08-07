package com.aibrain.app.groq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 18.7 — Testes do parser da resposta estruturada da Groq
 * (formato fixo da Fase 18.6: `NOME | SITE | CATEGORIA_SUGERIDA`).
 */
class ParserCuradoriaIATest {

    @Test
    fun `parseia linhas validas no formato esperado`() {
        val resposta = """
            Perplexity | https://perplexity.ai | Pesquisa
            Suno | https://suno.com | Música
        """.trimIndent()

        val sugestoes = ParserCuradoriaIA.parsear(resposta)

        assertEquals(2, sugestoes.size)
        assertEquals(SugestaoIA("Perplexity", "https://perplexity.ai", "Pesquisa"), sugestoes[0])
        assertEquals(SugestaoIA("Suno", "https://suno.com", "Música"), sugestoes[1])
    }

    @Test
    fun `ignora linhas malformadas sem lancar excecao`() {
        val resposta = """
            Aqui estão algumas sugestões:
            Perplexity | https://perplexity.ai | Pesquisa
            Nome sem separador completo | https://exemplo.com
            | https://exemplo.com | Categoria
        """.trimIndent()

        val sugestoes = ParserCuradoriaIA.parsear(resposta)

        assertEquals(1, sugestoes.size)
        assertEquals("Perplexity", sugestoes[0].nome)
    }

    @Test
    fun `resposta vazia ou em branco retorna lista vazia`() {
        assertTrue(ParserCuradoriaIA.parsear("").isEmpty())
        assertTrue(ParserCuradoriaIA.parsear("   \n   ").isEmpty())
    }

    @Test
    fun `resposta totalmente malformada nunca lanca excecao`() {
        val resposta = "isso não tem formato nenhum de sugestão"
        assertTrue(ParserCuradoriaIA.parsear(resposta).isEmpty())
    }
}
