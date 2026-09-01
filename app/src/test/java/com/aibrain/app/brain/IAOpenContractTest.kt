package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class IAOpenContractTest {
    @Test fun contratoPermitePrefillSomenteComCapacidadeConfirmada() {
        val contract = IAOpenContract("ia", "IA", "https://example.com", UrlResolutionStatus.RESOLVED, "prompt", PrefillCapability.CONFIRMED, true, BrowserOpenMode.PREFILL_ONLY)
        assertEquals(PrefillCapability.CONFIRMED, contract.prefillCapability)
        assertEquals(BrowserOpenMode.PREFILL_ONLY, contract.openMode)
    }

    @Test fun contratoNaoPermitePreenchimentoOuEnvioAutomatico() {
        val contract = IAOpenContract("ia", "IA", "https://example.com", UrlResolutionStatus.RESOLVED, "prompt")
        assertEquals(BrowserOpenMode.OPEN_ONLY, contract.openMode)
        assertFalse(contract.canPrefillPrompt)
    }

    @Test fun capacidadeDesconhecidaMantemAberturaSomente() {
        val contract = IAOpenContract("ia", "IA", null, UrlResolutionStatus.NOT_FOUND, "prompt")
        assertFalse(contract.canPrefillPrompt)
        assertEquals(BrowserOpenMode.OPEN_ONLY, contract.openMode)
    }
}
