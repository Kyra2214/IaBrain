package com.aibrain.app.data

import android.content.Context
import com.aibrain.app.model.CategoriaPrompt
import com.aibrain.app.model.Prompt
import com.aibrain.app.model.VariavelPrompt
import org.json.JSONArray
import org.json.JSONObject

/**
 * Armazenamento local (SharedPreferences) para dados pessoais do usuário
 * sobre a Biblioteca de Prompts — mesmo princípio "leve, sem dependências
 * extras" do [FavoritosRepository] (Fase 7.1).
 *
 * Fase 16.14 — criado como base mínima e direta para as ordenações "Mais
 * utilizados" e "Favoritos" (contagem de uso e conjunto de favoritos por
 * ID de prompt).
 * Fase 16.15 — [alternarFavorito] adiciona a ação de favoritar/desfavoritar,
 * consumida por [BibliotecaActivity] (item da lista) e [DetalhePromptActivity]
 * (tela de detalhes).
 * Fase 16.16 — [registrarUtilizacao] passa também a registrar a ordem de
 * utilização ([obterHistorico]), mesmo padrão de
 * [FavoritosRepository.registrarAcesso]/`obterHistorico` (Fase 7.3).
 * Fase 17.15 — [salvarPromptGerado]/[obterPromptsGerados] guardam os prompts
 * criados pelo Prompt Builder (Fase 17.6-17.14) como novos itens da
 * Biblioteca, serializados em JSON (mesmo schema de campos usado por
 * [com.aibrain.app.repository.PromptRepository] para o asset embutido) já
 * que não podem ser gravados de volta no `prompts_biblioteca.json` do APK.
 */
class PromptDadosLocaisRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NOME = "ai_brain_prompts_prefs"
        private const val CHAVE_FAVORITOS = "prompts_favoritos_ids"
        private const val CHAVE_CONTAGENS_USO = "prompts_contagens_uso"
        private const val CHAVE_HISTORICO = "prompts_historico_ids"
        private const val LIMITE_HISTORICO = 20
        private const val CHAVE_PROMPTS_GERADOS = "prompts_gerados_json"
    }

    // ---- Favoritos (Fase 16.15) ----

    fun isFavorito(idPrompt: String): Boolean = obterFavoritos().contains(idPrompt)

    /** Alterna o estado de favorito do prompt e retorna o novo estado (true = agora é favorito). */
    fun alternarFavorito(idPrompt: String): Boolean {
        val atuais = obterFavoritos().toMutableSet()
        val novoEstado = if (atuais.contains(idPrompt)) {
            atuais.remove(idPrompt)
            false
        } else {
            atuais.add(idPrompt)
            true
        }
        prefs.edit().putStringSet(CHAVE_FAVORITOS, atuais).apply()
        return novoEstado
    }

    fun obterFavoritos(): Set<String> =
        prefs.getStringSet(CHAVE_FAVORITOS, emptySet()) ?: emptySet()

    // ---- Contagem de utilização (base para a ordenação "Mais utilizados") ----

    /**
     * Registra uma utilização do prompt (copiar/compartilhar): incrementa
     * sua contagem (Fase 16.14) e o move para o topo do histórico (Fase 16.16).
     */
    fun registrarUtilizacao(idPrompt: String) {
        val contagens = obterContagensUso().toMutableMap()
        contagens[idPrompt] = (contagens[idPrompt] ?: 0) + 1
        salvarContagens(contagens)

        val historico = obterHistorico().toMutableList()
        historico.remove(idPrompt)
        historico.add(0, idPrompt)
        while (historico.size > LIMITE_HISTORICO) historico.removeAt(historico.lastIndex)
        prefs.edit().putString(CHAVE_HISTORICO, historico.joinToString(",")).apply()
    }

    fun obterContagemUso(idPrompt: String): Int = obterContagensUso()[idPrompt] ?: 0

    /** Todas as contagens de uso registradas, por ID de prompt. */
    fun obterContagensUso(): Map<String, Int> {
        val bruto = prefs.getString(CHAVE_CONTAGENS_USO, null) ?: return emptyMap()
        return bruto.split(",")
            .filter { it.isNotBlank() && it.contains(":") }
            .associate { par ->
                val (id, contagem) = par.split(":", limit = 2)
                id to (contagem.toIntOrNull() ?: 0)
            }
    }

    private fun salvarContagens(contagens: Map<String, Int>) {
        val serializado = contagens.entries.joinToString(",") { "${it.key}:${it.value}" }
        prefs.edit().putString(CHAVE_CONTAGENS_USO, serializado).apply()
    }

    // ---- Histórico de utilização (Fase 16.16) ----

    /** IDs dos últimos prompts copiados/usados, do mais recente para o mais antigo. */
    fun obterHistorico(): List<String> =
        prefs.getString(CHAVE_HISTORICO, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    // ---- Prompts gerados pelo Prompt Builder (Fase 17.15) ----

    /** Salva um novo prompt gerado (mais recente primeiro), preservando os já salvos. */
    fun salvarPromptGerado(prompt: Prompt) {
        val atuais = obterPromptsGerados().toMutableList()
        atuais.add(0, prompt)
        prefs.edit().putString(CHAVE_PROMPTS_GERADOS, serializarPrompts(atuais)).apply()
    }

    /** Todos os prompts gerados e salvos localmente, do mais recente para o mais antigo. */
    fun obterPromptsGerados(): List<Prompt> {
        val bruto = prefs.getString(CHAVE_PROMPTS_GERADOS, null) ?: return emptyList()
        return desserializarPrompts(bruto)
    }

    private fun serializarPrompts(prompts: List<Prompt>): String {
        val array = JSONArray()
        prompts.forEach { prompt ->
            val obj = JSONObject()
            obj.put("id", prompt.id)
            obj.put("titulo", prompt.titulo)
            obj.put("categoria", prompt.categoria.chave)
            obj.put("subcaso", prompt.subcaso)
            obj.put("descricao_curta", prompt.descricaoCurta)
            obj.put("objetivo", prompt.objetivo)
            obj.put("nivel", prompt.nivel)
            obj.put("melhor_para", JSONArray(prompt.melhorPara))
            obj.put("template", prompt.template)
            obj.put("variaveis", JSONArray(prompt.variaveis.map { v ->
                JSONObject().apply {
                    put("nome", v.nome)
                    v.padrao?.let { put("padrao", it) }
                }
            }))
            obj.put("tags", JSONArray(prompt.tags))
            obj.put("data_criacao", prompt.dataCriacao)
            array.put(obj)
        }
        return array.toString()
    }

    private fun desserializarPrompts(json: String): List<Prompt> {
        val array = JSONArray(json)
        val lista = mutableListOf<Prompt>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val categoria = CategoriaPrompt.porChave(obj.getString("categoria")) ?: continue

            val melhorPara = obj.optJSONArray("melhor_para")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()

            val variaveis = obj.optJSONArray("variaveis")?.let { arr ->
                (0 until arr.length()).map { idx ->
                    val v = arr.getJSONObject(idx)
                    VariavelPrompt(
                        nome = v.getString("nome"),
                        padrao = if (v.has("padrao")) v.optString("padrao") else null
                    )
                }
            } ?: emptyList()

            val tags = obj.optJSONArray("tags")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()

            lista.add(
                Prompt(
                    id = obj.getString("id"),
                    titulo = obj.getString("titulo"),
                    categoria = categoria,
                    subcaso = obj.getString("subcaso"),
                    descricaoCurta = obj.getString("descricao_curta"),
                    objetivo = obj.getString("objetivo"),
                    nivel = obj.getString("nivel"),
                    melhorPara = melhorPara,
                    template = obj.getString("template"),
                    variaveis = variaveis,
                    tags = tags,
                    dataCriacao = obj.getString("data_criacao")
                )
            )
        }
        return lista
    }
}
