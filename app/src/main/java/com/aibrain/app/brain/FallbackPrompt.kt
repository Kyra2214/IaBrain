package com.aibrain.app.brain

import com.aibrain.app.model.CategoriaPrompt
import com.aibrain.app.model.Prompt
import com.aibrain.app.model.VariavelPrompt

/**
 * Fase 17.10 — Tratamento de fallback do Prompt Builder, garantindo que o
 * fluxo (Fase 17.6) NUNCA trava por falta de resposta ou por ausência de
 * template com correspondência exata:
 *
 * 1) Variável não respondida → usa o valor `padrao` da variável (Fase 17.4);
 *    sem `padrao`, resolve para vazio em vez de travar o fluxo.
 * 2) Categoria sem template com correspondência de subcaso (Fase 17.8) →
 *    usa o template mais próximo da própria categoria e avisa isso na
 *    explicação final, reaproveitando o padrão de mensagem de fallback já
 *    usado quando nenhuma categoria é detectada (Fase 13.4).
 */

/** Valor final de uma variável: resposta do usuário, senão `padrao`, senão vazio (nunca trava por falta de dado). */
fun valorFinalVariavel(variavel: VariavelPrompt, respostasColetadas: Map<String, String>): String =
    respostasColetadas[variavel.nome] ?: variavel.padrao ?: ""

/** Resolve o valor final de TODAS as variáveis do [template], aplicando o fallback de `padrao`/vazio (item 1). */
fun resolverTodasVariaveis(template: Prompt, respostasColetadas: Map<String, String>): Map<String, String> =
    template.variaveis.associate { it.nome to valorFinalVariavel(it, respostasColetadas) }

/**
 * Fase 17.9/17.10 — pula a pergunta atual (usuário não respondeu): resolve a
 * variável pendente com seu `padrao`/vazio e registra como se fosse a
 * resposta, seguindo o mesmo caminho de avanço de [registrarResposta].
 * Sem variável pendente, retorna a sessão inalterada.
 */
fun pularPerguntaAtual(sessao: SessaoConstrutorPrompt): SessaoConstrutorPrompt {
    val variavel = proximaVariavelPendente(sessao) ?: return sessao
    val valor = valorFinalVariavel(variavel, sessao.respostasColetadas)
    return registrarResposta(sessao, variavel.nome, valor)
}

/** Mensagem de aviso quando o template entregue veio do fallback "mais próximo da categoria" (mesmo tom da Fase 13.4). */
private fun mensagemFallbackTemplate(template: Prompt): String =
    "Não encontrei um template exato para o que você descreveu; usando o mais próximo em " +
        "${template.categoria.rotulo}: \"${template.subcaso}\"."

/**
 * Busca com fallback (item 2): tenta [buscarTemplate] (Fase 17.8) por
 * correspondência de subcaso; sem correspondência, cai para o primeiro
 * template cadastrado da própria [categoria] (sempre existe, mínimo 5 por
 * categoria — Fase 17.5). Segundo valor do par indica se o fallback foi usado.
 */
fun buscarTemplateComFallback(categoria: CategoriaPrompt, textoUsuario: String, biblioteca: List<Prompt>): Pair<Prompt?, Boolean> {
    val correspondenciaExata = buscarTemplate(categoria, textoUsuario, biblioteca)
    if (correspondenciaExata != null) return correspondenciaExata to false

    val templateMaisProximo = biblioteca.firstOrNull { it.categoria == categoria }
    return templateMaisProximo to (templateMaisProximo != null)
}

/**
 * Substitui [avancarBuscaTemplate] (Fase 17.8) por uma versão com fallback:
 * avança para [EstagioConstrutorPrompt.PERGUNTANDO] sempre que a categoria já
 * foi detectada, usando o template exato ou, na ausência dele, o mais
 * próximo da categoria — com [SessaoConstrutorPrompt.avisoFallback] preenchido
 * nesse segundo caso.
 */
fun avancarBuscaTemplateComFallback(sessao: SessaoConstrutorPrompt, biblioteca: List<Prompt>): SessaoConstrutorPrompt {
    val categoria = sessao.categoriaDetectada ?: return sessao
    val (template, usouFallback) = buscarTemplateComFallback(categoria, sessao.textoUsuario, biblioteca)
    template ?: return sessao

    return sessao.copy(
        templateSelecionado = template,
        estagio = EstagioConstrutorPrompt.PERGUNTANDO,
        avisoFallback = if (usouFallback) mensagemFallbackTemplate(template) else null
    )
}
