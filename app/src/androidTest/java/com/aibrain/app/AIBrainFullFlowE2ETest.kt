package com.aibrain.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.app.Instrumentation.ActivityResult
import android.util.Xml
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import org.hamcrest.Matchers.allOf
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.io.PlatformTestStorageRegistry
import com.aibrain.app.brain.BrowserOpenMode
import com.aibrain.app.brain.ContextualPromptGenerator
import com.aibrain.app.brain.IAOpenContract
import com.aibrain.app.brain.IAUrlResolver
import com.aibrain.app.brain.LocalAIRouter
import com.aibrain.app.brain.PrefillCapability
import com.aibrain.app.brain.PromptGenerationSpecBuilder
import com.aibrain.app.brain.RoomCommandResolver
import com.aibrain.app.brain.RoutingStatus
import com.aibrain.app.browser.BrowserActivity
import com.aibrain.app.data.local.AppDatabase
import com.aibrain.app.data.local.ComandoIAEntity
import com.aibrain.app.data.local.IACapabilityEntity
import com.aibrain.app.data.local.toEntity
import com.aibrain.app.repository.AtualizacaoRepository
import com.aibrain.app.repository.CatalogoRepository
import com.aibrain.app.view.AIBrainActivity
import com.aibrain.app.view.CriadorPromptsActivity
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter

/**
 * E2E funcional do fluxo interno do IaBrain, sem login, API ou site externo.
 *
 * Pergunta -> intent -> /implement -> RoomCommandResolver -> LocalAIRouter ->
 * PromptGenerationSpec/ContextualPromptGenerator -> IAOpenContract ->
 * IAUrlResolver -> BrowserActivity -> nova aba -> preservação da aba anterior.
 *
 * O teste não interage com botões de envio de qualquer IA. Como o registry de
 * prefill atual é explicitamente vazio, o caso coberto é OPEN_ONLY: o prompt
 * permanece disponível para revisão/cópia pelo usuário.
 */
