package com.aibrain.app.brain

import java.security.MessageDigest
import java.util.zip.ZipFile

/** Local, deterministic intelligence layer for the ZIP-driven software factory. */
object SoftwareFactoryV3 {

    data class ZipFileInfo(
        val path: String,
        val size: Long,
        val sha256: String,
        val language: String,
        val critical: Boolean
    )

    data class ZipSnapshot(
        val artifactId: String,
        val taskId: String,
        val files: List<ZipFileInfo>,
        val added: List<String>,
        val modified: List<String>,
        val deleted: List<String>,
        val findings: List<String>,
        val safe: Boolean
    )

    data class MergePlan(
        val files: List<String>,
        val conflicts: List<String>,
        val safeToMerge: Boolean,
        val rollbackRequired: Boolean
    )

    data class ContractChange(
        val symbol: String,
        val kind: String,
        val breaking: Boolean,
        val reason: String
    )

    data class Review(
        val score: Int,
        val findings: List<String>,
        val approved: Boolean
    )

    data class ValidationTask(
        val id: String,
        val kind: Kind,
        val description: String,
        val blocking: Boolean
    ) {
        enum class Kind { UNIT, INTEGRATION, REGRESSION, SECURITY, COMPATIBILITY }
    }

    data class RepairTask(
        val id: String,
        val cause: String,
        val instructions: String,
        val preferredCapabilities: Set<String>
    )

    data class AiProfile(
        val aiId: String,
        val taskType: String,
        val successes: Int,
        val failures: Int,
        val regressions: Int,
        val averageSeconds: Double,
        val averageQuality: Double
    )

    data class MemorySnapshot(
        val architecture: Set<String>,
        val decisions: List<String>,
        val conventions: Set<String>,
        val contracts: Set<String>,
        val knownErrors: List<String>,
        val aiHistory: List<AiProfile>
    )

    data class FactoryRequest(val projectId: String, val objective: String)
    data class CouncilVerdict(val approved: Boolean, val score: Int, val votes: Map<String, Boolean>, val reasons: List<String>)
    data class FactoryResult(
        val projectId: String,
        val mergePlan: MergePlan,
        val validations: List<ValidationTask>,
        val council: CouncilVerdict,
        val repairTask: RepairTask?,
        val memory: MemorySnapshot
    )

