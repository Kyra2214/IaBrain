package com.aibrain.app.llm

import com.aibrain.app.groq.GroqClient
import com.aibrain.app.groq.PromptGeneratorGroq
import com.aibrain.app.resource.HeavyResource

 data class GenerationOptions(val contextSize: Int = 2048, val temperature: Float = 0.7f, val maxTokens: Int = 512)
 data class ProjetoContextSnapshot(val projetoId: String? = null, val objetivo: String, val stack: List<String> = emptyList(), val memoria: String = "", val decisoes: List<String> = emptyList(), val preferencias: String = "", val estadoAtual: String = "")
 data class PromptContext(val objetivo: String, val iaDestino: String? = null, val funcao: String? = null, val contexto: String? = null, val restricoes: List<String> = emptyList(), val projeto: ProjetoContextSnapshot? = null)

interface LocalLLMProvider {
    suspend fun isAvailable(): Boolean
    suspend fun generate(prompt: String, options: GenerationOptions = GenerationOptions()): String
}

/** Ponte isolada para o runtime GGUF. A implementação de llama.cpp pode ser trocada sem tocar na UI. */
class QwenLocalLLMProvider(private val resource: HeavyResource, private val modelPath: suspend () -> String?, private val runtime: LocalRuntime? = null) : LocalLLMProvider {
    override suspend fun isAvailable(): Boolean = modelPath() != null && runtime != null
    override suspend fun generate(prompt: String, options: GenerationOptions): String {
        val path = modelPath() ?: error("Modelo local indisponível")
        return runtime?.generate(path, prompt, options) ?: error("Runtime local não configurado")
    }
}

interface LocalRuntime { suspend fun generate(modelPath: String, prompt: String, options: GenerationOptions): String }

class GroqLLMProvider(private val client: GroqClient, private val model: String = "llama-3.1-8b-instant") : LocalLLMProvider {
    override suspend fun isAvailable() = true
    override suspend fun generate(prompt: String, options: GenerationOptions): String = when (val result = client.enviarMensagem(model, prompt, PromptGeneratorGroq.construirPromptSistema())) {
        is GroqClient.Resultado.Sucesso -> result.texto
        is GroqClient.Resultado.Falha -> error(result.motivo)
    }
}

class PromptGenerator(private val local: LocalLLMProvider?, private val groq: LocalLLMProvider?) {
    suspend fun generate(context: PromptContext): String {
        val prompt = buildString {
            appendLine("Objetivo: ${context.objetivo}")
            context.iaDestino?.let { appendLine("IA destino: $it") }
            context.funcao?.let { appendLine("Função: $it") }
            context.contexto?.let { appendLine("Contexto: $it") }
            context.projeto?.let {
                appendLine("Projeto: ${it.objetivo}")
                if (it.stack.isNotEmpty()) appendLine("Stack: ${it.stack.joinToString()}")
                if (it.memoria.isNotBlank()) appendLine("Memória: ${it.memoria}")
                if (it.decisoes.isNotEmpty()) appendLine("Decisões anteriores: ${it.decisoes.joinToString()}")
                if (it.preferencias.isNotBlank()) appendLine("Preferências: ${it.preferencias}")
                if (it.estadoAtual.isNotBlank()) appendLine("Estado: ${it.estadoAtual}")
            }
            if (context.restricoes.isNotEmpty()) appendLine("Restrições: ${context.restricoes.joinToString()}")
            appendLine("Retorne somente um prompt final estruturado, em português brasileiro.")
        }
        val localProvider = local
        if (localProvider != null && localProvider.isAvailable()) return runCatching { localProvider.generate(prompt) }.getOrElse { groq?.generate(prompt) ?: throw it }
        return groq?.generate(prompt) ?: error("Nenhum provedor de geração disponível")
    }
}
