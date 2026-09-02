package com.aibrain.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aibrain.app.brain.BrainCatalogFilter
import com.aibrain.app.brain.BrainDiscoveryEngine
import com.aibrain.app.cache.ImagemCache
import com.aibrain.app.data.AssistenteIARepository
import com.aibrain.app.data.FavoritosRepository
import com.aibrain.app.databinding.ActivityMainBinding
import com.aibrain.app.model.Categoria
import com.aibrain.app.model.CategoriaDinamica
import com.aibrain.app.model.IA
import com.aibrain.app.model.NivelAcesso
import com.aibrain.app.groq.GroqClient
import com.aibrain.app.groq.ParserAtualizacaoCatalogoIA
import com.aibrain.app.groq.PromptAtualizacaoCatalogoIA
import com.aibrain.app.groq.ResultadoComFallback
import com.aibrain.app.groq.enviarComBuscaNaWeb
import com.aibrain.app.repository.AtualizacaoRepository
import com.aibrain.app.repository.CatalogoCuradoRepository
import com.aibrain.app.repository.CatalogoRepository
import com.aibrain.app.view.AssistenteIAActivity
import com.aibrain.app.view.ColecoesActivity
import com.aibrain.app.view.ComandosActivity
import com.aibrain.app.view.CriarComIAActivity
import com.aibrain.app.view.CriadorPromptsActivity
import com.aibrain.app.view.DetalheIAActivity
import com.aibrain.app.view.FavoritosActivity
import com.aibrain.app.view.GuiasActivity
import com.aibrain.app.view.CompararIAsActivity
import com.aibrain.app.view.IAAdapter
import com.aibrain.app.viewmodel.MainViewModel
import com.aibrain.app.navigation.GlobalNavigation
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Home do Brain: descobrir, entender e decidir a partir do catálogo existente. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repositorio: CatalogoRepository
    private lateinit var favoritosRepositorio: FavoritosRepository
    private lateinit var atualizacaoRepositorio: AtualizacaoRepository
    private lateinit var imagemCache: ImagemCache
    private lateinit var viewModel: MainViewModel
    private lateinit var adapterCatalogo: IAAdapter
    private lateinit var adapterDestaques: IAAdapter
    private lateinit var adapterFavoritos: IAAdapter
    private lateinit var adapterGratuitas: IAAdapter
    private lateinit var adapterObjetivo: IAAdapter
    private lateinit var chipTodas: Chip
    private var catalogoAtual: List<IA> = emptyList()
    private var recomendacaoAtual: List<IA> = emptyList()

    private sealed class ResultadoAtualizacaoInterna {
        data class Sucesso(val quantidade: Int, val catalogo: List<IA>) : ResultadoAtualizacaoInterna()
        data class Falha(val motivo: String) : ResultadoAtualizacaoInterna()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        GlobalNavigation.attach(this, binding.root, GlobalNavigation.BRAIN)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        repositorio = CatalogoRepository(applicationContext)
        favoritosRepositorio = FavoritosRepository(applicationContext)
        atualizacaoRepositorio = AtualizacaoRepository(applicationContext)
        imagemCache = ImagemCache(applicationContext)

        configurarListas()
        configurarPesquisa()
        configurarChipsCategorias()
        configurarFiltrosAvancados()
        configurarObjetivos()
        configurarOrdenacao()
        configurarBotoes()
        observarResultado()
        carregarCatalogoSeNecessario()
    }

    override fun onResume() {
        super.onResume()
        if (::adapterCatalogo.isInitialized) {
            viewModel.definirFavoritos(favoritosRepositorio.obterFavoritos())
            atualizarFavoritosNosAdapters()
            catalogoAtual = viewModel.catalogo
            renderizarDashboard(catalogoAtual)
        }
    }

    private fun configurarListas() {
        adapterCatalogo = criarAdapter()
        adapterDestaques = criarAdapter()
        adapterFavoritos = criarAdapter()
        adapterGratuitas = criarAdapter()
        adapterObjetivo = criarAdapter()

        listOf(
            binding.recyclerIAs to adapterCatalogo,
            binding.recyclerDestaques to adapterDestaques,
            binding.recyclerFavoritosBrain to adapterFavoritos,
            binding.recyclerGratuitasBrain to adapterGratuitas,
            binding.recyclerObjetivo to adapterObjetivo
        ).forEach { (recycler, adapter) ->
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.adapter = adapter
            recycler.isNestedScrollingEnabled = false
        }
    }

    private fun criarAdapter(): IAAdapter = IAAdapter(
        escopo = lifecycleScope,
        imagemCache = imagemCache,
        aoClicar = { ia ->
            favoritosRepositorio.registrarAcesso(ia.id)
            startActivity(DetalheIAActivity.criarIntent(this, ia))
        },
        aoAlternarFavorito = { ia ->
            val agoraFavorita = favoritosRepositorio.alternarFavorita(ia.id)
            viewModel.definirFavoritos(favoritosRepositorio.obterFavoritos())
            atualizarFavoritosNosAdapters()
            renderizarDashboard(catalogoAtual)
            Snackbar.make(
                binding.root,
                getString(if (agoraFavorita) R.string.favorito_adicionado else R.string.favorito_removido, ia.nome),
                Snackbar.LENGTH_SHORT
            ).show()
        }
    )

    private fun atualizarFavoritosNosAdapters() {
        val favoritos = favoritosRepositorio.obterFavoritos()
        listOf(adapterCatalogo, adapterDestaques, adapterFavoritos, adapterGratuitas, adapterObjetivo)
            .forEach { it.atualizarFavoritos(favoritos) }
    }

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

    private fun configurarChipsCategorias() {
        binding.chipGroupCategorias.setOnCheckedStateChangeListener { group, checkedIds ->
            val chipId = checkedIds.firstOrNull()
            val tag = chipId?.let { group.findViewById<Chip>(it)?.tag }
            viewModel.definirCategoriaPorChave((tag as? Categoria)?.chave ?: tag as? String)
        }
    }

    private fun configurarFiltrosAvancados() {
        binding.chipGroupAcesso.setOnCheckedStateChangeListener { group, checkedIds ->
            viewModel.definirAcesso(checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it)?.tag as? NivelAcesso })
        }
        binding.chipGroupCapacidades.setOnCheckedStateChangeListener { group, checkedIds ->
            viewModel.definirCapacidade(checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it)?.tag as? String })
        }
        binding.chipGroupPlataformas.setOnCheckedStateChangeListener { group, checkedIds ->
            viewModel.definirPlataforma(checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it)?.tag as? String })
        }
    }

    private fun configurarObjetivos() {
        binding.chipGroupObjetivos.isSingleSelection = true
        binding.chipGroupObjetivos.setOnCheckedStateChangeListener { group, checkedIds ->
            val categoria = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it)?.tag as? Categoria } ?: run {
                binding.containerResultadoObjetivo.visibility = View.GONE
                recomendacaoAtual = emptyList()
                return@setOnCheckedStateChangeListener
            }
            exibirObjetivo(categoria)
        }
    }

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

    private fun configurarBotoes() {
        binding.btnAbrirFavoritos.setOnClickListener { startActivity(Intent(this, FavoritosActivity::class.java)) }
        binding.btnAbrirAIBrain.setOnClickListener { startActivity(Intent(this, CriarComIAActivity::class.java)) }
        binding.btnAbrirBiblioteca.setOnClickListener { startActivity(Intent(this, com.aibrain.app.view.BibliotecaActivity::class.java)) }
        binding.btnAbrirCriadorPrompts.setOnClickListener { startActivity(Intent(this, CriadorPromptsActivity::class.java)) }
        binding.btnAbrirAssistenteIA.setOnClickListener { startActivity(Intent(this, AssistenteIAActivity::class.java)) }
        binding.btnAbrirIA18.setOnClickListener { startActivity(Intent(this, com.aibrain.app.view.IA18Activity::class.java)) }
        binding.btnAbrirColecoes.setOnClickListener { startActivity(Intent(this, ColecoesActivity::class.java)) }
        binding.btnAbrirGuias.setOnClickListener { startActivity(Intent(this, GuiasActivity::class.java)) }
        binding.btnAbrirComandos.setOnClickListener { startActivity(Intent(this, ComandosActivity::class.java)) }
        binding.btnCompararObjetivo.setOnClickListener {
            if (recomendacaoAtual.size >= 2) {
                startActivity(CompararIAsActivity.criarIntent(this, recomendacaoAtual.take(3), binding.txtObjetivoResultado.text.toString()))
            } else {
                Snackbar.make(binding.root, R.string.brain_comparar_minimo, Snackbar.LENGTH_SHORT).show()
            }
        }
        binding.btnAtualizarIAs.contentDescription = getString(R.string.atualizar_ias_desc)
        binding.btnAtualizarIAs.setOnClickListener { atualizarIasComIA() }
    }

    private fun observarResultado() {
        viewModel.resultado.observe(this) { ias -> exibirResultado(ias) }
    }

    private fun carregarCatalogoSeNecessario() {
        if (viewModel.catalogoCarregado) {
            catalogoAtual = viewModel.catalogo
            binding.searchIAs.setQuery(viewModel.termoPesquisa, false)
            reconstruirChips(catalogoAtual)
            configurarObjetivosParaCatalogo(catalogoAtual)
            renderizarDashboard(catalogoAtual)
            return
        }
        lifecycleScope.launch {
            try {
                val ias = repositorio.carregarCatalogo()
                catalogoAtual = ias
                viewModel.definirFavoritos(favoritosRepositorio.obterFavoritos())
                viewModel.definirCatalogo(ias)
                reconstruirChips(ias)
                configurarObjetivosParaCatalogo(ias)
                renderizarDashboard(ias)
                sincronizarCatalogoRemoto()
                prefetchLogosSePrimeiraVez(ias)
            } catch (_: Exception) {
                exibirErro()
            }
        }
    }

    private fun reconstruirChips(ias: List<IA>) {
        val grupo = binding.chipGroupCategorias
        val chaveAtual = grupo.checkedChipId.takeIf { it != -1 }?.let { grupo.findViewById<Chip>(it)?.tag }
            ?: viewModel.categoriaSelecionadaChave
        grupo.removeAllViews()
        chipTodas = criarChip(getString(R.string.filtro_todas), null).also { it.isChecked = chaveAtual == null }
        grupo.addView(chipTodas)
        Categoria.entries.filter { categoria -> ias.any { categoria.chave in it.categorias } }
            .forEach { grupo.addView(criarChip("${it.emoji} ${it.rotulo}", it)) }
        BrainDiscoveryEngine.categoriasPresentes(ias)
            .filterNot { chave -> Categoria.entries.any { it.chave == chave } }
            .forEach { grupo.addView(criarChip(CategoriaDinamica.rotuloCurto(it), it)) }
        if (chaveAtual != null) {
            (0 until grupo.childCount).map { grupo.getChildAt(it) as Chip }
                .firstOrNull { (it.tag as? Categoria)?.chave == chaveAtual || it.tag == chaveAtual }
                ?.isChecked = true
        }

        preencherGrupoAcesso(ias)
        preencherGrupoTexto(
            binding.chipGroupCapacidades,
            ias.flatMap(BrainDiscoveryEngine::capacidadesDaIA).distinct().sorted(),
            ::rotuloCapacidade
        )
        val plataformas = ias.flatMap { it.plataformas }.distinct().sorted()
        binding.scrollPlataformas.visibility = if (plataformas.isEmpty()) View.GONE else View.VISIBLE
        preencherGrupoTexto(binding.chipGroupPlataformas, plataformas) { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun preencherGrupoAcesso(ias: List<IA>) {
        val grupo = binding.chipGroupAcesso
        grupo.removeAllViews()
        ias.map { it.acesso }.distinct().sortedBy(NivelAcesso::ordinal).forEach { acesso ->
            grupo.addView(Chip(this).apply {
                text = "${acesso.emoji} ${acesso.rotulo}"
                tag = acesso
                isCheckable = true
                contentDescription = getString(R.string.brain_filtro_acesso_desc, acesso.rotulo)
                isChecked = acesso == viewModel.acessoSelecionadoAtual
            })
        }
    }

    private fun preencherGrupoTexto(grupo: ChipGroup, valores: List<String>, rotulo: (String) -> String = { it }) {
        grupo.removeAllViews()
        valores.forEach { valor ->
            grupo.addView(Chip(this).apply {
                text = rotulo(valor)
                tag = valor
                isCheckable = true
                isClickable = true
                isChecked = when (grupo.id) {
                    R.id.chipGroupCapacidades -> valor == viewModel.capacidadeSelecionadaAtual
                    R.id.chipGroupPlataformas -> valor == viewModel.plataformaSelecionadaAtual
                    else -> false
                }
            })
        }
    }

    private fun configurarObjetivosParaCatalogo(ias: List<IA>) {
        val grupo = binding.chipGroupObjetivos
        grupo.removeAllViews()
        val categorias = listOf(
            Categoria.CODIGO, Categoria.PESQUISA, Categoria.IMAGEM, Categoria.ESCRITA,
            Categoria.ESTUDOS, Categoria.VIDEO, Categoria.PRODUTIVIDADE
        ).filter { categoria -> ias.any { categoria.chave in it.categorias } }
        categorias.forEach { categoria ->
            grupo.addView(Chip(this).apply {
                text = "${categoria.emoji} ${categoria.rotulo}"
                tag = categoria
                isCheckable = true
                isClickable = true
                contentDescription = getString(R.string.brain_objetivo_desc, categoria.rotulo)
            })
        }
    }

    private fun rotuloCapacidade(chave: String): String = "${CategoriaDinamica.rotuloCurto(chave)}"

    private fun criarChip(texto: String, tag: Any?): Chip = Chip(this).apply {
        text = texto
        this.tag = tag
        isCheckable = true
        isClickable = true
    }

    private fun exibirObjetivo(categoria: Categoria) {
        val resultado = BrainDiscoveryEngine.recomendar(
            catalogoAtual,
            "${categoria.rotulo} ${categoria.chave}",
            favoritos = favoritosRepositorio.obterFavoritos()
        )
        recomendacaoAtual = resultado.resultados.map { it.ia }
        binding.containerResultadoObjetivo.visibility = View.VISIBLE
        binding.txtObjetivoResultado.text = getString(R.string.brain_objetivo_resultado, categoria.rotulo)
        binding.txtObjetivoJustificativa.text = resultado.melhorOpcao?.reasons?.joinToString("\n") { "✓ $it" }
            ?: getString(R.string.brain_objetivo_sem_resultado)
        adapterObjetivo.submitList(resultado.resultados.take(3).map { it.ia })
        adapterObjetivo.atualizarFavoritos(favoritosRepositorio.obterFavoritos())
        binding.btnCompararObjetivo.visibility = if (resultado.resultados.size >= 2) View.VISIBLE else View.GONE
    }

    private fun renderizarDashboard(ias: List<IA>) {
        if (ias.isEmpty()) return
        catalogoAtual = ias
        val favoritos = favoritosRepositorio.obterFavoritos()
        val destaques = ias.sortedWith(compareByDescending<IA> { it.notaMediaSegura() }.thenBy { it.nome.lowercase() }).take(3)
        val favoritas = ias.filter { it.id in favoritos }.sortedBy { it.nome.lowercase() }.take(3)
        val gratuitas = ias.filter { it.gratuita }.sortedWith(compareByDescending<IA> { it.notaMediaSegura() }.thenBy { it.nome.lowercase() }).take(3)
        adapterDestaques.submitList(destaques)
        adapterFavoritos.submitList(favoritas)
        adapterGratuitas.submitList(gratuitas)
        adapterDestaques.atualizarFavoritos(favoritos)
        adapterFavoritos.atualizarFavoritos(favoritos)
        adapterGratuitas.atualizarFavoritos(favoritos)
        binding.recyclerFavoritosBrain.visibility = if (favoritas.isEmpty()) View.GONE else View.VISIBLE
        binding.txtFavoritosBrainVazio.visibility = if (favoritas.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerDestaques.visibility = if (destaques.isEmpty()) View.GONE else View.VISIBLE
        binding.recyclerGratuitasBrain.visibility = if (gratuitas.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun exibirResultado(ias: List<IA>) {
        if (ias.isEmpty()) {
            binding.txtVazioOuErro.text = getString(if (viewModel.catalogoCarregado) R.string.lista_vazia else R.string.lista_carregando)
            binding.containerVazioOuErro.visibility = View.VISIBLE
            binding.recyclerIAs.visibility = View.GONE
        } else {
            binding.containerVazioOuErro.visibility = View.GONE
            binding.recyclerIAs.visibility = View.VISIBLE
            adapterCatalogo.submitList(ias)
            adapterCatalogo.atualizarFavoritos(favoritosRepositorio.obterFavoritos())
        }
    }

    private fun exibirErro() {
        binding.txtVazioOuErro.text = getString(R.string.lista_erro)
        binding.containerVazioOuErro.visibility = View.VISIBLE
        binding.recyclerIAs.visibility = View.GONE
    }

    private fun atualizarIasComIA() {
        val apiKey = AssistenteIARepository(applicationContext).obterApiKey()
        if (apiKey.isNullOrBlank()) {
            Snackbar.make(binding.root, R.string.atualizar_ias_sem_api_key, Snackbar.LENGTH_LONG).show()
            return
        }
        binding.btnAtualizarIAs.isEnabled = false
        binding.progressSincronizandoCatalogo.visibility = View.VISIBLE
        Snackbar.make(binding.root, R.string.atualizar_ias_em_andamento, Snackbar.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val resultado = try {
                withContext(Dispatchers.IO) {
                    val catalogo = repositorio.carregarCatalogo()
                    val prompt = PromptAtualizacaoCatalogoIA.construir(Categoria.entries, catalogo)
                    when (val resposta = enviarComBuscaNaWeb(GroqClient(apiKey), "Faça uma nova varredura na internet por novidades de IA e atualize o catálogo.", prompt)) {
                        is ResultadoComFallback.Sucesso -> {
                            val parseado = ParserAtualizacaoCatalogoIA.parsear(resposta.texto, catalogo)
                            val quantidade = CatalogoCuradoRepository(applicationContext).adicionar(parseado.novasIas)
                            ResultadoAtualizacaoInterna.Sucesso(quantidade, repositorio.carregarCatalogo())
                        }
                        is ResultadoComFallback.Falha -> ResultadoAtualizacaoInterna.Falha(resposta.motivo)
                    }
                }
            } catch (e: Exception) {
                ResultadoAtualizacaoInterna.Falha(e.message ?: "Falha inesperada")
            }
            binding.progressSincronizandoCatalogo.visibility = View.GONE
            binding.btnAtualizarIAs.isEnabled = true
            when (resultado) {
                is ResultadoAtualizacaoInterna.Sucesso -> {
                    catalogoAtual = resultado.catalogo
                    viewModel.definirCatalogo(resultado.catalogo)
                    reconstruirChips(resultado.catalogo)
                    configurarObjetivosParaCatalogo(resultado.catalogo)
                    renderizarDashboard(resultado.catalogo)
                    Snackbar.make(binding.root, getString(if (resultado.quantidade == 0) R.string.atualizar_ias_nenhuma else R.string.atualizar_ias_sucesso, resultado.quantidade), Snackbar.LENGTH_LONG).show()
                }
                is ResultadoAtualizacaoInterna.Falha -> Snackbar.make(binding.root, getString(R.string.atualizar_ias_falha, resultado.motivo), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun sincronizarCatalogoRemoto() {
        lifecycleScope.launch {
            binding.progressSincronizandoCatalogo.visibility = View.VISIBLE
            try {
                if (atualizacaoRepositorio.verificarEAtualizar(repositorio.versaoDoAssetEmbutido())) {
                    val catalogo = repositorio.carregarCatalogo()
                    catalogoAtual = catalogo
                    viewModel.definirCatalogo(catalogo)
                    reconstruirChips(catalogo)
                    configurarObjetivosParaCatalogo(catalogo)
                    renderizarDashboard(catalogo)
                }
            } finally {
                binding.progressSincronizandoCatalogo.visibility = View.GONE
            }
        }
    }

    private fun prefetchLogosSePrimeiraVez(ias: List<IA>) {
        val prefs = getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(CHAVE_LOGOS_PREFETCH_FEITO, false)) return
        lifecycleScope.launch {
            imagemCache.prefetchTodos(ias.map { it.logo })
            prefs.edit().putBoolean(CHAVE_LOGOS_PREFETCH_FEITO, true).apply()
        }
    }

    companion object {
        private const val PREFS_NOME = "ai_brain_prefs"
        private const val CHAVE_LOGOS_PREFETCH_FEITO = "logos_prefetch_feito"
    }
}

private fun IA.notaMediaSegura(): Double = if (notas.isEmpty()) 0.0 else notas.values.average()
