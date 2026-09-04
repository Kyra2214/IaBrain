package com.aibrain.app.brain

/** Planeja testes automaticamente sem executá-los. Execução fica para o estágio final. */
object TestEngineer {
    enum class TestType { UNIT, INTEGRATION, REGRESSION, SECURITY, COMPATIBILITY }

    data class TestTask(
        val id: String,
        val functionId: String,
        val type: TestType,
        val description: String,
        val blocking: Boolean = true
    )

    fun generate(
        plan: ProjectWorkPlanner.Plan,
        review: IntegrationReviewEngine.Report? = null
    ): List<TestTask> {
        val tasks = mutableListOf<TestTask>()
        plan.workItems.forEach { item ->
            tasks += TestTask("${item.taskId}-unit", item.functionId, TestType.UNIT, "Validar a responsabilidade principal de ${item.functionId}")
            tasks += TestTask("${item.taskId}-integration", item.functionId, TestType.INTEGRATION, "Validar contratos com dependências de ${item.functionId}")
            tasks += TestTask("${item.taskId}-regression", item.functionId, TestType.REGRESSION, "Garantir compatibilidade com o contrato congelado")
            tasks += TestTask("${item.taskId}-security", item.functionId, TestType.SECURITY, "Verificar entradas e artefatos contra regras de segurança")
        }
        review?.findings?.filter { it.severity == IntegrationReviewEngine.Severity.BLOCKER }?.forEachIndexed { index, finding ->
            tasks += TestTask("review-blocker-$index", "integration", TestType.COMPATIBILITY, finding.message)
        }
        return tasks
    }
}
