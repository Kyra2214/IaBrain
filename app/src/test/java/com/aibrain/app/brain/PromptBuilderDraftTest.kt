package com.aibrain.app.brain

import com.aibrain.app.model.CategoriaPrompt
import com.aibrain.app.model.Prompt
import com.aibrain.app.model.VariavelPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderDraftTest {
    @Test
    fun `detecta placeholders sem duplicar e substitui apenas na preview`() {
        val draft = PromptBuilderDraft(textoLivre = "Olá, {{NOME}}. Crie para {{NOME}} na vaga {{ VAGA }}.")
            .detectarVariaveis()
            .copy(valoresVariaveis = mapOf("NOME" to "Alexandre"))

        assertEquals(listOf("NOME", "VAGA"), draft.variaveis.map { it.nome })
        assertTrue(draft.preview().contains("Olá, Alexandre."))
        assertTrue(draft.preview().contains("{{VAGA}}"))
        assertTrue(draft.textoLivre.contains("{{NOME}}"))
    }

    @Test
    fun `adicionar editar e remover variavel mantem nomes normalizados`() {
        val draft = PromptBuilderDraft()
            .adicionarVariavel(" vaga ")
            .adicionarVariavel("VAGA")
            .editarVariavel("vaga", "cargo", "Android")
            .removerVariavel("CARGO")

        assertTrue(draft.variaveis.isEmpty())
        assertTrue(draft.valoresVariaveis.isEmpty())
    }

    @Test
    fun `modo estruturado gera secoes e conversao preserva metadados`() {
        val draft = PromptBuilderDraft(
            titulo = "Currículo Android",
            categoria = CategoriaPrompt.DOCUMENTOS,
            objetivo = "Criar currículo",
            contexto = "Experiência com Kotlin",
            tarefa = "Analise os dados",
            restricoes = "Não invente experiências",
            formatoSaida = "Resumo e competências",
            iaDestinoId = "claude",
            iaDestinoNome = "Claude",
            comandoRelacionado = "/document"
        )
        val prompt = draft.toPrompt()
        assertTrue(prompt.template.contains("OBJETIVO"))
        assertTrue(prompt.template.contains("RESTRIÇÕES"))
        assertEquals("Claude", prompt.iaDestinoNome)
        assertEquals("/document", prompt.comandoRelacionado)
    }

    @Test
    fun `duplicar nao reutiliza identidade do prompt original`() {
        val original = Prompt("p1", "Original", CategoriaPrompt.ESCRITA, "livre", "desc", "obj", "iniciante", template = "texto", variaveis = listOf(VariavelPrompt("NOME")), dataCriacao = "2026-09-02")
        val duplicado = PromptBuilderDraft.fromPrompt(original, duplicar = true).toPrompt()
        assertNotEquals(original.id, duplicado.id)
        assertTrue(duplicado.titulo.contains("cópia"))
    }
}
