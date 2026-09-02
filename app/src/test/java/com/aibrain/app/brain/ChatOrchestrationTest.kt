package com.aibrain.app.brain

import com.aibrain.app.command.SlashCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatOrchestrationTest {
    @Test
    fun `texto livre identifica comandos reais do catalogo`() {
        assertEquals("/implement", TextoLivreIntent.commandFor("Quero criar um aplicativo Android em Kotlin"))
        assertEquals("/research", TextoLivreIntent.commandFor("Preciso pesquisar artigos científicos sobre energia solar"))
        assertEquals("/creative", TextoLivreIntent.commandFor("Quero criar uma imagem de um dragão medieval"))
        assertEquals("/document", TextoLivreIntent.commandFor("Preciso escrever um currículo profissional"))
        assertEquals("/analyzedata", TextoLivreIntent.commandFor("Quero analisar uma planilha"))
    }

    @Test
    fun `comando explicito preserva prioridade e ambiguidade nao seleciona IA`() {
        assertEquals("/implement", SlashCommandParser.parse("/implement criar aplicativo Android")?.comando)
        assertEquals(null, TextoLivreIntent.commandFor("Quero criar uma coisa para minha empresa"))
    }

    @Test
    fun `capacidades reais alimentam ranking deterministico e alternativas`() {
        val request = LocalAIRouter.request(
            "Quero criar um aplicativo Android",
            SlashCommandParser.parse("/implement criar um aplicativo Android"),
            required = setOf("CODIGO")
        )
        val decision = LocalAIRouter.route(
            request,
            listOf(
                RoutingCandidate("claude", "Claude", capabilities = setOf("CODIGO"), quality = 0.9),
                RoutingCandidate("chatgpt", "ChatGPT", capabilities = setOf("CONVERSA"), quality = 0.8)
            )
        )
        assertEquals("claude", decision.selectedAI?.iaId)
        assertEquals(1, decision.alternatives.size)
        assertTrue(decision.reasons.any { it.contains("capacidades exigidas") })
    }

    @Test
    fun `prompt contextual preserva pergunta comando e IA selecionada`() {
        val request = RoutingRequest(
            rawUserRequest = "Quero criar um aplicativo Android em Kotlin",
            canonicalCommand = "/implement",
            requiredCapabilities = setOf("CODIGO")
        )
        val decision = LocalAIRouter.route(request, listOf(RoutingCandidate("claude", "Claude", setOf("CODIGO"))))
        val spec = PromptGenerationSpecBuilder.from(request, decision)
        val prompt = ContextualPromptGenerator.generate(spec)
        assertTrue(prompt.contains(request.rawUserRequest))
        assertTrue(prompt.contains("/implement"))
        assertTrue(prompt.contains("Claude"))
    }

    @Test
    fun `contrato sem prefill confirmado permanece open only`() {
        val contract = IAOpenContract("claude", "Claude", "https://claude.ai", UrlResolutionStatus.RESOLVED, "prompt")
        assertEquals(BrowserOpenMode.OPEN_ONLY, contract.openMode)
        assertFalse(contract.canPrefillPrompt)
        assertEquals(PrefillCapability.UNKNOWN, contract.prefillCapability)
    }
}
