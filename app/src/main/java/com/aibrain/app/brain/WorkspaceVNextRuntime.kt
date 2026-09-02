package com.aibrain.app.brain

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Runtime persistente local para os 12 blocos do workspace. */
class WorkspaceVNextRuntime(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("workspace_vnext", Context.MODE_PRIVATE)
    fun recordPromptAction(prompt: String, aiId: String?, aiName: String?, action: PromptActionType): PromptActionRecord { val r=PromptActionRecord(UUID.randomUUID().toString(),prompt,aiId,aiName,action,System.currentTimeMillis()); appendJson(KEY_PROMPT_ACTIONS,r.toJson()); return r }
    fun promptHistory() = readJson(KEY_PROMPT_ACTIONS).map(PromptActionRecord::fromJson)
    fun savePrefillState(aiId:String,result:PrefillResult){prefs.edit().putString("prefill:$aiId",result.name).apply()}
    fun prefillState(aiId:String)=runCatching{PrefillResult.valueOf(prefs.getString("prefill:$aiId",PrefillResult.NOT_ATTEMPTED.name)!!)}.getOrDefault(PrefillResult.NOT_ATTEMPTED)
    fun saveWorkspaceValidation(projectId:String,report:WorkspaceValidationReport){prefs.edit().putString("validation:$projectId",report.toJson().toString()).apply()}
    fun workspaceValidation(projectId:String)=prefs.getString("validation:$projectId",null)?.let{parseValidationReport(JSONObject(it))}?:WorkspaceValidationReport(emptyList())
    fun saveGithubState(projectId:String,state:GithubWorkspaceState){prefs.edit().putString("github:$projectId",state.toJson().toString()).apply()}
    fun githubState(projectId:String)=prefs.getString("github:$projectId",null)?.let{parseGithubState(JSONObject(it))}?:GithubWorkspaceState()
    fun saveProjectContext(value:ProjectWorkspaceContext){prefs.edit().putString("context:${value.projectId}",value.toJson().toString()).apply()}
    fun projectContext(projectId:String)=prefs.getString("context:$projectId",null)?.let{parseWorkspaceContext(JSONObject(it))}
    fun saveChatContext(value:ProjectChatContext){prefs.edit().putString("chat-context:${value.workspace.projectId}",value.toJson().toString()).apply()}
    fun chatContext(projectId:String)=prefs.getString("chat-context:$projectId",null)?.let{parseChatContext(JSONObject(it))}
    fun addSkill(skill:SkillDefinition):SkillDefinition{val skills=skills().filterNot{it.id==skill.id}+skill;prefs.edit().putString(KEY_SKILLS,JSONArray(skills.map{it.toJson().toString()}).toString()).apply();return skill}
    fun skills()=readJson(KEY_SKILLS).map(::parseSkill)
    fun runSkill(projectId:String,skill:SkillDefinition,context:String):SkillRun{val r=IaBrainWorkspaceOrchestrator.runSkill(skill,context);appendJson(KEY_SKILL_RUNS,JSONObject().apply{put("projectId",projectId);put("skillId",r.skillId);put("context",r.context);put("steps",JSONArray(r.steps));put("automaticSend",r.automaticSend);put("at",System.currentTimeMillis())});return r}
    fun skillRuns()=readJson(KEY_SKILL_RUNS)
    fun addMemory(entry:ProjectMemoryEntry):ProjectMemoryEntry{val saved=IaBrainWorkspaceOrchestrator.addMemory(entry);appendJson(KEY_MEMORIES,saved.toJson());return saved}
    fun memories(projectId:String)=readJson(KEY_MEMORIES).map(::parseMemory).filter{it.projectId==projectId}.sortedByDescending{it.createdAt}
    fun createTask(title:String,projectId:String?,priority:TaskPriority=TaskPriority.NORMAL):ProjectTask{val t=IaBrainWorkspaceOrchestrator.createTask(title,projectId,priority);appendJson(KEY_TASKS,t.toJson());return t}
    fun tasks(projectId:String?=null)=readJson(KEY_TASKS).map(::parseTask).filter{projectId==null||it.projectId==projectId}.sortedWith(compareBy<ProjectTask>{it.status.ordinal}.thenByDescending{it.priority.ordinal})
    fun updateTaskStatus(id:String,status:TaskStatus):Boolean{val u=tasks().map{if(it.id==id)it.copy(status=status)else it};if(u.none{it.id==id})return false;prefs.edit().putString(KEY_TASKS,JSONArray(u.map{it.toJson().toString()}).toString()).apply();return true}
    fun saveBrowserContext(snapshot:BrowserContextSnapshot){prefs.edit().putString(KEY_BROWSER_CONTEXT,snapshot.toJson().toString()).apply()}
    fun browserContext()=prefs.getString(KEY_BROWSER_CONTEXT,null)?.let{parseBrowserContext(JSONObject(it))}
    private fun appendJson(key:String,value:JSONObject){val current=readJson(key).toMutableList();current.add(value);prefs.edit().putString(key,JSONArray(current.map{it.toString()}).toString()).apply()}
    private fun readJson(key:String):List<JSONObject>{val a=runCatching{JSONArray(prefs.getString(key,"[]"))}.getOrDefault(JSONArray());return(0 until a.length()).mapNotNull{runCatching{JSONObject(a.getString(it))}.getOrNull()}}
    companion object{private const val KEY_PROMPT_ACTIONS="prompt-actions";private const val KEY_SKILLS="skills";private const val KEY_SKILL_RUNS="skill-runs";private const val KEY_MEMORIES="memories";private const val KEY_TASKS="tasks";private const val KEY_BROWSER_CONTEXT="browser-context"}
}

