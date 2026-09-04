package com.aibrain.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BrowserRuntimeCoordinatorTest {
    private val coordinator = BrowserRuntimeCoordinator()
    private val id = "test-browser-runtime"

    private fun open() = coordinator.open(id, "chatgpt", "ChatGPT", "https://example.com", "responda ao teste")

    @Test fun `1 - opens real session`() {
        open()
        assertEquals(BrowserRuntimeSessionState.OPENING, coordinator.get(id)?.state)
        BrowserRuntimeSessionStore.clear(id)
    }

    @Test fun `2 - page ready advances state`() {
        open(); coordinator.pageReady(id)
        assertEquals(BrowserRuntimeSessionState.PAGE_READY, coordinator.get(id)?.state)
        BrowserRuntimeSessionStore.clear(id)
    }

    @Test fun `3 - prefill success is explicit`() {
        open(); coordinator.prefillSucceeded(id)
        assertEquals(BrowserRuntimeSessionState.PREFILL_SUCCEEDED, coordinator.get(id)?.state)
        BrowserRuntimeSessionStore.clear(id)
    }

    @Test fun `4 - waiting state is explicit`() {
        open(); coordinator.waitingForUser(id)
        assertEquals(BrowserRuntimeSessionState.WAITING_FOR_USER, coordinator.get(id)?.state)
        BrowserRuntimeSessionStore.clear(id)
    }

    @Test fun `5 - output is accepted only when supplied`() {
        open(); coordinator.complete(id, "resultado real")
        val session = coordinator.get(id)
        assertEquals(BrowserRuntimeSessionState.COMPLETED, session?.state)
        assertEquals("resultado real", session?.output)
        BrowserRuntimeSessionStore.clear(id)
    }

    @Test fun `6 - blank output is rejected`() {
        open()
        assertThrows(IllegalArgumentException::class.java) { coordinator.complete(id, " ") }
        BrowserRuntimeSessionStore.clear(id)
    }

    @Test fun `7 - failure is preserved`() {
        open(); coordinator.fail(id, "PAGE_LOAD_FAILED")
        assertEquals(BrowserRuntimeSessionState.FAILED, coordinator.get(id)?.state)
        assertEquals("PAGE_LOAD_FAILED", coordinator.get(id)?.errorCode)
        BrowserRuntimeSessionStore.clear(id)
    }

    @Test fun `8 - missing session cannot be silently created`() {
        BrowserRuntimeSessionStore.clear(id)
        assertNull(coordinator.pageReady(id))
        assertNull(coordinator.get(id))
    }

    @Test fun `9 - invalid URL is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            coordinator.open(id, "x", "X", "http://example.com", "prompt")
        }
        BrowserRuntimeSessionStore.clear(id)
    }

    @Test fun `10 - session keeps prompt and selected AI`() {
        open()
        val session = coordinator.get(id)
        assertNotNull(session)
        assertEquals("chatgpt", session?.aiId)
        assertEquals("ChatGPT", session?.aiName)
        assertEquals("responda ao teste", session?.prompt)
        BrowserRuntimeSessionStore.clear(id)
    }
}
