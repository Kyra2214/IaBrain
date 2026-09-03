package com.aibrain.app.brain

import java.util.UUID

/** Alvo explícito e autorizado; o adapter não aceita alvo implícito derivado de texto. */
data class StrixTarget(
    val value: String,
    val kind: Kind,
    val authorizationReference: String,
    val scope: String
) {
    enum class Kind { LOCAL_DIRECTORY, GITHUB_REPOSITORY, WEB_APPLICATION, API_SPEC }
    init {
        require(value.isNotBlank()) { "Alvo Strix não pode ser vazio" }
        require(authorizationReference.isNotBlank()) { "Referência de autorização é obrigatória" }
        require(scope.isNotBlank()) { "Escopo é obrigatório" }
    }
}

data class StrixPolicy(
    val allowNetwork: Boolean = false,
    val allowExploitValidation: Boolean = false,
    val requireHumanApproval: Boolean = true,
    val maxDurationMinutes: Int = 30,
    val maxFindings: Int = 100
) {
    init { require(maxDurationMinutes in 1..240 && maxFindings in 1..10_000) }
}

data class StrixAssessmentRequest(
    val id: String = UUID.randomUUID().toString(),
    val target: StrixTarget,
    val policy: StrixPolicy,
    val instruction: String = "",
    val requestedCapabilities: Set<String> = setOf("SECURITY_TESTING")
)

data class StrixFinding(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val severity: Severity,
    val owaspCategory: String?,
    val cvssScore: Double?,
    val evidence: String,
    val remediation: String? = null,
    val validated: Boolean = false
) {
    enum class Severity { INFO, LOW, MEDIUM, HIGH, CRITICAL }
    init {
        require(title.isNotBlank() && evidence.isNotBlank())
        require(cvssScore == null || cvssScore in 0.0..10.0)
    }
}

data class StrixReport(
    val assessmentId: String,
    val status: Status,
    val findings: List<StrixFinding> = emptyList(),
    val errors: List<String> = emptyList()
) {
    enum class Status { NOT_STARTED, RUNNING, SUCCEEDED, FAILED, BLOCKED }
    init { require(findings.size <= 10_000) }
}

/** Política de escopo para o provider de segurança; falha fechada. */
object StrixSecurityPolicy {
    fun authorize(request: StrixAssessmentRequest): List<String> {
        val errors = mutableListOf<String>()
        if (request.target.authorizationReference.length > 300) errors += "Referência de autorização inválida"
        if (request.target.scope.length > 2_000) errors += "Escopo excede limite"
        if (!request.policy.allowNetwork && request.target.kind in setOf(StrixTarget.Kind.WEB_APPLICATION, StrixTarget.Kind.GITHUB_REPOSITORY, StrixTarget.Kind.API_SPEC)) errors += "Alvo de rede requer allowNetwork=true"
        if (!request.policy.allowExploitValidation) errors += "Validação de exploração requer autorização explícita"
        if (request.instruction.isNotBlank() && !ExecutionSecurityPolicy.scanPrompt(request.instruction).allowed) errors += "Instrução contém possível segredo ou conteúdo bloqueado"
        return errors
    }
}

/** Contrato para execução externa. A implementação padrão deliberadamente não executa processos. */
interface StrixRunner {
    suspend fun assess(request: StrixAssessmentRequest): StrixReport
}

/** Adapter do Strix para o Provider Gateway. Não instala, não chama shell e não baixa dependências. */
class StrixAdapter(private val runner: StrixRunner) : ProviderGateway {
    override suspend fun execute(request: ProviderRequest): ProviderResponse {
        return ProviderResponse(false, error = "StrixAdapter requer integração de alvo por SecurityAssessmentRequest")
    }

    suspend fun assess(request: StrixAssessmentRequest): StrixReport {
        val errors = StrixSecurityPolicy.authorize(request)
        if (errors.isNotEmpty()) return StrixReport(request.id, StrixReport.Status.BLOCKED, errors = errors)
        val report = runner.assess(request)
        return report.copy(findings = report.findings.take(request.policy.maxFindings))
    }
}

/** Runner seguro para a primeira fase: gera estado bloqueado, nunca executa Strix real. */
class NonExecutingStrixRunner : StrixRunner {
    override suspend fun assess(request: StrixAssessmentRequest): StrixReport = StrixReport(
        assessmentId = request.id,
        status = StrixReport.Status.NOT_STARTED,
        errors = listOf("Strix real não configurado; nenhuma ferramenta foi executada")
    )
}

object StrixFindingValidator {
    fun validate(report: StrixReport): List<String> {
        val errors = mutableListOf<String>()
        report.findings.forEach { finding ->
            if (finding.validated && finding.evidence.length < 20) errors += "Finding ${finding.id} marcado como validado sem evidência suficiente"
            if (finding.severity == StrixFinding.Severity.CRITICAL && (finding.cvssScore == null || finding.cvssScore < 9.0)) errors += "Finding crítico precisa de CVSS >= 9.0"
        }
        return errors
    }
}
