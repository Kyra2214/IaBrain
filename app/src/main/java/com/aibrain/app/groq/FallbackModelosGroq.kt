package com.aibrain.app.groq

/**
 * Fase 18.5 — Lista fixa de modelos gratuitos da Groq, em ordem de
 * preferência, com fallback automático: se a chamada com o modelo atual
 * falhar (erro de API, modelo desativado/renomeado), tenta o próximo da
 * lista antes de reportar falha ao usuário — "não fica em uma só" IA.
 *
 * Orquestra [GroqClient] (Fase 18.4), que só sabe fazer UMA chamada para
 * UM modelo; esta camada decide QUAL modelo tentar e QUANDO desistir.
 */

/**
 * Modelos gratuitos da Groq, do mais capaz para o mais leve/disponível.
 * Nomes de modelo são o ponto mais instável da API da Groq (ela os
 * desativa/renomeia com alguma frequência) — por isso a lista existe:
 * se um nome parar de responder, o próximo assume sem exigir atualização
 * do app.
 */
val MODELOS_GROQ_GRATUITOS = listOf(
    "llama-3.3-70b-versatile",
    "llama-3.1-8b-instant",
    "gemma2-9b-it",
    "llama3-70b-8192",
    "llama3-8b-8192"
)

/** Resultado de uma tentativa com fallback: sucesso (com o modelo que respondeu) ou falha final. */
sealed class ResultadoComFallback {
    data class Sucesso(val texto: String, val modeloUsado: String) : ResultadoComFallback()
    data class Falha(val motivo: String) : ResultadoComFallback()
}

/**
 * Erros de configuração (chave inválida/ausente) não dependem do modelo —
 * tentar o próximo da lista é inútil e só atrasa a resposta ao usuário.
 * Reconhecido pelo prefixo de mensagem que [GroqClient.mensagemDeErro]
 * usa para o HTTP 401.
 */
private fun ehErroDeChave(motivo: String): Boolean =
    motivo.contains("API key inválida", ignoreCase = true)

/**
 * Envia [mensagemUsuario] tentando cada modelo de [modelos] em ordem, na
 * própria [cliente], até o primeiro sucesso. Para imediatamente (sem
 * tentar os demais) se o erro for de chave inválida/expirada — nesse
 * caso trocar de modelo não resolve nada. Sem nenhum sucesso, retorna
 * a última falha observada.
 */
fun enviarComFallback(
    cliente: GroqClient,
    mensagemUsuario: String,
    promptSistema: String? = null,
    modelos: List<String> = MODELOS_GROQ_GRATUITOS
): ResultadoComFallback {
    var ultimaFalha = "Nenhum modelo disponível"

    for (modelo in modelos) {
        when (val resultado = cliente.enviarMensagem(modelo, mensagemUsuario, promptSistema)) {
            is GroqClient.Resultado.Sucesso ->
                return ResultadoComFallback.Sucesso(resultado.texto, modelo)
            is GroqClient.Resultado.Falha -> {
                ultimaFalha = resultado.motivo
                if (ehErroDeChave(resultado.motivo)) {
                    return ResultadoComFallback.Falha(resultado.motivo)
                }
                // Modelo indisponível/limite atingido/serviço fora do ar — tenta o próximo.
            }
        }
    }

    return ResultadoComFallback.Falha(ultimaFalha)
}


/** Sistemas Compound da Groq com busca na web nativa para atualização do catálogo. */
val MODELOS_GROQ_COM_BUSCA_WEB = listOf(
    "groq/compound",
    "groq/compound-mini"
)

fun enviarComBuscaNaWeb(
    cliente: GroqClient,
    mensagemUsuario: String,
    promptSistema: String? = null
): ResultadoComFallback = enviarComFallback(
    cliente = cliente,
    mensagemUsuario = mensagemUsuario,
    promptSistema = promptSistema,
    modelos = MODELOS_GROQ_COM_BUSCA_WEB
)
