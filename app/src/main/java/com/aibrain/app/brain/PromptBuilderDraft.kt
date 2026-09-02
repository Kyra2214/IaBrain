package com.aibrain.app.brain

import com.aibrain.app.model.CategoriaPrompt
import com.aibrain.app.model.Prompt
import com.aibrain.app.model.VariavelPrompt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Estado editável do Builder; não é persistido separadamente do Prompt. */
data class PromptBuilderDraft(
    val id: String? = null,
    val titulo: String = "",
    val categoria: CategoriaPrompt = CategoriaPrompt.ENGENHARIA_DE_PROMPT,
    val objetivo: String = "",
    val contexto: String = "",
    val tarefa: String = "",
    val restricoes: String = "",
    val formatoSaida: String = "",
    val textoLivre: String = "",
    val variaveis: List<VariavelPrompt> = emptyList(),
    val valoresVariaveis: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList(),
    val iaDestinoId: String? = null,
    val iaDestinoNome: String? = null,
    val comandoRelacionado: String? = null,
    val descricao: String = "",
    val subcaso: String = "Prompt personalizado",
    val nivel: String = "personalizado",
    val dataCriacao: String? = null
) {
    fun detectarVariaveis(): PromptBuilderDraft {
        val nomes = PLACEHOLDER.findAll(textoLivre + "\n" + objetivo + "\n" + contexto + "\n" + tarefa + "\n" + restricoes + "\n" + formatoSaida)
            .map { normalizarNome(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .toList()
        val existentes = variaveis.associateBy { normalizarNome(it.nome) }
        val novas = nomes.distinct().map { existentes[it] ?: VariavelPrompt(it) }
        val valores = valoresVariaveis.mapKeys { normalizarNome(it.key) }.filterKeys { it in novas.map { v -> normalizarNome(v.nome) } }
        return copy(variaveis = novas, valoresVariaveis = valores)
    }

    fun adicionarVariavel(nome: String, padrao: String? = null): PromptBuilderDraft {
        val normalizado = normalizarNome(nome)
        if (normalizado.isBlank() || variaveis.any { normalizarNome(it.nome) == normalizado }) return this
        return copy(variaveis = variaveis + VariavelPrompt(normalizado, padrao), valoresVariaveis = valoresVariaveis + (normalizado to ""))
    }

    fun editarVariavel(nome: String, novoNome: String, valor: String? = null): PromptBuilderDraft {
        val antigo = normalizarNome(nome)
        val novo = normalizarNome(novoNome)
        if (novo.isBlank() || variaveis.any { normalizarNome(it.nome) == novo && normalizarNome(it.nome) != antigo }) return this
        val atualizadas = variaveis.map { if (normalizarNome(it.nome) == antigo) it.copy(nome = novo) else it }
        val valores = valoresVariaveis.toMutableMap()
        val antigoValor = valores.remove(antigo)
        valores[novo] = valor ?: antigoValor.orEmpty()
        return copy(variaveis = atualizadas, valoresVariaveis = valores)
    }

    fun removerVariavel(nome: String): PromptBuilderDraft {
        val alvo = normalizarNome(nome)
        return copy(variaveis = variaveis.filterNot { normalizarNome(it.nome) == alvo }, valoresVariaveis = valoresVariaveis - alvo)
    }

    fun preview(): String = renderPrompt(this)

    fun toPrompt(): Prompt = Prompt(
        id = id ?: UUID.randomUUID().toString(),
        titulo = titulo.ifBlank { "Prompt sem título" },
        categoria = categoria,
        subcaso = subcaso,
        descricaoCurta = descricao.ifBlank { objetivo.ifBlank { "Prompt personalizado" } },
        objetivo = objetivo,
        nivel = nivel,
        melhorPara = listOfNotNull(iaDestinoNome),
        template = preview(),
        variaveis = variaveis,
        tags = tags,
        dataCriacao = dataCriacao ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
        contexto = contexto,
        tarefa = tarefa,
        restricoes = restricoes,
        formatoSaida = formatoSaida,
        iaDestinoId = iaDestinoId,
        iaDestinoNome = iaDestinoNome,
        comandoRelacionado = comandoRelacionado
    )

    companion object {
        private val PLACEHOLDER = Regex("\\{\\{\\s*([A-Za-zÀ-ÿ0-9_ -]+?)\\s*}}")

        fun fromPrompt(prompt: Prompt, duplicar: Boolean = false): PromptBuilderDraft = PromptBuilderDraft(
            id = if (duplicar) null else prompt.id,
            titulo = if (duplicar) "${prompt.titulo} (cópia)" else prompt.titulo,
            categoria = prompt.categoria,
            objetivo = prompt.objetivo,
            contexto = prompt.contexto,
            tarefa = prompt.tarefa,
            restricoes = prompt.restricoes,
            formatoSaida = prompt.formatoSaida,
            textoLivre = prompt.template,
            variaveis = prompt.variaveis,
            valoresVariaveis = prompt.variaveis.associate { it.nome to (it.padrao.orEmpty()) },
            tags = prompt.tags,
            iaDestinoId = prompt.iaDestinoId,
            iaDestinoNome = prompt.iaDestinoNome,
            comandoRelacionado = prompt.comandoRelacionado,
            descricao = prompt.descricaoCurta,
            subcaso = prompt.subcaso,
            nivel = prompt.nivel,
            dataCriacao = prompt.dataCriacao
        )

        fun normalizarNome(nome: String): String = nome.trim().uppercase(Locale.ROOT)
        fun renderPrompt(draft: PromptBuilderDraft): String {
            val livre = draft.textoLivre.trim()
            val estruturado = buildList {
                draft.objetivo.trim().takeIf { it.isNotBlank() }?.let { add("OBJETIVO\n$it") }
                draft.contexto.trim().takeIf { it.isNotBlank() }?.let { add("CONTEXTO\n$it") }
                draft.tarefa.trim().takeIf { it.isNotBlank() }?.let { add("TAREFA\n$it") }
                draft.restricoes.trim().takeIf { it.isNotBlank() }?.let { add("RESTRIÇÕES\n$it") }
                draft.formatoSaida.trim().takeIf { it.isNotBlank() }?.let { add("FORMATO DE SAÍDA\n$it") }
            }.joinToString("\n\n")
            val template = listOf(livre, estruturado).filter { it.isNotBlank() }.joinToString("\n\n")
            return PLACEHOLDER.replace(template) { match ->
                val nome = normalizarNome(match.groupValues[1])
                draft.valoresVariaveis[nome]?.takeIf { it.isNotBlank() } ?: "{{${nome}}}"
            }.trim()
        }
    }
}

fun PromptBuilderDraft.fromGenerationSpec(spec: PromptGenerationSpec): PromptBuilderDraft = copy(
    titulo = "Prompt ${spec.comando}",
    objetivo = spec.objetivo,
    contexto = spec.contexto.orEmpty(),
    textoLivre = ContextualPromptGenerator.generate(spec),
    variaveis = detectarVariaveis().variaveis,
    iaDestinoId = spec.iaId,
    iaDestinoNome = spec.iaNome,
    comandoRelacionado = spec.comando,
    tags = spec.capacidades.toList()
)
