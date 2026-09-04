package com.aibrain.app.brain

import com.aibrain.app.model.ApiAuthentication
import com.aibrain.app.model.ApiSecurityFinding
import com.aibrain.app.model.ApiSecurityReport
import com.aibrain.app.model.PublicApi
import java.net.URI

/**
 * Validates discovery metadata without calling an API or handling credentials.
 * Network health checks and redirects remain explicit future operations.
 */
object ApiSecurityAnalyzer {
    private val secretPattern = Regex("(?i)(?:sk-[a-z0-9]{16,}|AIza[0-9A-Za-z_-]{20,}|bearer\\s+[a-z0-9._-]{20,}|(?:api[_ -]?key|token)\\s*[:=]\\s*[a-z0-9._-]{20,})")

    fun analyze(api: PublicApi): ApiSecurityReport {
        val findings = mutableListOf<ApiSecurityFinding>()
        val urls = listOfNotNull(api.baseUrl, api.documentationUrl).distinct()
        urls.forEach { url ->
            when (validateUrl(url)) {
                UrlValidation.INVALID -> findings += blocker("INVALID_URL", "URL inválida: $url")
                UrlValidation.HTTP -> findings += blocker("HTTP", "A URL não utiliza HTTPS: $url")
                UrlValidation.SUSPICIOUS -> findings += blocker("SUSPICIOUS_DOMAIN", "Domínio local ou endereço IP não confiável: $url")
                UrlValidation.PATH_TRAVERSAL -> findings += blocker("TRAVERSAL", "A URL contém segmentos de traversal: $url")
                UrlValidation.VALID -> Unit
            }
        }
        if (urls.isEmpty()) {
            findings += warning("URL_UNKNOWN", "A fonte não informou uma URL verificável")
        }
        if (api.documentationUrl == null) {
            findings += warning("DOCUMENTATION_UNKNOWN", "Documentação não informada")
        }
        if (api.authentication == ApiAuthentication.UNKNOWN) {
            findings += warning("AUTH_UNKNOWN", "Método de autenticação desconhecido")
        } else if (api.authentication != ApiAuthentication.NONE) {
            findings += info("CREDENTIALS_EXTERNAL", "A API exige credencial do usuário; nenhuma credencial é armazenada")
        }
        if (api.endpoints.isEmpty()) {
            findings += info("ENDPOINTS_UNKNOWN", "A fonte não informou endpoints conhecidos")
        }
        val exposedText = buildString {
            append(listOf(api.name, api.description, api.baseUrl.orEmpty(), api.documentationUrl.orEmpty()).joinToString(" "))
            api.endpoints.forEach { endpoint ->
                append(' ')
                append(listOf(endpoint.method, endpoint.path, endpoint.summary.orEmpty(), endpoint.parameters.joinToString(" "), endpoint.headers.joinToString(" "), endpoint.requestBody.orEmpty(), endpoint.response.orEmpty(), endpoint.schema.orEmpty()).joinToString(" "))
            }
        }
        if (secretPattern.containsMatchIn(exposedText)) {
            findings += blocker("CREDENTIAL_EXPOSED", "O metadado contém um padrão semelhante a segredo ou token")
        }
        api.endpoints.forEach { endpoint ->
            val path = endpoint.normalizedPath
            if (path.contains("..") || path.contains("\\") || path.startsWith("javascript:", ignoreCase = true)) {
                findings += blocker("SUSPICIOUS_ENDPOINT", "Endpoint suspeito: $path")
            }
        }
        val score = (100 - findings.sumOf { it.severity.weight }).coerceIn(0, 100)
        return ApiSecurityReport(findings.distinctBy { it.code }, score, urls.firstOrNull(), redirectsChecked = false)
    }

    private fun validateUrl(value: String): UrlValidation {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return UrlValidation.INVALID
        val scheme = uri.scheme?.lowercase() ?: return UrlValidation.INVALID
        val host = uri.host ?: return UrlValidation.INVALID
        if (uri.userInfo != null || uri.path.orEmpty().split('/').any { it == ".." }) return UrlValidation.PATH_TRAVERSAL
        if (scheme != "https") return if (scheme == "http") UrlValidation.HTTP else UrlValidation.INVALID
        val suspiciousHost = isSuspiciousHost(host)
        return if (suspiciousHost) UrlValidation.SUSPICIOUS else UrlValidation.VALID
    }

    private fun isSuspiciousHost(rawHost: String): Boolean {
        val host = rawHost.trim().removePrefix("[").removeSuffix("]").lowercase()
        return host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0" ||
            isPrivateIpv4(host) || host == "::1" || host == "0:0:0:0:0:0:0:1" ||
            host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe8") ||
            host.startsWith("fe9") || host.startsWith("fea") || host.startsWith("feb") ||
            host.substringAfterLast(':', missingDelimiterValue = "").let { isPrivateIpv4(it) }
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4 || parts.any { it.toIntOrNull() == null }) return false
        val first = parts[0].toInt()
        val second = parts[1].toInt()
        return first == 10 || first == 127 || (first == 172 && second in 16..31) || (first == 192 && second == 168)
    }

    private fun info(code: String, message: String) = ApiSecurityFinding(ApiSecurityFinding.Severity.INFO, code, message)
    private fun warning(code: String, message: String) = ApiSecurityFinding(ApiSecurityFinding.Severity.WARNING, code, message)
    private fun blocker(code: String, message: String) = ApiSecurityFinding(ApiSecurityFinding.Severity.BLOCKER, code, message)

    private enum class UrlValidation { VALID, INVALID, HTTP, SUSPICIOUS, PATH_TRAVERSAL }
}
