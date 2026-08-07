package com.aibrain.app.model

/**
 * Mensagem do histórico da sessão do Criador de Prompts (Fase 17.2).
 * Mantida em memória, escopo da própria [com.aibrain.app.view.CriadorPromptsActivity]
 * (não persiste entre sessões, diferente de Favoritos/Histórico da Fase 16.15/16.16).
 */
data class MensagemChat(
    val texto: String,
    val deUsuario: Boolean
)
