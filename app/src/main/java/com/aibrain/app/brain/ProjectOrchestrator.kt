package com.aibrain.app.brain

import com.aibrain.app.data.local.ProjetoExecucaoRepository
import com.aibrain.app.data.local.ProjetoFuncaoEntity
import com.aibrain.app.data.local.ProjetoFuncaoRepository
import com.aibrain.app.data.local.ProjetoRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orquestra o avanço do projeto sem executar a IA externa automaticamente.
 * A unidade de avanço é sempre uma função preparada em WAITING_USER.
 */
class ProjectOrchestrator(
    private val projetoRepository: ProjetoRepository,
    private val funcaoRepository: ProjetoFuncaoRepository,
    private val executionRepository: ProjetoExecucaoRepository,
    private val executionEngine: ProjectExecutionEngine
) {
    private val mutex = Mutex()

    suspend fun advance(projetoId: String): ProjectOrchestrationResult = mutex.withLock {
        val projeto = projetoRepository.buscar(projetoId)
            ?: return@withLock ProjectOrchestrationResult.NotFound(projetoId)
        val funcoes = funcaoRepository.listar(projetoId)

        if (funcoes.isEmpty()) {
            executionRepository.marcarProjetoStatus(projetoId, "SEM_FUNCOES")
            return@withLock ProjectOrchestrationResult.Blocked(projetoId, emptyList(), "O projeto não possui funções.")
        }

        val ativas = funcoes.mapNotNull { funcao ->
            val ultima = executionRepository.ultimaDaFuncao(projetoId, funcao.id)
            if (ultima?.status == ProjectExecutionStatus.WAITING_USER.name || ultima?.status == ProjectExecutionStatus.RUNNING.name) {
                return@withLock ProjectOrchestrationResult.InProgress(ultima.id, funcao.id, ultima.status)
            }
            if (funcao.status.equals("CONCLUIDA", true)) null else funcao
        }

        if (ativas.isEmpty()) {
            executionRepository.marcarProjetoStatus(projetoId, "CONCLUIDO")
            return@withLock ProjectOrchestrationResult.Completed(projetoId)
        }

        val ready = ativas.firstOrNull { executionRepository.podeExecutar(projetoId, it.id) }
        if (ready == null) {
            val bloqueadas = ativas.map { it.id }
            executionRepository.marcarProjetoStatus(projetoId, "BLOQUEADO")
            return@withLock ProjectOrchestrationResult.Blocked(
                projetoId,
                bloqueadas,
                "Nenhuma função restante pode avançar porque suas dependências ainda não foram concluídas."
            )
        }

        val plan = executionEngine.prepare(projeto, ready)
        when (plan.state.status) {
            ProjectExecutionStatus.WAITING_USER -> {
                executionRepository.marcarProjetoStatus(projetoId, "AGUARDANDO_USUARIO")
                ProjectOrchestrationResult.Prepared(plan)
            }
            ProjectExecutionStatus.BLOCKED -> ProjectOrchestrationResult.Blocked(
                projetoId,
                listOf(ready.id),
                plan.state.reason
            )
            else -> ProjectOrchestrationResult.Failed(ready.id, plan.state.reason)
        }
    }

    suspend fun concludeAndAdvance(executionId: String, resultado: String?, erro: String? = null): ProjectOrchestrationResult {
        val concluida = executionRepository.concluir(executionId, resultado, erro)
        if (!concluida) return ProjectOrchestrationResult.InvalidTransition(executionId)
        val execucao = executionRepository.listarPorId(executionId)
            ?: return ProjectOrchestrationResult.InvalidTransition(executionId)
        return advance(execucao.projetoId)
    }

    suspend fun retry(projetoId: String, funcaoId: String): ProjectOrchestrationResult = mutex.withLock {
        val projeto = projetoRepository.buscar(projetoId)
            ?: return@withLock ProjectOrchestrationResult.NotFound(projetoId)
        val funcao = funcaoRepository.buscar(funcaoId)
            ?: return@withLock ProjectOrchestrationResult.NotFound(funcaoId)
        if (funcao.projetoId != projetoId) return@withLock ProjectOrchestrationResult.InvalidFunction(funcaoId)

        val ultima = executionRepository.ultimaDaFuncao(projetoId, funcaoId)
            ?: return@withLock ProjectOrchestrationResult.NotFound("execução:$funcaoId")
        if (ultima.status != ProjectExecutionStatus.FAILED.name && ultima.status != ProjectExecutionStatus.CANCELLED.name) {
            return@withLock ProjectOrchestrationResult.InvalidTransition(ultima.id)
        }
        if (!executionRepository.podeExecutar(projetoId, funcaoId)) {
            return@withLock ProjectOrchestrationResult.Blocked(projetoId, listOf(funcaoId), "As dependências da função ainda não foram concluídas.")
        }

        val plan = executionEngine.prepare(projeto, funcao)
        if (plan.state.status == ProjectExecutionStatus.WAITING_USER) {
            executionRepository.marcarProjetoStatus(projetoId, "AGUARDANDO_USUARIO")
            ProjectOrchestrationResult.Prepared(plan)
        } else {
            ProjectOrchestrationResult.Failed(funcaoId, plan.state.reason)
        }
    }

    suspend fun cancel(executionId: String, motivo: String = "Execução cancelada pelo usuário"): ProjectOrchestrationResult = mutex.withLock {
        val cancelada = executionRepository.cancelar(executionId, motivo)
        if (!cancelada) return@withLock ProjectOrchestrationResult.InvalidTransition(executionId)
        ProjectOrchestrationResult.Cancelled(executionId)
    }

    suspend fun recoverAfterRestart(): Int = executionRepository.recuperarExecucoesInterrompidas()
}

sealed interface ProjectOrchestrationResult {
    data class Prepared(val plan: ProjectExecutionPlan) : ProjectOrchestrationResult
    data class InProgress(val executionId: String, val funcaoId: String, val status: String) : ProjectOrchestrationResult
    data class Blocked(val projetoId: String, val funcaoIds: List<String>, val reason: String) : ProjectOrchestrationResult
    data class Failed(val funcaoId: String, val reason: String) : ProjectOrchestrationResult
    data class Cancelled(val executionId: String) : ProjectOrchestrationResult
    data class Completed(val projetoId: String) : ProjectOrchestrationResult
    data class InvalidTransition(val id: String) : ProjectOrchestrationResult
    data class InvalidFunction(val funcaoId: String) : ProjectOrchestrationResult
    data class NotFound(val id: String) : ProjectOrchestrationResult
}
