package com.aibrain.app.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColecaoRepositoryTest {
    private val repository = ColecaoRepository()

    @Test
    fun colecoesDevemTerIdentificadoresEConteudo() {
        val colecoes = repository.carregarColecoes()
        assertFalse(colecoes.isEmpty())
        assertTrue(colecoes.all { it.id.isNotBlank() && it.titulo.isNotBlank() && it.itens.isNotEmpty() })
        assertTrue(colecoes.map { it.id }.distinct().size == colecoes.size)
    }

    @Test
    fun guiasDevemTerPassosEFerramentas() {
        val guias = repository.carregarGuias()
        assertFalse(guias.isEmpty())
        assertTrue(guias.all { it.id.isNotBlank() && it.passos.size >= 3 && it.ferramentas.isNotEmpty() })
        assertTrue(guias.map { it.id }.distinct().size == guias.size)
    }
}
