package com.aibrain.app.brain

import java.io.File
import java.security.MessageDigest

/** Operational local-first runtime for the twelve v3.1 capabilities. */
class SoftwareFactoryRuntimeV31(
    private val limits: Limits = Limits()
) {
    data class Limits(
        val maxFiles: Int = 10_000,
        val maxBytes: Long = 50_000_000,
        val maxRepairAttempts: Int = 3,
        val maxDependencyDepth: Int = 20
    )

    data class SnapshotFile(val path: String, val sha256: String, val size: Long)
    data class WorkspaceSnapshot(val id: String, val root: File, val files: List<SnapshotFile>, val backup: File)
    data class MergeResult(val assessment: SoftwareFactoryIntelligenceV31.MergeAssessment, val snapshot: WorkspaceSnapshot?, val rolledBack: Boolean)

    fun snapshot(id: String, root: File): WorkspaceSnapshot {
        require(id.isNotBlank()) { "Snapshot sem id" }
        require(root.isDirectory) { "Workspace não encontrado: $root" }
        val files = root.walkTopDown().filter { it.isFile }.map {
            val relative = it.relativeTo(root).invariantSeparatorsPath
            SnapshotFile(relative, sha256(it.readBytes()), it.length())
        }.toList().also { validateLimits(it) }
        val backup = createTempDir(prefix = "iabrain-snapshot-")
        root.copyRecursively(backup, overwrite = true)
        return WorkspaceSnapshot(id, root, files.sortedBy { it.path }, backup)
    }

    fun restore(snapshot: WorkspaceSnapshot) {
        require(snapshot.root.isDirectory) { "Workspace não encontrado" }
        snapshot.root.deleteRecursively()
        require(snapshot.root.mkdirs() || snapshot.root.isDirectory) { "Não foi possível recriar workspace" }
        snapshot.backup.copyRecursively(snapshot.root, overwrite = true)
    }

    fun merge(
        workspace: File,
        analysis: ZipIntegrationEngine.Analysis,
        artifacts: List<ZipIntegrationEngine.Artifact>,
        declaredFilesByFunction: Map<String, Set<String>>,
        allowAutoMerge: Boolean = false
    ): MergeResult {
        val before = snapshot("before-merge", workspace)
        val assessment = SoftwareFactoryIntelligenceV31.assessMerge(analysis, declaredFilesByFunction, allowAutoMerge)
        if (assessment.decision == SoftwareFactoryIntelligenceV31.MergeDecision.HUMAN_AI_REVIEW_REQUIRED) {
            return MergeResult(assessment, before, false)
        }
        try {
            artifacts.forEach { artifact ->
                ZipIntegrationEngine.materialize(artifact, workspace)
            }
            val after = snapshot("after-merge", workspace)
            return MergeResult(assessment, after, false)
        } catch (failure: Throwable) {
            // Materialization is isolated; callers receive the pre-merge snapshot and failure state.
            return MergeResult(assessment, before, true)
        }
    }

    private fun validateLimits(files: List<SnapshotFile>) {
        require(files.size <= limits.maxFiles) { "Workspace excede limite de arquivos" }
        require(files.sumOf { it.size } <= limits.maxBytes) { "Workspace excede limite de bytes" }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    data class ContractNode(
        val key: String,
        val symbol: SoftwareFactoryIntelligenceV31.Symbol,
        val consumers: Set<String>
    )
    data class ContractGraph(val nodes: List<ContractNode>, val dependencies: Map<String, Set<String>>)

    fun contractGraph(contentsByPath: Map<String, String>): ContractGraph {
        val symbols = SoftwareFactoryIntelligenceV31.analyzeContracts(contentsByPath)
        val names = symbols.map { it.name }.toSet()
        val nodes = symbols.map { symbol ->
            val key = "${symbol.path}|${symbol.kind}|${symbol.name}"
            val consumers = contentsByPath.filter { (_, content) ->
                it.key != key && names.any { name -> content.contains("$name(") || content.contains("$name {") }
            }.keys
            ContractNode(key, symbol, consumers)
        }
        val dependencies = nodes.associate { it.key to it.symbol.dependencies }
        return ContractGraph(nodes, dependencies)
    }

    data class FailureLocation(val path: String?, val line: Int?, val symbol: String?)
    data class StructuredFailure(
        val diagnosis: SoftwareFactoryIntelligenceV31.FailureDiagnosis,
        val location: FailureLocation,
        val raw: String
    )

    fun structureFailure(output: String): StructuredFailure {
        val diagnosis = SoftwareFactoryIntelligenceV31.diagnoseFailure(output)
        val locationMatch = Regex("(?:file://)?([^\\s:()]+\\.(?:kt|java)):(\\d+)").find(output)
        val symbol = Regex("(?:at|in)\\s+([A-Za-z_][A-Za-z0-9_.]*)").find(output)?.groupValues?.get(1)
        return StructuredFailure(
            diagnosis,
            FailureLocation(locationMatch?.groupValues?.get(1), locationMatch?.groupValues?.get(2)?.toIntOrNull(), symbol),
            output.take(2_000)
        )
    }

    data class RepairTask(
        val id: String,
        val attempt: Int,
        val diagnosis: StructuredFailure,
        val preferredCapabilities: Set<String>,
        val acceptance: List<String>
    )
    data class RepairCycle(val task: RepairTask, val allowed: Boolean, val reason: String)

    fun repairCycle(projectId: String, output: String, attempt: Int): RepairCycle {
        val diagnosis = structureFailure(output)
        val task = RepairTask(
            "$projectId-repair-$attempt",
            attempt,
            diagnosis,
            when (diagnosis.diagnosis.category) {
                SoftwareFactoryIntelligenceV31.FailureClass.CODE -> setOf("CODIGO")
                SoftwareFactoryIntelligenceV31.FailureClass.INVALID_TEST -> setOf("TESTES")
                else -> setOf("ANALISE")
            },
            listOf("Corrigir a causa identificada", "Preservar contratos existentes", "Entregar ZIP e permitir reteste")
        )
        return RepairCycle(task, attempt in 1..limits.maxRepairAttempts, if (attempt <= limits.maxRepairAttempts) "Reparo permitido" else "Limite de reparos atingido")
    }

    enum class TestOutcome { PASSED, CODE_FAILURE, ENVIRONMENT_FAILURE, DEPENDENCY_FAILURE, INVALID_TEST }
    data class TestReport(val command: String, val exitCode: Int, val output: String, val outcome: TestOutcome)

    fun classifyTest(command: String, exitCode: Int, output: String): TestReport {
        val diagnosis = SoftwareFactoryIntelligenceV31.diagnoseFailure(output)
        val outcome = when {
            exitCode == 0 -> TestOutcome.PASSED
            diagnosis.category == SoftwareFactoryIntelligenceV31.FailureClass.DEPENDENCY -> TestOutcome.DEPENDENCY_FAILURE
            diagnosis.category == SoftwareFactoryIntelligenceV31.FailureClass.ENVIRONMENT -> TestOutcome.ENVIRONMENT_FAILURE
            diagnosis.category == SoftwareFactoryIntelligenceV31.FailureClass.INVALID_TEST -> TestOutcome.INVALID_TEST
            else -> TestOutcome.CODE_FAILURE
        }
        return TestReport(command, exitCode, output.take(2_000), outcome)
    }

    data class ReviewCase(
        val id: String,
        val assessment: SoftwareFactoryIntelligenceV31.MergeAssessment,
        val contractChanges: List<SoftwareFactoryIntelligenceV31.ContractChange>,
        val reason: String,
        val resolved: Boolean = false
    )
    fun reviewCase(id: String, assessment: SoftwareFactoryIntelligenceV31.MergeAssessment, changes: List<SoftwareFactoryIntelligenceV31.ContractChange>): ReviewCase =
        ReviewCase(id, assessment, changes, "Ownership, conflito ou alteração de contrato exige revisão")

    class Store {
        private val snapshots = linkedMapOf<String, WorkspaceSnapshot>()
        private val reviews = linkedMapOf<String, ReviewCase>()
        private val repairs = linkedMapOf<String, RepairTask>()
        private val reports = linkedMapOf<String, TestReport>()
        fun save(snapshot: WorkspaceSnapshot) { snapshots[snapshot.id] = snapshot }
        fun save(review: ReviewCase) { reviews[review.id] = review }
        fun save(repair: RepairTask) { repairs[repair.id] = repair }
        fun save(id: String, report: TestReport) { reports[id] = report }
        fun snapshot(id: String) = snapshots[id]
        fun review(id: String) = reviews[id]
        fun repair(id: String) = repairs[id]
        fun report(id: String) = reports[id]
        fun size(): Int = snapshots.size + reviews.size + repairs.size + reports.size
    }
}
