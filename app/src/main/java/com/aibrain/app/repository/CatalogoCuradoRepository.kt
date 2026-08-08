package com.aibrain.app.repository

import android.content.Context
import com.aibrain.app.model.IA
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Persiste apenas as IAs inseridas pela atualização automática via Groq Compound. */
class CatalogoCuradoRepository(private val context: Context) {

    companion object {
        private const val ARQUIVO = "ia_catalogo_curado.json"
    }

    private fun arquivo(): File = File(context.filesDir, ARQUIVO)

    fun lerJson(): String? = arquivo().takeIf { it.exists() }?.readText()

    /** Fase 26 — adiciona uma única IA convertida de sugestão da curadoria, retornando true se ela realmente entrou (sem duplicar). */
    suspend fun adicionarUma(ia: IA): Boolean = adicionar(listOf(ia)) == 1

    suspend fun adicionar(novas: List<IA>): Int = withContext(Dispatchers.IO) {
        if (novas.isEmpty()) return@withContext 0

        val atuais = try {
            lerJson()?.let(::parsear) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val chavesExistentes = atuais.flatMap { listOf(it.nome, it.site) }.map(::chave).toMutableSet()
        val realmenteNovas = novas.filter { ia ->
            val chaves = listOf(ia.nome, ia.site).map(::chave)
            if (chaves.any { it in chavesExistentes }) {
                false
            } else {
                chavesExistentes += chaves
                true
            }
        }
        if (realmenteNovas.isEmpty()) return@withContext 0

        val todos = atuais + realmenteNovas
        val raiz = JSONObject().put("versao", 1).put("ias", JSONArray().apply {
            todos.forEach { put(serializar(it)) }
        })
        arquivo().writeText(raiz.toString())
        realmenteNovas.size
    }

    private fun parsear(json: String): List<IA> {
        val raiz = JSONObject(json)
        val array = raiz.getJSONArray("ias")
        return (0 until array.length()).map { indice ->
            val obj = array.getJSONObject(indice)
            val categorias = obj.optJSONArray("categorias").let { arr ->
                if (arr == null) emptyList() else (0 until arr.length()).map { arr.getString(it) }
            }
            val idiomas = obj.optJSONArray("idiomas").let { arr ->
                if (arr == null) emptyList() else (0 until arr.length()).map { arr.getString(it) }
            }
            val notasObj = obj.optJSONObject("notas")
            val notas = if (notasObj == null) emptyMap() else notasObj.keys().asSequence()
                .associateWith { notasObj.optInt(it, 5) }
            IA(
                id = obj.getString("id"),
                nome = obj.getString("nome"),
                logo = obj.getString("logo"),
                site = obj.getString("site"),
                descricao = obj.getString("descricao"),
                categorias = categorias,
                idiomas = idiomas,
                gratuita = obj.optBoolean("gratuita", false),
                acesso = com.aibrain.app.model.NivelAcesso.porChave(obj.optString("acesso"))
                    ?: if (obj.optBoolean("gratuita", false)) com.aibrain.app.model.NivelAcesso.GRATUITA
                    else com.aibrain.app.model.NivelAcesso.PAGA,
                notas = notas,
                categoriaPrincipal = obj.optString("categoriaPrincipal").takeIf { it.isNotBlank() }
            )
        }
    }

    private fun serializar(ia: IA): JSONObject = JSONObject().apply {
        put("id", ia.id)
        put("nome", ia.nome)
        put("logo", ia.logo)
        put("site", ia.site)
        put("descricao", ia.descricao)
        put("categorias", JSONArray().apply { ia.categorias.forEach(::put) })
        put("idiomas", JSONArray().apply { ia.idiomas.forEach(::put) })
        put("gratuita", ia.gratuita)
        put("acesso", ia.acesso.chave)
        put("notas", JSONObject().apply { ia.notas.forEach { (chave, nota) -> put(chave, nota) } })
        ia.categoriaPrincipal?.let { put("categoriaPrincipal", it) }
    }

    private fun chave(valor: String): String = valor.trim().lowercase()
}
