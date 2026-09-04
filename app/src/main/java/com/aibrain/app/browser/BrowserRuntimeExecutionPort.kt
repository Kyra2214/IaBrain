package com.aibrain.app.browser

import android.content.Context
import android.content.Intent
import com.aibrain.app.brain.BrowserDispatch
import com.aibrain.app.brain.IAOpenContract
import com.aibrain.app.brain.RuntimeCandidate
import com.aibrain.app.brain.RuntimeDispatchResult
import com.aibrain.app.brain.RuntimeExecutionPort
import com.aibrain.app.brain.RuntimeRequest
import com.aibrain.app.brain.UrlResolutionStatus
import java.net.URI

/** v7.1 bridge: opens the existing native BrowserActivity and registers a real runtime session. */
class BrowserRuntimeExecutionPort(private val context: Context) : RuntimeExecutionPort {
    override fun openBrowser(request: RuntimeRequest, candidate: RuntimeCandidate): RuntimeDispatchResult {
        val url = candidate.url ?: return RuntimeDispatchResult.Failure("BROWSER_URL_MISSING", false)
        val normalizedUrl = url.trim()
        val valid = runCatching {
            val uri = URI(normalizedUrl)
            uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
        }.getOrDefault(false)
        if (!valid) return RuntimeDispatchResult.Failure("BROWSER_URL_INVALID", false)

        BrowserRuntimeSessionStore.opened(
            BrowserRuntimeSession(
                requestId = request.id,
                aiId = candidate.id,
                aiName = candidate.name,
                url = normalizedUrl,
                prompt = request.prompt,
                state = BrowserRuntimeSessionState.OPENING
            )
        )

        val contract = IAOpenContract(
            selectedAIId = candidate.id,
            selectedAIName = candidate.name,
            officialResolvedUrl = normalizedUrl,
            urlStatus = UrlResolutionStatus.RESOLVED,
            generatedPrompt = request.prompt
        )
        val intent = BrowserActivity.criarIntent(context, contract).apply {
            putExtra(BrowserActivity.EXTRA_RUNTIME_REQUEST_ID, request.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        BrowserRuntimeSessionStore.update(request.id) {
            it.copy(state = BrowserRuntimeSessionState.WAITING_FOR_USER)
        }

        return RuntimeDispatchResult.Browser(
            BrowserDispatch(
                requestId = request.id,
                aiId = candidate.id,
                aiName = candidate.name,
                url = normalizedUrl,
                prompt = request.prompt,
                prefillAttempted = false,
                awaitingUser = true
            )
        )
    }

    override fun executeApi(request: RuntimeRequest, candidate: RuntimeCandidate): RuntimeDispatchResult =
        RuntimeDispatchResult.Failure("API_ADAPTER_NOT_CONFIGURED", false)
}
