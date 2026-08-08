package com.aibrain.app.groq

import com.aibrain.app.model.Categoria
import com.aibrain.app.model.IA

/**
 * Prompt otimizado para atualização automática de IAs sem causar estouro de payload (HTTP 413).
 */
object PromptAtualizacaoCatalogoIA {

    fun construir(categorias: List<Categoria>, catalogoAtual: List<IA>): String {
        val categoriasPermitidas = categorias.joinToString(", ") { "${it.chave} (${it.rotulo})" }
        // Passamos apenas os nomes principais em vez de todas as URLs para poupar largura de banda e evitar erro 413
        val nomesExistentes = catalogoAtual.map { it.nome }.take(30).joinToString(", ")

        return """
            Você é o curador automático do catálogo AI Brain. Faça uma varredura na web agora,
            usando busca na web, procurando IAs, agentes, modelos, plataformas, ferramentas e lançamentos
            recentes (como Manus, novos modelos da OpenAI, Anthropic, Google, etc.). Priorize fontes oficiais.

            Evite duplicar itens já conhecidos nesta lista resumida: $nomesExistentes.

            Classifique cada item somente nas categorias permitidas abaixo:
            $categoriasPermitidas

            Regras:
            - Inclua de 1 a 10 novidades relevantes ou IAs de destaque recentes.
            - O site deve ser a URL oficial HTTPS do produto ou projeto.
            - A descrição deve ser curta, factual e em português.
            - Use idiomas como códigos ISO curtos, por exemplo pt, en, es, fr, de, ja ou zh.
            - "gratuita" deve ser true quando houver uso gratuito, inclusive freemium.
            - "acesso" deve ser exatamente "gratuita", "freemium" ou "paga".
            - Em "notas", use somente categorias escolhidas e notas inteiras de 0 a 10.
            - "categoriaPrincipal" deve ser uma das categorias escolhidas.

            Responda APENAS com um array JSON válido, sem markdown, comentários ou texto fora do JSON, seguindo este formato:
            [
              {
                "nome": "Nome oficial",
                "site": "https://exemplo.com",
                "descricao": "Descrição factual curta.",
                "categorias": ["conversa"],
                "idiomas": ["en", "pt"],
                "gratuita": true,
                "acesso": "freemium",
                "notas": {"conversa": 9},
                "categoriaPrincipal": "conversa"
              }
            ]
            Se não encontrar novidades, responda apenas com [].
        """.trimIndent()
    }
}
