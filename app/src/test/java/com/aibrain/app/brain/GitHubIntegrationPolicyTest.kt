package com.aibrain.app.brain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubIntegrationPolicyTest {
    @Test fun mergePermitidoSomenteComQualityGateVerde() {
        val valid = ProjectQualityGate.validate(
            ProjectWorkPlanner.build(ProjectBuilder.build("Criar aplicativo"))
        )
        assertTrue(valid.passed)
        assertTrue(GitHubIntegrationPolicy.canMerge(valid))
    }

    @Test fun mergeBloqueadoQuandoPlanoNaoPassa() {
        val invalid = ProjectQualityGate.validate(
            ProjectWorkPlanner.build(ProjectBuilder.build("Criar aplicativo"), "develop")
        )
        assertFalse(invalid.passed)
        assertFalse(GitHubIntegrationPolicy.canMerge(invalid))
    }
}
