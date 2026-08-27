package com.aibrain.app.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fase 8 — Atualização automática do catálogo (sincronização local vs remoto).
 * Usa HttpURLConnection nativo (sem Retrofit/OkHttp) para manter o app leve,
 * seguindo o mesmo princípio já usado no CatalogoRepository (Fase 2.3, org.json).
 *
 * Nunca sobrescreve favoritos/histórico — esses ficam em FavoritosRepository,
 * indexados por ID da IA, e não são tocados por este repositório.
 *
 * URL_CATALOGO_REMOTO aponta para o repositório público
 * https://github.com/Kyra2214/Arquivo-Json, servido via jsDelivr — para
 * publicar uma atualização, basta subir/substituir o arquivo
 * `ia_catalogo.json` (com `versao` incrementada) na raiz do repositório,
 * branch `main`.
 */
class AtualizacaoRepository(private val context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NOME = "ai_brain_prefs"
        private const val CHAVE_VERSAO_ATIVA = "catalogo_versao_ativa"
        private const val ARQUIVO_CACHE = "ia_catalogo_atualizado.json"
        private const val TIMEOUT_MS = 8000

        // Fase 8 (resolvido) — repositório público https://github.com/Kyra2214/Arquivo-Json,
        // servido via jsDelivr (CDN com cache, mais estável/rápido que raw.githubusercontent.com
        // e sem limite de rate por IP). Após cada atualização do ia_catalogo.json no repositório,
        // o cache do jsDelivr pode levar até algumas horas para refletir a mudança — para forçar
        // atualização imediata, acesse (uma vez, após o push):
        // https://purge.jsdelivr.net/gh/Kyra2214/Arquivo-Json@main/ia_catalogo.json
        private const val URL_CATALOGO_REMOTO =
            "https://cdn.jsdelivr.net/gh/Kyra2214/Arquivo-Json@main/ia_catalogo.json"
    }

    /** Fase 8.3 — versão atualmente ativa (do cache atualizado), ou -1 se nunca houve atualização. */
    fun versaoAtiva(): Int = prefs.getInt(CHAVE_VERSAO_ATIVA, -1)

    private fun definirVersaoAtiva(versao: Int) {
        prefs.edit().putInt(CHAVE_VERSAO_ATIVA, versao).apply()
    }

    private fun arquivoCache(): File = File(context.filesDir, ARQUIVO_CACHE)

    /** Fase 8.3 — catálogo atualizado salvo localmente (ou null se ainda não houve atualização). */
    fun catalogoCacheado(): String? = arquivoCache().takeIf { it.exists() }?.readText()

    /**
     * Fase 10.3 — descarta um cache local corrompido (ex.: gravação interrompida)
     * para que as próximas leituras voltem a usar o asset embutido até a
     * próxima atualização remota bem-sucedida.
     */
    fun descartarCacheCorrompido() {
        arquivoCache().delete()
        prefs.edit().remove(CHAVE_VERSAO_ATIVA).apply()
    }

    /**
     * Fase 8.1 (verificação de versão) + 8.2 (download) + 8.3 (aplicação do cache).
     * Compara a versão remota com a versão ativa (cache, ou [versaoLocalBase] se
     * nunca houve atualização) e só substitui o cache local se a remota for maior.
     *
     * Fase 10.3 — modo offline: qualquer falha (rede indisponível, HTTP de erro,
     * JSON remoto malformado) é tratada aqui e nunca propaga — mantém o catálogo
     * atual (cache local ou asset embutido) como fallback.
     *
     * @param versaoLocalBase versão do ia_catalogo.json embutido no APK.
     * @return true se uma atualização foi baixada e aplicada.
     */
    suspend fun verificarEAtualizar(versaoLocalBase: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = baixarJson(URL_CATALOGO_REMOTO)
            val objeto = JSONObject(json)
            val versaoRemota = objeto.optInt("versao", -1)
            validarCatalogo(objeto, versaoRemota)
            val versaoAtual = if (versaoAtiva() >= 0) versaoAtiva() else versaoLocalBase

            if (versaoRemota <= versaoAtual) return@withContext false

            // Escreve e valida o temporário antes do rename, evitando cache
            // parcial se o processo for interrompido durante a gravação.
            val destino = arquivoCache()
            val temporario = File(destino.parentFile, "${destino.name}.tmp")
            temporario.writeText(json)
            if (!temporario.renameTo(destino)) {
                temporario.delete()
                throw IllegalStateException("Não foi possível ativar o catálogo atualizado")
            }
            definirVersaoAtiva(versaoRemota)
            true
        } catch (e: Exception) {
            // Offline, serviço indisponível ou JSON remoto malformado —
            // mantém o catálogo atual (fallback para o último JSON salvo).
            false
        }
    }

    private fun validarCatalogo(objeto: JSONObject, versao: Int) {
        require(versao >= 0) { "Versão inválida" }
        val ias = objeto.optJSONArray("ias") ?: error("Catálogo sem lista de IAs")
        require(ias.length() > 0) { "Catálogo vazio" }
        val ids = mutableSetOf<String>()
        for (i in 0 until ias.length()) {
            val ia = ias.getJSONObject(i)
            val id = ia.optString("id").trim()
            val site = ia.optString("site").trim()
            require(id.isNotEmpty()) { "IA sem id" }
            require(ids.add(id)) { "ID duplicado: $id" }
            val uri = URI(site)
            require(uri.scheme == "https" && !uri.host.isNullOrBlank()) {
                "Site inseguro ou inválido para $id"
            }
        }
    }

    private fun baixarJson(urlStr: String): String {
        val conexao = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
        }
        try {
            if (conexao.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${conexao.responseCode}")
            }
            return conexao.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conexao.disconnect()
        }
    }
}
