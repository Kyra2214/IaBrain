package com.aibrain.app.brain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OrchestrationTest {
    @Test fun plannerCriaGrafoValido() {
        val plan = BrainPlanner().plan("Criar aplicativo Android")
        assertTrue(OrchestrationPlanValidator.validate(plan).valid)
        assertTrue(plan.tasks.any { it.requiredCapabilities.contains("CODIGO") })
    }

    @Test fun cicloDeDependenciaERejeitado() {
        val a = OrchestrationTask(id = "a", title = "A", objective = "a", dependsOn = setOf("b"))
        val b = OrchestrationTask(id = "b", title = "B", objective = "b", dependsOn = setOf("a"))
        val result = OrchestrationPlanValidator.validate(OrchestrationPlan("x", tasks = listOf(a, b)))
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("ciclo") })
    }

    @Test fun policyBloqueiaSegredoNoContexto() {
        val errors = OrchestrationPolicyGuard.validateContext("api_key=abc", OrchestrationPolicy())
        assertTrue(errors.isNotEmpty())
    }

    @Test fun engineExecutaDependenciasEConclui() = runTest {
        val plan = BrainPlanner().plan("Criar aplicativo Android")
        val gateway = object : ProviderGateway {
            override suspend fun execute(request: ProviderRequest) = ProviderResponse(true, "resultado válido para ${request.task.title}")
        }
        val (finished, steps) = TaskEngine(gateway).run(plan) { "ia-${it.requiredCapabilities.first()}" }
        assertEquals(OrchestrationTaskStatus.SUCCEEDED, finished.status)
        assertEquals(finished.tasks.size, steps.count { it.status == OrchestrationTaskStatus.SUCCEEDED })
    }

    @Test fun engineBloqueiaQuandoNaoHaIa() = runTest {
        val plan = BrainPlanner().plan("Pesquisar tema")
        val gateway = object : ProviderGateway {
            override suspend fun execute(request: ProviderRequest) = ProviderResponse(true, "não deveria executar")
        }
        val (finished, steps) = TaskEngine(gateway).run(plan) { null }
        assertEquals(OrchestrationTaskStatus.FAILED, finished.status)
        assertTrue(steps.any { it.status == OrchestrationTaskStatus.BLOCKED })
    }
}
