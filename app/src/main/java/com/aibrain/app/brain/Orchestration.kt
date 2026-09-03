package com.aibrain.app.brain

import java.util.UUID

enum class OrchestrationTaskStatus { PENDING, READY, RUNNING, SUCCEEDED, FAILED, RETRYING, NEEDS_REVISION, WAITING_HUMAN, BLOCKED, CANCELLED }
enum class ValidationOutcome { SUCCEEDED, FAILED, NEEDS_REVISION, NEEDS_HUMAN, BLOCKED }

data class OrchestrationPolicy(
    val maxRetriesPerTask: Int = 2,
    val maxRevisionsPerPlan: Int = 3,
    val maxSteps: Int = 30,
    val maxContextChars: Int = 12_000,
    val allowNetwork: Boolean = false,
    val requireHumanApproval: Boolean = false
) { init { require(maxRetriesPerTask >= 0 && maxRevisionsPerPlan >= 0 && maxSteps > 0 && maxContextChars > 0) } }

data class OrchestrationTask(
    val id: String = UUID.randomUUID().toString(), val title: String, val objective: String,
    val requiredCapabilities: Set<String> = emptySet(), val dependsOn: Set<String> = emptySet(), val priority: Int = 0,
    val status: OrchestrationTaskStatus = OrchestrationTaskStatus.PENDING, val attempts: Int = 0, val revisions: Int = 0,
    val assignedIaId: String? = null, val expectedOutput: String = "", val result: String? = null, val error: String? = null
)

data class OrchestrationPlan(
    val id: String = UUID.randomUUID().toString(), val objective: String, val version: Int = 1,
    val tasks: List<OrchestrationTask>, val policy: OrchestrationPolicy = OrchestrationPolicy(),
    val status: OrchestrationTaskStatus = OrchestrationTaskStatus.PENDING
)

data class PlanValidation(val valid: Boolean, val errors: List<String>)

object OrchestrationPlanValidator {
    fun validate(plan: OrchestrationPlan): PlanValidation {
        val errors = mutableListOf<String>(); val ids = plan.tasks.map { it.id }; val idSet = ids.toSet()
        if (plan.objective.isBlank()) errors += "Objetivo não pode ser vazio"
        if (ids.size != idSet.size) errors += "IDs de tarefas duplicados"
        plan.tasks.forEach { task ->
            if (task.title.isBlank() || task.objective.isBlank()) errors += "Tarefa ${task.id} sem título ou objetivo"
            if (task.id in task.dependsOn) errors += "Tarefa ${task.id} depende de si mesma"
            if (!task.dependsOn.all { it in idSet }) errors += "Tarefa ${task.id} possui dependência inexistente"
        }
        if (hasCycle(plan.tasks)) errors += "Grafo de tarefas contém ciclo"
        return PlanValidation(errors.isEmpty(), errors)
    }
    private fun hasCycle(tasks: List<OrchestrationTask>): Boolean {
        val byId = tasks.associateBy { it.id }; val visiting = mutableSetOf<String>(); val visited = mutableSetOf<String>()
        fun visit(id: String): Boolean {
            if (id in visiting) return true
            if (!visited.add(id)) return false
            visiting += id; val cycle = byId[id]?.dependsOn?.any(::visit) == true; visiting -= id; return cycle
        }
        return tasks.any { visit(it.id) }
    }
}

