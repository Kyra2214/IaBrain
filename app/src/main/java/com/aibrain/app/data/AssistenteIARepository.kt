package com.aibrain.app.data

import android.content.Context

/**
 * Fase 18.1 — Armazenamento local da API key da Groq, usada exclusivamente
 * pelo Assistente de IA (Fase 18) para curadoria de novas IAs a adicionar
 * ao catálogo. Mesmo princípio leve (SharedPreferences, sem dependências
 * extras) do [FavoritosRepository]/[com.aibrain.app.data.PromptDadosLocaisRepository].
 *
 * A chave nunca é embutida no APK nem enviada a nenhum servidor além da
 * própria API da Groq nas chamadas feitas pelo cliente (Fase 18.4) — fica
 * salva só no aparelho do usuário, que a gera e cola manualmente.
 */
class AssistenteIARepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NOME = "ai_brain_prefs"
        private const val CHAVE_API_KEY_GROQ = "assistente_ia_api_key_groq"
    }

    fun temApiKey(): Boolean = !obterApiKey().isNullOrBlank()

    fun obterApiKey(): String? = prefs.getString(CHAVE_API_KEY_GROQ, null)

    fun salvarApiKey(apiKey: String) {
        prefs.edit().putString(CHAVE_API_KEY_GROQ, apiKey.trim()).apply()
    }

    fun removerApiKey() {
        prefs.edit().remove(CHAVE_API_KEY_GROQ).apply()
    }
}
