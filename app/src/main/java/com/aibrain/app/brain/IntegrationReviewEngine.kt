package com.aibrain.app.brain

/** Resultado da análise de integração antes do merge. */
object IntegrationReviewEngine {
    enum class Severity { INFO, WARNING, BLOCKER }
    enum class Category { ARCHITECTURE, SECURITY, COMPATIBILITY, PERFORMANCE, OWNERSHIP, TESTING }

    data class Finding(
        val category: Category,
        val severity: Severity,
        val path: String?,
        val message: String
    )

    data class Report(
        val findings: List<Finding>,
        val score: Int,
        val approved: Boolean
    )

    fun review(
        plan: ProjectWorkPlanner.Plan,
        analysis: ZipIntegrationEngine.Analysis,
        declaredFilesByFunction: Map<String, Set<String>> = emptyMap()
    ): Report {
        val findings = mutableListOf<Finding>()
        val functionById = plan.workItems.associateBy { it.functionId }

        analysis.conflicts.forEach { conflict ->
            val severity = if (conflict.type == ZipIntegrationEngine.ConflictType.CROSS_ARTIFACT_MODIFIED) Severity.BLOCKER else Severity.WARNING
            findings += Finding(Category.COMPATIBILITY, severity, conflict.path, conflict.message)
        }

        val ownership = mutableMapOf<String, String>()
        analysis.entries.forEach { entry ->
            val functionId = plan.workItems.firstOrNull { item ->
                declaredFilesByFunction[item.functionId]?.contains(entry.path) == true
            }?.functionId
            if (functionId != null) {
                val previous = ownership.putIfAbsent(entry.path, functionId)
                if (previous != null && previous != functionId) {
                    findings += Finding(Category.OWNERSHIP, Severity.BLOCKER, entry.path, "Arquivo declarado por duas funções: $previous e $functionId")
                }
            }
        }

        plan.workItems.forEach { item ->
            if (item.requiredCapabilities.contains("CODIGO") && item.acceptanceCriteria.isEmpty()) {
                findings += Finding(Category.TESTING, Severity.BLOCKER, null, "Função ${item.functionId} não possui critérios de aceitação")
            }
            if (functionById[item.functionId] == null) {
                findings += Finding(Category.ARCHITECTURE, Severity.BLOCKER, null, "Função não pertence ao plano")
            }
        }

        val blockers = findings.count { it.severity == Severity.BLOCKER }
        val warnings = findings.count { it.severity == Severity.WARNING }
        val score = (100 - blockers * 35 - warnings * 5).coerceIn(0, 100)
        return Report(findings, score, blockers == 0 && analysis.safe)
    }
}
