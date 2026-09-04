package com.aibrain.app.model

/**
 * Deterministic merge policy shared by discovery and local persistence.
 * Higher-priority sources provide authoritative non-empty metadata; lower-priority
 * sources may only fill fields that are absent from the preferred record.
 */
internal object PublicApiMergePolicy {
    fun deduplicate(apis: List<PublicApi>, now: Long = System.currentTimeMillis()): List<PublicApi> = apis
        .groupBy(::identityKey)
        .toSortedMap()
        .values
        .map { group ->
            group.sortedWith(compareByDescending<PublicApi> { it.source.priority }.thenBy { it.id })
                .reduce { current, next -> merge(current, next, now) }
        }
        .sortedWith(compareBy<PublicApi> { it.name.lowercase() }.thenBy { it.id })

    fun sameApi(left: PublicApi, right: PublicApi): Boolean = identityKey(left) == identityKey(right)

    fun merge(left: PublicApi, right: PublicApi, now: Long): PublicApi {
        val preferred = listOf(left, right)
            .sortedWith(compareByDescending<PublicApi> { it.source.priority }.thenBy { it.id })
            .first()
        val fallback = if (preferred === left) right else left
        return preferred.copy(
            id = left.id,
            name = preferred.name.ifBlank { fallback.name },
            description = preferred.description.ifBlank { fallback.description },
            category = preferred.category.ifBlank { fallback.category },
            baseUrl = preferred.baseUrl ?: fallback.baseUrl,
            documentationUrl = preferred.documentationUrl ?: fallback.documentationUrl,
            authentication = if (preferred.authentication != ApiAuthentication.UNKNOWN) preferred.authentication else fallback.authentication,
            https = preferred.https ?: fallback.https,
            endpoints = (preferred.endpoints + fallback.endpoints).distinctBy { it.normalizedMethod + " " + it.normalizedPath },
            capabilities = (preferred.capabilities + fallback.capabilities).distinct().sorted(),
            status = if (preferred.status != ApiStatus.UNKNOWN) preferred.status else fallback.status,
            reliability = preferred.reliability ?: fallback.reliability,
            lastChecked = preferred.lastChecked ?: fallback.lastChecked,
            createdAt = left.createdAt ?: right.createdAt ?: now,
            updatedAt = maxOf(left.updatedAt ?: 0L, right.updatedAt ?: 0L, now).takeIf { it > 0 },
            sources = (left.allSources + right.allSources).distinct()
        )
    }

    fun identityKey(api: PublicApi): String = (api.baseUrl ?: api.documentationUrl)
        ?.trim()?.lowercase()?.removeSuffix("/")
        ?.takeIf(String::isNotBlank)
        ?: api.name.trim().lowercase()
}
