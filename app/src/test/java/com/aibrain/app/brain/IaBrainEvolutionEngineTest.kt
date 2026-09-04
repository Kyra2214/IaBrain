package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IaBrainEvolutionEngineTest {
    private val engine = IaBrainEvolutionEngine()

    @Test
    fun discovery_normalizes_deduplicates_and_rejects_non_https() {
        val source = object : DiscoverySource {
            override val name = "test"
            override val priority = 10
            override fun load() = listOf(
                DiscoveryCandidate("a", "  Alpha   AI ", "HTTPS://EXAMPLE.COM/api/", categories = setOf(" Code "), source = ""),
                DiscoveryCandidate("a2", "Alpha AI", "https://example.com/api", source = ""),
                DiscoveryCandidate("bad", "Bad", "http://example.com", source = "")
            )
        }
        val report = engine.discover(listOf(source))
        assertEquals(1, report.entries.size)
        assertEquals(1, report.rejected.size)
        assertEquals(1, report.duplicatesRemoved)
        assertEquals("Alpha AI", report.entries.single().name)
        assertEquals("https://example.com/api", report.entries.single().url)
    }

    @Test
    fun sync_updates_and_preserves_user_sets() {
        val current = CatalogEntry("a", "Alpha", "https://example.com", updatedAt = 1)
        val incoming = current.copy(description = "new", updatedAt = 2)
        val result = engine.sync(listOf(current), listOf(incoming), setOf("a"), setOf("a"))
        assertEquals(1, result.updated)
        assertEquals(setOf("a"), result.favoritesPreserved)
        assertEquals(setOf("a"), result.historyPreserved)
    }

    @Test
    fun routing_selects_best_compatible_provider_and_alternatives() {
        val request = ExecutionRequest("/research", "hello", requiredCapabilities = setOf("web"))
        val first = Provider("p1", "One", setOf("web"), quality = .9, reliability = .9)
        val second = Provider("p2", "Two", setOf("web"), quality = .8, reliability = .8)
        val decision = engine.route(request, listOf(second, first))
        assertTrue(decision.selected)
        assertEquals("p1", decision.provider?.id)
        assertEquals(listOf("p2"), decision.alternatives.map { it.id })
    }

    @Test
    fun orchestration_never_executes_without_a_real_executor() {
        val request = ExecutionRequest("/research", "hello", requiredCapabilities = setOf("web"))
        val provider = Provider("p1", "One", setOf("web"))
        val result = engine.orchestrate(request, listOf(provider), null)
        assertEquals(OrchestrationResult.Status.FAILED, result.status)
        assertEquals("EXECUTOR_NOT_CONFIGURED", result.errorCode)
    }
}
