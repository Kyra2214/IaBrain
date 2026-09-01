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
        assertTrue(!prompt.contains("MODO DE EXECUÇÃO SILENCIOSA"))
    }

    @Test fun promptDeImplementacaoComecaComPadraoUniversalUmaVez() {
        val spec = PromptGenerationSpec("Criar aplicativo Android", "ia-code", "IA Código", "/implement", setOf("CODIGO"), isDevelopmentPrompt = true, fase = "FASE 1", modulo = "MÓDULO 1", submodulo = "SUBMÓDULO 1", pesoSubmodulo = "1")
        val prompt = ContextualPromptGenerator.generate(spec)
        assertTrue(prompt.startsWith(DeveloperPromptStandard.HEADER))
        assertTrue(prompt.contains("PROJETO") && prompt.contains("IMPLEMENTAÇÃO") && prompt.contains("CRITÉRIOS DE CONCLUSÃO"))
        assertTrue(prompt.indexOf("MODO DE EXECUÇÃO SILENCIOSA") == prompt.lastIndexOf("MODO DE EXECUÇÃO SILENCIOSA"))
    }
}
