package com.aibrain.app.groq

import com.aibrain.app.model.NivelAcesso
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 18.8 / 26 — Testes da geração de IA a partir de uma [SugestaoIA]:
 * snippet JSON (curadoria manual) e [SnippetCatalogoIA.paraIA] (persistência
 * real na Fase 26, quando o curador toca em "Adicionar ao catálogo").
 */
class SnippetCatalogoIATest {

    @Test
    fun `gera snippet com id normalizado e campos da sugestao`() {
        val sugestao = SugestaoIA("Perplexity AI", "https://perplexity.ai", "Pesquisa")

        val snippet = JSONObject(SnippetCatalogoIA.gerar(sugestao))

        assertEquals("curada-perplexity-perplexity-ai", snippet.getString("id"))
        assertEquals("Perplexity AI", snippet.getString("nome"))
        assertEquals("https://perplexity.ai", snippet.getString("site"))
        // A categoria sugerida é mapeada para a chave fixa do enum ("Pesquisa" → "pesquisa").
        assertEquals("pesquisa", snippet.getJSONArray("categorias").getString(0))
        assertFalse(snippet.getBoolean("gratuita"))
    }

    @Test
    fun `paraIA gera IA completa com favicon como logo`() {
        val sugestao = SugestaoIA("Suno", "https://suno.com", "Música")

        val ia = SnippetCatalogoIA.paraIA(sugestao)

        assertEquals("curada-suno-suno", ia.id)
        assertEquals("Suno", ia.nome)
        assertEquals("https://suno.com", ia.site)
        assertEquals(listOf("musica"), ia.categorias)
        assertEquals(listOf("en"), ia.idiomas)
        assertEquals(mapOf("musica" to 5), ia.notas)
        assertEquals("musica", ia.categoriaPrincipal)
        assertEquals(NivelAcesso.PAGA, ia.acesso)
        assertTrue(ia.logo.contains("favicons?domain=suno.com"))
    }

    @Test
    fun `paraIA usa descricao sugerida quando presente`() {
        val sugestao = SugestaoIA(
            "Bem Estar IA",
            "https://bemestar-ia.com",
            "Saúde Mental",
            "Assistente de terapia com diário de humor."
        )

        val ia = SnippetCatalogoIA.paraIA(sugestao)

        assertEquals("Assistente de terapia com diário de humor.", ia.descricao)
        assertEquals(listOf("Saúde Mental"), ia.categorias)
    }

    @Test
    fun `paraIA usa placeholder quando a Groq nao responde descricao`() {
        val sugestao = SugestaoIA("Bem Estar IA", "https://bemestar-ia.com", "Saúde Mental")

        val ia = SnippetCatalogoIA.paraIA(sugestao)

        assertTrue(ia.descricao.contains("curadoria"))
    }

    @Test
    fun `categoria existente do app e mapeada para a chave fixa`() {
        // "Pesquisa" (rotulo do enum) vira a chave "pesquisa", não o rotulo.
        val sugestao = SugestaoIA("Perplexity", "https://perplexity.ai", "Pesquisa")
        assertEquals("pesquisa", SnippetCatalogoIA.categoriaSugerida(sugestao))

        // Acentos e maiúsculas também casam com a categoria fixa.
        val sugestao2 = SugestaoIA("X", "https://x.com", "AUTOMAÇÃO")
        assertEquals("automacao", SnippetCatalogoIA.categoriaSugerida(sugestao2))
    }

    @Test
    fun `categoria nova nao mapeada e mantida capitalizada`() {
        // "saude mental" não existe no enum fixo -> chave textual "Saúde Mental",
        // que ganha chip/aba próprio na tela principal (Fase 26).
        val sugestao = SugestaoIA("Bem Estar IA", "https://bemestar-ia.com", "saude mental")
        // A capitalização preserva os caracteres da entrada (sem acento →
        // "Saude Mental"); quando a Groq responde com acento ("saúde mental")
        // o rótulo exibido fica "Saúde Mental". O chip/aba novo é criado
        // automaticamente na tela principal (Fase 26).
        assertEquals("Saude Mental", SnippetCatalogoIA.categoriaSugerida(sugestao))
    }

    @Test
    fun `categoria nova com acento e preservada`() {
        val sugestao = SugestaoIA("Bem Estar IA", "https://bemestar-ia.com", "saúde mental")
        assertEquals("Saúde Mental", SnippetCatalogoIA.categoriaSugerida(sugestao))
    }

    @Test
    fun `categoria vazia cai no fallback outras`() {
        val sugestao = SugestaoIA("X", "https://x.com", "   ")
        assertEquals("Outras", SnippetCatalogoIA.categoriaSugerida(sugestao))
    }

    @Test
    fun `limite de tamanho de descricao e nome aplicado na IA`() {
        val sugestao = SugestaoIA("X".repeat(300), "https://x.com", "Pesquisa", "D".repeat(500))

        val ia = SnippetCatalogoIA.paraIA(sugestao)

        assertEquals(80, ia.nome.length)
        assertEquals(280, ia.descricao.length)
    }
}
