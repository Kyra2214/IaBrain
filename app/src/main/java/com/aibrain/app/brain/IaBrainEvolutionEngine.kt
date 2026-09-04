package com.aibrain.app.brain

import java.net.URI
import java.security.MessageDigest

/** Unified domain engine for the v3.3 -> v4.0 evolution.
 * Pure Kotlin: the core does not require network, UI or provider SDKs.
 */
class IaBrainEvolutionEngine(
    private val discovery: DiscoveryEngine = DiscoveryEngine(),
    private val router: RoutingEngine = RoutingEngine(),
    private val synchronizer: CatalogSynchronizer = CatalogSynchronizer(),
    private val executor: AIExecutor = NoopAIExecutor()
) {
    fun discover(sources: List<DiscoverySource>): DiscoveryReport = discovery.discover(sources)

    fun sync(current: List<CatalogEntry>, incoming: List<CatalogEntry>, favorites: Set<String>, history: Set<String>): SyncResult =
        synchronizer.merge(current, incoming, favorites, history)

    fun route(request: ExecutionRequest, candidates: List<Provider>): RouteDecision = router.route(request, candidates)

    fun execute(request: ExecutionRequest, provider: Provider, credential: Credential?): ExecutionResult =
        executor.execute(request, provider, credential)

    fun orchestrate(
        request: ExecutionRequest,
        candidates: List<Provider>,
        credential: Credential?,
        maxSteps: Int = 4
    ): OrchestrationResult {
        require(maxSteps in 1..8) { "maxSteps must be between 1 and 8" }
        var route = router.route(request, candidates)
        if (!route.selected) return OrchestrationResult.Failed("NO_COMPATIBLE_PROVIDER", route)
        var last = executor.execute(request, route.provider!!, credential)
        var steps = 1
        while (!last.success && last.retryable && steps < maxSteps) {
            val currentProvider = route.provider ?: break
            val next = route.alternatives.firstOrNull { it.id != currentProvider.id } ?: break
            route = router.route(request, listOf(next))
            if (!route.selected) break
            last = executor.execute(request, route.provider!!, credential)
            steps++
        }
        return if (last.success) OrchestrationResult.Completed(route, last, steps)
        else OrchestrationResult.Failed(last.code, route, last, steps)
    }
}

data class CatalogEntry(
    val id: String,
    val name: String,
    val url: String,
    val sourcePriority: Int = 0,
    val description: String = "",
    val categories: Set<String> = emptySet(),
    val active: Boolean = true,
    val updatedAt: Long = 0L
) {
    val fingerprint: String get() = fingerprintOf(id, name, url, description, categories.sorted().joinToString(","))
    init {
        require(id.isNotBlank() && name.isNotBlank())
        require(isHttpsUrl(url)) { "Catalog URL must use HTTPS" }
    }
}

data class DiscoveryCandidate(
    val id: String,
    val name: String,
    val url: String,
    val description: String = "",
    val categories: Set<String> = emptySet(),
    val source: String,
    val sourcePriority: Int = 0,
    val active: Boolean = true
)

data class DiscoveryReport(
    val entries: List<CatalogEntry>,
    val rejected: List<String>,
    val duplicatesRemoved: Int,
    val sourceCount: Int
)

interface DiscoverySource {
    val name: String
    val priority: Int
    fun load(): List<DiscoveryCandidate>
}

class DiscoveryEngine {
    fun discover(sources: List<DiscoverySource>): DiscoveryReport {
        val rejected = mutableListOf<String>()
        val all = sources.flatMap { source ->
            source.load().map { it.copy(source = source.name, sourcePriority = maxOf(it.sourcePriority, source.priority)) }
        }
        val valid = all.filter { candidate ->
            val ok = candidate.id.isNotBlank() && candidate.name.isNotBlank() && isHttpsUrl(candidate.url)
            if (!ok) rejected += "${candidate.source}:${candidate.id.ifBlank { candidate.name }}"
            ok
        }
        val normalized = valid.map {
            it.copy(name = normalize(it.name), categories = it.categories.map(::normalize).filter(String::isNotBlank).toSet())
        }
        val grouped = normalized.groupBy { canonicalUrl(it.url) + "|" + normalize(it.name) }
        val entries = grouped.values.map { group ->
            val best = group.maxWithOrNull(compareBy<DiscoveryCandidate> { it.sourcePriority }.thenBy { it.id })!!
            CatalogEntry(best.id, best.name, canonicalUrl(best.url), best.sourcePriority, best.description, best.categories, best.active)
        }.sortedWith(compareByDescending<CatalogEntry> { it.sourcePriority }.thenBy { it.name.lowercase() })
        return DiscoveryReport(entries, rejected, normalized.size - entries.size, sources.size)
    }
}

data class SyncResult(
    val merged: List<CatalogEntry>,
    val added: Int,
    val updated: Int,
    val unchanged: Int,
    val removed: Int,
    val favoritesPreserved: Set<String>,
    val historyPreserved: Set<String>
)

