package com.aibrain.app.brain

import com.aibrain.app.data.local.IARepository
import com.aibrain.app.data.local.PromptEntity
import com.aibrain.app.data.local.PromptRoomRepository
import com.aibrain.app.data.local.ProjetoEntity
import com.aibrain.app.data.local.ProjetoExecucaoEntity
import com.aibrain.app.data.local.ProjetoExecucaoRepository
import com.aibrain.app.data.local.ProjetoFuncaoEntity
import com.aibrain.app.data.local.ProjetoIARepository
import com.aibrain.app.model.Categoria
import java.util.UUID

enum class ProjectExecutionStatus { BLOCKED, READY, RUNNING, WAITING_USER, COMPLETED, FAILED, SKIPPED, CANCELLED }
data class ProjectExecutionState(
    val projetoId: String,
    val funcaoId: String,
    val status: ProjectExecutionStatus,
    val iaId: String? = null,
    val prompt: String? = null,
    val executionId: String? = null,
    val confidence: Double = 0.0,
    val reason: String = ""
)
data class ProjectExecutionPlan(val state: ProjectExecutionState, val decision: RoutingDecision? = null)

/** Orquestra a preparação local: dependência → roteamento → prompt → registro → navegador. */
class ProjectExecutionEngine(
    private val iaRepository: IARepository,
    private val projetoIARepository: ProjetoIARepository,
    private val executionRepository: ProjetoExecucaoRepository,
    private val promptRepository: PromptRoomRepository
) {
    suspend fun prepare(projeto: ProjetoEntity, funcao: ProjetoFuncaoEntity): ProjectExecutionPlan {
        if (!executionRepository.podeExecutar(projeto.id, funcao.id)) {
            return ProjectExecutionPlan(
                ProjectExecutionState(
                    projeto.id,
                    funcao.id,
                    ProjectExecutionStatus.BLOCKED,
                    reason = "Existem funções dependentes ainda não concluídas."
                )
            )
        }

        val categoria = Categoria.entries.firstOrNull {
            it.rotulo.equals(funcao.funcao, true) || it.chave.equals(funcao.funcao, true)
        }
        val capability = categoria?.chave ?: funcao.funcao.lowercase()
        val ias = iaRepository.listarAtivas()
        val vinculadasIds = projetoIARepository.escolhidas(projeto.id, funcao.id)
            .filter { it.selecionada }
            .map { it.iaId }
            .toSet()

        val candidates = ias.map { ia ->
            RoutingCandidate(
                iaId = ia.id,
                nome = ia.nome,
                capabilities = (ia.categorias + ia.notas.keys).map(String::lowercase).toSet(),
                specialties = buildSet {
                    if (ia.id in vinculadasIds) add("projeto-selecionado")
                },
                quality = ((ia.notas[capability] ?: 5) / 10.0).coerceIn(0.0, 1.0),
                supportsCode = ia.categorias.any { it.equals("codigo", true) },
                supportsFiles = ia.categorias.any {
                    it.equals("codigo", true) || it.equals("escrita", true)
                },
                supportsImages = ia.categorias.any {
                    it.equals("imagem", true) || it.equals("design", true)
                },
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
        val selected = decision.selectedAI
            ?: return ProjectExecutionPlan(
                ProjectExecutionState(
                    projeto.id,
                    funcao.id,
                    ProjectExecutionStatus.FAILED,
                    reason = "Nenhuma IA disponível para a função."
                ),
                decision
            )

        val spec = PromptGenerationSpecBuilder.from(request, decision, funcao.id)
        val generatedPrompt = ContextualPromptGenerator.generate(spec)
        val agora = System.currentTimeMillis()
        val promptId = UUID.randomUUID().toString()
        val executionId = UUID.randomUUID().toString()

        promptRepository.salvar(
            PromptEntity(
                id = promptId,
                projetoId = projeto.id,
                funcaoId = funcao.id,
                iaId = selected.iaId,
                titulo = "Execução: ${funcao.funcao}",
                prompt = generatedPrompt,
                modeloGeracao = spec.modeloGeracao,
                origem = "PROJECT_EXECUTION",
                criadoEm = agora,
                atualizadoEm = agora,
                favorito = false
            )
        )

        executionRepository.preparar(
            ProjetoExecucaoEntity(
                id = executionId,
                projetoId = projeto.id,
                funcaoId = funcao.id,
                iaId = selected.iaId,
                promptId = promptId,
                status = ProjectExecutionStatus.WAITING_USER.name,
                promptSnapshot = generatedPrompt,
                resultado = null,
                erro = null,
                iniciadoEm = null,
                finalizadoEm = null,
                criadoEm = agora,
                atualizadoEm = agora
            )
        )

        return ProjectExecutionPlan(
            ProjectExecutionState(
                projeto.id,
                funcao.id,
                ProjectExecutionStatus.WAITING_USER,
                selected.iaId,
                generatedPrompt,
                executionId,
                decision.confidence,
                "Prompt preparado; usuário deve abrir a IA e iniciar a execução."
            ),
            decision
        )
    }
}
