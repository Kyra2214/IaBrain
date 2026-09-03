package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionSecurityPolicyTest {
    @Test fun itemValidoEPermitido() {
        val item = ProjectWorkPlanner.build(ProjectBuilder.build("Criar aplicativo")).workItems.first()
        val result = ExecutionSecurityPolicy.validateWorkItem(item)
        assertEquals(ExecutionSecurityPolicy.Status.SAFE, result.status)
        assertTrue(result.allowed)
    }

    @Test fun segredoNoPromptBloqueiaExecucao() {
        val result = ExecutionSecurityPolicy.scanPrompt("Use password=superSecret123 no arquivo")
        assertEquals(ExecutionSecurityPolicy.Status.BLOCKED, result.status)
        assertFalse(result.allowed)
    }

    @Test fun promptNormalPermanecePermitido() {
        val result = ExecutionSecurityPolicy.scanPrompt("Implemente a função de login sem incluir credenciais reais.")
        assertTrue(result.allowed)
    }
}
