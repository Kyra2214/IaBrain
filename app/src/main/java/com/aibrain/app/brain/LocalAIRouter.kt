package com.aibrain.app.brain

import com.aibrain.app.command.ParsedSlashCommand
import com.aibrain.app.command.SlashCommandParser

data class RoutingRequest(
    val rawUserRequest: String,
    val canonicalCommand: String?,
    val freeArguments: String = "",
    val namedParameters: Map<String, String> = emptyMap(),
    val requiredCapabilities: Set<String> = emptySet(),
    val preferredCapabilities: Set<String> = emptySet(),
    val context: String? = null,
    val preferredAIIds: Set<String> = emptySet()
)

data class RoutingCandidate(
    val iaId: String,
    val nome: String,
    val capabilities: Set<String> = emptySet(),
    val supportedCommands: Set<String> = emptySet(),
    val specialties: Set<String> = emptySet(),
    val quality: Double = 0.5,
    val speed: Double = 0.5,
    val cost: Double = 0.0,
    val supportsCode: Boolean = false,
    val supportsFiles: Boolean = false,
    val supportsImages: Boolean = false,
    val supportsWeb: Boolean = false,
    val supportsReasoning: Boolean = false,
    val reliability: Double = 0.5,
    val contextQuality: Double = 0.5,
    val isDefaultProfile: Boolean = true
)

data class RoutingScore(
    val commandCompatibility: Double,
    val capabilityCompatibility: Double,
    val specialization: Double,
    val quality: Double,
    val speed: Double,
    val context: Double,
    val cost: Double,
    val preference: Double = 0.0,
    val reliability: Double = 0.5
) {
    fun total(policy: RoutingPolicy): Double =
        commandCompatibility * policy.commandWeight +
            capabilityCompatibility * policy.capabilityWeight +
            specialization * policy.specializationWeight +
            quality * policy.qualityWeight +
            speed * policy.speedWeight +
            context * policy.contextWeight +
            preference * policy.preferenceWeight +
            reliability * policy.reliabilityWeight -
            cost * policy.costWeight
}

data class RoutingPolicy(
    val commandWeight: Double = 4.0,
    val capabilityWeight: Double = 4.0,
    val specializationWeight: Double = 2.0,
    val qualityWeight: Double = 1.5,
    val speedWeight: Double = 0.5,
    val contextWeight: Double = 0.5,
    val preferenceWeight: Double = 1.5,
    val reliabilityWeight: Double = 1.0,
    val costWeight: Double = 1.0,
    val alternatives: Int = 3
) {
    companion object {
        fun default() = RoutingPolicy()
    }
}

data class RoutingCandidateScore(val candidate: RoutingCandidate, val score: RoutingScore)

enum class RoutingStatus { SELECTED, NO_COMPATIBLE_PROVIDER }

data class RoutingDecision(
    val status: RoutingStatus,
    val command: String?,
    val selectedAI: RoutingCandidate?,
    val score: RoutingScore?,
    val confidence: Double,
    val alternatives: List<RoutingCandidateScore>,
    val reasons: List<String>
)

object LocalAIRouter {
    fun request(
        raw: String,
        parsed: ParsedSlashCommand? = SlashCommandParser.parse(raw),
        required: Set<String> = emptySet(),
        preferred: Set<String> = emptySet(),
        context: String? = null,
        preferredAIIds: Set<String> = emptySet()
    ): RoutingRequest = RoutingRequest(
        rawUserRequest = raw,
        canonicalCommand = parsed?.comando,
        freeArguments = parsed?.argumentos.orEmpty(),
        namedParameters = parsed?.parametros.orEmpty(),
        requiredCapabilities = required,
        preferredCapabilities = preferred,
        context = context,
        preferredAIIds = preferredAIIds
    )

