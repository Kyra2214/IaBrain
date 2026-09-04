package com.aibrain.app.brain

import java.net.HttpURLConnection
import java.net.URL

/** Minimal real API probe. It only sends a tiny request to a catalogued model after a key is supplied. */
class AiApiHttpTestRunner(private val connectTimeoutMs:Int=10_000,private val readTimeoutMs:Int=15_000,private val transport:AiApiTransport?=null){
    fun test(model:AiApiModel,apiKey:String?,nowEpochMs:Long):AiApiVerification{
        if(model.requiresKey&&apiKey.isNullOrBlank())return AiApiVerification(model.providerId,model.modelId,AiApiAvailability.CREDENTIAL_REQUIRED,checkedAtEpochMs=nowEpochMs,evidence="API key não configurada")
        if(transport!=null)return injected(model,apiKey,nowEpochMs)
        val started=System.nanoTime()
        return try{
            val c=(URL(model.endpoint.trimEnd('/')+"/chat/completions").openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=connectTimeoutMs;readTimeout=readTimeoutMs;doOutput=true;setRequestProperty("Accept","application/json");setRequestProperty("Content-Type","application/json");apiKey?.takeIf{it.isNotBlank()}?.let{setRequestProperty("Authorization","Bearer $it")}}
            c.outputStream.use{it.write(requestBody(model.modelId).toByteArray(Charsets.UTF_8))}
            val status=c.responseCode;val stream=if(status in 200..299)c.inputStream else c.errorStream;val response=stream?.bufferedReader()?.use{it.readText()}.orEmpty().take(2000);classify(model,status,response,(System.nanoTime()-started)/1_000_000L,nowEpochMs)
        }catch(_:java.net.SocketTimeoutException){AiApiVerification(model.providerId,model.modelId,AiApiAvailability.UNAVAILABLE,latencyMs=(System.nanoTime()-started)/1_000_000L,checkedAtEpochMs=nowEpochMs,error=AiApiFailure.TIMEOUT)}catch(_:Exception){AiApiVerification(model.providerId,model.modelId,AiApiAvailability.UNAVAILABLE,latencyMs=(System.nanoTime()-started)/1_000_000L,checkedAtEpochMs=nowEpochMs,error=AiApiFailure.NETWORK)}
    }
    private fun injected(model:AiApiModel,key:String?,now:Long)=runCatching{transport!!.execute(model,key,requestBody(model.modelId)).let{classify(model,it.httpStatus,it.body,it.latencyMs,now,it.timedOut)}}.getOrElse{AiApiVerification(model.providerId,model.modelId,AiApiAvailability.UNAVAILABLE,checkedAtEpochMs=now,error=AiApiFailure.NETWORK)}
    private fun classify(model:AiApiModel,status:Int,response:String,latency:Long,now:Long,timedOut:Boolean=false):AiApiVerification{val a=when{timedOut->AiApiAvailability.UNAVAILABLE;status in 200..299&&response.isNotBlank()->AiApiAvailability.VERIFIED;status==401||status==403->AiApiAvailability.CREDENTIAL_REQUIRED;status==429&&response.containsAnyIgnoreCase("quota","credit","balance")->AiApiAvailability.QUOTA_EXHAUSTED;status==429||status>=500->AiApiAvailability.UNAVAILABLE;else->AiApiAvailability.UNAVAILABLE};val e=when(a){AiApiAvailability.VERIFIED->null;AiApiAvailability.CREDENTIAL_REQUIRED->AiApiFailure.AUTH;AiApiAvailability.QUOTA_EXHAUSTED->AiApiFailure.QUOTA;else->AiApiFailure.INVALID_RESPONSE};return AiApiVerification(model.providerId,model.modelId,a,status,latency,now,response,e)}
    private fun requestBody(modelId:String)="{\"model\":\"${escape(modelId)}\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello from IaBrain\"}],\"max_tokens\":8}"
    private fun escape(v:String)=v.replace("\\","\\\\").replace("\"","\\\"")
    private fun String.containsAnyIgnoreCase(vararg values:String)=values.any{contains(it,true)}
}
