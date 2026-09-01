package com.aibrain.app.brain

import org.junit.Assert.assertTrue
import org.junit.Test

class PromptGenerationFlowTest {
    @Test fun geraPromptEspecificoComIAComandoECapacidades() {
        val request = RoutingRequest("pesquise Android offline", "/research", requiredCapabilities=setOf("PESQUISA"), context="fontes atuais")
        val decision = LocalAIRouter.route(request, listOf(RoutingCandidate("ia", "Pesquisa", setOf("PESQUISA"), setOf("/research"))))
        val spec = PromptGenerationSpecBuilder.from(request, decision)
        val prompt = ContextualPromptGenerator.generate(spec)
        assertTrue(spec.iaId == "ia" && spec.comando == "/research")
        assertTrue(prompt.contains("/research") && prompt.contains("PESQUISA") && prompt.contains("Pesquisa"))
    }
}
