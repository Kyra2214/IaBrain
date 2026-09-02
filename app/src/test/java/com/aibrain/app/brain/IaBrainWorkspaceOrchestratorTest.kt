package com.aibrain.app.brain

import org.junit.Assert.*
import org.junit.Test

class IaBrainWorkspaceOrchestratorTest {
    @Test fun `prompt actions never allow automatic send`() {
        val actions = IaBrainWorkspaceOrchestrator.promptActions("teste", "chat", "Chat")
        assertTrue(actions.canCopy)
        assertTrue(actions.canSave)
        assertTrue(actions.canOpenAI)
        assertFalse(actions.canSendAutomatically)
    }

    @Test fun `prefill only succeeds with confirmed capability`() {
        assertEquals(PrefillResult.PREFILLED, IaBrainWorkspaceOrchestrator.prefillResult(PrefillCapability.CONFIRMED, true, true))
        assertEquals(PrefillResult.FALLBACK_COPY_OPEN, IaBrainWorkspaceOrchestrator.prefillResult(PrefillCapability.CONFIRMED, true, false))
        assertEquals(PrefillResult.NOT_SUPPORTED, IaBrainWorkspaceOrchestrator.prefillResult(PrefillCapability.UNKNOWN, true, true))
    }

    @Test fun `workspace validation exposes failures`() {
        val report = IaBrainWorkspaceOrchestrator.validateWorkspace(
            listOf(
                WorkspaceValidationInput("estrutura", true, "ok"),
                WorkspaceValidationInput("imports", false, "import ausente")
            )
        )
        assertTrue(report.hasFailure)
        assertFalse(report.isComplete)
    }

    @Test fun `multi ai plan is deterministic by score then name`() {
        val plan = IaBrainWorkspaceOrchestrator.buildMultiAiPlan(
            "implementar",
            listOf(
                AiCandidate("2", "Beta", 0.9, "especialista"),
                AiCandidate("1", "Alpha", 0.9, "especialista"),
                AiCandidate("3", "Gamma", 0.7, "alternativa")
            )
        )
        assertEquals(listOf("Alpha", "Beta", "Gamma"), plan.candidates.map { it.name })
        assertTrue(plan.requiresUserApproval)
    }

    @Test fun `skill execution never sends automatically`() {
        val run = IaBrainWorkspaceOrchestrator.runSkill(
            SkillDefinition("review", "Revisar", listOf("estrutura", "testes")),
            "projeto"
        )
        assertFalse(run.automaticSend)
        assertEquals(2, run.steps.size)
    }

    @Test fun `memory gets a timestamp`() {
        val entry = IaBrainWorkspaceOrchestrator.addMemory(
            ProjectMemoryEntry("1", "p", MemoryType.DECISION, "decisão", "Room")
        )
        assertTrue(entry.createdAt > 0)
    }

    @Test fun `new task starts pending`() {
        val task = IaBrainWorkspaceOrchestrator.createTask("Validar projeto", "p")
        assertEquals(TaskStatus.PENDING, task.status)
        assertEquals(TaskPriority.NORMAL, task.priority)
        assertFalse(task.id.isBlank())
    }
}
