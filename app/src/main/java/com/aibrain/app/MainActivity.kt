package com.aibrain.app

import android.content.Context
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
import com.aibrain.app.data.AssistenteIARepository
import com.aibrain.app.data.FavoritosRepository
import com.aibrain.app.databinding.ActivityMainBinding
import com.aibrain.app.model.Categoria
import com.aibrain.app.model.CategoriaDinamica
import com.aibrain.app.model.IA
import com.aibrain.app.groq.GroqClient
import com.aibrain.app.groq.ParserAtualizacaoCatalogoIA
import com.aibrain.app.groq.PromptAtualizacaoCatalogoIA
import com.aibrain.app.groq.ResultadoComFallback
import com.aibrain.app.groq.enviarComBuscaNaWeb
import com.aibrain.app.repository.AtualizacaoRepository
import com.aibrain.app.repository.CatalogoCuradoRepository
import com.aibrain.app.repository.CatalogoRepository
import com.aibrain.app.view.CriarComIAActivity
import com.aibrain.app.view.AssistenteIAActivity
import com.aibrain.app.view.BibliotecaActivity
import com.aibrain.app.view.CriadorPromptsActivity
import com.aibrain.app.view.ColecoesActivity
import com.aibrain.app.view.GuiasActivity
import com.aibrain.app.view.DetalheIAActivity
import com.aibrain.app.view.FavoritosActivity
import com.aibrain.app.view.IAAdapter
import com.aibrain.app.viewmodel.MainViewModel
import com.aibrain.app.navigation.GlobalNavigation
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        configurarLista()
        configurarPesquisa()
        configurarChipsCategorias()
        configurarOrdenacao()
        configurarBotaoFavoritos()
        configurarBotaoAtualizarIAs()
        binding.btnAbrirColecoes.setOnClickListener { startActivity(Intent(this, ColecoesActivity::class.java)) }
        binding.btnAbrirGuias.setOnClickListener { startActivity(Intent(this, GuiasActivity::class.java)) }
        binding.btnAbrirComandos.setOnClickListener { startActivity(Intent(this, com.aibrain.app.view.ComandosActivity::class.java)) }
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
            startActivity(Intent(this, CriarComIAActivity::class.java))
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
        binding.btnAbrirIA18.setOnClickListener {
            startActivity(Intent(this, com.aibrain.app.view.IA18Activity::class.java))
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
     * Gera um Chip "Todas" + um Chip por categoria presente no catálogo.
     *
     * Fase 26 — além das categorias fixas do enum [Categoria], IAs adicionadas
     * pela curadoria podem trazer categorias novas (ex.: "Saúde Mental"), que
     * ganham automaticamente um chip/aba próprio aqui. As categorias dinâmicas
     * são extraídas do catálogo e armazenadas como chaves textuais (tags
     * `Categoria? = null` com chave no `tag`), e o filtro aplica direto pela
     * chave — sem precisar registrar nada no enum fixo.
     */
    private fun configurarChipsCategorias() {
        val grupo = binding.chipGroupCategorias

        chipTodas = criarChip(getString(R.string.filtro_todas), null)
        chipTodas.isChecked = true
        grupo.addView(chipTodas)

        grupo.setOnCheckedStateChangeListener { group, checkedIds ->
            val chipId = checkedIds.firstOrNull()
            val chipTag = if (chipId != null) {
                group.findViewById<Chip>(chipId)?.tag
            } else {
                null
            }
            val chave = if (chipTag is Categoria) chipTag.chave else chipTag as? String
            viewModel.definirCategoriaPorChave(chave)
        }
    }

    /**
     * Fase 26 — reconstrói os chips de categoria a partir do catálogo atual:
     * todas as categorias fixas do enum que têm ao menos uma IA no catálogo
     * + as categorias novas vindas da curadoria (Fase 26). "Todas" continua
     * fixa. Chamado também após uma adição via curadoria, para a nova
     * categoria aparecer na hora.
     */
    private fun reconstruirChipsCategorias(ias: List<IA>) {
        val grupo = binding.chipGroupCategorias
        // Captura a categoria/chave atualmente selecionada antes de reconstruir.
        val chipAtual = grupo.checkedChipId.takeIf { it != -1 }?.let { grupo.findViewById<Chip>(it) }
        val chaveAtual: String? = chipAtual?.let {
            (it.tag as? Categoria)?.chave ?: it.tag as? String
        }

        grupo.removeAllViews()
        chipTodas = criarChip(getString(R.string.filtro_todas), null)
        grupo.addView(chipTodas)

        Categoria.entries.forEach { categoria ->
            grupo.addView(criarChip("${categoria.emoji} ${categoria.rotulo}", categoria))
        }

        // Fase 26 — categorias novas: chaves textuais presentes no catálogo que
        // não pertencem ao enum fixo, exibidas com rótulo capitalizado.
        val chavesFixas = Categoria.entries.map { it.chave }.toSet()
        val chavesDoCatalogo = ias.flatMap { it.categorias }.toSet()
        chavesDoCatalogo
            .filterNot { it in chavesFixas }
            .sorted()
            .forEach { chaveNova ->
                val chip = criarChip(CategoriaDinamica.rotulo(chaveNova), chaveNova)
                grupo.addView(chip)
            }

        // Re-seleciona a categoria que estava ativa antes da reconstrução.
        if (chaveAtual != null) {
            for (i in 0 until grupo.childCount) {
                val chip = grupo.getChildAt(i) as? Chip ?: continue
                if ((chip.tag as? Categoria)?.chave == chaveAtual || chip.tag == chaveAtual) {
                    chip.isChecked = true
                    break
                }
            }
        } else {
            chipTodas.isChecked = true
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

    private fun criarChip(texto: String, chaveCategoria: String): Chip {
        return Chip(this).apply {
            text = texto
            tag = chaveCategoria
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

    /** Configura a atualização manual do catálogo usando a busca web da Groq Compound. */
    private fun configurarBotaoAtualizarIAs() {
        binding.btnAtualizarIAs.contentDescription = getString(R.string.atualizar_ias_desc)
        binding.btnAtualizarIAs.setOnClickListener { atualizarIasComIA() }
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
                    val catalogoAtual = repositorio.carregarCatalogo()
                    val prompt = PromptAtualizacaoCatalogoIA.construir(Categoria.entries, catalogoAtual)
                    when (val resposta = enviarComBuscaNaWeb(
                        GroqClient(apiKey),
                        "Faça uma nova varredura na internet por novidades de IA e atualize o catálogo.",
                        prompt
                    )) {
                        is ResultadoComFallback.Sucesso -> {
                            val parseado = ParserAtualizacaoCatalogoIA.parsear(resposta.texto, catalogoAtual)
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
                    viewModel.definirCatalogo(resultado.catalogo)
                    reconstruirChipsCategorias(resultado.catalogo)
                    if (resultado.quantidade == 0) {
                        Snackbar.make(binding.root, R.string.atualizar_ias_nenhuma, Snackbar.LENGTH_LONG).show()
                    } else {
                        Snackbar.make(
                            binding.root,
                            getString(R.string.atualizar_ias_sucesso, resultado.quantidade),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
                is ResultadoAtualizacaoInterna.Falha -> Snackbar.make(
                    binding.root,
                    getString(R.string.atualizar_ias_falha, resultado.motivo),
                    Snackbar.LENGTH_LONG
                ).show()
            }
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
                reconstruirChipsCategorias(ias)
                sincronizarCatalogoRemoto()
                prefetchLogosSePrimeiraVez(ias)
            } catch (e: Exception) {
                exibirErro()
            }
        }
    }

    /**
     * Fase 19.9 — no primeiro uso do app (ícones não embutidos no APK, Fase 19.8),
     * baixa e cacheia em disco todos os logos do catálogo em segundo plano, sem
     * bloquear a tela (a listagem já é exibida com o placeholder enquanto isso).
     * Marca a flag só ao final: se a Activity for encerrada no meio do prefetch,
     * a próxima abertura tenta de novo — URLs já cacheadas ou já marcadas como
     * "miss" (Fase 14.4) não geram nova requisição de rede.
     */
    private fun prefetchLogosSePrimeiraVez(ias: List<IA>) {
        val prefs = getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(CHAVE_LOGOS_PREFETCH_FEITO, false)) return

        lifecycleScope.launch {
            imagemCache.prefetchTodos(ias.map { it.logo })
            prefs.edit().putBoolean(CHAVE_LOGOS_PREFETCH_FEITO, true).apply()
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
                    val catalogo = repositorio.carregarCatalogo()
                    viewModel.definirCatalogo(catalogo)
                    reconstruirChipsCategorias(catalogo)
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

    companion object {
        private const val PREFS_NOME = "ai_brain_prefs"
        private const val CHAVE_LOGOS_PREFETCH_FEITO = "logos_prefetch_feito"
    }
}
