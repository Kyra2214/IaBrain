package com.aibrain.app.brain

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Runtime local real das 12 evoluções do workspace.
 * Não envia dados, não executa código recebido e não faz automação externa.
 * O estado fica em armazenamento privado do aplicativo.
 */
class WorkspaceVNextRuntime(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("workspace_vnext", Context.MODE_PRIVATE)

    fun recordPromptAction(prompt: String, aiId: String?, aiName: String?, action: PromptActionType): PromptActionRecord {
        val record = PromptActionRecord(UUID.randomUUID().toString(), prompt, aiId, aiName, action, System.currentTimeMillis())
        appendJson(KEY_PROMPT_ACTIONS, record.toJson())
        return record
    }

    fun promptHistory(): List<PromptActionRecord> = readJson(KEY_PROMPT_ACTIONS).map { PromptActionRecord.fromJson(it) }

    fun savePrefillState(aiId: String, result: PrefillResult) {
        prefs.edit().putString("prefill:$aiId", result.name).apply()
    }

    fun prefillState(aiId: String): PrefillResult = runCatching {
        PrefillResult.valueOf(prefs.getString("prefill:$aiId", PrefillResult.NOT_ATTEMPTED.name)!!)
    }.getOrDefault(PrefillResult.NOT_ATTEMPTED)

    fun saveWorkspaceValidation(projectId: String, report: WorkspaceValidationReport) {
        prefs.edit().putString("validation:$projectId", report.toJson().toString()).apply()
    }

    fun workspaceValidation(projectId: String): WorkspaceValidationReport =
        prefs.getString("validation:$projectId", null)?.let { WorkspaceValidationReport.fromJson(JSONObject(it)) }
            ?: WorkspaceValidationReport(emptyList())

    fun saveGithubState(projectId: String, state: GithubWorkspaceState) {
        prefs.edit().putString("github:$projectId", state.toJson().toString()).apply()
    }

    fun githubState(projectId: String): GithubWorkspaceState =
        prefs.getString("github:$projectId", null)?.let { GithubWorkspaceState.fromJson(JSONObject(it)) }
            ?: GithubWorkspaceState()

    fun saveProjectContext(context: ProjectWorkspaceContext) {
        prefs.edit().putString("context:${context.projectId}", context.toJson().toString()).apply()
    }

    fun projectContext(projectId: String): ProjectWorkspaceContext? =
        prefs.getString("context:$projectId", null)?.let { ProjectWorkspaceContext.fromJson(JSONObject(it)) }

    fun saveChatContext(context: ProjectChatContext) {
        prefs.edit().putString("chat-context:${context.workspace.projectId}", context.toJson().toString()).apply()
    }

    fun chatContext(projectId: String): ProjectChatContext? =
        prefs.getString("chat-context:$projectId", null)?.let { ProjectChatContext.fromJson(JSONObject(it)) }

    fun addSkill(skill: SkillDefinition): SkillDefinition {
        val skills = skills().filterNot { it.id == skill.id } + skill
        prefs.edit().putString(KEY_SKILLS, JSONArray(skills.map { it.toJson().toString() }).toString()).apply()
        return skill
    }

    fun skills(): List<SkillDefinition> = readJson(KEY_SKILLS).map { SkillDefinition.fromJson(it) }

    fun runSkill(projectId: String, skill: SkillDefinition, context: String): SkillRun {
        val run = IaBrainWorkspaceOrchestrator.runSkill(skill, context)
        appendJson(KEY_SKILL_RUNS, JSONObject().apply {
            put("projectId", projectId); put("skillId", run.skillId); put("context", run.context)
            put("steps", JSONArray(run.steps)); put("automaticSend", run.automaticSend); put("at", System.currentTimeMillis())
        })
        return run
    }

    fun skillRuns(): List<JSONObject> = readJson(KEY_SKILL_RUNS)

    fun addMemory(entry: ProjectMemoryEntry): ProjectMemoryEntry {
        val saved = IaBrainWorkspaceOrchestrator.addMemory(entry)
        appendJson(KEY_MEMORIES, saved.toJson())
        return saved
    }

    fun memories(projectId: String): List<ProjectMemoryEntry> = readJson(KEY_MEMORIES)
        .map { ProjectMemoryEntry.fromJson(it) }
        .filter { it.projectId == projectId }
        .sortedByDescending { it.createdAt }

    fun createTask(title: String, projectId: String?, priority: TaskPriority = TaskPriority.NORMAL): ProjectTask {
        val task = IaBrainWorkspaceOrchestrator.createTask(title, projectId, priority)
        appendJson(KEY_TASKS, task.toJson())
        return task
    }

    fun tasks(projectId: String? = null): List<ProjectTask> = readJson(KEY_TASKS)
        .map { ProjectTask.fromJson(it) }
        .filter { projectId == null || it.projectId == projectId }
        .sortedWith(compareBy<ProjectTask> { it.status.ordinal }.thenByDescending { it.priority.ordinal })

    fun updateTaskStatus(id: String, status: TaskStatus): Boolean {
        val updated = tasks().map { if (it.id == id) it.copy(status = status) else it }
        if (updated.none { it.id == id }) return false
        prefs.edit().putString(KEY_TASKS, JSONArray(updated.map { it.toJson().toString() }).toString()).apply()
        return true
    }

    fun saveBrowserContext(snapshot: BrowserContextSnapshot) {
        prefs.edit().putString(KEY_BROWSER_CONTEXT, snapshot.toJson().toString()).apply()
    }

    fun browserContext(): BrowserContextSnapshot? =
        prefs.getString(KEY_BROWSER_CONTEXT, null)?.let { BrowserContextSnapshot.fromJson(JSONObject(it)) }

    private fun appendJson(key: String, value: JSONObject) {
        val current = readJson(key).toMutableList()
        current.add(value)
        prefs.edit().putString(key, JSONArray(current.map { it.toString() }).toString()).apply()
    }

    private fun readJson(key: String): List<JSONObject> {
        val array = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { index -> runCatching { JSONObject(array.getString(index)) }.getOrNull() }
    }

    companion object {
        private const val KEY_PROMPT_ACTIONS = "prompt-actions"
        private const val KEY_SKILLS = "skills"
        private const val KEY_SKILL_RUNS = "skill-runs"
        private const val KEY_MEMORIES = "memories"
        private const val KEY_TASKS = "tasks"
        private const val KEY_BROWSER_CONTEXT = "browser-context"
    }
}

