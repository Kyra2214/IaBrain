package com.aibrain.app.brain

import com.aibrain.app.model.CategoriaPrompt
import com.aibrain.app.model.Prompt
import java.text.Normalizer

/**
 * Fase 17.8 — Busca do template mais adequado ENTRE os templates da categoria
 * já detectada (Fase 17.7), por correspondência de palavras-chave do
 * [Prompt.subcaso] (ex: "gol", "estádio" → subcaso "Foto esportiva
 * profissional" dentro de Imagem) — nunca gera um prompt do zero se já
 * existir template compatível na Biblioteca (Fase 16), regra fixa do
 * Prompt Builder.
 *
 * Terceiro passo do fluxo fixo (Fase 17.6:
 * [EstagioConstrutorPrompt.BUSCANDO_TEMPLATE]). O fallback para quando
 * nenhum subcaso corresponde (usar o template mais próximo da categoria e
 * avisar na explicação final) é escopo da Fase 17.10 — aqui só a busca por
 * correspondência real.
 */

private fun String.semAcento(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

private fun String.tokens(): List<String> =
    semAcento().lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }

/**
 * Pontua cada template da [categoria] pela sobreposição de palavras entre o
 * texto do usuário e o `subcaso`/`titulo`/`tags` do template (subcaso vale
 * mais, por ser o campo mais específico do dado — mesmo princípio de peso
 * maior para o sinal mais específico já usado em `detectarCategoria()`,
 * Fase 13.2).
 */
private const val PESO_SUBCASO = 3
private const val PESO_TITULO = 2
private const val PESO_TAG = 1

private fun pontuarTemplates(textoUsuario: String, templatesDaCategoria: List<Prompt>): List<Pair<Prompt, Int>> {
    val termosUsuario = textoUsuario.tokens().toSet()
    if (termosUsuario.isEmpty()) return templatesDaCategoria.map { it to 0 }

    return templatesDaCategoria.map { prompt ->
        val termosSubcaso = prompt.subcaso.tokens().toSet()
        val termosTitulo = prompt.titulo.tokens().toSet()
        val termosTags = prompt.tags.flatMap { it.tokens() }.toSet()

        val pontos = termosUsuario.count { it in termosSubcaso } * PESO_SUBCASO +
            termosUsuario.count { it in termosTitulo } * PESO_TITULO +
            termosUsuario.count { it in termosTags } * PESO_TAG

        prompt to pontos
    }
}

/**
 * Busca o template mais adequado dentro de [categoria], por correspondência
 * de palavras-chave do [textoUsuario] contra `subcaso`/`titulo`/`tags`.
 * Retorna null se nenhum template da categoria tiver correspondência real
 * (pontuação zero em todos) — fallback fica para a Fase 17.10.
 */
fun buscarTemplate(categoria: CategoriaPrompt, textoUsuario: String, biblioteca: List<Prompt>): Prompt? {
    val templatesDaCategoria = biblioteca.filter { it.categoria == categoria }
    if (templatesDaCategoria.isEmpty()) return null

    return pontuarTemplates(textoUsuario, templatesDaCategoria)
        .filter { it.second > 0 }
        .maxByOrNull { it.second }
        ?.first
}

/**
 * Fase 17.6 — avança a sessão do estágio
 * [EstagioConstrutorPrompt.BUSCANDO_TEMPLATE] para
 * [EstagioConstrutorPrompt.PERGUNTANDO] quando um template compatível é
 * encontrado na categoria já detectada. Sem categoria detectada ou sem
 * template compatível, a sessão permanece no mesmo estágio (fallback: Fase 17.10).
 */
fun avancarBuscaTemplate(sessao: SessaoConstrutorPrompt, biblioteca: List<Prompt>): SessaoConstrutorPrompt {
    val categoria = sessao.categoriaDetectada ?: return sessao
    val template = buscarTemplate(categoria, sessao.textoUsuario, biblioteca) ?: return sessao

    return sessao.copy(
        templateSelecionado = template,
        estagio = EstagioConstrutorPrompt.PERGUNTANDO
    )
}
