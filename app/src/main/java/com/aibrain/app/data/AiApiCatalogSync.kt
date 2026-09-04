package com.aibrain.app.data

import android.content.Context
import com.aibrain.app.data.local.AiApiCatalogEntity
import com.aibrain.app.data.local.AiApiSyncStateEntity
import com.aibrain.app.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Synchronizes the API catalog at most once per 24h. Network and Room work stay off the UI thread. */
class AiApiCatalogSync(context: Context) {
    private val appContext=context.applicationContext
    private val db=AppDatabase.getInstance(appContext)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)

    fun syncIfStale(nowEpochMs:Long=System.currentTimeMillis()){scope.launch{val state=runCatching{db.aiApiSyncStateDao().get()}.getOrNull();if(com.aibrain.app.brain.AiApiSyncPolicy.isStale(state?.lastSyncAt?:0L,nowEpochMs))runCatching{syncBlocking(nowEpochMs)}}}
    fun syncNow(nowEpochMs:Long=System.currentTimeMillis()): Result<Int> = runCatching{syncBlocking(nowEpochMs)}

    private fun syncBlocking(nowEpochMs:Long):Int{
        val raw=fetchRemoteCatalog()?:appContext.assets.open(LOCAL_ASSET).bufferedReader().use{it.readText()};val hash=sha256(raw);val root=JSONObject(raw);val version=root.optString("version","unknown");val providers=root.optJSONArray("providers")?:return 0
        val entities=buildList{
            for(i in 0 until providers.length()){
                val provider=providers.getJSONObject(i);val providerId=provider.optString("id");val providerName=provider.optString("name",providerId);val region=provider.optString("region","GLOBAL");val documentationUrl=provider.optString("documentationUrl",provider.optString("officialUrl"));val models=provider.optJSONArray("models")?:continue
                for(j in 0 until models.length()){
                    val model=models.getJSONObject(j);val modelId=model.optString("id");val endpoint=model.optString("endpoint");val capabilities=model.optJSONArray("capabilities")?:continue
                    if(providerId.isBlank()||modelId.isBlank()||endpoint.isBlank())continue
                    if(!com.aibrain.app.brain.AiApiSecurityPolicy.isSafeHttps(endpoint)||!com.aibrain.app.brain.AiApiSecurityPolicy.isSafeHttps(documentationUrl))continue
                    add(AiApiCatalogEntity(modelId,providerId,providerName,region,model.optString("name",modelId),(0 until capabilities.length()).map{capabilities.getString(it)}.joinToString("|"),model.optString("access","UNKNOWN"),endpoint,documentationUrl,model.optBoolean("requiresKey",true),true,"UNKNOWN",null,null,0L,"",null,provider.optString("officialUrl"),nowEpochMs))
                }
            }
        }
        if(entities.isNotEmpty())db.aiApiCatalogDao().upsertAll(entities);db.aiApiSyncStateDao().save(AiApiSyncStateEntity(1,nowEpochMs,version,hash));return entities.size
    }

    private fun fetchRemoteCatalog():String?=runCatching{val c=URL(REMOTE_URL).openConnection() as HttpURLConnection;c.connectTimeout=8_000;c.readTimeout=8_000;c.requestMethod="GET";c.setRequestProperty("Accept","application/json");c.connect();if(c.responseCode !in 200..299)return null;c.inputStream.bufferedReader().use{it.readText()}.takeIf{it.isNotBlank()}}.getOrNull()
    private fun sha256(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){ "%02x".format(it) }
    companion object{private const val LOCAL_ASSET="ai_api_catalog.json";private const val REMOTE_URL="https://raw.githubusercontent.com/Kyra2214/IaBrain/main/app/src/main/assets/ai_api_catalog.json"}
}
