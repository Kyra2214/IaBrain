package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IaBrainStableContractTest {
    @Test fun preparaProjetoCompletoProntoParaIntegracao() {
        val result = IaBrainStableContract.prepare("Criar aplicativo Android para tarefas")
        assertEquals("1.0.0", IaBrainStableContract.VERSION)
        assertEquals("0.2.0", IaBrainStableContract.MIN_COMPATIBLE_VERSION)
        assertEquals(4, result.project.functions.size)
        assertEquals(4, result.workPlan.workItems.size)
        assertTrue(result.qualityGate.passed)
        assertTrue(result.security.all { it.allowed })
        assertTrue(result.readyForIntegration)
    }

    @Test(expected = IllegalArgumentException::class)
    fun objetivoVazioNaoEntraNoContratoEstavel() {
        IaBrainStableContract.prepare("   ")
    }
}
