package com.aibrain.app.brain

import com.aibrain.app.model.Categoria
import com.aibrain.app.model.IA
import com.aibrain.app.model.NivelAcesso
import com.aibrain.app.util.notaPara

/** Intenção interpretada a partir da ideia livre do usuário. */
data class ProjetoIntent(
    val textoOriginal: String,
    val tipoProjeto: String?,
    val plataforma: String?,
    val areas: List<Categoria>,
    val complexidade: Complexidade,
    val orcamentoMensal: Double?,
    val acessoPreferido: NivelAcesso?,
    val restricoes: List<String> = emptyList()
)

enum class Complexidade { BAIXA, MEDIA, ALTA }

data class CatalogoQuery(
    val idsPermitidos: Set<String>? = null,
    val plataforma: String? = null,
    val acesso: NivelAcesso? = null,
    val somenteAtivas: Boolean = true
) {
    fun aplicar(catalogo: List<IA>): List<IA> = catalogo.filter { ia ->
        (idsPermitidos == null || ia.id in idsPermitidos!!) &&
            (plataforma == null || ia.plataformas.isEmpty() || ia.plataformas.any { it.equals(plataforma, true) }) &&
            (!somenteAtivas || ia.status.isNullOrBlank() || !ia.status.equals("inativa", true)) &&
            (acesso == null || ia.acesso == acesso)
    }
}

data class FuncaoProjeto(val categoria: Categoria, val nome: String)
data class RecomendacaoFuncao(
    val funcao: FuncaoProjeto,
    val ia: IA?,
    val nota: Int,
    val motivo: String,
    val alternativas: List<IA> = emptyList()
)
data class StackProjeto(val itens: List<IA>, val custoMensalEstimado: String)
data class ProjetoRecommendation(
    val intent: ProjetoIntent,
    val funcoes: List<FuncaoProjeto>,
    val recomendacoes: List<RecomendacaoFuncao>,
    val stack: StackProjeto,
    val encontrouCorrespondencia: Boolean
)

object ProjetoIntentParser {
    fun parse(texto: String): ProjetoIntent {
        val t = texto.trim()
        val normalizado = t.lowercase().normalizar()
        val areas = detectarCategorias(t, 5)
        val plataforma = when {
            "android" in normalizado -> "android"
            "ios" in normalizado || "iphone" in normalizado -> "ios"
            "web" in normalizado || "site" in normalizado -> "web"
            else -> null
        }
        val tipo = when {
            "aplicativo" in normalizado || "app" in normalizado -> "Aplicativo"
            "site" in normalizado || "web" in normalizado -> "Site"
            "video" in normalizado -> "Vídeo"
            else -> null
        }
        val orçamento = Regex("(?:r\\$|rs|orçamento|budget)\\s*(\\d+[,.]?\\d*)", RegexOption.IGNORE_CASE)
            .find(normalizado)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
        val acesso = when {
            "open source" in normalizado || "gratuito" in normalizado || "grátis" in normalizado -> NivelAcesso.GRATUITA
            "freemium" in normalizado -> NivelAcesso.FREEMIUM
            "pago" in normalizado || "paga" in normalizado -> NivelAcesso.PAGA
            else -> null
        }
        val complexidade = when {
            listOf("simples", "básico", "basico", "protótipo", "prototipo").any { it in normalizado } -> Complexidade.BAIXA
            listOf("complexo", "produção", "producao", "escala", "enterprise").any { it in normalizado } -> Complexidade.ALTA
            else -> Complexidade.MEDIA
        }
        val restricoes = listOf("sem login", "sem api", "offline").filter { it in normalizado }
        return ProjetoIntent(t, tipo, plataforma, areas, complexidade, orçamento, acesso, restricoes)
    }

    private fun String.normalizar(): String = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
}

object RecomendadorProjeto {
    fun recomendar(catalogo: List<IA>, intent: ProjetoIntent): ProjetoRecommendation {
        val funcoes = intent.areas.map { FuncaoProjeto(it, it.rotulo) }
        val elegiveis = CatalogoQuery(plataforma = intent.plataforma).aplicar(catalogo)
        val recomendações = funcoes.map { funcao ->
            val ranking = elegiveis.map { ia -> ia to pontuar(ia, funcao.categoria, intent) }
                .filter { it.second > 0 }.sortedByDescending { it.second }
            val melhor = ranking.firstOrNull()
            RecomendacaoFuncao(funcao, melhor?.first, melhor?.second ?: 0,
                melhor?.first?.let { motivo(it, funcao.categoria, intent) } ?: "Nenhuma correspondência suficiente no catálogo.",
                ranking.drop(1).take(3).map { it.first })
        }
        val itens = recomendações.mapNotNull { it.ia }.distinctBy { it.id }
        return ProjetoRecommendation(intent, funcoes, recomendações, StackProjeto(itens, estimarCusto(itens)), itens.isNotEmpty())
    }
    private fun pontuar(ia: IA, categoria: Categoria, intent: ProjetoIntent): Int {
        if (intent.acessoPreferido != null && ia.acesso != intent.acessoPreferido) return 0
        if (intent.orcamentoMensal == 0.0 && !ia.gratuita) return 0
        val nota = ia.notaPara(categoria) ?: return 0
        val especializacao = if (ia.categoriaPrincipal == categoria.chave) 5 else 0
        val casoDeUso = if (ia.casosDeUso.any { it.contains(categoria.chave, true) || it.contains(categoria.rotulo, true) }) 4 else 0
        val api = if ("api" in intent.restricoes && ia.possuiApi == true) 2 else 0
        return nota * 3 + especializacao + casoDeUso + api + if (ia.gratuita) 2 else 0
    }
    private fun motivo(ia: IA, categoria: Categoria, intent: ProjetoIntent): String = buildString {
        append("Boa aderência a ${categoria.rotulo.lowercase()}")
        if (ia.categoriaPrincipal == categoria.chave) append(" e especialização principal")
        if (ia.casosDeUso.isNotEmpty()) append("; casos de uso cadastrados")
        if (intent.orcamentoMensal == 0.0 && ia.gratuita) append("; compatível com orçamento zero")
    }
    private fun estimarCusto(itens: List<IA>): String = when {
        itens.isEmpty() -> "Não disponível"
        itens.all { it.gratuita } -> "R$ 0/mês (camadas gratuitas)"
        else -> "Variável — confirme preços nos sites oficiais"
    }
}

fun List<IA>.recomendarProjeto(texto: String): ProjetoRecommendation =
    RecomendadorProjeto.recomendar(this, ProjetoIntentParser.parse(texto))
