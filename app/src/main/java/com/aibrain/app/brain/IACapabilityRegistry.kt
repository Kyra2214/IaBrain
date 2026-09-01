package com.aibrain.app.brain

import android.content.Context
import com.aibrain.app.data.local.AppDatabase
import com.aibrain.app.data.local.toDomain

/** Fonte local de RoutingCandidate; não executa providers nem faz rede. */
class IACapabilityRegistry(context: Context, private val database: AppDatabase = AppDatabase.getInstance(context)) {
    suspend fun candidates(): List<RoutingCandidate> = database.iaDao().listarAtivas().map { entity ->
        val perfil = database.iaCapabilityDao().porIA(entity.id)
        val categorias = entity.categorias.map { it.uppercase().replace(' ', '_') }.toSet()
        val capacidades = perfil.filterNot { it.especialidade }.map { it.capacidade }.toSet().ifEmpty { categorias }
        val especialidades = perfil.filter { it.especialidade }.map { it.capacidade }.toSet()
        val comandos = database.comandoGrafoDao().comandosDaIA(entity.id).map { "/${it.removePrefix("/")}" }.toSet()
        RoutingCandidate(entity.id, entity.nome, capacidades, comandos, especialidades, quality = DEFAULT_METRIC, speed = DEFAULT_METRIC, cost = DEFAULT_COST, supportsCode = "CODIGO" in capacidades, supportsFiles = "ARQUIVO" in capacidades, supportsImages = "IMAGEM" in capacidades, supportsWeb = "WEB" in capacidades || entity.plataformas.any { it.equals("web", true) }, supportsReasoning = "RACIOCINIO" in capacidades)
    }
    companion object { private const val DEFAULT_METRIC = 0.5; private const val DEFAULT_COST = 0.0 }
}
