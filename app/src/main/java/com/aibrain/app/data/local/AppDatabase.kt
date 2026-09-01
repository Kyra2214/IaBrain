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

@Database(entities = [IAEntity::class, ProjetoEntity::class, ProjetoFuncaoEntity::class, ProjetoIAEntity::class, PromptEntity::class, ProjetoContextoEntity::class], version = 3, exportSchema = true)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun iaDao(): IADao
    abstract fun projetoDao(): ProjetoDao
    abstract fun projetoFuncaoDao(): ProjetoFuncaoDao
    abstract fun projetoIADao(): ProjetoIADao
    abstract fun promptDao(): PromptDao
    abstract fun projetoContextoDao(): ProjetoContextoDao
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
        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "iabrain.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
        }
    }
}