/** Planner local, determinístico e sem rede. */
class BrainPlanner {
    fun plan(objective: String, policy: OrchestrationPolicy = OrchestrationPolicy()): OrchestrationPlan {
        require(objective.isNotBlank()) { "Objetivo não pode ser vazio" }
        val normalized = objective.lowercase(); val tasks = mutableListOf<OrchestrationTask>()
        fun add(title: String, goal: String, caps: Set<String>, deps: Set<String> = emptySet()) = tasks.add(OrchestrationTask(title = title, objective = goal, requiredCapabilities = caps, dependsOn = deps, expectedOutput = "Resultado verificável de: $title"))
        add("Entender objetivo", objective, setOf("PESQUISA")); val understanding = tasks.last().id
        if (normalized.contains("código") || normalized.contains("codigo") || normalized.contains("app") || normalized.contains("aplicativo")) add("Projetar solução", "Definir arquitetura e passos técnicos para: $objective", setOf("CODIGO", "ANALISE"), setOf(understanding)) else add("Pesquisar e analisar", "Reunir informações relevantes para: $objective", setOf("PESQUISA", "ANALISE"), setOf(understanding))
        val work = tasks.last().id; add("Produzir resultado", objective, setOf(if (normalized.contains("código") || normalized.contains("codigo")) "CODIGO" else "ESCRITA"), setOf(work)); val output = tasks.last().id
        add("Revisar resultado", "Validar aderência ao objetivo: $objective", setOf("ANALISE"), setOf(output))
        return OrchestrationPlan(objective = objective, tasks = tasks, policy = policy)
    }
    fun revise(plan: OrchestrationPlan, taskId: String, reason: String): OrchestrationPlan {
        require(plan.version < plan.policy.maxRevisionsPerPlan + 1) { "Limite de revisões do plano atingido" }
        val revised = plan.tasks.map { if (it.id == taskId) it.copy(status = OrchestrationTaskStatus.PENDING, error = reason, result = null) else it }
        return plan.copy(version = plan.version + 1, tasks = revised, status = OrchestrationTaskStatus.PENDING)
    }
}

/** Adapta o catálogo/capacidades existente ao módulo AI Router sem executar providers. */
class OrchestrationRouter(private val candidates: suspend () -> List<RoutingCandidate>) {
    suspend fun route(task: OrchestrationTask): RoutingDecision = LocalAIRouter.route(
        RoutingRequest(rawUserRequest = task.objective, canonicalCommand = null, requiredCapabilities = task.requiredCapabilities), candidates()
    )
}

data class ProviderRequest(val task: OrchestrationTask, val context: String, val iaId: String)
data class ProviderResponse(val success: Boolean, val output: String? = null, val error: String? = null, val transient: Boolean = false)
interface ProviderGateway { suspend fun execute(request: ProviderRequest): ProviderResponse }

class ContextManager(private val policy: OrchestrationPolicy) {
    fun build(task: OrchestrationTask, dependencyResults: Map<String, String>): String {
        val deps = dependencyResults.entries.joinToString("\n") { "[${it.key}] ${it.value}" }
        return ("OBJETIVO: ${task.objective}\nSAÍDA ESPERADA: ${task.expectedOutput}\nRESULTADOS APROVADOS:\n$deps").take(policy.maxContextChars)
    }
}

object OrchestrationPolicyGuard {
    private val secretPattern = Regex("(?i)(password|passwd|secret|api[_-]?key|access[_-]?token|private[_ -]?key)\\s*[:=]")
    fun validateContext(context: String, policy: OrchestrationPolicy): List<String> {
        val errors = mutableListOf<String>(); if (context.length > policy.maxContextChars) errors += "Contexto excede limite"; if (secretPattern.containsMatchIn(context)) errors += "Contexto contém possível segredo"; return errors
    }
}

data class ValidationReport(val outcome: ValidationOutcome, val reasons: List<String>, val evidence: String? = null)
class OrchestrationValidator {
    fun validate(task: OrchestrationTask, response: ProviderResponse): ValidationReport {
        if (!response.success || response.output.isNullOrBlank()) return ValidationReport(if (response.transient) ValidationOutcome.FAILED else ValidationOutcome.NEEDS_REVISION, listOf(response.error ?: "Provider não produziu saída"))
        if (response.output.length < 8) return ValidationReport(ValidationOutcome.NEEDS_REVISION, listOf("Saída curta demais para o objetivo"))
        return ValidationReport(ValidationOutcome.SUCCEEDED, listOf("Saída não vazia e dentro do contrato"), response.output.take(240))
    }
}

data class OrchestrationEvent(val planId: String, val taskId: String?, val type: String, val details: String, val at: Long = System.currentTimeMillis())
class OrchestrationHistory { private val events = mutableListOf<OrchestrationEvent>(); fun append(event: OrchestrationEvent) { events += event }; fun all(planId: String): List<OrchestrationEvent> = events.filter { it.planId == planId }.toList() }

