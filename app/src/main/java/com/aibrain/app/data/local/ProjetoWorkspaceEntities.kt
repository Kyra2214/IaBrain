package com.aibrain.app.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "projeto_contribuicoes", indices = [Index("projetoId"), Index("recebidoEm")])
data class ProjetoContribuicaoEntity(
    @androidx.room.PrimaryKey val id: String,
    val projetoId: String,
    val fonte: String,
    val nomeFonte: String,
    val recebidoEm: Long,
    val status: String
)

@Entity(tableName = "projeto_arquivos_workspace", primaryKeys = ["contribuicaoId", "caminho"], indices = [Index("projetoId"), Index("caminho")])
data class ProjetoArquivoWorkspaceEntity(
    val projetoId: String,
    val contribuicaoId: String,
    val caminho: String,
    val hash: String,
    val tamanho: Long,
    val origem: String
)

@Entity(tableName = "projeto_integracoes", indices = [Index("projetoId"), Index("criadoEm")])
data class ProjetoIntegracaoEntity(
    @androidx.room.PrimaryKey val id: String,
    val projetoId: String,
    val numero: Int,
    val fontes: List<String>,
    val status: String,
    val conflitos: List<String>,
    val criadoEm: Long,
    val concluidoEm: Long?
)

@Entity(tableName = "projeto_validacoes", indices = [Index("projetoId"), Index("executadoEm")])
data class ProjetoValidacaoEntity(
    @androidx.room.PrimaryKey val id: String,
    val projetoId: String,
    val nome: String,
    val nivel: String,
    val status: String,
    val detalhes: String,
    val executadoEm: Long
)

@Entity(tableName = "projeto_historico", indices = [Index("projetoId"), Index("criadoEm")])
data class ProjetoHistoricoEntity(
    @androidx.room.PrimaryKey val id: String,
    val projetoId: String,
    val tipo: String,
    val detalhes: String,
    val criadoEm: Long
)

@Entity(tableName = "projeto_ci_profiles", indices = [Index("projetoId", unique = true)])
data class ProjetoCiProfileEntity(
    @androidx.room.PrimaryKey val id: String,
    val projetoId: String,
    val stack: String,
    val obrigatorias: List<String>,
    val recomendadas: List<String>,
    val build: String?,
    val lint: String?,
    val analiseEstatica: String?,
    val seguranca: String?,
    val atualizadoEm: Long
)

@Entity(tableName = "projeto_github", indices = [Index("projetoId", unique = true)])
data class ProjetoGithubEntity(
    @androidx.room.PrimaryKey val id: String,
    val projetoId: String,
    val status: String,
    val repositorio: String?,
    val branchAtual: String?,
    val ultimaSincronizacao: Long?,
    val mensagemErro: String?
)
