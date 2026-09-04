package com.aibrain.app.brain

/** IaBrain runtime v6.1 -> v7.0. Browser is primary; APIs are secondary and injectable. */
enum class ExecutionChannel { BROWSER, API }
enum class RuntimeStage { CONTEXT, PLAN, ROUTE, DISPATCH, WAITING_FOR_BROWSER, EVALUATE, MEMORY, RECOVERY, HUMAN_APPROVAL, COMPLETED, FAILED }

data class RuntimeRequest(val id: String, val command: String, val prompt: String, val preferredAiId: String? = null, val browserPreferred: Boolean = true, val apiOnly: Boolean = false, val requiredCapabilities: Set<String> = emptySet(), val context: String? = null) {
    init { require(id.isNotBlank()); require(command.matches(Regex("^/[a-z0-9-]{1,32}$"))); require(prompt.isNotBlank()); require(prompt.length <= 50_000) }
}

data class RuntimeCandidate(val id: String, val name: String, val channel: ExecutionChannel, val capabilities: Set<String> = emptySet(), val quality: Double = .5, val reliability: Double = .5, val speed: Double = .5, val cost: Double = 0.0, val url: String? = null, val enabled: Boolean = true) {
    init { require(id.isNotBlank() && name.isNotBlank()); require(quality in 0.0..1.0 && reliability in 0.0..1.0 && speed in 0.0..1.0); require(cost >= 0.0); if (channel == ExecutionChannel.BROWSER) require(!url.isNullOrBlank()) }
}

data class RuntimeRoute(val selected: RuntimeCandidate?, val alternatives: List<RuntimeCandidate>, val reason: String, val channel: ExecutionChannel?)

class BrowserFirstRouter {
    fun route(request: RuntimeRequest, candidates: List<RuntimeCandidate>): RuntimeRoute {
        val compatible = candidates.filter { it.enabled && request.requiredCapabilities.all(it.capabilities::contains) && (!request.apiOnly || it.channel == ExecutionChannel.API) }
        if (compatible.isEmpty()) return RuntimeRoute(null, emptyList(), "NO_COMPATIBLE_EXECUTOR", null)
        val preferred = request.preferredAiId?.let { id -> compatible.filter { it.id == id } }.orEmpty()
        val pool = if (preferred.isNotEmpty()) preferred + compatible.filter { it.id != request.preferredAiId } else compatible
        val ranked = pool.sortedWith(compareByDescending<RuntimeCandidate> { priority(request, it) }.thenByDescending { score(it) }.thenBy { it.id })
        val selected = ranked.first()
        return RuntimeRoute(selected, ranked.drop(1).take(5), if (selected.channel == ExecutionChannel.BROWSER) "BROWSER_FIRST" else "API_FALLBACK", selected.channel)
    }
    private fun priority(request: RuntimeRequest, candidate: RuntimeCandidate): Int = when { request.apiOnly && candidate.channel == ExecutionChannel.API -> 2; request.browserPreferred && candidate.channel == ExecutionChannel.BROWSER -> 2; else -> 1 }
    private fun score(candidate: RuntimeCandidate): Double = candidate.quality * 2.0 + candidate.reliability * 1.5 + candidate.speed - candidate.cost
}

data class BrowserDispatch(val requestId: String, val aiId: String, val aiName: String, val url: String, val prompt: String, val prefillAttempted: Boolean, val awaitingUser: Boolean)
data class ApiDispatch(val requestId: String, val providerId: String, val output: String)
sealed class RuntimeDispatchResult { data class Browser(val dispatch: BrowserDispatch) : RuntimeDispatchResult(); data class Api(val dispatch: ApiDispatch) : RuntimeDispatchResult(); data class Failure(val code: String, val retryable: Boolean) : RuntimeDispatchResult() }

interface RuntimeExecutionPort { fun openBrowser(request: RuntimeRequest, candidate: RuntimeCandidate): RuntimeDispatchResult; fun executeApi(request: RuntimeRequest, candidate: RuntimeCandidate): RuntimeDispatchResult }
class NoopRuntimeExecutionPort : RuntimeExecutionPort { override fun openBrowser(request: RuntimeRequest, candidate: RuntimeCandidate) = RuntimeDispatchResult.Failure("BROWSER_EXECUTOR_NOT_CONFIGURED", false); override fun executeApi(request: RuntimeRequest, candidate: RuntimeCandidate) = RuntimeDispatchResult.Failure("API_EXECUTOR_NOT_CONFIGURED", false) }

