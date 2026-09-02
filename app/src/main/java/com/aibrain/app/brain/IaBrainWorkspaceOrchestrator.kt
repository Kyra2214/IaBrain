package com.aibrain.app.brain

/**
 * Núcleo local da próxima camada do IaBrain.
 * Centraliza contratos das 12 evoluções do workspace sem duplicar catálogo,
 * roteamento, prompts, navegador ou projetos.
 */
object IaBrainWorkspaceOrchestrator {
    const val VERSION = 1

    fun promptActions(prompt: String, aiId: String?, aiName: String?): PromptActionSet =
        PromptActionSet(prompt = prompt, destinationAIId = aiId, destinationAIName = aiName)

    fun prefillResult(capability: PrefillCapability, attempted: Boolean, filled: Boolean): PrefillResult =
        when {
            capability != PrefillCapability.CONFIRMED -> PrefillResult.NOT_SUPPORTED
            !attempted -> PrefillResult.NOT_ATTEMPTED
            filled -> PrefillResult.PREFILLED
            else -> PrefillResult.FALLBACK_COPY_OPEN
        }

    fun validateWorkspace(files: List<WorkspaceValidationInput>): WorkspaceValidationReport {
        val results = files.map { file ->
            ValidationItem(
                name = file.name,
                level = ValidationLevel.LOCAL,
                status = if (file.valid) ValidationStatus.OK else ValidationStatus.FAILED,
                details = file.details
            )
        }
        return WorkspaceValidationReport(results)
    }

    fun buildMultiAiPlan(task: String, candidates: List<AiCandidate>): MultiAiPlan {
        val ordered = candidates.sortedWith(compareByDescending<AiCandidate> { it.score }.thenBy { it.name })
        return MultiAiPlan(task = task, candidates = ordered, requiresUserApproval = true)
    }

    fun runSkill(skill: SkillDefinition, context: String): SkillRun =
        SkillRun(skillId = skill.id, context = context, steps = skill.steps, automaticSend = false)

    fun addMemory(entry: ProjectMemoryEntry): ProjectMemoryEntry =
        entry.copy(createdAt = if (entry.createdAt > 0) entry.createdAt else System.currentTimeMillis())

    fun createTask(title: String, projectId: String?, priority: TaskPriority = TaskPriority.NORMAL): ProjectTask =
        ProjectTask(
            id = java.util.UUID.randomUUID().toString(),
            projectId = projectId,
            title = title,
            priority = priority,
            status = TaskStatus.PENDING
        )
}

data class PromptActionSet(
    val prompt: String,
    val destinationAIId: String?,
    val destinationAIName: String?,
    val canCopy: Boolean = prompt.isNotBlank(),
    val canSave: Boolean = prompt.isNotBlank(),
    val canOpenAI: Boolean = !destinationAIId.isNullOrBlank(),
    val canSendAutomatically: Boolean = false
)

enum class PrefillResult { PREFILLED, FALLBACK_COPY_OPEN, NOT_SUPPORTED, NOT_ATTEMPTED }

data class WorkspaceValidationInput(val name: String, val valid: Boolean, val details: String)

enum class ValidationLevel { LOCAL, ENVIRONMENT, REMOTE }
enum class ValidationStatus { OK, PENDING, FAILED, NOT_EXECUTED, NOT_VERIFIED, EXTERNAL_DEPENDENCY }

data class ValidationItem(
    val name: String,
    val level: ValidationLevel,
    val status: ValidationStatus,
    val details: String
)

data class WorkspaceValidationReport(val items: List<ValidationItem>) {
    val isComplete: Boolean get() = items.isNotEmpty() && items.all { it.status == ValidationStatus.OK }
    val hasFailure: Boolean get() = items.any { it.status == ValidationStatus.FAILED }
}

data class GithubWorkspaceState(
    val repository: String? = null,
    val branch: String? = null,
    val lastCommit: String? = null,
    val ciStatus: ValidationStatus = ValidationStatus.NOT_VERIFIED,
    val userApprovalRequired: Boolean = true
)

data class ProjectWorkspaceContext(
    val projectId: String,
    val projectName: String,
    val stack: String,
    val objective: String,
    val currentState: String,
    val resources: List<String> = emptyList()
)

data class ProjectChatContext(
    val workspace: ProjectWorkspaceContext,
    val selectedFiles: List<String> = emptyList(),
    val selectedContributions: List<String> = emptyList(),
    val recentMemory: List<ProjectMemoryEntry> = emptyList()
)

data class AiCandidate(val id: String, val name: String, val score: Double, val reason: String)

data class MultiAiPlan(
    val task: String,
    val candidates: List<AiCandidate>,
    val requiresUserApproval: Boolean
)

data class SkillDefinition(
    val id: String,
    val name: String,
    val steps: List<String>,
    val projectAware: Boolean = true
)

data class SkillRun(
    val skillId: String,
    val context: String,
    val steps: List<String>,
    val automaticSend: Boolean
)

enum class MemoryType { DECISION, ARCHITECTURE, PREFERENCE, PROBLEM, SOLUTION, NOTE }

data class ProjectMemoryEntry(
    val id: String,
    val projectId: String,
    val type: MemoryType,
    val title: String,
    val content: String,
    val createdAt: Long = 0L
)

data class BrowserContextSnapshot(
    val selectedTabId: String?,
    val tabIds: List<String>,
    val source: String?,
    val prompt: String?
)

enum class TaskStatus { PENDING, IN_PROGRESS, WAITING_USER, COMPLETED, FAILED, CANCELLED }
enum class TaskPriority { LOW, NORMAL, HIGH, URGENT }

data class ProjectTask(
    val id: String,
    val projectId: String?,
    val title: String,
    val priority: TaskPriority,
    val status: TaskStatus,
    val detail: String = ""
)

data class IntegrationReview(
    val projectId: String,
    val contributionId: String,
    val files: List<IntegrationFileReview>,
    val requiresExplicitApproval: Boolean = true
)

data class IntegrationFileReview(
    val path: String,
    val currentHash: String?,
    val contributionHash: String?,
    val change: String,
    val decision: DecisaoIntegracao
)
