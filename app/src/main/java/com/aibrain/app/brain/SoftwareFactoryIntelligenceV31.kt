package com.aibrain.app.brain

/** Deterministic v3.1 intelligence primitives; no provider or main-branch writes. */
object SoftwareFactoryIntelligenceV31 {
    enum class MergeDecision { SAFE_MERGE, AUTO_MERGE, HUMAN_AI_REVIEW_REQUIRED }

    data class OwnedFile(
        val path: String,
        val artifactId: String,
        val functionId: String,
        val sha256: String
    )

    data class MergeAssessment(
        val decision: MergeDecision,
        val files: List<OwnedFile>,
        val conflicts: List<String>,
        val unownedPaths: List<String>,
        val duplicateOwners: List<String>
    )

    fun assessMerge(
        analysis: ZipIntegrationEngine.Analysis,
        declaredFilesByFunction: Map<String, Set<String>>,
        allowAutoMerge: Boolean = false
    ): MergeAssessment {
        val owners = linkedMapOf<String, MutableSet<String>>()
        val files = analysis.entries.map { entry ->
            val functions = declaredFilesByFunction.filterValues { it.contains(entry.path) }.keys.toList()
            functions.forEach { owners.getOrPut(entry.path) { linkedSetOf() }.add(it) }
            OwnedFile(entry.path, entry.artifactId, functions.singleOrNull() ?: "", entry.sha256)
        }
        val duplicateOwners = owners.filterValues { it.size > 1 }.keys.sorted()
        val unownedPaths = files.filter { it.functionId.isBlank() }.map { it.path }.distinct().sorted()
        val conflicts = analysis.conflicts.map { "${it.type}:${it.path}" }.distinct().sorted()
        val requiresReview = conflicts.isNotEmpty() || duplicateOwners.isNotEmpty() || unownedPaths.isNotEmpty() || !analysis.safe
        val decision = when {
            requiresReview -> MergeDecision.HUMAN_AI_REVIEW_REQUIRED
            allowAutoMerge -> MergeDecision.AUTO_MERGE
            else -> MergeDecision.SAFE_MERGE
        }
        return MergeAssessment(decision, files, conflicts, unownedPaths, duplicateOwners)
    }

    enum class SymbolKind { CLASS, INTERFACE, FUNCTION }
    data class Symbol(
        val path: String,
        val name: String,
        val kind: SymbolKind,
        val signature: String,
        val dependencies: Set<String>
    )
    data class ContractChange(
        val symbol: String,
        val kind: SymbolKind,
        val breaking: Boolean,
        val reason: String
    )

    fun analyzeContracts(contentsByPath: Map<String, String>): List<Symbol> = contentsByPath
        .toSortedMap()
        .flatMap { (path, content) ->
            if (!path.endsWith(".kt") && !path.endsWith(".java")) return@flatMap emptyList()
            val imports = Regex("(?m)^\\s*import\\s+([A-Za-z_][A-Za-z0-9_.]*)").findAll(content)
                .map { it.groupValues[1].substringAfterLast('.') }.toSet()
            val symbols = mutableListOf<Symbol>()
            Regex("\\b(class|interface)\\s+([A-Za-z_][A-Za-z0-9_]*)").findAll(content).forEach { match ->
                val kind = if (match.groupValues[1] == "interface") SymbolKind.INTERFACE else SymbolKind.CLASS
                symbols += Symbol(path, match.groupValues[2], kind, match.value, imports)
            }
            Regex("(?m)\\bfun\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)\\s*(?::\\s*([^\\s{=]+))?").findAll(content).forEach { match ->
                val signature = "fun ${match.groupValues[1]}(${match.groupValues[2]}):${match.groupValues[3]}"
                symbols += Symbol(path, match.groupValues[1], SymbolKind.FUNCTION, signature, imports)
            }
            symbols
        }

    fun compareContracts(base: List<Symbol>, current: List<Symbol>): List<ContractChange> {
        fun key(symbol: Symbol) = "${symbol.path}|${symbol.kind}|${symbol.name}"
        val baseByKey = base.associateBy(::key)
        val currentByKey = current.associateBy(::key)
        return (baseByKey.keys + currentByKey.keys).distinct().sorted().mapNotNull { symbolKey ->
            val old = baseByKey[symbolKey]
            val now = currentByKey[symbolKey]
            when {
                old != null && now == null -> ContractChange(symbolKey, old.kind, true, "Símbolo removido")
                old != null && now != null && old.signature != now.signature -> ContractChange(symbolKey, old.kind, true, "Assinatura alterada")
                old == null && now != null -> ContractChange(symbolKey, now.kind, false, "Símbolo adicionado")
                else -> null
            }
        }
    }

    enum class FailureClass { CODE, ENVIRONMENT, DEPENDENCY, INVALID_TEST, UNKNOWN }
    data class FailureDiagnosis(val category: FailureClass, val evidence: String, val confidence: Int)
    data class RepairPlan(val attempt: Int, val maxAttempts: Int, val diagnosis: FailureDiagnosis, val aiId: String?, val allowed: Boolean)

    fun diagnoseFailure(output: String): FailureDiagnosis {
        val rules = listOf(
            FailureClass.DEPENDENCY to listOf("Could not resolve", "403 from", "Could not GET", "Dependency resolution"),
            FailureClass.ENVIRONMENT to listOf("adb", "emulator", "device offline", "SDK location", "connection reset"),
            FailureClass.INVALID_TEST to listOf("No tests found", "test selector", "does not match any"),
            FailureClass.CODE to listOf("AssertionError", "NoMatchingViewException", "Compilation error", "FAILED")
        )
        val match = rules.firstOrNull { (_, markers) -> markers.any { output.contains(it, ignoreCase = true) } }
        return if (match == null) FailureDiagnosis(FailureClass.UNKNOWN, output.take(500), 20)
        else FailureDiagnosis(match.first, match.second.first { output.contains(it, ignoreCase = true) }, 90)
    }

    fun planRepair(
        diagnosis: FailureDiagnosis,
        attempt: Int,
        maxAttempts: Int = 3,
        candidates: List<SoftwareFactoryV3.AiProfile> = emptyList()
    ): RepairPlan {
        require(maxAttempts > 0) { "Limite de tentativas deve ser positivo" }
        val taskType = when (diagnosis.category) {
            FailureClass.CODE -> "CODIGO"
            FailureClass.INVALID_TEST -> "TESTES"
            FailureClass.DEPENDENCY, FailureClass.ENVIRONMENT, FailureClass.UNKNOWN -> "ANALISE"
        }
        val ai = SoftwareFactoryV3.rankAIs(taskType, candidates).firstOrNull()?.aiId
        return RepairPlan(attempt, maxAttempts, diagnosis, ai, attempt < maxAttempts)
    }
}
