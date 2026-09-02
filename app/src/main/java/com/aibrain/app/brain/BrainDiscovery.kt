package com.aibrain.app.brain

import com.aibrain.app.model.Categoria
import com.aibrain.app.model.CategoriaDinamica
import com.aibrain.app.model.IA
import com.aibrain.app.model.NivelAcesso
import com.aibrain.app.util.notaMedia
import com.aibrain.app.util.notaPara
import com.aibrain.app.util.normalizarBusca

/**
 * Estado de filtro da central de descoberta.
 * Valores nulos significam "qualquer valor"; os filtros são combinados com AND.
 */
data class BrainCatalogFilter(
    val categoria: String? = null,
    val acesso: NivelAcesso? = null,
    val capacidade: String? = null,
    val plataforma: String? = null,
    val somenteFavoritos: Boolean = false,
    val termo: String = ""
)

data class BrainRankedIA(
    val ia: IA,
    val score: Int,
    val reasons: List<String>
)

data class BrainRecommendation(
    val objetivo: String,
    val categoriasDetectadas: List<Categoria>,
    val resultados: List<BrainRankedIA>,
    val melhorOpcao: BrainRankedIA?,
    val alternativas: List<BrainRankedIA>,
    val justificativa: List<String>
)

data class BrainComparisonRow(
    val criterio: String,
    val valores: List<String>
)

/**
 * Regras locais de descoberta. Não chama rede, não cria IAs e não substitui o
 * LocalAIRouter: o roteador continua responsável por comandos e execução.
 */
object BrainDiscoveryEngine {

    private val TERMOS_RELACIONADOS = mapOf(
        "programacao" to setOf("codigo", "code", "coding", "development", "programming", "software"),
        "programar" to setOf("codigo", "code", "coding", "development", "programming", "software"),
        "code" to setOf("codigo", "coding", "development", "programming", "software"),
        "coding" to setOf("codigo", "code", "development", "programming", "software"),
        "development" to setOf("codigo", "code", "coding", "programming", "software"),
        "programming" to setOf("codigo", "code", "coding", "development", "software"),
        "software" to setOf("codigo", "code", "coding", "development", "programming"),
        "pesquisar" to setOf("pesquisa", "research", "fontes", "referencias"),
        "pesquisa" to setOf("pesquisa", "research", "fontes", "referencias"),
        "research" to setOf("pesquisa", "fontes", "referencias"),
        "imagem" to setOf("imagem", "image", "foto", "arte visual"),
        "imagens" to setOf("imagem", "image", "foto", "arte visual"),
        "image" to setOf("imagem", "foto", "arte visual"),
        "escrever" to setOf("escrita", "writing", "texto", "redacao"),
        "escrita" to setOf("escrita", "writing", "texto", "redacao"),
        "writing" to setOf("escrita", "texto", "redacao")
    )

    /** Busca nos campos disponíveis do catálogo e nas categorias/casos de uso. */
    fun correspondeBusca(ia: IA, termo: String): Boolean {
        val consulta = termo.trim().normalizarBusca()
        if (consulta.isBlank()) return true

        val campos = buildList {
            add(ia.nome)
            add(ia.descricao)
            addAll(ia.categorias)
            addAll(ia.categorias.map(CategoriaDinamica::rotuloCurto))
            addAll(ia.notas.keys)
            addAll(ia.casosDeUso)
            addAll(ia.plataformas)
            add(ia.modeloAcesso.orEmpty())
            add(ia.status.orEmpty())
            add(ia.acesso.rotulo)
        }.map(String::normalizarBusca)

        if (campos.any { it.contains(consulta) }) return true

        // Expansão somente por aliases explícitos. Não usamos a detecção de
        // categoria aqui: um nome como "chatgpt" contém "chat", mas isso não
        // deve transformar toda IA de conversa em resultado.
        val termosExpandidos = buildSet {
            add(consulta)
            TERMOS_RELACIONADOS[consulta].orEmpty().forEach(::add)
        }
        return termosExpandidos.any { termoExpandido ->
            campos.any { campo -> campo.contains(termoExpandido) }
        }
    }

    /** Capacidades visíveis derivadas dos dados reais já presentes no catálogo. */
    fun capacidadesDaIA(ia: IA): Set<String> = buildSet {
        addAll(ia.categorias)
        addAll(ia.notas.keys)
    }

    fun atendeFiltros(ia: IA, filtro: BrainCatalogFilter, favoritos: Set<String> = emptySet()): Boolean {
        val categoria = filtro.categoria?.normalizarBusca()
        val capacidade = filtro.capacidade?.normalizarBusca()
        val plataforma = filtro.plataforma?.normalizarBusca()
        val passaCategoria = categoria == null || ia.categorias.any {
            it.normalizarBusca() == categoria || CategoriaDinamica.rotuloCurto(it).normalizarBusca() == categoria
        }
        val passaCapacidade = capacidade == null || capacidadesDaIA(ia).any { it.normalizarBusca() == capacidade }
        val passaPlataforma = plataforma == null || ia.plataformas.any { it.normalizarBusca() == plataforma }
        return passaCategoria &&
            (filtro.acesso == null || ia.acesso == filtro.acesso) &&
            passaCapacidade &&
            passaPlataforma &&
            (!filtro.somenteFavoritos || ia.id in favoritos) &&
            correspondeBusca(ia, filtro.termo)
    }

