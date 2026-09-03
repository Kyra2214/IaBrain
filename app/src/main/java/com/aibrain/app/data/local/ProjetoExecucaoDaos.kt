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

    @Query("SELECT * FROM projeto_execucoes WHERE projetoId=:projetoId ORDER BY criadoEm DESC")
    suspend fun listarDoProjeto(projetoId: String): List<ProjetoExecucaoEntity>

    @Query("SELECT * FROM projeto_execucoes WHERE projetoId=:projetoId AND funcaoId=:funcaoId ORDER BY criadoEm DESC LIMIT 1")
    suspend fun ultimaDaFuncao(projetoId: String, funcaoId: String): ProjetoExecucaoEntity?

    @Query("SELECT * FROM projeto_execucoes WHERE id=:id LIMIT 1")
    suspend fun buscar(id: String): ProjetoExecucaoEntity?

    @Query("UPDATE projeto_execucoes SET status=:status, iniciadoEm=:iniciadoEm, atualizadoEm=:iniciadoEm WHERE id=:id AND status=:statusEsperado")
    suspend fun iniciar(id: String, status: String, iniciadoEm: Long, statusEsperado: String): Int

    @Query("UPDATE projeto_execucoes SET status=:status, resultado=:resultado, erro=:erro, finalizadoEm=:finalizadoEm, atualizadoEm=:finalizadoEm WHERE id=:id AND status=:statusEsperado")
    suspend fun concluir(id: String, status: String, resultado: String?, erro: String?, finalizadoEm: Long?, statusEsperado: String): Int

    @Query("UPDATE projeto_execucoes SET status=:status, erro=:motivo, finalizadoEm=:agora, atualizadoEm=:agora WHERE id=:id AND status IN ('WAITING_USER', 'RUNNING')")
    suspend fun cancelar(id: String, status: String, motivo: String, agora: Long): Int
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
