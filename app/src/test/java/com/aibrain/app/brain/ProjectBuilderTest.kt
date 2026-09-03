package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectBuilderTest {
    @Test fun criaPlanoComDependenciasOrdenadas() {
        val plan = ProjectBuilder.build("Criar aplicativo financeiro")
        assertEquals("Criar aplicativo financeiro", plan.objective)
        assertEquals(listOf("analysis", "architecture", "implementation", "validation"), plan.functions.map { it.id })
        assertEquals(listOf("analysis"), plan.functions[1].dependencies)
        assertEquals(listOf("architecture"), plan.functions[2].dependencies)
        assertEquals(listOf("implementation"), plan.functions[3].dependencies)
    }

    @Test fun rejeitaObjetivoVazio() {
        try {
            ProjectBuilder.build("   ")
            assertTrue("deveria rejeitar objetivo vazio", false)
        } catch (e: IllegalArgumentException) {
            assertEquals("Objetivo do projeto não pode ser vazio", e.message)
        }
    }
}
