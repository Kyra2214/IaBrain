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
    private val projetoDao = db.projetoDao()

    fun observar(projetoId: String): Flow<List<ProjetoExecucaoEntity>> = execucaoDao.observarDoProjeto(projetoId)

    suspend fun listar(projetoId: String): List<ProjetoExecucaoEntity> = execucaoDao.listarDoProjeto(projetoId)

    suspend fun ultimaDaFuncao(projetoId: String, funcaoId: String): ProjetoExecucaoEntity? =
        execucaoDao.ultimaDaFuncao(projetoId, funcaoId)

    suspend fun registrarDependencias(projetoId: String, dependencias: List<Pair<String, String>>) {
        require(dependencias.none { it.first == it.second }) { "Uma função não pode depender de si mesma" }
        dependenciaDao.salvarTodos(dependencias.map { (funcao, dependeDe) -> ProjetoFuncaoDependenciaEntity(projetoId, funcao, dependeDe) })
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

    suspend fun iniciar(id: String): Boolean {
        val execucao = execucaoDao.buscar(id) ?: return false
        if (execucao.status != ProjectExecutionStatus.WAITING_USER.name) return false
        val agora = System.currentTimeMillis()
        val alteradas = execucaoDao.iniciar(id, ProjectExecutionStatus.RUNNING.name, agora, ProjectExecutionStatus.WAITING_USER.name)
        if (alteradas == 0) return false
        funcaoDao.marcarStatus(execucao.funcaoId, "EM_EXECUCAO")
        historicoDao.registrar(ProjetoHistoricoEntity(UUID.randomUUID().toString(), execucao.projetoId, "EXECUCAO_INICIADA", "Função ${execucao.funcaoId} iniciada pelo usuário", agora))
        return true
    }

    suspend fun concluir(id: String, resultado: String?, erro: String? = null): Boolean {
        val execucao = execucaoDao.buscar(id) ?: return false
        val status = if (erro.isNullOrBlank()) ProjectExecutionStatus.COMPLETED.name else ProjectExecutionStatus.FAILED.name
        val agora = System.currentTimeMillis()
        val alteradas = execucaoDao.concluir(id, status, resultado, erro, agora, ProjectExecutionStatus.RUNNING.name)
        if (alteradas == 0) return false

        if (status == ProjectExecutionStatus.COMPLETED.name) {
            funcaoDao.marcarStatus(execucao.funcaoId, "CONCLUIDA")
            historicoDao.registrar(ProjetoHistoricoEntity(UUID.randomUUID().toString(), execucao.projetoId, "EXECUCAO_CONCLUIDA", "Função ${execucao.funcaoId} concluída", agora))
        } else {
            funcaoDao.marcarStatus(execucao.funcaoId, "ERRO")
            historicoDao.registrar(ProjetoHistoricoEntity(UUID.randomUUID().toString(), execucao.projetoId, "EXECUCAO_FALHOU", erro.orEmpty(), agora))
        }
        return true
    }

    suspend fun cancelar(id: String, motivo: String = "Execução cancelada pelo usuário"): Boolean {
        val execucao = execucaoDao.buscar(id) ?: return false
        val agora = System.currentTimeMillis()
        val alteradas = execucaoDao.cancelar(id, ProjectExecutionStatus.CANCELLED.name, motivo, agora)
        if (alteradas == 0) return false
        funcaoDao.marcarStatus(execucao.funcaoId, "CANCELADA")
        historicoDao.registrar(ProjetoHistoricoEntity(UUID.randomUUID().toString(), execucao.projetoId, "EXECUCAO_CANCELADA", "Função ${execucao.funcaoId}: $motivo", agora))
        return true
    }

    suspend fun recuperarExecucoesInterrompidas(): Int {
        val agora = System.currentTimeMillis()
        val alteradas = execucaoDao.recuperarEmExecucao("Execução retomável após reinício do aplicativo", agora)
        if (alteradas > 0) {
            historicoDao.registrar(ProjetoHistoricoEntity(UUID.randomUUID().toString(), "SISTEMA", "EXECUCOES_RECUPERADAS", "$alteradas execução(ões) retornaram para WAITING_USER", agora))
        }
        return alteradas
    }

    suspend fun marcarProjetoStatus(projetoId: String, status: String) {
        projetoDao.marcarStatus(projetoId, status, System.currentTimeMillis())
    }
}
