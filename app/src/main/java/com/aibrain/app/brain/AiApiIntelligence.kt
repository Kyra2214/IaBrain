package com.aibrain.app.brain

/** AI API Intelligence: provider metadata, verification, routing and quota policy. Credentials never belong in the catalog. */
enum class AiApiAvailability { UNKNOWN, CREDENTIAL_REQUIRED, VERIFIED, QUOTA_EXHAUSTED, UNAVAILABLE, RETIRED }
enum class AiApiAccess { FREE_PERMANENT, FREE_TIER, PROMOTIONAL_CREDITS, PAID, BROWSER_ONLY, UNKNOWN }
enum class AiApiChannel { API, BROWSER, LOCAL }
enum class AiApiFailure { AUTH, QUOTA, RATE_LIMIT, SERVER, TIMEOUT, INVALID_RESPONSE, NETWORK, UNKNOWN }
enum class AiApiProjectPolicy { ASK_BEFORE_SWITCH, AUTO_SWITCH_COMPATIBLE, CONTINUE_FREE_ONLY, ALLOW_BROWSER_FALLBACK, STOP }

data class AiApiModel(val providerId:String,val modelId:String,val displayName:String,val capabilities:Set<String>,val access:AiApiAccess,val endpoint:String,val documentationUrl:String,val requiresKey:Boolean=true,val active:Boolean=true){init{require(providerId.isNotBlank()&&modelId.isNotBlank()&&displayName.isNotBlank());require(endpoint.isSafeHttps());require(documentationUrl.isSafeHttps())}}
data class AiApiProvider(val id:String,val name:String,val region:ExplorerRegion,val models:List<AiApiModel>,val officialUrl:String,val priority:Int=0){init{require(id.isNotBlank()&&name.isNotBlank());require(officialUrl.isSafeHttps());require(priority>=0)}}
data class AiApiVerification(val providerId:String,val modelId:String,val availability:AiApiAvailability,val httpStatus:Int?=null,val latencyMs:Long?=null,val checkedAtEpochMs:Long,val evidence:String="",val error:AiApiFailure?=null){init{require(providerId.isNotBlank()&&modelId.isNotBlank());require(checkedAtEpochMs>=0);require(latencyMs==null||latencyMs>=0)}}
data class AiApiRoute(val selected:AiApiModel?,val alternatives:List<AiApiModel>,val reason:String)
data class AiApiQuotaEvent(val providerId:String,val modelId:String,val failure:AiApiFailure,val detectedAtEpochMs:Long)
data class AiApiSwitchDecision(val shouldAskUser:Boolean,val shouldSwitch:Boolean,val alternatives:List<AiApiModel>,val reason:String)

fun interface AiApiTransport{fun execute(model:AiApiModel,apiKey:String?,requestBody:String):AiApiTransportResult}
data class AiApiTransportResult(val httpStatus:Int,val body:String,val latencyMs:Long,val timedOut:Boolean=false)

class AiApiRouter{fun route(models:List<AiApiModel>,verifications:List<AiApiVerification>,requiredCapabilities:Set<String>,freeOnly:Boolean=false,excludeModelId:String?=null):AiApiRoute{val verified=verifications.associateBy{it.modelId};val eligible=models.filter{m->m.active&&m.modelId!=excludeModelId&&(!freeOnly||m.access in setOf(AiApiAccess.FREE_PERMANENT,AiApiAccess.FREE_TIER,AiApiAccess.PROMOTIONAL_CREDITS))&&requiredCapabilities.all{it in m.capabilities}&&verified[m.modelId]?.availability==AiApiAvailability.VERIFIED};val ordered=eligible.sortedByDescending{requiredCapabilities.count{c->c in it.capabilities}};return AiApiRoute(ordered.firstOrNull(),ordered.drop(1),if(ordered.isEmpty())"nenhuma API verificada compatível" else "API verificada com capacidades compatíveis")}}

class AiApiProjectPolicyEngine{fun decide(policy:AiApiProjectPolicy,alternatives:List<AiApiModel>,browserAvailable:Boolean,failure:AiApiFailure):AiApiSwitchDecision{val a=alternatives.filter{it.active};return when(policy){AiApiProjectPolicy.ASK_BEFORE_SWITCH->AiApiSwitchDecision(true,false,a,"projeto exige confirmação antes da troca");AiApiProjectPolicy.AUTO_SWITCH_COMPATIBLE->AiApiSwitchDecision(false,a.isNotEmpty(),a,"troca automática para API compatível");AiApiProjectPolicy.CONTINUE_FREE_ONLY->AiApiSwitchDecision(false,a.isNotEmpty(),a,"continuar somente com APIs gratuitas disponíveis");AiApiProjectPolicy.ALLOW_BROWSER_FALLBACK->AiApiSwitchDecision(false,a.isNotEmpty(),a,if(a.isNotEmpty())"usar API alternativa antes do navegador" else "usar navegador nativo como fallback");AiApiProjectPolicy.STOP->AiApiSwitchDecision(false,false,emptyList(),"projeto configurado para parar após falha de API")}.let{d->if(failure==AiApiFailure.AUTH&&d.alternatives.isEmpty()&&browserAvailable&&policy==AiApiProjectPolicy.ALLOW_BROWSER_FALLBACK)d.copy(reason="API exige autenticação; navegador nativo disponível como fallback")else d}}}

object AiApiSyncPolicy{const val TTL_MS=86_400_000L;fun isStale(lastSyncEpochMs:Long,nowEpochMs:Long)=lastSyncEpochMs<=0L||nowEpochMs<lastSyncEpochMs||nowEpochMs-lastSyncEpochMs>=TTL_MS}
object AiApiSecurityPolicy{private val blocked=listOf("127.","10.","192.168.","172.16.","172.17.","172.18.","172.19.","172.20.","172.21.","172.22.","172.23.","172.24.","172.25.","172.26.","172.27.","172.28.","172.29.","172.30.","172.31.");fun isSafeHttps(value:String)=value.isSafeHttps();internal fun safeHost(host:String)=!host.equals("localhost",true)&&blocked.none{host.startsWith(it)}}
private fun String.isSafeHttps()=runCatching{val u=java.net.URI(trim());u.scheme.equals("https",true)&&!u.host.isNullOrBlank()&&u.userInfo==null&&u.fragment==null&&AiApiSecurityPolicy.safeHost(u.host)}.getOrDefault(false)
