package com.aibrain.app.data

import android.content.Context

/**
 * Fase 7.1 — Favoritos (salvar/remover, armazenamento local).
 * Fase 7.3 — Histórico de acesso (últimas IAs abertas).
 *
 * Usa SharedPreferences (nativo do Android) para manter o app leve,
 * seguindo o mesmo princípio de "sem dependências pesadas" já aplicado
 * no CatalogoRepository (Fase 2.3).
 */
class FavoritosRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NOME = "ai_brain_prefs"
        private const val CHAVE_FAVORITOS = "favoritos_ids"
        private const val CHAVE_HISTORICO = "historico_ids"
        private const val LIMITE_HISTORICO = 20
    }

    // ---- Favoritos (7.1) ----

    fun isFavorita(idIA: String): Boolean = obterFavoritos().contains(idIA)

    /** Alterna o estado de favorito da IA e retorna o novo estado (true = agora é favorita). */
    fun alternarFavorita(idIA: String): Boolean {
        val atuais = obterFavoritos().toMutableSet()
        val novoEstado = if (atuais.contains(idIA)) {
            atuais.remove(idIA)
            false
        } else {
            atuais.add(idIA)
            true
        }
        prefs.edit().putStringSet(CHAVE_FAVORITOS, atuais).apply()
        return novoEstado
    }

    fun obterFavoritos(): Set<String> =
        prefs.getStringSet(CHAVE_FAVORITOS, emptySet()) ?: emptySet()

    // ---- Histórico (7.3) ----

    /** Registra o acesso a uma IA, deixando-a no topo do histórico (mais recente primeiro). */
    fun registrarAcesso(idIA: String) {
        val lista = obterHistorico().toMutableList()
        lista.remove(idIA)
        lista.add(0, idIA)
        while (lista.size > LIMITE_HISTORICO) lista.removeAt(lista.lastIndex)
        prefs.edit().putString(CHAVE_HISTORICO, lista.joinToString(",")).apply()
    }

    /** IDs das últimas IAs abertas, do mais recente para o mais antigo. */
    fun obterHistorico(): List<String> =
        prefs.getString(CHAVE_HISTORICO, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
}
