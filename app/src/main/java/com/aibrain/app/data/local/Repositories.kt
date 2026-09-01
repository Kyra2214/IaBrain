package com.aibrain.app.data.local

import android.content.Context
import com.aibrain.app.brain.Complexidade
import com.aibrain.app.brain.ProjetoIntent
import com.aibrain.app.brain.ProjetoRecommendation
import com.aibrain.app.model.IA
import com.aibrain.app.model.NivelAcesso
import kotlinx.coroutines.flow.Flow
import java.util.UUID

fun IA.toEntity(): IAEntity = IAEntity(id, nome, descricao, site, logo, idiomas, categoriaPrincipal, categorias, notas, plataformas, modeloAcesso, possuiApi, requerLogin, status, ultimaVerificacao, casosDeUso, gratuita, acesso.chave)
fun IAEntity.toDomain(): IA = IA(id, nome, logo, url, descricao, categorias, idiomas, gratuita, NivelAcesso.porChave(acesso) ?: if (gratuita) NivelAcesso.GRATUITA else NivelAcesso.PAGA, notas, categoria, plataformas, modeloAcesso, possuiApi, requerLogin, ultimaVerificacao, status, casosDeUso)

class IARepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).iaDao()
    fun observarAtivas(): Flow<List<IAEntity>> = dao.observarAtivas()
    suspend fun listarAtivas(): List<IA> = dao.listarAtivas().map { it.toDomain() }
    suspend fun buscar(id: String): IA? = dao.buscar(id)?.toDomain()
    suspend fun importar(ias: List<IA>) { dao.salvarTodos(ias.map { it.toEntity() }) }
}

class ProjetoRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).projetoDao()
    fun observarTodos(): Flow<List<ProjetoEntity>> = dao.observarTodos()
    suspend fun buscar(id: String): ProjetoEntity? = dao.buscar(id)
    suspend fun salvar(intent: ProjetoIntent, recommendation: ProjetoRecommendation, nome: String = intent.tipoProjeto ?: "Novo projeto"): String {
        val agora = System.currentTimeMillis(); val id = UUID.randomUUID().toString()
        dao.salvar(ProjetoEntity(id, nome, intent.textoOriginal, intent.plataforma, intent.complexidade.name, intent.acessoPreferido?.chave, agora, agora, "ATIVO"))
        return id
    }
    suspend fun atualizar(id: String, nome: String, descricao: String, status: String = "ATIVO") = dao.atualizar(id, nome, descricao, System.currentTimeMillis(), status)
}

class ProjetoFuncaoRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).projetoFuncaoDao()
    fun observar(projetoId: String): Flow<List<ProjetoFuncaoEntity>> = dao.observarDoProjeto(projetoId)
    suspend fun salvar(projetoId: String, funcoes: List<ProjetoFuncaoEntity>) = dao.salvarTodos(funcoes.mapIndexed { i, f -> f.copy(projetoId = projetoId, ordem = i) })
}

class ProjetoIARepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).projetoIADao()
    fun observar(projetoId: String): Flow<List<ProjetoIAEntity>> = dao.observarDoProjeto(projetoId)
    suspend fun salvar(vinculos: List<ProjetoIAEntity>) = dao.salvarTodos(vinculos)
    suspend fun escolhidas(projetoId: String, funcaoId: String) = dao.buscarEscolhidas(projetoId, funcaoId)
}

class PromptRoomRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).promptDao()
    fun observarTodos(): Flow<List<PromptEntity>> = dao.observarTodos()
    suspend fun buscar(id: String) = dao.buscar(id)
    suspend fun salvar(prompt: PromptEntity) = dao.salvar(prompt)
    suspend fun favoritar(id: String, favorito: Boolean) = dao.marcarFavorito(id, favorito, System.currentTimeMillis())
}
