package com.aibrain.app.groq

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/**
 * Fase 18.8 — Gera o snippet JSON pré-preenchido a partir de uma
 * [SugestaoIA] (Fase 18.7), no mesmo formato de item usado em
 * `ia_catalogo.json` (campos espelham [com.aibrain.app.model.IA]).
 *
 * Pré-preenchimento apenas — a inserção efetiva no `ia_catalogo.json`
 * continua manual e revisada pelo curador (a Groq pode errar nome/site,
 * e campos como `descricao`/`logo`/`gratuita`/`notas` não vêm da sugestão
 * e ficam como placeholder para revisão), conforme o princípio de
 * curadoria humana já estabelecido na Fase 18.
 */
object SnippetCatalogoIA {

    fun gerar(sugestao: SugestaoIA): String {
        val objeto = JSONObject()
            .put("id", idSugerido(sugestao.nome))
            .put("nome", sugestao.nome)
            .put("logo", "")
            .put("site", sugestao.site)
            .put("descricao", "")
            .put("categorias", JSONArray(listOf(sugestao.categoriaSugerida)))
            .put("idiomas", JSONArray(emptyList<String>()))
            .put("gratuita", false)
            .put("notas", JSONObject())

        return objeto.toString(2)
    }

    /** `id` sugerido: nome sem acento/pontuação, em minúsculas, palavras separadas por `_`. */
    private fun idSugerido(nome: String): String {
        val semAcento = Normalizer.normalize(nome, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        return semAcento
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }
}
