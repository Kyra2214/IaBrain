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
