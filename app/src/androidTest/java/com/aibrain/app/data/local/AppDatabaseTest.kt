package com.aibrain.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aibrain.app.brain.ProjetoIntentParser
import com.aibrain.app.brain.RecomendadorProjeto
import com.aibrain.app.model.Categoria
import com.aibrain.app.model.IA
import kotlinx.coroutines.flow.first
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

    @Test fun projetoFuncaoIaEPromptPodemSerRelacionados() = runBlocking {
        db.projetoDao().salvar(ProjetoEntity("p1", "App", "desc", "android", "MEDIA", null, 1, 1, "ATIVO"))
        db.projetoFuncaoDao().salvarTodos(listOf(ProjetoFuncaoEntity("f1", "p1", "CODIGO", "", 0, "ATIVA")))
        db.projetoIADao().salvarTodos(listOf(ProjetoIAEntity("pi1", "p1", "f1", "ia1", 1, "nota", true)))
        db.promptDao().salvar(PromptEntity("pr1", "p1", "f1", "ia1", "Título", "prompt", "LOCAL", "LOCAL", 1, 1, false))
        assertEquals("App", db.projetoDao().buscar("p1")?.nome)
        assertEquals("ia1", db.projetoIADao().buscarEscolhidas("p1", "f1").single().iaId)
        assertTrue(db.promptDao().buscar("pr1") != null)
    }

    @Test fun fluxoRealPersisteProjetoFuncoesEIas() = runBlocking {
        val ia = IA("ia1", "Editor", "", "https://example.com", "Editor", listOf(Categoria.CODIGO.chave), emptyList(), true)
        val recomendacao = RecomendadorProjeto.recomendar(listOf(ia), ProjetoIntentParser.parse("aplicativo de código gratuito"))
        val projetoId = SalvarProjetoCompletoUseCase(db)(recomendacao)
        assertTrue(db.projetoDao().buscar(projetoId) != null)
        assertEquals(recomendacao.funcoes.size, db.projetoFuncaoDao().observarDoProjeto(projetoId).first().size)
        assertTrue(db.projetoIADao().observarDoProjeto(projetoId).first().isNotEmpty())
    }
}