enum class PromptActionType{COPY,SAVE,OPEN}
data class PromptActionRecord(val id:String,val prompt:String,val aiId:String?,val aiName:String?,val action:PromptActionType,val createdAt:Long){fun toJson()=JSONObject().apply{put("id",id);put("prompt",prompt);put("aiId",aiId);put("aiName",aiName);put("action",action.name);put("createdAt",createdAt)};companion object{fun fromJson(o:JSONObject)=PromptActionRecord(o.optString("id"),o.optString("prompt"),o.optString("aiId").ifBlank{null},o.optString("aiName").ifBlank{null},runCatching{PromptActionType.valueOf(o.optString("action"))}.getOrDefault(PromptActionType.COPY),o.optLong("createdAt"))}}
private fun ProjectTask.toJson()=JSONObject().apply{put("id",id);put("projectId",projectId);put("title",title);put("priority",priority.name);put("status",status.name);put("detail",detail)}
private fun ProjectMemoryEntry.toJson()=JSONObject().apply{put("id",id);put("projectId",projectId);put("type",type.name);put("title",title);put("content",content);put("createdAt",createdAt)}
private fun SkillDefinition.toJson()=JSONObject().apply{put("id",id);put("name",name);put("steps",JSONArray(steps));put("projectAware",projectAware)}
private fun BrowserContextSnapshot.toJson()=JSONObject().apply{put("selectedTabId",selectedTabId);put("tabIds",JSONArray(tabIds));put("source",source);put("prompt",prompt)}
private fun GithubWorkspaceState.toJson()=JSONObject().apply{put("repository",repository);put("branch",branch);put("lastCommit",lastCommit);put("ciStatus",ciStatus.name);put("userApprovalRequired",userApprovalRequired)}
private fun ProjectWorkspaceContext.toJson()=JSONObject().apply{put("projectId",projectId);put("projectName",projectName);put("stack",stack);put("objective",objective);put("currentState",currentState);put("resources",JSONArray(resources))}
private fun ProjectChatContext.toJson()=JSONObject().apply{put("workspace",workspace.toJson());put("selectedFiles",JSONArray(selectedFiles));put("selectedContributions",JSONArray(selectedContributions));put("recentMemory",JSONArray(recentMemory.map{it.toJson().toString()}))}
private fun WorkspaceValidationReport.toJson()=JSONObject().apply{put("items",JSONArray(items.map{JSONObject().apply{put("name",it.name);put("level",it.level.name);put("status",it.status.name);put("details",it.details)}.toString()}))}
private fun parseTask(o:JSONObject)=ProjectTask(o.optString("id"),o.optString("projectId").ifBlank{null},o.optString("title"),runCatching{TaskPriority.valueOf(o.optString("priority"))}.getOrDefault(TaskPriority.NORMAL),runCatching{TaskStatus.valueOf(o.optString("status"))}.getOrDefault(TaskStatus.PENDING),o.optString("detail"))
private fun parseMemory(o:JSONObject)=ProjectMemoryEntry(o.optString("id"),o.optString("projectId"),runCatching{MemoryType.valueOf(o.optString("type"))}.getOrDefault(MemoryType.NOTE),o.optString("title"),o.optString("content"),o.optLong("createdAt"))
private fun parseSkill(o:JSONObject):SkillDefinition{val a=o.optJSONArray("steps")?:JSONArray();return SkillDefinition(o.optString("id"),o.optString("name"),(0 until a.length()).map{a.optString(it)},o.optBoolean("projectAware",true))}
private fun parseBrowserContext(o:JSONObject):BrowserContextSnapshot{val a=o.optJSONArray("tabIds")?:JSONArray();return BrowserContextSnapshot(o.optString("selectedTabId").ifBlank{null},(0 until a.length()).map{a.optString(it)},o.optString("source").ifBlank{null},o.optString("prompt").ifBlank{null})}
private fun parseGithubState(o:JSONObject)=GithubWorkspaceState(o.optString("repository").ifBlank{null},o.optString("branch").ifBlank{null},o.optString("lastCommit").ifBlank{null},runCatching{ValidationStatus.valueOf(o.optString("ciStatus"))}.getOrDefault(ValidationStatus.NOT_VERIFIED),o.optBoolean("userApprovalRequired",true))
private fun parseWorkspaceContext(o:JSONObject):ProjectWorkspaceContext{val a=o.optJSONArray("resources")?:JSONArray();return ProjectWorkspaceContext(o.optString("projectId"),o.optString("projectName"),o.optString("stack"),o.optString("objective"),o.optString("currentState"),(0 until a.length()).map{a.optString(it)})}
private fun parseChatContext(o:JSONObject):ProjectChatContext{val f=o.optJSONArray("selectedFiles")?:JSONArray();val c=o.optJSONArray("selectedContributions")?:JSONArray();val m=o.optJSONArray("recentMemory")?:JSONArray();return ProjectChatContext(parseWorkspaceContext(o.optJSONObject("workspace")),(0 until f.length()).map{f.optString(it)},(0 until c.length()).map{c.optString(it)},(0 until m.length()).map{parseMemory(JSONObject(m.optString(it)))})}
private fun parseValidationReport(o:JSONObject):WorkspaceValidationReport{val a=o.optJSONArray("items")?:JSONArray();return WorkspaceValidationReport((0 until a.length()).map{val i=JSONObject(a.optString(it));ValidationItem(i.optString("name"),runCatching{ValidationLevel.valueOf(i.optString("level"))}.getOrDefault(ValidationLevel.LOCAL),runCatching{ValidationStatus.valueOf(i.optString("status"))}.getOrDefault(ValidationStatus.NOT_VERIFIED),i.optString("details"))})}
