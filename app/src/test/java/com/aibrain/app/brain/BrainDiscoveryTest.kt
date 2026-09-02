package com.aibrain.app.brain

import com.aibrain.app.model.IA
import com.aibrain.app.model.NivelAcesso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainDiscoveryTest {

    private val claude = ia(
        "claude", "Claude", "Assistente para programação e análise de documentos.",
        listOf("conversa", "codigo", "analise"),
        NivelAcesso.FREEMIUM,
        mapOf("codigo" to 10, "analise" to 9),
        casos = listOf("projetos técnicos", "código")
    )
    private val deepseek = ia(
        "deepseek", "DeepSeek", "Modelo de raciocínio para código.",
        listOf("codigo", "raciocinio"),
        NivelAcesso.GRATUITA,
        mapOf("codigo" to 10, "raciocinio" to 10)
    )
    private val perplexity = ia(
        "perplexity", "Perplexity", "Pesquisa com fontes da web.",
        listOf("pesquisa"),
        NivelAcesso.FREEMIUM,
        mapOf("pesquisa" to 10)
    )

    @Test
    fun `busca encontra nome categoria capacidade e descricao`() {
        val catalogo = listOf(claude, deepseek, perplexity)
        assertEquals(listOf(claude), BrainDiscoveryEngine.filtrar(catalogo, BrainCatalogFilter(termo = "documentos")))
        assertEquals(listOf(claude, deepseek), BrainDiscoveryEngine.filtrar(catalogo, BrainCatalogFilter(termo = "programação")))
        assertEquals(listOf(perplexity), BrainDiscoveryEngine.filtrar(catalogo, BrainCatalogFilter(termo = "pesquisa")))
    }

    @Test
    fun `busca sem resultado retorna lista vazia`() {
        assertTrue(BrainDiscoveryEngine.filtrar(listOf(claude), BrainCatalogFilter(termo = "inexistente")).isEmpty())
    }

    @Test
    fun `filtros combinados usam AND e podem ser removidos`() {
        val catalogo = listOf(claude, deepseek, perplexity)
        val codigo = BrainDiscoveryEngine.filtrar(catalogo, BrainCatalogFilter(categoria = "codigo"))
        assertEquals(listOf(claude, deepseek), codigo)
        assertEquals(listOf(deepseek), BrainDiscoveryEngine.filtrar(catalogo, BrainCatalogFilter(categoria = "codigo", acesso = NivelAcesso.GRATUITA)))
        assertEquals(catalogo, BrainDiscoveryEngine.filtrar(catalogo, BrainCatalogFilter()))
    }

    @Test
    fun `ranking orientado a objetivo usa nota e gera justificativa consistente`() {
        val resultado = BrainDiscoveryEngine.recomendar(
            listOf(claude, deepseek),
            "Quero programar um aplicativo",
            favoritos = setOf("claude")
        )
        assertEquals("claude", resultado.melhorOpcao?.ia?.id)
        assertTrue(resultado.melhorOpcao!!.reasons.any { it.contains("Código") })
        assertTrue(resultado.alternativas.any { it.ia.id == "deepseek" })
        assertFalse(resultado.melhorOpcao!!.reasons.any { it.contains("95") })
    }

    @Test
    fun `empate e ausencia de notas nao criam avaliacao inventada`() {
        val semNota = ia("sem-nota", "Sem nota", "Ferramenta", listOf("codigo"), NivelAcesso.PAGA, emptyMap())
        val resultado = BrainDiscoveryEngine.recomendar(listOf(semNota), "programar")
        assertTrue(resultado.resultados.isEmpty())
    }

    @Test
    fun `comparacao mostra lacunas como nao informado e nao preenche suposicoes`() {
        val linhas = BrainDiscoveryEngine.comparar(listOf(claude, perplexity), "pesquisa")
        val pesquisa = linhas.first { it.criterio == "Pesquisa" }
        assertEquals(listOf("—", "10/10"), pesquisa.valores)
        assertEquals(listOf("🟡 Freemium", "🟡 Freemium"), linhas.first { it.criterio == "Acesso" }.valores)
    }

    private fun ia(
        id: String,
        nome: String,
        descricao: String,
        categorias: List<String>,
        acesso: NivelAcesso,
        notas: Map<String, Int>,
        casos: List<String> = emptyList()
    ) = IA(
        id = id,
        nome = nome,
        logo = "",
        site = "https://example.com/$id",
        descricao = descricao,
        categorias = categorias,
        idiomas = listOf("pt"),
        gratuita = acesso != NivelAcesso.PAGA,
        acesso = acesso,
        notas = notas,
        casosDeUso = casos
    )
}
