package com.aibrain.app.brain

/** v4.1 -> v6.0 autonomous reasoning domain, kept pure Kotlin and deterministic by default. */

data class TaskContext(
    val rawRequest: String,
    val objective: String,
    val constraints: Set<String> = emptySet(),
    val requirements: Set<String> = emptySet(),
    val projectFacts: Set<String> = emptySet(),
    val priorDecisions: List<String> = emptyList(),
    val confidence: Double = 0.0
) {
    init { require(rawRequest.isNotBlank()); require(objective.isNotBlank()); require(confidence in 0.0..1.0) }
}

class ContextIntelligence {
    fun understand(request: String, projectFacts: Set<String> = emptySet(), priorDecisions: List<String> = emptyList()): TaskContext {
        require(request.isNotBlank())
        val normalized = request.trim().replace(Regex("\\s+"), " ")
        val constraints = linkedSetOf<String>()
        val requirements = linkedSetOf<String>()
        val lower = normalized.lowercase()
        if (lower.contains("offline") || lower.contains("local-first")) constraints += "OFFLINE_FIRST"
        if (lower.contains("sem api") || lower.contains("without api")) constraints += "NO_EXTERNAL_API_REQUIRED"
        if (lower.contains("não quebr") || lower.contains("nao quebr") || lower.contains("preserve o contrato") || lower.contains("preservar o contrato")) constraints += "PRESERVE_EXISTING_CONTRACT"
        if (lower.contains("github")) requirements += "GITHUB_INTEGRATION"
        if (lower.contains("test")) requirements += "TEST_COVERAGE"
        if (lower.contains("android")) requirements += "ANDROID_COMPATIBILITY"
        val objective = normalized
            .replaceFirst(Regex("^(faça|faca|implemente|crie|adicione|construa)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { normalized }
        return TaskContext(normalized, objective, constraints, requirements, projectFacts, priorDecisions, 0.85)
    }
}

data class PlanStep(
    val id: String,
    val objective: String,
    val dependencies: Set<String> = emptySet(),
    val requiredCapabilities: Set<String> = emptySet(),
    val risk: Risk = Risk.LOW
)

enum class Risk { LOW, MEDIUM, HIGH, CRITICAL }

data class ExecutionPlan(val steps: List<PlanStep>, val orderedStepIds: List<String>, val valid: Boolean, val errors: List<String>)

class AutonomousPlanner {
    fun plan(context: TaskContext): ExecutionPlan {
        val steps = linkedMapOf<String, PlanStep>()
        steps["understand"] = PlanStep("understand", "Validate task context")
        steps["design"] = PlanStep("design", "Define implementation approach", setOf("understand"), risk = Risk.LOW)
        steps["implement"] = PlanStep("implement", "Implement the required change", setOf("design"), setOf("code"), Risk.MEDIUM)
        steps["test"] = PlanStep("test", "Run automated validation", setOf("implement"), setOf("test"), Risk.MEDIUM)
        if (context.requirements.contains("GITHUB_INTEGRATION")) steps["review"] = PlanStep("review", "Review repository integration", setOf("test"), setOf("github"), Risk.MEDIUM)
        val order = topologicalOrder(steps.values.toList())
        return if (order.size == steps.size) ExecutionPlan(steps.values.toList(), order, true, emptyList())
        else ExecutionPlan(steps.values.toList(), order, false, listOf("PLAN_DEPENDENCY_CYCLE"))
    }

    private fun topologicalOrder(steps: List<PlanStep>): List<String> {
        val pending = steps.associateBy { it.id }.toMutableMap()
        val result = mutableListOf<String>()
        while (pending.isNotEmpty()) {
            val ready = pending.values.filter { it.dependencies.all(result::contains) }.sortedBy { it.id }
            if (ready.isEmpty()) return result
            ready.forEach { pending.remove(it.id); result += it.id }
        }
        return result
    }
}

data class Evaluation(
    val success: Boolean,
    val score: Double,
    val objectiveMet: Boolean,
    val partial: Boolean,
    val retryRecommended: Boolean,
    val evidence: List<String>
)

class ResultEvaluator {
    fun evaluate(context: TaskContext, result: String?, expectedSignals: Set<String> = emptySet()): Evaluation {
        if (result.isNullOrBlank()) return Evaluation(false, 0.0, false, false, true, listOf("NO_OUTPUT"))
        val normalized = result.lowercase()
        val hits = expectedSignals.count { normalized.contains(it.lowercase()) }
        val signalScore = if (expectedSignals.isEmpty()) 1.0 else hits.toDouble() / expectedSignals.size
        val objectiveWords = context.objective.lowercase().split(Regex("\\W+")).filter { it.length >= 4 }.toSet()
        val objectiveHits = objectiveWords.count { normalized.contains(it) }
        val objectiveScore = if (objectiveWords.isEmpty()) 0.5 else objectiveHits.toDouble() / objectiveWords.size
        val score = ((signalScore * 0.6) + (objectiveScore * 0.4)).coerceIn(0.0, 1.0)
        return Evaluation(score >= 0.75, score, score >= 0.75, score > 0.0, score < 0.75, listOf("SIGNALS:$hits/${expectedSignals.size}", "OBJECTIVE_MATCH:$objectiveHits/${objectiveWords.size}"))
    }
}

enum class CouncilRole { PLANNER, IMPLEMENTER, REVIEWER, TESTER, EVALUATOR }
data class CouncilOpinion(val role: CouncilRole, val decision: Decision, val rationale: String, val confidence: Double)
enum class Decision { APPROVE, REVISE, BLOCK }
data class CouncilResult(val decision: Decision, val opinions: List<CouncilOpinion>, val confidence: Double)

class MultiAgentCouncil {
    fun deliberate(context: TaskContext, plan: ExecutionPlan, evaluation: Evaluation? = null): CouncilResult {
        val opinions = mutableListOf<CouncilOpinion>()
        opinions += CouncilOpinion(CouncilRole.PLANNER, if (plan.valid) Decision.APPROVE else Decision.BLOCK, if (plan.valid) "Plan dependencies are valid" else "Plan is invalid", if (plan.valid) .9 else .99)
        opinions += CouncilOpinion(CouncilRole.IMPLEMENTER, if (plan.steps.any { it.id == "implement" }) Decision.APPROVE else Decision.REVISE, "Implementation step is represented", .8)
        opinions += CouncilOpinion(CouncilRole.REVIEWER, if (context.confidence >= .7) Decision.APPROVE else Decision.REVISE, "Context confidence checked", context.confidence)
        opinions += CouncilOpinion(CouncilRole.TESTER, if (plan.steps.any { it.id == "test" }) Decision.APPROVE else Decision.BLOCK, "Validation step is represented", .9)
        if (evaluation != null) opinions += CouncilOpinion(CouncilRole.EVALUATOR, if (evaluation.success) Decision.APPROVE else Decision.REVISE, "Result score=${"%.2f".format(evaluation.score)}", evaluation.score)
        val blocks = opinions.count { it.decision == Decision.BLOCK }
        val revisions = opinions.count { it.decision == Decision.REVISE }
        val decision = when { blocks > 0 -> Decision.BLOCK; revisions > 0 -> Decision.REVISE; else -> Decision.APPROVE }
        return CouncilResult(decision, opinions, opinions.map { it.confidence }.average())
    }
}

enum class FactoryStage { UNDERSTAND, PLAN, ROUTE, IMPLEMENT, TEST, REVIEW, QUALITY_GATE, PUBLISH, COMPLETED, FAILED }
data class FactoryRun(val id: String, val stage: FactoryStage, val plan: ExecutionPlan, val evaluation: Evaluation?, val council: CouncilResult?, val evidence: List<String>, val approved: Boolean, val error: String? = null)

class AutonomousSoftwareFactory(
    private val contextIntelligence: ContextIntelligence = ContextIntelligence(),
    private val planner: AutonomousPlanner = AutonomousPlanner(),
    private val evaluator: ResultEvaluator = ResultEvaluator(),
    private val council: MultiAgentCouncil = MultiAgentCouncil()
) {
    fun prepare(request: String, projectFacts: Set<String> = emptySet(), priorDecisions: List<String> = emptyList()): FactoryRun {
        val context = contextIntelligence.understand(request, projectFacts, priorDecisions)
        val plan = planner.plan(context)
        val councilResult = council.deliberate(context, plan)
        return FactoryRun("factory-${fingerprint(request)}", FactoryStage.PLAN, plan, null, councilResult, listOf("CONTEXT_READY", "PLAN_READY"), councilResult.decision == Decision.APPROVE, if (!plan.valid) "PLAN_DEPENDENCY_CYCLE" else null)
    }

    fun evaluate(run: FactoryRun, output: String?, expectedSignals: Set<String> = emptySet()): FactoryRun {
        val context = TaskContext(run.plan.steps.joinToString(" ") { it.objective }, run.plan.steps.joinToString(" ") { it.objective }, confidence = 1.0)
        val evaluation = evaluator.evaluate(context, output, expectedSignals)
        val councilResult = council.deliberate(context, run.plan, evaluation)
        val stage = if (evaluation.success && councilResult.decision == Decision.APPROVE) FactoryStage.QUALITY_GATE else FactoryStage.FAILED
        return run.copy(stage = stage, evaluation = evaluation, council = councilResult, approved = stage == FactoryStage.QUALITY_GATE, evidence = run.evidence + evaluation.evidence)
    }

    private fun fingerprint(value: String): String = value.hashCode().toString(16)
}

data class MemoryRecord(val key: String, val value: String, val confidence: Double, val timestamp: Long, val tags: Set<String> = emptySet())

class AdaptiveMemory {
    private val records = linkedMapOf<String, MemoryRecord>()
    fun remember(record: MemoryRecord) { require(record.key.isNotBlank()); records[record.key] = record }
    fun recall(key: String): MemoryRecord? = records[key]
    fun search(tag: String): List<MemoryRecord> = records.values.filter { tag in it.tags }.sortedByDescending { it.timestamp }
    fun snapshot(): List<MemoryRecord> = records.values.toList()
}

data class LearningObservation(val task: String, val providerId: String?, val success: Boolean, val score: Double, val latencyMs: Long, val cost: Double)
data class LearningUpdate(val providerId: String, val observations: Int, val successRate: Double, val averageScore: Double, val averageLatencyMs: Long, val averageCost: Double)

class LearningEngine {
    private val observations = mutableListOf<LearningObservation>()
    fun record(observation: LearningObservation) { observations += observation }
    fun update(providerId: String): LearningUpdate {
        val items = observations.filter { it.providerId == providerId }
        if (items.isEmpty()) return LearningUpdate(providerId, 0, 0.0, 0.0, 0L, 0.0)
        return LearningUpdate(providerId, items.size, items.count { it.success }.toDouble() / items.size, items.map { it.score }.average(), items.map { it.latencyMs }.average().toLong(), items.map { it.cost }.average())
    }
}

data class ProviderProfile(val providerId: String, val quality: Double, val reliability: Double, val speed: Double, val cost: Double)
data class OptimizationDecision(val providerId: String, val score: Double, val reason: String)

class CostLatencyOptimizer {
    fun choose(profiles: List<ProviderProfile>, prioritizeCost: Boolean = false, prioritizeSpeed: Boolean = false): OptimizationDecision? {
        if (profiles.isEmpty()) return null
        fun score(p: ProviderProfile): Double {
            val base = p.quality * 2.0 + p.reliability * 1.5
            val speed = if (prioritizeSpeed) p.speed * 2.0 else p.speed
            val cost = if (prioritizeCost) p.cost * 2.0 else p.cost
            return base + speed - cost
        }
        val best = profiles.maxWithOrNull(compareBy<ProviderProfile> { score(it) }.thenBy { it.providerId }) ?: return null
        return OptimizationDecision(best.providerId, score(best), "COST_LATENCY_POLICY")
    }
}

data class WorkflowDefinition(val id: String, val steps: List<String>, val maxRuns: Int = 1)
data class WorkflowState(val workflowId: String, val runCount: Int, val currentStep: Int, val completed: Boolean)

class PersistentWorkflowEngine {
    private val states = mutableMapOf<String, WorkflowState>()
    fun start(definition: WorkflowDefinition): WorkflowState {
        require(definition.steps.isNotEmpty()); require(definition.maxRuns > 0)
        val state = WorkflowState(definition.id, 1, 0, definition.steps.size == 1)
        states[definition.id] = state
        return state
    }
    fun advance(definition: WorkflowDefinition): WorkflowState {
        val current = states[definition.id] ?: return start(definition)
        if (current.completed) return current
        val lastIndex = definition.steps.lastIndex
        val nextStep = (current.currentStep + 1).coerceAtMost(lastIndex)
        val done = nextStep >= lastIndex
        val next = current.copy(currentStep = nextStep, completed = done)
        states[definition.id] = next
        return next
    }
    fun state(id: String): WorkflowState? = states[id]
}

enum class ApprovalLevel { NONE, HUMAN_REQUIRED }
data class AutonomyPolicy(val approvalLevel: ApprovalLevel = ApprovalLevel.HUMAN_REQUIRED, val protectedStages: Set<FactoryStage> = setOf(FactoryStage.PUBLISH), val maxAutonomousRetries: Int = 2)
data class AutonomyDecision(val allowed: Boolean, val requiresHumanApproval: Boolean, val reason: String)

class AdvancedAutonomy(private val policy: AutonomyPolicy = AutonomyPolicy()) {
    fun authorize(stage: FactoryStage, retries: Int = 0): AutonomyDecision {
        if (retries > policy.maxAutonomousRetries) return AutonomyDecision(false, false, "RETRY_LIMIT_REACHED")
        if (stage in policy.protectedStages && policy.approvalLevel == ApprovalLevel.HUMAN_REQUIRED) return AutonomyDecision(false, true, "HUMAN_APPROVAL_REQUIRED")
        return AutonomyDecision(true, false, "AUTONOMOUS_STAGE_ALLOWED")
    }
}

class IaBrainAutonomyEngine(
    val context: ContextIntelligence = ContextIntelligence(),
    val planner: AutonomousPlanner = AutonomousPlanner(),
    val evaluator: ResultEvaluator = ResultEvaluator(),
    val council: MultiAgentCouncil = MultiAgentCouncil(),
    val factory: AutonomousSoftwareFactory = AutonomousSoftwareFactory(),
    val memory: AdaptiveMemory = AdaptiveMemory(),
    val learning: LearningEngine = LearningEngine(),
    val optimizer: CostLatencyOptimizer = CostLatencyOptimizer(),
    val workflows: PersistentWorkflowEngine = PersistentWorkflowEngine(),
    val autonomy: AdvancedAutonomy = AdvancedAutonomy()
)
