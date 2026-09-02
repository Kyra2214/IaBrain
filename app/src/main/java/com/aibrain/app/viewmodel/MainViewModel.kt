package com.aibrain.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.aibrain.app.brain.BrainCatalogFilter
import com.aibrain.app.brain.BrainDiscoveryEngine
import com.aibrain.app.model.Categoria
import com.aibrain.app.model.IA
import com.aibrain.app.model.NivelAcesso

/**
 * Estado da home do Brain. O ViewModel sobrevive a rotações e mantém busca,
 * filtros, ordenação e a janela paginada sem recarregar o catálogo.
 */
class MainViewModel : ViewModel() {

    enum class Ordenacao { NENHUMA, RANKING, POPULARES, NOVIDADES }

    companion object {
        const val TAMANHO_PAGINA = 20
    }

    private var catalogoCompleto: List<IA> = emptyList()
    var catalogoCarregado: Boolean = false
        private set

    /** Catálogo completo já carregado; a UI usa-o para seções do dashboard. */
    val catalogo: List<IA>
        get() = catalogoCompleto

    private var termoPesquisaAtual: String = ""
    private var chaveCategoriaSelecionada: String? = null
    private var acessoSelecionado: NivelAcesso? = null
    private var capacidadeSelecionada: String? = null
    private var plataformaSelecionada: String? = null
    private var favoritosSelecionados: Set<String> = emptySet()
    private var ordenacaoAtual: Ordenacao = Ordenacao.NENHUMA
    private var resultadoCompletoFiltrado: List<IA> = emptyList()
    private var itensVisiveis: Int = TAMANHO_PAGINA

    private val _resultado = MutableLiveData<List<IA>>(emptyList())
    val resultado: LiveData<List<IA>> = _resultado

    private val _filtroAtivo = MutableLiveData(false)
    val filtroAtivo: LiveData<Boolean> = _filtroAtivo

    val temMaisPaginas: Boolean
        get() = itensVisiveis < resultadoCompletoFiltrado.size

    val termoPesquisa: String
        get() = termoPesquisaAtual
    val categoriaSelecionadaChave: String?
        get() = chaveCategoriaSelecionada
    val acessoSelecionadoAtual: NivelAcesso?
        get() = acessoSelecionado
    val capacidadeSelecionadaAtual: String?
        get() = capacidadeSelecionada
    val plataformaSelecionadaAtual: String?
        get() = plataformaSelecionada

    fun definirCatalogo(ias: List<IA>) {
        catalogoCompleto = ias
        catalogoCarregado = true
        aplicarFiltros()
    }

    fun definirTermoPesquisa(termo: String) {
        termoPesquisaAtual = termo
        aplicarFiltros()
    }

    fun definirCategoria(categoria: Categoria?) = definirCategoriaPorChave(categoria?.chave)

    fun definirCategoriaPorChave(chave: String?) {
        chaveCategoriaSelecionada = chave
        aplicarFiltros()
    }

    fun definirAcesso(acesso: NivelAcesso?) {
        acessoSelecionado = acesso
        aplicarFiltros()
    }

    fun definirCapacidade(capacidade: String?) {
        capacidadeSelecionada = capacidade
        aplicarFiltros()
    }

    fun definirPlataforma(plataforma: String?) {
        plataformaSelecionada = plataforma
        aplicarFiltros()
    }

    /** O armazenamento continua em FavoritosRepository; aqui só informamos os IDs ativos. */
    fun definirFavoritos(ids: Set<String>) {
        favoritosSelecionados = ids
        aplicarFiltros()
    }

    fun definirOrdenacao(ordenacao: Ordenacao) {
        ordenacaoAtual = ordenacao
        aplicarFiltros()
    }

    fun limparFiltros() {
        termoPesquisaAtual = ""
        chaveCategoriaSelecionada = null
        acessoSelecionado = null
        capacidadeSelecionada = null
        plataformaSelecionada = null
        favoritosSelecionados = emptySet()
        ordenacaoAtual = Ordenacao.NENHUMA
        aplicarFiltros()
    }

    fun carregarMaisItens() {
        if (!temMaisPaginas) return
        itensVisiveis = (itensVisiveis + TAMANHO_PAGINA).coerceAtMost(resultadoCompletoFiltrado.size)
        _resultado.value = resultadoCompletoFiltrado.take(itensVisiveis)
    }

    private fun aplicarFiltros() {
        val filtro = BrainCatalogFilter(
            categoria = chaveCategoriaSelecionada,
            acesso = acessoSelecionado,
            capacidade = capacidadeSelecionada,
            plataforma = plataformaSelecionada,
            termo = termoPesquisaAtual
        )
        val filtradas = BrainDiscoveryEngine.filtrar(catalogoCompleto, filtro, favoritosSelecionados)
        val ordenadas = when (ordenacaoAtual) {
            Ordenacao.RANKING -> filtradas.sortedWith(
                compareByDescending<IA> { it.notaMediaSegura() }
                    .thenBy { it.nome.lowercase() }
            )
            Ordenacao.POPULARES -> filtradas.sortedWith(
                compareByDescending<IA> { it.categorias.size * 10 + it.notaMediaSegura() }
                    .thenBy { it.nome.lowercase() }
            )
            Ordenacao.NOVIDADES -> filtradas.asReversed()
            Ordenacao.NENHUMA -> filtradas
        }

        resultadoCompletoFiltrado = ordenadas
        itensVisiveis = TAMANHO_PAGINA
        _resultado.value = ordenadas.take(itensVisiveis)
        _filtroAtivo.value = termoPesquisaAtual.isNotBlank() ||
            chaveCategoriaSelecionada != null ||
            acessoSelecionado != null ||
            capacidadeSelecionada != null ||
            plataformaSelecionada != null ||
            ordenacaoAtual != Ordenacao.NENHUMA
    }
}

private fun IA.notaMediaSegura(): Double = if (notas.isEmpty()) 0.0 else notas.values.average()
