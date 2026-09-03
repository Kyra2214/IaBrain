package com.aibrain.app.brain

import java.io.File

/**
 * Pipeline unificado das fases 1.1 -> 2.0. A execução externa continua sob
 * controle do usuário; o IaBrain recebe o ZIP e decide se ele pode avançar.
 */
class SoftwareFactoryPipeline(
    private val worker: GitHubWorker = GitHubWorker()
) {
    enum class Stage {
        PLANNED,
        ASSIGNED,
        IN_PROGRESS,
        ZIP_RECEIVED,
        INTEGRATED,
        REVIEWED,
        QUALITY_GATE,
        APPROVED,
        MERGED,
        BLOCKED
    }

    data class TaskState(
        val taskId: String,
        val functionId: String,
        val aiId: String,
        val branchName: String,
        val stage: Stage,
        val zip: File? = null,
        val review: IntegrationReviewEngine.Report? = null,
        val qualityGate: ProjectQualityGate.Result? = null,
        val error: String? = null
    )

    data class Submission(
        val taskId: String,
        val functionId: String,
        val aiId: String,
        val zip: File
    )

    private val states = linkedMapOf<String, TaskState>()

    fun assign(item: ProjectWorkPlanner.WorkItem, aiId: String): TaskState {
        val security = ExecutionSecurityPolicy.validateWorkItem(item)
        check(security.allowed) { "Unidade bloqueada pela segurança: ${security.findings.joinToString { it.message }}" }
        val state = TaskState(item.taskId, item.functionId, aiId, item.branchName, Stage.ASSIGNED)
        states[item.taskId] = state
        worker.register(GitHubWorker.Work(item.taskId, item.functionId, item.branchName, aiId))
        FactoryTelemetry.record(FactoryTelemetry.Event(FactoryTelemetry.EventType.TASK_STARTED, "", item.taskId, aiId))
        return state
    }

    fun markInProgress(taskId: String): TaskState {
        val state = states[taskId] ?: error("Task não encontrada")
        worker.transition(taskId, GitHubWorker.Status.IN_PROGRESS)
        return state.copy(stage = Stage.IN_PROGRESS).also { states[taskId] = it }
    }

    fun receiveZip(
        plan: ProjectWorkPlanner.Plan,
        submission: Submission,
        baseSha256ByPath: Map<String, String> = emptyMap(),
        declaredFilesByFunction: Map<String, Set<String>> = emptyMap()
    ): TaskState {
        val state = states[submission.taskId] ?: error("Task não encontrada")
        require(state.functionId == submission.functionId && state.aiId == submission.aiId) { "ZIP não pertence à task" }
        require(submission.zip.isFile) { "ZIP não encontrado" }
        val analysis = ZipIntegrationEngine.analyze(
            listOf(ZipIntegrationEngine.Artifact(submission.taskId, submission.functionId, submission.aiId, submission.zip)),
            baseSha256ByPath
        )
        if (!analysis.safe) {
            worker.transition(submission.taskId, GitHubWorker.Status.BLOCKED, error = "Conflito estrutural no ZIP")
            return state.copy(stage = Stage.BLOCKED, zip = submission.zip, error = "Conflito estrutural no ZIP").also { states[submission.taskId] = it }
        }
        val review = IntegrationReviewEngine.review(plan, analysis, declaredFilesByFunction)
        val nextStage = if (review.approved) Stage.REVIEWED else Stage.BLOCKED
        if (!review.approved) worker.transition(submission.taskId, GitHubWorker.Status.BLOCKED, error = "Review bloqueou integração")
        else worker.transition(submission.taskId, GitHubWorker.Status.READY_FOR_REVIEW)
        FactoryTelemetry.record(FactoryTelemetry.Event(FactoryTelemetry.EventType.ZIP_RECEIVED, "", submission.taskId, submission.aiId))
        return state.copy(stage = nextStage, zip = submission.zip, review = review).also { states[submission.taskId] = it }
    }

    fun runQualityGate(taskId: String, plan: ProjectWorkPlanner.Plan): TaskState {
        val state = states[taskId] ?: error("Task não encontrada")
        check(state.stage == Stage.REVIEWED) { "Review precisa passar antes do Quality Gate" }
        val gate = ProjectQualityGate.validate(plan)
        if (!gate.passed) {
            worker.transition(taskId, GitHubWorker.Status.BLOCKED, error = "Quality Gate bloqueado")
            return state.copy(stage = Stage.BLOCKED, qualityGate = gate, error = "Quality Gate bloqueado").also { states[taskId] = it }
        }
        worker.transition(taskId, GitHubWorker.Status.PR_OPEN)
        worker.transition(taskId, GitHubWorker.Status.QUALITY_GATE)
        FactoryTelemetry.record(FactoryTelemetry.Event(FactoryTelemetry.EventType.REVIEWED, "", taskId, state.aiId))
        return state.copy(stage = Stage.QUALITY_GATE, qualityGate = gate).also { states[taskId] = it }
    }

    fun approve(taskId: String): TaskState {
        val state = states[taskId] ?: error("Task não encontrada")
        val gate = state.qualityGate ?: error("Quality Gate ausente")
        worker.readyToMerge(taskId, gate)
        return state.copy(stage = Stage.APPROVED).also { states[taskId] = it }
    }

    fun markMerged(taskId: String): TaskState {
        val state = states[taskId] ?: error("Task não encontrada")
        check(state.stage == Stage.APPROVED) { "Task não aprovada" }
        worker.transition(taskId, GitHubWorker.Status.MERGED)
        FactoryTelemetry.record(FactoryTelemetry.Event(FactoryTelemetry.EventType.MERGED, "", taskId, state.aiId))
        return state.copy(stage = Stage.MERGED).also { states[taskId] = it }
    }

    fun snapshot(): List<TaskState> = states.values.toList()
}
