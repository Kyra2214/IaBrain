package com.aibrain.app.browser

/**
 * v7.1-v7.10 browser runtime boundary.
 * Keeps browser execution state separate from WebView implementation and never fabricates output.
 */
class BrowserRuntimeCoordinator(
    private val sessions: BrowserRuntimeSessionStoreFacade = BrowserRuntimeSessionStoreFacade()
) {
    fun open(requestId: String, aiId: String, aiName: String, url: String, prompt: String) {
        require(requestId.isNotBlank())
        require(aiId.isNotBlank())
        require(aiName.isNotBlank())
        require(url.startsWith("https://", ignoreCase = true))
        require(prompt.isNotBlank())
        sessions.opened(BrowserRuntimeSession(requestId, aiId, aiName, url, prompt, BrowserRuntimeSessionState.OPENING))
    }

    fun pageReady(requestId: String) = sessions.update(requestId) {
        it.copy(state = BrowserRuntimeSessionState.PAGE_READY)
    }

    fun prefillSucceeded(requestId: String) = sessions.update(requestId) {
        it.copy(state = BrowserRuntimeSessionState.PREFILL_SUCCEEDED)
    }

    fun waitingForUser(requestId: String) = sessions.update(requestId) {
        it.copy(state = BrowserRuntimeSessionState.WAITING_FOR_USER)
    }

    /** Only a real browser adapter/UI callback may supply output. */
    fun complete(requestId: String, output: String) = sessions.complete(requestId, output)

    fun fail(requestId: String, errorCode: String) = sessions.fail(requestId, errorCode)

    fun get(requestId: String): BrowserRuntimeSession? = sessions.get(requestId)
}

/** Small facade keeps the coordinator independently testable without coupling it to Android. */
class BrowserRuntimeSessionStoreFacade {
    fun opened(session: BrowserRuntimeSession) = BrowserRuntimeSessionStore.opened(session)
    fun update(requestId: String, transform: (BrowserRuntimeSession) -> BrowserRuntimeSession) =
        BrowserRuntimeSessionStore.update(requestId, transform)
    fun get(requestId: String) = BrowserRuntimeSessionStore.get(requestId)
    fun complete(requestId: String, output: String) = BrowserRuntimeSessionStore.complete(requestId, output)
    fun fail(requestId: String, errorCode: String) = BrowserRuntimeSessionStore.fail(requestId, errorCode)
}
