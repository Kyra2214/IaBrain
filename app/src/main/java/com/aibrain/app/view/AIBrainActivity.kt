package com.aibrain.app.view

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aibrain.app.R
import com.aibrain.app.brain.recomendar
import com.aibrain.app.brain.ContextualPromptGenerator
import com.aibrain.app.brain.PromptGenerationSpecBuilder
import com.aibrain.app.brain.RoomCommandResolver
import com.aibrain.app.brain.toEntity
import com.aibrain.app.data.local.PromptRoomRepository
import com.aibrain.app.browser.BrowserActivity
import com.aibrain.app.brain.IAOpenContract
import com.aibrain.app.brain.IAUrlResolver
import com.aibrain.app.brain.UrlResolutionStatus
import com.aibrain.app.cache.ImagemCache
import com.aibrain.app.data.FavoritosRepository
import androidx.core.widget.NestedScrollView
import com.aibrain.app.databinding.ActivityAiBrainBinding
import com.aibrain.app.model.Categoria
import com.aibrain.app.model.IA
import com.aibrain.app.model.NivelAcesso
import com.aibrain.app.repository.CatalogoRepository
import com.aibrain.app.util.FaixaAvaliacao
import com.aibrain.app.util.FiltroIdioma
import com.aibrain.app.util.atendeFaixa
import com.aibrain.app.util.filtroIdioma
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast

/**
 * Tela do AI Brain — IA Auxiliar (Fase 9).
 * O usuário digita o que precisa fazer (ex: "quero criar vídeo"); o app
 * detecta a categoria (9.1) e retorna melhor opção / segunda opção /
 * alternativas gratuitas (9.2), a partir do catálogo completo já carregado.
 */
class AIBrainActivity : AppCompatActivity() {

    companion object {
        /** Fase 15.7 — itens por página do resultado dos filtros rápidos. */
        private const val TAMANHO_PAGINA_FILTROS = 20
        /** Fase 15.7 — dispara a próxima página quando faltam N px pro fim do scroll. */
        private const val GATILHO_PAGINACAO_PX = 400
    }

    private lateinit var binding: ActivityAiBrainBinding
    private lateinit var repositorio: CatalogoRepository
    private lateinit var favoritosRepositorio: FavoritosRepository
    private lateinit var imagemCache: ImagemCache
    private lateinit var adapterMelhor: IAAdapter
    private lateinit var adapterSegunda: IAAdapter
    private lateinit var adapterGratuitas: IAAdapter
    private lateinit var adapterFiltros: IAAdapter
    private lateinit var promptRepository: PromptRoomRepository
    private lateinit var commandResolver: RoomCommandResolver
    private var promptAtual: String = ""
    private var iaSelecionadaId: String? = null
    private var contratoIA: IAOpenContract? = null

    private var catalogoCompleto: List<IA> = emptyList()

    // Fase 15.4/15.5 — seleção atual de cada grupo de filtro rápido (1 valor por grupo, combinados via AND).
    private var nivelSelecionado: NivelAcesso? = null
    private var categoriaFiltroSelecionada: Categoria? = null
    private var idiomaSelecionado: FiltroIdioma? = null
    private var faixaSelecionada: FaixaAvaliacao? = null

    // Fase 15.7 — lista completa filtrada (antes da paginação) + janela publicada no adapter.
    private var resultadoFiltrosCompleto: List<IA> = emptyList()
    private var itensVisiveisFiltros: Int = TAMANHO_PAGINA_FILTROS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiBrainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repositorio = CatalogoRepository(applicationContext)
        favoritosRepositorio = FavoritosRepository(applicationContext)
        imagemCache = ImagemCache(applicationContext)
        promptRepository = PromptRoomRepository(applicationContext)
        commandResolver = RoomCommandResolver(applicationContext)

        configurarListas()
        configurarFiltrosRapidos()
        binding.btnVoltar.setOnClickListener { finish() }
        binding.btnPerguntar.setOnClickListener { processarPergunta() }
        binding.btnCopiarPrompt.setOnClickListener { copiarPromptAtual() }
        binding.btnAbrirIA.setOnClickListener { abrirIASelecionada() }

