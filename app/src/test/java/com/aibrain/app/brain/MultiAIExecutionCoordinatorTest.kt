package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiAIExecutionCoordinatorTest {
    @Test fun respeitaDependenciasAntesDeLiberarIA() {
        val plan = ProjectWorkPlanner.build(ProjectBuilder.build("Criar aplicativo"))
        val coordinator = MultiAIExecutionCoordinator(plan)

        var blocked = false
        try { coordinator.claim("implementation", "ia-c") } catch (_: IllegalStateException) { blocked = true }
        assertTrue(blocked)

        coordinator.claim("analysis", "ia-a")
        coordinator.complete("analysis")
        coordinator.claim("architecture", "ia-b")
        assertEquals(MultiAIExecutionCoordinator.Status.RUNNING, coordinator.snapshot()[1].status)
    }

    @Test fun respeitaLimiteDeExecucoesSimultaneas() {
        val plan = ProjectWorkPlanner.build(ProjectBuilder.build("Criar aplicativo"))
        val coordinator = MultiAIExecutionCoordinator(plan, maxConcurrent = 1)
        coordinator.claim("analysis", "ia-a")

        var blocked = false
        try { coordinator.claim("architecture", "ia-b") } catch (_: IllegalStateException) { blocked = true }
        assertTrue(blocked)
    }

    @Test fun exigeBranchIsoladaEIdentificacaoDaIA() {
        val plan = ProjectWorkPlanner.build(ProjectBuilder.build("Criar aplicativo"))
        val coordinator = MultiAIExecutionCoordinator(plan)
        var rejected = false
        try { coordinator.claim("analysis", " ") } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertEquals("ai/criar-aplicativo/analysis", coordinator.snapshot().first().branchName)
    }
}
