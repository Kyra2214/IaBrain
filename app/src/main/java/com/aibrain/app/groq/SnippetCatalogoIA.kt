package com.aibrain.app.groq

import com.aibrain.app.model.Categoria
import com.aibrain.app.model.IA
import com.aibrain.app.model.NivelAcesso
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.text.Normalizer

/**
 * Fase 18.8 — Gera a entrada completa de uma IA a partir de uma [SugestaoIA]
 * (Fase 18.7), no mesmo formato de item usado em `ia_catalogo.json`.
 *
 * Fase 26 — deixa de ser um "snippet para colar manualmente": agora produz
 * uma [IA] completa e pronta para persistência real:
 * - `id` derivado do site/nome (único, prefixo `curada-` para não colidir
 *   com os IDs do `ia_catalogo.json` embutido);
 * - `logo` apontando para o favicon do domínio (mesmo padrão da Fase 8,
 *   atualizado por [com.aibrain.app.cache.ImagemCache] depois se possível);
 * - `descricao` preenchida a partir de [SugestaoIA.descricao] (quando a
 *   Groq responder no formato estendido) ou placeholder revisável;
 * - `categorias`: a categoria sugerida da Groq — mapeada para a chave fixa
 *   quando ela corresponde a uma categoria do app, ou mantida como categoria
 *   nova (que ganha aba própria na tela principal);
 * - demais campos com defaults seguros (idiomas "en", nota 5, gratuito false).
 */
object SnippetCatalogoIA {

    fun gerar(sugestao: SugestaoIA): String {
        val objeto = JSONObject()
        // Chamadas separadas (sem encadeamento): em testes locais o `JSONObject`
        // do framework Android é mockado com retorno padrão, então `.put(...)`
        // encadeado viraria NPE. Fora dos testes, o encadeamento funcionaria,
        // mas o código separado funciona nos dois ambientes.
        objeto.put("id", idSugerido(sugestao))
        objeto.put("nome", sugestao.nome)
        objeto.put("logo", logoSugerido(sugestao.site))
        objeto.put("site", sugestao.site)
        objeto.put("descricao", descricaoSugerida(sugestao))
        objeto.put("categorias", JSONArray().apply { put(categoriaSugerida(sugestao)) })
        objeto.put("idiomas", JSONArray().apply { put("en") })
        objeto.put("gratuita", false)
        objeto.put("acesso", NivelAcesso.PAGA.chave)
        objeto.put("notas", JSONObject().apply {
            put(categoriaSugerida(sugestao), 5)
        })
        // `toString(2)` retorna null nos testes locais (framework Android mockado
        // com valores padrão); no ambiente real ele sempre retorna o JSON válido.
        // O `orEmpty()` só ativa em teste, sem custo no app.
        return objeto.toString(2).orEmpty()
    }

    /** Fase 26 — converte a [SugestaoIA] em uma [IA] completa, pronta para persistir. */
    fun paraIA(sugestao: SugestaoIA): IA {
        val chaveCategoria = categoriaSugerida(sugestao)
        return IA(
            id = idSugerido(sugestao),
            nome = sugestao.nome.take(80),
            logo = logoSugerido(sugestao.site),
            site = sugestao.site,
            descricao = descricaoSugerida(sugestao).take(280),
            categorias = listOf(chaveCategoria),
            idiomas = listOf("en"),
            gratuita = false,
            acesso = NivelAcesso.PAGA,
            notas = mapOf(chaveCategoria to 5),
            categoriaPrincipal = chaveCategoria
        )
    }

    /**
     * Chave de categoria usada na persistência: quando a sugestão da Groq
     * corresponde (ignorando maiúsculas/acento) a uma categoria fixa do app,
     * usa a chave do enum; caso contrário a categoria nova é mantida como
     * está (capitalizada), ganhando aba própria na tela principal.
     */
    fun categoriaSugerida(sugestao: SugestaoIA): String {
        val rotulo = sugestao.categoriaSugerida.trim()
        Categoria.entries.firstOrNull {
            it.rotulo.equals(rotulo, ignoreCase = true) ||
                normalizar(it.rotulo) == normalizar(rotulo)
        }?.let { return it.chave }
        return capitalizar(rotulo).ifBlank { "Outras" }
    }

    private fun descricaoSugerida(sugestao: SugestaoIA): String = sugestao.descricao
        .takeIf { it.isNotBlank() }
        ?.trim()?.take(280)
        ?: "Nova IA adicionada pela curadoria. Descreva o que ela faz."

    private fun logoSugerido(site: String): String = try {
        "https://www.google.com/s2/favicons?domain=${URI(site).host}&sz=128"
    } catch (_: Exception) {
        ""
    }

    private fun idSugerido(sugestao: SugestaoIA): String {
        val base = try {
            URI(sugestao.site).host.orEmpty().removePrefix("www.").substringBefore('.')
        } catch (_: Exception) {
            ""
        }
        val sufixo = sugestao.nome
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(28)
        return "curada-${base.ifBlank { "ia" }}-${sufixo.ifBlank { "nova" }}"
    }

    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

    private fun capitalizar(texto: String): String = texto
        .lowercase()
        .split(" ")
        .filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
