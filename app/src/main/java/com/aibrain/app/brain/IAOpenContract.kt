package com.aibrain.app.brain

enum class UrlResolutionStatus { RESOLVED, NOT_FOUND, INVALID }
enum class BrowserOpenMode { OPEN_ONLY, PREFILL_ONLY }
enum class PrefillCapability { CONFIRMED, NOT_SUPPORTED, UNKNOWN }

data class IAOpenContract(
    val selectedAIId: String,
    val selectedAIName: String,
    val officialResolvedUrl: String?,
    val urlStatus: UrlResolutionStatus,
    val generatedPrompt: String,
    val prefillCapability: PrefillCapability = PrefillCapability.UNKNOWN,
    val canPrefillPrompt: Boolean = prefillCapability == PrefillCapability.CONFIRMED,
    val openMode: BrowserOpenMode = BrowserOpenMode.OPEN_ONLY
) {
    init {
        require(openMode != BrowserOpenMode.PREFILL_ONLY || canPrefillPrompt) {
            "O modo de abertura nunca pode exigir pré-preenchimento"
        }
    }
}
