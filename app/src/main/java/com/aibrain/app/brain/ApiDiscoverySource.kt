package com.aibrain.app.brain

import com.aibrain.app.model.ApiAuthentication
import com.aibrain.app.model.ApiSource
import com.aibrain.app.model.ApiStatus
import com.aibrain.app.model.PublicApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Query submitted to one or more independent discovery sources. */
data class ApiDiscoveryQuery(
    val text: String = "",
    val category: String? = null,
    val limit: Int = 200
)

data class ApiDiscoveryResult(
    val source: ApiSource,
    val apis: List<PublicApi>,
    val complete: Boolean,
    val error: String? = null
)

/** Extension point for official docs, OpenAPI, GitHub, RapidAPI and web discovery. */
interface ApiDiscoverySource {
    val source: ApiSource

    suspend fun discover(query: ApiDiscoveryQuery = ApiDiscoveryQuery()): ApiDiscoveryResult
}

/**
 * PublicAPIs.io is intentionally only one source. It returns directory metadata;
 * it does not invent endpoints or schemas when the source does not provide them.
 */
class PublicApisIoSource(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val transport: suspend (String) -> String = { url -> get(url) }
) : ApiDiscoverySource {
    override val source: ApiSource = ApiSource.PUBLIC_APIS_IO

    override suspend fun discover(query: ApiDiscoveryQuery): ApiDiscoveryResult = withContext(Dispatchers.IO) {
        val requestUrl = buildUrl(query)
        val primary = runCatching { JSONObject(transport(requestUrl)) }
        primary.getOrNull()?.let { root ->
            val entries = root.optJSONArray("entries") ?: JSONArray()
            val now = System.currentTimeMillis()
            val parsed = (0 until entries.length()).mapNotNull { index -> parseEntry(entries.optJSONObject(index), now) }
            return@withContext ApiDiscoveryResult(source, parsed.take(query.limit.coerceIn(1, 500)), complete = true)
        }

        // Current PublicAPIs.io renders a Next.js hydration payload. Reading that
        // bounded payload is a compatibility fallback, not aggressive scraping.
        val fallback = runCatching { transport(WEB_ENDPOINT) }.getOrNull()
        val fallbackApis = fallback?.let { parseHydrationPayload(it, query) }
        if (!fallbackApis.isNullOrEmpty()) {
            return@withContext ApiDiscoveryResult(source, fallbackApis.take(query.limit.coerceIn(1, 500)), complete = true)
        }
        val error = primary.exceptionOrNull()?.message ?: "Fonte indisponível"
        ApiDiscoveryResult(source, emptyList(), complete = false, error = error)
    }

    private fun parseHydrationPayload(html: String, query: ApiDiscoveryQuery): List<PublicApi> {
        val startMarker = "<script id=\"__NEXT_DATA__\""
        val start = html.indexOf(startMarker)
        if (start < 0) return emptyList()
        val jsonStart = html.indexOf('>', start).takeIf { it >= 0 }?.plus(1) ?: return emptyList()
        val jsonEnd = html.indexOf("</script>", jsonStart).takeIf { it >= 0 } ?: return emptyList()
        val root = runCatching { JSONObject(html.substring(jsonStart, jsonEnd)) }.getOrNull() ?: return emptyList()
        val collected = mutableListOf<PublicApi>()
        collectWebEntries(root, collected)
        val text = query.text.trim().lowercase()
        val category = query.category?.trim()?.lowercase().orEmpty()
        return collected.distinctBy { it.id }.filter { api ->
            (text.isBlank() || api.searchableText.contains(text)) &&
                (category.isBlank() || api.category.lowercase() == category)
        }
    }

    private fun collectWebEntries(value: Any?, output: MutableList<PublicApi>) {
        when (value) {
            is JSONObject -> {
                val name = value.optString("company_name").ifBlank { value.optString("name") }.trim()
                val description = value.optString("short_description").ifBlank { value.optString("description") }.trim()
                val link = value.optString("website").ifBlank { value.optString("url") }.trim().takeIf { it.isNotBlank() }
                val categories = value.optJSONArray("categories")
                val category = categories?.optString(0)?.trim().takeIf { !it.isNullOrBlank() }
                    ?: value.optString("category").trim().ifBlank { "Uncategorized" }
                if (name.isNotBlank() && description.isNotBlank() && link != null && link.startsWith("http")) {
                    val now = System.currentTimeMillis()
                    output += PublicApi(
                        id = PublicApisIoSource.deterministicId(link),
                        name = name.removeSuffix(" API").trim(),
                        description = description,
                        category = category,
                        baseUrl = link,
                        documentationUrl = "https://publicapis.io/",
                        source = source,
                        authentication = ApiAuthentication.UNKNOWN,
                        https = link.startsWith("https://", ignoreCase = true),
                        capabilities = inferCapabilities(description, category),
                        status = ApiStatus.DISCOVERED,
                        lastChecked = now,
                        createdAt = now,
                        updatedAt = now,
                        sources = listOf(source)
                    )
                }
                value.keys().forEach { key -> collectWebEntries(value.opt(key), output) }
            }
            is JSONArray -> for (index in 0 until value.length()) collectWebEntries(value.opt(index), output)
        }
    }

    private fun buildUrl(query: ApiDiscoveryQuery): String {
        val params = mutableListOf<String>()
        query.text.trim().takeIf { it.isNotBlank() }?.let {
            val encoded = URLEncoder.encode(it, Charsets.UTF_8.name())
            params += "title=$encoded"
        }
        query.category?.trim()?.takeIf { it.isNotBlank() }?.let {
            params += "category=${URLEncoder.encode(it, Charsets.UTF_8.name())}"
        }
        return if (params.isEmpty()) endpoint else "$endpoint?${params.joinToString("&")}"
    }

    private fun parseEntry(entry: JSONObject?, now: Long): PublicApi? {
        if (entry == null) return null
        val name = entry.optString("API").trim()
        if (name.isBlank()) return null
        val description = entry.optString("Description").trim()
        val link = entry.optString("Link").trim().takeIf { it.isNotBlank() }
        val category = entry.optString("Category", "Uncategorized").trim().ifBlank { "Uncategorized" }
        val rawHttps = if (entry.has("HTTPS")) entry.optBoolean("HTTPS") else null
        val idSeed = link ?: "$name|$category"
        return PublicApi(
            id = deterministicId(idSeed),
            name = name,
            description = description,
            category = category,
            baseUrl = link,
            documentationUrl = link,
            source = source,
            authentication = ApiAuthentication.fromExternal(entry.optString("Auth")),
            https = rawHttps,
            endpoints = emptyList(),
            capabilities = inferCapabilities(description, category),
            status = ApiStatus.DISCOVERED,
            lastChecked = now,
            createdAt = now,
            updatedAt = now,
            sources = listOf(source)
        )
    }

    private fun inferCapabilities(description: String, category: String): List<String> =
        listOf(category, description)
            .flatMap { it.split(Regex("[^A-Za-zÀ-ÿ0-9]+")) }
            .map { it.trim().lowercase() }
            .filter { it.length >= 4 }
            .distinct()
            .take(8)

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.publicapis.org/entries"
        const val WEB_ENDPOINT = "https://publicapis.io/"

        fun deterministicId(seed: String): String = "api-" + seed.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(96)

        private fun get(url: String): String {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "IaBrain-ApiDiscovery/3.2")
            }
            return try {
                if (connection.responseCode !in 200..299) {
                    throw IOException("HTTP ${connection.responseCode}")
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }
}
