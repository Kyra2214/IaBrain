package com.aibrain.app.groq

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fase 18.4 — Cliente HTTP para a API de chat completions da Groq
 * (`HttpURLConnection` nativo, mesmo padrão sem dependências extras já
 * usado em [com.aibrain.app.repository.AtualizacaoRepository]/
 * [com.aibrain.app.repository.CatalogoRepository]).
 *
 * Só monta e envia UMA chamada para UM modelo específico — a lista de
 * modelos gratuitos e o fallback automático entre eles (tentar o próximo
 * se o atual falhar) é responsabilidade da Fase 18.5, que vai chamar
 * [GroqClient.enviarMensagem] em sequência até obter sucesso.
 *
 * Usado exclusivamente pelo Assistente de IA (Fase 18) para curadoria de
 * novas IAs — nunca pelo [com.aibrain.app.brain.RecomendadorIA] offline
 * (Fase 9), que não depende de rede nem de chave.
 */
class GroqClient(private val apiKey: String) {

    /**
     * Resultado de uma chamada à Groq: sucesso com o texto da resposta,
     * ou falha com uma descrição curta do motivo (nunca lança exceção
     * para quem chama — Fase 18.5 decide o que fazer com a falha,
     * tipicamente tentar o próximo modelo da lista).
     */
    sealed class Resultado {
        data class Sucesso(val texto: String) : Resultado()
        data class Falha(val motivo: String) : Resultado()
    }

    /**
     * Envia [mensagemUsuario] (mais um [promptSistema] opcional) para o
     * [modelo] informado e retorna o texto da resposta. Timeout curto
     * (mesmo valor usado em [com.aibrain.app.repository.AtualizacaoRepository]),
     * já que é uma chamada interativa disparada pelo curador, não uma
     * sincronização em segundo plano.
     */
    fun enviarMensagem(modelo: String, mensagemUsuario: String, promptSistema: String? = null): Resultado {
        return try {
            val corpo = montarCorpoRequisicao(modelo, mensagemUsuario, promptSistema)
            val resposta = executarRequisicao(corpo)
            extrairTexto(resposta)
        } catch (e: IOException) {
            Resultado.Falha(e.message ?: "Falha de rede")
        } catch (e: Exception) {
            Resultado.Falha(e.message ?: "Falha inesperada")
        }
    }

    private fun montarCorpoRequisicao(modelo: String, mensagemUsuario: String, promptSistema: String?): String {
        val mensagens = JSONArray()
        if (!promptSistema.isNullOrBlank()) {
            mensagens.put(JSONObject().put("role", "system").put("content", promptSistema))
        }
        mensagens.put(JSONObject().put("role", "user").put("content", mensagemUsuario))

        return JSONObject()
            .put("model", modelo)
            .put("messages", mensagens)
            .toString()
    }

    private fun executarRequisicao(corpoJson: String): String {
        val conexao = (URL(URL_CHAT_COMPLETIONS).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            conexao.outputStream.use { it.write(corpoJson.toByteArray(Charsets.UTF_8)) }

            val codigo = conexao.responseCode
            if (codigo != HttpURLConnection.HTTP_OK) {
                val corpoErro = conexao.errorStream?.bufferedReader()?.use { it.readText() }
                throw IllegalStateException(mensagemDeErro(codigo, corpoErro))
            }
            return conexao.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conexao.disconnect()
        }
    }

    /** Mensagem curta de erro a partir do código HTTP, sem expor detalhes internos da API. */
    private fun mensagemDeErro(codigo: Int, corpoErro: String?): String = when (codigo) {
        401 -> "API key inválida ou expirada"
        404 -> "Modelo indisponível"
        429 -> "Limite de uso atingido"
        in 500..599 -> "Serviço da Groq indisponível no momento"
        else -> "Erro HTTP $codigo${corpoErro?.let { " — $it" } ?: ""}"
    }

    private fun extrairTexto(respostaJson: String): Resultado {
        return try {
            val texto = JSONObject(respostaJson)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            Resultado.Sucesso(texto)
        } catch (e: Exception) {
            Resultado.Falha("Resposta da Groq em formato inesperado")
        }
    }

    companion object {
        private const val URL_CHAT_COMPLETIONS = "https://api.groq.com/openai/v1/chat/completions"
        private const val TIMEOUT_MS = 15000
    }
}