class CatalogSynchronizer {
    fun merge(
        current: List<CatalogEntry>,
        incoming: List<CatalogEntry>,
        favorites: Set<String>,
        history: Set<String>
    ): SyncResult {
        val currentById = current.associateBy { it.id }
        val incomingById = incoming.associateBy { it.id }
        val merged = incomingById.values.map { next ->
            val old = currentById[next.id]
            if (old != null && next.updatedAt < old.updatedAt) old else next
        }.toMutableList()
        current.filter { it.id !in incomingById && it.active }.forEach { merged += it.copy(active = false) }
        val added = incomingById.keys.count { it !in currentById }
        val updated = incomingById.keys.count { id -> id in currentById && currentById.getValue(id).fingerprint != incomingById.getValue(id).fingerprint }
        val unchanged = incomingById.size - added - updated
        val mergedIds = merged.map { it.id }.toSet()
        return SyncResult(
            merged = merged.sortedBy { it.name.lowercase() },
            added = added,
            updated = updated,
            unchanged = unchanged,
            removed = current.count { it.id !in incomingById },
            favoritesPreserved = favorites.intersect(mergedIds),
            historyPreserved = history.intersect(mergedIds)
        )
    }
}

data class ExecutionRequest(
    val command: String,
    val prompt: String,
    val requiredCapabilities: Set<String> = emptySet(),
    val preferredCapabilities: Set<String> = emptySet(),
    val context: String? = null
) {
    init {
        require(command.matches(Regex("^/[a-z0-9-]{1,32}$")))
        require(prompt.isNotBlank())
        require(prompt.length <= 50_000)
    }
}

data class Provider(
    val id: String,
    val name: String,
    val capabilities: Set<String>,
    val quality: Double = .5,
    val speed: Double = .5,
    val reliability: Double = .5,
    val cost: Double = 0.0,
    val enabled: Boolean = true
)

data class RouteDecision(
    val selected: Boolean,
    val provider: Provider?,
    val score: Double,
    val alternatives: List<Provider>,
    val reason: String
)

class RoutingEngine {
    fun route(request: ExecutionRequest, providers: List<Provider>): RouteDecision {
        val compatible = providers.filter { it.enabled && request.requiredCapabilities.all(it.capabilities::contains) }
        if (compatible.isEmpty()) return RouteDecision(false, null, 0.0, emptyList(), "NO_COMPATIBLE_PROVIDER")
        val ranked = compatible.sortedWith(compareByDescending<Provider> { score(request, it) }.thenBy { it.id })
        val top = ranked.first()
        return RouteDecision(true, top, score(request, top), ranked.drop(1).take(3), "DETERMINISTIC_LOCAL_POLICY")
    }

    private fun score(request: ExecutionRequest, provider: Provider): Double {
        val preferred = if (request.preferredCapabilities.isEmpty()) 0.0 else
            request.preferredCapabilities.count { it in provider.capabilities }.toDouble() / request.preferredCapabilities.size
        return preferred * 2.0 + provider.quality * 2.0 + provider.reliability * 1.5 + provider.speed - provider.cost
    }
}

data class Credential(val providerId: String, val secret: String) {
    init { require(secret.isNotBlank()) }
}

data class ExecutionResult(
    val success: Boolean,
    val code: String,
    val retryable: Boolean,
    val output: String = ""
)

interface AIExecutor {
    fun execute(request: ExecutionRequest, provider: Provider, credential: Credential?): ExecutionResult
}

class NoopAIExecutor : AIExecutor {
    override fun execute(request: ExecutionRequest, provider: Provider, credential: Credential?): ExecutionResult {
        if (credential != null && credential.providerId != provider.id) return ExecutionResult(false, "CREDENTIAL_PROVIDER_MISMATCH", false)
        return ExecutionResult(false, "EXECUTOR_NOT_CONFIGURED", false)
    }
}

data class OrchestrationResult private constructor(
    val status: Status,
    val route: RouteDecision,
    val execution: ExecutionResult?,
    val steps: Int,
    val errorCode: String?
) {
    enum class Status { COMPLETED, FAILED }
    companion object {
        fun Completed(route: RouteDecision, execution: ExecutionResult, steps: Int) = OrchestrationResult(Status.COMPLETED, route, execution, steps, null)
        fun Failed(code: String, route: RouteDecision, execution: ExecutionResult? = null, steps: Int = 0) = OrchestrationResult(Status.FAILED, route, execution, steps, code)
    }
}

private fun normalize(value: String): String = value.trim().replace(Regex("\\s+"), " ")
private fun canonicalUrl(value: String): String = URI(value.trim()).let { uri ->
    URI(uri.scheme.lowercase(), uri.userInfo, uri.host.lowercase(), uri.port, uri.path?.trimEnd('/').orEmpty().ifBlank { "/" }, uri.query, null).toString()
}
private fun isHttpsUrl(value: String): Boolean = runCatching {
    val uri = URI(value.trim())
    uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
}.getOrDefault(false)
private fun fingerprintOf(vararg values: String): String {
    val bytes = values.joinToString("\u001f").toByteArray()
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