enum class PromptActionType { COPY, SAVE, OPEN }

data class PromptActionRecord(
    val id: String, val prompt: String, val aiId: String?, val aiName: String?,
    val action: PromptActionType, val createdAt: Long
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("prompt", prompt); put("aiId", aiId); put("aiName", aiName)
        put("action", action.name); put("createdAt", createdAt)
    }
    companion object { fun fromJson(o: JSONObject) = PromptActionRecord(o.optString("id"), o.optString("prompt"), o.optString("aiId").ifBlank { null }, o.optString("aiName").ifBlank { null }, runCatching { PromptActionType.valueOf(o.optString("action")) }.getOrDefault(PromptActionType.COPY), o.optLong("createdAt")) }
}

private fun ProjectTask.toJson() = JSONObject().apply { put("id", id); put("projectId", projectId); put("title", title); put("priority", priority.name); put("status", status.name); put("detail", detail) }
private fun ProjectMemoryEntry.toJson() = JSONObject().apply { put("id", id); put("projectId", projectId); put("type", type.name); put("title", title); put("content", content); put("createdAt", createdAt) }
private fun SkillDefinition.toJson() = JSONObject().apply { put("id", id); put("name", name); put("steps", JSONArray(steps)); put("projectAware", projectAware) }
private fun BrowserContextSnapshot.toJson() = JSONObject().apply { put("selectedTabId", selectedTabId); put("tabIds", JSONArray(tabIds)); put("source", source); put("prompt", prompt) }
private fun GithubWorkspaceState.toJson() = JSONObject().apply { put("repository", repository); put("branch", branch); put("lastCommit", lastCommit); put("ciStatus", ciStatus.name); put("userApprovalRequired", userApprovalRequired) }
private fun ProjectWorkspaceContext.toJson() = JSONObject().apply { put("projectId", projectId); put("projectName", projectName); put("stack", stack); put("objective", objective); put("currentState", currentState); put("resources", JSONArray(resources)) }
private fun ProjectChatContext.toJson() = JSONObject().apply { put("workspace", workspace.toJson()); put("selectedFiles", JSONArray(selectedFiles)); put("selectedContributions", JSONArray(selectedContributions)); put("recentMemory", JSONArray(recentMemory.map { it.toJson().toString() })) }
private fun WorkspaceValidationReport.toJson() = JSONObject().apply { put("items", JSONArray(items.map { JSONObject().apply { put("name", it.name); put("level", it.level.name); put("status", it.status.name); put("details", it.details) }.toString() })) }

