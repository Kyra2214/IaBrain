package com.aibrain.app.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fase 21.1 — esqueleto do BrowserHistoryManager.
 * Persiste a sessão de abas entre aberturas do app: abas abertas, IA de
 * cada uma, URL atual, ordem e qual está ativa. SharedPreferences + JSON
 * (`org.json`), mesmo princípio leve de [com.aibrain.app.data.FavoritosRepository]
 * (Fase 7.1) e mesmo padrão de serialização de
 * [com.aibrain.app.data.PromptDadosLocaisRepository] (Fase 17.15).
 *
 * Fase 21.10 — [salvarSessao] grava a lista completa de [AbaNavegador]
 * (ordem da lista preservada — `JSONArray` mantém posição) e o id da aba
 * ativa.
 *
 * Fase 21.11 — [lerSessao] lê e desserializa essa sessão salva; a
 * recriação das abas em si (novo `WebView` por aba) é responsabilidade do
 * [BrowserTabManager.restaurarAbas], chamado pela `BrowserActivity`.
 */
class BrowserHistoryManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NOME = "ai_brain_browser_prefs"
        private const val CHAVE_ABAS = "browser_sessao_abas_json"
        private const val CHAVE_ID_ATIVA = "browser_sessao_id_aba_ativa"
    }

    /**
     * Fase 21.10 — salva a sessão completa de abas: todas as [AbaNavegador]
     * abertas (com IA, URL atual, URL inicial, histórico, estado de
     * navegação, posição de scroll e pin), na mesma ordem em que aparecem
     * na barra, e qual delas está ativa.
     */
    fun salvarSessao(abas: List<AbaNavegador>, idAbaAtiva: String?) {
        prefs.edit()
            .putString(CHAVE_ABAS, serializarAbas(abas))
            .putString(CHAVE_ID_ATIVA, idAbaAtiva)
            .apply()
    }

    private fun serializarAbas(abas: List<AbaNavegador>): String {
        val array = JSONArray()
        abas.forEach { aba ->
            val obj = JSONObject()
            obj.put("id", aba.id)
            obj.put("nome_ia", aba.nomeIA)
            obj.put("url_atual", aba.urlAtual)
            obj.put("icone_ia", aba.iconeIA)
            obj.put("url_inicial", aba.urlInicial)
            obj.put("historico", JSONArray(aba.historico))
            obj.put("pode_voltar", aba.podeVoltar)
            obj.put("pode_avancar", aba.podeAvancar)
            obj.put("posicao_scroll", aba.posicaoScroll)
            obj.put("ultima_atualizacao", aba.ultimaAtualizacao)
            obj.put("fixada", aba.fixada)
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Fase 21.11 — lê a sessão salva pela [salvarSessao] (Fase 21.10).
     * Retorna a lista de [AbaNavegador] (vazia se nunca houve sessão salva
     * ou se o JSON estiver corrompido) e o id da aba que estava ativa.
     */
    fun lerSessao(): Pair<List<AbaNavegador>, String?> {
        val json = prefs.getString(CHAVE_ABAS, null) ?: return emptyList<AbaNavegador>() to null
        val idAtiva = prefs.getString(CHAVE_ID_ATIVA, null)
        val abas = try {
            desserializarAbas(json)
        } catch (e: org.json.JSONException) {
            emptyList()
        }
        return abas to idAtiva
    }

    private fun desserializarAbas(json: String): List<AbaNavegador> {
        val array = JSONArray(json)
        return (0 until array.length()).map { indice ->
            val obj = array.getJSONObject(indice)
            val historicoArray = obj.optJSONArray("historico") ?: JSONArray()
            val urlAtual = obj.getString("url_atual")
            AbaNavegador(
                id = obj.getString("id"),
                nomeIA = obj.optString("nome_ia"),
                urlAtual = urlAtual,
                iconeIA = obj.optString("icone_ia"),
                urlInicial = obj.optString("url_inicial", urlAtual),
                historico = (0 until historicoArray.length()).map { historicoArray.getString(it) },
                podeVoltar = obj.optBoolean("pode_voltar", false),
                podeAvancar = obj.optBoolean("pode_avancar", false),
                posicaoScroll = obj.optInt("posicao_scroll", 0),
                ultimaAtualizacao = obj.optLong("ultima_atualizacao", System.currentTimeMillis()),
                fixada = obj.optBoolean("fixada", false)
            )
        }
    }
}
