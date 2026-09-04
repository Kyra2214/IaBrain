package com.aibrain.app.brain

import org.junit.Assert.*
import org.junit.Test

class AiApiIntelligenceTest {
    private val models = listOf(
        AiApiModel("qwen", "qwen-test", "Qwen", setOf("chat", "coding"), AiApiAccess.FREE_TIER, "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", "https://platform.qianwenai.com/docs/api-reference/chat/dashscope"),
        AiApiModel("glm", "glm-test", "GLM", setOf("chat", "coding"), AiApiAccess.FREE_TIER, "https://api.z.ai/api/paas/v4", "https://docs.z.ai")
    )

    @Test fun safeHttpsRejectsPrivateAndCredentialUrls() {
        assertTrue(AiApiSecurityPolicy.isSafeHttps("https://example.com/v1"))
        assertFalse(AiApiSecurityPolicy.isSafeHttps("http://example.com/v1"))
        assertFalse(AiApiSecurityPolicy.isSafeHttps("https://user:pass@example.com/v1"))
        assertFalse(AiApiSecurityPolicy.isSafeHttps("https://127.0.0.1/v1"))
        assertFalse(AiApiSecurityPolicy.isSafeHttps("https://localhost/v1"))
    }

    @Test fun routerOnlyUsesVerifiedCompatibleModels() {
        val verified = listOf(AiApiVerification("qwen", "qwen-test", AiApiAvailability.VERIFIED, 200, 100, 10L))
        val route = AiApiRouter().route(models, verified, setOf("chat", "coding"), freeOnly = true)
        assertEquals("qwen-test", route.selected?.modelId)
        assertTrue(route.alternatives.isEmpty())
    }

    @Test fun quotaPolicyCanAskOrSwitch() {
        val engine = AiApiProjectPolicyEngine()
        val ask = engine.decide(AiApiProjectPolicy.ASK_BEFORE_SWITCH, models, false, AiApiFailure.QUOTA)
        assertTrue(ask.shouldAskUser)
        assertFalse(ask.shouldSwitch)
        val automatic = engine.decide(AiApiProjectPolicy.AUTO_SWITCH_COMPATIBLE, models, false, AiApiFailure.QUOTA)
        assertFalse(automatic.shouldAskUser)
        assertTrue(automatic.shouldSwitch)
    }

    @Test fun browserRemainsFallback() {
        val decision = AiApiProjectPolicyEngine().decide(AiApiProjectPolicy.ALLOW_BROWSER_FALLBACK, emptyList(), true, AiApiFailure.AUTH)
        assertFalse(decision.shouldSwitch)
        assertTrue(decision.reason.contains("navegador"))
    }

    @Test fun twentyFourHourTtlIsDeterministic() {
        assertTrue(AiApiSyncPolicy.isStale(0L, 100L))
        assertFalse(AiApiSyncPolicy.isStale(100L, 100L + AiApiSyncPolicy.TTL_MS - 1L))
        assertTrue(AiApiSyncPolicy.isStale(100L, 100L + AiApiSyncPolicy.TTL_MS))
    }
}
