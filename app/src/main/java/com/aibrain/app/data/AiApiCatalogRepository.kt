package com.aibrain.app.data

import android.content.Context
import com.aibrain.app.brain.AiApiAvailability
import com.aibrain.app.brain.AiApiAccess
import com.aibrain.app.brain.AiApiModel
import com.aibrain.app.data.local.AiApiCatalogEntity
import com.aibrain.app.data.local.AppDatabase

class AiApiCatalogRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val dao = db.aiApiCatalogDao()

    fun listActive(): List<AiApiModel> = dao.getActive().map { it.toDomain() }

    fun find(modelId: String): AiApiModel? = dao.get(modelId)?.toDomain()

    fun upsert(models: List<AiApiModel>, nowEpochMs: Long) {
        dao.upsertAll(models.map { it.toEntity(nowEpochMs) })
    }

    fun recordVerification(model: AiApiModel, availability: AiApiAvailability, httpStatus: Int?, latencyMs: Long?, checkedAt: Long, evidence: String = "", error: String? = null) {
        val current = dao.get(model.modelId)
        dao.upsertAll(listOf((current ?: model.toEntity(checkedAt)).copy(availability = availability.name, httpStatus = httpStatus, latencyMs = latencyMs, lastVerifiedAt = checkedAt, evidence = evidence.take(2000), lastError = error?.take(500), updatedAt = checkedAt)))
    }

    private fun AiApiCatalogEntity.toDomain() = AiApiModel(providerId, modelId, displayName, capabilities.split('|').filter { it.isNotBlank() }.toSet(), runCatching { AiApiAccess.valueOf(access) }.getOrDefault(AiApiAccess.UNKNOWN), endpoint, documentationUrl, requiresKey, active)

    private fun AiApiModel.toEntity(now: Long) = AiApiCatalogEntity(modelId, providerId, providerId, "GLOBAL", displayName, capabilities.sorted().joinToString("|"), access.name, endpoint, documentationUrl, requiresKey, active, AiApiAvailability.UNKNOWN.name, null, null, 0L, "", null, documentationUrl, now)
}
