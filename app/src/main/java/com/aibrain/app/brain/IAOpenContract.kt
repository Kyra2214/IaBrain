package com.aibrain.app.brain

enum class UrlResolutionStatus { RESOLVED, NOT_FOUND, INVALID }
enum class BrowserOpenMode { OPEN_ONLY }

data class IAOpenContract(
    val selectedAIId: String,
    val selectedAIName: String,
    val officialResolvedUrl: String?,
    val urlStatus: UrlResolutionStatus,
    val generatedPrompt: String,
    val canPrefillPrompt: Boolean = false,
    val openMode: BrowserOpenMode = BrowserOpenMode.OPEN_ONLY
)