    fun analyzeZip(artifactId: String, taskId: String, zipPath: String): ZipSnapshot {
        val files = mutableListOf<ZipFileInfo>()
        val findings = mutableListOf<String>()
        ZipFile(zipPath).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) return@forEach
                val normalized = normalize(entry.name)
                if (normalized == null) {
                    findings += "Unsafe ZIP path: ${entry.name}"
                    return@forEach
                }
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                files += ZipFileInfo(normalized, entry.size, sha256(bytes), detectLanguage(normalized), isCritical(normalized))
            }
        }
        return ZipSnapshot(artifactId, taskId, files, files.map { it.path }, emptyList(), emptyList(), findings, findings.isEmpty())
    }

    fun planMerge(snapshots: List<ZipSnapshot>): MergePlan {
        val grouped = snapshots.flatMap { it.files }.groupBy { it.path }
        val conflicts = grouped.filterValues { files -> files.map { it.sha256 }.distinct().size > 1 }.keys.sorted()
        val files = grouped.keys.sorted()
        return MergePlan(files, conflicts, conflicts.isEmpty() && snapshots.all { it.safe }, conflicts.isNotEmpty())
    }

    fun generateValidationTasks(projectId: String, files: List<String>, contracts: List<ContractChange>): List<ValidationTask> = listOf(
        ValidationTask("$projectId-unit", ValidationTask.Kind.UNIT, "Validar unidades afetadas: ${files.size} arquivos", true),
        ValidationTask("$projectId-integration", ValidationTask.Kind.INTEGRATION, "Validar integração entre módulos", true),
        ValidationTask("$projectId-regression", ValidationTask.Kind.REGRESSION, "Validar regressões do contrato", true),
        ValidationTask("$projectId-security", ValidationTask.Kind.SECURITY, "Validar segurança das alterações", true),
        ValidationTask("$projectId-compatibility", ValidationTask.Kind.COMPATIBILITY, "Validar ${contracts.count { it.breaking }} mudanças potencialmente incompatíveis", true)
    )

    fun createRepairTask(projectId: String, cause: String, taskType: String): RepairTask = RepairTask(
        id = "repair-$projectId-${taskType.lowercase()}",
        cause = cause,
        instructions = "Corrigir somente a causa identificada e devolver novo ZIP; não alterar a main.",
        preferredCapabilities = setOf("CODIGO", "DEBUG")
    )

    fun reviewCouncil(review: Review): CouncilVerdict {
        val votes = mapOf(
            "architecture" to (review.score >= 60),
            "security" to (review.score >= 80),
            "performance" to (review.score >= 60),
            "testing" to (review.score >= 70),
            "compatibility" to (review.score >= 75)
        )
        val approved = votes.values.all { it } && review.approved
        return CouncilVerdict(approved, review.score, votes, if (approved) emptyList() else listOf("Council veto: one or more specialist gates failed"))
    }

    /** v2.7: choose an AI using success, quality, regressions and speed. */
    fun rankAIs(taskType: String, profiles: List<AiProfile>): List<AiProfile> = profiles
        .filter { it.taskType.equals(taskType, true) || taskType == "ANY" }
        .sortedWith(compareByDescending<AiProfile> {
            (it.averageQuality.coerceIn(0.0, 1.0) * 0.5) +
                (it.successes.toDouble() / (it.successes + it.failures + 1)) * 0.3 -
                (it.regressions.toDouble() / (it.successes + it.failures + 1)) * 0.2
        }.thenBy { it.averageSeconds }.thenBy { it.aiId })

    /** v2.8: update memory immutably so every run can be audited locally. */
    fun updateMemory(memory: MemorySnapshot, review: Review, contracts: List<ContractChange>, error: String? = null): MemorySnapshot = memory.copy(
        decisions = (memory.decisions + "review-score=${review.score}").takeLast(100),
        contracts = (memory.contracts + contracts.map { it.symbol }).takeLast(200).toSet(),
        knownErrors = (memory.knownErrors + listOfNotNull(error)).takeLast(100)
    )

    /** v3.0 unified entry point. ZIPs are analyzed together; tests remain an external final gate. */
    fun plan(request: FactoryRequest, snapshots: List<ZipSnapshot>, review: Review, contracts: List<ContractChange>, memory: MemorySnapshot): FactoryResult {
        val merge = planMerge(snapshots)
        val validations = generateValidationTasks(request.projectId, merge.files, contracts)
        val council = reviewCouncil(review)
        val accepted = merge.safeToMerge && council.approved
        val repair = if (accepted) null else createRepairTask(request.projectId, (merge.conflicts + review.findings).joinToString("; "), "INTEGRATION")
        return FactoryResult(request.projectId, merge, validations, council, repair, updateMemory(memory, review, contracts, repair?.cause))
    }

    private fun normalize(path: String): String? {
        if (path.isBlank() || path.startsWith("/") || path.contains('\\')) return null
        val parts = path.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." }) return null
        return parts.joinToString("/")
    }

    private fun detectLanguage(path: String): String = when {
        path.endsWith(".kt") -> "KOTLIN"
        path.endsWith(".java") -> "JAVA"
        path.endsWith(".xml") -> "XML"
        path.endsWith(".json") -> "JSON"
        path.endsWith(".gradle") || path.endsWith(".gradle.kts") -> "GRADLE"
        path.endsWith(".js") -> "JAVASCRIPT"
        path.endsWith(".ts") -> "TYPESCRIPT"
        path.endsWith(".py") -> "PYTHON"
        else -> "OTHER"
    }

    private fun isCritical(path: String): Boolean = path.endsWith("AndroidManifest.xml") || path.contains("build.gradle") || path.contains("settings.gradle")
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
