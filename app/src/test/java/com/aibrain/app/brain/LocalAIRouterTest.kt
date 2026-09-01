package com.aibrain.app.brain

import com.aibrain.app.command.SlashCommandParser
import org.junit.Assert.*
import org.junit.Test

class LocalAIRouterTest {
    private val direto = RoutingCandidate("a", "Direta", supportedCommands=setOf("/research"), capabilities=setOf("PESQUISA"), quality=.5)
    private val generica = RoutingCandidate("b", "Genérica", capabilities=setOf("PESQUISA"), quality=.5)
    @Test fun comandoDiretoVenceGenerica() { val d=LocalAIRouter.route(RoutingRequest("/research x","/research",requiredCapabilities=setOf("PESQUISA")),listOf(generica,direto)); assertEquals("a",d.selectedAI?.iaId) }
    @Test fun capacidadePermiteCandidataSemComando() { val d=LocalAIRouter.route(RoutingRequest("/research x","/research",requiredCapabilities=setOf("PESQUISA")),listOf(generica)); assertEquals(RoutingStatus.SELECTED,d.status) }
    @Test fun retornaTopTresEConfiancaHeuristica() { val cs=(1..4).map { RoutingCandidate("$it","IA$it",quality=it/4.0) }; val d=LocalAIRouter.route(RoutingRequest("x",null),cs); assertEquals(3,d.alternatives.size); assertTrue(d.confidence>=0) }
    @Test fun parserPreservaParametros() { val p=SlashCommandParser.parse("/research tema=\"Android offline\" profundidade=alta")!!; assertEquals("Android offline",p.parametros["tema"]); assertEquals("alta",p.parametros["profundidade"]) }
    @Test fun nenhumCandidatoNaoFazCrash() { val d=LocalAIRouter.route(RoutingRequest("x",null),emptyList()); assertEquals(RoutingStatus.NO_COMPATIBLE_PROVIDER,d.status) }
}
