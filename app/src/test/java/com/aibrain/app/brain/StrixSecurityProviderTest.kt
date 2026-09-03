package com.aibrain.app.brain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrixSecurityProviderTest {
    private fun localRequest(policy: StrixPolicy = StrixPolicy(allowExploitValidation = true)) = StrixAssessmentRequest(
        target = StrixTarget("./app", StrixTarget.Kind.LOCAL_DIRECTORY, "ticket-sec-123", "somente app/src"),
        policy = policy
    )

    @Test fun alvoDeRedeSemRedeEExplicitamenteBloqueado() {
        val request = StrixAssessmentRequest(
            target = StrixTarget("https://example.invalid", StrixTarget.Kind.WEB_APPLICATION, "ticket-1", "somente example.invalid"),
            policy = StrixPolicy(allowExploitValidation = true)
        )
        assertTrue(StrixSecurityPolicy.authorize(request).any { it.contains("allowNetwork") })
    }

    @Test fun exploracaoExigeAprovacaoExplicita() {
        val errors = StrixSecurityPolicy.authorize(localRequest(StrixPolicy()))
        assertTrue(errors.any { it.contains("exploração") })
    }

    @Test fun instrucaoComSegredoERejeitada() {
        val request = localRequest().copy(instruction = "password=superSecret123")
        assertTrue(StrixSecurityPolicy.authorize(request).any { it.contains("segredo") })
    }

    @Test fun adapterNaoExecutaStrixPorPadrao() = runTest {
        val report = StrixAdapter(NonExecutingStrixRunner()).assess(localRequest())
        assertEquals(StrixReport.Status.NOT_STARTED, report.status)
        assertTrue(report.errors.single().contains("não configurado"))
    }

    @Test fun findingsCriticoPrecisaDeCvssAltoEEvidencia() {
        val finding = StrixFinding(title = "Falha", severity = StrixFinding.Severity.CRITICAL, owaspCategory = "A01", cvssScore = 7.5, evidence = "evidência curta", validated = true)
        val errors = StrixFindingValidator.validate(StrixReport("a", StrixReport.Status.SUCCEEDED, listOf(finding)))
        assertEquals(2, errors.size)
    }

    @Test fun adapterLimitaQuantidadeDeFindings() = runTest {
        val runner = object : StrixRunner {
            override suspend fun assess(request: StrixAssessmentRequest) = StrixReport(
                request.id, StrixReport.Status.SUCCEEDED,
                (1..5).map { StrixFinding(title = "f$it", severity = StrixFinding.Severity.LOW, owaspCategory = "A05", cvssScore = 2.0, evidence = "evidence-$it") }
            )
        }
        val report = StrixAdapter(runner).assess(localRequest(StrixPolicy(allowExploitValidation = true, maxFindings = 2)))
        assertEquals(2, report.findings.size)
    }
}
