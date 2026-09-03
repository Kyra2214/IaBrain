package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectExecutionTest {
    @Test
    fun selectedProjectProviderWinsWhenCapabilityIsEqual() {
        val request = RoutingRequest(
            rawUserRequest = "implementar código",
            canonicalCommand = "/develop",
            requiredCapabilities = setOf("codigo"),
            preferredCapabilities = setOf("projeto-selecionado")
        )
        val candidates = listOf(
            RoutingCandidate("generic", "Genérica", capabilities = setOf("codigo"), quality = 0.9),
            RoutingCandidate("project", "Selecionada", capabilities = setOf("codigo"), specialties = setOf("projeto-selecionado"), quality = 0.7)
        )
        val decision = LocalAIRouter.route(request, candidates)
        assertEquals("project", decision.selectedAI?.iaId)
        assertTrue(decision.confidence >= 0.0)
    }

    @Test
    fun blockedStateIsExplicitAndDoesNotPretendExecutionStarted() {
        val state = ProjectExecutionState("p", "f", ProjectExecutionStatus.BLOCKED, reason = "dependência")
        assertEquals(ProjectExecutionStatus.BLOCKED, state.status)
        assertEquals(null, state.executionId)
        assertTrue(state.reason.contains("dependência"))
    }

    @Test
    fun completedStateHasStableTerminalStatus() {
        val state = ProjectExecutionState("p", "f", ProjectExecutionStatus.COMPLETED, executionId = "e1")
        assertEquals(ProjectExecutionStatus.COMPLETED, state.status)
        assertEquals("e1", state.executionId)
    }
}
