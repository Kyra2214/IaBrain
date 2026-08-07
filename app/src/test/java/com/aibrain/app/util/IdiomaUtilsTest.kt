package com.aibrain.app.util

import com.aibrain.app.model.IA
import org.junit.Assert.assertEquals
import org.junit.Test

/** Fase 15.2 — Testes unitários do helper de idioma por filtro. */
class IdiomaUtilsTest {

    private fun criarIA(idiomas: List<String>) = IA(
        id = "ia",
        nome = "IA",
        logo = "",
        site = "https://exemplo.com",
        descricao = "Descrição de teste.",
        categorias = listOf("conversa"),
        idiomas = idiomas,
        gratuita = true,
        notas = mapOf("conversa" to 8)
    )

    @Test
    fun `apenas ingles classifica como Ingles`() {
        val ia = criarIA(listOf("en"))
        assertEquals(FiltroIdioma.INGLES, ia.filtroIdioma())
    }

    @Test
    fun `ingles e portugues classifica como Portugues`() {
        val ia = criarIA(listOf("en", "pt"))
        assertEquals(FiltroIdioma.PORTUGUES, ia.filtroIdioma())
    }

    @Test
    fun `tres idiomas ou mais classifica como Multilingue mesmo com portugues`() {
        val ia = criarIA(listOf("en", "es", "pt"))
        assertEquals(FiltroIdioma.MULTILINGUE, ia.filtroIdioma())
    }

    @Test
    fun `sem portugues e sem atingir tres idiomas cai em Ingles`() {
        val ia = criarIA(listOf("en", "es"))
        assertEquals(FiltroIdioma.INGLES, ia.filtroIdioma())
    }
}