@RunWith(AndroidJUnit4::class)
class AIBrainFullFlowE2ETest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase
    private lateinit var iaSelecionada: com.aibrain.app.model.IA

    private val pergunta = "Quero criar um aplicativo Android para organizar minhas tarefas."

    @Before
    fun prepararCenarioLocal() = runBlocking {
        limparEstadoLocal()
        database = AppDatabase.getInstance(context)
        database.clearAllTables()

        val catalogo = CatalogoRepository(context).carregarCatalogo()
        iaSelecionada = catalogo
            .filter { it.site.startsWith("https://") && URI(it.site).host != null }
            .sortedBy { it.id }
            .firstOrNull()
            ?: error("O catálogo local não possui uma IA HTTPS")

        database.iaDao().salvarTodos(listOf(iaSelecionada.toEntity()))

        val resolver = RoomCommandResolver(context, database)
        val request = resolver.resolve(pergunta) ?: error("A pergunta não resolveu para um comando")
        assertEquals("/implement", request.canonicalCommand)

        database.comandoGrafoDao().salvarComandosIA(
            listOf(ComandoIAEntity("implement", iaSelecionada.id, 1, "IA local do catálogo"))
        )
        database.iaCapabilityDao().salvarTodos(
            listOf(IACapabilityEntity(iaSelecionada.id, "CODIGO", especialidade = false, nivel = 1))
        )
    }

    @After
    fun limparCenario() {
        runCatching { capturarTela("99-estado-final-ou-falha.png") }
        if (::database.isInitialized) database.clearAllTables()
        limparEstadoLocal()
    }

    @Test
    fun fluxoCompletoPreparaPromptAbreIAEPreservaAbasSemEnviar() = runBlocking {
        val resolverComando = RoomCommandResolver(context, database)
        val request = resolverComando.resolve(pergunta)
            ?: error("A pergunta não foi reconhecida pelo resolver local")

        assertEquals("/implement", request.canonicalCommand)
        assertTrue(request.rawUserRequest.contains("aplicativo Android"))

        val candidates = resolverComando.candidates()
        val decision = LocalAIRouter.route(request, candidates)
        assertEquals(RoutingStatus.SELECTED, decision.status)
        assertNotNull(decision.selectedAI)
        val selectedAI = requireNotNull(decision.selectedAI)
        assertEquals(iaSelecionada.id, selectedAI.iaId)
        assertEquals("/implement", decision.command)
        assertTrue(decision.reasons.any { it.contains("/implement") })

        val spec = PromptGenerationSpecBuilder.from(request, decision)
        val prompt = ContextualPromptGenerator.generate(spec)
        assertTrue(prompt.contains(pergunta))
        assertTrue(prompt.contains("/implement"))
        assertTrue(prompt.contains(iaSelecionada.nome))

        val contratoInicial = IAOpenContract(
            selectedAIId = selectedAI.iaId,
            selectedAIName = selectedAI.nome,
            officialResolvedUrl = null,
            urlStatus = com.aibrain.app.brain.UrlResolutionStatus.NOT_FOUND,
            generatedPrompt = prompt
        )
        val contratoResolvido = IAUrlResolver(context).resolve(contratoInicial)

        assertEquals(com.aibrain.app.brain.UrlResolutionStatus.RESOLVED, contratoResolvido.urlStatus)
        val resolvedUrl = requireNotNull(contratoResolvido.officialResolvedUrl)
        assertFalse(resolvedUrl.isBlank())
        assertEquals("https", URI(resolvedUrl).scheme)
        assertTrue(!URI(resolvedUrl).host.isNullOrBlank())
        assertEquals(BrowserOpenMode.OPEN_ONLY, contratoResolvido.openMode)
        assertEquals(PrefillCapability.UNKNOWN, contratoResolvido.prefillCapability)
        assertFalse(contratoResolvido.canPrefillPrompt)

        validarBrowserEAbas(contratoResolvido)
        validarFluxoDaTelaBrain()
    }

    private fun validarBrowserEAbas(contrato: IAOpenContract) {
        limparEstadoLocal()
        val intent = BrowserActivity.criarIntent(context, contrato)

        ActivityScenario.launch<BrowserActivity>(intent).use { browserScenario ->
            capturarTela("03-browser-inicial.png")
            onView(isRoot()).perform(aguardarViewVisivel(R.id.recyclerAbasBrowser))
            onView(isRoot()).perform(aguardarQuantidadeDeAbas(1))
            onView(withId(R.id.containerWebViewBrowser)).check(matches(isDisplayed()))
            onView(withId(R.id.txtAbaItem)).check(matches(isDisplayed()))
            capturarTela("03-browser-primeira-aba.png")

            browserScenario.onActivity { activity ->
                assertEquals(contrato.selectedAIName, activity.intent.getStringExtra(BrowserActivity.EXTRA_NOME_IA))
                assertEquals(contrato.officialResolvedUrl, activity.intent.getStringExtra(BrowserActivity.EXTRA_URL))
                assertEquals(contrato.generatedPrompt, activity.intent.getStringExtra(BrowserActivity.EXTRA_PROMPT))
                assertEquals(BrowserOpenMode.OPEN_ONLY.name, activity.intent.getStringExtra(BrowserActivity.EXTRA_OPEN_MODE))
                assertTrue(activity.findViewById<android.view.ViewGroup>(R.id.containerWebViewBrowser).childCount > 0)
            }

            // A segunda aba deve ser criada pela ação de UI do navegador. O
            // startActivity com REORDER_TO_FRONT usado antes não representa a
            // interação do usuário e, com singleTask, podia apenas reordenar a
            // Activity sem entregar um novo Intent observável ao teste.
            onView(withId(R.id.btnNovaAbaItem)).perform(
                androidx.test.espresso.action.ViewActions.click()
            )
            onView(isRoot()).perform(aguardarQuantidadeDeAbas(2))
            capturarTela("04-browser-segunda-aba.png")
            browserScenario.onActivity { activity ->
                val itens = (activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerAbasBrowser)
                    .adapter as com.aibrain.app.browser.BrowserAdapter).currentList
                    .filterIsInstance<com.aibrain.app.browser.BrowserAdapter.ItemBarra.Aba>()
                assertEquals(2, itens.size)
                assertEquals(contrato.selectedAIName, itens.first().aba.nomeIA)
                assertTrue(itens[0].aba.id != itens[1].aba.id)
            }
            onView(withId(R.id.containerWebViewBrowser)).check(matches(isDisplayed()))
        }
    }

    private fun validarFluxoDaTelaBrain() {
        limparEstadoLocal()
        var intentPromptBuilder: Intent? = null
        ActivityScenario.launch(AIBrainActivity::class.java).use {
            capturarTela("00-tela-inicial.png")
            onView(withId(R.id.btnPerguntar)).perform(aguardarHabilitado())
            onView(withId(R.id.editPergunta)).perform(
                androidx.test.espresso.action.ViewActions.replaceText(pergunta)
            )
            onView(withId(R.id.btnPerguntar)).perform(
                androidx.test.espresso.action.ViewActions.click()
            )

            // A pergunta primeiro produz a recomendação. O prompt só é criado
            // depois da ação explícita "Criar prompt"; esperar o container do
            // prompt aqui confundia estado intermediário com falha de navegação.
            onView(isRoot()).perform(aguardarViewVisivel(R.id.containerResultado))
            onView(isRoot()).perform(rolarParaFimDoBrain())
            capturarEvidencia("01-antes-criar-prompt")

            // O clique continua sendo exercitado e o Intent produzido pela
            // aplicação é capturado. A navegação real para outra Activity é
            // aberta em um ActivityScenario separado abaixo: no API 34, a
            // combinação startActivity durante um cenário ativo e a transição
            // top-resumed do emulator-runner pode derrubar o processo com
            // ActivityClientRecord nulo, antes de qualquer asserção da UI.
            Intents.init()
            try {
                Intents.intending(hasComponent(CriadorPromptsActivity::class.java.name))
                    .respondWith(ActivityResult(android.app.Activity.RESULT_OK, null))
                onView(withId(R.id.btnCriarPromptChat)).perform(
                    androidx.test.espresso.action.ViewActions.click()
                )
                intentPromptBuilder = Intents.getIntents()
                    .lastOrNull { it.component?.className == CriadorPromptsActivity::class.java.name }
            } finally {
                Intents.release()
            }
        }

        val intent = requireNotNull(intentPromptBuilder) {
            "O clique em Criar prompt não emitiu Intent para CriadorPromptsActivity"
        }
        ActivityScenario.launch<CriadorPromptsActivity>(intent).use {
            onView(isRoot()).perform(aguardarTextoVisivel(R.id.txtPreviewPrompt, "/implement"))
            onView(withId(R.id.txtTituloCriadorPrompts)).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.txtPreviewPrompt), withText(containsString(pergunta))))
                .check(matches(withText(containsString(pergunta))))
            onView(allOf(withId(R.id.txtPreviewPrompt), withText(containsString(iaSelecionada.nome))))
                .check(matches(withText(containsString(iaSelecionada.nome))))
            capturarEvidencia("02-depois-criar-prompt")
        }
    }

    private fun capturarTela(nome: String) {
        capturarEvidencia(nome.removeSuffix(".png"))
    }

    private fun limparEstadoLocal() {
        context.getSharedPreferences("ai_brain_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("ai_brain_browser_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        AtualizacaoRepository(context).catalogoCacheado()?.let {
            context.deleteFile("ia_catalogo_atualizado.json")
        }
    }

    private fun aguardarViewVisivel(id: Int, timeoutMs: Long = 20_000): ViewAction =
        esperarNoRoot(timeoutMs, "view-${nomeRecurso(id)}") { root ->
            val view = root.findViewById<View>(id)
            view != null && view.visibility == View.VISIBLE && view.isShown
        }

    private fun aguardarTextoVisivel(id: Int, trecho: String, timeoutMs: Long = 20_000): ViewAction =
        esperarNoRoot(timeoutMs, "texto-${nomeRecurso(id)}") { root ->
            val view = root.findViewById<TextView>(id)
            view != null && view.visibility == View.VISIBLE && view.isShown &&
                view.text?.toString()?.contains(trecho) == true
        }

    private fun rolarParaFimDoBrain(): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isRoot()
        override fun getDescription(): String = "rolar até a ação Criar prompt"
        override fun perform(uiController: androidx.test.espresso.UiController, view: View) {
            view.findViewById<androidx.core.widget.NestedScrollView>(R.id.nestedScrollBrain)
                ?.fullScroll(View.FOCUS_DOWN)
            uiController.loopMainThreadForAtLeast(100)
        }
    }

    private fun aguardarHabilitado(timeoutMs: Long = 20_000): ViewAction =
        object : ViewAction {
            override fun getConstraints(): Matcher<View> = isDisplayed()
            override fun getDescription(): String = "aguardar botão habilitado"
            override fun perform(uiController: androidx.test.espresso.UiController, view: View) {
                val limite = android.os.SystemClock.uptimeMillis() + timeoutMs
                while (!view.isEnabled && android.os.SystemClock.uptimeMillis() < limite) {
                    uiController.loopMainThreadForAtLeast(50)
                }
                if (!view.isEnabled) error("Botão não ficou habilitado dentro do prazo")
            }
        }

    private fun aguardarQuantidadeDeAbas(quantidade: Int, timeoutMs: Long = 20_000): ViewAction =
        esperarNoRoot(timeoutMs, "abas-$quantidade") { root ->
            val recycler = root.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerAbasBrowser)
            val adapter = recycler?.adapter as? com.aibrain.app.browser.BrowserAdapter
            adapter?.currentList?.count {
                it is com.aibrain.app.browser.BrowserAdapter.ItemBarra.Aba
            } == quantidade
        }

    private fun esperarNoRoot(
        timeoutMs: Long,
        estado: String,
        condicao: (View) -> Boolean
    ): ViewAction =
        object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription(): String = "aguardar $estado"
            override fun perform(uiController: androidx.test.espresso.UiController, view: View) {
                val limite = android.os.SystemClock.uptimeMillis() + timeoutMs
                while (!condicao(view) && android.os.SystemClock.uptimeMillis() < limite) {
                    uiController.loopMainThreadForAtLeast(50)
                }
                if (!condicao(view)) {
                    capturarDiagnostico("timeout-$estado")
                    error("Estado esperado não apareceu dentro do prazo: $estado")
                }
            }
        }

    private fun nomeRecurso(id: Int): String =
        runCatching { context.resources.getResourceEntryName(id) }.getOrDefault(id.toString())

    private fun capturarDiagnostico(prefixo: String) {
        capturarEvidencia(prefixo)
    }

    /**
     * Captura screenshot e hierarchy do mesmo frame crítico. O dump usa o
     * mesmo UiAutomation já usado para o screenshot; não há um segundo
     * `uiautomator dump` concorrendo com o Espresso no Android 14.
     */
    private fun capturarEvidencia(base: String) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val uiAutomation = instrumentation.uiAutomation
        runCatching {
            val bitmap = uiAutomation.takeScreenshot()
            escreverEvidencia("$base.png") { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            bitmap.recycle()
        }
        runCatching {
            val hierarchy = serializarHierarchy(uiAutomation.rootInActiveWindow)
            escreverEvidencia("$base.xml") { output ->
                output.write(hierarchy.toByteArray(Charsets.UTF_8))
            }
        }
    }

    private fun serializarHierarchy(root: android.view.accessibility.AccessibilityNodeInfo?): String {
        requireNotNull(root) { "UiAutomation não retornou a janela ativa" }
        val writer = StringWriter()
        val serializer = Xml.newSerializer()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "hierarchy")
        serializer.attribute(null, "rotation", "0")
        serializarNo(serializer, root, 0)
        serializer.endTag(null, "hierarchy")
        serializer.endDocument()
        root.recycle()
        return writer.toString()
    }

    private fun serializarNo(
        serializer: org.xmlpull.v1.XmlSerializer,
        node: android.view.accessibility.AccessibilityNodeInfo,
        index: Int
    ) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        serializer.startTag(null, "node")
        serializer.attribute(null, "index", index.toString())
        serializer.attribute(null, "text", node.text?.toString().orEmpty())
        serializer.attribute(null, "resource-id", node.viewIdResourceName.orEmpty())
        serializer.attribute(null, "class", node.className?.toString().orEmpty())
        serializer.attribute(null, "package", node.packageName?.toString().orEmpty())
        serializer.attribute(null, "content-desc", node.contentDescription?.toString().orEmpty())
        serializer.attribute(null, "clickable", node.isClickable.toString())
        serializer.attribute(null, "enabled", node.isEnabled.toString())
        serializer.attribute(null, "focused", node.isFocused.toString())
        serializer.attribute(null, "scrollable", node.isScrollable.toString())
        serializer.attribute(null, "bounds", "[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]")
        for (childIndex in 0 until node.childCount) {
            node.getChild(childIndex)?.let { child ->
                serializarNo(serializer, child, childIndex)
                child.recycle()
            }
        }
        serializer.endTag(null, "node")
    }

    private fun escreverEvidencia(nome: String, escrever: (java.io.OutputStream) -> Unit) {
        val arquivoPrivado = File(context.getDir("e2e-screenshots", Context.MODE_PRIVATE), nome)
        val privadoEscrito = runCatching {
            arquivoPrivado.parentFile?.mkdirs()
            FileOutputStream(arquivoPrivado).use(escrever)
            true
        }.getOrDefault(false)
        if (privadoEscrito) {
            runCatching {
                PlatformTestStorageRegistry.getInstance()
                    .openOutputFile("e2e-evidence/$nome")
                    .use { output -> arquivoPrivado.inputStream().use { it.copyTo(output) } }
            }
            return
        }
        PlatformTestStorageRegistry.getInstance()
            .openOutputFile("e2e-evidence/$nome")
            .use(escrever)
    }
}
