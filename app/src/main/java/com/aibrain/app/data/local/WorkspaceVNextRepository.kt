package com.aibrain.app.data.local

import kotlinx.coroutines.flow.Flow

/** Repositório único para a camada persistente das evoluções do workspace. */
class WorkspaceVNextRepository(private val db: AppDatabase) {
    fun tarefas(projetoId: String): Flow<List<ProjetoTarefaEntity>> = db.projetoTarefaDao().observar(projetoId)
    fun todasTarefas(): Flow<List<ProjetoTarefaEntity>> = db.projetoTarefaDao().observarTodas()
    suspend fun salvarTarefa(item: ProjetoTarefaEntity) = db.projetoTarefaDao().salvar(item)
    suspend fun atualizarTarefa(item: ProjetoTarefaEntity) = db.projetoTarefaDao().atualizar(item)
    suspend fun removerTarefa(item: ProjetoTarefaEntity) = db.projetoTarefaDao().remover(item)

    fun memorias(projetoId: String): Flow<List<ProjetoMemoriaEntity>> = db.projetoMemoriaDao().observar(projetoId)
    suspend fun salvarMemoria(item: ProjetoMemoriaEntity) = db.projetoMemoriaDao().salvar(item)
    suspend fun removerMemoria(item: ProjetoMemoriaEntity) = db.projetoMemoriaDao().remover(item)

    fun skillsAtivas(projetoId: String): Flow<List<ProjetoSkillEntity>> = db.projetoSkillDao().observarAtivas(projetoId)
    suspend fun salvarSkill(item: ProjetoSkillEntity) = db.projetoSkillDao().salvar(item)
    suspend fun atualizarSkill(item: ProjetoSkillEntity) = db.projetoSkillDao().atualizar(item)

    fun contextosRecentes(): Flow<List<BrowserContextoEntity>> = db.browserContextoDao().observarRecentes()
    suspend fun salvarContexto(item: BrowserContextoEntity) = db.browserContextoDao().salvar(item)
    suspend fun removerContexto(item: BrowserContextoEntity) = db.browserContextoDao().remover(item)

    fun historicoPrompt(promptId: String): Flow<List<PromptAcaoHistoricoEntity>> = db.promptAcaoHistoricoDao().observar(promptId)
    suspend fun registrarAcao(item: PromptAcaoHistoricoEntity) = db.promptAcaoHistoricoDao().salvar(item)
}
