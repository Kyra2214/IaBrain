package com.aibrain.app.brain

import com.aibrain.app.model.Prompt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fase 17.15 — Salvar o prompt gerado pelo Prompt Builder como novo item na
 * Biblioteca (Fase 16): monta um [Prompt] "fixado" a partir da sessão já
 * concluída (Fase 17.14 — `PROMPT_ENTREGUE` com `promptFinal` preenchido),
 * pronto para ser persistido por [com.aibrain.app.data.PromptDadosLocaisRepository.salvarPromptGerado].
 *
 * O novo item usa o texto JÁ substituído ([SessaoConstrutorPrompt.promptFinal])
 * como `template`, sem `variaveis` — diferente do template original da
 * Biblioteca, este item representa um prompt PRONTO, específico da sessão do
 * usuário (mesmo contrato de [Prompt], Fase 16.1, mas sem placeholders
 * restantes para substituir).
 */

private val FORMATO_DATA = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

/**
 * Monta o [Prompt] a ser salvo a partir da [sessao]. Retorna null se a sessão
 * ainda não chegou em [EstagioConstrutorPrompt.PROMPT_ENTREGUE] ou não tem
 * `templateSelecionado`/`promptFinal` (nada para salvar ainda).
 */
fun criarPromptGeradoASalvar(sessao: SessaoConstrutorPrompt): Prompt? {
    if (sessao.estagio != EstagioConstrutorPrompt.PROMPT_ENTREGUE) return null
    val origem = sessao.templateSelecionado ?: return null
    val promptFinal = sessao.promptFinal ?: return null

    return Prompt(
        id = "gerado_${System.currentTimeMillis()}",
        titulo = origem.titulo,
        categoria = origem.categoria,
        subcaso = origem.subcaso,
        descricaoCurta = origem.descricaoCurta,
        objetivo = origem.objetivo,
        nivel = origem.nivel,
        melhorPara = sessao.iaDestino?.let { listOf(it) } ?: origem.melhorPara,
        template = promptFinal,
        variaveis = emptyList(),
        tags = origem.tags,
        dataCriacao = FORMATO_DATA.format(Date())
    )
}
