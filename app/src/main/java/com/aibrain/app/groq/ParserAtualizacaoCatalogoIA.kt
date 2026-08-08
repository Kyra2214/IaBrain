package com.aibrain.app.groq

import com.aibrain.app.model.Categoria
import com.aibrain.app.model.IA
import com.aibrain.app.model.NivelAcesso
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** Resultado validado de uma atualização automática do catálogo. */
data class ResultadoAtualizacaoCatalogo(
    val novasIas: List<IA>,
    val ignoradas: Int
)

/** Converte a resposta JSON da Groq Compound em IAs seguras para persistência. */
object ParserAtualizacaoCatalogoIA {

    fun parsear(resposta: String, catalogoAtual: List<IA>): ResultadoAtualizacaoCatalogo {
        val bruto = resposta.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val inicio = bruto.indexOf('[')
        val fim = bruto.lastIndexOf(']')
        if (inicio < 0 || fim <= inicio) return ResultadoAtualizacaoCatalogo(emptyList(), 1)
        val array = try {
            JSONArray(bruto.substring(inicio, fim + 1))
        } catch (_: Exception) {
            return ResultadoAtualizacaoCatalogo(emptyList(), 1)
        }

        val existentesPorChave = catalogoAtual
            .flatMap { listOf(it.nome, it.site) }
            .map(::chave)
            .toSet()
        val novas = mutableListOf<IA>()
        var ignoradas = 0

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i)
            val ia = obj?.let { validar(it) }
            if (ia == null) {
                ignoradas++
                continue
            }

            val chavesDaNova = listOf(ia.nome, ia.site).map(::chave)
            if (chavesDaNova.any { it in existentesPorChave } ||
                chavesDaNova.any { nova -> novas.flatMap { listOf(it.nome, it.site) }.map(::chave).contains(nova) }
            ) {
                ignoradas++
                continue
            }
            novas += ia
        }

        return ResultadoAtualizacaoCatalogo(novas, ignoradas)
    }

    private fun validar(obj: JSONObject): IA? {
        val nome = obj.optString("nome").trim()
        val site = obj.optString("site").trim().removeSuffix("/")
        val descricao = obj.optString("descricao").trim()
        if (nome.length < 2 || site.isBlank() || descricao.isBlank()) return null
        if (!site.startsWith("https://") || !siteValido(site)) return null

        val categorias = lerCategorias(obj.optJSONArray("categorias"))
        if (categorias.isEmpty()) return null
        val idiomas = lerStrings(obj.optJSONArray("idiomas"), maximo = 8)
            .ifEmpty { listOf("en") }
        val categoriaPrincipal = obj.optString("categoriaPrincipal").trim()
            .takeIf { it in categorias }
        val acesso = NivelAcesso.porChave(obj.optString("acesso")) ?: return null
        val gratuita = obj.optBoolean("gratuita", acesso != NivelAcesso.PAGA)
        val notas = lerNotas(obj.optJSONObject("notas"), categorias)

        return IA(
            id = gerarId(site, nome),
            nome = nome.take(80),
            logo = "https://www.google.com/s2/favicons?domain=${URI(site).host}&sz=128",
            site = site,
            descricao = descricao.take(280),
            categorias = categorias,
            idiomas = idiomas,
            gratuita = gratuita,
            acesso = acesso,
            notas = notas,
            categoriaPrincipal = categoriaPrincipal ?: categorias.first()
        )
    }

    private fun lerCategorias(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length())
            .mapNotNull { Categoria.porChave(array.optString(it).trim())?.chave }
            .distinct()
            .take(5)
    }

    private fun lerStrings(array: JSONArray?, maximo: Int): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length())
            .map { array.optString(it).trim().lowercase() }
            .filter { it.matches(Regex("[a-z]{2,3}")) }
            .distinct()
            .take(maximo)
    }

    private fun lerNotas(obj: JSONObject?, categorias: List<String>): Map<String, Int> {
        if (obj == null) return categorias.associateWith { 5 }
        return categorias.associateWith { categoria -> obj.optInt(categoria, 5).coerceIn(0, 10) }
    }

    private fun siteValido(site: String): Boolean = try {
        val host = URI(site).host.orEmpty()
        host.contains('.') && !host.contains(' ')
    } catch (_: Exception) {
        false
    }

    private fun gerarId(site: String, nome: String): String {
        val base = URI(site).host.orEmpty().removePrefix("www.").substringBefore('.')
        val sufixo = nome.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(28)
        return "curada-${base.ifBlank { "ia" }}-${sufixo.ifBlank { "nova" }}"
    }

    private fun chave(valor: String): String = valor.trim().lowercase()
}
