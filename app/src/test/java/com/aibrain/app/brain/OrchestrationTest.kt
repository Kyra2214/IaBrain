package com.aibrain.app.brain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrchestrationTest {
    @Test
    fun plannerCriaGrafoValido() {
        val plan = BrainPlanner().plan("Criar aplicativo Android")
        assertTrue(OrchestrationPlanValidator.validate(plan).valid)
        assertTrue(plan.tasks.any { it.requiredCapabilities.contains("CODIGO") })
        assertEquals(4, plan.tasks.size)
    }

    @Test
    fun cicloDeDependenciaERejeitado() {
        val a = OrchestrationTask(id = "a", title = "A", objective = "a", dependsOn = setOf("b"))
        val b = OrchestrationTask(id = "b", title = "B", objective = "b", dependsOn = setOf("a"))
        val result = OrchestrationPlanValidator.validate(OrchestrationPlan("x", tasks = listOf(a, b)))
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("ciclo") })
    }

    @Test
    fun policyBloqueiaSegredoNoContexto() {
        val errors = OrchestrationPolicyGuard.validateContext("api_key=abc", OrchestrationPolicy())
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun executaPlanoCompletoRespeitandoDependenciasERegistraHistorico() = runTest {
        val plan = BrainPlanner().plan("Criar aplicativo Android")
        val executionOrder = mutableListOf<String>()
        val history = OrchestrationHistory()
        val gateway = object : ProviderGateway {
            override suspend fun execute(request: ProviderRequest): ProviderResponse {
                executionOrder += request.task.title
                return ProviderResponse(true, "resultado válido para ${request.task.title}")
            }
        }

        val (finished, steps) = TaskEngine(gateway, history = history).run(plan) {
            "ia-${it.requiredCapabilities.first()}"
        }

        assertEquals(OrchestrationTaskStatus.SUCCEEDED, finished.status)
        assertTrue(finished.tasks.all { it.status == OrchestrationTaskStatus.SUCCEEDED })
        assertEquals(finished.tasks.size, steps.size)
        assertEquals(finished.tasks.size, history.all(plan.id).size)
        assertEquals(
            listOf("Entender objetivo", "Projetar solução", "Produzir resultado", "Revisar resultado"),
            executionOrder
        )
    }

    @Test
    fun engineFazRetryDeFalhaTransienteEAprovaNaSegundaTentativa() = runTest {
        val task = OrchestrationTask(id = "task", title = "Executar", objective = "Executar tarefa")
        val plan = OrchestrationPlan(objective = "Executar tarefa", tasks = listOf(task))
        var calls = 0
        val gateway = object : ProviderGateway {
            override suspend fun execute(request: ProviderRequest): ProviderResponse {
                calls++
                return if (calls == 1) ProviderResponse(false, error = "timeout", transient = true)
                else ProviderResponse(true, "resultado após retry")
            }
        }

        val (finished, steps) = TaskEngine(gateway).run(plan) { "ia-teste" }

        assertEquals(OrchestrationTaskStatus.SUCCEEDED, finished.status)
        assertEquals(2, calls)
        assertEquals(listOf(OrchestrationTaskStatus.RETRYING, OrchestrationTaskStatus.SUCCEEDED), steps.map { it.status })
        assertEquals(2, finished.tasks.single().attempts)
    }

    @Test
    fun engineBloqueiaPlanoQuandoRoteadorNaoEncontraCandidato() = runTest {
        val plan = BrainPlanner().plan("Pesquisar tema")
        var executions = 0
        val gateway = object : ProviderGateway {
            override suspend fun execute(request: ProviderRequest): ProviderResponse {
                executions++
                return ProviderResponse(true, "não deveria executar")
            }
        }

        val (finished, steps) = TaskEngine(gateway).run(plan) { null }

        assertEquals(OrchestrationTaskStatus.FAILED, finished.status)
        assertTrue(steps.any { it.status == OrchestrationTaskStatus.BLOCKED })
        assertEquals(0, executions)
    }

    @Test
    fun saidaRuimGeraNeedsRevisionSemFingirSucesso() = runTest {
        val task = OrchestrationTask(id = "task", title = "Revisar", objective = "Revisar conteúdo")
        val plan = OrchestrationPlan(objective = "Revisar conteúdo", tasks = listOf(task))
        val gateway = object : ProviderGateway {
            override suspend fun execute(request: ProviderRequest) = ProviderResponse(true, "ruim")
        }

        val (finished, steps) = TaskEngine(gateway).run(plan) { "ia-teste" }

        assertEquals(OrchestrationTaskStatus.NEEDS_REVISION, finished.status)
        assertEquals(OrchestrationTaskStatus.NEEDS_REVISION, steps.single().status)
        assertFalse(finished.tasks.single().status == OrchestrationTaskStatus.SUCCEEDED)
    }
}
