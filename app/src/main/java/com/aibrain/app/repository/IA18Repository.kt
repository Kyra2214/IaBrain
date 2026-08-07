package com.aibrain.app.repository

import android.content.Context
import com.aibrain.app.model.IA
import org.json.JSONObject
import java.io.InputStreamReader

class IA18Repository(private val context: Context) {

    data class Categoria18(
        val id: String,
        val nome: String,
        val emoji: String,
        val descricao: String
    )

    data class IA18(
        val id: String,
        val nome: String,
        val logo: String,
        val site: String,
        val descricao: String,
        val categoriaId: String,
        val status: String
    )

    fun carregarCategorias(): List<Categoria18> {
        return try {
            val json = carregarJson()
            val categoriasArray = json.getJSONArray("categorias")
            List(categoriasArray.length()) { i ->
                val obj = categoriasArray.getJSONObject(i)
                Categoria18(
                    id = obj.getString("id"),
                    nome = obj.getString("nome"),
                    emoji = obj.getString("emoji"),
                    descricao = obj.getString("descricao")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun carregarIAs(categoriaId: String? = null): List<IA18> {
        return try {
            val json = carregarJson()
            val iasArray = json.getJSONArray("ias")
            val list = mutableListOf<IA18>()
            for (i in 0 until iasArray.length()) {
                val obj = iasArray.getJSONObject(i)
                val ia = IA18(
                    id = obj.getString("id"),
                    nome = obj.getString("nome"),
                    logo = obj.getString("logo"),
                    site = obj.getString("site"),
                    descricao = obj.getString("descricao"),
                    categoriaId = obj.getString("categoria_id"),
                    status = obj.getString("status")
                )
                if (categoriaId == null || ia.categoriaId == categoriaId) {
                    list.add(ia)
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun carregarJson(): JSONObject {
        val inputStream = context.assets.open("ia_18_catalogo.json")
        val reader = InputStreamReader(inputStream)
        val content = reader.readText()
        reader.close()
        return JSONObject(content)
    }

    fun confirmarIdade() {
        val prefs = context.getSharedPreferences("ia_18_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("idade_confirmada", true).apply()
    }

    fun isIdadeConfirmada(): Boolean {
        val prefs = context.getSharedPreferences("ia_18_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("idade_confirmada", false)
    }
}
