package com.aibrain.app.brain

import android.content.Context
import com.aibrain.app.command.SlashCommandParser
import com.aibrain.app.data.local.ComandoRepository
import com.aibrain.app.data.local.IARepository

class RoomCommandResolver(private val context: Context) {
    private val comandos = ComandoRepository(context.applicationContext)
    private val ias = IARepository(context.applicationContext)
    suspend fun resolve(raw: String): RoutingRequest? {
        comandos.ensureSeed()
        val parsed = SlashCommandParser.parse(raw) ?: return null
        val definition = com.aibrain.app.data.local.AppDatabase.getInstance(context).comandoDao().buscarPorComando(parsed.comando, parsed.comando.removePrefix("/")) ?: return null
        val capabilities = com.aibrain.app.data.local.AppDatabase.getInstance(context).comandoGrafoDao().capacidades(definition.id).map { it.capacidade }
        return LocalAIRouter.request(raw, parsed, capabilities.toSet(), emptySet())
    }
    suspend fun candidates(): List<RoutingCandidate> = ias.listarAtivas().map { ia ->
        RoutingCandidate(ia.id, ia.nome, ia.categorias.map { it.uppercase().replace(' ','_') }.toSet(), emptySet(), ia.categorias.toSet(), quality = 0.5, supportsWeb = ia.plataformas.any { it.equals("web", true) })
    }
}
