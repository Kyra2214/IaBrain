package com.aibrain.app.brain

import com.aibrain.app.model.VariavelPrompt

/**
 * Fase 17.9 — Perguntas de refinamento do Prompt Builder: no máximo
 * [SessaoConstrutorPrompt.MAXIMO_PERGUNTAS] (3), uma por variável do
 * [SessaoConstrutorPrompt.templateSelecionado] ainda não informada pelo
 * usuário e sem valor `padrao` suficiente no template — priorizando
 * rapidez e simplicidade (regra fixa do Prompt Builder).
 *
 * Quarto passo do fluxo fixo (Fase 17.6:
 * [EstagioConstrutorPrompt.PERGUNTANDO]). O uso do `padrao` quando o
 * usuário NÃO responde é o fallback da Fase 17.10 — aqui só a seleção de
 * quais variáveis pedir e o registro das respostas dadas.
 */

/**
 * Variáveis do template ainda pendentes de pergunta: não estão em
 * [SessaoConstrutorPrompt.respostasColetadas] e não têm `padrao` (um
 * `padrao` em branco/nulo é tratado como insuficiente). Limitado ao que
 * ainda cabe dentro do máximo de 3 perguntas da sessão.
 */
fun variaveisAPerguntar(sessao: SessaoConstrutorPrompt): List<VariavelPrompt> {
    val template = sessao.templateSelecionado ?: return emptyList()
    val restantes = SessaoConstrutorPrompt.MAXIMO_PERGUNTAS - sessao.perguntasFeitas
    if (restantes <= 0) return emptyList()

    return template.variaveis
        .filter { it.nome !in sessao.respostasColetadas && it.padrao.isNullOrBlank() }
        .take(restantes)
}

/** Próxima variável a perguntar, ou null se não há mais nenhuma pendente (ou fora do estágio PERGUNTANDO). */
fun proximaVariavelPendente(sessao: SessaoConstrutorPrompt): VariavelPrompt? {
    if (sessao.estagio != EstagioConstrutorPrompt.PERGUNTANDO) return null
    return variaveisAPerguntar(sessao).firstOrNull()
}

/** Texto da pergunta para uma variável, formatado a partir do nome (ex: "ESTILO" → "Qual o valor de estilo?"). */
fun textoPergunta(variavel: VariavelPrompt): String {
    val nomeLegivel = variavel.nome.lowercase().replace('_', ' ')
    return "Qual o valor de $nomeLegivel?"
}

/**
 * Registra a resposta do usuário para [nomeVariavel], soma 1 à contagem de
 * perguntas feitas e avança para
 * [EstagioConstrutorPrompt.SUBSTITUINDO_VARIAVEIS] quando não há mais
 * nenhuma variável pendente (todas respondidas ou limite de 3 perguntas
 * atingido — Fase 17.10 decide o valor final de cada variável não
 * respondida via `padrao`).
 */
fun registrarResposta(sessao: SessaoConstrutorPrompt, nomeVariavel: String, resposta: String): SessaoConstrutorPrompt {
    val respostas = sessao.respostasColetadas + (nomeVariavel to resposta)
    val atualizada = sessao.copy(
        respostasColetadas = respostas,
        perguntasFeitas = sessao.perguntasFeitas + 1
    )

    return if (variaveisAPerguntar(atualizada).isEmpty()) {
        atualizada.copy(estagio = EstagioConstrutorPrompt.SUBSTITUINDO_VARIAVEIS)
    } else {
        atualizada
    }
}
