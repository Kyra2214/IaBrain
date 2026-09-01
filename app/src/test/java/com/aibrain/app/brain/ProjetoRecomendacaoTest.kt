package com.aibrain.app.brain

import com.aibrain.app.model.IA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjetoRecomendacaoTest {
    @Test fun `parser identifica aplicativo android e complexidade`() {
        val intent = ProjetoIntentParser.parse("Quero criar um aplicativo Android simples de finanças pessoais")
        assertEquals("Aplicativo", intent.tipoProjeto)
        assertEquals("android", intent.plataforma)
        assertEquals(Complexidade.BAIXA, intent.complexidade)
        assertTrue(intent.areas.isNotEmpty())
    }

    @Test fun `orcamento zero exclui IA paga`() {
        val paga = ia("paga", false, 10)
        val gratuita = ia("gratis", true, 7)
        val resultado = listOf(paga, gratuita).recomendarProjeto("criar aplicativo de código gratuito orçamento 0")
        assertTrue(resultado.recomendacoes.all { it.ia?.gratuita != false })
    }

    @Test fun `recomendacao aponta somente para itens existentes`() {
        val resultado = listOf(ia("real", true, 9)).recomendarProjeto("criar aplicativo de código")
        assertTrue(resultado.stack.itens.all { it.id == "real" })
    }

    private fun ia(id: String, gratuita: Boolean, nota: Int) = IA(
        id = id, nome = id, logo = "", site = "https://example.com/$id",
        descricao = "teste", categorias = listOf("codigo"), gratuita = gratuita,
        notas = mapOf("codigo" to nota), categoriaPrincipal = "codigo"
    )
}
