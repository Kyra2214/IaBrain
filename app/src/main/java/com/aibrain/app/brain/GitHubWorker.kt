package com.aibrain.app.brain

/**
 * Máquina de estados do trabalhador GitHub. A implementação de rede é
 * deliberadamente separada: o núcleo pode funcionar offline e um adaptador
 * opcional pode materializar estas operações no GitHub.
 */
class GitHubWorker {
    enum class Status {
        ASSIGNED, IN_PROGRESS, READY_FOR_REVIEW, PR_OPEN, QUALITY_GATE,
        APPROVED, MERGED, BLOCKED, FAILED
    }

    data class Work(
        val taskId: String,
        val functionId: String,
        val branchName: String,
        val aiId: String,
        val status: Status = Status.ASSIGNED,
        val pullRequestNumber: Int? = null,
        val error: String? = null
    )

    interface RemotePort {
        fun createBranch(branchName: String, baseBranch: String): Boolean
        fun openPullRequest(branchName: String, baseBranch: String, title: String, body: String): Int?
        fun mergePullRequest(number: Int): Boolean
    }

    private val works = linkedMapOf<String, Work>()

    fun register(work: Work): Work {
        require(work.taskId.isNotBlank()) { "taskId obrigatório" }
        require(work.aiId.isNotBlank()) { "IA obrigatória" }
        require(work.branchName.startsWith("ai/")) { "Branch fora do contrato de IA" }
        check(work.taskId !in works) { "Task já registrada: ${work.taskId}" }
        works[work.taskId] = work
        return work
    }

    fun get(taskId: String): Work? = works[taskId]

    fun snapshot(): List<Work> = works.values.toList()

    fun transition(taskId: String, target: Status, prNumber: Int? = null, error: String? = null): Work {
        val current = works[taskId] ?: error("Task não encontrada: $taskId")
        check(isAllowed(current.status, target)) { "Transição inválida: ${current.status} -> $target" }
        return current.copy(status = target, pullRequestNumber = prNumber ?: current.pullRequestNumber, error = error)
            .also { works[taskId] = it }
    }

    fun readyToMerge(taskId: String, qualityGate: ProjectQualityGate.Result): Work {
        check(qualityGate.passed) { "Quality Gate bloqueou a integração" }
        val current = works[taskId] ?: error("Task não encontrada: $taskId")
        check(current.status == Status.QUALITY_GATE || current.status == Status.APPROVED) { "Task não está em Quality Gate" }
        return transition(taskId, Status.APPROVED)
    }

    private fun isAllowed(from: Status, to: Status): Boolean = when (from) {
        Status.ASSIGNED -> to == Status.IN_PROGRESS || to == Status.BLOCKED || to == Status.FAILED
        Status.IN_PROGRESS -> to == Status.READY_FOR_REVIEW || to == Status.BLOCKED || to == Status.FAILED
        Status.READY_FOR_REVIEW -> to == Status.PR_OPEN || to == Status.BLOCKED
        Status.PR_OPEN -> to == Status.QUALITY_GATE || to == Status.BLOCKED || to == Status.FAILED
        Status.QUALITY_GATE -> to == Status.APPROVED || to == Status.BLOCKED
        Status.APPROVED -> to == Status.MERGED || to == Status.BLOCKED
        Status.MERGED, Status.BLOCKED, Status.FAILED -> false
    }
}