private fun ProjectTask.Companion.fromJson(o: JSONObject) = ProjectTask(o.optString("id"), o.optString("projectId").ifBlank { null }, o.optString("title"), runCatching { TaskPriority.valueOf(o.optString("priority")) }.getOrDefault(TaskPriority.NORMAL), runCatching { TaskStatus.valueOf(o.optString("status")) }.getOrDefault(TaskStatus.PENDING), o.optString("detail"))
private fun ProjectMemoryEntry.Companion.fromJson(o: JSONObject) = ProjectMemoryEntry(o.optString("id"), o.optString("projectId"), runCatching { MemoryType.valueOf(o.optString("type")) }.getOrDefault(MemoryType.NOTE), o.optString("title"), o.optString("content"), o.optLong("createdAt"))
private fun SkillDefinition.Companion.fromJson(o: JSONObject) = SkillDefinition(o.optString("id"), o.optString("name"), (0 until o.optJSONArray("steps").length()).map { o.optJSONArray("steps").optString(it) }, o.optBoolean("projectAware", true))
private fun BrowserContextSnapshot.Companion.fromJson(o: JSONObject) = BrowserContextSnapshot(o.optString("selectedTabId").ifBlank { null }, (0 until o.optJSONArray("tabIds").length()).map { o.optJSONArray("tabIds").optString(it) }, o.optString("source").ifBlank { null }, o.optString("prompt").ifBlank { null })
private fun GithubWorkspaceState.Companion.fromJson(o: JSONObject) = GithubWorkspaceState(o.optString("repository").ifBlank { null }, o.optString("branch").ifBlank { null }, o.optString("lastCommit").ifBlank { null }, runCatching { ValidationStatus.valueOf(o.optString("ciStatus")) }.getOrDefault(ValidationStatus.NOT_VERIFIED), o.optBoolean("userApprovalRequired", true))
private fun ProjectWorkspaceContext.Companion.fromJson(o: JSONObject) = ProjectWorkspaceContext(o.optString("projectId"), o.optString("projectName"), o.optString("stack"), o.optString("objective"), o.optString("currentState"), (0 until o.optJSONArray("resources").length()).map { o.optJSONArray("resources").optString(it) })
private fun ProjectChatContext.Companion.fromJson(o: JSONObject) = ProjectChatContext(ProjectWorkspaceContext.fromJson(o.optJSONObject("workspace")), (0 until o.optJSONArray("selectedFiles").length()).map { o.optJSONArray("selectedFiles").optString(it) }, (0 until o.optJSONArray("selectedContributions").length()).map { o.optJSONArray("selectedContributions").optString(it) }, (0 until o.optJSONArray("recentMemory").length()).map { ProjectMemoryEntry.fromJson(JSONObject(o.optJSONArray("recentMemory").optString(it))) })
private fun WorkspaceValidationReport.Companion.fromJson(o: JSONObject): WorkspaceValidationReport { val a=o.optJSONArray("items") ?: JSONArray(); return WorkspaceValidationReport((0 until a.length()).map { val i=JSONObject(a.optString(it)); ValidationItem(i.optString("name"), runCatching { ValidationLevel.valueOf(i.optString("level")) }.getOrDefault(ValidationLevel.LOCAL), runCatching { ValidationStatus.valueOf(i.optString("status")) }.getOrDefault(ValidationStatus.NOT_VERIFIED), i.optString("details")) }) }
