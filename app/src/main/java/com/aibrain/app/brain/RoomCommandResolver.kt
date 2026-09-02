package com.aibrain.app.brain

import android.content.Context
import com.aibrain.app.command.SlashCommandParser
import com.aibrain.app.data.local.ComandoRepository

class RoomCommandResolver(private val context: Context, private val database: com.aibrain.app.data.local.AppDatabase = com.aibrain.app.data.local.AppDatabase.getInstance(context)) {
    private val comandos = ComandoRepository(context.applicationContext)
    private val registry = IACapabilityRegistry(context, database)
    suspend fun resolve(raw: String): RoutingRequest? {
        comandos.ensureSeed()
        val parsed = SlashCommandParser.parse(raw)
        if (parsed == null) return resolveTextoLivre(raw)
        return resolveComando(raw, parsed.comando, parsed)
    }

    private suspend fun resolveTextoLivre(raw: String): RoutingRequest? {
        val command = TextoLivreIntent.commandFor(raw) ?: return null
        val parsed = SlashCommandParser.parse("$command $raw") ?: return null
        return resolveComando(raw, command, parsed)
    }

    private suspend fun resolveComando(raw: String, command: String, parsed: com.aibrain.app.command.ParsedSlashCommand): RoutingRequest? {
        val definition = database.comandoDao().buscarPorComando(command, command.removePrefix("/")) ?: return null
        val capabilities = CAPACIDADES_REAIS_POR_COMANDO[command]
            ?: database.comandoGrafoDao().capacidades(definition.id).map { it.capacidade }.toSet()
        return LocalAIRouter.request(raw, parsed, capabilities.toSet(), emptySet())
    }
    suspend fun candidates(): List<RoutingCandidate> = registry.candidates()

    /** Faz a ponte entre categorias operacionais do comando e chaves que existem nas IAs. */
    companion object {
        private val CAPACIDADES_REAIS_POR_COMANDO = mapOf(
            "/implement" to setOf("CODIGO"),
            "/debug" to setOf("CODIGO"),
            "/test" to setOf("CODIGO"),
            "/review" to setOf("CODIGO"),
            "/research" to setOf("PESQUISA"),
            "/creative" to setOf("IMAGEM"),
            "/document" to setOf("ESCRITA"),
            "/data" to setOf("ANALISE"),
            "/analyzedata" to setOf("ANALISE")
        )
    }
}
