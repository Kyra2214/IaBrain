package com.aibrain.app.brain

import android.content.Context
import android.net.Uri
import com.aibrain.app.data.local.AppDatabase
import com.aibrain.app.repository.CatalogoRepository

/** Motor único para obter e validar a URL atual; a UI não define endereços. */
class IAUrlResolver(context: Context) {
    private val catalogo = CatalogoRepository(context.applicationContext)
    private val database = AppDatabase.getInstance(context.applicationContext)

    suspend fun resolve(contract: IAOpenContract): IAOpenContract {
        val ia = runCatching { catalogo.carregarCatalogoSincronizado().firstOrNull { it.id == contract.selectedAIId } }.getOrNull()
        val url = ia?.site
        val valid = url?.let { runCatching { Uri.parse(it).scheme == "https" && !Uri.parse(it).host.isNullOrBlank() }.getOrDefault(false) } == true
        val capabilities = database.iaCapabilityDao().porIA(contract.selectedAIId)
        val prefill = when {
            capabilities.any { it.capacidade.uppercase() in PREFILL_CAPABILITIES && it.nivel > 0 } -> PrefillCapability.CONFIRMED
            capabilities.any { it.capacidade.uppercase() in PREFILL_CAPABILITIES } -> PrefillCapability.NOT_SUPPORTED
            else -> PrefillCapability.UNKNOWN
        }
        return contract.copy(
            officialResolvedUrl = if (valid) url else null,
            urlStatus = if (valid) UrlResolutionStatus.RESOLVED else if (url == null) UrlResolutionStatus.NOT_FOUND else UrlResolutionStatus.INVALID,
            prefillCapability = prefill,
            canPrefillPrompt = prefill == PrefillCapability.CONFIRMED,
            openMode = if (prefill == PrefillCapability.CONFIRMED) BrowserOpenMode.PREFILL_ONLY else BrowserOpenMode.OPEN_ONLY
        )
    }

    companion object {
        private val PREFILL_CAPABILITIES = setOf("PREFILL_PROMPT", "PREENCHIMENTO_PROMPT")
    }
}
