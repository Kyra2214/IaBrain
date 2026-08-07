package com.aibrain.app.brain

import com.aibrain.app.model.Prompt

/**
 * Fase 17.11 — Substituição APENAS dos campos de `variaveis` no `template`
 * (texto fixo permanece intacto), mantendo o objetivo original do usuário e
 * a categoria escolhida — regra fixa do Prompt Builder: nunca reescreve o
 * template, só troca `{VARIAVEL}` pelo valor final de cada variável.
 *
 * Quinto passo do fluxo fixo (Fase 17.6:
 * [EstagioConstrutorPrompt.SUBSTITUINDO_VARIAVEIS] → [EstagioConstrutorPrompt.PROMPT_ENTREGUE]).
 */

/**
 * Substitui cada placeholder `{NOME}` do [template] pelo valor correspondente
 * em [valoresFinais] (chave = nome da variável, sem chaves). Placeholder sem
 * valor correspondente é removido (string vazia) em vez de travar a geração —
 * na prática não deve ocorrer, já que [resolverTodasVariaveis] (Fase 17.10)
 * cobre toda variável declarada em `template.variaveis`.
 */
fun substituirVariaveis(template: Prompt, valoresFinais: Map<String, String>): String {
    var resultado = template.template
    template.variaveis.forEach { variavel ->
        val valor = valoresFinais[variavel.nome].orEmpty()
        resultado = resultado.replace("{${variavel.nome}}", valor)
    }
    return resultado
}

/**
 * Fase 17.6 — avança a sessão do estágio
 * [EstagioConstrutorPrompt.SUBSTITUINDO_VARIAVEIS] para
 * [EstagioConstrutorPrompt.PROMPT_ENTREGUE], gerando [SessaoConstrutorPrompt.promptFinal]
 * a partir do template selecionado (Fase 17.8/17.10) e do valor final de
 * cada variável (resposta do usuário ou `padrao`/vazio — Fase 17.10).
 * Sem template selecionado, retorna a sessão inalterada.
 */
fun avancarSubstituicaoVariaveis(sessao: SessaoConstrutorPrompt): SessaoConstrutorPrompt {
    val template = sessao.templateSelecionado ?: return sessao
    val valoresFinais = resolverTodasVariaveis(template, sessao.respostasColetadas)
    val promptFinal = substituirVariaveis(template, valoresFinais)

    return sessao.copy(
        promptFinal = promptFinal,
        estagio = EstagioConstrutorPrompt.PROMPT_ENTREGUE
    )
}
