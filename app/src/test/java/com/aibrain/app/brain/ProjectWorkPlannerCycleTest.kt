package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProjectWorkPlannerCycleTest {
    @Test fun planoValidoPassaTodasAsVerificacoes() {
        val result = ProjectQualityGate.validate(
            ProjectWorkPlanner.build(ProjectBuilder.build("Criar aplicativo Android"))
        )
        assertEquals(ProjectQualityGate.Status.PASSED, result.status)
    }

    @Test fun dependenciaInvalidaBloqueiaQualityGate() {
        val base = ProjectWorkPlanner.build(ProjectBuilder.build("Criar aplicativo Android"))
        val broken = base.copy(
            workItems = base.workItems.map {
                if (it.functionId == "implementation") it.copy(dependsOn = listOf("inexistente")) else it
            }
        )
        val result = ProjectQualityGate.validate(broken)
        assertEquals(ProjectQualityGate.Status.BLOCKED, result.status)
        assertFalse(result.passed)
    }
}
