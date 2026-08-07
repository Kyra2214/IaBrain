package com.aibrain.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.Intent
import com.aibrain.app.cache.ImagemCache
import com.aibrain.app.data.FavoritosRepository
import com.aibrain.app.databinding.ActivityMainBinding
import com.aibrain.app.model.Categoria
import com.aibrain.app.model.IA
import com.aibrain.app.repository.AtualizacaoRepository
import com.aibrain.app.repository.CatalogoRepository
import com.aibrain.app.view.AIBrainActivity
import com.aibrain.app.view.AssistenteIAActivity
import com.aibrain.app.view.BibliotecaActivity
import com.aibrain.app.view.CriadorPromptsActivity
import com.aibrain.app.view.DetalheIAActivity
import com.aibrain.app.view.FavoritosActivity
import com.aibrain.app.view.IAAdapter
import com.aibrain.app.viewmodel.MainViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * MainActivity — Tela inicial (Fase 6.1).
 * Carrega o catálogo via CatalogoRepository e exibe a listagem completa
 * de IAs em um RecyclerView.
 *
 * Fase 12.4 — pesquisa/filtro/categoria/ordenação vivem no [MainViewModel],
 * que sobrevive a mudanças de configuração (rotação de tela) sem recarregar
 * o catálogo nem perder os filtros aplicados.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repositorio: CatalogoRepository
    private lateinit var favoritosRepositorio: FavoritosRepository
    private lateinit var atualizacaoRepositorio: AtualizacaoRepository
    private lateinit var imagemCache: ImagemCache
    private lateinit var adapter: IAAdapter
    private lateinit var viewModel: MainViewModel
    private lateinit var chipTodas: Chip

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        repositorio = CatalogoRepository(applicationContext)
        favoritosRepositorio = FavoritosRepository(applicationContext)
        atualizacaoRepositorio = AtualizacaoRepository(applicationContext)
        imagemCache = ImagemCache(applicationContext)

        configurarLista()
        configurarPesquisa()
        configurarChipsCategorias()
        configurarOrdenacao()
        configurarBotaoFavoritos()
        configurarBotaoLimparFiltros()
        observarResultado()
        carregarCatalogoSeNecessario()
    }

    override fun onResume() {
        super.onResume()
        // Fase 7.1 — reflete favoritos alterados na tela de Favoritos/Detalhe ao voltar para cá.
        if (::adapter.isInitialized) {
            adapter.atualizarFavoritos(favoritosRepositorio.obterFavoritos())
        }
    }

    private fun configurarLista() {
        adapter = IAAdapter(
            escopo = lifecycleScope,
            imagemCache = imagemCache,
            aoClicar = { ia ->
                favoritosRepositorio.registrarAcesso(ia.id)
                startActivity(DetalheIAActivity.criarIntent(this, ia))
            },
            aoAlternarFavorito = { ia ->
                val agoraFavorita = favoritosRepositorio.alternarFavorita(ia.id)
                adapter.atualizarFavoritos(favoritosRepositorio.obterFavoritos())
                val mensagem = if (agoraFavorita) {
                    getString(R.string.favorito_adicionado, ia.nome)
                } else {
                    getString(R.string.favorito_removido, ia.nome)
                }
                Snackbar.make(binding.root, mensagem, Snackbar.LENGTH_SHORT).show()
            }
        )
        binding.recyclerIAs.layoutManager = LinearLayoutManager(this)
        binding.recyclerIAs.adapter = adapter
        configurarPaginacao()
    }

    /**
     * Fase 14.1 — Lazy loading: quando o usuário rola perto do fim da lista já
     * carregada, pede mais itens ao [viewModel] (que só amplia a janela sobre
     * o resultado já filtrado/ordenado, sem refazer o filtro).
     */
    private fun configurarPaginacao() {
        val gatilho = 5 // pede a próxima página quando faltam N itens pro fim
        binding.recyclerIAs.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val totalItens = layoutManager.itemCount
                val ultimoVisivel = layoutManager.findLastVisibleItemPosition()
                if (ultimoVisivel >= totalItens - 1 - gatilho) {
                    viewModel.carregarMaisItens()
                }
            }
        })
    }

    /** Fase 7.2 — Abre a tela de favoritos/histórico. Fase 16.9 — acesso à Biblioteca de Prompts. Fase 17.1 — acesso ao Criador de Prompts. */
    private fun configurarBotaoFavoritos() {
        binding.btnAbrirFavoritos.setOnClickListener {
            startActivity(Intent(this, FavoritosActivity::class.java))
        }
        binding.btnAbrirAIBrain.setOnClickListener {
            startActivity(Intent(this, AIBrainActivity::class.java))
        }
        binding.btnAbrirBiblioteca.setOnClickListener {
            startActivity(Intent(this, BibliotecaActivity::class.java))
        }
        binding.btnAbrirCriadorPrompts.setOnClickListener {
            startActivity(Intent(this, CriadorPromptsActivity::class.java))
        }
        binding.btnAbrirAssistenteIA.setOnClickListener {
            startActivity(Intent(this, AssistenteIAActivity::class.java))
        }
    }

    /**
     * Fase 6.2 — Pesquisa por nome ou função (categoria).
     * Delega o estado para o [viewModel]; o filtro roda lá, não aqui.
     */
    private fun configurarPesquisa() {
        binding.searchIAs.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.definirTermoPesquisa(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.definirTermoPesquisa(newText.orEmpty())
                return true
            }
        })
    }

    /**
     * Fase 6.3 — Filtro por categoria.
     * Gera um Chip "Todas" + um Chip por categoria do enum [Categoria].
     */
    private fun configurarChipsCategorias() {
        val grupo = binding.chipGroupCategorias

        chipTodas = criarChip(getString(R.string.filtro_todas), null)
        chipTodas.isChecked = true
        grupo.addView(chipTodas)

        Categoria.entries.forEach { categoria ->
            grupo.addView(criarChip("${categoria.emoji} ${categoria.rotulo}", categoria))
        }

        grupo.setOnCheckedStateChangeListener { group, checkedIds ->
            val chipId = checkedIds.firstOrNull()
            val categoria = if (chipId != null) {
                group.findViewById<Chip>(chipId)?.tag as? Categoria
            } else {
                null
            }
            viewModel.definirCategoria(categoria)
        }
    }

    private fun criarChip(texto: String, categoria: Categoria?): Chip {
        return Chip(this).apply {
            text = texto
            tag = categoria
            isCheckable = true
            isClickable = true
        }
    }

    /**
     * Fase 6.4 — Ranking / Populares / Novidades.
     * Botões de seleção única; nenhum selecionado = ordem original do catálogo.
     */
    private fun configurarOrdenacao() {
        binding.toggleOrdenacao.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val ordenacao = when (checkedId) {
                binding.btnOrdenarRanking.id -> MainViewModel.Ordenacao.RANKING
                binding.btnOrdenarPopulares.id -> MainViewModel.Ordenacao.POPULARES
                binding.btnOrdenarNovidades.id -> MainViewModel.Ordenacao.NOVIDADES
                else -> MainViewModel.Ordenacao.NENHUMA
            }
            viewModel.definirOrdenacao(ordenacao)
        }
    }

    /** Observa o resultado já filtrado/ordenado que o [viewModel] expõe e atualiza a tela. */
    private fun observarResultado() {
        viewModel.resultado.observe(this) { ias ->
            exibirResultado(ias)
        }
    }

    /**
     * Fase 13.6 — Botão "Limpar filtros": some quando nenhum filtro está ativo
     * (observando [MainViewModel.filtroAtivo]) e, ao ser tocado, reseta pesquisa,
     * categoria e ordenação tanto no [viewModel] quanto nos widgets da tela.
     */
    private fun configurarBotaoLimparFiltros() {
        viewModel.filtroAtivo.observe(this) { ativo ->
            binding.btnLimparFiltros.visibility = if (ativo) View.VISIBLE else View.GONE
        }
        binding.btnLimparFiltros.setOnClickListener {
            binding.searchIAs.setQuery("", false)
            binding.searchIAs.clearFocus()
            chipTodas.isChecked = true
            binding.toggleOrdenacao.clearChecked()
            viewModel.limparFiltros()
        }
    }

    /**
     * Fase 12.4 — só busca o catálogo (JSON local + sincronização remota) se o
     * [viewModel] ainda não tiver um catálogo carregado. Em rotação de tela o
     * ViewModel sobrevive, então evitamos releitura e piscar de tela à toa.
     */
    private fun carregarCatalogoSeNecessario() {
        if (viewModel.catalogoCarregado) return
        lifecycleScope.launch {
            try {
                val ias = repositorio.carregarCatalogo()
                viewModel.definirCatalogo(ias)
                sincronizarCatalogoRemoto()
            } catch (e: Exception) {
                exibirErro()
            }
        }
    }

    /**
     * Fase 8 — Atualização automática do catálogo em segundo plano.
     * Não bloqueia a exibição inicial (que já usa o catálogo local/cache);
     * se houver versão remota mais nova, o catálogo do ViewModel é atualizado.
     *
     * Fase 14.2 — a sincronização deixa de ser silenciosa: exibe um indicador
     * fino no topo da tela (mesmo padrão de ProgressBar indeterminado já usado
     * no "Perguntar" do AI Brain, Fase 12.2) enquanto verifica/baixa o catálogo
     * remoto, e o esconde ao final, com ou sem atualização.
     */
    private fun sincronizarCatalogoRemoto() {
        lifecycleScope.launch {
            binding.progressSincronizandoCatalogo.visibility = View.VISIBLE
            try {
                val versaoBase = repositorio.versaoDoAssetEmbutido()
                val atualizou = atualizacaoRepositorio.verificarEAtualizar(versaoBase)
                if (atualizou) {
                    viewModel.definirCatalogo(repositorio.carregarCatalogo())
                }
            } finally {
                binding.progressSincronizandoCatalogo.visibility = View.GONE
            }
        }
    }

    private fun exibirResultado(ias: List<IA>) {
        if (ias.isEmpty()) {
            binding.txtVazioOuErro.text = getString(R.string.lista_vazia)
            binding.containerVazioOuErro.visibility = View.VISIBLE
            binding.recyclerIAs.visibility = View.GONE
        } else {
            binding.containerVazioOuErro.visibility = View.GONE
            binding.recyclerIAs.visibility = View.VISIBLE
            adapter.submitList(ias)
        }
    }

    private fun exibirErro() {
        binding.txtVazioOuErro.text = getString(R.string.lista_erro)
        binding.containerVazioOuErro.visibility = View.VISIBLE
        binding.recyclerIAs.visibility = View.GONE
    }
}
