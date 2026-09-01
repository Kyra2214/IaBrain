package com.aibrain.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aibrain.app.brain.ProjetoIntentParser
import com.aibrain.app.brain.RecomendadorProjeto
import com.aibrain.app.brain.LocalAIRouter
import com.aibrain.app.brain.RoomCommandResolver
import com.aibrain.app.brain.RoutingStatus
import com.aibrain.app.brain.PromptGenerationSpec
import com.aibrain.app.brain.toEntity
import com.aibrain.app.model.Categoria
import com.aibrain.app.model.IA
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

    @Test fun e2eParserRoomGrafoERouterProduzDecisionSemExecutarProvider() = runBlocking {
        val agora = System.currentTimeMillis()
        db.iaDao().salvarTodos(listOf(
            IA("ia-generica", "IA Genérica", "", "https://generic.example", "", listOf("PESQUISA"), emptyList(), true).toEntity(),
            IA("ia-pesquisa", "IA Pesquisa", "", "https://research.example", "", listOf("PESQUISA"), emptyList(), true).toEntity()
        ))
        db.comandoDao().inserirTodos(listOf(ComandoEntity("research", "research", "Pesquisa", "/research", "Pesquisa", "Pesquisa verificável", "Busca evidências", "Encontrar fontes", "Quando precisar pesquisar", "Quando não houver pesquisa", "/research [tema]", "/research Android", emptyList(), "IA de pesquisa", "PROMPT", true, false, false, false, "INTERMEDIARIO", true, false, 0, agora, agora)))
        db.comandoGrafoDao().salvarCapacidades(listOf(ComandoCapacidadeEntity("research", "PESQUISA", true, 1)))
        db.comandoGrafoDao().salvarComandosIA(listOf(ComandoIAEntity("research", "ia-pesquisa", 1, "suporta pesquisa")))
        db.iaRoutingProfileDao().upsert(IARoutingProfileEntity("profile-research", "ia-pesquisa", .9, .7, .2, .8, .9, true, agora))
        val resolver = RoomCommandResolver(ApplicationProvider.getApplicationContext(), db)
        val request = resolver.resolve("/research tema=\"Android offline\"")!!
        assertEquals("/research", request.canonicalCommand)
        assertEquals("Android offline", request.namedParameters["tema"])
        val candidatos = resolver.candidates()
        assertEquals(2, candidatos.size)
        val decision = LocalAIRouter.route(request, candidatos)
        assertEquals(RoutingStatus.SELECTED, decision.status)
        assertEquals("ia-pesquisa", decision.selectedAI?.iaId)
        assertEquals(.9, decision.selectedAI?.quality ?: 0.0, 0.0)
        assertFalse(decision.selectedAI?.isDefaultProfile ?: true)
        assertTrue(decision.alternatives.any { it.candidate.iaId == "ia-generica" })
        assertTrue(decision.score!!.commandCompatibility > 0.0)
        assertTrue(decision.score != null && decision.reasons.isNotEmpty())
    }

    @Test fun perfilRoutingUpsertAtualizaListaAtivosERejeitaScoreInvalido() = runBlocking {
        val dao = db.iaRoutingProfileDao(); val agora = System.currentTimeMillis()
        val base = IARoutingProfileEntity("p1", "ia1", .9, .4, .2, .8, .7, true, agora)
        dao.upsert(base); dao.upsert(base.copy(qualityScore = .95))
        assertEquals(.95, dao.buscarPorIA("ia1")!!.qualityScore, 0.0)
        assertEquals(1, dao.listarAtivos().size)
        dao.upsert(base.copy(enabled = false)); assertFalse(dao.listarAtivos().any { it.iaId == "ia1" })
        try { IARoutingProfileEntity("bad", "ia1", 1.1, .5, 0.0, .5, .5, true, agora); throw AssertionError("score inválido aceito") } catch (_: IllegalArgumentException) { }
    }

    @Test fun registryMarcaPerfilAusenteComoDefault() = runBlocking {
        db.iaDao().salvarTodos(listOf(IA("sem-perfil", "Sem Perfil", "", "", "", listOf("PESQUISA"), emptyList(), true).toEntity()))
        val candidatos = com.aibrain.app.brain.IACapabilityRegistry(ApplicationProvider.getApplicationContext(), db).candidates()
        assertEquals(1, candidatos.size); assertTrue(candidatos.single().isDefaultProfile); assertEquals(.5, candidatos.single().quality, 0.0)
    }

    @Test fun promptGeradoPersisteIAComandoEOrigem() = runBlocking {
        val spec = PromptGenerationSpec("Pesquisar Android offline", "ia-pesquisa", "IA Pesquisa", "/research", setOf("PESQUISA"), funcaoId = "f1")
        val entity = spec.toEntity("Prompt contextual")
        db.promptDao().salvar(entity)
        val salvo = db.promptDao().buscar(entity.id)!!
        assertEquals("ia-pesquisa", salvo.iaId); assertTrue(salvo.origem.contains("/research")); assertEquals("Prompt contextual", salvo.prompt)
    }
}
