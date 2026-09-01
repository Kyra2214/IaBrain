package com.aibrain.app.repository

import com.aibrain.app.model.Colecao
import com.aibrain.app.model.Guia

/** Conteúdo editorial inicial, independente do código do Latentbox. */
class ColecaoRepository {
    fun carregarColecoes(): List<Colecao> = listOf(
        Colecao("ia-criacao", "Criação com IA", "Ferramentas para imagem, vídeo, áudio e design.", "criação", listOf("imagem", "vídeo", "música", "design")),
        Colecao("ia-programacao", "Programação", "Assistentes, pesquisa técnica e produtividade para desenvolvedores.", "desenvolvimento", listOf("programação", "pesquisa", "produtividade")),
        Colecao("aprendizado", "Aprendizado", "Recursos para estudar, resumir e transformar informação em prática.", "educação", listOf("estudos", "pesquisa", "escrita")),
        Colecao("open-source", "Open Source e pesquisa", "Modelos, datasets, papers e projetos abertos para explorar.", "pesquisa", listOf("open source", "datasets", "papers"))
    )

    fun carregarGuias(): List<Guia> = listOf(
        Guia("guia-video", "Criar um vídeo com IA", "Um fluxo curto do roteiro à publicação.", listOf("Defina o objetivo e escreva o roteiro.", "Gere imagens ou cenas de referência.", "Crie voz, trilha e animações.", "Edite, revise direitos e exporte."), listOf("ChatGPT", "Runway", "ElevenLabs", "Suno")),
        Guia("guia-estudo", "Estudar com IA", "Use IA para compreender, praticar e revisar sem substituir o aprendizado.", listOf("Colete fontes confiáveis.", "Peça uma explicação no seu nível.", "Gere perguntas e flashcards.", "Revise as respostas e confira as fontes."), listOf("Perplexity", "Claude", "ChatGPT")),
        Guia("guia-prompt", "Criar um prompt melhor", "Transforme uma intenção vaga em uma instrução testável.", listOf("Defina o resultado esperado.", "Inclua contexto e público.", "Especifique formato, limites e exemplos.", "Teste, compare e salve a melhor versão."), listOf("Criador de Prompts", "AI Brain"))
    )
}
