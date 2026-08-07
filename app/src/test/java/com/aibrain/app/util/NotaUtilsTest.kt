package com.aibrain.app.util

import com.aibrain.app.model.Categoria
import com.aibrain.app.model.IA
import org.junit.Assert.assertEquals
import org.junit.Test

/** Fase 12.9 — Testes unitários do cálculo de notas e ranking (Fase 4). */
class NotaUtilsTest {

    private fun criarIA(id: String, notas: Map<String, Int>, categoriaPrincipal: String? = null) = IA(
        id = id,
        nome = id,
        logo = "",
        site = "https://exemplo.com",
        descricao = "Descrição de teste.",
        categorias = notas.keys.toList(),
        idiomas = listOf("pt"),
        gratuita = true,
        notas = notas,
        categoriaPrincipal = categoriaPrincipal
    )

    @Test
    fun `notaMedia calcula a media das notas`() {
        val ia = criarIA("a", mapOf("conversa" to 8, "codigo" to 6))
        assertEquals(7.0, ia.notaMedia(), 0.0001)
    }

    @Test
    fun `notaMedia retorna zero quando nao ha notas`() {
        val ia = criarIA("a", emptyMap())
        assertEquals(0.0, ia.notaMedia(), 0.0001)
    }

    @Test
    fun `rankingGeral ordena pela nota media, maior primeiro`() {
        val fraca = criarIA("fraca", mapOf("conversa" to 4))
        val forte = criarIA("forte", mapOf("conversa" to 9))
        val media = criarIA("media", mapOf("conversa" to 6))

        val ranking = listOf(fraca, forte, media).rankingGeral()

        assertEquals(listOf(forte, media, fraca), ranking)
    }

    @Test
    fun `rankingPorCategoria ignora IAs que nao atuam na categoria`() {
        val comCategoria = criarIA("com", mapOf("video" to 7))
        val semCategoria = criarIA("sem", mapOf("imagem" to 9))

        val ranking = listOf(comCategoria, semCategoria).rankingPorCategoria(Categoria.VIDEO)

        assertEquals(listOf(comCategoria), ranking)
    }

    // ---- Fase 19.3 — priorização de categoriaPrincipal no ranking por categoria ----

    @Test
    fun `rankingPorCategoria prioriza categoriaPrincipal mesmo com nota bruta menor`() {
        val secundariaNotaAlta = criarIA("chatgpt", mapOf("codigo" to 9), categoriaPrincipal = "conversa")
        val principalNotaMenor = criarIA("claude", mapOf("codigo" to 8), categoriaPrincipal = "codigo")

        val ranking = listOf(secundariaNotaAlta, principalNotaMenor).rankingPorCategoria(Categoria.CODIGO)

        assertEquals(listOf(principalNotaMenor, secundariaNotaAlta), ranking)
    }

    @Test
    fun `rankingPorCategoria desempata por nota quando nenhuma tem a categoria como principal`() {
        val nenhumaPrincipal1 = criarIA("a", mapOf("codigo" to 9))
        val nenhumaPrincipal2 = criarIA("b", mapOf("codigo" to 7))

        val ranking = listOf(nenhumaPrincipal2, nenhumaPrincipal1).rankingPorCategoria(Categoria.CODIGO)

        assertEquals(listOf(nenhumaPrincipal1, nenhumaPrincipal2), ranking)
    }

    @Test
    fun `rankingPorCategoria desempata por nota entre duas IAs com a mesma categoriaPrincipal`() {
        val principalNotaAlta = criarIA("a", mapOf("codigo" to 10), categoriaPrincipal = "codigo")
        val principalNotaBaixa = criarIA("b", mapOf("codigo" to 6), categoriaPrincipal = "codigo")

        val ranking = listOf(principalNotaBaixa, principalNotaAlta).rankingPorCategoria(Categoria.CODIGO)

        assertEquals(listOf(principalNotaAlta, principalNotaBaixa), ranking)
    }

    // ---- Fase 15.3 — FaixaAvaliacao / atendeFaixa() ----

    @Test
    fun `atendeFaixa e verdadeiro quando a nota media e igual ao limiar`() {
        val ia = criarIA("a", mapOf("conversa" to 9))
        assertEquals(true, ia.atendeFaixa(FaixaAvaliacao.NOVE_MAIS))
    }

    @Test
    fun `atendeFaixa e verdadeiro quando a nota media e maior que o limiar`() {
        val ia = criarIA("a", mapOf("conversa" to 10, "codigo" to 8))
        assertEquals(true, ia.atendeFaixa(FaixaAvaliacao.OITO_MAIS))
    }

    @Test
    fun `atendeFaixa e falso quando a nota media fica abaixo do limiar`() {
        val ia = criarIA("a", mapOf("conversa" to 6))
        assertEquals(false, ia.atendeFaixa(FaixaAvaliacao.SETE_MAIS))
    }

    @Test
    fun `atendeFaixa DEZ so e verdadeiro com nota media exatamente 10`() {
        val perfeita = criarIA("perfeita", mapOf("conversa" to 10, "codigo" to 10))
        val quaseperfeita = criarIA("quase", mapOf("conversa" to 10, "codigo" to 9))

        assertEquals(true, perfeita.atendeFaixa(FaixaAvaliacao.DEZ))
        assertEquals(false, quaseperfeita.atendeFaixa(FaixaAvaliacao.DEZ))
    }
}
