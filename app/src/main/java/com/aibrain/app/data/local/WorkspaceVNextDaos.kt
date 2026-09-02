package com.aibrain.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjetoTarefaDao {
    @Query("SELECT * FROM projeto_tarefas WHERE projetoId = :projetoId OR projetoId IS NULL ORDER BY prioridade DESC, atualizadoEm DESC")
    fun observar(projetoId: String): Flow<List<ProjetoTarefaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(item: ProjetoTarefaEntity)

    @Update
    suspend fun atualizar(item: ProjetoTarefaEntity)

    @Delete
    suspend fun remover(item: ProjetoTarefaEntity)
}

@Dao
interface ProjetoMemoriaDao {
    @Query("SELECT * FROM projeto_memorias WHERE projetoId = :projetoId ORDER BY criadoEm DESC")
    fun observar(projetoId: String): Flow<List<ProjetoMemoriaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(item: ProjetoMemoriaEntity)

    @Delete
    suspend fun remover(item: ProjetoMemoriaEntity)
}

@Dao
interface ProjetoSkillDao {
    @Query("SELECT * FROM projeto_skills WHERE (projetoId = :projetoId OR projetoId IS NULL) AND ativo = 1 ORDER BY nome")
    fun observarAtivas(projetoId: String): Flow<List<ProjetoSkillEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(item: ProjetoSkillEntity)

    @Update
    suspend fun atualizar(item: ProjetoSkillEntity)
}

@Dao
interface BrowserContextoDao {
    @Query("SELECT * FROM browser_contextos ORDER BY criadoEm DESC LIMIT 20")
    fun observarRecentes(): Flow<List<BrowserContextoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(item: BrowserContextoEntity)

    @Delete
    suspend fun remover(item: BrowserContextoEntity)
}

@Dao
interface PromptAcaoHistoricoDao {
    @Query("SELECT * FROM prompt_acoes_historico WHERE promptId = :promptId ORDER BY criadoEm DESC")
    fun observar(promptId: String): Flow<List<PromptAcaoHistoricoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(item: PromptAcaoHistoricoEntity)
}
