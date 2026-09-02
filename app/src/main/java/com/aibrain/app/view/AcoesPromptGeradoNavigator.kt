package com.aibrain.app.view

import android.content.Context
import android.content.Intent
import com.aibrain.app.brain.IaBrainWorkspaceOrchestrator
import com.aibrain.app.brain.PromptActionSet
import com.aibrain.app.brain.SessaoConstrutorPrompt
import com.aibrain.app.brain.criarPromptGeradoASalvar

/**
 * Ponto único das ações de um prompt gerado.
 * Chat, Brain e Prompt Builder podem reutilizar o mesmo contrato de ações.
 * A abertura de uma IA continua sendo responsabilidade de IAOpenContract +
 * IAUrlResolver + BrowserActivity; este navigator não envia nada.
 */
fun acoesDoPromptGerado(prompt: String, iaId: String?, iaNome: String?): PromptActionSet =
    IaBrainWorkspaceOrchestrator.promptActions(prompt, iaId, iaNome)

/**
 * Monta o Intent para abrir o detalhe do prompt gerado, preservando o fluxo
 * existente de favoritar, editar e copiar/compartilhar.
 */
fun intentParaDetalhePromptGerado(context: Context, sessao: SessaoConstrutorPrompt): Intent? {
    val promptGerado = criarPromptGeradoASalvar(sessao) ?: return null
    return DetalhePromptActivity.criarIntent(context, promptGerado)
}
