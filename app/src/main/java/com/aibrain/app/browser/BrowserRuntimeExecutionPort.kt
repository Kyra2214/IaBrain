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

/** v6.1/v6.3 Android bridge: sends the browser-first route to the existing in-app BrowserActivity. */
class BrowserRuntimeExecutionPort(private val context: Context) : RuntimeExecutionPort {
    override fun openBrowser(request: RuntimeRequest, candidate: RuntimeCandidate): RuntimeDispatchResult {
        val url = candidate.url ?: return RuntimeDispatchResult.Failure("BROWSER_URL_MISSING", false)
        val valid = runCatching {
            val uri = URI(url.trim())
            uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
        }.getOrDefault(false)
        if (!valid) return RuntimeDispatchResult.Failure("BROWSER_URL_INVALID", false)

        val contract = IAOpenContract(
            selectedAIId = candidate.id,
            selectedAIName = candidate.name,
            officialResolvedUrl = url.trim(),
            urlStatus = UrlResolutionStatus.RESOLVED,
            generatedPrompt = request.prompt
        )
        context.startActivity(BrowserActivity.criarIntent(context, contract).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return RuntimeDispatchResult.Browser(
            BrowserDispatch(
                requestId = request.id,
                aiId = candidate.id,
                aiName = candidate.name,
                url = url.trim(),
                prompt = request.prompt,
                prefillAttempted = false,
                awaitingUser = true
            )
        )
    }

    override fun executeApi(request: RuntimeRequest, candidate: RuntimeCandidate): RuntimeDispatchResult =
        RuntimeDispatchResult.Failure("API_ADAPTER_NOT_CONFIGURED", false)
}
