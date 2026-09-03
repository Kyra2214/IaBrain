package com.aibrain.app.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "ias")
data class IAEntity(
    @androidx.room.PrimaryKey val id: String,
    val nome: String,
    val descricao: String,
    val url: String,
    val logo: String,
    val idiomas: List<String>,
    val categoria: String?,
    val categorias: List<String>,
    val notas: Map<String, Int>,
    val plataformas: List<String>,
    val modeloAcesso: String?,
    val possuiApi: Boolean?,
    val requerLogin: Boolean?,
    val status: String?,
    val ultimaVerificacao: String?,
    val casosDeUso: List<String>,
    val gratuita: Boolean,
    val acesso: String,
    val ativo: Boolean = true
)

@Entity(tableName = "projetos")
data class ProjetoEntity(
    @androidx.room.PrimaryKey val id: String,
    val nome: String,
    val descricao: String,
    val plataforma: String?,
    val complexidade: String,
    val modeloAcessoPreferido: String?,
    val criadoEm: Long,
    val atualizadoEm: Long,
    val status: String
)

@Entity(tableName = "projeto_funcoes", indices = [Index("projetoId")])
data class ProjetoFuncaoEntity(
    @androidx.room.PrimaryKey val id: String,
    val projetoId: String,
    val funcao: String,
    val descricao: String,
    val ordem: Int,
    val status: String
)

@Entity(tableName = "projeto_ias", indices = [Index("projetoId"), Index("funcaoId"), Index("iaId")])
data class ProjetoIAEntity(
    @androidx.room.PrimaryKey val id: String,
    val projetoId: String,
    val funcaoId: String,
    val iaId: String,
    val prioridade: Int,
    val motivo: String,
    val selecionada: Boolean
)

@Entity(tableName = "prompts", indices = [Index("projetoId"), Index("funcaoId"), Index("iaId")])
data class PromptEntity(
    @androidx.room.PrimaryKey val id: String,
    val projetoId: String?,
    val funcaoId: String?,
    val iaId: String?,
    val titulo: String,
    val prompt: String,
    val modeloGeracao: String?,
    val origem: String,
    val criadoEm: Long,
    val atualizadoEm: Long,
    val favorito: Boolean
)

@Entity(tableName = "projeto_contextos", indices = [Index("projetoId", unique = true)])
data class ProjetoContextoEntity(
    @androidx.room.PrimaryKey val id: String,
    val projetoId: String,
    val objetivo: String,
    val stack: List<String>,
    val memoria: String,
    val preferencias: String,
    val estadoAtual: String,
    val recursos: List<String>,
    val atualizadoEm: Long
)

@Entity(tableName = "comandos", indices = [Index("categoria"), Index("ativo"), Index("usoCount")])
data class ComandoEntity(
    @androidx.room.PrimaryKey val id: String,
    val slug: String,
    val nome: String,
    val comando: String,
    val categoria: String,
    val descricaoCurta: String,
    val explicacao: String,
    val objetivo: String,
    val quandoUsar: String,
    val quandoNaoUsar: String,
    val sintaxe: String,
    val exemplo: String,
    val aliases: List<String>,
    val iaRecomendada: String,
    val modoExecucao: String,
    val suportaWeb: Boolean,
    val suportaArquivos: Boolean,
    val suportaProjeto: Boolean,
    val suportaMultiplasIAs: Boolean,
    val nivel: String,
    val ativo: Boolean,
    val favorito: Boolean,
    val usoCount: Int,
    val criadoEm: Long,
    val atualizadoEm: Long
)

@Entity(tableName = "comando_capacidades", primaryKeys = ["comandoId", "capacidade"], indices = [Index("capacidade")])
data class ComandoCapacidadeEntity(val comandoId: String, val capacidade: String, val obrigatoria: Boolean, val peso: Int = 1)

@Entity(tableName = "comando_relacionamentos", primaryKeys = ["origemId", "destinoId", "tipo"], indices = [Index("destinoId")])
data class ComandoRelacionamentoEntity(val origemId: String, val destinoId: String, val tipo: String, val ordem: Int = 0)

@Entity(tableName = "comando_parametros", primaryKeys = ["comandoId", "nome"])
data class ComandoParametroEntity(val comandoId: String, val nome: String, val tipo: String, val obrigatorio: Boolean, val valorPadrao: String?, val descricao: String, val opcoes: List<String>)

@Entity(tableName = "comando_ias", primaryKeys = ["comandoId", "iaId"], indices = [Index("iaId")])
data class ComandoIAEntity(val comandoId: String, val iaId: String, val prioridade: Int, val motivo: String)

@Entity(tableName = "ia_capacidades", primaryKeys = ["iaId", "capacidade"], indices = [Index("capacidade")])
data class IACapabilityEntity(val iaId: String, val capacidade: String, val especialidade: Boolean = false, val nivel: Int = 1)

@Entity(tableName = "ia_routing_profiles", indices = [Index("iaId", unique = true), Index("enabled")])
data class IARoutingProfileEntity(
    @androidx.room.PrimaryKey val id: String,
    val iaId: String,
    val qualityScore: Double,
    val speedScore: Double,
    val costScore: Double,
    val reliabilityScore: Double,
    val contextScore: Double,
    val enabled: Boolean,
    val updatedAt: Long
) {
    init { require(listOf(qualityScore, speedScore, costScore, reliabilityScore, contextScore).all { it in 0.0..1.0 }) { "Routing profile scores must be between 0.0 and 1.0" } }
}

@Entity(tableName = "workflows", indices = [Index("atualizadoEm")])
data class WorkflowEntity(@androidx.room.PrimaryKey val id: String, val nome: String, val estrategia: String, val estado: String, val criadoEm: Long, val atualizadoEm: Long)

@Entity(tableName = "workflow_comandos", primaryKeys = ["workflowId", "ordem"], indices = [Index("comandoId")])
data class WorkflowComandoEntity(val workflowId: String, val ordem: Int, val comandoId: String, val iaId: String?, val handoff: Boolean)

@Entity(tableName = "comando_execucoes", indices = [Index("comandoId"), Index("iaId")])
data class ComandoExecucaoEntity(@androidx.room.PrimaryKey val id: String, val comandoId: String, val iaId: String?, val workflowId: String?, val duracaoMs: Long?, val sucesso: Boolean?, val erro: String?, val avaliacao: Int?, val criadoEm: Long)

@Entity(
    tableName = "projeto_funcao_dependencias",
    primaryKeys = ["projetoId", "funcaoId", "dependeDeFuncaoId"],
    indices = [Index("dependeDeFuncaoId")]
)
data class ProjetoFuncaoDependenciaEntity(
    val projetoId: String,
    val funcaoId: String,
    val dependeDeFuncaoId: String
)

@Entity(
    tableName = "projeto_execucoes",
    indices = [Index("projetoId"), Index("funcaoId"), Index("iaId"), Index("status"), Index("criadoEm")]
)
data class ProjetoExecucaoEntity(
    @androidx.room.PrimaryKey val id: String,
    val projetoId: String,
    val funcaoId: String,
    val iaId: String?,
    val promptId: String?,
    val status: String,
    val promptSnapshot: String,
    val resultado: String?,
    val erro: String?,
    val iniciadoEm: Long?,
    val finalizadoEm: Long?,
    val criadoEm: Long,
    val atualizadoEm: Long
)