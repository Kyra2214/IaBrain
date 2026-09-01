package com.aibrain.app.view

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.aibrain.app.databinding.ActivityGuiasBinding
import com.aibrain.app.model.Guia
import com.aibrain.app.repository.ColecaoRepository

class GuiasActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGuiasBinding
    private lateinit var adapter: ColecaoAdapter
    private val repository = ColecaoRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuiasBinding.inflate(layoutInflater)
        setContentView(binding.root)
        adapter = ColecaoAdapter(aoClicarGuia = ::mostrarDetalhe)
        binding.recyclerGuias.layoutManager = LinearLayoutManager(this)
        binding.recyclerGuias.adapter = adapter
        binding.btnVoltarGuias.setOnClickListener { finish() }
        adapter.mostrarGuias(repository.carregarGuias())
    }

    private fun mostrarDetalhe(guia: Guia) {
        val passos = guia.passos.mapIndexed { index, passo -> "${index + 1}. $passo" }.joinToString("\n")
        val ferramentas = guia.ferramentas.joinToString(", ")
        AlertDialog.Builder(this)
            .setTitle(guia.titulo)
            .setMessage("${guia.descricao}\n\n$passos\n\nFerramentas: $ferramentas")
            .setPositiveButton("Fechar", null)
            .show()
    }
}
