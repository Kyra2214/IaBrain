package com.aibrain.app.brain

import java.security.MessageDigest
import java.util.zip.ZipFile

/** Local, deterministic intelligence layer for the ZIP-driven software factory. */
object SoftwareFactoryV3 {

    data class ZipFileInfo(val path: String, val size: Long, val sha256: String, val language: String, val critical: Boolean)
    data class ZipSnapshot(val artifactId: String, val taskId: String, val files: List<ZipFileInfo>, val added: List<String>, val modified: List<String>, val deleted: List<String>, val findings: List<String>, val safe: Boolean)
    data class MergePlan(val files: List<String>, val conflicts: List<String>, val safeToMerge: Boolean, val rollbackRequired: Boolean)
    data class ContractChange(val symbol: String, val kind: String, val breaking: Boolean, val reason: String)
    data class Review(val score: Int, val findings: List<String>, val approved: Boolean)
    data class ValidationTask(val id: String, val kind: Kind, val description: String, val blocking: Boolean) { enum class Kind { UNIT, INTEGRATION, REGRESSION, SECURITY, COMPATIBILITY } }
    data class RepairTask(val id: String, val cause: String, val instructions: String, val preferredCapabilities: Set<String>)
    data class AiProfile(val aiId: String, val taskType: String, val successes: Int, val failures: Int, val regressions: Int, val averageSeconds: Double, val averageQuality: Double)
    data class MemorySnapshot(val architecture: Set<String>, val decisions: List<String>, val conventions: Set<String>, val contracts: Set<String>, val knownErrors: List<String>, val aiHistory: List<AiProfile>)
    data class CouncilVerdict(val approved: Boolean, val score: Int, val votes: Map<String, Boolean>, val findings: List<String>)
    data class FactoryRequest(val projectId: String, val objective: String, val taskId: String, val zipPaths: List<String> = emptyList())
    data class FactoryResult(val accepted: Boolean, val phase: String, val snapshotCount: Int, val mergePlan: MergePlan, val contracts: List<ContractChange>, val review: Review, val validationTasks: List<ValidationTask>, val repair: RepairTask?, val recommendation: String, val memory: MemorySnapshot)

    private val criticalNames = setOf("AndroidManifest.xml", "build.gradle", "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "proguard-rules.pro")
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun language(path: String): String = when { path.endsWith(".kt") -> "Kotlin"; path.endsWith(".java") -> "Java"; path.endsWith(".ktm") -> "Kotlin"; path.endsWith(".xml") -> "XML"; path.endsWith(".json") -> "JSON"; path.endsWith(".md") -> "Markdown"; path.endsWith(".gradle") || path.endsWith(".gradle.kts") -> "Gradle"; path.endsWith(".yml") || path.endsWith(".yaml") -> "YAML"; path.endsWith(".toml") -> "TOML"; else -> "Other" }

    /** v2.1: inspect the ZIP without extracting it and reject traversal/oversized entries. */
    fun inspectZip(zipPath: String, artifactId: String, taskId: String, maxFiles: Int = 10_000, maxBytes: Long = 50_000_000): ZipSnapshot {
        val files = mutableListOf<ZipFileInfo>(); val findings = mutableListOf<String>(); var total = 0L
        ZipFile(zipPath).use { zip ->
            if (zip.size() > maxFiles) findings += "ZIP contains too many entries"
            zip.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                val normalized = entry.name.replace('\\', '/')
                if (normalized.startsWith("/") || normalized.split('/').any { it == ".." }) { findings += "Path traversal rejected: ${entry.name}"; return@forEach }
                if (normalized.contains("://")) findings += "Suspicious path rejected: $normalized"
                total += entry.size.coerceAtLeast(0L); if (total > maxBytes) findings += "ZIP uncompressed size limit exceeded"
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                files += ZipFileInfo(normalized, entry.size, sha256(bytes), language(normalized), normalized.substringAfterLast('/') in criticalNames)
            }
        }
        val paths = files.map { it.path }
        return ZipSnapshot(artifactId, taskId, files.sortedBy { it.path }, paths, emptyList(), emptyList(), findings.distinct(), findings.none { it.contains("rejected") || it.contains("limit") })
    }

    /** v2.2: combine snapshots and block same-path divergent writes. */
    fun planMerge(snapshots: List<ZipSnapshot>): MergePlan {
        val byPath = snapshots.flatMap { it.files }.groupBy { it.path }
        val conflicts = byPath.filterValues { entries -> entries.map { it.sha256 }.distinct().size > 1 }.keys.sorted().map { "Conflicting artifact content: $it" }
        val files = byPath.keys.sorted()
        return MergePlan(files, conflicts, conflicts.isEmpty() && snapshots.all { it.safe }, conflicts.isNotEmpty())
    }

