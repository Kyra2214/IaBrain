package com.aibrain.app.view

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aibrain.app.R
import com.aibrain.app.cache.ImagemCache
import com.aibrain.app.data.FavoritosRepository
import com.aibrain.app.databinding.ActivityFavoritosBinding
import com.aibrain.app.model.IA
import com.aibrain.app.repository.CatalogoRepository
import com.aibrain.app.util.notaMedia
import kotlinx.coroutines.launch

/** Favoritos e histórico alimentados pelo mesmo FavoritosRepository do restante do app. */
class FavoritosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritosBinding
    private lateinit var repositorioCatalogo: CatalogoRepository
    private lateinit var favoritosRepositorio: FavoritosRepository
    private lateinit var imagemCache: ImagemCache
    private lateinit var adapterFavoritos: IAAdapter
    private lateinit var adapterHistorico: IAAdapter
    private var ordenarPorNome = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repositorioCatalogo = CatalogoRepository(applicationContext)
        favoritosRepositorio = FavoritosRepository(applicationContext)
        imagemCache = ImagemCache(applicationContext)
        configurarListas()
        binding.btnVoltar.setOnClickListener { finish() }
        binding.btnOrdenarFavoritos.setOnClickListener {
            ordenarPorNome = !ordenarPorNome
            binding.btnOrdenarFavoritos.setText(if (ordenarPorNome) R.string.favoritos_ordenar_nome else R.string.favoritos_ordenar_ranking)
            carregarDados()
        }
    }

    override fun onResume() {
        super.onResume()
        carregarDados()
    }

    private fun configurarListas() {
        adapterFavoritos = criarAdapter { carregarDados() }
        binding.recyclerFavoritos.layoutManager = LinearLayoutManager(this)
        binding.recyclerFavoritos.adapter = adapterFavoritos
        binding.recyclerFavoritos.isNestedScrollingEnabled = false

        adapterHistorico = criarAdapter { carregarDados() }
        binding.recyclerHistorico.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistorico.adapter = adapterHistorico
        binding.recyclerHistorico.isNestedScrollingEnabled = false
    }

    private fun criarAdapter(aoAlternar: () -> Unit): IAAdapter = IAAdapter(
        escopo = lifecycleScope,
        imagemCache = imagemCache,
        aoClicar = { ia ->
            favoritosRepositorio.registrarAcesso(ia.id)
            startActivity(DetalheIAActivity.criarIntent(this, ia))
        },
        aoAlternarFavorito = { ia ->
            favoritosRepositorio.alternarFavorita(ia.id)
            aoAlternar()
        }
    )

    private fun carregarDados() {
        lifecycleScope.launch {
            try {
                val catalogo = repositorioCatalogo.carregarCatalogoSincronizado()
                val idsFavoritos = favoritosRepositorio.obterFavoritos()
                val idsHistorico = favoritosRepositorio.obterHistorico()
                val favoritas = catalogo.filter { it.id in idsFavoritos }.let(::ordenarFavoritos)
                val historico = idsHistorico.mapNotNull { id -> catalogo.find { it.id == id } }
                exibirFavoritos(favoritas, idsFavoritos)
                exibirHistorico(historico, idsFavoritos)
            } catch (_: Exception) {
                exibirFavoritos(emptyList(), emptySet())
                exibirHistorico(emptyList(), emptySet())
            }
        }
    }

    private fun ordenarFavoritos(ias: List<IA>): List<IA> = if (ordenarPorNome) {
        ias.sortedBy { it.nome.lowercase() }
    } else {
        ias.sortedWith(compareByDescending<IA> { it.notaMedia() }.thenBy { it.nome.lowercase() })
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