data class RuntimeCheckpoint(val requestId: String, val stage: RuntimeStage, val selectedAiId: String?, val attempt: Int, val evidence: List<String> = emptyList(), val lastError: String? = null)
interface RuntimeCheckpointStore { fun save(checkpoint: RuntimeCheckpoint); fun load(requestId: String): RuntimeCheckpoint?; fun clear(requestId: String) }
class InMemoryRuntimeCheckpointStore : RuntimeCheckpointStore { private val checkpoints = mutableMapOf<String, RuntimeCheckpoint>(); override fun save(checkpoint: RuntimeCheckpoint) { checkpoints[checkpoint.requestId] = checkpoint }; override fun load(requestId: String): RuntimeCheckpoint? = checkpoints[requestId]; override fun clear(requestId: String) { checkpoints.remove(requestId) } }

data class RuntimeResult(val requestId: String, val stage: RuntimeStage, val route: RuntimeRoute, val dispatch: RuntimeDispatchResult?, val evaluation: Evaluation?, val checkpoint: RuntimeCheckpoint, val requiresHumanApproval: Boolean = false, val errorCode: String? = null)

class IaBrainAutonomousRuntime(private val router: BrowserFirstRouter = BrowserFirstRouter(), private val autonomy: AdvancedAutonomy = AdvancedAutonomy(), private val evaluator: ResultEvaluator = ResultEvaluator(), private val memory: AdaptiveMemory = AdaptiveMemory(), private val checkpointStore: RuntimeCheckpointStore = InMemoryRuntimeCheckpointStore()) {
    fun start(request: RuntimeRequest, candidates: List<RuntimeCandidate>, port: RuntimeExecutionPort): RuntimeResult {
        val existing = checkpointStore.load(request.id)
        if (existing != null && existing.stage !in setOf(RuntimeStage.COMPLETED, RuntimeStage.FAILED)) return recover(request, candidates, port)
        checkpointStore.save(RuntimeCheckpoint(request.id, RuntimeStage.CONTEXT, null, 0))
        checkpointStore.save(RuntimeCheckpoint(request.id, RuntimeStage.PLAN, null, 0, listOf("CONTEXT_READY", "PLAN_READY")))
        val route = router.route(request, candidates)
        if (route.selected == null) return fail(request.id, route, "NO_COMPATIBLE_EXECUTOR")
        checkpointStore.save(RuntimeCheckpoint(request.id, RuntimeStage.ROUTE, route.selected.id, 0, listOf(route.reason)))
        val authorization = autonomy.authorize(FactoryStage.IMPLEMENT)
        if (!authorization.allowed) return fail(request.id, route, authorization.reason)
        val selected = route.selected
        val dispatch = when (selected.channel) { ExecutionChannel.BROWSER -> port.openBrowser(request, selected); ExecutionChannel.API -> port.executeApi(request, selected) }
        return when (dispatch) {
            is RuntimeDispatchResult.Browser -> { checkpointStore.save(RuntimeCheckpoint(request.id, RuntimeStage.WAITING_FOR_BROWSER, selected.id, 1, listOf("BROWSER_OPENED"))); RuntimeResult(request.id, RuntimeStage.WAITING_FOR_BROWSER, route, dispatch, null, checkpointStore.load(request.id)!!) }
            is RuntimeDispatchResult.Api -> completeApi(request, route, dispatch)
            is RuntimeDispatchResult.Failure -> { checkpointStore.save(RuntimeCheckpoint(request.id, RuntimeStage.FAILED, selected.id, 1, emptyList(), dispatch.code)); RuntimeResult(request.id, RuntimeStage.FAILED, route, dispatch, null, checkpointStore.load(request.id)!!, errorCode = dispatch.code) }
        }
    }
    fun recover(request: RuntimeRequest, candidates: List<RuntimeCandidate>, port: RuntimeExecutionPort): RuntimeResult {
        val checkpoint = checkpointStore.load(request.id) ?: return start(request, candidates, port)
        val selected = candidates.firstOrNull { it.id == checkpoint.selectedAiId }
        if (checkpoint.stage == RuntimeStage.WAITING_FOR_BROWSER && selected != null) return RuntimeResult(request.id, RuntimeStage.WAITING_FOR_BROWSER, router.route(request, listOf(selected) + candidates.filter { it.id != selected.id }), null, null, checkpoint)
        return start(request, candidates, port)
    }
    fun continueFromBrowser(request: RuntimeRequest, candidates: List<RuntimeCandidate>, browserOutput: String?, expectedSignals: Set<String> = emptySet()): RuntimeResult {
        val checkpoint = checkpointStore.load(request.id) ?: return fail(request.id, RuntimeRoute(null, emptyList(), "NO_CHECKPOINT", null), "NO_CHECKPOINT")
        val selected = candidates.firstOrNull { it.id == checkpoint.selectedAiId }
        val route = if (selected != null) router.route(request, listOf(selected)) else RuntimeRoute(null, emptyList(), "NO_SELECTED_BROWSER_AI", null)
        if (route.selected == null) return fail(request.id, route, "NO_SELECTED_BROWSER_AI")
        val context = TaskContext(request.prompt, request.prompt, projectFacts = setOf("BROWSER_EXECUTION"), priorDecisions = memory.snapshot().map { it.value }, confidence = 1.0)
        val evaluation = evaluator.evaluate(context, browserOutput, expectedSignals)
        val nextStage = if (evaluation.success) RuntimeStage.COMPLETED else RuntimeStage.EVALUATE
        checkpointStore.save(RuntimeCheckpoint(request.id, nextStage, route.selected.id, checkpoint.attempt, evaluation.evidence, if (evaluation.success) null else "EVALUATION_RETRY_RECOMMENDED"))
        memory.remember(MemoryRecord("runtime:${request.id}", browserOutput.orEmpty(), evaluation.score, System.currentTimeMillis(), setOf("browser", "runtime")))
        val saved = checkpointStore.load(request.id)!!
        return RuntimeResult(request.id, saved.stage, route, null, evaluation, saved, errorCode = if (evaluation.success) null else "EVALUATION_RETRY_RECOMMENDED")
    }
    fun authorizePublication(requestId: String, humanApproved: Boolean): RuntimeResult {
        val checkpoint = checkpointStore.load(requestId) ?: return RuntimeResult(requestId, RuntimeStage.FAILED, RuntimeRoute(null, emptyList(), "NO_CHECKPOINT", null), null, null, RuntimeCheckpoint(requestId, RuntimeStage.FAILED, null, 0, lastError = "NO_CHECKPOINT"), errorCode = "NO_CHECKPOINT")
        val route = RuntimeRoute(null, emptyList(), "PUBLICATION_PROTECTED", null)
        val authorization = autonomy.authorize(FactoryStage.PUBLISH)
        if (!authorization.requiresHumanApproval) return RuntimeResult(requestId, RuntimeStage.FAILED, route, null, null, checkpoint, errorCode = "PUBLICATION_POLICY_INVALID")
        if (!humanApproved) { val waiting = checkpoint.copy(stage = RuntimeStage.HUMAN_APPROVAL, lastError = "HUMAN_APPROVAL_REQUIRED"); checkpointStore.save(waiting); return RuntimeResult(requestId, RuntimeStage.HUMAN_APPROVAL, route, null, null, waiting, true, "HUMAN_APPROVAL_REQUIRED") }
        val completed = checkpoint.copy(stage = RuntimeStage.COMPLETED, lastError = null, evidence = checkpoint.evidence + "HUMAN_APPROVAL_GRANTED")
        checkpointStore.save(completed)
        return RuntimeResult(requestId, RuntimeStage.COMPLETED, route, null, null, completed)
    }
    private fun completeApi(request: RuntimeRequest, route: RuntimeRoute, dispatch: RuntimeDispatchResult.Api): RuntimeResult { checkpointStore.save(RuntimeCheckpoint(request.id, RuntimeStage.COMPLETED, route.selected?.id, 1, listOf("API_COMPLETED"))); return RuntimeResult(request.id, RuntimeStage.COMPLETED, route, dispatch, null, checkpointStore.load(request.id)!!) }
    private fun fail(requestId: String, route: RuntimeRoute, code: String): RuntimeResult { val checkpoint = RuntimeCheckpoint(requestId, RuntimeStage.FAILED, route.selected?.id, 0, lastError = code); checkpointStore.save(checkpoint); return RuntimeResult(requestId, RuntimeStage.FAILED, route, null, null, checkpoint, errorCode = code) }
}
