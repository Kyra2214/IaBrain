package com.aibrain.app.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.aibrain.app.model.Categoria
import com.aibrain.app.model.IA
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Fase 12.9 — Testes unitários da lógica de pesquisa/filtro/ordenação que foi
 * movida da MainActivity para o MainViewModel na Fase 12.4. Antes desse
 * movimento, essa lógica vivia dentro de uma Activity e não era testável
 * de forma isolada.
 */
class MainViewModelTest {

    // Roda o LiveData de forma síncrona nos testes (sem precisar do main looper do Android).
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: MainViewModel

    private val chatgpt = criarIA(
        id = "chatgpt", nome = "ChatGPT",
        descricao = "Assistente de IA conversacional para conversas, código e escrita.",
        categorias = listOf("conversa", "codigo"),
        gratuita = true,
        notas = mapOf("conversa" to 9, "codigo" to 8)
    )
    private val midjourney = criarIA(
        id = "midjourney", nome = "Midjourney",
        descricao = "Geração de imagens artísticas a partir de texto.",
        categorias = listOf("imagem", "design"),
        gratuita = false,
        notas = mapOf("imagem" to 10, "design" to 9)
    )
    private val gemini = criarIA(
        id = "gemini", nome = "Gemini",
        descricao = "Assistente de IA do Google, com foco em pesquisa.",
        categorias = listOf("conversa", "pesquisa"),
        gratuita = true,
        notas = mapOf("conversa" to 7, "pesquisa" to 8)
    )

    private val catalogo = listOf(chatgpt, midjourney, gemini)

    @Before
    fun setUp() {
        viewModel = MainViewModel()
    }

    @Test
    fun `catalogo completo aparece sem nenhum filtro aplicado`() {
        viewModel.definirCatalogo(catalogo)

        assertEquals(catalogo, viewModel.resultado.value)
        assertEquals(true, viewModel.catalogoCarregado)
    }

    @Test
    fun `pesquisa por nome filtra apenas a IA correspondente`() {
        viewModel.definirCatalogo(catalogo)

        viewModel.definirTermoPesquisa("chatgpt")

        assertEquals(listOf(chatgpt), viewModel.resultado.value)
    }

    @Test
    fun `pesquisa por termo da descricao tambem encontra a IA`() {
        viewModel.definirCatalogo(catalogo)

        viewModel.definirTermoPesquisa("artistica")

        assertEquals(listOf(midjourney), viewModel.resultado.value)
    }

    @Test
    fun `pesquisa sem correspondencia retorna lista vazia`() {
        viewModel.definirCatalogo(catalogo)

        viewModel.definirTermoPesquisa("algo-que-nao-existe")

        assertEquals(emptyList<IA>(), viewModel.resultado.value)
    }

    @Test
    fun `filtro por categoria retorna somente IAs daquela categoria`() {
        viewModel.definirCatalogo(catalogo)

        viewModel.definirCategoria(Categoria.CONVERSA)

        assertEquals(listOf(chatgpt, gemini), viewModel.resultado.value)
    }

    @Test
    fun `pesquisa e categoria se combinam`() {
        viewModel.definirCatalogo(catalogo)

        viewModel.definirCategoria(Categoria.CONVERSA)
        viewModel.definirTermoPesquisa("gemini")

        assertEquals(listOf(gemini), viewModel.resultado.value)
    }

    @Test
    fun `ordenacao por ranking usa a nota media, maior primeiro`() {
        viewModel.definirCatalogo(catalogo)

        viewModel.definirOrdenacao(MainViewModel.Ordenacao.RANKING)

        // midjourney (9.5) > chatgpt (8.5) > gemini (7.5)
        assertEquals(listOf(midjourney, chatgpt, gemini), viewModel.resultado.value)
    }

    @Test
    fun `ordenacao novidades inverte a ordem do catalogo`() {
        viewModel.definirCatalogo(catalogo)

        viewModel.definirOrdenacao(MainViewModel.Ordenacao.NOVIDADES)

        assertEquals(catalogo.asReversed(), viewModel.resultado.value)
    }

    @Test
    fun `limpar categoria volta a mostrar todas as IAs`() {
        viewModel.definirCatalogo(catalogo)
        viewModel.definirCategoria(Categoria.IMAGEM)

        viewModel.definirCategoria(null)

        assertEquals(catalogo, viewModel.resultado.value)
    }

    // Fase 14.1 — paginação/lazy loading.
    @Test
    fun `resultado inicial mostra no maximo uma pagina de itens`() {
        val catalogoGrande = (1..45).map {
            criarIA(
                id = "ia-$it", nome = "IA $it",
                descricao = "Descrição $it",
                categorias = listOf("conversa"),
                gratuita = true,
                notas = mapOf("conversa" to 5)
            )
        }

        viewModel.definirCatalogo(catalogoGrande)

        assertEquals(MainViewModel.TAMANHO_PAGINA, viewModel.resultado.value?.size)
        assertEquals(true, viewModel.temMaisPaginas)
    }

