package com.aibrain.app.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

class RoomConverters {
    @TypeConverter fun listToJson(value: List<String>): String = JSONArray(value).toString()
    @TypeConverter fun jsonToList(value: String): List<String> = runCatching {
        val a = JSONArray(value); (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())
    @TypeConverter fun mapToJson(value: Map<String, Int>): String = JSONObject(value).toString()
    @TypeConverter fun jsonToMap(value: String): Map<String, Int> = runCatching {
        val o = JSONObject(value); o.keys().asSequence().associateWith { o.optInt(it) }
    }.getOrDefault(emptyMap())
}

@Database(entities = [IAEntity::class, ProjetoEntity::class, ProjetoFuncaoEntity::class, ProjetoIAEntity::class, PromptEntity::class, ProjetoContextoEntity::class, ComandoEntity::class, ComandoCapacidadeEntity::class, ComandoRelacionamentoEntity::class, ComandoParametroEntity::class, ComandoIAEntity::class, WorkflowEntity::class, WorkflowComandoEntity::class, ComandoExecucaoEntity::class, IACapabilityEntity::class, IARoutingProfileEntity::class, ProjetoContribuicaoEntity::class, ProjetoArquivoWorkspaceEntity::class, ProjetoIntegracaoEntity::class, ProjetoValidacaoEntity::class, ProjetoHistoricoEntity::class, ProjetoCiProfileEntity::class, ProjetoGithubEntity::class, ProjetoTarefaEntity::class, ProjetoMemoriaEntity::class, ProjetoSkillEntity::class, BrowserContextoEntity::class, PromptAcaoHistoricoEntity::class], version = 9, exportSchema = true)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun iaDao(): IADao
    abstract fun projetoDao(): ProjetoDao
    abstract fun projetoFuncaoDao(): ProjetoFuncaoDao
    abstract fun projetoIADao(): ProjetoIADao
    abstract fun promptDao(): PromptDao
    abstract fun projetoContextoDao(): ProjetoContextoDao
    abstract fun comandoDao(): ComandoDao
    abstract fun comandoGrafoDao(): ComandoGrafoDao
    abstract fun workflowDao(): WorkflowDao
    abstract fun comandoExecucaoDao(): ComandoExecucaoDao
    abstract fun iaCapabilityDao(): IACapabilityDao
    abstract fun iaRoutingProfileDao(): IARoutingProfileDao
    abstract fun projetoWorkspaceDao(): ProjetoWorkspaceDao
    abstract fun projetoIntegracaoDao(): ProjetoIntegracaoDao
    abstract fun projetoValidacaoDao(): ProjetoValidacaoDao
    abstract fun projetoHistoricoDao(): ProjetoHistoricoDao
    abstract fun projetoCiDao(): ProjetoCiDao
    abstract fun projetoGithubDao(): ProjetoGithubDao
    abstract fun projetoTarefaDao(): ProjetoTarefaDao
    abstract fun projetoMemoriaDao(): ProjetoMemoriaDao
    abstract fun projetoSkillDao(): ProjetoSkillDao
    abstract fun browserContextoDao(): BrowserContextoDao
    abstract fun promptAcaoHistoricoDao(): PromptAcaoHistoricoDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE ias ADD COLUMN logo TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE ias ADD COLUMN idiomas TEXT NOT NULL DEFAULT '[]'")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS projeto_contextos (id TEXT NOT NULL, projetoId TEXT NOT NULL, objetivo TEXT NOT NULL, stack TEXT NOT NULL, memoria TEXT NOT NULL, preferencias TEXT NOT NULL, estadoAtual TEXT NOT NULL, recursos TEXT NOT NULL, atualizadoEm INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_projeto_contextos_projetoId ON projeto_contextos(projetoId)")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS comandos (id TEXT NOT NULL, slug TEXT NOT NULL, nome TEXT NOT NULL, comando TEXT NOT NULL, categoria TEXT NOT NULL, descricaoCurta TEXT NOT NULL, explicacao TEXT NOT NULL, objetivo TEXT NOT NULL, quandoUsar TEXT NOT NULL, quandoNaoUsar TEXT NOT NULL, sintaxe TEXT NOT NULL, exemplo TEXT NOT NULL, aliases TEXT NOT NULL, iaRecomendada TEXT NOT NULL, modoExecucao TEXT NOT NULL, suportaWeb INTEGER NOT NULL, suportaArquivos INTEGER NOT NULL, suportaProjeto INTEGER NOT NULL, suportaMultiplasIAs INTEGER NOT NULL, nivel TEXT NOT NULL, ativo INTEGER NOT NULL, favorito INTEGER NOT NULL, usoCount INTEGER NOT NULL, criadoEm INTEGER NOT NULL, atualizadoEm INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_comandos_categoria ON comandos(categoria)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_comandos_ativo ON comandos(ativo)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_comandos_usoCount ON comandos(usoCount)")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS comando_capacidades (comandoId TEXT NOT NULL, capacidade TEXT NOT NULL, obrigatoria INTEGER NOT NULL, peso INTEGER NOT NULL, PRIMARY KEY(comandoId, capacidade))")
                db.execSQL("CREATE TABLE IF NOT EXISTS comando_relacionamentos (origemId TEXT NOT NULL, destinoId TEXT NOT NULL, tipo TEXT NOT NULL, ordem INTEGER NOT NULL, PRIMARY KEY(origemId, destinoId, tipo))")
                db.execSQL("CREATE TABLE IF NOT EXISTS comando_parametros (comandoId TEXT NOT NULL, nome TEXT NOT NULL, tipo TEXT NOT NULL, obrigatorio INTEGER NOT NULL, valorPadrao TEXT, descricao TEXT NOT NULL, opcoes TEXT NOT NULL, PRIMARY KEY(comandoId, nome))")
                db.execSQL("CREATE TABLE IF NOT EXISTS comando_ias (comandoId TEXT NOT NULL, iaId TEXT NOT NULL, prioridade INTEGER NOT NULL, motivo TEXT NOT NULL, PRIMARY KEY(comandoId, iaId))")
                db.execSQL("CREATE TABLE IF NOT EXISTS workflows (id TEXT NOT NULL, nome TEXT NOT NULL, estrategia TEXT NOT NULL, estado TEXT NOT NULL, criadoEm INTEGER NOT NULL, atualizadoEm INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS workflow_comandos (workflowId TEXT NOT NULL, ordem INTEGER NOT NULL, comandoId TEXT NOT NULL, iaId TEXT, handoff INTEGER NOT NULL, PRIMARY KEY(workflowId, ordem))")
                db.execSQL("CREATE TABLE IF NOT EXISTS comando_execucoes (id TEXT NOT NULL, comandoId TEXT NOT NULL, iaId TEXT, workflowId TEXT, duracaoMs INTEGER, sucesso INTEGER, erro TEXT, avaliacao INTEGER, criadoEm INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS ia_capacidades (iaId TEXT NOT NULL, capacidade TEXT NOT NULL, especialidade INTEGER NOT NULL, nivel INTEGER NOT NULL, PRIMARY KEY(iaId, capacidade))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ia_capacidades_capacidade ON ia_capacidades(capacidade)")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS ia_routing_profiles (id TEXT NOT NULL, iaId TEXT NOT NULL, qualityScore REAL NOT NULL, speedScore REAL NOT NULL, costScore REAL NOT NULL, reliabilityScore REAL NOT NULL, contextScore REAL NOT NULL, enabled INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ia_routing_profiles_iaId ON ia_routing_profiles(iaId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ia_routing_profiles_enabled ON ia_routing_profiles(enabled)")
            }
        }
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS projeto_contribuicoes (id TEXT NOT NULL, projetoId TEXT NOT NULL, fonte TEXT NOT NULL, nomeFonte TEXT NOT NULL, recebidoEm INTEGER NOT NULL, status TEXT NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projeto_contribuicoes_projetoId ON projeto_contribuicoes(projetoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projeto_contribuicoes_recebidoEm ON projeto_contribuicoes(recebidoEm)")
                db.execSQL("CREATE TABLE IF NOT EXISTS projeto_arquivos_workspace (projetoId TEXT NOT NULL, contribuicaoId TEXT NOT NULL, caminho TEXT NOT NULL, hash TEXT NOT NULL, tamanho INTEGER NOT NULL, origem TEXT NOT NULL, PRIMARY KEY(contribuicaoId, caminho))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projeto_arquivos_workspace_projetoId ON projeto_arquivos_workspace(projetoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projeto_arquivos_workspace_caminho ON projeto_arquivos_workspace(caminho)")
                db.execSQL("CREATE TABLE IF NOT EXISTS projeto_integracoes (id TEXT NOT NULL, projetoId TEXT NOT NULL, numero INTEGER NOT NULL, fontes TEXT NOT NULL, status TEXT NOT NULL, conflitos TEXT NOT NULL, criadoEm INTEGER NOT NULL, concluidoEm INTEGER, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projeto_integracoes_projetoId ON projeto_integracoes(projetoId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS projeto_validacoes (id TEXT NOT NULL, projetoId TEXT NOT NULL, nome TEXT NOT NULL, nivel TEXT NOT NULL, status TEXT NOT NULL, detalhes TEXT NOT NULL, executadoEm INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projeto_validacoes_projetoId ON projeto_validacoes(projetoId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS projeto_historico (id TEXT NOT NULL, projetoId TEXT NOT NULL, tipo TEXT NOT NULL, detalhes TEXT NOT NULL, criadoEm INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projeto_historico_projetoId ON projeto_historico(projetoId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS projeto_ci_profiles (id TEXT NOT NULL, projetoId TEXT NOT NULL, stack TEXT NOT NULL, obrigatorias TEXT NOT NULL, recomendadas TEXT NOT NULL, build TEXT, lint TEXT, analiseEstatica TEXT, seguranca TEXT, atualizadoEm INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_projeto_ci_profiles_projetoId ON projeto_ci_profiles(projetoId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS projeto_github (id TEXT NOT NULL, projetoId TEXT NOT NULL, status TEXT NOT NULL, repositorio TEXT, branchAtual TEXT, ultimaSincronizacao INTEGER, mensagemErro TEXT, PRIMARY KEY(id))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_projeto_github_projetoId ON projeto_github(projetoId)")
            }
        }
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS projeto_memorias (id TEXT NOT NULL, projetoId TEXT NOT NULL, tipo TEXT NOT NULL, titulo TEXT NOT NULL, conteudo TEXT NOT NULL, criadoEm INTEGER NOT NULL, atualizadoEm INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projeto_memorias_projetoId ON projeto_memorias(projetoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projeto_memorias_criadoEm ON projeto_memorias(criadoEm)")
                db.execSQL("CREATE TABLE IF NOT EXISTS projeto_skills (id TEXT NOT NULL, projetoId TEXT, nome TEXT NOT NULL, descricao TEXT NOT NULL, passos TEXT NOT NULL, ativo INTEGER NOT NULL, criadoEm INTEGER NOT NULL, atualizadoEm INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projeto_skills_projetoId ON projeto_skills(projetoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projeto_skills_ativo ON projeto_skills(ativo)")
                db.execSQL("CREATE TABLE IF NOT EXISTS projeto_tarefas (id TEXT NOT NULL, projetoId TEXT, titulo TEXT NOT NULL, detalhe TEXT NOT NULL, status TEXT NOT NULL, prioridade TEXT NOT NULL, criadoEm INTEGER NOT NULL, atualizadoEm INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projeto_tarefas_projetoId ON projeto_tarefas(projetoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projeto_tarefas_status ON projeto_tarefas(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projeto_tarefas_prioridade ON projeto_tarefas(prioridade)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projeto_tarefas_atualizadoEm ON projeto_tarefas(atualizadoEm)")
                db.execSQL("CREATE TABLE IF NOT EXISTS browser_contextos (id TEXT NOT NULL, origem TEXT NOT NULL, abaSelecionadaId TEXT, abas TEXT NOT NULL, prompt TEXT, criadoEm INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_browser_contextos_criadoEm ON browser_contextos(criadoEm)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_browser_contextos_origem ON browser_contextos(origem)")
                db.execSQL("CREATE TABLE IF NOT EXISTS prompt_acoes_historico (id TEXT NOT NULL, promptId TEXT, acao TEXT NOT NULL, iaId TEXT, detalhe TEXT NOT NULL, criadoEm INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_prompt_acoes_historico_promptId ON prompt_acoes_historico(promptId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_prompt_acoes_historico_acao ON prompt_acoes_historico(acao)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_prompt_acoes_historico_criadoEm ON prompt_acoes_historico(criadoEm)")
            }
        }

        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "iabrain.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build().also { INSTANCE = it }
        }
    }
}
