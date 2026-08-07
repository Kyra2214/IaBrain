package com.aibrain.app.groq

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Fase 18.8 — Testes do snippet JSON pré-preenchido gerado a partir de
 * uma [SugestaoIA] para o curador colar manualmente no `ia_catalogo.json`.
 */
class SnippetCatalogoIATest {

    @Test
    fun `gera snippet com id normalizado e campos da sugestao`() {
        val sugestao = SugestaoIA("Perplexity AI", "https://perplexity.ai", "Pesquisa")

        val snippet = JSONObject(SnippetCatalogoIA.gerar(sugestao))

        assertEquals("perplexity_ai", snippet.getString("id"))
        assertEquals("Perplexity AI", snippet.getString("nome"))
        assertEquals("https://perplexity.ai", snippet.getString("site"))
        assertEquals("Pesquisa", snippet.getJSONArray("categorias").getString(0))
        assertFalse(snippet.getBoolean("gratuita"))
    }

    @Test
    fun `id remove acentos e caracteres especiais`() {
        val sugestao = SugestaoIA("Súper-IA 2.0!", "https://exemplo.com", "Automação")

        val snippet = JSONObject(SnippetCatalogoIA.gerar(sugestao))

        assertEquals("super_ia_2_0", snippet.getString("id"))
    }
}
