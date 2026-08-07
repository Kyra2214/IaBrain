package com.aibrain.app.brain

import com.aibrain.app.model.Prompt

/**
 * Fase 17.12 — Adaptação do prompt final para a IA de destino, usando
 * `Prompt.melhorPara` do template selecionado (Fase 17.8/17.10): ChatGPT,
 * Claude, Grok, Gemini, IAs de imagem, vídeo, código — conforme cadastrado
 * por template na Biblioteca (Fase 16.3).
 *
 * ASSUMINDO: cada `template` da Biblioteca já foi escrito com o estilo
 * adequado à(s) IA(s) do seu próprio `melhor_para` (Fase 16.3/17.3-17.5 —
 * ex. templates de Imagem descrevem cena/estilo/iluminação, já no formato
 * que IAs de imagem esperam); reescrever o texto do prompt por IA de destino
 * duplicaria trabalho e arriscaria alterar o objetivo original do usuário
 * (proibido pela regra fixa da Fase 17.11). A "adaptação" desta fase é
 * portanto a escolha explícita de qual IA do `melhor_para` é a de destino
 * — preferência do usuário quando informada e compatível com o template,
 * senão a primeira opção cadastrada — para uso na recomendação final
 * (Fase 17.13) e na resposta enxuta (Fase 17.14).
 */

/**
 * Escolhe a IA de destino entre as opções de `template.melhorPara`.
 * [preferenciaUsuario], se informada e presente em `melhorPara` (comparação
 * sem diferenciar maiúsculas/minúsculas), tem prioridade; caso contrário,
 * usa a primeira opção cadastrada no template. Retorna null se o template
 * não tiver nenhuma IA cadastrada em `melhorPara`.
 */
fun selecionarIADestino(template: Prompt, preferenciaUsuario: String? = null): String? {
    if (preferenciaUsuario != null) {
        val correspondente = template.melhorPara.firstOrNull { it.equals(preferenciaUsuario, ignoreCase = true) }
        if (correspondente != null) return correspondente
    }
    return template.melhorPara.firstOrNull()
}

/**
 * Fase 17.6 — preenche [SessaoConstrutorPrompt.iaDestino] a partir do
 * template selecionado, sem alterar [SessaoConstrutorPrompt.promptFinal]
 * nem o estágio (a sessão já está em [EstagioConstrutorPrompt.PROMPT_ENTREGUE]
 * após a Fase 17.11). Sem template selecionado, retorna a sessão inalterada.
 */
fun avancarAdaptacaoIADestino(sessao: SessaoConstrutorPrompt, preferenciaUsuario: String? = null): SessaoConstrutorPrompt {
    val template = sessao.templateSelecionado ?: return sessao
    return sessao.copy(iaDestino = selecionarIADestino(template, preferenciaUsuario))
}
