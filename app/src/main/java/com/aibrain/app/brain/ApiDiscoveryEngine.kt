package com.aibrain.app.brain

import com.aibrain.app.model.ApiAnalysis
import com.aibrain.app.model.ApiAuthentication
import com.aibrain.app.model.ApiReviewDecision
import com.aibrain.app.model.ApiStatus
import com.aibrain.app.model.PublicApi
import com.aibrain.app.model.PublicApiMergePolicy
import com.aibrain.app.repository.ApiCatalogMergeResult
import com.aibrain.app.repository.PublicApiCatalogRepository

interface ApiReviewer {
    fun review(api: PublicApi, security: com.aibrain.app.model.ApiSecurityReport, contract: com.aibrain.app.model.ApiContractReport): ApiReviewDecision
}

/** Safe local fallback reviewer; a provider can be injected without coupling the catalog to one service. */
object LocalApiReviewer : ApiReviewer {
    override fun review(
        api: PublicApi,
        security: com.aibrain.app.model.ApiSecurityReport,
        contract: com.aibrain.app.model.ApiContractReport
    ): ApiReviewDecision = when {
        security.blockers.isNotEmpty() -> ApiReviewDecision.REJECT
        security.warnings.isNotEmpty() || contract.unknownFields.isNotEmpty() -> ApiReviewDecision.REVIEW_REQUIRED
        else -> ApiReviewDecision.APPROVE
    }
}

data class ApiDiscoverySnapshot(
    val candidates: List<PublicApi>,
    val sourceErrors: Map<String, String> = emptyMap(),
    val complete: Boolean = sourceErrors.isEmpty()
)

data class RankedPublicApi(val api: PublicApi, val analysis: ApiAnalysis, val score: Int)

data class ApiCatalogUpdateResult(
    val discovered: Int,
    val accepted: Int,
    val rejected: Int,
    val reviewRequired: Int,
    val sourceErrors: Map<String, String>,
    val merge: ApiCatalogMergeResult
)

/**
 * The product-level engine. Sources are independent and the local repository is
 * the source of truth; network discovery is optional and never required to read the catalog.
 */
class ApiDiscoveryEngine(
    private val sources: List<ApiDiscoverySource> = listOf(PublicApisIoSource()),
    private val reviewer: ApiReviewer = LocalApiReviewer
) {
    suspend fun discover(query: ApiDiscoveryQuery = ApiDiscoveryQuery()): ApiDiscoverySnapshot {
        val results = sources.map { source ->
            runCatching { source.discover(query) }
                .getOrElse { ApiDiscoveryResult(source.source, emptyList(), complete = false, error = it.message ?: "Fonte indisponível") }
        }
        val errors = results.filterNot { it.complete }.associate { it.source.key to (it.error ?: "Fonte indisponível") }
        return ApiDiscoverySnapshot(
            candidates = deduplicate(results.flatMap { it.apis }.map(::normalize)),
            sourceErrors = errors,
            complete = errors.isEmpty()
        )
    }

    fun normalize(api: PublicApi): PublicApi {
        val normalizedBase = normalizeUrl(api.baseUrl)
        val normalizedDocs = normalizeUrl(api.documentationUrl)
        val id = api.id.trim().ifBlank {
            "api-" + (normalizedBase ?: api.name.trim().lowercase())
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
        }
        return api.copy(
            id = id,
            name = api.name.trim(),
            description = api.description.trim(),
            category = api.category.trim().ifBlank { "Uncategorized" },
            baseUrl = normalizedBase,
            documentationUrl = normalizedDocs,
            authentication = api.authentication,
            endpoints = api.endpoints.map { endpoint ->
                endpoint.copy(method = endpoint.normalizedMethod, path = endpoint.normalizedPath)
            }.distinctBy { it.normalizedMethod + " " + it.normalizedPath },
            capabilities = api.capabilities.map { it.trim().lowercase() }.filter(String::isNotBlank).distinct().sorted(),
            sources = (api.allSources + api.source).distinct()
        )
    }

    fun deduplicate(apis: List<PublicApi>): List<PublicApi> = PublicApiMergePolicy.deduplicate(apis.map(::normalize))

    fun validate(api: PublicApi) = ApiSecurityAnalyzer.analyze(normalize(api))

    fun analyze(api: PublicApi): ApiAnalysis {
        val normalized = normalize(api)
        val security = validate(normalized)
        val contract = ApiContractIntelligence.analyze(normalized)
        val review = reviewer.review(normalized, security, contract)
        val score = score(normalized, security, contract, review)
        return ApiAnalysis(normalized, security, contract, review, score)
    }

    fun rank(apis: List<PublicApi>): List<RankedPublicApi> = apis
        .map(::analyze)
        .map { result -> RankedPublicApi(result.api, result, result.score) }
        .sortedWith(compareByDescending<RankedPublicApi> { it.score }.thenBy { it.api.id })

    fun suggest(query: ApiDiscoveryQuery, catalog: List<PublicApi>): List<RankedPublicApi> {
        val text = query.text.trim().lowercase()
        val category = query.category?.trim()?.lowercase().orEmpty()
        return rank(catalog.filter { api ->
            (text.isBlank() || api.searchableText.contains(text)) &&
                (category.isBlank() || api.category.lowercase() == category)
        }).take(query.limit.coerceIn(1, 500))
    }

    suspend fun updateCatalog(repository: PublicApiCatalogRepository): ApiCatalogUpdateResult {
        val snapshot = discover(ApiDiscoveryQuery(limit = 500))
        val analyses = snapshot.candidates.map(::analyze)
        val accepted = analyses.filter { it.review != ApiReviewDecision.REJECT }.map { analyzed ->
            analyzed.api.copy(status = when (analyzed.review) {
                ApiReviewDecision.APPROVE -> ApiStatus.APPROVED
                ApiReviewDecision.REVIEW_REQUIRED -> ApiStatus.REVIEW_REQUIRED
                ApiReviewDecision.REJECT -> ApiStatus.REJECTED
            })
        }
        val merge = repository.merge(accepted)
        return ApiCatalogUpdateResult(
            discovered = analyses.size,
            accepted = accepted.count { it.status == ApiStatus.APPROVED },
            rejected = analyses.count { it.review == ApiReviewDecision.REJECT },
            reviewRequired = accepted.count { it.status == ApiStatus.REVIEW_REQUIRED },
            sourceErrors = snapshot.sourceErrors,
            merge = merge
        )
    }

    private fun score(
        api: PublicApi,
        security: com.aibrain.app.model.ApiSecurityReport,
        contract: com.aibrain.app.model.ApiContractReport,
        review: ApiReviewDecision
    ): Int {
        val documentation = if (api.documentationUrl != null) 15 else 0
        val contractQuality = when {
            contract.knownEndpointCount == 0 -> 0
            contract.unknownFields.isEmpty() -> 15
            else -> 8
        }
        val reliability = api.reliability?.coerceIn(0, 100)?.times(0.15)?.toInt() ?: 0
        val sourceReputation = (api.allSources.maxOfOrNull { it.priority } ?: 0) / 10
        val reviewPenalty = if (review == ApiReviewDecision.REJECT) 30 else if (review == ApiReviewDecision.REVIEW_REQUIRED) 5 else 0
        return (security.score * 0.55 + documentation + contractQuality + reliability + sourceReputation - reviewPenalty)
            .toInt()
            .coerceIn(0, 100)
    }

    private fun normalizeUrl(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() }?.removeSuffix("/")
}
