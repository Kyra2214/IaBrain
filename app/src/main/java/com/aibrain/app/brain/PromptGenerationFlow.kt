package com.aibrain.app.brain

import com.aibrain.app.data.local.PromptEntity
import java.util.UUID

data class PromptGenerationSpec(
    val objetivo: String,
    val iaId: String,
    val iaNome: String,
    val comando: String,
    val capacidades: Set<String>,
    val contexto: String? = null,
    val requisitos: List<String> = emptyList(),
    val funcaoId: String? = null,
    val modeloGeracao: String = "LOCAL_DETERMINISTICO"
)

object PromptGenerationSpecBuilder {
    fun from(request: RoutingRequest, decision: RoutingDecision, funcaoId: String? = null): PromptGenerationSpec {
        val ia = requireNotNull(decision.selectedAI) { "Não é possível gerar prompt sem IA selecionada" }
        val comando = requireNotNull(decision.command ?: request.canonicalCommand) { "Não é possível gerar prompt sem comando" }
        return PromptGenerationSpec(request.rawUserRequest, ia.iaId, ia.nome, comando, request.requiredCapabilities, request.context, listOf("Usar a sintaxe ${comando}", "Atender às capacidades: ${request.requiredCapabilities.joinToString()}", "Produzir resposta específica para ${ia.nome}"), funcaoId)
    }
}

object ContextualPromptGenerator {
    fun generate(spec: PromptGenerationSpec): String = buildString {
        appendLine("Você é a IA selecionada: ${spec.iaNome}.")
        appendLine("Comando operacional: ${spec.comando}.")
        appendLine("Objetivo da tarefa: ${spec.objetivo}.")
        if (spec.capacidades.isNotEmpty()) appendLine("Capacidades necessárias: ${spec.capacidades.joinToString()}.")
        if (!spec.contexto.isNullOrBlank()) appendLine("Contexto adicional: ${spec.contexto}.")
        appendLine("Requisitos:")
        spec.requisitos.forEach { appendLine("- $it") }
        appendLine("Responda com uma solução clara, verificável e adequada ao comando selecionado.")
    }.trim()
}

fun PromptGenerationSpec.toEntity(prompt: String): PromptEntity = PromptEntity(UUID.randomUUID().toString(), null, funcaoId, iaId, "Prompt ${comando}", prompt, modeloGeracao, "ROUTER_COMMAND:$comando", System.currentTimeMillis(), System.currentTimeMillis(), false)
