package com.aibrain.app.view

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aibrain.app.R
import com.aibrain.app.data.PromptDadosLocaisRepository
import com.aibrain.app.databinding.ActivityBibliotecaBinding
import com.aibrain.app.model.CategoriaPrompt
import com.aibrain.app.model.Prompt
import com.aibrain.app.repository.PromptRepository
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Tela da Biblioteca de Prompts (Fase 16.9-16.16) — o conjunto completo
 * de prompts, mesmo papel que a Fase 6 teve para o catálogo de IAs.
 *
 * Fase 16.14 — ordenação combinada (AND) com pesquisa (16.12) e categoria
 * (16.13), mesmo padrão do `toggleOrdenacao` da Fase 6.4: "Mais utilizados"
 * (contagem de cópia/compartilhamento, novo [PromptDadosLocaisRepository])
 * e "Mais recentes" (`dataCriacao`) funcionam com dado real hoje.
 * "Favoritos" (Fase 16.15) ordena pelos favoritos salvos localmente (botão
 * de favoritar já disponível no item e na tela de detalhes). "Mais bem
 * avaliados" fica fora do grupo por enquanto: ASSUMINDO — diferente de
 * `IA.notaMedia()` (Fase 4.1), `Prompt` (Fase 16.1) não tem campo de
 * nota/avaliação, e alterá-lo agora romperia o contrato que a Fase 17 vai
 * consumir sem alterar; entra quando um campo de avaliação para prompts
 * for definido no roadmap.
 *
 * Fase 16.16 — "Histórico" ordena/filtra pelos últimos prompts
 * copiados/usados ([PromptDadosLocaisRepository.obterHistorico]), mesmo
 * papel que a Fase 7.3 teve para IAs (lá numa seção dedicada da
 * `FavoritosActivity`; aqui reaproveitando o mesmo grupo de ordenação
 * já existente desde a Fase 16.14).
 */
class BibliotecaActivity : AppCompatActivity() {

    private enum class Ordenacao { NENHUMA, UTILIZADOS, RECENTES, FAVORITOS, HISTORICO }

    private lateinit var binding: ActivityBibliotecaBinding
    private lateinit var repositorioPrompts: PromptRepository
    private lateinit var dadosLocaisRepositorio: PromptDadosLocaisRepository
    private lateinit var adapterPrompts: PromptAdapter
    private lateinit var chipTodasCategorias: Chip

    /** Biblioteca completa carregada do JSON — pesquisa/filtro/ordenação atuam sobre esta lista, nunca sobre o resultado exibido. */
    private var bibliotecaCompleta: List<Prompt> = emptyList()
    private var termoPesquisaAtual: String = ""
    private var categoriaSelecionada: CategoriaPrompt? = null
    private var ordenacaoAtual: Ordenacao = Ordenacao.NENHUMA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBibliotecaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repositorioPrompts = PromptRepository(applicationContext)
        dadosLocaisRepositorio = PromptDadosLocaisRepository(applicationContext)

