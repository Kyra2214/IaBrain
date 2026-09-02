package com.aibrain.app.brain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubSecurityPromptBuilderTest {
    @Test
    fun `prompt contains traceability and mandatory security rules`() {
        val prompt = GitHubSecurityPromptBuilder.build(
            objective = "Adicionar uma nova integração",
            issueNumber = "123",
            pullRequest = "456",
            changedAreas = "browser e workspace"
        )

        assertTrue(prompt.contains("Issue: #123"))
        assertTrue(prompt.contains("Pull Request: #456"))
        assertTrue(prompt.contains("NÃO faça merge"))
        assertTrue(prompt.contains("NÃO envie prompts automaticamente"))
        assertTrue(prompt.contains("A decisão final de merge pertence ao responsável humano"))
        assertFalse(prompt.contains("github_pat_"))
    }

    @Test
    fun `blank optional metadata is safe`() {
        val prompt = GitHubSecurityPromptBuilder.build(objective = "Revisar arquitetura")

        assertTrue(prompt.contains("Issue: não informado"))
        assertTrue(prompt.contains("Pull Request: não informado"))
        assertTrue(prompt.contains("identifique as áreas alteradas"))
    }
}
