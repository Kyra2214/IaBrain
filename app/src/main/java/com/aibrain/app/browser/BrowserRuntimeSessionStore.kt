package com.aibrain.app.browser

import java.util.concurrent.ConcurrentHashMap

/** Runtime bridge for browser executions. It stores state only; it never fabricates AI output. */
enum class BrowserRuntimeSessionState { OPENING, PAGE_READY, PREFILL_SUCCEEDED, WAITING_FOR_USER, COMPLETED, FAILED }

data class BrowserRuntimeSession(
    val requestId: String,
    val aiId: String,
    val aiName: String,
    val url: String,
    val prompt: String,
    val state: BrowserRuntimeSessionState,
    val output: String? = null,
    val errorCode: String? = null
)

object BrowserRuntimeSessionStore {
    private val sessions = ConcurrentHashMap<String, BrowserRuntimeSession>()

    fun opened(session: BrowserRuntimeSession) { sessions[session.requestId] = session }

    fun update(requestId: String, transform: (BrowserRuntimeSession) -> BrowserRuntimeSession): BrowserRuntimeSession? =
        sessions.computeIfPresent(requestId) { _, current -> transform(current) }

    fun get(requestId: String): BrowserRuntimeSession? = sessions[requestId]

    /** Called by a verified browser adapter/result callback only. No output is generated here. */
    fun complete(requestId: String, output: String): BrowserRuntimeSession? {
        require(output.isNotBlank())
        return update(requestId) { it.copy(state = BrowserRuntimeSessionState.COMPLETED, output = output, errorCode = null) }
    }

    fun fail(requestId: String, errorCode: String): BrowserRuntimeSession? =
        update(requestId) { it.copy(state = BrowserRuntimeSessionState.FAILED, errorCode = errorCode) }

    fun clear(requestId: String) { sessions.remove(requestId) }
}
