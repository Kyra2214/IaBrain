package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjetoIntegracaoEngineTest {
    @Test
    fun `classifica novo modificado removido e igual`() {
        val base = listOf(
            ArquivoWorkspace("igual.txt", "a", 1),
            ArquivoWorkspace("mod.txt", "a", 1),
            ArquivoWorkspace("removido.txt", "a", 1)
        )
        val contribuicao = listOf(
            ArquivoWorkspace("igual.txt", "a", 1, "IA"),
            ArquivoWorkspace("mod.txt", "b", 1, "IA"),
            ArquivoWorkspace("novo.txt", "c", 1, "IA")
        )
        val resultado = ProjetoIntegracaoEngine.analisar(base, contribuicao)
        assertEquals(TipoMudanca.IGUAL, resultado.mudancas.first { it.caminho == "igual.txt" }.tipo)
        assertEquals(TipoMudanca.MODIFICADO, resultado.mudancas.first { it.caminho == "mod.txt" }.tipo)
        assertEquals(TipoMudanca.REMOVIDO, resultado.mudancas.first { it.caminho == "removido.txt" }.tipo)
        assertEquals(TipoMudanca.NOVO, resultado.mudancas.first { it.caminho == "novo.txt" }.tipo)
    }

    @Test
    fun `decisao padrao aceita novos e modificados`() {
        val analise = ProjetoIntegracaoEngine.analisar(
            listOf(ArquivoWorkspace("a.txt", "a", 1)),
            listOf(ArquivoWorkspace("a.txt", "b", 1, "IA"), ArquivoWorkspace("b.txt", "c", 1, "IA"))
        )
        val decisoes = ProjetoIntegracaoEngine.decisoesPadrao(analise).associateBy { it.caminho }
        assertEquals(DecisaoIntegracao.ACEITAR_CONTRIBUICAO, decisoes["a.txt"]?.decisao)
        assertEquals(DecisaoIntegracao.ACEITAR_CONTRIBUICAO, decisoes["b.txt"]?.decisao)
    }

    @Test
    fun `removido fica como manter por seguranca`() {
        val analise = ProjetoIntegracaoEngine.analisar(
            listOf(ArquivoWorkspace("a.txt", "a", 1)),
            emptyList()
        )
        val decisoes = ProjetoIntegracaoEngine.decisoesPadrao(analise)
        assertEquals(DecisaoIntegracao.MANTER_ATUAL, decisoes.single().decisao)
    }
}
