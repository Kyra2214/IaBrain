package com.aibrain.app.groq

/**
 * Fase 25 — geração de prompts por IA no Criador de Prompts.
 *
 * Reaproveita [GroqClient] e o fallback automático de modelos gratuitos
 * ([enviarComFallback], Fase 18.5) já usados pela curadoria do Assistente
 * de IA: o usuário descreve o que precisa em texto livre e a Groq devolve
 * um prompt bem estruturado, pronto para ser usado na IA de destino ou
 * salvo na Biblioteca.
 *
 * A API key vem do mesmo armazenamento da Fase 18.1
 * ([com.aibrain.app.data.AssistenteIARepository]) — o usuário configura
 * a chave uma única vez no app e ela serve tanto à curadoria quanto à
 * geração de prompts.
 */
object PromptGeneratorGroq {

    /**
     * Prompt de sistema fixo que orienta a Groq a produzir um prompt
     * profissional: objetivo claro, contexto, papel/voz, formato de saída
     * e restrições. A resposta vem como texto corrido do prompt gerado,
     * sem rodeios nem explicações fora dele.
     */
    fun construirPromptSistema(contextoExtra: String? = null): String = buildString {
        appendLine("Você é um especialista em engenharia de prompts (prompt engineering).")
        appendLine("Receberá uma descrição livre do que o usuário precisa e deve gerar UM prompt completo e pronto para uso, escrito em português brasileiro, que o usuário possa copiar e colar diretamente em uma IA (ChatGPT, Claude, Gemini etc.).")
        appendLine("O prompt gerado deve conter:")
        appendLine("1. Papel/função da IA (ex: 'Aja como um redator profissional de...');")
        appendLine("2. Objetivo claro e específico do que a IA deve produzir;")
        appendLine("3. Contexto necessário para a tarefa (extraia da descrição do usuário; se faltar, invente um contexto genérico razoável);")
        appendLine("4. Formato de saída desejado (ex: lista, passo a passo, tabela, texto corrido);")
        appendLine("5. Restrições e dicas de qualidade (ex: tom, tamanho, o que evitar).")
        appendLine("Regras de resposta: devolva APENAS o prompt final, sem introduções, sem títulos em markdown, sem explicações e sem aspas extras ao redor. Se a descrição do usuário for ambígua, escolha a interpretação mais útil e comum.")
        if (!contextoExtra.isNullOrBlank()) {
            appendLine("Contexto adicional informado pelo usuário: $contextoExtra")
        }
    }
}
