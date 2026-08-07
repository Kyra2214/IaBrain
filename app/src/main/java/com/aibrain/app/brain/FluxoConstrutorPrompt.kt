package com.aibrain.app.brain

import com.aibrain.app.model.CategoriaPrompt
import com.aibrain.app.model.Prompt

/**
 * Fase 17.6 — Fluxo fixo e sequencial do Assistente de Prompts (Prompt Builder),
 * operando sobre a Biblioteca inteira (Fase 16) para PREENCHER templates travados,
 * nunca para redigir prompts do zero (mesmo papel estrutural de [RecomendadorIA] na Fase 9,
 * mas aqui sobre `Prompt` em vez de `IA`).
 *
 * Sequência fixa do roadmap:
 * ```
 * Usuário → Identifica intenção → Busca template correto na Biblioteca
 *         → Faz no máximo 3 perguntas → Substitui variáveis → Entrega prompt final
 * ```
 *
 * Este submódulo define apenas o ESQUELETO do fluxo (estágios + estado da sessão);
 * cada etapa é implementada em seu próprio submódulo, sem pular ordem:
 * - [EstagioConstrutorPrompt.IDENTIFICANDO_INTENCAO] → Fase 17.7 (detecção de categoria)
 * - [EstagioConstrutorPrompt.BUSCANDO_TEMPLATE] → Fase 17.8 (busca por subcaso)
 * - [EstagioConstrutorPrompt.PERGUNTANDO] → Fase 17.9/17.10 (perguntas + fallback)
 * - [EstagioConstrutorPrompt.SUBSTITUINDO_VARIAVEIS] → Fase 17.11 (substituição)
 * - [EstagioConstrutorPrompt.PROMPT_ENTREGUE] → Fase 17.12/17.13/17.14 (adaptação/recomendação/resposta enxuta)
 */
enum class EstagioConstrutorPrompt {
    IDENTIFICANDO_INTENCAO,
    BUSCANDO_TEMPLATE,
    PERGUNTANDO,
    SUBSTITUINDO_VARIAVEIS,
    PROMPT_ENTREGUE
}

/**
 * Estado de uma sessão do Prompt Builder, avançando estritamente na ordem de
 * [EstagioConstrutorPrompt]. Cada campo é preenchido pelo submódulo responsável
 * pelo estágio correspondente; nenhum estágio é pulado (mesma regra do roadmap
 * que rege as próprias Fases: "nunca pular etapas").
 *
 * [respostasColetadas] guarda o que o usuário já respondeu por nome de variável,
 * usado pela Fase 17.9/17.10 para saber quais perguntas ainda faltam e por
 * [Prompt.variaveis]/`padrao` como fallback quando a resposta não chega.
 * [avisoFallback] (Fase 17.10) é preenchido só quando o template selecionado
 * não veio de correspondência exata de subcaso, e sim do fallback "mais
 * próximo da categoria" — usado na explicação final (Fase 17.14).
 * [iaDestino] (Fase 17.12) é a IA recomendada para o [promptFinal], escolhida
 * a partir de `Prompt.melhorPara` do template selecionado.
 * [recomendacaoIA]/[motivoRecomendacaoIA] (Fase 17.13) reaproveitam o
 * `RecomendadorIA` da Fase 9 sobre o catálogo de IAs, indicando qual IA do
 * catálogo usar para este prompt e por quê (breve).
 */
data class SessaoConstrutorPrompt(
    val estagio: EstagioConstrutorPrompt = EstagioConstrutorPrompt.IDENTIFICANDO_INTENCAO,
    val textoUsuario: String = "",
    val categoriaDetectada: CategoriaPrompt? = null,
    val templateSelecionado: Prompt? = null,
    val respostasColetadas: Map<String, String> = emptyMap(),
    val perguntasFeitas: Int = 0,
    val promptFinal: String? = null,
    val avisoFallback: String? = null,
    val iaDestino: String? = null,
    val recomendacaoIA: RecomendacaoIA? = null,
    val motivoRecomendacaoIA: String? = null
) {
    /** Fase 17.9 — regra fixa: no máximo 3 perguntas de refinamento por sessão. */
    val podePerguntar: Boolean
        get() = perguntasFeitas < MAXIMO_PERGUNTAS

    companion object {
        const val MAXIMO_PERGUNTAS = 3
    }
}
