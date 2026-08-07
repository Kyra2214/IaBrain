package com.aibrain.app.repository

import android.content.Context
import com.aibrain.app.model.CategoriaPrompt
import com.aibrain.app.model.Prompt
import com.aibrain.app.model.VariavelPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

/**
 * Repositório responsável por ler a Biblioteca de Prompts a partir do JSON
 * local (app/src/main/assets/prompts_biblioteca.json).
 *
 * Fase 16.4 — mesmo padrão de [CatalogoRepository] (Fase 2.3): org.json
 * (sem dependências extras), leitura do asset embutido no APK. Módulo novo
 * e independente do catálogo de IAs (Fases 1-6) — sem sincronização remota
 * ou cache em memória ainda, escopo equivalente ao da Fase 2.3 original.
 */
class PromptRepository(private val context: Context) {

    companion object {
        private const val ARQUIVO_BIBLIOTECA = "prompts_biblioteca.json"
    }

    /**
     * Lê o asset local e retorna a lista de prompts da Biblioteca.
     * Executa em thread de I/O para não travar a UI.
     */
    suspend fun carregarBiblioteca(): List<Prompt> = withContext(Dispatchers.IO) {
        parsearBiblioteca(lerArquivoAssets(ARQUIVO_BIBLIOTECA))
    }

    private fun lerArquivoAssets(nomeArquivo: String): String {
        return try {
            context.assets.open(nomeArquivo).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw IllegalStateException("Não foi possível ler $nomeArquivo", e)
        }
    }

    private fun parsearBiblioteca(json: String): List<Prompt> {
        val raiz = JSONObject(json)
        val arrayPrompts = raiz.getJSONArray("prompts")
        val lista = mutableListOf<Prompt>()

        for (i in 0 until arrayPrompts.length()) {
            val obj = arrayPrompts.getJSONObject(i)

            val chaveCategoria = obj.getString("categoria")
            val categoria = CategoriaPrompt.porChave(chaveCategoria)
                ?: throw IllegalStateException("Categoria de prompt desconhecida: $chaveCategoria")

            val melhorPara = obj.optJSONArray("melhor_para")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()

            val variaveis = obj.getJSONArray("variaveis").let { arr ->
                (0 until arr.length()).map { idx ->
                    val v = arr.getJSONObject(idx)
                    VariavelPrompt(
                        nome = v.getString("nome"),
                        padrao = if (v.has("padrao")) v.optString("padrao") else null
                    )
                }
            }

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
