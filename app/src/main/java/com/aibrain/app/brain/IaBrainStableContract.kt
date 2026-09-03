package com.aibrain.app.brain

/** Contrato público do motor estável do IaBrain v1.0. */
object IaBrainStableContract {
    const val VERSION = "1.0.0"
    const val MIN_COMPATIBLE_VERSION = "0.2.0"
    const val BASE_BRANCH = "main"

    data class BuildResult(
        val project: ProjectBuilder.ProjectPlan,
        val workPlan: ProjectWorkPlanner.Plan,
        val qualityGate: ProjectQualityGate.Result,
        val security: List<ExecutionSecurityPolicy.Result>
    ) {
        val readyForIntegration: Boolean get() = qualityGate.passed && security.all { it.allowed }
    }

    fun prepare(objective: String): BuildResult {
        val project = ProjectBuilder.build(objective)
        val workPlan = ProjectWorkPlanner.build(project, BASE_BRANCH)
        val qualityGate = ProjectQualityGate.validate(workPlan)
        val security = workPlan.workItems.map(ExecutionSecurityPolicy::validateWorkItem)
        return BuildResult(project, workPlan, qualityGate, security)
    }
}
