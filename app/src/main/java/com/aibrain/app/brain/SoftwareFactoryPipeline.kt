package com.aibrain.app.brain

import java.io.File

/**
 * Pipeline unificado das fases 1.1 -> 2.0. A execução externa continua sob
 * controle do usuário; o IaBrain recebe ZIPs e decide se o conjunto pode avançar.
 */
class SoftwareFactoryPipeline(
    private val worker: GitHubWorker = GitHubWorker(),
    private val runtime: SoftwareFactoryRuntimeV31? = null,
    private val projectId: String = "default",
    private val workspace: File? = null
) {
    private var lastPhysicalMerge: SoftwareFactoryRuntimeV31.MergeResult? = null
    fun lastMergeResult(): SoftwareFactoryRuntimeV31.MergeResult? = lastPhysicalMerge

    enum class Stage {
        PLANNED, ASSIGNED, IN_PROGRESS, ZIP_RECEIVED, INTEGRATED, REVIEWED,
        QUALITY_GATE, APPROVED, MERGED, BLOCKED
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
        return state
    }

    fun markInProgress(taskId: String): TaskState {
        val state = requireNotNull(states[taskId]) { "Task não encontrada" }
        worker.transition(taskId, GitHubWorker.Status.IN_PROGRESS)
        return state.copy(stage = Stage.IN_PROGRESS).also { states[taskId] = it }
    }

    /**
     * Analisa todos os ZIPs juntos. Isso é a fronteira real da integração:
     * conflitos entre duas IAs só são detectáveis quando os artefatos são
     * comparados no mesmo snapshot.
     */
    fun integrateBatch(
        plan: ProjectWorkPlanner.Plan,
        submissions: List<Submission>,
        baseSha256ByPath: Map<String, String> = emptyMap(),
        declaredFilesByFunction: Map<String, Set<String>> = emptyMap(),
        baseModifiedPolicy: SoftwareFactoryRuntimeV31.BaseModifiedPolicy = SoftwareFactoryRuntimeV31.BaseModifiedPolicy.REVIEW,
        allowAutoMerge: Boolean = false
    ): List<TaskState> {
        require(submissions.isNotEmpty()) { "Nenhum ZIP para integrar" }
        submissions.forEach { submission ->
            val state = requireNotNull(states[submission.taskId]) { "Task não encontrada: ${submission.taskId}" }
            require(state.functionId == submission.functionId && state.aiId == submission.aiId) { "ZIP não pertence à task ${submission.taskId}" }
            require(state.stage == Stage.IN_PROGRESS) { "Task ${submission.taskId} não está em execução" }
            require(submission.zip.isFile) { "ZIP não encontrado: ${submission.zip}" }
        }

        val artifacts = submissions.map {
            ZipIntegrationEngine.Artifact(it.taskId, it.functionId, it.aiId, it.zip)
        }
        val analysis = ZipIntegrationEngine.analyze(artifacts, baseSha256ByPath)
        val review = IntegrationReviewEngine.review(plan, analysis, declaredFilesByFunction)
        val physicalMerge = if (runtime != null && workspace != null) {
            runtime.merge(workspace, analysis, artifacts, declaredFilesByFunction, allowAutoMerge = allowAutoMerge, baseModifiedPolicy = baseModifiedPolicy)
        } else null
        lastPhysicalMerge = physicalMerge
        val blocked = !analysis.safe || !review.approved || physicalMerge?.blocked == true

        submissions.forEach { submission ->
            val current = states.getValue(submission.taskId)
            if (blocked) {
                val reason = physicalMerge?.reason
                    ?: if (!analysis.safe) "Conflito estrutural no conjunto de ZIPs" else "Integration Review bloqueou o conjunto"
                worker.transition(submission.taskId, GitHubWorker.Status.BLOCKED, error = reason)
                states[submission.taskId] = current.copy(stage = Stage.BLOCKED, zip = submission.zip, review = review, error = reason)
            } else {
                worker.transition(submission.taskId, GitHubWorker.Status.READY_FOR_REVIEW)
                states[submission.taskId] = current.copy(stage = Stage.INTEGRATED, zip = submission.zip, review = review)
            }
            FactoryTelemetry.record(FactoryTelemetry.Event(FactoryTelemetry.EventType.ZIP_RECEIVED, projectId, submission.taskId, submission.aiId))
        }
        return snapshot()
    }

    fun receiveZip(
        plan: ProjectWorkPlanner.Plan,
        submission: Submission,
        baseSha256ByPath: Map<String, String> = emptyMap(),
        declaredFilesByFunction: Map<String, Set<String>> = emptyMap(),
        baseModifiedPolicy: SoftwareFactoryRuntimeV31.BaseModifiedPolicy = SoftwareFactoryRuntimeV31.BaseModifiedPolicy.REVIEW,
        allowAutoMerge: Boolean = false
    ): TaskState = integrateBatch(plan, listOf(submission), baseSha256ByPath, declaredFilesByFunction, baseModifiedPolicy, allowAutoMerge)
        .first { it.taskId == submission.taskId }
        .let { if (it.stage == Stage.INTEGRATED) it.copy(stage = Stage.REVIEWED) else it }
        .also { states[submission.taskId] = it }

    fun runQualityGate(taskId: String, plan: ProjectWorkPlanner.Plan): TaskState {
        val state = requireNotNull(states[taskId]) { "Task não encontrada" }
        check(state.stage == Stage.REVIEWED || state.stage == Stage.INTEGRATED) { "Review precisa passar antes do Quality Gate" }
        val gate = ProjectQualityGate.validate(plan)
        if (!gate.passed) {
            worker.transition(taskId, GitHubWorker.Status.BLOCKED, error = "Quality Gate bloqueado")
            return state.copy(stage = Stage.BLOCKED, qualityGate = gate, error = "Quality Gate bloqueado").also { states[taskId] = it }
        }
        worker.transition(taskId, GitHubWorker.Status.PR_OPEN)
        worker.transition(taskId, GitHubWorker.Status.QUALITY_GATE)
        return state.copy(stage = Stage.QUALITY_GATE, qualityGate = gate).also { states[taskId] = it }
    }

    fun approve(taskId: String): TaskState {
        val state = requireNotNull(states[taskId]) { "Task não encontrada" }
        val gate = requireNotNull(state.qualityGate) { "Quality Gate ausente" }
        worker.readyToMerge(taskId, gate)
        return state.copy(stage = Stage.APPROVED).also { states[taskId] = it }
    }

    fun markMerged(taskId: String): TaskState {
        val state = requireNotNull(states[taskId]) { "Task não encontrada" }
        check(state.stage == Stage.APPROVED) { "Task não aprovada" }
        worker.transition(taskId, GitHubWorker.Status.MERGED)
        FactoryTelemetry.record(FactoryTelemetry.Event(FactoryTelemetry.EventType.MERGED, projectId, taskId, state.aiId))
        return state.copy(stage = Stage.MERGED).also { states[taskId] = it }
    }

    fun snapshot(): List<TaskState> = states.values.toList()
}
