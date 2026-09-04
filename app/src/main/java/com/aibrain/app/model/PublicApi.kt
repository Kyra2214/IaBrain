package com.aibrain.app.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class ApiSource(val key: String, val label: String, val priority: Int) {
    OFFICIAL_DOCS("OFFICIAL_DOCS", "Documentação oficial", 100),
    OPENAPI("OPENAPI", "OpenAPI", 90),
    GITHUB("GITHUB", "GitHub", 80),
    PUBLIC_APIS_IO("PUBLIC_APIS_IO", "PublicAPIs.io", 70),
    WEB_DISCOVERY("WEB_DISCOVERY", "Web discovery", 60),
    USER("USER", "Usuário", 50),
    RAPID_API("RAPID_API", "RapidAPI", 40),
    UNKNOWN("UNKNOWN", "Origem desconhecida", 0);

    companion object {
        fun fromKey(value: String?): ApiSource = entries.firstOrNull { it.key == value } ?: UNKNOWN
    }
}

enum class ApiAuthentication(val key: String, val label: String) {
    NONE("NONE", "Sem autenticação"),
    API_KEY("API_KEY", "API key"),
    OAUTH("OAUTH", "OAuth"),
    BASIC("BASIC", "Basic auth"),
    TOKEN("TOKEN", "Token"),
    UNKNOWN("UNKNOWN", "Desconhecida");

    companion object {
        fun fromExternal(value: String?): ApiAuthentication {
            val normalized = value.orEmpty().trim().lowercase()
            return when {
                normalized.isBlank() || normalized == "null" || normalized == "no" || normalized == "none" -> NONE
                "api key" in normalized || "apikey" in normalized || "key" == normalized -> API_KEY
                "oauth" in normalized -> OAUTH
                "basic" in normalized -> BASIC
                "token" in normalized || "bearer" in normalized -> TOKEN
                else -> UNKNOWN
            }
        }

        fun fromKey(value: String?): ApiAuthentication = entries.firstOrNull { it.key == value } ?: UNKNOWN
    }
}

enum class ApiStatus(val key: String, val label: String) {
    DISCOVERED("DISCOVERED", "Descoberta"),
    APPROVED("APPROVED", "Aprovada"),
    REVIEW_REQUIRED("REVIEW_REQUIRED", "Revisão necessária"),
    REJECTED("REJECTED", "Rejeitada"),
    INACTIVE("INACTIVE", "Inativa"),
    UNKNOWN("UNKNOWN", "Desconhecida");

    companion object {
        fun fromKey(value: String?): ApiStatus = entries.firstOrNull { it.key == value } ?: UNKNOWN
    }
}

enum class ApiReviewDecision(val key: String, val label: String) {
    APPROVE("APPROVE", "Aprovar"),
    REVIEW_REQUIRED("REVIEW_REQUIRED", "Revisão necessária"),
    REJECT("REJECT", "Rejeitar")
}

@Parcelize
data class ApiEndpoint(
    val method: String,
    val path: String,
    val summary: String? = null,
    val parameters: List<String> = emptyList(),
    val headers: List<String> = emptyList(),
    val requestBody: String? = null,
    val response: String? = null,
    val schema: String? = null
) : Parcelable {
    val normalizedMethod: String get() = method.trim().uppercase().ifBlank { "UNKNOWN" }
    val normalizedPath: String get() = path.trim().ifBlank { "UNKNOWN" }
}

@Parcelize
data class PublicApi(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val baseUrl: String? = null,
    val documentationUrl: String? = null,
    val source: ApiSource = ApiSource.UNKNOWN,
    val authentication: ApiAuthentication = ApiAuthentication.UNKNOWN,
    val https: Boolean? = null,
    val endpoints: List<ApiEndpoint> = emptyList(),
    val capabilities: List<String> = emptyList(),
    val status: ApiStatus = ApiStatus.DISCOVERED,
    val reliability: Int? = null,
    val lastChecked: Long? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val sources: List<ApiSource> = emptyList()
) : Parcelable {
    val allSources: List<ApiSource>
        get() = (listOf(source) + sources).distinct().sortedByDescending { it.priority }

    val searchableText: String
        get() = listOf(name, description, category, baseUrl.orEmpty(), capabilities.joinToString(" "))
            .joinToString(" ")
            .lowercase()
}

data class ApiSecurityFinding(
    val severity: Severity,
    val code: String,
    val message: String
) {
    enum class Severity(val weight: Int, val label: String) {
        INFO(0, "Informativo"), WARNING(15, "Atenção"), BLOCKER(60, "Bloqueador")
    }
}

data class ApiSecurityReport(
    val findings: List<ApiSecurityFinding>,
    val score: Int,
    val checkedUrl: String?,
    val redirectsChecked: Boolean = false
) {
    val blockers: List<ApiSecurityFinding> get() = findings.filter { it.severity == ApiSecurityFinding.Severity.BLOCKER }
    val warnings: List<ApiSecurityFinding> get() = findings.filter { it.severity == ApiSecurityFinding.Severity.WARNING }
}

data class ApiContractReport(
    val knownEndpointCount: Int,
    val methods: List<String>,
    val symbols: List<String>,
    val unknownFields: List<String>,
    val breakingChanges: List<String>,
    val resolution: String,
    val summary: String
)

data class ApiAnalysis(
    val api: PublicApi,
    val security: ApiSecurityReport,
    val contract: ApiContractReport,
    val review: ApiReviewDecision,
    val score: Int
)
