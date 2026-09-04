package com.aibrain.app.brain

import java.util.UUID

/** Runtime local para acompanhar múltiplos projetos e recuperar tarefas órfãs. */
class FactoryRuntime {
    data class ProjectRuntime(
        val projectId: String,
        val objective: String,
        val plan: ProjectWorkPlanner.Plan,
        val createdAt: Long = System.currentTimeMillis()
    )

    data class TaskRuntime(
        val taskId: String,
        val projectId: String,
        val aiId: String,
        val startedAt: Long,
        var lastHeartbeatAt: Long = startedAt,
        var status: GitHubWorker.Status = GitHubWorker.Status.IN_PROGRESS,
        var attempts: Int = 1
    )

    private val projects = linkedMapOf<String, ProjectRuntime>()
    private val tasks = linkedMapOf<String, TaskRuntime>()

    fun registerProject(plan: ProjectWorkPlanner.Plan, projectId: String = UUID.randomUUID().toString()): ProjectRuntime {
        require(projectId.isNotBlank()) { "projectId obrigatório" }
        check(projectId !in projects) { "Projeto já registrado" }
        return ProjectRuntime(projectId, plan.objective, plan).also { projects[projectId] = it }
    }

    fun projects(): List<ProjectRuntime> = projects.values.toList()

    fun startTask(projectId: String, taskId: String, aiId: String, now: Long = System.currentTimeMillis()): TaskRuntime {
        check(projectId in projects) { "Projeto não encontrado" }
        require(aiId.isNotBlank()) { "IA obrigatória" }
        check(taskId !in tasks) { "Task já em execução" }
        return TaskRuntime(taskId, projectId, aiId, now).also { tasks[taskId] = it }
    }

    fun heartbeat(taskId: String, now: Long = System.currentTimeMillis()) {
        val task = tasks[taskId] ?: error("Task não encontrada")
        check(task.status == GitHubWorker.Status.IN_PROGRESS) { "Task não está em execução" }
        task.lastHeartbeatAt = now
    }

    fun watchdog(now: Long = System.currentTimeMillis(), timeoutMs: Long = 15 * 60 * 1000L): List<TaskRuntime> {
        require(timeoutMs > 0) { "timeoutMs inválido" }
        return tasks.values.filter {
            it.status == GitHubWorker.Status.IN_PROGRESS && now - it.lastHeartbeatAt > timeoutMs
        }.onEach {
            it.status = GitHubWorker.Status.BLOCKED
        }
    }

    fun retry(taskId: String, now: Long = System.currentTimeMillis()): TaskRuntime {
        val task = tasks[taskId] ?: error("Task não encontrada")
        check(task.status == GitHubWorker.Status.BLOCKED || task.status == GitHubWorker.Status.FAILED) { "Task não pode ser recuperada" }
        task.status = GitHubWorker.Status.IN_PROGRESS
        task.attempts += 1
        task.lastHeartbeatAt = now
        return task
    }
}

/** Observabilidade local: eventos estruturados sem depender de servidor. */
object FactoryTelemetry {
    enum class EventType { PROJECT_CREATED, TASK_STARTED, ZIP_RECEIVED, REVIEWED, BLOCKED, PR_OPENED, MERGED, RECOVERED }

    data class Event(
        val type: EventType,
        val projectId: String,
        val taskId: String? = null,
        val aiId: String? = null,
        val timestamp: Long = System.currentTimeMillis(),
        val detail: String = ""
    )

    private val events = mutableListOf<Event>()

    fun record(event: Event) { events += event }
    fun snapshot(): List<Event> = events.toList()
    fun forProject(projectId: String): List<Event> = events.filter { it.projectId == projectId }
    fun clear() { events.clear() }
}

/** Aprende apenas com métricas fornecidas localmente; não envia dados para fora. */
object ProcessOptimizer {
    data class Observation(
        val functionType: String,
        val aiId: String,
        val success: Boolean,
        val durationMs: Long,
        val regressions: Int = 0
    )

    data class Recommendation(val aiId: String, val score: Double, val sampleSize: Int)

    private val observations = mutableListOf<Observation>()

    fun record(observation: Observation) {
        require(observation.durationMs >= 0) { "durationMs inválido" }
        observations += observation
    }

    fun recommend(functionType: String): List<Recommendation> = observations
        .filter { it.functionType == functionType }
        .groupBy { it.aiId }
        .map { (ai, samples) ->
            val success = samples.count { it.success }.toDouble() / samples.size
            val regressionPenalty = samples.sumOf { it.regressions }.toDouble() / samples.size
            val durationPenalty = (samples.map { it.durationMs }.average() / 60_000.0).coerceAtMost(1.0)
            Recommendation(ai, (success * 0.65 + (1.0 - durationPenalty) * 0.2 + (1.0 - regressionPenalty.coerceAtMost(1.0)) * 0.15).coerceIn(0.0, 1.0), samples.size)
        }
        .sortedWith(compareByDescending<Recommendation> { it.score }.thenBy { it.aiId })
}
