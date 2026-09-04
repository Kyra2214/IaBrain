package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IaBrainRuntimeV61To70Test {
    private val browser = RuntimeCandidate("browser-ai", "Browser AI", ExecutionChannel.BROWSER, setOf("chat"), quality = .7, reliability = .8, speed = .7, url = "https://ai.example.com")
    private val api = RuntimeCandidate("free-api", "Free API", ExecutionChannel.API, setOf("chat"), quality = .95, reliability = .95, speed = .95, cost = 0.0)

    @Test
    fun browser_is_primary_even_when_api_scores_higher() {
        val request = RuntimeRequest("r1", "/ask", "hello", requiredCapabilities = setOf("chat"))
        val route = BrowserFirstRouter().route(request, listOf(api, browser))
        assertEquals(ExecutionChannel.BROWSER, route.channel)
        assertEquals("browser-ai", route.selected?.id)
        assertEquals("BROWSER_FIRST", route.reason)
    }

    @Test
    fun api_only_explicitly_bypasses_browser_priority() {
        val request = RuntimeRequest("r2", "/ask", "hello", apiOnly = true, requiredCapabilities = setOf("chat"))
        val route = BrowserFirstRouter().route(request, listOf(browser, api))
        assertEquals(ExecutionChannel.API, route.channel)
        assertEquals("free-api", route.selected?.id)
        assertEquals("API_FALLBACK", route.reason)
    }

    @Test
    fun runtime_opens_browser_and_waits_without_faking_output() {
        val port = object : RuntimeExecutionPort {
            override fun openBrowser(request: RuntimeRequest, candidate: RuntimeCandidate) = RuntimeDispatchResult.Browser(
                BrowserDispatch(request.id, candidate.id, candidate.name, candidate.url!!, request.prompt, false, true)
            )
            override fun executeApi(request: RuntimeRequest, candidate: RuntimeCandidate) = RuntimeDispatchResult.Failure("UNEXPECTED_API", false)
        }
        val runtime = IaBrainAutonomousRuntime(checkpointStore = InMemoryRuntimeCheckpointStore())
        val result = runtime.start(RuntimeRequest("r3", "/ask", "hello", requiredCapabilities = setOf("chat")), listOf(api, browser), port)
        assertEquals(RuntimeStage.WAITING_FOR_BROWSER, result.stage)
        assertTrue(result.dispatch is RuntimeDispatchResult.Browser)
        assertTrue(result.checkpoint.evidence.contains("BROWSER_OPENED"))
    }

    @Test
    fun recovery_does_not_dispatch_browser_twice() {
        var opens = 0
        val port = object : RuntimeExecutionPort {
            override fun openBrowser(request: RuntimeRequest, candidate: RuntimeCandidate): RuntimeDispatchResult {
                opens++
                return RuntimeDispatchResult.Browser(BrowserDispatch(request.id, candidate.id, candidate.name, candidate.url!!, request.prompt, false, true))
            }
            override fun executeApi(request: RuntimeRequest, candidate: RuntimeCandidate) = RuntimeDispatchResult.Failure("UNEXPECTED_API", false)
        }
        val store = InMemoryRuntimeCheckpointStore()
        val runtime = IaBrainAutonomousRuntime(checkpointStore = store)
        val request = RuntimeRequest("r4", "/ask", "hello", requiredCapabilities = setOf("chat"))
        runtime.start(request, listOf(browser), port)
        val recovered = runtime.recover(request, listOf(browser), port)
        assertEquals(1, opens)
        assertEquals(RuntimeStage.WAITING_FOR_BROWSER, recovered.stage)
        assertEquals(null, recovered.dispatch)
    }

    @Test
    fun browser_result_is_evaluated_and_remembered() {
        val store = InMemoryRuntimeCheckpointStore()
        val memory = AdaptiveMemory()
        val runtime = IaBrainAutonomousRuntime(checkpointStore = store, memory = memory)
        val port = object : RuntimeExecutionPort {
            override fun openBrowser(request: RuntimeRequest, candidate: RuntimeCandidate) = RuntimeDispatchResult.Browser(BrowserDispatch(request.id, candidate.id, candidate.name, candidate.url!!, request.prompt, false, true))
            override fun executeApi(request: RuntimeRequest, candidate: RuntimeCandidate) = RuntimeDispatchResult.Failure("UNEXPECTED_API", false)
        }
        val request = RuntimeRequest("r5", "/ask", "write tests", requiredCapabilities = setOf("chat"))
        runtime.start(request, listOf(browser), port)
        val result = runtime.continueFromBrowser(request, listOf(browser), "write tests passed", setOf("tests", "passed"))
        assertEquals(RuntimeStage.COMPLETED, result.stage)
        assertTrue(result.evaluation?.success == true)
        assertTrue(memory.recall("runtime:r5") != null)
    }

    @Test
    fun protected_publication_requires_human_approval() {
        val store = InMemoryRuntimeCheckpointStore()
        val runtime = IaBrainAutonomousRuntime(checkpointStore = store)
        val request = RuntimeRequest("r6", "/ask", "publish")
        val port = object : RuntimeExecutionPort {
            override fun openBrowser(request: RuntimeRequest, candidate: RuntimeCandidate) = RuntimeDispatchResult.Browser(BrowserDispatch(request.id, candidate.id, candidate.name, candidate.url!!, request.prompt, false, true))
            override fun executeApi(request: RuntimeRequest, candidate: RuntimeCandidate) = RuntimeDispatchResult.Api(ApiDispatch(request.id, candidate.id, "done"))
        }
        runtime.start(request, listOf(browser), port)
        val waiting = runtime.authorizePublication("r6", false)
        assertEquals(RuntimeStage.HUMAN_APPROVAL, waiting.stage)
        assertTrue(waiting.requiresHumanApproval)
        assertFalse(waiting.checkpoint.evidence.contains("HUMAN_APPROVAL_GRANTED"))
        val approved = runtime.authorizePublication("r6", true)
        assertEquals(RuntimeStage.COMPLETED, approved.stage)
        assertTrue(approved.checkpoint.evidence.contains("HUMAN_APPROVAL_GRANTED"))
    }
}
