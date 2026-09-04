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
    data class MergeResult(
        val assessment: SoftwareFactoryIntelligenceV31.MergeAssessment,
        val snapshot: WorkspaceSnapshot?,
        val merged: Boolean,
        val blocked: Boolean,
        val rolledBack: Boolean,
        val reason: String? = null
    )
    enum class BaseModifiedPolicy { BLOCK, REVIEW, ALLOW }

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
        allowAutoMerge: Boolean = false,
        baseModifiedPolicy: BaseModifiedPolicy = BaseModifiedPolicy.REVIEW
    ): MergeResult {
        val before = snapshot("before-merge", workspace)
        val baseModified = analysis.conflicts.any { it.type == ZipIntegrationEngine.ConflictType.BASE_MODIFIED }
        val effectiveAnalysis = if (baseModified && baseModifiedPolicy == BaseModifiedPolicy.ALLOW) {
            analysis.copy(conflicts = analysis.conflicts.filterNot { it.type == ZipIntegrationEngine.ConflictType.BASE_MODIFIED })
        } else analysis
        val assessment = SoftwareFactoryIntelligenceV31.assessMerge(effectiveAnalysis, declaredFilesByFunction, allowAutoMerge)
        if (assessment.decision == SoftwareFactoryIntelligenceV31.MergeDecision.HUMAN_AI_REVIEW_REQUIRED ||
            (baseModified && baseModifiedPolicy != BaseModifiedPolicy.ALLOW)
        ) {
            val reason = if (baseModified && baseModifiedPolicy != BaseModifiedPolicy.ALLOW) "BASE_MODIFIED policy=${baseModifiedPolicy.name}" else "Merge requires review"
            return MergeResult(assessment, before, merged = false, blocked = true, rolledBack = false, reason = reason)
        }
        try {
            artifacts.forEach { artifact ->
                ZipIntegrationEngine.materialize(artifact, workspace)
            }
            val after = snapshot("after-merge", workspace)
            return MergeResult(assessment, after, merged = true, blocked = false, rolledBack = false)
        } catch (failure: Throwable) {
            restore(before)
            return MergeResult(assessment, before, merged = false, blocked = true, rolledBack = true, reason = failure.message)
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
    enum class ContractGraphResolution { LEXICAL_V1, AST_V2_PENDING }
    data class ContractGraph(
        val nodes: List<ContractNode>,
        val dependencies: Map<String, Set<String>>,
        val resolution: ContractGraphResolution = ContractGraphResolution.LEXICAL_V1
    )

    fun contractGraph(contentsByPath: Map<String, String>): ContractGraph {
        val symbols = SoftwareFactoryIntelligenceV31.analyzeContracts(contentsByPath)
        val nodes = symbols.map { symbol ->
            val key = "${symbol.path}|${symbol.kind}|${symbol.name}"
            val consumers = contentsByPath.filter { (path, content) ->
                path != symbol.path && when (symbol.kind) {
                    SoftwareFactoryIntelligenceV31.SymbolKind.FUNCTION -> Regex("\\b${Regex.escape(symbol.name)}\\s*\\(").containsMatchIn(content)
                    SoftwareFactoryIntelligenceV31.SymbolKind.CLASS,
                    SoftwareFactoryIntelligenceV31.SymbolKind.INTERFACE -> Regex("\\b${Regex.escape(symbol.name)}\\b").containsMatchIn(content)
                }
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

    class Store(private val journal: File? = null) {
        private val snapshots = linkedMapOf<String, WorkspaceSnapshot>()
        private val reviews = linkedMapOf<String, ReviewCase>()
        private val repairs = linkedMapOf<String, RepairTask>()
        private val reports = linkedMapOf<String, TestReport>()
        private val persisted = linkedSetOf<String>()

        init {
            journal?.takeIf { it.isFile }?.forEachLine { line ->
                line.split('\t', limit = 2).takeIf { it.size == 2 }?.let { persisted += "${it[0]}:${it[1]}" }
            }
        }

        fun save(snapshot: WorkspaceSnapshot) { snapshots[snapshot.id] = snapshot; persist("snapshot", snapshot.id) }
        fun save(review: ReviewCase) { reviews[review.id] = review; persist("review", review.id) }
        fun save(repair: RepairTask) { repairs[repair.id] = repair; persist("repair", repair.id) }
        fun save(id: String, report: TestReport) { reports[id] = report; persist("report", id) }
        fun snapshot(id: String) = snapshots[id]
        fun review(id: String) = reviews[id]
        fun repair(id: String) = repairs[id]
        fun report(id: String) = reports[id]
        fun contains(type: String, id: String): Boolean = "$type:$id" in persisted || when (type) {
            "snapshot" -> id in snapshots
            "review" -> id in reviews
            "repair" -> id in repairs
            "report" -> id in reports
            else -> false
        }
        fun size(): Int = (persisted + snapshots.keys.map { "snapshot:$it" } + reviews.keys.map { "review:$it" } + repairs.keys.map { "repair:$it" } + reports.keys.map { "report:$it" }).size

        private fun persist(type: String, id: String) {
            val key = "$type:$id"
            if (persisted.add(key)) {
                journal?.parentFile?.mkdirs()
                journal?.appendText("$type\t$id\n")
            }
        }
    }
}
