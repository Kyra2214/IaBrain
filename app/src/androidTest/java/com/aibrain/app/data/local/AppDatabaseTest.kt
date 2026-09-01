package com.aibrain.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase
    @Before fun abrir() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun fechar() = db.close()

    @Test fun `projeto funcao ia e prompt podem ser relacionados`() = runBlocking {
        db.projetoDao().salvar(ProjetoEntity("p1", "App", "desc", "android", "MEDIA", null, 1, 1, "ATIVO"))
        db.projetoFuncaoDao().salvarTodos(listOf(ProjetoFuncaoEntity("f1", "p1", "CODIGO", "", 0, "ATIVA")))
        db.projetoIADao().salvarTodos(listOf(ProjetoIAEntity("pi1", "p1", "f1", "ia1", 1, "nota", true)))
        db.promptDao().salvar(PromptEntity("pr1", "p1", "f1", "ia1", "Título", "prompt", "LOCAL", "LOCAL", 1, 1, false))
        assertEquals("App", db.projetoDao().buscar("p1")?.nome)
        assertEquals("ia1", db.projetoIADao().buscarEscolhidas("p1", "f1").single().iaId)
        assertTrue(db.promptDao().buscar("pr1") != null)
    }
}
