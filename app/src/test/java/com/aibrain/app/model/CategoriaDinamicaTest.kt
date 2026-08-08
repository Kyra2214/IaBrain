package com.aibrain.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fase 26 — Testes do rótulo exibível de categorias, cobrindo tanto as
 * categorias fixas do enum [Categoria] quanto as categorias novas criadas
 * pela curadoria (que ganham chip/aba próprio na tela principal).
 */
class CategoriaDinamicaTest {

    @Test
    fun `categorias fixas usam emoji e rotulo do enum`() {
        assertEquals("💬 Conversa", CategoriaDinamica.rotulo("conversa"))
        assertEquals("🎵 Música", CategoriaDinamica.rotulo("musica"))
        assertEquals("🔎 Pesquisa", CategoriaDinamica.rotulo("pesquisa"))
    }

    @Test
    fun `rotuloCurto de categoria fixa nao inclui emoji`() {
        assertEquals("Conversa", CategoriaDinamica.rotuloCurto("conversa"))
    }

    @Test
    fun `categoria nova e capitalizada no rotulo`() {
        // A Groq pode responder com acentos ("saúde mental") ou sem ("saude
        // mental"); o rótulo capitaliza preservando exatamente os caracteres
        // da entrada.
        assertEquals("Saúde Mental", CategoriaDinamica.rotulo("saúde mental"))
        assertEquals("Saude Mental", CategoriaDinamica.rotulo("saude mental"))
        assertEquals("Saude Mental", CategoriaDinamica.rotuloCurto("saude mental"))
    }

    @Test
    fun `categoria nova capitaliza todas as palavras`() {
        assertEquals("Bem Estar", CategoriaDinamica.rotulo("bem estar"))
        assertEquals("Inteligência Artificial Para Educação", CategoriaDinamica.rotulo("inteligência artificial para educação"))
    }

    @Test
    fun `texto em branco e vazio retorna string vazia`() {
        assertEquals("", CategoriaDinamica.rotulo(""))
        assertEquals("", CategoriaDinamica.rotulo("   "))
    }
}
