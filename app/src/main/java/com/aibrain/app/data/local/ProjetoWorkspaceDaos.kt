package com.aibrain.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjetoWorkspaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun salvarContribuicao(item: ProjetoContribuicaoEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun salvarArquivos(itens: List<ProjetoArquivoWorkspaceEntity>)
    @Query("SELECT * FROM projeto_contribuicoes WHERE projetoId=:projetoId ORDER BY recebidoEm DESC") fun contribuicoes(projetoId: String): Flow<List<ProjetoContribuicaoEntity>>
    @Query("SELECT * FROM projeto_arquivos_workspace WHERE projetoId=:projetoId ORDER BY caminho") suspend fun arquivos(projetoId: String): List<ProjetoArquivoWorkspaceEntity>
    @Query("SELECT * FROM projeto_arquivos_workspace WHERE contribuicaoId=:contribuicaoId ORDER BY caminho") suspend fun arquivosDaContribuicao(contribuicaoId: String): List<ProjetoArquivoWorkspaceEntity>
}

@Dao
interface ProjetoIntegracaoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun salvar(item: ProjetoIntegracaoEntity)
    @Query("SELECT * FROM projeto_integracoes WHERE projetoId=:projetoId ORDER BY numero DESC") fun observar(projetoId: String): Flow<List<ProjetoIntegracaoEntity>>
    @Query("SELECT COALESCE(MAX(numero), 0) FROM projeto_integracoes WHERE projetoId=:projetoId") suspend fun maiorNumero(projetoId: String): Int
}

@Dao
interface ProjetoValidacaoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun salvarTodos(itens: List<ProjetoValidacaoEntity>)
    @Query("SELECT * FROM projeto_validacoes WHERE projetoId=:projetoId ORDER BY executadoEm DESC") fun observar(projetoId: String): Flow<List<ProjetoValidacaoEntity>>
}

@Dao
interface ProjetoHistoricoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun registrar(item: ProjetoHistoricoEntity)
    @Query("SELECT * FROM projeto_historico WHERE projetoId=:projetoId ORDER BY criadoEm DESC") fun observar(projetoId: String): Flow<List<ProjetoHistoricoEntity>>
}

@Dao
interface ProjetoCiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun salvar(item: ProjetoCiProfileEntity)
    @Query("SELECT * FROM projeto_ci_profiles WHERE projetoId=:projetoId LIMIT 1") suspend fun buscar(projetoId: String): ProjetoCiProfileEntity?
}

@Dao
interface ProjetoGithubDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun salvar(item: ProjetoGithubEntity)
    @Query("SELECT * FROM projeto_github WHERE projetoId=:projetoId LIMIT 1") suspend fun buscar(projetoId: String): ProjetoGithubEntity?
}
