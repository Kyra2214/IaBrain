package com.aibrain.app.data.local

import android.content.Context

/** Memória persistente e local do projeto, armazenada no contexto Room existente. */
class ProjetoContextoRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).projetoContextoDao()

    suspend fun buscar(projetoId: String): ProjetoContextoEntity? = dao.buscar(projetoId)

    suspend fun salvar(contexto: ProjetoContextoEntity) = dao.salvar(contexto)

    suspend fun registrarEvento(
        projeto: ProjetoEntity,
        evento: String,
        resultado: String? = null,
        erro: String? = null
    ) {
        val agora = System.currentTimeMillis()
        val atual = dao.buscar(projeto.id)
        val linha = buildString {
            append("[").append(agora).append("] ")
            append(evento.trim())
            resultado?.takeIf { it.isNotBlank() }?.let { append(" | resultado: ").append(it.trim()) }
            erro?.takeIf { it.isNotBlank() }?.let { append(" | erro: ").append(it.trim()) }
        }
        val memoriaAtual = atual?.memoria.orEmpty().trim()
        val memoria = if (memoriaAtual.isBlank()) linha else "$memoriaAtual\n$linha"
        val limitada = memoria.lines().takeLast(MAX_EVENTOS).joinToString("\n")
        val contexto = atual?.copy(
            memoria = limitada,
            estadoAtual = evento.trim(),
            atualizadoEm = agora
        ) ?: ProjetoContextoEntity(
            id = "contexto-${projeto.id}",
            projetoId = projeto.id,
            objetivo = projeto.descricao,
            stack = listOfNotNull(projeto.plataforma),
            memoria = limitada,
            preferencias = "",
            estadoAtual = evento.trim(),
            recursos = emptyList(),
            atualizadoEm = agora
        )
        dao.salvar(contexto)
    }

    suspend fun resumo(projetoId: String): String = dao.buscar(projetoId)?.let { contexto ->
        buildString {
            if (contexto.objetivo.isNotBlank()) append("Objetivo: ${contexto.objetivo}\n")
            if (contexto.stack.isNotEmpty()) append("Stack: ${contexto.stack.joinToString(", ")}\n")
            if (contexto.estadoAtual.isNotBlank()) append("Estado atual: ${contexto.estadoAtual}\n")
            if (contexto.preferencias.isNotBlank()) append("Preferências: ${contexto.preferencias}\n")
            if (contexto.memoria.isNotBlank()) append("Memória: ${contexto.memoria}")
        }.trim()
    }.orEmpty()

    companion object {
        private const val MAX_EVENTOS = 50
    }
}