        configurarLista()
        configurarPesquisa()
        configurarChipsCategorias()
        configurarOrdenacao()
        binding.btnVoltarBiblioteca.setOnClickListener { finish() }
        carregarPrompts()
    }

    override fun onResume() {
        super.onResume()
        // Fase 16.15 — reflete favoritos alterados na tela de Detalhe ao voltar para cá.
        adapterPrompts.atualizarFavoritos(dadosLocaisRepositorio.obterFavoritos())
        // Fase 16.14/16.16 — favoritos e histórico podem mudar fora desta tela; reaplica se a ordenação depender deles.
        if (ordenacaoAtual == Ordenacao.FAVORITOS || ordenacaoAtual == Ordenacao.HISTORICO) aplicarFiltros()
    }

    private fun configurarLista() {
        adapterPrompts = PromptAdapter(
            aoClicar = { prompt -> abrirDetalhe(prompt) },
            aoAlternarFavorito = { prompt -> alternarFavorito(prompt) }
        )
        binding.recyclerPrompts.layoutManager = LinearLayoutManager(this)
        binding.recyclerPrompts.adapter = adapterPrompts
    }

    /** Fase 16.15 — Favoritar/desfavoritar prompt a partir da listagem. */
    private fun alternarFavorito(prompt: Prompt) {
        val agoraFavorito = dadosLocaisRepositorio.alternarFavorito(prompt.id)
        adapterPrompts.atualizarFavoritos(dadosLocaisRepositorio.obterFavoritos())
        if (ordenacaoAtual == Ordenacao.FAVORITOS) aplicarFiltros()
        val mensagem = if (agoraFavorito) {
            getString(R.string.prompt_favorito_adicionado, prompt.titulo)
        } else {
            getString(R.string.prompt_favorito_removido, prompt.titulo)
        }
        Snackbar.make(binding.root, mensagem, Snackbar.LENGTH_SHORT).show()
    }

    private fun configurarPesquisa() {
        binding.searchPrompts.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                termoPesquisaAtual = newText.orEmpty()
                aplicarFiltros()
                return true
            }
        })
    }

    /** Fase 16.13 — Chip "Todas" + um Chip por categoria do enum [CategoriaPrompt]. */
    private fun configurarChipsCategorias() {
        val grupo = binding.chipGroupCategoriasBiblioteca

        chipTodasCategorias = criarChipCategoria(getString(R.string.filtro_todas), null)
        chipTodasCategorias.isChecked = true
        grupo.addView(chipTodasCategorias)

        CategoriaPrompt.entries.forEach { categoria ->
            grupo.addView(criarChipCategoria("${categoria.emoji} ${categoria.rotulo}", categoria))
        }

        grupo.setOnCheckedStateChangeListener { group, checkedIds ->
            val chipId = checkedIds.firstOrNull()
            categoriaSelecionada = if (chipId != null) {
                group.findViewById<Chip>(chipId)?.tag as? CategoriaPrompt
            } else {
                null
            }
            aplicarFiltros()
        }
    }

    private fun criarChipCategoria(texto: String, categoria: CategoriaPrompt?): Chip {
        return Chip(this).apply {
            text = texto
            tag = categoria
            isCheckable = true
            isClickable = true
        }
    }

    /** Fase 16.14 — seleção única entre Mais utilizados / Mais recentes / Favoritos; nenhum selecionado = ordem original. */
    private fun configurarOrdenacao() {
        binding.toggleOrdenacaoBiblioteca.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            ordenacaoAtual = when (checkedId) {
                binding.btnOrdenarUtilizados.id -> Ordenacao.UTILIZADOS
                binding.btnOrdenarRecentes.id -> Ordenacao.RECENTES
                binding.btnOrdenarFavoritos.id -> Ordenacao.FAVORITOS
                binding.btnOrdenarHistorico.id -> Ordenacao.HISTORICO
                else -> Ordenacao.NENHUMA
            }
            aplicarFiltros()
        }
    }

    private fun abrirDetalhe(prompt: Prompt) {
        startActivity(DetalhePromptActivity.criarIntent(this, prompt))
    }

    private fun carregarPrompts() {
        lifecycleScope.launch {
            bibliotecaCompleta = try {
                repositorioPrompts.carregarBiblioteca()
            } catch (e: Exception) {
                emptyList()
            }
            adapterPrompts.atualizarFavoritos(dadosLocaisRepositorio.obterFavoritos())
            aplicarFiltros()
        }
    }

    /** Fase 16.12/16.13/16.14 — combina (AND) pesquisa, categoria e aplica a ordenação selecionada por cima. */
    private fun aplicarFiltros() {
        val termo = termoPesquisaAtual.trim().lowercase()
        val filtrados = bibliotecaCompleta.filter { prompt ->
            val passaPesquisa = termo.isBlank() ||
                prompt.titulo.lowercase().contains(termo) ||
                prompt.descricaoCurta.lowercase().contains(termo) ||
                prompt.tags.any { it.lowercase().contains(termo) }

            val passaCategoria = categoriaSelecionada == null || prompt.categoria == categoriaSelecionada

            passaPesquisa && passaCategoria
        }

        val ordenados = when (ordenacaoAtual) {
            Ordenacao.UTILIZADOS -> {
                val contagens = dadosLocaisRepositorio.obterContagensUso()
                filtrados.sortedByDescending { contagens[it.id] ?: 0 }
            }
            Ordenacao.RECENTES -> filtrados.sortedByDescending { it.dataCriacao }
            Ordenacao.FAVORITOS -> {
                val favoritos = dadosLocaisRepositorio.obterFavoritos()
                filtrados.sortedByDescending { favoritos.contains(it.id) }
            }
            Ordenacao.HISTORICO -> {
                val historico = dadosLocaisRepositorio.obterHistorico()
                filtrados.filter { it.id in historico }
                    .sortedBy { historico.indexOf(it.id) }
            }
            Ordenacao.NENHUMA -> filtrados
        }

        exibirPrompts(ordenados)
    }

    private fun exibirPrompts(lista: List<Prompt>) {
        binding.txtBibliotecaVazia.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerPrompts.visibility = if (lista.isEmpty()) View.GONE else View.VISIBLE
        adapterPrompts.submitList(lista)
    }
}
