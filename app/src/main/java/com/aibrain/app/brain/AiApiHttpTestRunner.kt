package com.aibrain.app.brain

import java.net.HttpURLConnection
import java.net.URL

/** Minimal real API probe. It only sends a tiny request to a catalogued model after a key is supplied. */
class AiApiHttpTestRunner(private val connectTimeoutMs: Int = 10_000, private val readTimeoutMs: Int = 15_000) {
    fun test(model: AiApiModel, apiKey: String?, nowEpochMs: Long): AiApiVerification {
        if (model.requiresKey && apiKey.isNullOrBlank()) return AiApiVerification(model.providerId, model.modelId, AiApiAvailability.CREDENTIAL_REQUIRED, checkedAtEpochMs = nowEpochMs, evidence = "API key não configurada")
        val started = System.nanoTime()
        return try {
            val connection = (URL(model.endpoint.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
                apiKey?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            val body = "{\"model\":\"${escape(model.modelId)}\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello from IaBrain\"}],\"max_tokens\":8}"
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty().take(2000)
            val latency = (System.nanoTime() - started) / 1_000_000L
            val availability = when {
                status in 200..299 && response.isNotBlank() -> AiApiAvailability.VERIFIED
                status == 401 || status == 403 -> AiApiAvailability.CREDENTIAL_REQUIRED
                status == 429 && response.containsAnyIgnoreCase("quota", "credit", "balance") -> AiApiAvailability.QUOTA_EXHAUSTED
                status == 429 -> AiApiAvailability.UNAVAILABLE
                status >= 500 -> AiApiAvailability.UNAVAILABLE
                else -> AiApiAvailability.UNAVAILABLE
            }
            AiApiVerification(model.providerId, model.modelId, availability, status, latency, nowEpochMs, response, when (availability) {
                AiApiAvailability.VERIFIED -> null
                AiApiAvailability.CREDENTIAL_REQUIRED -> AiApiFailure.AUTH
                AiApiAvailability.QUOTA_EXHAUSTED -> AiApiFailure.QUOTA
                else -> AiApiFailure.INVALID_RESPONSE
            })
        } catch (_: java.net.SocketTimeoutException) {
            AiApiVerification(model.providerId, model.modelId, AiApiAvailability.UNAVAILABLE, latencyMs = (System.nanoTime() - started) / 1_000_000L, checkedAtEpochMs = nowEpochMs, error = AiApiFailure.TIMEOUT)
        } catch (_: Exception) {
            AiApiVerification(model.providerId, model.modelId, AiApiAvailability.UNAVAILABLE, latencyMs = (System.nanoTime() - started) / 1_000_000L, checkedAtEpochMs = nowEpochMs, error = AiApiFailure.NETWORK)
        }
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
    private fun String.containsAnyIgnoreCase(vararg values: String): Boolean = values.any { contains(it, ignoreCase = true) }
}
