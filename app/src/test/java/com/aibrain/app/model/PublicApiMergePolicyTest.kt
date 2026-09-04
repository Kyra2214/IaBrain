package com.aibrain.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicApiMergePolicyTest {
    @Test
    fun `higher priority metadata is not overwritten by lower priority refresh`() {
        val official = api(
            id = "official",
            name = "Canonical API",
            description = "Official description",
            category = "Official",
            baseUrl = "https://api.example.com",
            documentationUrl = "https://docs.example.com",
            authentication = ApiAuthentication.OAUTH,
            source = ApiSource.OFFICIAL_DOCS,
            endpoints = listOf(ApiEndpoint("GET", "/books"))
        )
        val directory = api(
            id = "directory",
            name = "Stale API",
            description = "Directory description",
            category = "Directory",
            baseUrl = "https://api.example.com",
            documentationUrl = "https://directory.example.com",
            authentication = ApiAuthentication.UNKNOWN,
            source = ApiSource.PUBLIC_APIS_IO,
            endpoints = listOf(ApiEndpoint("POST", "/books"))
        )

        val merged = PublicApiMergePolicy.merge(official, directory, now = 123L)

        assertEquals("Canonical API", merged.name)
        assertEquals("Official description", merged.description)
        assertEquals("Official", merged.category)
        assertEquals("https://docs.example.com", merged.documentationUrl)
        assertEquals(ApiAuthentication.OAUTH, merged.authentication)
        assertEquals(listOf("GET", "POST"), merged.endpoints.map { it.normalizedMethod }.sorted())
        assertTrue(ApiSource.PUBLIC_APIS_IO in merged.allSources)
        assertEquals(123L, merged.updatedAt)
    }

    @Test
    fun `unknown preferred fields are filled only from lower priority evidence`() {
        val preferred = api(
            id = "official",
            baseUrl = "https://api.example.com",
            documentationUrl = null,
            authentication = ApiAuthentication.UNKNOWN,
            source = ApiSource.OFFICIAL_DOCS
        )
        val fallback = api(
            id = "directory",
            baseUrl = "https://api.example.com",
            documentationUrl = "https://directory.example.com",
            authentication = ApiAuthentication.API_KEY,
            source = ApiSource.PUBLIC_APIS_IO
        )

        val merged = PublicApiMergePolicy.merge(preferred, fallback, now = 456L)

        assertEquals("https://directory.example.com", merged.documentationUrl)
        assertEquals(ApiAuthentication.API_KEY, merged.authentication)
    }

    @Test
    fun `deduplication is stable by identity and source priority`() {
        val first = api(id = "z", name = "Z API", baseUrl = "https://z.example.com", source = ApiSource.PUBLIC_APIS_IO)
        val second = api(id = "a", name = "A API", baseUrl = "https://z.example.com/", source = ApiSource.OFFICIAL_DOCS)

        val result = PublicApiMergePolicy.deduplicate(listOf(first, second), now = 789L)

        assertEquals(1, result.size)
        assertEquals(ApiSource.OFFICIAL_DOCS, result.single().source)
        assertEquals("a", result.single().id)
    }

    private fun api(
        id: String,
        name: String = "Example API",
        description: String = "Description",
        category: String = "Development",
        baseUrl: String? = "https://example.com",
        documentationUrl: String? = null,
        authentication: ApiAuthentication = ApiAuthentication.NONE,
        source: ApiSource = ApiSource.OFFICIAL_DOCS,
        endpoints: List<ApiEndpoint> = emptyList()
    ) = PublicApi(
        id = id,
        name = name,
        description = description,
        category = category,
        baseUrl = baseUrl,
        documentationUrl = documentationUrl,
        authentication = authentication,
        source = source,
        endpoints = endpoints
    )
}
