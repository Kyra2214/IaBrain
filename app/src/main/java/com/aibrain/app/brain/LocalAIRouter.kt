package com.aibrain.app.brain

import com.aibrain.app.command.ParsedSlashCommand
import com.aibrain.app.command.SlashCommandParser

 data class RoutingRequest(val rawUserRequest: String, val canonicalCommand: String?, val freeArguments: String = "", val namedParameters: Map<String,String> = emptyMap(), val requiredCapabilities: Set<String> = emptySet(), val preferredCapabilities: Set<String> = emptySet(), val context: String? = null)
 data class RoutingCandidate(val iaId: String, val nome: String, val capabilities: Set<String> = emptySet(), val supportedCommands: Set<String> = emptySet(), val specialties: Set<String> = emptySet(), val quality: Double = 0.5, val speed: Double = 0.5, val cost: Double = 0.0, val supportsCode: Boolean = false, val supportsFiles: Boolean = false, val supportsImages: Boolean = false, val supportsWeb: Boolean = false, val supportsReasoning: Boolean = false, val reliability: Double = 0.5, val contextQuality: Double = 0.5, val isDefaultProfile: Boolean = true)
 data class RoutingScore(val commandCompatibility: Double, val capabilityCompatibility: Double, val specialization: Double, val quality: Double, val speed: Double, val context: Double, val cost: Double) {
    fun total(policy: RoutingPolicy) = commandCompatibility*policy.commandWeight + capabilityCompatibility*policy.capabilityWeight + specialization*policy.specializationWeight + quality*policy.qualityWeight + speed*policy.speedWeight + context*policy.contextWeight - cost*policy.costWeight
 }
 data class RoutingPolicy(val commandWeight: Double=4.0, val capabilityWeight: Double=4.0, val specializationWeight: Double=2.0, val qualityWeight: Double=1.5, val speedWeight: Double=0.5, val contextWeight: Double=0.5, val costWeight: Double=1.0, val alternatives: Int=3) {
    companion object { fun default() = RoutingPolicy() }
 }
 data class RoutingCandidateScore(val candidate: RoutingCandidate, val score: RoutingScore)
 enum class RoutingStatus { SELECTED, NO_COMPATIBLE_PROVIDER }
 data class RoutingDecision(val status: RoutingStatus, val command: String?, val selectedAI: RoutingCandidate?, val score: RoutingScore?, val confidence: Double, val alternatives: List<RoutingCandidateScore>, val reasons: List<String>)

object LocalAIRouter {
    fun request(raw: String, parsed: ParsedSlashCommand? = SlashCommandParser.parse(raw), required: Set<String> = emptySet(), preferred: Set<String> = emptySet()): RoutingRequest = RoutingRequest(raw, parsed?.comando, parsed?.argumentos.orEmpty(), parsed?.parametros.orEmpty(), required, preferred)
    fun route(request: RoutingRequest, candidates: List<RoutingCandidate>, policy: RoutingPolicy = RoutingPolicy.default()): RoutingDecision {
        if (candidates.isEmpty()) return RoutingDecision(RoutingStatus.NO_COMPATIBLE_PROVIDER, request.canonicalCommand, null, null, 0.0, emptyList(), listOf("Nenhum provider cadastrado"))
        val ranked = candidates.map { candidate ->
            val command = if (request.canonicalCommand != null && request.canonicalCommand in candidate.supportedCommands) 1.0 else 0.0
            val required = if (request.requiredCapabilities.isEmpty()) 1.0 else request.requiredCapabilities.count { it in candidate.capabilities }.toDouble()/request.requiredCapabilities.size
            val preferred = if (request.preferredCapabilities.isEmpty()) 0.0 else request.preferredCapabilities.count { it in candidate.capabilities || it in candidate.specialties }.toDouble()/request.preferredCapabilities.size
            val score = RoutingScore(command, required, preferred, candidate.quality, candidate.speed, if (request.context.isNullOrBlank()) 0.5 else candidate.contextQuality, candidate.cost)
            candidate to score
        }.sortedByDescending { it.second.total(policy) }.map { RoutingCandidateScore(it.first,it.second) }
        val top = ranked.first(); val second = ranked.getOrNull(1)?.score?.total(policy) ?: top.score.total(policy)
        val confidence = ((top.score.total(policy)-second)/(kotlin.math.abs(top.score.total(policy))+1.0)).coerceIn(0.0,1.0)
        val reasons = buildList { if (top.score.commandCompatibility > 0) add("suporta o comando ${request.canonicalCommand}"); if (top.score.capabilityCompatibility > 0) add("possui capacidades exigidas"); if (top.score.specialization > 0) add("possui especialização preferida"); add("ranking determinístico por política local") }
        return RoutingDecision(RoutingStatus.SELECTED, request.canonicalCommand, top.candidate, top.score, confidence, ranked.drop(1).take(policy.alternatives), reasons)
    }
}
