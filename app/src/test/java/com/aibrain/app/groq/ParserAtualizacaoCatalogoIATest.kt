package com.aibrain.app.groq

import com.aibrain.app.model.IA
import com.aibrain.app.model.NivelAcesso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserAtualizacaoCatalogoIATest {

    private val atual = IA(
        id = "existente",
        nome = "IA Existente",
        logo = "https://www.google.com/s2/favicons?domain=existente.com&sz=128",
        site = "https://existente.com",
        descricao = "Já cadastrada",
        categorias = listOf("codigo"),
        idiomas = listOf("en"),
        gratuita = true,
        acesso = NivelAcesso.GRATUITA,
        notas = mapOf("codigo" to 8),
        categoriaPrincipal = "codigo"
    )

    @Test
    fun `aceita novidade válida e normaliza categorias`() {
        val resposta = """
            ```json
            [{
              "nome":"Nova IA",
              "site":"https://novaia.example.com",
              "descricao":"Ferramenta para programação.",
              "categorias":["codigo", "categoria-inexistente"],
              "idiomas":["en", "pt"],
              "gratuita":true,
              "acesso":"freemium",
              "notas":{"codigo":9},
              "categoriaPrincipal":"codigo"
            }]
            ```
        """.trimIndent()

        val resultado = ParserAtualizacaoCatalogoIA.parsear(resposta, listOf(atual))

        assertEquals(1, resultado.novasIas.size)
        assertEquals(listOf("codigo"), resultado.novasIas.single().categorias)
        assertEquals(NivelAcesso.FREEMIUM, resultado.novasIas.single().acesso)
    }

    @Test
    fun `descarta item duplicado e item sem site oficial https`() {
        val resposta = """
            [
              {"nome":"IA Existente","site":"https://existente.com","descricao":"x","categorias":["codigo"],"idiomas":["en"],"gratuita":true,"acesso":"gratuita","notas":{"codigo":8}},
              {"nome":"IA Insegura","site":"http://insegura.com","descricao":"x","categorias":["codigo"],"idiomas":["en"],"gratuita":true,"acesso":"gratuita","notas":{"codigo":8}}
            ]
        """.trimIndent()

        val resultado = ParserAtualizacaoCatalogoIA.parsear(resposta, listOf(atual))

        assertTrue(resultado.novasIas.isEmpty())
        assertEquals(2, resultado.ignoradas)
    }
}
