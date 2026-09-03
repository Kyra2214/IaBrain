package com.aibrain.app.data.local

import com.aibrain.app.brain.ProjectExecutionStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ProjetoExecucaoRepository(context: android.content.Context) {
    private val db = AppDatabase.getInstance(context)
    private val execucaoDao = db.projetoExecucaoDao()
    private val dependenciaDao = db.projetoFuncaoDependenciaDao()
    private val funcaoDao = db.projetoFuncaoDao()
    private val historicoDao = db.projetoHistoricoDao()

    fun observar(projetoId: String): Flow<List<ProjetoExecucaoEntity>> = execucaoDao.observarDoProjeto(projetoId)

    suspend fun registrarDependencias(projetoId: String, dependencias: List<Pair<String, String>>) {
        require(dependencias.none { it.first == it.second }) { "Uma função não pode depender de si mesma" }
        val itens = dependencias.map { (funcao, dependeDe) -> ProjetoFuncaoDependenciaEntity(projetoId, funcao, dependeDe) }
        dependenciaDao.salvarTodos(itens)
    }

    suspend fun dependencias(projetoId: String, funcaoId: String): List<ProjetoFuncaoDependenciaEntity> = dependenciaDao.dependencias(projetoId, funcaoId)

    suspend fun podeExecutar(projetoId: String, funcaoId: String): Boolean {
        val deps = dependenciaDao.dependencias(projetoId, funcaoId)
        if (deps.isEmpty()) return true
        val funcoes = deps.mapNotNull { funcaoDao.buscar(it.dependeDeFuncaoId) }
        return funcoes.size == deps.size && funcoes.all { it.status.equals("CONCLUIDA", true) }
    }

    suspend fun preparar(execucao: ProjetoExecucaoEntity) {
        execucaoDao.salvar(execucao)
        historicoDao.registrar(ProjetoHistoricoEntity(UUID.randomUUID().toString(), execucao.projetoId, "EXECUCAO_PREPARADA", "Função ${execucao.funcaoId}; IA ${execucao.iaId ?: "não selecionada"}", System.currentTimeMillis()))
    }

    suspend fun concluir(id: String, resultado: String?, erro: String? = null) {
        val status = if (erro.isNullOrBlank()) ProjectExecutionStatus.COMPLETED.name else ProjectExecutionStatus.FAILED.name
        execucaoDao.concluir(id, status, resultado, erro, System.currentTimeMillis())
        execucaoDao.buscar(id)?.let { execucao ->
            if (status == ProjectExecutionStatus.COMPLETED.name) {
                funcaoDao.marcarStatus(execucao.funcaoId, "CONCLUIDA")
                historicoDao.registrar(ProjetoHistoricoEntity(UUID.randomUUID().toString(), execucao.projetoId, "EXECUCAO_CONCLUIDA", "Função ${execucao.funcaoId} concluída", System.currentTimeMillis()))
            } else {
                funcaoDao.marcarStatus(execucao.funcaoId, "ERRO")
                historicoDao.registrar(ProjetoHistoricoEntity(UUID.randomUUID().toString(), execucao.projetoId, "EXECUCAO_FALHOU", erro.orEmpty(), System.currentTimeMillis()))
            }
        }
    }
}
