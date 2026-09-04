package com.aibrain.app.brain

/** Local telemetry used to improve routing without sending project content anywhere. */
data class AiApiUsageMetric(val modelId:String,val successCount:Int=0,val failureCount:Int=0,val totalLatencyMs:Long=0L,val quotaFailures:Int=0){
    init{require(modelId.isNotBlank());require(successCount>=0&&failureCount>=0&&quotaFailures>=0);require(totalLatencyMs>=0)}
    val successRate:Double get()=(successCount.toDouble()/(successCount+failureCount).coerceAtLeast(1)).coerceIn(0.0,1.0)
    val averageLatencyMs:Long get()=if(successCount+failureCount==0)0L else totalLatencyMs/(successCount+failureCount)
}

class AiApiUsageTracker{private val metrics=linkedMapOf<String,AiApiUsageMetric>();fun record(modelId:String,success:Boolean,latencyMs:Long,quotaFailure:Boolean=false){val m=metrics[modelId]?:AiApiUsageMetric(modelId);metrics[modelId]=m.copy(successCount=m.successCount+if(success)1 else 0,failureCount=m.failureCount+if(success)0 else 1,totalLatencyMs=m.totalLatencyMs+latencyMs,quotaFailures=m.quotaFailures+if(quotaFailure)1 else 0)};fun snapshot(): List<AiApiUsageMetric> = metrics.values.toList()}

fun interface AiApiStrongReviewer{fun review(provider:AiApiProvider):AiApiReview}
data class AiApiReview(val providerId:String,val approved:Boolean,val confidence:Double,val notes:String){init{require(providerId.isNotBlank());require(confidence in 0.0..1.0)}}

class AiApiCatalogPolicy{fun usable(model:AiApiModel,verification:AiApiVerification):Boolean=model.active&&verification.availability==AiApiAvailability.VERIFIED}
