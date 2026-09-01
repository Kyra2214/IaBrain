package com.aibrain.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface IADao {
    @Query("SELECT * FROM ias WHERE ativo = 1 ORDER BY nome") fun observarAtivas(): Flow<List<IAEntity>>
    @Query("SELECT * FROM ias WHERE ativo = 1 ORDER BY nome") suspend fun listarAtivas(): List<IAEntity>
    @Query("SELECT * FROM ias WHERE id = :id LIMIT 1") suspend fun buscar(id: String): IAEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun salvarTodos(ias: List<IAEntity>)
    @Query("DELETE FROM ias") suspend fun limpar()
}

@Dao interface ProjetoDao {
    @Query("SELECT * FROM projetos ORDER BY atualizadoEm DESC") fun observarTodos(): Flow<List<ProjetoEntity>>
    @Query("SELECT * FROM projetos WHERE id = :id LIMIT 1") suspend fun buscar(id: String): ProjetoEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun salvar(projeto: ProjetoEntity)
    @Query("UPDATE projetos SET nome=:nome, descricao=:descricao, atualizadoEm=:atualizadoEm, status=:status WHERE id=:id") suspend fun atualizar(id: String, nome: String, descricao: String, atualizadoEm: Long, status: String)
}

@Dao interface ProjetoFuncaoDao {
    @Query("SELECT * FROM projeto_funcoes WHERE projetoId = :projetoId ORDER BY ordem") fun observarDoProjeto(projetoId: String): Flow<List<ProjetoFuncaoEntity>>
    @Query("SELECT * FROM projeto_funcoes WHERE id = :id LIMIT 1") suspend fun buscar(id: String): ProjetoFuncaoEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun salvarTodos(funcoes: List<ProjetoFuncaoEntity>)
}

@Dao interface ProjetoIADao {
    @Query("SELECT * FROM projeto_ias WHERE projetoId = :projetoId ORDER BY prioridade") fun observarDoProjeto(projetoId: String): Flow<List<ProjetoIAEntity>>
    @Query("SELECT * FROM projeto_ias WHERE projetoId=:projetoId AND funcaoId=:funcaoId ORDER BY prioridade") suspend fun buscarEscolhidas(projetoId: String, funcaoId: String): List<ProjetoIAEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun salvarTodos(vinculos: List<ProjetoIAEntity>)
}

@Dao interface PromptDao {
    @Query("SELECT * FROM prompts ORDER BY atualizadoEm DESC") fun observarTodos(): Flow<List<PromptEntity>>
    @Query("SELECT * FROM prompts WHERE id = :id LIMIT 1") suspend fun buscar(id: String): PromptEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun salvar(prompt: PromptEntity)
    @Query("UPDATE prompts SET favorito = :favorito, atualizadoEm = :atualizadoEm WHERE id = :id") suspend fun marcarFavorito(id: String, favorito: Boolean, atualizadoEm: Long)
}

@Dao interface ProjetoContextoDao {
    @Query("SELECT * FROM projeto_contextos WHERE projetoId = :projetoId LIMIT 1") suspend fun buscar(projetoId: String): ProjetoContextoEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun salvar(contexto: ProjetoContextoEntity)
}
