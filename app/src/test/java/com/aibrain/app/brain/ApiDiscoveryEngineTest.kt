package com.aibrain.app.brain

import com.aibrain.app.model.ApiAuthentication
import com.aibrain.app.model.ApiEndpoint
import com.aibrain.app.model.ApiReviewDecision
import com.aibrain.app.model.ApiSource
import com.aibrain.app.model.PublicApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiDiscoveryEngineTest {
    @Test
    fun `valid PublicAPIs response is discovered and normalized`() = runBlocking {
        val source = PublicApisIoSource(transport = {
            """{"count":1,"entries":[{"API":"Books","Description":"Book search","Auth":"apiKey","HTTPS":true,"Cors":"yes","Link":"https://books.example.com/"}]}"""
        })
        val result = source.discover(ApiDiscoveryQuery(text = "book"))
        val api = result.apis.single()
        assertTrue(result.complete)
        assertEquals("Books", api.name)
        assertEquals(ApiAuthentication.API_KEY, api.authentication)
        assertEquals("https://books.example.com/", api.baseUrl)
        assertEquals(ApiSource.PUBLIC_APIS_IO, api.source)
    }

    @Test
    fun `current website hydration payload is accepted as a bounded fallback`() = runBlocking {
        val source = PublicApisIoSource(transport = { url ->
            if (url == PublicApisIoSource.DEFAULT_ENDPOINT) error("legacy host unavailable")
            """<script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"apis":[{"name":"Books API","description":"Book search","url":"https://books.example.com","category":"Books"}]}}}</script>"""
        })
        val result = source.discover(ApiDiscoveryQuery(text = "books"))
        assertTrue(result.complete)
        assertEquals("Books", result.apis.single().name)
        assertEquals(ApiSource.PUBLIC_APIS_IO, result.apis.single().source)
    }

    @Test
    fun `unavailable and invalid sources do not break discovery`() = runBlocking {
        val unavailable = PublicApisIoSource(transport = { error("offline") }).discover()
        val invalid = PublicApisIoSource(transport = { "not-json" }).discover()
        assertFalse(unavailable.complete)
        assertFalse(invalid.complete)
        assertTrue(unavailable.apis.isEmpty())
        assertTrue(invalid.apis.isEmpty())
    }

    @Test
    fun `equivalent URLs are normalized and deduplicated by source priority`() {
        val engine = ApiDiscoveryEngine(sources = emptyList())
        val publicApi = api(id = "public", name = "Same", baseUrl = "https://example.com/", source = ApiSource.PUBLIC_APIS_IO)
        val official = api(id = "official", name = "Same API", baseUrl = "https://example.com", source = ApiSource.OFFICIAL_DOCS, documentationUrl = "https://docs.example.com")
        val result = engine.deduplicate(listOf(publicApi, official))
        assertEquals(1, result.size)
        assertEquals(ApiSource.OFFICIAL_DOCS, result.single().source)
        assertEquals(2, result.single().allSources.size)
    }

    @Test
    fun `known endpoint contract is analyzed while missing fields remain UNKNOWN`() {
        val api = api(
            endpoints = listOf(ApiEndpoint("get", "/v1/books", summary = "List books"))
        )
        val report = ApiContractIntelligence.analyze(api)
        assertEquals(1, report.knownEndpointCount)
        assertEquals(listOf("GET"), report.methods)
        assertTrue("parameters" in report.unknownFields)
        assertTrue(report.symbols.isNotEmpty())
    }

    @Test
    fun `security blocks HTTP, invalid URLs, traversal and exposed credentials`() {
        val report = ApiSecurityAnalyzer.analyze(api(
            baseUrl = "http://example.com/../v1",
            documentationUrl = "https://docs.example.com",
            description = "api_key: abcdefghijklmnopqrstuvwxyz123456"
        ))
        val codes = report.findings.map { it.code }
        assertTrue("HTTP" in codes || "TRAVERSAL" in codes)
        assertTrue("CREDENTIAL_EXPOSED" in codes)
        assertTrue(report.blockers.isNotEmpty())
    }

    @Test
    fun `security blocks private IPv6 and secrets in endpoint metadata`() {
        val report = ApiSecurityAnalyzer.analyze(api(
            baseUrl = "https://[::1]/v1",
            endpoints = listOf(ApiEndpoint("GET", "/books", headers = listOf("Authorization: Bearer abcdefghijklmnopqrstuvwxyz123456")))
        ))
        val codes = report.findings.map { it.code }
        assertTrue("SUSPICIOUS_DOMAIN" in codes)
        assertTrue("CREDENTIAL_EXPOSED" in codes)
        assertTrue(report.blockers.isNotEmpty())
    }

    @Test
    fun `review decisions are deterministic and ranking breaks ties by id`() {
        val safe = api(id = "b", baseUrl = "https://b.example.com", documentationUrl = "https://b.example.com/docs", endpoints = listOf(ApiEndpoint("GET", "/health", parameters = listOf("verbose"), headers = listOf("Accept"), requestBody = "none", response = "HealthResponse", schema = "HealthResponse")))
        val safeA = api(id = "a", baseUrl = "https://a.example.com", documentationUrl = "https://a.example.com/docs", endpoints = listOf(ApiEndpoint("GET", "/health", parameters = listOf("verbose"), headers = listOf("Accept"), requestBody = "none", response = "HealthResponse", schema = "HealthResponse")))
        val engine = ApiDiscoveryEngine(sources = emptyList())
        assertEquals(ApiReviewDecision.APPROVE, engine.analyze(safe).review)
        assertEquals(listOf("a", "b"), engine.rank(listOf(safe, safeA)).map { it.api.id })
    }

    private fun api(
        id: String = "api-1",
        name: String = "Example API",
        baseUrl: String? = "https://example.com",
        documentationUrl: String? = null,
        description: String = "A public API",
        source: ApiSource = ApiSource.OFFICIAL_DOCS,
        endpoints: List<ApiEndpoint> = emptyList()
    ) = PublicApi(
        id = id,
        name = name,
        description = description,
        category = "Development",
        baseUrl = baseUrl,
        documentationUrl = documentationUrl,
        source = source,
        authentication = ApiAuthentication.NONE,
        https = true,
        endpoints = endpoints,
        capabilities = listOf("search")
    )
}
