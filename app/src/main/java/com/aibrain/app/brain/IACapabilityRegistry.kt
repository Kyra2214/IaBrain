package com.aibrain.app.brain

import android.content.Context
import com.aibrain.app.data.local.AppDatabase
import com.aibrain.app.data.local.toDomain
import com.aibrain.app.data.local.toEntity
import com.aibrain.app.repository.CatalogoRepository

/** Fonte local de RoutingCandidate; não executa providers nem faz rede. */
class IACapabilityRegistry(context: Context, private val database: AppDatabase = AppDatabase.getInstance(context)) {
    private val appContext = context.applicationContext

    suspend fun candidates(): List<RoutingCandidate> {
        garantirCatalogoNoRoom()
        return database.iaDao().listarAtivas().map { entity ->
        val perfil = database.iaCapabilityDao().porIA(entity.id)
        val categorias = entity.categorias.map { it.uppercase().replace(' ', '_') }.toSet()
        val capacidades = perfil.filterNot { it.especialidade }.map { it.capacidade }.toSet().ifEmpty { categorias }
        val especialidades = perfil.filter { it.especialidade }.map { it.capacidade }.toSet()
        val comandos = database.comandoGrafoDao().comandosDaIA(entity.id).map { "/${it.removePrefix("/")}" }.toSet()
        val profile = database.iaRoutingProfileDao().buscarPorIA(entity.id)
        RoutingCandidate(entity.id, entity.nome, capacidades, comandos, especialidades,
            quality = profile?.qualityScore ?: DEFAULT_METRIC, speed = profile?.speedScore ?: DEFAULT_METRIC, cost = profile?.costScore ?: DEFAULT_COST,
            supportsCode = "CODIGO" in capacidades, supportsFiles = "ARQUIVO" in capacidades, supportsImages = "IMAGEM" in capacidades,
            supportsWeb = "WEB" in capacidades || entity.plataformas.any { it.equals("web", true) }, supportsReasoning = "RACIOCINIO" in capacidades,
            reliability = profile?.reliabilityScore ?: DEFAULT_METRIC, contextQuality = profile?.contextScore ?: DEFAULT_METRIC, isDefaultProfile = profile == null)
        }
    }

    /**
     * O Room continua sendo a fonte consultada pelo Registry, mas a primeira
     * leitura pode acontecer antes da tela de projeto ter importado as IAs.
     * Nesse caso, espelha o catálogo sincronizado no mesmo armazenamento local;
     * não cria catálogo ou persistência paralelos.
     */
    private suspend fun garantirCatalogoNoRoom() {
        if (database.iaDao().listarAtivas().isNotEmpty()) return
        val catalogo = runCatching { CatalogoRepository(appContext).carregarCatalogoSincronizado() }.getOrDefault(emptyList())
        if (catalogo.isNotEmpty()) database.iaDao().salvarTodos(catalogo.map { it.toEntity() })
    }
    companion object { private const val DEFAULT_METRIC = 0.5; private const val DEFAULT_COST = 0.0 }
}
