package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class IAOpenContractTest {
    @Test fun contratoNaoPermitePreenchimentoOuEnvioAutomatico() {
        val contract = IAOpenContract("ia", "IA", "https://example.com", UrlResolutionStatus.RESOLVED, "prompt")
        assertEquals(BrowserOpenMode.OPEN_ONLY, contract.openMode)
        assertFalse(contract.canPrefillPrompt)
    }
}
