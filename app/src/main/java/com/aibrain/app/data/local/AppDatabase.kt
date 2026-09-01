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

@Database(entities = [IAEntity::class, ProjetoEntity::class, ProjetoFuncaoEntity::class, ProjetoIAEntity::class, PromptEntity::class], version = 2, exportSchema = true)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun iaDao(): IADao
    abstract fun projetoDao(): ProjetoDao
    abstract fun projetoFuncaoDao(): ProjetoFuncaoDao
    abstract fun projetoIADao(): ProjetoIADao
    abstract fun promptDao(): PromptDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE ias ADD COLUMN logo TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE ias ADD COLUMN idiomas TEXT NOT NULL DEFAULT '[]'")
            }
        }
        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "iabrain.db")
                .addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
        }
    }
}
