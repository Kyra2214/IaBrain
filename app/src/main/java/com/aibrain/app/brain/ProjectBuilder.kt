package com.aibrain.app.brain

/**
 * Converte um objetivo textual em um plano local de projeto.
 * Não executa IAs externas nem acessa a rede.
 */
object ProjectBuilder {
    data class PlannedFunction(
        val id: String,
        val name: String,
        val description: String,
        val dependencies: List<String> = emptyList(),
        val requiredCapabilities: Set<String> = emptySet()
    )

    data class ProjectPlan(
        val objective: String,
        val functions: List<PlannedFunction>
    )

    fun build(objective: String): ProjectPlan {
        val normalized = objective.trim()
        require(normalized.isNotBlank()) { "Objetivo do projeto não pode ser vazio" }

        val functions = listOf(
            PlannedFunction(
                id = "analysis",
                name = "Análise e requisitos",
                description = "Extrair requisitos, restrições e critérios de aceitação.",
                requiredCapabilities = setOf("PESQUISA", "RACIOCINIO")
            ),
            PlannedFunction(
                id = "architecture",
                name = "Arquitetura",
                description = "Definir módulos, contratos e dependências do projeto.",
                dependencies = listOf("analysis"),
                requiredCapabilities = setOf("CODIGO", "RACIOCINIO")
            ),
            PlannedFunction(
                id = "implementation",
                name = "Implementação",
                description = "Implementar os módulos conforme os contratos definidos.",
                dependencies = listOf("architecture"),
                requiredCapabilities = setOf("CODIGO")
            ),
            PlannedFunction(
                id = "validation",
                name = "Testes e validação",
                description = "Executar testes e verificar compatibilidade com o plano.",
                dependencies = listOf("implementation"),
                requiredCapabilities = setOf("CODIGO", "RACIOCINIO")
            )
        )

        return ProjectPlan(normalized, functions)
    }
}
