package com.aibrain.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjetoExecucaoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(execucao: ProjetoExecucaoEntity)

    @Query("SELECT * FROM projeto_execucoes WHERE projetoId=:projetoId ORDER BY criadoEm DESC")
    fun observarDoProjeto(projetoId: String): Flow<List<ProjetoExecucaoEntity>>

    @Query("SELECT * FROM projeto_execucoes WHERE id=:id LIMIT 1")
    suspend fun buscar(id: String): ProjetoExecucaoEntity?

    @Query("UPDATE projeto_execucoes SET status=:status, resultado=:resultado, erro=:erro, concluidoEm=:concluidoEm WHERE id=:id")
    suspend fun concluir(id: String, status: String, resultado: String?, erro: String?, concluidoEm: Long?)
}

@Dao
interface ProjetoFuncaoDependenciaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarTodos(itens: List<ProjetoFuncaoDependenciaEntity>)

    @Query("SELECT * FROM projeto_funcao_dependencias WHERE projetoId=:projetoId AND funcaoId=:funcaoId")
    suspend fun dependencias(projetoId: String, funcaoId: String): List<ProjetoFuncaoDependenciaEntity>

    @Query("SELECT * FROM projeto_funcao_dependencias WHERE projetoId=:projetoId")
    suspend fun todas(projetoId: String): List<ProjetoFuncaoDependenciaEntity>
}
