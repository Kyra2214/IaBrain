package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiApiLearningTest {
    @Test fun usageTrackerKeepsSuccessRateAndLatency() {
        val tracker=AiApiUsageTracker()
        tracker.record("m",true,100)
        tracker.record("m",false,300,true)
        val metric=tracker.snapshot().single()
        assertEquals(0.5,metric.successRate,0.001)
        assertEquals(200L,metric.averageLatencyMs)
        assertEquals(1,metric.quotaFailures)
    }

    @Test fun onlyVerifiedActiveModelsAreUsable() {
        val model=AiApiModel("p","m","Model",setOf("chat"),AiApiAccess.FREE_TIER,"https://example.com/v1","https://example.com/docs")
        val ok=AiApiCatalogPolicy().usable(model,AiApiVerification("p","m",AiApiAvailability.VERIFIED,200,10,1L))
        val bad=AiApiCatalogPolicy().usable(model,AiApiVerification("p","m",AiApiAvailability.UNKNOWN,200,10,1L))
        assertTrue(ok)
        assertEquals(false,bad)
    }
}
