package com.aibrain.app.brain

import com.aibrain.app.model.ApiContractReport
import com.aibrain.app.model.ApiEndpoint
import com.aibrain.app.model.PublicApi

/** Adapts known API metadata to the existing v3.1 contract-analysis primitives. */
object ApiContractIntelligence {
    fun analyze(api: PublicApi): ApiContractReport {
        val contractPath = "api-${api.id}.kt"
        val source = renderKnownContract(api)
        val graph = SoftwareFactoryRuntimeV31().contractGraph(mapOf(contractPath to source))
        val symbols = graph.nodes.map { it.symbol }
        val unknown = unknownFields(api)
        val changes = SoftwareFactoryIntelligenceV31.compareContracts(emptyList(), symbols)
            .filter { it.breaking }
            .map { it.reason }
            .distinct()
        val summary = if (api.endpoints.isEmpty()) {
            "UNKNOWN: a fonte não informou endpoints, schemas ou parâmetros."
        } else {
            "${api.endpoints.size} endpoint(s) conhecido(s); campos ausentes permanecem UNKNOWN."
        }
        return ApiContractReport(
            knownEndpointCount = api.endpoints.size,
            methods = api.endpoints.map { it.normalizedMethod }.distinct().sorted(),
            symbols = symbols.map { it.name }.distinct().sorted(),
            unknownFields = unknown,
            breakingChanges = changes,
            resolution = graph.resolution.name,
            summary = summary
        )
    }

    private fun renderKnownContract(api: PublicApi): String = buildString {
        append("interface ApiContract")
        append(safeIdentifier(api.id))
        append(" {\n")
        api.endpoints.forEachIndexed { index, endpoint ->
            append("    // ${endpoint.normalizedMethod} ${endpoint.normalizedPath}\n")
            append("    fun ")
            append(endpointFunctionName(endpoint, index))
            append("(): Unit\n")
        }
        append("}\n")
    }

    private fun endpointFunctionName(endpoint: ApiEndpoint, index: Int): String {
        val method = endpoint.normalizedMethod.lowercase().replace(Regex("[^a-z0-9]"), "")
        return (method.ifBlank { "unknown" } + "Endpoint" + index).replaceFirstChar { it.lowercase() }
    }

    private fun unknownFields(api: PublicApi): List<String> {
        if (api.endpoints.isEmpty()) return listOf("endpoints", "methods", "parameters", "headers", "schemas", "requestBody", "response")
        val unknown = mutableListOf<String>()
        if (api.endpoints.all { it.parameters.isEmpty() }) unknown += "parameters"
        if (api.endpoints.all { it.headers.isEmpty() }) unknown += "headers"
        if (api.endpoints.all { it.schema.isNullOrBlank() }) unknown += "schemas"
        if (api.endpoints.all { it.requestBody.isNullOrBlank() }) unknown += "requestBody"
        if (api.endpoints.all { it.response.isNullOrBlank() }) unknown += "response"
        return unknown
    }

    private fun safeIdentifier(value: String): String = value
        .replace(Regex("[^A-Za-z0-9_]"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
        .ifBlank { "unknown" }
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
