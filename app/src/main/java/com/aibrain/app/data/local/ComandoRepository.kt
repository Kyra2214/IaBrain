package com.aibrain.app.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class ComandoRepository(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).comandoDao()
    suspend fun ensureSeed() = withContext(Dispatchers.IO) {
        if (dao.contar() > 0) return@withContext
        val root = context.assets.open("comandos_catalogo.json").use { JSONObject(BufferedReader(InputStreamReader(it)).readText()) }
        val array = root.getJSONArray("commands")
        val agora = System.currentTimeMillis()
        val comandos = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            ComandoEntity(o.getString("id"), o.getString("slug"), o.getString("nome"), o.getString("comando"), o.getString("categoria"), o.getString("descricaoCurta"), o.getString("explicacao"), o.getString("objetivo"), o.getString("quandoUsar"), o.getString("quandoNaoUsar"), o.getString("sintaxe"), o.getString("exemplo"), emptyList(), o.getString("iaRecomendada"), o.getString("modoExecucao"), o.optBoolean("suportaWeb"), o.optBoolean("suportaArquivos"), o.optBoolean("suportaProjeto"), o.optBoolean("suportaMultiplasIAs"), o.getString("nivel"), true, false, 0, agora, agora)
        }
        dao.inserirTodos(comandos)
        val ids = comandos.map { it.id }.toSet()
        val capacidades = comandos.map { ComandoCapacidadeEntity(it.id, it.categoria.uppercase().replace(' ', '_'), true, 1) }
        val relacoes = listOf(
            "research" to listOf("source", "factcheck", "evidence", "compare", "deepresearch"),
            "implement" to listOf("analyze", "architecture", "spec", "code", "test", "review", "debug"),
            "deepresearch" to listOf("multiai", "aidebate", "aiconsensus", "aiverify", "aisynthesis")
        ).flatMap { (origem, destinos) -> destinos.filter { it in ids }.mapIndexed { index, destino -> ComandoRelacionamentoEntity(origem, destino, "RECOMMENDS", index) } }
        database().comandoGrafoDao().salvarCapacidades(capacidades)
        database().comandoGrafoDao().salvarRelacionamentos(relacoes)
    }
    private fun database() = AppDatabase.getInstance(context)
    suspend fun pesquisar(termo: String = "", categoria: String = "", limite: Int = 40, offset: Int = 0) = dao.pesquisar(termo.trim(), categoria, limite, offset)
    suspend fun categorias() = dao.categorias()
    suspend fun buscar(id: String) = dao.buscar(id)
    suspend fun alternarFavorito(id: String, atual: Boolean) = dao.marcarFavorito(id, !atual, System.currentTimeMillis())
    suspend fun registrarUso(id: String) = dao.registrarUso(id, System.currentTimeMillis())
}
