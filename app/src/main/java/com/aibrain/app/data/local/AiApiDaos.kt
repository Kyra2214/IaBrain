package com.aibrain.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AiApiCatalogDao {
    @Query("SELECT * FROM ai_api_catalog WHERE active = 1 ORDER BY providerName, displayName")
    fun getActive(): List<AiApiCatalogEntity>
    @Query("SELECT * FROM ai_api_catalog WHERE modelId = :modelId LIMIT 1")
    fun get(modelId:String): AiApiCatalogEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE)
    fun upsertAll(items:List<AiApiCatalogEntity>)
    @Query("DELETE FROM ai_api_catalog WHERE modelId NOT IN (:activeModelIds)")
    fun removeMissing(activeModelIds:List<String>)
    @Query("DELETE FROM ai_api_catalog")
    fun clear()
}

@Dao
interface AiApiSyncStateDao {
    @Query("SELECT * FROM ai_api_sync_state WHERE id = 1 LIMIT 1")
    fun get(): AiApiSyncStateEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE)
    fun save(state:AiApiSyncStateEntity)
}

@Dao
interface AiApiQuotaEventDao {
    @Insert(onConflict=OnConflictStrategy.REPLACE)
    fun insert(event:AiApiQuotaEventEntity)
    @Query("SELECT * FROM ai_api_quota_events ORDER BY detectedAt DESC LIMIT :limit")
    fun recent(limit:Int):List<AiApiQuotaEventEntity>
}