    @Test
    fun `carregarMaisItens amplia a janela ate o fim da lista filtrada`() {
        val catalogoGrande = (1..45).map {
            criarIA(
                id = "ia-$it", nome = "IA $it",
                descricao = "Descrição $it",
                categorias = listOf("conversa"),
                gratuita = true,
                notas = mapOf("conversa" to 5)
            )
        }
        viewModel.definirCatalogo(catalogoGrande)

        viewModel.carregarMaisItens()
        assertEquals(MainViewModel.TAMANHO_PAGINA * 2, viewModel.resultado.value?.size)
        assertEquals(true, viewModel.temMaisPaginas)

        viewModel.carregarMaisItens()
        assertEquals(45, viewModel.resultado.value?.size)
        assertEquals(false, viewModel.temMaisPaginas)

        // Chamar de novo sem mais itens não deve quebrar nem duplicar.
        viewModel.carregarMaisItens()
        assertEquals(45, viewModel.resultado.value?.size)
    }

    @Test
    fun `mudar filtro reinicia a paginacao na primeira pagina`() {
        val catalogoGrande = (1..45).map {
            criarIA(
                id = "ia-$it", nome = "IA $it",
                descricao = "Descrição $it",
                categorias = listOf("conversa"),
                gratuita = true,
                notas = mapOf("conversa" to 5)
            )
        }
        viewModel.definirCatalogo(catalogoGrande)
        viewModel.carregarMaisItens()
        assertEquals(MainViewModel.TAMANHO_PAGINA * 2, viewModel.resultado.value?.size)

        viewModel.definirTermoPesquisa("ia-1")

        // "ia-1" casa com ia-1, ia-10..19, ia-1... — bem menos que uma página inteira.
        assertEquals(true, (viewModel.resultado.value?.size ?: 0) < MainViewModel.TAMANHO_PAGINA)
    }

    // ---- Fase 14.3 — filtroAtivo e limparFiltros() ----

    @Test
    fun `filtroAtivo comeca falso sem nenhum filtro aplicado`() {
        viewModel.definirCatalogo(catalogo)

        assertEquals(false, viewModel.filtroAtivo.value)
    }

    @Test
    fun `filtroAtivo fica verdadeiro ao definir termo de pesquisa`() {
        viewModel.definirCatalogo(catalogo)

        viewModel.definirTermoPesquisa("chatgpt")

        assertEquals(true, viewModel.filtroAtivo.value)
    }

    @Test
    fun `filtroAtivo fica verdadeiro ao selecionar categoria`() {
        viewModel.definirCatalogo(catalogo)

        viewModel.definirCategoria(Categoria.IMAGEM)

        assertEquals(true, viewModel.filtroAtivo.value)
    }

    @Test
    fun `filtroAtivo fica verdadeiro ao definir ordenacao`() {
        viewModel.definirCatalogo(catalogo)

        viewModel.definirOrdenacao(MainViewModel.Ordenacao.RANKING)

        assertEquals(true, viewModel.filtroAtivo.value)
    }

    @Test
    fun `filtroAtivo permanece falso quando termo de pesquisa e so espacos`() {
        viewModel.definirCatalogo(catalogo)

        viewModel.definirTermoPesquisa("   ")

        assertEquals(false, viewModel.filtroAtivo.value)
    }

    @Test
    fun `limparFiltros reseta pesquisa categoria e ordenacao de uma vez`() {
        viewModel.definirCatalogo(catalogo)
        viewModel.definirTermoPesquisa("chatgpt")
        viewModel.definirCategoria(Categoria.CONVERSA)
        viewModel.definirOrdenacao(MainViewModel.Ordenacao.RANKING)

        viewModel.limparFiltros()

        assertEquals(catalogo, viewModel.resultado.value)
        assertEquals(false, viewModel.filtroAtivo.value)
    }

    @Test
    fun `limparFiltros reinicia a paginacao na primeira pagina`() {
        val catalogoGrande = (1..45).map {
            criarIA(
                id = "ia-$it", nome = "IA $it",
                descricao = "Descrição $it",
                categorias = listOf("conversa"),
                gratuita = true,
                notas = mapOf("conversa" to 5)
            )
        }
        viewModel.definirCatalogo(catalogoGrande)
        viewModel.definirTermoPesquisa("ia-1")
        viewModel.carregarMaisItens()

        viewModel.limparFiltros()

        assertEquals(MainViewModel.TAMANHO_PAGINA, viewModel.resultado.value?.size)
        assertEquals(true, viewModel.temMaisPaginas)
    }

    private fun criarIA(
        id: String,
        nome: String,
        descricao: String,
        categorias: List<String>,
        gratuita: Boolean,
        notas: Map<String, Int>
    ) = IA(
        id = id,
        nome = nome,
        logo = "",
        site = "https://exemplo.com",
        descricao = descricao,
        categorias = categorias,
        idiomas = listOf("pt"),
        gratuita = gratuita,
        notas = notas
    )
}
