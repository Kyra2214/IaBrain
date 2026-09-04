package com.aibrain.app.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName="ai_api_catalog",indices=[Index("providerId"),Index("availability"),Index("access"),Index("active")])
data class AiApiCatalogEntity(@androidx.room.PrimaryKey val modelId:String,val providerId:String,val providerName:String,val region:String,val displayName:String,val capabilities:String,val access:String,val endpoint:String,val documentationUrl:String,val requiresKey:Boolean,val active:Boolean,val availability:String="UNKNOWN",val httpStatus:Int?=null,val latencyMs:Long?=null,val lastVerifiedAt:Long=0L,val evidence:String="",val lastError:String?=null,val sourceUrl:String="",val updatedAt:Long=0L)

@Entity(tableName="ai_api_sync_state")
data class AiApiSyncStateEntity(@androidx.room.PrimaryKey val id:Int=1,val lastSyncAt:Long=0L,val catalogVersion:String="",val sourceHash:String="")

@Entity(tableName="ai_api_quota_events",indices=[Index("providerId"),Index("modelId"),Index("detectedAt")])
data class AiApiQuotaEventEntity(@androidx.room.PrimaryKey val id:String,val providerId:String,val modelId:String,val failure:String,val detectedAt:Long)