        carregarCatalogo()
    }

    private fun configurarListas() {
        adapterMelhor = criarAdapterResultado()
        adapterSegunda = criarAdapterResultado()
        adapterGratuitas = criarAdapterResultado()
        adapterFiltros = criarAdapterResultado()

        listOf(
            binding.recyclerMelhor to adapterMelhor,
            binding.recyclerSegunda to adapterSegunda,
            binding.recyclerGratuitas to adapterGratuitas,
            binding.recyclerFiltros to adapterFiltros
        ).forEach { (recycler, adapter) ->
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.adapter = adapter
            recycler.isNestedScrollingEnabled = false
        }
    }

    /**
     * Fase 15.4/15.5/15.6/15.7 — Filtros rápidos do AI Brain.
     * 4 grupos de chips combináveis por AND (Acesso, Categoria, Idioma, Avaliação),
     * mais o texto já digitado no campo de pergunta como termo de pesquisa livre.
     * 100% local sobre [catalogoCompleto], sem chamada de rede, com atualização
     * instantânea a cada mudança de chip ou de texto.
     */
    private fun configurarFiltrosRapidos() {
        configurarChipGroup(binding.chipGroupAcesso, NivelAcesso.entries, { "${it.emoji} ${it.rotulo}" }) {
            nivelSelecionado = it
            aplicarFiltrosRapidos()
        }
        configurarChipGroup(binding.chipGroupCategoriaBrain, Categoria.entries, { "${it.emoji} ${it.rotulo}" }) {
            categoriaFiltroSelecionada = it
            aplicarFiltrosRapidos()
        }
        configurarChipGroup(binding.chipGroupIdioma, FiltroIdioma.entries, { "${it.emoji} ${it.rotulo}" }) {
            idiomaSelecionado = it
            aplicarFiltrosRapidos()
        }
        configurarChipGroup(binding.chipGroupAvaliacao, FaixaAvaliacao.entries, { it.rotulo }) {
            faixaSelecionada = it
            aplicarFiltrosRapidos()
        }

        binding.editPergunta.addTextChangedListener { aplicarFiltrosRapidos() }

        // Fase 15.7 — lazy loading: amplia a janela publicada quando o scroll chega perto do fim.
        binding.nestedScrollBrain.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val alturaConteudo = binding.nestedScrollBrain.getChildAt(0)?.height ?: 0
            if (scrollY + binding.nestedScrollBrain.height >= alturaConteudo - GATILHO_PAGINACAO_PX) {
                carregarMaisFiltros()
            }
        }
    }

    /** Gera 1 Chip por item de [itens] em [grupo], selecionável exclusivamente dentro do grupo. */
    private fun <T> configurarChipGroup(grupo: ChipGroup, itens: List<T>, rotulo: (T) -> String, aoSelecionar: (T?) -> Unit) {
        itens.forEach { item ->
            grupo.addView(
                Chip(this).apply {
                    text = rotulo(item)
                    tag = item
                    isCheckable = true
                    isClickable = true
                }
            )
        }
        grupo.setOnCheckedStateChangeListener { grupoAlterado, checkedIds ->
            val chipId = checkedIds.firstOrNull()
            @Suppress("UNCHECKED_CAST")
            val selecionado = chipId?.let { grupoAlterado.findViewById<Chip>(it)?.tag as? T }
            aoSelecionar(selecionado)
        }
    }

    /** Combina os 4 filtros ativos (AND) + termo de pesquisa livre sobre [catalogoCompleto]. */
    private fun aplicarFiltrosRapidos() {
        val termo = binding.editPergunta.text?.toString()?.trim()?.lowercase().orEmpty()
        val nivel = nivelSelecionado
        val categoria = categoriaFiltroSelecionada
        val idioma = idiomaSelecionado
        val faixa = faixaSelecionada
        val quantidadeAtiva = listOfNotNull(nivel, categoria, idioma, faixa).size

        atualizarIndicadorFiltros(quantidadeAtiva)

        if (quantidadeAtiva == 0) {
            binding.containerResultadoFiltros.visibility = View.GONE
            return
        }

        // Enquanto há filtro rápido ativo, o resultado da pergunta (Fase 9) fica em segundo plano.
        binding.containerResultado.visibility = View.GONE
        binding.txtCategoriaDetectada.visibility = View.GONE
        binding.txtSemResultado.visibility = View.GONE
        binding.containerResultadoFiltros.visibility = View.VISIBLE

        resultadoFiltrosCompleto = catalogoCompleto.filter { ia ->
            val passaNivel = nivel == null || ia.acesso == nivel
            val passaCategoria = categoria == null || ia.categorias.contains(categoria.chave)
            val passaIdioma = idioma == null || ia.filtroIdioma() == idioma
            val passaFaixa = faixa == null || ia.atendeFaixa(faixa)
            val passaTermo = termo.isEmpty() ||
                ia.nome.lowercase().contains(termo) ||
                ia.descricao.lowercase().contains(termo)

            passaNivel && passaCategoria && passaIdioma && passaFaixa && passaTermo
        }
        itensVisiveisFiltros = TAMANHO_PAGINA_FILTROS
        publicarResultadoFiltros()
    }

    /** Fase 15.7 — amplia a janela de itens exibida sem refazer o filtro. */
    private fun carregarMaisFiltros() {
        if (itensVisiveisFiltros >= resultadoFiltrosCompleto.size) return
        itensVisiveisFiltros = (itensVisiveisFiltros + TAMANHO_PAGINA_FILTROS).coerceAtMost(resultadoFiltrosCompleto.size)
        publicarResultadoFiltros()
    }

    private fun publicarResultadoFiltros() {
        val visiveis = resultadoFiltrosCompleto.take(itensVisiveisFiltros)
        binding.txtFiltrosVazio.visibility = if (visiveis.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerFiltros.visibility = if (visiveis.isEmpty()) View.GONE else View.VISIBLE
        adapterFiltros.submitList(visiveis)
        adapterFiltros.atualizarFavoritos(favoritosRepositorio.obterFavoritos())
    }

    /** Mostra a quantidade de filtros ativos quando houver filtros selecionados. */
    private fun atualizarIndicadorFiltros(quantidadeAtiva: Int) {
        binding.rowFiltrosStatus.visibility = if (quantidadeAtiva > 0) View.VISIBLE else View.GONE
        binding.txtFiltrosAtivos.text = getString(R.string.brain_filtros_contagem, quantidadeAtiva)
    }

    private fun criarAdapterResultado(): IAAdapter = IAAdapter(
        escopo = lifecycleScope,
        imagemCache = imagemCache,
        aoClicar = { ia ->
            favoritosRepositorio.registrarAcesso(ia.id)
            startActivity(DetalheIAActivity.criarIntent(this, ia))
        },
        aoAlternarFavorito = { ia ->
            favoritosRepositorio.alternarFavorita(ia.id)
            atualizarFavoritosNasListas()
        }
    )

    private fun atualizarFavoritosNasListas() {
        val favoritos = favoritosRepositorio.obterFavoritos()
        adapterMelhor.atualizarFavoritos(favoritos)
        adapterSegunda.atualizarFavoritos(favoritos)
        adapterGratuitas.atualizarFavoritos(favoritos)
        adapterFiltros.atualizarFavoritos(favoritos)
    }

    private fun carregarCatalogo() {
        binding.progressCarregandoCatalogo.visibility = View.VISIBLE
        binding.btnPerguntar.isEnabled = false
        lifecycleScope.launch {
            catalogoCompleto = try {
                // Fase 12.5 — método único: já traz o catálogo sincronizado com
                // a versão remota mais recente, se houver, sem precisar orquestrar
                // CatalogoRepository + AtualizacaoRepository aqui.
                repositorio.carregarCatalogoSincronizado()
            } catch (e: Exception) {
                emptyList()
            }
            binding.progressCarregandoCatalogo.visibility = View.GONE
            binding.btnPerguntar.isEnabled = true
            // Fase 15.5 — se algum filtro rápido já estava ativo antes do catálogo carregar, reaplica agora.
            aplicarFiltrosRapidos()
        }
    }

    /** Fase 9.1 + 9.2 — processa o texto livre e exibe a recomendação estruturada. */
    private fun processarPergunta() {
        val texto = binding.editPergunta.text?.toString().orEmpty()
        gerarPromptContextual(texto)
        binding.containerResultadoFiltros.visibility = View.GONE
        val recomendacao = catalogoCompleto.recomendar(texto)
        val categorias = recomendacao.categoriasDetectadas

        if (categorias.isEmpty()) {
            binding.containerResultado.visibility = View.GONE
            binding.txtCategoriaDetectada.visibility = View.GONE
            binding.txtSemResultado.visibility = View.VISIBLE
            // Fase 13.4 — sugere termos reconhecidos para ajudar o usuário a reformular a pergunta.
            binding.txtSemResultado.text = if (recomendacao.sugestaoTermos.isNotEmpty()) {
                getString(R.string.brain_sem_resultado) + "\n\n" +
                    getString(R.string.brain_sugestao_termos, recomendacao.sugestaoTermos.joinToString(", "))
            } else {
                getString(R.string.brain_sem_resultado)
            }
            return
        }

        binding.txtSemResultado.visibility = View.GONE
        binding.txtCategoriaDetectada.visibility = View.VISIBLE
        // Fase 13.3 — quando há 2 categorias detectadas, mostra as duas (ex: "🎥 Vídeo, 🎙️ Voz").
        val rotuloCategorias = categorias.joinToString(", ") { "${it.emoji} ${it.rotulo}" }
        binding.txtCategoriaDetectada.text =
            getString(R.string.brain_categoria_detectada, rotuloCategorias)
        binding.containerResultado.visibility = View.VISIBLE

        exibirResultado(binding.recyclerMelhor, binding.txtMelhorVazio, adapterMelhor, listOfNotNull(recomendacao.melhorOpcao))
        exibirResultado(binding.recyclerSegunda, binding.txtSegundaVazio, adapterSegunda, listOfNotNull(recomendacao.segundaOpcao))
        exibirResultado(binding.recyclerGratuitas, binding.txtGratuitasVazio, adapterGratuitas, recomendacao.alternativasGratuitas)

        atualizarFavoritosNasListas()
    }

    private fun gerarPromptContextual(texto: String) {
        binding.containerPromptGerado.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val request = commandResolver.resolve(texto) ?: return@launch
                val decision = com.aibrain.app.brain.LocalAIRouter.route(request, commandResolver.candidates())
                val spec = PromptGenerationSpecBuilder.from(request, decision)
                val prompt = ContextualPromptGenerator.generate(spec)
                promptRepository.salvar(spec.toEntity(prompt))
                promptAtual = prompt
                iaSelecionadaId = spec.iaId
                contratoIA = IAOpenContract(spec.iaId, spec.iaNome, null, UrlResolutionStatus.NOT_FOUND, prompt)
                binding.txtPromptMeta.text = "IA: ${spec.iaNome} · Comando: ${spec.comando} · Capacidades: ${spec.capacidades.joinToString() }"
                binding.txtPromptGerado.text = prompt
                binding.containerPromptGerado.visibility = View.VISIBLE
            } catch (_: Exception) {
                binding.containerPromptGerado.visibility = View.GONE
            }
        }
    }

    private fun abrirIASelecionada() {
        lifecycleScope.launch {
            val contrato = contratoIA ?: return@launch
            val resolvido = IAUrlResolver(applicationContext).resolve(contrato)
            contratoIA = resolvido
            val url = resolvido.officialResolvedUrl
            if (resolvido.urlStatus != UrlResolutionStatus.RESOLVED || url == null) {
                Toast.makeText(this@AIBrainActivity, "Esta IA não possui endereço configurado", Toast.LENGTH_SHORT).show()
                return@launch
            }
            copiarPromptAtual()
            startActivity(BrowserActivity.criarIntent(this@AIBrainActivity, resolvido))
        }
    }

    private fun copiarPromptAtual() {
        if (promptAtual.isBlank()) return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Prompt IaBrain", promptAtual))
        Toast.makeText(this, "Prompt copiado", Toast.LENGTH_SHORT).show()
    }

    private fun exibirResultado(
        recycler: RecyclerView,
        txtVazio: TextView,
        adapter: IAAdapter,
        lista: List<IA>
    ) {
        txtVazio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (lista.isEmpty()) View.GONE else View.VISIBLE
        adapter.submitList(lista)
    }
}
