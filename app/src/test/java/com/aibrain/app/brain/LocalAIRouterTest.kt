package com.aibrain.app.brain

import com.aibrain.app.command.SlashCommandParser
import org.junit.Assert.*
import org.junit.Test

class LocalAIRouterTest {
    private val direto = RoutingCandidate("a", "Direta", supportedCommands=setOf("/research"), capabilities=setOf("PESQUISA"), quality=.5)
    private val generica = RoutingCandidate("b", "Genérica", capabilities=setOf("PESQUISA"), quality=.5)

    @Test fun comandoDiretoVenceGenerica() {
        val d = LocalAIRouter.route(
            RoutingRequest("/research x", "/research", requiredCapabilities=setOf("PESQUISA")),
            listOf(generica, direto)
        )
        assertEquals("a", d.selectedAI?.iaId)
    }

    @Test fun capacidadePermiteCandidataSemComando() {
        val d = LocalAIRouter.route(
            RoutingRequest("/research x", "/research", requiredCapabilities=setOf("PESQUISA")),
            listOf(generica)
        )
        assertEquals(RoutingStatus.SELECTED, d.status)
    }

    @Test fun capacidadeExigidaEHardRequirement() {
        val semCodigo = RoutingCandidate("a", "Sem código", capabilities=setOf("PESQUISA"), quality=1.0)
        val comCodigo = RoutingCandidate("b", "Com código", capabilities=setOf("PESQUISA", "CODIGO"), quality=.4)
        val d = LocalAIRouter.route(
            RoutingRequest("implementar", null, requiredCapabilities=setOf("PESQUISA", "CODIGO")),
            listOf(semCodigo, comCodigo)
        )
        assertEquals(RoutingStatus.SELECTED, d.status)
        assertEquals("b", d.selectedAI?.iaId)
    }

    @Test fun nenhumaCandidataComTodasCapacidadesRetornaIncompativel() {
        val d = LocalAIRouter.route(
            RoutingRequest("x", null, requiredCapabilities=setOf("CODIGO", "IMAGEM")),
            listOf(RoutingCandidate("a", "IA", capabilities=setOf("CODIGO")))
        )
        assertEquals(RoutingStatus.NO_COMPATIBLE_PROVIDER, d.status)
        assertNull(d.selectedAI)
        assertEquals(0.0, d.confidence, 0.0)
    }

    @Test fun iaPreferidaDesempata() {
        val a = RoutingCandidate("a", "IA A", capabilities=setOf("PESQUISA"), quality=.8)
        val b = RoutingCandidate("b", "IA B", capabilities=setOf("PESQUISA"), quality=.8)
        val d = LocalAIRouter.route(
            RoutingRequest("x", null, requiredCapabilities=setOf("PESQUISA"), preferredAIIds=setOf("b")),
            listOf(a, b)
        )
        assertEquals("b", d.selectedAI?.iaId)
        assertTrue(d.reasons.any { it.contains("IA preferida") })
    }

    @Test fun resultadoDeterministicoEmEmpate() {
        val a = RoutingCandidate("a", "IA A", quality=.8)
        val b = RoutingCandidate("b", "IA B", quality=.8)
        val d = LocalAIRouter.route(RoutingRequest("x", null), listOf(b, a))
        assertEquals("a", d.selectedAI?.iaId)
    }

    @Test fun retornaTopTresEConfiancaHeuristica() {
        val cs = (1..4).map { RoutingCandidate("$it", "IA$it", quality=it/4.0) }
        val d = LocalAIRouter.route(RoutingRequest("x", null), cs)
        assertEquals(3, d.alternatives.size)
        assertTrue(d.confidence >= 0)
    }

    @Test fun candidatoUnicoTemConfiancaTotal() {
        val d = LocalAIRouter.route(RoutingRequest("x", null), listOf(RoutingCandidate("a", "IA")))
        assertEquals(1.0, d.confidence, 0.0)
    }

    @Test fun parserPreservaParametros() {
        val p = SlashCommandParser.parse("/research tema=\"Android offline\" profundidade=alta")!!
        assertEquals("Android offline", p.parametros["tema"])
        assertEquals("alta", p.parametros["profundidade"])
    }

    @Test fun nenhumCandidatoNaoFazCrash() {
        val d = LocalAIRouter.route(RoutingRequest("x", null), emptyList())
        assertEquals(RoutingStatus.NO_COMPATIBLE_PROVIDER, d.status)
    }
}
