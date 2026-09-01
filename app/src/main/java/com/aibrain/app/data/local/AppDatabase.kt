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

@Database(entities = [IAEntity::class, ProjetoEntity::class, ProjetoFuncaoEntity::class, ProjetoIAEntity::class, PromptEntity::class, ProjetoContextoEntity::class, ComandoEntity::class, ComandoCapacidadeEntity::class, ComandoRelacionamentoEntity::class, ComandoParametroEntity::class, ComandoIAEntity::class, WorkflowEntity::class, WorkflowComandoEntity::class, ComandoExecucaoEntity::class], version = 5, exportSchema = true)
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
        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "iabrain.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { INSTANCE = it }
        }
    }
}
