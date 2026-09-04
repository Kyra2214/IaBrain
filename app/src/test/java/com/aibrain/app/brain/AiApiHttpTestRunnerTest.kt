package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiApiHttpTestRunnerTest {
    private val model = AiApiModel("test", "model-1", "Test", setOf("chat"), AiApiAccess.FREE_TIER, "https://example.com/v1", "https://example.com/docs")

    @Test fun missingKeyDoesNotProbe() {
        val result = AiApiHttpTestRunner(transport = AiApiTransport { _, _, _ -> error("must not execute") }).test(model, null, 1L)
        assertEquals(AiApiAvailability.CREDENTIAL_REQUIRED, result.availability)
    }

    @Test fun successfulProbeBecomesVerified() {
        val result = AiApiHttpTestRunner(transport = AiApiTransport { _, _, _ -> AiApiTransportResult(200, "{\"choices\":[{\"message\":{\"content\":\"IaBrain OK\"}}]}", 42L) }).test(model, "test-key", 10L)
        assertEquals(AiApiAvailability.VERIFIED, result.availability)
        assertEquals(42L, result.latencyMs)
        assertTrue(result.evidence.contains("IaBrain OK"))
    }

    @Test fun quotaResponseIsDetected() {
        val result = AiApiHttpTestRunner(transport = AiApiTransport { _, _, _ -> AiApiTransportResult(429, "quota exceeded", 20L) }).test(model, "test-key", 10L)
        assertEquals(AiApiAvailability.QUOTA_EXHAUSTED, result.availability)
        assertEquals(AiApiFailure.QUOTA, result.error)
    }
}