    fun filtrar(
        catalogo: List<IA>,
        filtro: BrainCatalogFilter,
        favoritos: Set<String> = emptySet()
    ): List<IA> = catalogo.filter { atendeFiltros(it, filtro, favoritos) }

    /**
     * Ranking orientado ao objetivo. A nota cadastrada tem prioridade; categoria
     * principal e favorito apenas desempatatam/ajudam a ordenar, sem inventar
     * avaliações ou percentuais.
     */
    fun recomendar(
        catalogo: List<IA>,
        objetivo: String,
        filtro: BrainCatalogFilter = BrainCatalogFilter(),
        favoritos: Set<String> = emptySet()
    ): BrainRecommendation {
        val categorias = detectarCategorias(objetivo)
        val filtroComObjetivo = if (categorias.isEmpty() || filtro.categoria != null) {
            filtro
        } else {
            filtro.copy(categoria = categorias.first().chave)
        }
        val candidatos = filtrar(catalogo, filtroComObjetivo, favoritos)
        val ranking = candidatos.mapNotNull { ia ->
            val notas = categorias.mapNotNull { ia.notaPara(it) }
            val melhorNota = notas.maxOrNull() ?: if (categorias.isEmpty()) ia.notaMedia().toInt().takeIf { ia.notas.isNotEmpty() } else null
            melhorNota?.let {
                val principal = categorias.any { categoria -> ia.categoriaPrincipal == categoria.chave }
                val favorita = ia.id in favoritos
                val score = it * 10 + (if (principal) 3 else 0) + (if (favorita) 1 else 0)
                BrainRankedIA(ia, score, explicar(ia, categorias, filtro, favoritos))
            }
        }.sortedWith(compareByDescending<BrainRankedIA> { it.score }.thenBy { it.ia.nome.lowercase() })

        val melhor = ranking.firstOrNull()
        val justificativa = melhor?.reasons.orEmpty()
        return BrainRecommendation(
            objetivo = objetivo,
            categoriasDetectadas = categorias,
            resultados = ranking,
            melhorOpcao = melhor,
            alternativas = ranking.drop(1).take(3),
            justificativa = justificativa
        )
    }

    private fun explicar(
        ia: IA,
        categorias: List<Categoria>,
        filtro: BrainCatalogFilter,
        favoritos: Set<String>
    ): List<String> = buildList {
        categorias.firstOrNull { ia.notaPara(it) != null }?.let { categoria ->
            add("possui nota cadastrada em ${categoria.rotulo}: ${ia.notaPara(categoria)}/10")
        }
        categorias.firstOrNull { ia.categoriaPrincipal == it.chave }?.let {
            add("tem ${it.rotulo.lowercase()} como categoria principal")
        }
        if (filtro.acesso != null && ia.acesso == filtro.acesso) {
            add("atende ao filtro de acesso ${ia.acesso.rotulo.lowercase()}")
        }
        if (ia.id in favoritos) add("está nos seus favoritos")
        if (isEmpty() && ia.notas.isNotEmpty()) add("tem dados de avaliação cadastrados no catálogo")
    }

    /** Linhas de comparação mostram somente dados presentes nas IAs selecionadas. */
    fun comparar(ias: List<IA>, objetivo: String? = null): List<BrainComparisonRow> {
        if (ias.isEmpty()) return emptyList()
        val categoriasObjetivo = objetivo.orEmpty().let(::detectarCategorias)
        val categorias = (categoriasObjetivo.map { it.chave } + ias.flatMap { it.categorias } + ias.flatMap { it.notas.keys })
            .distinct()
            .sortedWith(compareBy({ chave -> if (categoriasObjetivo.any { it.chave == chave }) 0 else 1 }, { chave -> CategoriaDinamica.rotuloCurto(chave) }))

        val linhas = categorias.map { chave ->
            BrainComparisonRow(
                criterio = CategoriaDinamica.rotuloCurto(chave),
                valores = ias.map { ia ->
                    ia.notaPara(chave)?.let { "$it/10" } ?: if (chave in ia.categorias) "✓" else "—"
                }
            )
        }.toMutableList()

        linhas += BrainComparisonRow("Acesso", ias.map { "${it.acesso.emoji} ${it.acesso.rotulo}" })
        if (ias.any { it.plataformas.isNotEmpty() }) {
            linhas += BrainComparisonRow("Plataformas", ias.map { ia -> ia.plataformas.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "—" })
        }
        if (ias.any { it.possuiApi != null }) {
            linhas += BrainComparisonRow("API", ias.map { it.possuiApi?.let { possui -> if (possui) "Sim" else "Não" } ?: "—" })
        }
        if (ias.any { it.requerLogin != null }) {
            linhas += BrainComparisonRow("Login", ias.map { it.requerLogin?.let { requer -> if (requer) "Necessário" else "Não informado" } ?: "—" })
        }
        return linhas
    }

    fun categoriasPresentes(catalogo: List<IA>): List<String> = catalogo.flatMap { it.categorias }.distinct().sorted()

    fun acessosPresentes(catalogo: List<IA>): List<NivelAcesso> = catalogo.map { it.acesso }.distinct().sortedBy(NivelAcesso::ordinal)
}
