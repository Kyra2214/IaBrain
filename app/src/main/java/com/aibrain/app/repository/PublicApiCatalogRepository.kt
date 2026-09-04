package com.aibrain.app.repository

import android.content.Context
import com.aibrain.app.model.ApiAuthentication
import com.aibrain.app.model.ApiEndpoint
import com.aibrain.app.model.ApiSource
import com.aibrain.app.model.ApiStatus
import com.aibrain.app.model.PublicApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local-first catalog; favorite/history state is deliberately stored elsewhere. */
class PublicApiCatalogRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val file: File get() = File(applicationContext.filesDir, FILE_NAME)

    suspend fun load(): List<PublicApi> = withContext(Dispatchers.IO) {
        synchronized(this@PublicApiCatalogRepository) { readUnsafe() }
    }

    suspend fun add(api: PublicApi): Boolean = withContext(Dispatchers.IO) {
        synchronized(this@PublicApiCatalogRepository) {
            val current = readUnsafe()
            if (current.any { sameApi(it, api) }) return@synchronized false
            writeUnsafe(current + api.copy(createdAt = api.createdAt ?: System.currentTimeMillis()))
            true
        }
    }

    suspend fun remove(id: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(this@PublicApiCatalogRepository) {
            val current = readUnsafe()
            val filtered = current.filterNot { it.id == id }
            if (filtered.size == current.size) false else {
                writeUnsafe(filtered)
                true
            }
        }
    }

    suspend fun merge(discovered: List<PublicApi>, markMissingInactive: Boolean = false): ApiCatalogMergeResult = withContext(Dispatchers.IO) {
        synchronized(this@PublicApiCatalogRepository) {
            val now = System.currentTimeMillis()
            val current = readUnsafe()
            val uniqueIncoming = deduplicate(discovered)
            val merged = current.toMutableList()
            var added = 0
            var updated = 0
            uniqueIncoming.forEach { incoming ->
                val index = merged.indexOfFirst { sameApi(it, incoming) }
                if (index < 0) {
                    merged += incoming.copy(createdAt = incoming.createdAt ?: now, updatedAt = now)
                    added++
                } else {
                    val existing = merged[index]
                    merged[index] = combine(existing, incoming, now)
                    if (merged[index] != existing) updated++
                }
            }
            var inactive = 0
            if (markMissingInactive && uniqueIncoming.isNotEmpty()) {
                val incomingKeys = uniqueIncoming.map(::identityKey).toSet()
                merged.indices.forEach { index ->
                    val existing = merged[index]
                    if (identityKey(existing) !in incomingKeys && existing.status != ApiStatus.INACTIVE) {
                        merged[index] = existing.copy(status = ApiStatus.INACTIVE, updatedAt = now)
                        inactive++
                    }
                }
            }
            val before = current.size
            val normalized = deduplicate(merged)
            val duplicatesRemoved = (before + added - normalized.size).coerceAtLeast(0)
            writeUnsafe(normalized)
            ApiCatalogMergeResult(added, updated, inactive, duplicatesRemoved, normalized.size)
        }
    }

    private fun readUnsafe(): List<PublicApi> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val root = JSONObject(file.readText())
            val array = root.optJSONArray("apis") ?: JSONArray()
            (0 until array.length()).mapNotNull { parse(array.optJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun writeUnsafe(apis: List<PublicApi>) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        val root = JSONObject().put("version", 1).put("apis", JSONArray().apply { apis.forEach { put(serialize(it)) } })
        temporary.writeText(root.toString())
        if (!temporary.renameTo(file)) {
            file.writeText(root.toString())
            temporary.delete()
        }
    }

    private fun parse(value: JSONObject?): PublicApi? {
        if (value == null) return null
        val id = value.optString("id").trim()
        val name = value.optString("name").trim()
        if (id.isBlank() || name.isBlank()) return null
        val endpointArray = value.optJSONArray("endpoints")
        val endpoints = if (endpointArray == null) emptyList() else (0 until endpointArray.length()).mapNotNull { index ->
            val endpoint = endpointArray.optJSONObject(index) ?: return@mapNotNull null
            ApiEndpoint(
                method = endpoint.optString("method", "UNKNOWN"),
                path = endpoint.optString("path", "UNKNOWN"),
                summary = endpoint.optString("summary").takeIf { it.isNotBlank() },
                parameters = endpoint.optStringList("parameters"),
                headers = endpoint.optStringList("headers"),
                requestBody = endpoint.optString("requestBody").takeIf { it.isNotBlank() },
                response = endpoint.optString("response").takeIf { it.isNotBlank() },
                schema = endpoint.optString("schema").takeIf { it.isNotBlank() }
            )
        }
        return PublicApi(
            id = id,
            name = name,
            description = value.optString("description"),
            category = value.optString("category", "Uncategorized"),
            baseUrl = value.optString("baseUrl").takeIf { it.isNotBlank() },
            documentationUrl = value.optString("documentationUrl").takeIf { it.isNotBlank() },
            source = ApiSource.fromKey(value.optString("source")),
            authentication = ApiAuthentication.fromKey(value.optString("authentication")),
            https = if (value.has("https") && !value.isNull("https")) value.optBoolean("https") else null,
            endpoints = endpoints,
            capabilities = value.optStringList("capabilities"),
            status = ApiStatus.fromKey(value.optString("status")),
            reliability = value.optIntOrNull("reliability"),
            lastChecked = value.optLongOrNull("lastChecked"),
            createdAt = value.optLongOrNull("createdAt"),
            updatedAt = value.optLongOrNull("updatedAt"),
            sources = value.optStringList("sources").map(ApiSource::fromKey)
        )
    }

    private fun serialize(api: PublicApi): JSONObject = JSONObject().apply {
        put("id", api.id)
        put("name", api.name)
        put("description", api.description)
        put("category", api.category)
        putNullable("baseUrl", api.baseUrl)
        putNullable("documentationUrl", api.documentationUrl)
        put("source", api.source.key)
        put("authentication", api.authentication.key)
        putNullable("https", api.https)
        put("endpoints", JSONArray().apply { api.endpoints.forEach { endpoint ->
            put(JSONObject().apply {
                put("method", endpoint.normalizedMethod)
                put("path", endpoint.normalizedPath)
                putNullable("summary", endpoint.summary)
                put("parameters", JSONArray(endpoint.parameters))
                put("headers", JSONArray(endpoint.headers))
                putNullable("requestBody", endpoint.requestBody)
                putNullable("response", endpoint.response)
                putNullable("schema", endpoint.schema)
            })
        } })
        put("capabilities", JSONArray(api.capabilities))
        put("status", api.status.key)
        putNullable("reliability", api.reliability)
        putNullable("lastChecked", api.lastChecked)
        putNullable("createdAt", api.createdAt)
        putNullable("updatedAt", api.updatedAt)
        put("sources", JSONArray(api.allSources.map { it.key }))
    }

    private fun combine(existing: PublicApi, incoming: PublicApi, now: Long): PublicApi {
        val preferred = listOf(existing, incoming).sortedWith(compareByDescending<PublicApi> { it.source.priority }.thenBy { it.id }).first()
        return preferred.copy(
            id = existing.id,
            name = if (incoming.name.isNotBlank()) incoming.name else existing.name,
            description = if (incoming.description.isNotBlank()) incoming.description else existing.description,
            category = if (incoming.category.isNotBlank()) incoming.category else existing.category,
            baseUrl = incoming.baseUrl ?: existing.baseUrl,
            documentationUrl = incoming.documentationUrl ?: existing.documentationUrl,
            authentication = if (incoming.authentication != ApiAuthentication.UNKNOWN) incoming.authentication else existing.authentication,
            https = incoming.https ?: existing.https,
            endpoints = (existing.endpoints + incoming.endpoints).distinctBy { it.normalizedMethod + " " + it.normalizedPath },
            capabilities = (existing.capabilities + incoming.capabilities).distinct().sorted(),
            status = if (incoming.status != ApiStatus.UNKNOWN) incoming.status else existing.status,
            reliability = incoming.reliability ?: existing.reliability,
            lastChecked = incoming.lastChecked ?: existing.lastChecked,
            createdAt = existing.createdAt ?: incoming.createdAt ?: now,
            updatedAt = now,
            sources = (existing.allSources + incoming.allSources).distinct()
        )
    }

    private fun deduplicate(apis: List<PublicApi>): List<PublicApi> = apis
        .groupBy(::identityKey)
        .toSortedMap()
        .values
        .map { group -> group.sortedWith(compareByDescending<PublicApi> { it.source.priority }.thenBy { it.id }).reduce { current, next -> combine(current, next, current.updatedAt ?: System.currentTimeMillis()) } }
        .sortedWith(compareBy<PublicApi> { it.name.lowercase() }.thenBy { it.id })

    private fun sameApi(left: PublicApi, right: PublicApi): Boolean = identityKey(left) == identityKey(right)

    private fun identityKey(api: PublicApi): String {
        val url = (api.baseUrl ?: api.documentationUrl).orEmpty().trim().lowercase()
            .removeSuffix("/")
        return if (url.isNotBlank()) url else api.name.trim().lowercase()
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) optLong(key).takeIf { it > 0 } else null
    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key).takeIf { it > 0 } else null
    private fun JSONObject.putNullable(key: String, value: Any?) { if (value == null) put(key, JSONObject.NULL) else put(key, value) }

    companion object {
        const val FILE_NAME = "public_api_catalog.json"
    }
}

data class ApiCatalogMergeResult(
    val added: Int,
    val updated: Int,
    val inactive: Int,
    val duplicatesRemoved: Int,
    val total: Int
)
