package com.aibrain.app.view

import android.content.Context
import android.content.Intent
import com.aibrain.app.brain.SessaoConstrutorPrompt
import com.aibrain.app.brain.criarPromptGeradoASalvar

/**
 * Fase 17.16 — Favoritar / Copiar / Editar o prompt gerado ANTES de usar,
 * reaproveitando integralmente [DetalhePromptActivity]: favoritar (Fase 16.15),
 * copiar (16.6) e editar o campo do template antes de copiar/compartilhar
 * (16.7) já funcionam para qualquer [com.aibrain.app.model.Prompt], sem
 * nenhuma alteração nessa tela — só era preciso o caminho de navegação a
 * partir de uma sessão concluída do Prompt Builder (Fase 17.6-17.15).
 */

/**
 * Monta o [Intent] para abrir [DetalhePromptActivity] com o prompt gerado
 * pela [sessao] (Fase 17.15), de onde o usuário pode favoritar, editar o
 * texto e copiar/compartilhar antes de efetivamente usar o prompt em outra
 * IA. Retorna null se a sessão ainda não tem um prompt gerado (Fase 17.15).
 */
fun intentParaDetalhePromptGerado(context: Context, sessao: SessaoConstrutorPrompt): Intent? {
    val promptGerado = criarPromptGeradoASalvar(sessao) ?: return null
    return DetalhePromptActivity.criarIntent(context, promptGerado)
}
