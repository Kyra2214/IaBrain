package com.aibrain.app.brain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubQualityGateContractTest {
    @Test fun mergeSoPodeAcontecerComGateVerde() {
        val gate = ProjectQualityGate.validate(ProjectWorkPlanner.build(ProjectBuilder.build("Criar aplicativo")))
        assertTrue(gate.passed)
        assertTrue(GitHubIntegrationPolicy.canMerge(gate))
    }

    @Test fun gateVermelhoBloqueiaMerge() {
        val gate = ProjectQualityGate.validate(ProjectWorkPlanner.build(ProjectBuilder.build("Criar aplicativo"), "develop"))
        assertFalse(gate.passed)
        assertFalse(GitHubIntegrationPolicy.canMerge(gate))
    }
}
