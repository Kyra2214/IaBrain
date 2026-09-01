package com.aibrain.app.brain

import android.content.Context
import com.aibrain.app.command.SlashCommandParser
import com.aibrain.app.data.local.ComandoRepository

class RoomCommandResolver(private val context: Context, private val database: com.aibrain.app.data.local.AppDatabase = com.aibrain.app.data.local.AppDatabase.getInstance(context)) {
    private val comandos = ComandoRepository(context.applicationContext)
    private val registry = IACapabilityRegistry(context, database)
    suspend fun resolve(raw: String): RoutingRequest? {
        comandos.ensureSeed()
        val parsed = SlashCommandParser.parse(raw) ?: return null
        val definition = database.comandoDao().buscarPorComando(parsed.comando, parsed.comando.removePrefix("/")) ?: return null
        val capabilities = database.comandoGrafoDao().capacidades(definition.id).map { it.capacidade }
        return LocalAIRouter.request(raw, parsed, capabilities.toSet(), emptySet())
    }
    suspend fun candidates(): List<RoutingCandidate> = registry.candidates()
}
