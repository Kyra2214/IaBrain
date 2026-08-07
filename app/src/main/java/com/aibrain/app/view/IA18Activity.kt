package com.aibrain.app.view

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aibrain.app.R
import com.aibrain.app.cache.ImagemCache
import com.aibrain.app.databinding.ActivityIa18Binding
import com.aibrain.app.repository.IA18Repository
import com.aibrain.app.viewmodel.IA18ViewModel
import com.google.android.material.chip.Chip

class IA18Activity : AppCompatActivity() {

    private lateinit var binding: ActivityIa18Binding
    private lateinit var viewModel: IA18ViewModel
    private lateinit var adapter: IA18Adapter
    private lateinit var imagemCache: ImagemCache

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        viewModel = ViewModelProvider(this)[IA18ViewModel::class.java]
        
        // Verificação de segurança: se não confirmou idade, redireciona
        if (!viewModel.isIdadeConfirmada()) {
            startActivity(Intent(this, IA18VerificacaoActivity::class.java))
            finish()
            return
        }

        binding = ActivityIa18Binding.inflate(layoutInflater)
        setContentView(binding.root)

        imagemCache = ImagemCache(this)
        
        configurarToolbar()
        configurarLista()
        observarDados()
        
        viewModel.carregarDados()
    }

    private fun configurarToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnConfiguracoes18.setOnClickListener {
            // Placeholder para configurações independentes
            android.widget.Toast.makeText(this, "Configurações +18 em breve", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun configurarLista() {
        adapter = IA18Adapter(
            escopo = lifecycleScope,
            imagemCache = imagemCache,
            aoClicar = { ia ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ia.site))
                startActivity(intent)
            }
        )
        binding.recyclerIAs18.layoutManager = LinearLayoutManager(this)
        binding.recyclerIAs18.adapter = adapter
    }

    private fun observarDados() {
        viewModel.categorias.observe(this) { categorias ->
            configurarChips(categorias)
        }

        viewModel.ias.observe(this) { ias ->
            adapter.submitList(ias)
            binding.containerVazio18.visibility = if (ias.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.categoriaSelecionada.observe(this) { categoria ->
            binding.txtDescricaoCategoria.text = categoria?.descricao ?: ""
        }
    }

    private fun configurarChips(categorias: List<IA18Repository.Categoria18>) {
        val grupo = binding.chipGroupCategorias18
        grupo.removeAllViews()

        categorias.forEach { categoria ->
            val chip = Chip(this).apply {
                text = "${categoria.emoji} ${categoria.nome}"
                isCheckable = true
                isClickable = true
                tag = categoria
                id = View.generateViewId()
            }
            grupo.addView(chip)
            
            if (viewModel.categoriaSelecionada.value?.id == categoria.id) {
                chip.isChecked = true
            }
        }

        grupo.setOnCheckedStateChangeListener { group, checkedIds ->
            val chipId = checkedIds.firstOrNull()
            if (chipId != null) {
                val categoria = group.findViewById<Chip>(chipId).tag as IA18Repository.Categoria18
                viewModel.selecionarCategoria(categoria)
            }
        }
    }
}
