package com.aibrain.app.command

/** Representa uma chamada slash sem criar um segundo motor de IA. */
data class ParsedSlashCommand(val comando: String, val argumentos: String, val parametros: Map<String, String>)

object SlashCommandParser {
    fun parse(texto: String): ParsedSlashCommand? {
        val t = texto.trim(); if (!t.startsWith('/')) return null
        val partes = t.split(Regex("\\s+"), limit = 2); val comando = partes[0].lowercase()
        val argumentos = partes.getOrNull(1).orEmpty()
        val params = Regex("([\\w-]+)=\\\"([^\\\"]*)\\\"|([\\w-]+)=([^\\s]+)").findAll(argumentos).associate {
            val nome = it.groups[1]?.value ?: it.groups[3]?.value.orEmpty()
            nome to (it.groups[2]?.value ?: it.groups[4]?.value.orEmpty())
        }
        return ParsedSlashCommand(comando, argumentos, params)
    }
}
