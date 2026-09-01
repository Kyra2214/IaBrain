package com.aibrain.app.view

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.aibrain.app.databinding.ActivityColecoesBinding
import com.aibrain.app.model.Colecao
import com.aibrain.app.repository.ColecaoRepository

class ColecoesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityColecoesBinding
    private lateinit var adapter: ColecaoAdapter
    private val repository = ColecaoRepository()
    private var todas: List<Colecao> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityColecoesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        adapter = ColecaoAdapter(aoClicarColecao = ::mostrarDetalhe)
        binding.recyclerColecoes.layoutManager = LinearLayoutManager(this)
        binding.recyclerColecoes.adapter = adapter
        binding.btnVoltarColecoes.setOnClickListener { finish() }
        todas = repository.carregarColecoes()
        adapter.mostrarColecoes(todas)
        binding.searchColecoes.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                val termo = newText.orEmpty().trim().lowercase()
                adapter.mostrarColecoes(todas.filter { it.titulo.lowercase().contains(termo) || it.descricao.lowercase().contains(termo) || it.itens.any { item -> item.lowercase().contains(termo) } })
                return true
            }
        })
    }

    private fun mostrarDetalhe(colecao: Colecao) {
        AlertDialog.Builder(this)
            .setTitle(colecao.titulo)
            .setMessage("${colecao.descricao}\n\nTemas incluídos:\n${colecao.itens.joinToString("\n") { "• $it" }}")
            .setPositiveButton("Fechar", null)
            .show()
    }
}