    fun route(
        request: RoutingRequest,
        candidates: List<RoutingCandidate>,
        policy: RoutingPolicy = RoutingPolicy.default()
    ): RoutingDecision {
        if (candidates.isEmpty()) {
            return noProvider(request, "Nenhum provider cadastrado")
        }

        // Capacidades exigidas são um requisito duro: uma IA que não possui
        // todas elas nunca pode vencer apenas por qualidade, velocidade ou custo.
        val compatible = candidates.filter { candidate ->
            request.requiredCapabilities.all { it in candidate.capabilities }
        }
        if (compatible.isEmpty()) {
            val required = request.requiredCapabilities.joinToString(", ")
            return noProvider(
                request,
                if (required.isBlank()) "Nenhum provider compatível" else "Nenhum provider possui todas as capacidades exigidas: $required"
            )
        }

        val ranked = compatible.map { candidate ->
            val command = if (
                request.canonicalCommand != null &&
                request.canonicalCommand in candidate.supportedCommands
            ) 1.0 else 0.0

            val capability = if (request.requiredCapabilities.isEmpty()) {
                1.0
            } else {
                request.requiredCapabilities.count { it in candidate.capabilities }.toDouble() /
                    request.requiredCapabilities.size
            }

            val preferred = if (request.preferredCapabilities.isEmpty()) {
                0.0
            } else {
                request.preferredCapabilities.count {
                    it in candidate.capabilities || it in candidate.specialties
                }.toDouble() / request.preferredCapabilities.size
            }

            val preference = if (request.preferredAIIds.isEmpty()) {
                0.0
            } else if (candidate.iaId in request.preferredAIIds) {
                1.0
            } else {
                0.0
            }

            val contextScore = if (request.context.isNullOrBlank()) {
                0.5
            } else {
                candidate.contextQuality
            }

            val score = RoutingScore(
                commandCompatibility = command,
                capabilityCompatibility = capability,
                specialization = preferred,
                quality = candidate.quality.coerceIn(0.0, 1.0),
                speed = candidate.speed.coerceIn(0.0, 1.0),
                context = contextScore.coerceIn(0.0, 1.0),
                cost = candidate.cost.coerceAtLeast(0.0),
                preference = preference,
                reliability = candidate.reliability.coerceIn(0.0, 1.0)
            )
            candidate to score
        }
            .sortedWith(
                compareByDescending<Pair<RoutingCandidate, RoutingScore>> { it.second.total(policy) }
                    .thenBy { it.first.iaId }
            )
            .map { RoutingCandidateScore(it.first, it.second) }

        val top = ranked.first()
        val topTotal = top.score.total(policy)
        val secondTotal = ranked.getOrNull(1)?.score?.total(policy) ?: topTotal
        val confidence = if (ranked.size == 1) {
            1.0
        } else {
            ((topTotal - secondTotal) / (kotlin.math.abs(topTotal) + 1.0)).coerceIn(0.0, 1.0)
        }

        val reasons = buildList {
            if (top.score.commandCompatibility > 0) {
                add("suporta o comando ${request.canonicalCommand}")
            }
            if (top.score.capabilityCompatibility >= 1.0) {
                add("cumpre todas as capacidades exigidas")
            }
            if (top.score.specialization > 0) {
                add("possui especialização preferida")
            }
            if (top.score.preference > 0) {
                add("foi marcado como IA preferida para este contexto")
            }
            if (!request.context.isNullOrBlank() && top.score.context > 0.5) {
                add("possui boa adequação ao contexto do projeto")
            }
            if (top.score.reliability >= 0.8) {
                add("possui alta confiabilidade registrada")
            }
            add("ranking determinístico por política local")
        }

        return RoutingDecision(
            status = RoutingStatus.SELECTED,
            command = request.canonicalCommand,
            selectedAI = top.candidate,
            score = top.score,
            confidence = confidence,
            alternatives = ranked.drop(1).take(policy.alternatives.coerceAtLeast(0)),
            reasons = reasons
        )
    }

    private fun noProvider(request: RoutingRequest, reason: String): RoutingDecision =
        RoutingDecision(
            status = RoutingStatus.NO_COMPATIBLE_PROVIDER,
            command = request.canonicalCommand,
            selectedAI = null,
            score = null,
            confidence = 0.0,
            alternatives = emptyList(),
            reasons = listOf(reason)
        )
}
