package com.aibrain.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTabStateTest {
    @Test
    fun `cada aba possui identidade e estado de navegacao proprio`() {
        val primeira = AbaNavegador(nomeIA = "ChatGPT", urlAtual = "https://example.com", iconeIA = "chat")
        val segunda = AbaNavegador(nomeIA = "Claude", urlAtual = "https://example.org", iconeIA = "claude")

        assertNotEquals(primeira.id, segunda.id)
        assertNotEquals(primeira.urlAtual, segunda.urlAtual)
        assertEquals(false, primeira.carregando)
        assertEquals(null, primeira.tituloPagina)
    }

    @Test
    fun `estado de titulo loading historico e scroll pode ser atualizado sem alterar outra aba`() {
        val primeira = AbaNavegador(
            nomeIA = "ChatGPT",
            urlAtual = "https://example.com/pagina",
            iconeIA = "chat",
            tituloPagina = "Página atual",
            carregando = true,
            historico = listOf("https://example.com"),
            podeVoltar = true,
            posicaoScroll = 320
        )
        val segunda = AbaNavegador(nomeIA = "Claude", urlAtual = "https://example.org", iconeIA = "claude")

        assertTrue(primeira.carregando)
        assertEquals("Página atual", primeira.tituloPagina)
        assertEquals(listOf("https://example.com"), primeira.historico)
        assertEquals(320, primeira.posicaoScroll)
        assertEquals(false, segunda.carregando)
        assertEquals(0, segunda.posicaoScroll)
    }
}
