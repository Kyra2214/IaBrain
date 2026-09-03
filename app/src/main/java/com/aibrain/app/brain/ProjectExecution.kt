package com.aibrain.app.brain

import com.aibrain.app.data.local.IARepository
import com.aibrain.app.data.local.ProjetoEntity
import com.aibrain.app.data.local.ProjetoExecucaoEntity
import com.aibrain.app.data.local.ProjetoExecucaoRepository
import com.aibrain.app.data.local.ProjetoFuncaoEntity
import com.aibrain.app.data.local.ProjetoIARepository
import com.aibrain.app.model.Categoria
import java.util.UUID

enum class ProjectExecutionStatus { BLOCKED, READY, RUNNING, WAITING_USER, COMPLETED, FAILED, SKIPPED }

data class ProjectExecutionState(val projetoId: String, val funcaoId: String, val status: ProjectExecutionStatus, val iaId: String? = null, val prompt: String? = null, val executionId: String? = null, val confidence: Double = 0.0, val reason: String = "")
data class ProjectExecutionPlan(val state: ProjectExecutionState, val decision: RoutingDecision? = null)

/** Orquestra apenas preparação local. A execução da IA externa continua sob controle do usuário. */
class ProjectExecutionEngine(
    private val iaRepository: IARepository,
    private val projetoIARepository: ProjetoIARepository,
    private val executionRepository: ProjetoExecucaoRepository
) {
    suspend fun prepare(projeto: ProjetoEntity, funcao: ProjetoFuncaoEntity): ProjectExecutionPlan {
        if (!executionRepository.podeExecutar(projeto.id, funcao.id)) {
            return ProjectExecutionPlan(ProjectExecutionState(projeto.id, funcao.id, ProjectExecutionStatus.BLOCKED, reason = "Existem funções dependentes ainda não concluídas."))
        }
        val categoria = Categoria.entries.firstOrNull { it.rotulo.equals(funcao.funcao, true) || it.chave.equals(funcao.funcao, true) }
        val capability = categoria?.chave ?: funcao.funcao.lowercase()
        val ias = iaRepository.listarAtivas()
        val vinculadasIds = projetoIARepository.escolhidas(projeto.id, funcao.id).filter { it.selecionada }.map { it.iaId }.toSet()
        val candidates = ias.map { ia ->
            RoutingCandidate(
                iaId = ia.id,
                nome = ia.nome,
                capabilities = (ia.categorias + ia.notas.keys).map(String::lowercase).toSet(),
                specialties = buildSet { if (ia.id in vinculadasIds) add("projeto-selecionado") },
                quality = ((ia.notas[capability] ?: 5) / 10.0).coerceIn(0.0, 1.0),
                supportsCode = ia.categorias.any { it.equals("codigo", true) },
                supportsFiles = ia.categorias.any { it.equals("codigo", true) || it.equals("escrita", true) },
                supportsImages = ia.categorias.any { it.equals("imagem", true) || it.equals("design", true) },
                supportsWeb = ia.plataformas.any { it.equals("web", true) }
            )
        }
        val request = RoutingRequest(
            rawUserRequest = "Desenvolver a função '${funcao.funcao}' do projeto '${projeto.nome}': ${funcao.descricao.ifBlank { projeto.descricao }}",
            canonicalCommand = "/develop",
            requiredCapabilities = setOf(capability),
            preferredCapabilities = setOf("projeto-selecionado"),
            context = "Projeto: ${projeto.nome}; plataforma: ${projeto.plataforma ?: "não informada"}; complexidade: ${projeto.complexidade}"
        )
        val decision = LocalAIRouter.route(request, candidates)
        val selected = decision.selectedAI ?: return ProjectExecutionPlan(ProjectExecutionState(projeto.id, funcao.id, ProjectExecutionStatus.FAILED, reason = "Nenhuma IA disponível para a função."), decision)
        val spec = PromptGenerationSpecBuilder.from(request, decision, funcao.id)
        val prompt = ContextualPromptGenerator.generate(spec)
        val id = UUID.randomUUID().toString()
        executionRepository.preparar(ProjetoExecucaoEntity(id, projeto.id, funcao.id, selected.iaId, prompt, ProjectExecutionStatus.WAITING_USER.name, null, null, decision.confidence, System.currentTimeMillis(), System.currentTimeMillis(), null))
        return ProjectExecutionPlan(ProjectExecutionState(projeto.id, funcao.id, ProjectExecutionStatus.WAITING_USER, selected.iaId, prompt, id, decision.confidence, "Prompt preparado; usuário deve abrir a IA e iniciar a execução."), decision)
    }
}
