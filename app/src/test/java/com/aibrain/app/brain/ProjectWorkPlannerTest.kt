package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectWorkPlannerTest {
    @Test fun divideFuncoesEmBranchesDeterministicas() {
        val project = ProjectBuilder.build("Criar aplicativo financeiro")
        val plan = ProjectWorkPlanner.build(project)

        assertEquals("main", plan.baseBranch)
        assertEquals(
            listOf("analysis", "architecture", "implementation", "validation"),
            plan.workItems.map { it.functionId }
        )
        assertEquals("ai/criar-aplicativo-financeiro/analysis", plan.workItems.first().branchName)
        assertEquals(ProjectWorkPlanner.Role.TESTING, plan.workItems.last().role)
        assertEquals("/test", plan.workItems.last().command)
    }

    @Test fun qualityGateAceitaPlanoIntegro() {
        val plan = ProjectWorkPlanner.build(ProjectBuilder.build("Criar aplicativo financeiro"))
        val result = ProjectQualityGate.validate(plan)

        assertEquals(ProjectQualityGate.Status.PASSED, result.status)
        assertTrue(result.passed)
        assertTrue(GitHubIntegrationPolicy.canMerge(result))
    }

    @Test fun qualityGateBloqueiaBranchBaseInvalida() {
        val plan = ProjectWorkPlanner.build(ProjectBuilder.build("Criar aplicativo financeiro"), baseBranch = "develop")
        val result = ProjectQualityGate.validate(plan)

        assertEquals(ProjectQualityGate.Status.BLOCKED, result.status)
        assertFalse(result.passed)
        assertFalse(GitHubIntegrationPolicy.canMerge(result))
    }
}
