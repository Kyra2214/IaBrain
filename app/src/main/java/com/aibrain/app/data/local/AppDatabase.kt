package com.aibrain.app.data.local

import android.content.Context
import androidx.room.*
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

@Database(entities = [IAEntity::class, ProjetoEntity::class, ProjetoFuncaoEntity::class, ProjetoIAEntity::class, PromptEntity::class], version = 1, exportSchema = true)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun iaDao(): IADao
    abstract fun projetoDao(): ProjetoDao
    abstract fun projetoFuncaoDao(): ProjetoFuncaoDao
    abstract fun projetoIADao(): ProjetoIADao
    abstract fun promptDao(): PromptDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "iabrain.db").build().also { INSTANCE = it }
        }
    }
}
