package com.aibrain.app.view

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aibrain.app.cache.ImagemCache
import com.aibrain.app.data.FavoritosRepository
import com.aibrain.app.databinding.ActivityFavoritosBinding
import com.aibrain.app.model.IA
import com.aibrain.app.repository.CatalogoRepository
import com.aibrain.app.util.notaMedia
import kotlinx.coroutines.launch

/**
 * Tela de Favoritos (Fase 7.2) e Histórico de acesso (Fase 7.3).
 * Reaproveita o IAAdapter e o CatalogoRepository já existentes — aqui
 * apenas cruza os IDs salvos em [FavoritosRepository] com o catálogo completo.
 */
class FavoritosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritosBinding
    private lateinit var repositorioCatalogo: CatalogoRepository
    private lateinit var favoritosRepositorio: FavoritosRepository
    private lateinit var imagemCache: ImagemCache
    private lateinit var adapterFavoritos: IAAdapter
    private lateinit var adapterHistorico: IAAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repositorioCatalogo = CatalogoRepository(applicationContext)
        favoritosRepositorio = FavoritosRepository(applicationContext)
        imagemCache = ImagemCache(applicationContext)

        configurarListas()
        binding.btnVoltar.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        carregarDados()
    }

    private fun configurarListas() {
        adapterFavoritos = IAAdapter(
            escopo = lifecycleScope,
            imagemCache = imagemCache,
            aoClicar = { ia -> abrirDetalhe(ia) },
            aoAlternarFavorito = { ia ->
                favoritosRepositorio.alternarFavorita(ia.id)
                carregarDados()
            }
        )
        binding.recyclerFavoritos.layoutManager = LinearLayoutManager(this)
        binding.recyclerFavoritos.adapter = adapterFavoritos
        binding.recyclerFavoritos.isNestedScrollingEnabled = false

        adapterHistorico = IAAdapter(
            escopo = lifecycleScope,
            imagemCache = imagemCache,
            aoClicar = { ia -> abrirDetalhe(ia) },
            aoAlternarFavorito = { ia ->
                favoritosRepositorio.alternarFavorita(ia.id)
                carregarDados()
            }
        )
        binding.recyclerHistorico.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistorico.adapter = adapterHistorico
        binding.recyclerHistorico.isNestedScrollingEnabled = false
    }

    private fun abrirDetalhe(ia: IA) {
        favoritosRepositorio.registrarAcesso(ia.id)
        startActivity(DetalheIAActivity.criarIntent(this, ia))
    }

    private fun carregarDados() {
        lifecycleScope.launch {
            try {
                val catalogo = repositorioCatalogo.carregarCatalogo()
                val idsFavoritos = favoritosRepositorio.obterFavoritos()
                val idsHistorico = favoritosRepositorio.obterHistorico()

                val favoritas = catalogo.filter { it.id in idsFavoritos }
                    .sortedByDescending { it.notaMedia() }
                val historico = idsHistorico.mapNotNull { id -> catalogo.find { it.id == id } }

                exibirFavoritos(favoritas, idsFavoritos)
                exibirHistorico(historico, idsFavoritos)
            } catch (e: Exception) {
                exibirFavoritos(emptyList(), emptySet())
                exibirHistorico(emptyList(), emptySet())
            }
        }
    }

    private fun exibirFavoritos(lista: List<IA>, idsFavoritos: Set<String>) {
        binding.txtFavoritosVazio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerFavoritos.visibility = if (lista.isEmpty()) View.GONE else View.VISIBLE
        adapterFavoritos.submitList(lista)
        adapterFavoritos.atualizarFavoritos(idsFavoritos)
    }

    private fun exibirHistorico(lista: List<IA>, idsFavoritos: Set<String>) {
        binding.txtHistoricoVazio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerHistorico.visibility = if (lista.isEmpty()) View.GONE else View.VISIBLE
        adapterHistorico.submitList(lista)
        adapterHistorico.atualizarFavoritos(idsFavoritos)
    }
}
