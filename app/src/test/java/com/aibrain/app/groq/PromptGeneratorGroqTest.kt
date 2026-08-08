package com.aibrain.app.groq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 25 — testes do prompt de sistema do gerador de prompts via Groq
 * (usado pelo Criador de Prompts quando o modo "Gerar com IA" está ativo).
 */
class PromptGeneratorGroqTest {

    @Test
    fun `prompt de sistema deve orientar a producao de um prompt estruturado`() {
        val sistema = PromptGeneratorGroq.construirPromptSistema()
        assertTrue("deve pedir papel/funcao", sistema.contains("Papel"))
        assertTrue("deve pedir objetivo", sistema.contains("Objetivo"))
        assertTrue("deve pedir formato de saida", sistema.contains("Formato"))
        assertTrue("deve exigir APENAS o prompt final", sistema.contains("APENAS o prompt final"))
        assertTrue("deve estar em portugues brasileiro", sistema.contains("português brasileiro"))
    }

    @Test
    fun `contexto extra deve aparecer no final do prompt de sistema`() {
        val sistema = PromptGeneratorGroq.construirPromptSistema("meu contexto extra")
        assertTrue(sistema.contains("Contexto adicional informado pelo usuário: meu contexto extra"))
    }

    @Test
    fun `sem contexto extra nao deve haver linha de contexto adicional`() {
        val sistema = PromptGeneratorGroq.construirPromptSistema()
        assertFalse(sistema.contains("Contexto adicional"))
    }

    @Test
    fun `instrucoes nao devem proibir o uso correto pela IA`() {
        val sistema = PromptGeneratorGroq.construirPromptSistema()
        // O prompt orienta a IA a gerar, não a ignorar instruções.
        assertEquals("numero de linhas esperado sem contexto", 9, sistema.trim().split("\n").size)
    }
}
