package com.aibrain.app.brain

/**
 * Plano local que transforma as funções do Project Builder em unidades de
 * trabalho independentes. Cada unidade pode ser executada por uma IA em uma
 * branch própria e só retorna ao projeto por PR.
 */
object ProjectWorkPlanner {
    enum class Role { ANALYSIS, ARCHITECTURE, IMPLEMENTATION, TESTING }

    data class WorkItem(
        val functionId: String,
        val role: Role,
        val branchName: String,
        val dependsOn: List<String>,
        val requiredCapabilities: Set<String>,
        val command: String = "/develop"
    )

    data class Plan(
        val objective: String,
        val baseBranch: String,
        val workItems: List<WorkItem>
    )

    fun build(project: ProjectBuilder.ProjectPlan, baseBranch: String = "main"): Plan {
        require(baseBranch.isNotBlank()) { "Branch base não pode ser vazia" }
        val usedBranches = mutableSetOf<String>()
        val items = project.functions.map { function ->
            val role = when (function.id) {
                "analysis" -> Role.ANALYSIS
                "architecture" -> Role.ARCHITECTURE
                "implementation" -> Role.IMPLEMENTATION
                "validation" -> Role.TESTING
                else -> Role.IMPLEMENTATION
            }
            val branch = "ai/${projectKey(project.objective)}/${function.id}"
            require(usedBranches.add(branch)) { "Branch duplicada no plano: $branch" }
            WorkItem(
                functionId = function.id,
                role = role,
                branchName = branch,
                dependsOn = function.dependencies,
                requiredCapabilities = function.requiredCapabilities,
                command = if (role == Role.TESTING) "/test" else "/develop"
            )
        }
        return Plan(project.objective, baseBranch, items)
    }

    private fun projectKey(objective: String): String = objective
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(40)
        .ifBlank { "projeto" }
}

/** Regras locais que impedem integração estruturalmente inconsistente. */
object ProjectQualityGate {
    enum class Status { PASSED, BLOCKED }

    data class Check(val name: String, val passed: Boolean, val reason: String)
    data class Result(val status: Status, val checks: List<Check>) {
        val passed: Boolean get() = status == Status.PASSED
    }

    fun validate(plan: ProjectWorkPlanner.Plan): Result {
        val ids = plan.workItems.map { it.functionId }.toSet()
        val branches = plan.workItems.map { it.branchName }
        val checks = mutableListOf<Check>()

        checks += Check("objetivo", plan.objective.isNotBlank(), "Objetivo deve existir")
        checks += Check("branch-base", plan.baseBranch == "main", "Integração deve partir de main")
        checks += Check("branches-unicas", branches.size == branches.toSet().size, "Cada função precisa de uma branch própria")
        checks += Check(
            "dependencias-validas",
            plan.workItems.all { item -> item.dependsOn.all { it in ids && it != item.functionId } },
            "Toda dependência deve apontar para outra função do plano"
        )
        checks += Check(
            "capacidades", 
            plan.workItems.all { it.requiredCapabilities.isNotEmpty() },
            "Toda função precisa declarar capacidades exigidas"
        )
        checks += Check("comandos", plan.workItems.all { it.command.startsWith("/") }, "Toda unidade precisa de comando explícito")

        return Result(
            if (checks.all { it.passed }) Status.PASSED else Status.BLOCKED,
            checks
        )
    }
}

/** Contrato de integração: AIs não escrevem diretamente na branch base. */
object GitHubIntegrationPolicy {
    const val BASE_BRANCH = "main"
    const val INTEGRATION_BOUNDARY = "PULL_REQUEST"

    fun canMerge(qualityGate: ProjectQualityGate.Result): Boolean = qualityGate.passed
}
