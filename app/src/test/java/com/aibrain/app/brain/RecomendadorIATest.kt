package com.aibrain.app.brain

import com.aibrain.app.model.Categoria
import com.aibrain.app.model.IA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fase 12.9 — Testes unitários do AI Brain (Fase 9): detecção de categoria
 * por palavra-chave e a recomendação estruturada (melhor opção / segunda
 * opção / alternativas gratuitas).
 */
class RecomendadorIATest {

    @Test
    fun `detecta categoria de video a partir de frase livre`() {
        assertEquals(Categoria.VIDEO, detectarCategoria("quero criar um vídeo para o youtube"))
    }

    @Test
    fun `deteccao ignora acentuacao e caixa`() {
        assertEquals(Categoria.TRADUCAO, detectarCategoria("PRECISO TRADUZIR um texto pro INGLES"))
    }

    @Test
    fun `texto sem correspondencia nao detecta categoria`() {
        assertNull(detectarCategoria("blablabla xyz 123"))
    }

    @Test
    fun `texto em branco nao detecta categoria`() {
        assertNull(detectarCategoria("   "))
    }

    @Test
    fun `recomendar retorna melhor e segunda opcao ordenadas pela nota da categoria`() {
        val runwayml = criarIA("runway", "RunwayML", listOf("video"), true, mapOf("video" to 7))
        val capcut = criarIA("capcut", "CapCut", listOf("video"), true, mapOf("video" to 9))
        val premiere = criarIA("premiere", "Premiere", listOf("video"), false, mapOf("video" to 10))
        val catalogo = listOf(runwayml, capcut, premiere)

        val recomendacao = catalogo.recomendar("quero editar um vídeo")

        assertEquals(Categoria.VIDEO, recomendacao.categoriaDetectada)
        assertEquals(premiere, recomendacao.melhorOpcao)
        assertEquals(capcut, recomendacao.segundaOpcao)
        assertEquals(listOf(capcut, runwayml), recomendacao.alternativasGratuitas)
    }

    @Test
    fun `recomendar sem categoria detectada retorna tudo nulo ou vazio`() {
        val catalogo = listOf(criarIA("x", "X", listOf("video"), true, mapOf("video" to 5)))

        val recomendacao = catalogo.recomendar("xyz sem sentido")

        assertNull(recomendacao.categoriaDetectada)
        assertEquals(emptyList<Categoria>(), recomendacao.categoriasDetectadas)
        assertNull(recomendacao.melhorOpcao)
        assertNull(recomendacao.segundaOpcao)
        assertEquals(emptyList<IA>(), recomendacao.alternativasGratuitas)
    }

    // Fase 13.2 — match exato na chave/rótulo da categoria pesa mais que palavra-chave auxiliar.
    @Test
    fun `match exato no nome da categoria desempata a favor dela mesma`() {
        // Antes da Fase 13.2 (peso uniforme) isso empatava: 3 palavras-chave auxiliares de
        // Produtividade ("organizar", "tarefas", "agenda") vs. 3 ocorrências de "escrita"
        // (chave + rótulo + palavra-chave). Com o peso maior no match exato, Escrita vence.
        assertEquals(Categoria.ESCRITA, detectarCategoria("organizar tarefas na agenda para escrita"))
    }

    // Fase 13.3 — detecta as 2 categorias mais fortes de uma consulta mista.
    @Test
    fun `detectarCategorias reconhece ate 2 categorias em consulta mista`() {
        val categorias = detectarCategorias("quero criar um vídeo com voz narrada")
        assertEquals(2, categorias.size)
        assert(categorias.contains(Categoria.VIDEO))
        assert(categorias.contains(Categoria.VOZ))
    }

    @Test
    fun `recomendar com consulta mista mescla ranking das 2 categorias`() {
        val heygen = criarIA("heygen", "HeyGen", listOf("video", "voz"), true, mapOf("video" to 9, "voz" to 8))
        val elevenlabs = criarIA("elevenlabs", "ElevenLabs", listOf("voz"), true, mapOf("voz" to 10))
        val runway = criarIA("runway", "Runway", listOf("video"), true, mapOf("video" to 7))
        val catalogo = listOf(heygen, elevenlabs, runway)

        val recomendacao = catalogo.recomendar("quero criar vídeo com voz")

        // ElevenLabs entra com nota 10 (voz), a maior entre as categorias detectadas.
        assertEquals(elevenlabs, recomendacao.melhorOpcao)
        assertEquals(heygen, recomendacao.segundaOpcao)
    }

    // Fase 13.4 — fallback traz sugestão de termos quando nada é detectado.
    @Test
    fun `recomendar sem categoria detectada sugere termos reconhecidos`() {
        val catalogo = listOf(criarIA("x", "X", listOf("video"), true, mapOf("video" to 5)))

        val recomendacao = catalogo.recomendar("xyz sem sentido")

        assert(recomendacao.sugestaoTermos.isNotEmpty())
    }

    @Test
    fun `gerarDescricaoCurta nao corta texto dentro do limite`() {
        assertEquals("Texto curto.", gerarDescricaoCurta("Texto curto.", maxCaracteres = 140))
    }

    @Test
    fun `gerarDescricaoCurta corta no ultimo espaco e adiciona reticencias`() {
        val original = "Esta e uma descricao bem longa que certamente ultrapassa o limite definido para o teste"
        val resultado = gerarDescricaoCurta(original, maxCaracteres = 30)

        assert(resultado.length <= 31) // 30 + "…"
        assert(resultado.endsWith("…"))
        assert(!resultado.contains("  "))
    }

    private fun criarIA(
        id: String,
        nome: String,
        categorias: List<String>,
        gratuita: Boolean,
        notas: Map<String, Int>
    ) = IA(
        id = id,
        nome = nome,
        logo = "",
        site = "https://exemplo.com",
        descricao = "Descrição de teste para $nome.",
        categorias = categorias,
        idiomas = listOf("pt"),
        gratuita = gratuita,
        notas = notas
    )
}
