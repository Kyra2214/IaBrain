package com.aibrain.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.aibrain.app.model.Categoria
import com.aibrain.app.model.IA
import com.aibrain.app.util.normalizarBusca
import com.aibrain.app.util.notaMedia
import com.aibrain.app.util.rankingGeral

/**
 * Fase 12.4 — MainViewModel.
 * Concentra o estado e a lógica de pesquisa/filtro/ordenação que antes vivia
 * na MainActivity. Sobrevive a mudanças de configuração (rotação de tela),
 * então o catálogo e os filtros aplicados não se perdem nem precisam
 * ser recarregados/reaplicados à toa.
 *
 * Fase 14.1 — [resultado] passa a ser paginado: [aplicarFiltros] calcula a lista
 * completa filtrada/ordenada, mas só publica uma janela dela por vez
 * ([itensVisiveis]); [carregarMaisItens] amplia essa janela conforme o usuário
 * rola a lista. Evita renderizar de uma vez um catálogo grande.
 */
class MainViewModel : ViewModel() {

    enum class Ordenacao { NENHUMA, RANKING, POPULARES, NOVIDADES }

    companion object {
        /** Fase 14.1 — quantidade de itens exibidos por página. */
        const val TAMANHO_PAGINA = 20
    }

    /** Catálogo completo carregado do JSON — a pesquisa filtra sobre esta lista, nunca sobre o resultado exibido. */
    private var catalogoCompleto: List<IA> = emptyList()

    /** true assim que o catálogo já foi carregado ao menos uma vez nesta instância do ViewModel. */
    var catalogoCarregado: Boolean = false
        private set

    private var termoPesquisaAtual: String = ""
    // Fase 26 — filtro por chave textual (não só enum): permite filtrar por
    // categorias novas criadas pela curadoria, que não existem no enum fixo.
    private var chaveCategoriaSelecionada: String? = null
    private val categoriaSelecionada: Categoria?
        get() = chaveCategoriaSelecionada?.let { Categoria.porChave(it) }
    private var ordenacaoAtual: Ordenacao = Ordenacao.NENHUMA

    /** Fase 14.1 — lista completa filtrada/ordenada (antes da paginação); base para [carregarMaisItens]. */
    private var resultadoCompletoFiltrado: List<IA> = emptyList()

    /** Fase 14.1 — quantos itens de [resultadoCompletoFiltrado] estão publicados em [resultado] agora. */
    private var itensVisiveis: Int = TAMANHO_PAGINA

    private val _resultado = MutableLiveData<List<IA>>(emptyList())
    val resultado: LiveData<List<IA>> = _resultado

    /** Indica quando pesquisa, categoria ou ordenação está ativa. */
    private val _filtroAtivo = MutableLiveData(false)
    val filtroAtivo: LiveData<Boolean> = _filtroAtivo

    /** Fase 14.1 — true enquanto ainda há itens de [resultadoCompletoFiltrado] fora de [resultado]. */
    val temMaisPaginas: Boolean
        get() = itensVisiveis < resultadoCompletoFiltrado.size

    /** Define/atualiza o catálogo completo (chamado após a leitura do JSON ou uma sincronização remota). */
    fun definirCatalogo(ias: List<IA>) {
        catalogoCompleto = ias
        catalogoCarregado = true
        aplicarFiltros()
    }

    fun definirTermoPesquisa(termo: String) {
        termoPesquisaAtual = termo
        aplicarFiltros()
    }

    fun definirCategoria(categoria: Categoria?) {
        chaveCategoriaSelecionada = categoria?.chave
        aplicarFiltros()
    }

    /**
     * Fase 26 — filtro por chave textual de categoria: aceita tanto as chaves
     * fixas do enum [Categoria] quanto categorias novas criadas pela curadoria
     * (que ganham chip/aba próprio na tela principal).
     */
    fun definirCategoriaPorChave(chave: String?) {
        chaveCategoriaSelecionada = chave
        aplicarFiltros()
    }

    fun definirOrdenacao(ordenacao: Ordenacao) {
        ordenacaoAtual = ordenacao
        aplicarFiltros()
    }

    /** Reseta pesquisa + categoria + ordenação de uma vez para consumidores internos. */
    fun limparFiltros() {
        termoPesquisaAtual = ""
        chaveCategoriaSelecionada = null
        ordenacaoAtual = Ordenacao.NENHUMA
        aplicarFiltros()
    }

    /**
     * Fase 14.1 — Amplia a janela de itens publicada em [resultado] em mais
     * [TAMANHO_PAGINA] itens (sem refazer pesquisa/filtro/ordenação, que já
     * estão computados em [resultadoCompletoFiltrado]). Não faz nada se já
     * não houver mais itens a mostrar.
     */
    fun carregarMaisItens() {
        if (!temMaisPaginas) return
        itensVisiveis = (itensVisiveis + TAMANHO_PAGINA).coerceAtMost(resultadoCompletoFiltrado.size)
        _resultado.value = resultadoCompletoFiltrado.take(itensVisiveis)
    }

    /** Combina pesquisa + categoria selecionada + ordenação sobre o catálogo completo. */
    private fun aplicarFiltros() {
        val termo = termoPesquisaAtual.trim().normalizarBusca()
        val categoria = categoriaSelecionada
        // Fase 26 — filtro pela chave textual direta, cobrindo também as
        // categorias novas (dinâmicas) que não existem no enum fixo.
        val chaveAtiva = chaveCategoriaSelecionada

        val filtradas = catalogoCompleto.filter { ia ->
            val passaPesquisa = termo.isEmpty() ||
                ia.nome.normalizarBusca().contains(termo) ||
                ia.descricao.normalizarBusca().contains(termo) ||
                ia.categorias.any { chave ->
                    chave.normalizarBusca().contains(termo) ||
                        Categoria.porChave(chave)?.rotulo?.normalizarBusca()?.contains(termo) == true
                }

            val passaCategoria = chaveAtiva == null || ia.categorias.any { it == chaveAtiva || Categoria.porChave(it)?.chave == chaveAtiva }

            passaPesquisa && passaCategoria
        }

        val ordenadas = when (ordenacaoAtual) {
            // Ranking: nota média geral.
            Ordenacao.RANKING -> filtradas.rankingGeral()
            // Populares: aproximação por versatilidade (quanto mais categorias bem avaliadas, mais "popular").
            Ordenacao.POPULARES -> filtradas.sortedByDescending { it.categorias.size * 10 + it.notaMedia() }
            // Novidades: últimas adicionadas ao catálogo (fim da lista do JSON primeiro).
            Ordenacao.NOVIDADES -> filtradas.asReversed()
            Ordenacao.NENHUMA -> filtradas
        }

        // Fase 14.1 — toda vez que pesquisa/categoria/ordenação muda, a paginação volta à primeira página.
        resultadoCompletoFiltrado = ordenadas
        itensVisiveis = TAMANHO_PAGINA
        _resultado.value = ordenadas.take(itensVisiveis)
        _filtroAtivo.value = termo.isNotEmpty() || chaveAtiva != null || ordenacaoAtual != Ordenacao.NENHUMA
    }
}
