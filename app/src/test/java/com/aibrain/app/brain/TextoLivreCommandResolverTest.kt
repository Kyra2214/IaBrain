package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextoLivreCommandResolverTest {
    @Test fun criaAplicativoSelecionaImplementacao() = assertEquals("/implement", TextoLivreIntent.commandFor("Quero criar um aplicativo de análise de vendas"))
    @Test fun pesquisaSelecionaResearch() = assertEquals("/research", TextoLivreIntent.commandFor("pesquisar fontes sobre Android offline"))
    @Test fun pedidoAmbiguoNaoInventaComando() = assertNull(TextoLivreIntent.commandFor("Olá, tudo bem?"))
}
