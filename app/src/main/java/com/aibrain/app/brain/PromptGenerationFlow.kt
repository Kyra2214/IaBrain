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
    val modeloGeracao: String = "LOCAL_DETERMINISTICO",
    val isDevelopmentPrompt: Boolean = false,
    val fase: String = "Não informada",
    val modulo: String = "Não informado",
    val submodulo: String = "Não informado",
    val pesoSubmodulo: String = "Não informado"
)

object DeveloperPromptStandard {
    const val HEADER = """MODO DE EXECUÇÃO SILENCIOSA
FASE → MÓDULO → SUBMÓDULO
- Executar sempre do menor peso para o maior peso
- Trabalhar em apenas 1 submódulo por vez
- Documentação obrigatória ao concluir cada submódulo
- Testes somente no fechamento da Fase
- Gerar ZIP completo do projeto
- Parar imediatamente ao concluir o escopo
- Economia de tokens
- Preservação da arquitetura existente
- Nenhuma invenção
- Nenhuma narração intermediária"""

    fun isDevelopment(command: String, objective: String): Boolean {
        val commands = setOf("/implement", "/code", "/debug", "/test", "/review", "/develop", "/build")
        val terms = listOf("implementar", "desenvolver", "criar aplicativo", "programar", "executar código", "corrigir código")
        return command.lowercase() in commands || terms.any { objective.lowercase().contains(it) }
    }
}

object PromptGenerationSpecBuilder {
    fun from(request: RoutingRequest, decision: RoutingDecision, funcaoId: String? = null): PromptGenerationSpec {
        val ia = requireNotNull(decision.selectedAI) { "Não é possível gerar prompt sem IA selecionada" }
        val comando = requireNotNull(decision.command ?: request.canonicalCommand) { "Não é possível gerar prompt sem comando" }
        return PromptGenerationSpec(request.rawUserRequest, ia.iaId, ia.nome, comando, request.requiredCapabilities, request.context, listOf("Usar a sintaxe ${comando}", "Atender às capacidades: ${request.requiredCapabilities.joinToString()}", "Produzir resposta específica para ${ia.nome}"), funcaoId, isDevelopmentPrompt = DeveloperPromptStandard.isDevelopment(comando, request.rawUserRequest))
    }
}

object ContextualPromptGenerator {
    fun generate(spec: PromptGenerationSpec): String = buildString {
        if (spec.isDevelopmentPrompt) {
            appendLine(DeveloperPromptStandard.HEADER)
            appendLine()
            appendLine("PROJETO")
            appendLine(spec.contexto ?: "Não informado")
            appendLine()
            appendLine("FASE")
            appendLine(spec.fase)
            appendLine()
            appendLine("MÓDULO")
            appendLine(spec.modulo)
            appendLine()
            appendLine("SUBMÓDULO")
            appendLine("${spec.submodulo} (peso: ${spec.pesoSubmodulo})")
            appendLine()
            appendLine("OBJETIVO")
        }
        appendLine("Você é a IA selecionada: ${spec.iaNome}.")
        appendLine("Comando operacional: ${spec.comando}.")
        appendLine("Objetivo da tarefa: ${spec.objetivo}.")
        if (spec.capacidades.isNotEmpty()) appendLine("Capacidades necessárias: ${spec.capacidades.joinToString()}.")
        if (!spec.contexto.isNullOrBlank()) appendLine("Contexto adicional: ${spec.contexto}.")
        appendLine("Requisitos:")
        spec.requisitos.forEach { appendLine("- $it") }
        if (spec.isDevelopmentPrompt) {
            appendLine("IMPLEMENTAÇÃO")
            appendLine("Executar somente o escopo descrito, preservando a arquitetura existente.")
            appendLine("CRITÉRIOS DE CONCLUSÃO")
            appendLine("Concluir apenas quando o objetivo e os requisitos forem atendidos.")
            appendLine("REGRA")
            appendLine("Aplicar o cabeçalho PADRÃO DE DESENVOLVEDOR somente a este prompt de desenvolvimento.")
        }
        appendLine("Responda com uma solução clara, verificável e adequada ao comando selecionado.")
    }.trim()
}

fun PromptGenerationSpec.toEntity(prompt: String): PromptEntity = PromptEntity(UUID.randomUUID().toString(), null, funcaoId, iaId, "Prompt ${comando}", prompt, modeloGeracao, "ROUTER_COMMAND:$comando", System.currentTimeMillis(), System.currentTimeMillis(), false)
