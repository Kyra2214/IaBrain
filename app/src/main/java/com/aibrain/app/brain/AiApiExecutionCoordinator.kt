package com.aibrain.app.brain

/** API execution boundary used by the orchestrator. It never invents a successful response. */
fun interface AiApiExecutor { fun execute(model:AiApiModel,apiKey:String?,prompt:String):AiApiExecutionResult }
data class AiApiExecutionResult(val success:Boolean,val output:String="",val failure:AiApiFailure?=null,val latencyMs:Long?=null)
data class AiApiExecutionOutcome(val result:AiApiExecutionResult,val model:AiApiModel?,val switched:Boolean,val requiresUserConfirmation:Boolean,val alternatives:List<AiApiModel>,val reason:String)

class AiApiExecutionCoordinator(private val router:AiApiRouter=AiApiRouter(),private val policyEngine:AiApiProjectPolicyEngine=AiApiProjectPolicyEngine()) {
    fun execute(models:List<AiApiModel>,verifications:List<AiApiVerification>,requiredCapabilities:Set<String>,apiKeyFor:(AiApiModel)->String?,prompt:String,policy:AiApiProjectPolicy,browserAvailable:Boolean,executor:AiApiExecutor):AiApiExecutionOutcome {
        val route=router.route(models,verifications,requiredCapabilities,freeOnly=policy==AiApiProjectPolicy.CONTINUE_FREE_ONLY)
        val selected=route.selected ?: return AiApiExecutionOutcome(AiApiExecutionResult(false,failure=AiApiFailure.UNKNOWN),null,false,false,emptyList(),route.reason)
        val first=executor.execute(selected,apiKeyFor(selected),prompt)
        if(first.success)return AiApiExecutionOutcome(first,selected,false,false,route.alternatives,"API executada com sucesso")
        val failure=first.failure?:AiApiFailure.UNKNOWN
        val decision=policyEngine.decide(policy,route.alternatives,browserAvailable,failure)
        if(decision.shouldAskUser)return AiApiExecutionOutcome(first,selected,false,true,decision.alternatives,decision.reason)
        if(decision.shouldSwitch){for(alternative in decision.alternatives){val result=executor.execute(alternative,apiKeyFor(alternative),prompt);if(result.success)return AiApiExecutionOutcome(result,alternative,true,false,decision.alternatives,"troca automática após falha da API anterior");if(result.failure==AiApiFailure.QUOTA||result.failure==AiApiFailure.RATE_LIMIT)continue}}
        return AiApiExecutionOutcome(first,selected,false,false,decision.alternatives,decision.reason)
    }
}
