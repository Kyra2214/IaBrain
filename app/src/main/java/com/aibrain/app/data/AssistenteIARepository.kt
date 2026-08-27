package com.aibrain.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Fase 18.1 — Armazenamento local da API key da Groq, usada exclusivamente
 * pelo Assistente de IA (Fase 18) para curadoria de novas IAs a adicionar
 * ao catálogo. O armazenamento local é criptografado usando Android Keystore por meio de
 * EncryptedSharedPreferences. A chave não deve ser tratada como segredo de
 * servidor: um usuário com controle do aparelho ainda pode extraí-la.
 *
 * A chave nunca é embutida no APK nem enviada a nenhum servidor além da
 * própria API da Groq nas chamadas feitas pelo cliente (Fase 18.4) — fica
 * salva só no aparelho do usuário, que a gera e cola manualmente.
 */
class AssistenteIARepository(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFS_NOME,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val PREFS_NOME = "ai_brain_secure_prefs"
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
