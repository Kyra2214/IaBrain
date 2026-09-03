package com.aibrain.app.brain

/**
 * Orquestra unidades de trabalho sem permitir execução fora da atribuição.
 * A coordenação é local e determinística; a IA externa continua sendo
 * iniciada pelo usuário e o retorno ao projeto acontece via PR.
 */
class MultiAIExecutionCoordinator(
    private val plan: ProjectWorkPlanner.Plan,
    private val maxConcurrent: Int = 3
) {
    enum class Status { BLOCKED, QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }

    data class Assignment(
        val functionId: String,
        val branchName: String,
        val status: Status,
        val aiId: String? = null,
        val error: String? = null
    )

    private val assignments = linkedMapOf<String, Assignment>()

    init {
        require(maxConcurrent > 0) { "maxConcurrent deve ser maior que zero" }
        plan.workItems.forEach { item ->
            assignments[item.functionId] = Assignment(item.functionId, item.branchName, Status.QUEUED)
        }
    }

    fun snapshot(): List<Assignment> = assignments.values.toList()

    fun claim(functionId: String, aiId: String): Assignment {
        require(aiId.isNotBlank()) { "IA deve ser identificada" }
        val item = plan.workItems.firstOrNull { it.functionId == functionId }
            ?: error("Função não encontrada: $functionId")
        val current = assignments.getValue(functionId)
        check(current.status == Status.QUEUED) { "Função não está disponível: $functionId" }
        check(runningCount() < maxConcurrent) { "Limite de IAs simultâneas atingido" }
        check(item.branchName.startsWith("ai/")) { "Branch de IA inválida: ${item.branchName}" }
        check(dependenciesCompleted(item)) { "Dependências ainda não concluídas: ${item.functionId}" }
        return current.copy(status = Status.RUNNING, aiId = aiId).also { assignments[functionId] = it }
    }

    fun complete(functionId: String): Assignment = transition(functionId, Status.COMPLETED)

    fun fail(functionId: String, reason: String): Assignment {
        check(reason.isNotBlank()) { "Falha precisa de motivo" }
        return transition(functionId, Status.FAILED, reason)
    }

    fun cancel(functionId: String, reason: String = "Cancelado pelo usuário"): Assignment {
        return transition(functionId, Status.CANCELLED, reason)
    }

    private fun transition(functionId: String, target: Status, error: String? = null): Assignment {
        val current = assignments[functionId] ?: error("Função não encontrada: $functionId")
        check(current.status == Status.RUNNING) { "Transição inválida para $functionId: ${current.status} -> $target" }
        return current.copy(status = target, error = error).also { assignments[functionId] = it }
    }

    private fun runningCount() = assignments.values.count { it.status == Status.RUNNING }

    private fun dependenciesCompleted(item: ProjectWorkPlanner.WorkItem): Boolean =
        item.dependsOn.all { assignments[it]?.status == Status.COMPLETED }
}