/** Engine adaptativo: o host controla todos os estados; providers retornam apenas dados. */
class TaskEngine(private val gateway: ProviderGateway, private val validator: OrchestrationValidator = OrchestrationValidator(), private val history: OrchestrationHistory? = null) {
    data class Step(val taskId: String, val status: OrchestrationTaskStatus, val message: String)
    suspend fun run(plan: OrchestrationPlan, route: suspend (OrchestrationTask) -> String?): Pair<OrchestrationPlan, List<Step>> {
        val checked = OrchestrationPlanValidator.validate(plan); require(checked.valid) { checked.errors.joinToString("; ") }
        var current = plan.copy(status = OrchestrationTaskStatus.RUNNING); val steps = mutableListOf<Step>(); var cycles = 0; val results = mutableMapOf<String, String>()
        while (cycles++ < plan.policy.maxSteps) {
            val ready = current.tasks.filter { task -> task.status in setOf(OrchestrationTaskStatus.PENDING, OrchestrationTaskStatus.RETRYING) && task.dependsOn.all { id -> current.tasks.firstOrNull { it.id == id }?.status == OrchestrationTaskStatus.SUCCEEDED } }.sortedByDescending { it.priority }
            if (ready.isEmpty()) break
            for (candidate in ready) {
                val iaId = route(candidate)
                if (iaId == null) { current = replace(current, candidate.copy(status = OrchestrationTaskStatus.BLOCKED, error = "Nenhuma IA compatível")); steps += Step(candidate.id, OrchestrationTaskStatus.BLOCKED, "Nenhuma IA compatível"); history?.append(OrchestrationEvent(current.id, candidate.id, "BLOCKED", "Nenhuma IA compatível")); continue }
                val running = candidate.copy(status = OrchestrationTaskStatus.RUNNING, assignedIaId = iaId, attempts = candidate.attempts + 1); current = replace(current, running)
                val context = ContextManager(plan.policy).build(running, results); val policyErrors = OrchestrationPolicyGuard.validateContext(context, plan.policy)
                val response = if (policyErrors.isNotEmpty()) ProviderResponse(false, error = policyErrors.joinToString()) else gateway.execute(ProviderRequest(running, context, iaId)); val report = validator.validate(running, response)
                val nextStatus = when (report.outcome) { ValidationOutcome.SUCCEEDED -> OrchestrationTaskStatus.SUCCEEDED; ValidationOutcome.FAILED -> if (running.attempts <= plan.policy.maxRetriesPerTask) OrchestrationTaskStatus.RETRYING else OrchestrationTaskStatus.FAILED; ValidationOutcome.NEEDS_REVISION -> if (running.revisions < plan.policy.maxRevisionsPerPlan) OrchestrationTaskStatus.NEEDS_REVISION else OrchestrationTaskStatus.FAILED; ValidationOutcome.NEEDS_HUMAN -> OrchestrationTaskStatus.WAITING_HUMAN; ValidationOutcome.BLOCKED -> OrchestrationTaskStatus.BLOCKED }
                val finished = running.copy(status = nextStatus, result = response.output, error = report.reasons.joinToString(), revisions = running.revisions + if (nextStatus == OrchestrationTaskStatus.NEEDS_REVISION) 1 else 0); current = replace(current, finished); response.output?.let { results[candidate.id] = it }
                steps += Step(candidate.id, nextStatus, report.reasons.joinToString()); history?.append(OrchestrationEvent(current.id, candidate.id, nextStatus.name, report.reasons.joinToString()))
            }
            if (current.tasks.any { it.status in setOf(OrchestrationTaskStatus.WAITING_HUMAN, OrchestrationTaskStatus.BLOCKED, OrchestrationTaskStatus.FAILED, OrchestrationTaskStatus.NEEDS_REVISION) }) break
        }
        val finalStatus = when { current.tasks.any { it.status == OrchestrationTaskStatus.WAITING_HUMAN } -> OrchestrationTaskStatus.WAITING_HUMAN; current.tasks.any { it.status == OrchestrationTaskStatus.NEEDS_REVISION } -> OrchestrationTaskStatus.NEEDS_REVISION; current.tasks.any { it.status in setOf(OrchestrationTaskStatus.BLOCKED, OrchestrationTaskStatus.FAILED) } -> OrchestrationTaskStatus.FAILED; current.tasks.all { it.status == OrchestrationTaskStatus.SUCCEEDED } -> OrchestrationTaskStatus.SUCCEEDED; else -> OrchestrationTaskStatus.RUNNING }
        return current.copy(status = finalStatus) to steps
    }
    private fun replace(plan: OrchestrationPlan, task: OrchestrationTask) = plan.copy(tasks = plan.tasks.map { if (it.id == task.id) task else it })
}