    /** v2.3: lightweight contract extraction suitable for offline analysis. */
    fun analyzeContracts(contentsByPath: Map<String, String>): List<ContractChange> {
        val result = mutableListOf<ContractChange>()
        contentsByPath.forEach { (path, content) ->
            if (!path.endsWith(".kt") && !path.endsWith(".java")) return@forEach
            Regex("(?:fun|function)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)").findAll(content).forEach { result += ContractChange("${path}:${it.groupValues[1]}", "FUNCTION", false, "Public callable detected") }
            Regex("interface\\s+([A-Za-z_][A-Za-z0-9_]*)").findAll(content).forEach { result += ContractChange("${path}:${it.groupValues[1]}", "INTERFACE", false, "Interface contract detected") }
        }
        return result.distinctBy { it.symbol to it.kind }
    }

    /** v2.4/v2.9: turn a failed integration into a bounded repair task. */
    fun createRepairTask(projectId: String, failure: String, failedPhase: String): RepairTask = RepairTask("repair-$projectId-${failedPhase.lowercase()}", failure.take(500), "Diagnose the failure, preserve existing contracts, produce a new ZIP, and resubmit through the same integration gate.", setOf("CODIGO", "TESTES", "ANALISE"))

    /** v2.5: generate tests after integration planning, without executing them here. */
    fun generateValidationTasks(projectId: String, files: List<String>, contracts: List<ContractChange>): List<ValidationTask> {
        val tasks = mutableListOf<ValidationTask>()
        tasks += ValidationTask("$projectId-unit", ValidationTask.Kind.UNIT, "Validate changed implementation units", true)
        if (files.size > 1 || contracts.isNotEmpty()) tasks += ValidationTask("$projectId-integration", ValidationTask.Kind.INTEGRATION, "Validate cross-module contracts and integration", true)
        tasks += ValidationTask("$projectId-regression", ValidationTask.Kind.REGRESSION, "Run the frozen v0.2.0 compatibility suite", true)
        tasks += ValidationTask("$projectId-security", ValidationTask.Kind.SECURITY, "Scan paths, secrets, dangerous commands and artifacts", true)
        tasks += ValidationTask("$projectId-compatibility", ValidationTask.Kind.COMPATIBILITY, "Verify public contracts and persisted data compatibility", true)
        return tasks
    }

    /** v2.6: deterministic council; each specialist has a clear veto domain. */
    fun reviewCouncil(review: Review): CouncilVerdict {
        val votes = mapOf("architecture" to (review.score >= 70), "security" to (review.findings.none { it.contains("secret", true) || it.contains("unsafe", true) }), "performance" to (review.score >= 60), "testing" to (review.score >= 70), "compatibility" to (review.score >= 75))
        val approved = votes.values.all { it } && review.approved
        return CouncilVerdict(approved, review.score, votes, if (approved) emptyList() else listOf("Council veto: one or more specialist gates failed"))
    }

    /** v2.7: choose an AI using success, quality, regressions and speed. */
    fun rankAIs(taskType: String, profiles: List<AiProfile>): List<AiProfile> = profiles.filter { it.taskType.equals(taskType, true) || taskType == "ANY" }.sortedWith(compareByDescending<AiProfile> { (it.averageQuality.coerceIn(0.0, 1.0) * 0.5) + (it.successes.toDouble() / (it.successes + it.failures + 1)) * 0.3 - (it.regressions.toDouble() / (it.successes + it.failures + 1)) * 0.2 }.thenBy { it.averageSeconds }.thenBy { it.aiId })

    /** v2.8: update memory immutably so every run can be audited locally. */
    fun updateMemory(memory: MemorySnapshot, review: Review, contracts: List<ContractChange>, error: String? = null): MemorySnapshot = memory.copy(
        decisions = (memory.decisions + "review-score=${review.score}").takeLast(100),
        contracts = ((memory.contracts + contracts.map { it.symbol }).toList()).takeLast(200).toSet(),
        knownErrors = (memory.knownErrors + listOfNotNull(error)).takeLast(100)
    )

    /** v3.0 unified entry point. ZIPs are analyzed together; tests remain an external final gate. */
    fun plan(request: FactoryRequest, snapshots: List<ZipSnapshot>, review: Review, contracts: List<ContractChange>, memory: MemorySnapshot): FactoryResult {
        val merge = planMerge(snapshots); val validations = generateValidationTasks(request.projectId, merge.files, contracts); val council = reviewCouncil(review); val accepted = merge.safeToMerge && council.approved
        val repair = if (accepted) null else createRepairTask(request.projectId, (merge.conflicts + review.findings).joinToString("; "), "INTEGRATION")
        val recommendation = if (accepted) "Proceed to external validation, Quality Gate and PR; never write directly to main." else "Do not merge. Execute the repair task and resubmit the ZIP snapshot."
        return FactoryResult(accepted, "V3_PIPELINE", snapshots.size, merge, contracts, review, validations, repair, recommendation, updateMemory(memory, review, contracts, repair?.cause))
    }
}
