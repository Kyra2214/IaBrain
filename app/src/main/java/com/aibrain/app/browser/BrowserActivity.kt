package com.aibrain.app.browser

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aibrain.app.cache.ImagemCache
import com.aibrain.app.brain.IAOpenContract
import com.aibrain.app.brain.BrowserOpenMode
import com.aibrain.app.brain.PrefillCapability
import com.aibrain.app.brain.PrefillAdapterRegistry
import com.aibrain.app.databinding.ActivityBrowserBinding
import com.aibrain.app.navigation.GlobalNavigation
import com.google.android.material.snackbar.Snackbar

/**
 * Fase 21 — Módulo Browser (Navegador Interno com Abas).
 * Resolve o Bug 4 da Fase 19 (navegador externo desfocado): o AI Brain nunca
 * deve levar o usuário para fora do app ao abrir uma IA.
 *
 * Fase 21.8 — pontos de entrada existentes ("Abrir IA" da Fase 5.2/17.17,
 * ver [com.aibrain.app.view.DetalheIAActivity] e
 * [com.aibrain.app.brain.abrirIARecomendadaNoNavegador]) passam a abrir esta
 * Activity em vez de Custom Tabs. `launchMode="singleTask"` (ver
 * AndroidManifest) garante uma única instância: se já houver abas abertas,
 * o Intent chega em [onNewIntent] e cria uma **nova aba** via
 * [BrowserTabManager] em vez de substituir a atual — nenhuma aba anterior é
 * fechada. A barra de abas (Fase 21.6/[BrowserAdapter]) passa a ficar
 * integrada à tela, trocando qual [WebView] do [BrowserTabManager] é exibido
 * no container (Fase 21.7 — sem recriar nem recarregar).
 *
 * Fase 21.10 — `onStop()` salva a sessão de abas via [BrowserHistoryManager]
 * sempre que a tela sai de primeiro plano (background ou fechamento do
 * app).
 *
 * Fase 21.11 — `onCreate()` chama [restaurarSessaoSalva] antes de tratar o
 * Intent atual: se havia uma sessão salva (processo recriado do zero, sem
 * nenhuma aba em memória), recria as abas via
 * [BrowserTabManager.restaurarAbas] e recarrega a URL de cada uma; a aba do
 * Intent atual (se houver) é aberta em seguida, por cima da sessão
 * restaurada, do mesmo jeito que uma nova aba é sempre aberta por cima das
 * existentes (Fase 21.8).
 *
 * Fase 21.12 — `onTrimMemory()` descarta o WebView das abas inativas em
 * memória baixa; `selecionarAba()`/o menu de aba recriam sob demanda via
 * [BrowserTabManager.garantirWebView], reaplicando `configurarWebView()`.
 *
 * Fase 21.13 — o descarte de `onTrimMemory()` poupa abas fixadas (pin) no
 * nível `RUNNING_LOW`, só as incluindo em `RUNNING_CRITICAL`.
 *
 * Fase 21.15 — `WebViewClient.onReceivedError`/`onReceivedHttpError` (frame
 * principal) disparam [avisarFalhaCarregamento]: Snackbar com ação "Abrir no
 * navegador externo" para a URL que falhou — nunca trava o app nem a aba.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private lateinit var tabManager: BrowserTabManager
    private lateinit var adapter: BrowserAdapter
	private lateinit var historyManager: BrowserHistoryManager
	private val prefillTentado = mutableSetOf<String>()

    private var callbackUploadArquivo: ValueCallback<Array<Uri>>? = null
    private val launcherUploadArquivo: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val resultado = if (uri != null) arrayOf(uri) else null
            callbackUploadArquivo?.onReceiveValue(resultado)
            callbackUploadArquivo = null
        }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_NOME_IA = "extra_nome_ia"
        const val EXTRA_ICONE_IA = "extra_icone_ia"
        const val EXTRA_PROMPT = "extra_prompt"
        const val EXTRA_PREFILL_CAPABILITY = "extra_prefill_capability"
        const val EXTRA_OPEN_MODE = "extra_open_mode"

        /** Helper — nova aba com a IA informada, reaproveitando a instância singleTask já aberta. */
        fun criarIntent(context: android.content.Context, nomeIA: String, url: String, iconeIA: String): Intent {
            return Intent(context, BrowserActivity::class.java).apply {
                putExtra(EXTRA_NOME_IA, nomeIA)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_ICONE_IA, iconeIA)
            }
        }

        fun criarIntent(context: android.content.Context, contract: IAOpenContract): Intent {
            return Intent(context, BrowserActivity::class.java).apply {
                putExtra(EXTRA_NOME_IA, contract.selectedAIName)
                putExtra(EXTRA_URL, contract.officialResolvedUrl)
                putExtra(EXTRA_PROMPT, contract.generatedPrompt)
                putExtra(EXTRA_PREFILL_CAPABILITY, contract.prefillCapability.name)
                putExtra(EXTRA_OPEN_MODE, contract.openMode.name)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        GlobalNavigation.attach(this, binding.root, GlobalNavigation.NAVEGADOR)

        tabManager = BrowserTabManager(applicationContext)
        historyManager = BrowserHistoryManager(applicationContext)
        adapter = BrowserAdapter(
            escopo = lifecycleScope,
            imagemCache = ImagemCache(applicationContext),
            aoSelecionarAba = { aba -> selecionarAba(aba.id) },
            aoFecharAba = { aba -> fecharAba(aba.id) },
            aoNovaAba = { criarNovaAbaManual() },
            aoFixarAba = { aba ->
                tabManager.alternarFixada(aba.id)
                adapter.atualizar(tabManager.obterAbas(), tabManager.idAbaAtiva())
            },
            aoAtualizarAba = { aba -> tabManager.atualizarWebViewDaAba(aba.id) { webView -> configurarWebView(webView, aba.id) } },
            aoAbrirPaginaInicialAba = { aba -> tabManager.abrirPaginaInicial(aba.id) { webView -> configurarWebView(webView, aba.id) } },
            // Fase 24 — barra superior removida; compartilhar/abrir externo ficam no menu da aba.
            aoCompartilharAba = { _ -> compartilharPaginaAtual() },
            aoAbrirExternoAba = { _ -> abrirNoNavegadorExterno() }
        )
        binding.recyclerAbasBrowser.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerAbasBrowser.adapter = adapter
        binding.btnBrowserVoltar.setOnClickListener { navegarVoltar() }
        binding.btnBrowserAvancar.setOnClickListener { tabManager.webViewAtiva()?.goForward() }
        binding.btnBrowserRecarregar.setOnClickListener { tabManager.webViewAtiva()?.reload() }
        binding.editBrowserEndereco.setOnEditorActionListener { _, _, _ -> carregarEnderecoDigitado(); true }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!navegarVoltar()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        restaurarSessaoSalva()
        if (intent.getStringExtra(EXTRA_URL) != null) {
            abrirNovaAbaDoIntent(intent)
        } else {
            tabManager.idAbaAtiva()?.let { selecionarAba(it) }
                ?: criarNovaAbaManual()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        abrirNovaAbaDoIntent(intent)
    }

    /**
     * Fase 21.11 — restaura a sessão salva pela Fase 21.10 (chamada só uma
     * vez, no `onCreate`, antes de qualquer aba do Intent atual ser aberta).
     * Recria cada aba salva com [BrowserTabManager.restaurarAbas] e carrega a
     * `urlAtual` de cada uma no WebView recém-criado. Sem sessão salva, não
     * faz nada — comportamento idêntico ao de antes da 21.11.
     */
    private fun restaurarSessaoSalva() {
        val (abasSalvas, idAtivaSalvo) = historyManager.lerSessao()
        if (abasSalvas.isEmpty()) return
        tabManager.restaurarAbas(abasSalvas, idAtivaSalvo)
        abasSalvas.forEach { aba ->
            val webView = tabManager.obterWebView(aba.id) ?: return@forEach
            configurarWebView(webView, aba.id)
            webView.loadUrl(aba.urlAtual)
        }
    }

    /** Fase 21.8 — cria uma nova aba a partir do Intent recebido (inicial ou singleTask) e a ativa na tela. */
    private fun abrirNovaAbaDoIntent(intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL) ?: return
        val nomeIA = intent.getStringExtra(EXTRA_NOME_IA) ?: ""
        val iconeIA = intent.getStringExtra(EXTRA_ICONE_IA) ?: ""
        val request = PrefillRequest(
            intent.getStringExtra(EXTRA_PROMPT).orEmpty(),
            intent.getStringExtra(EXTRA_PREFILL_CAPABILITY)?.let { runCatching { PrefillCapability.valueOf(it) }.getOrNull() } ?: PrefillCapability.UNKNOWN,
            intent.getStringExtra(EXTRA_OPEN_MODE)?.let { runCatching { BrowserOpenMode.valueOf(it) }.getOrNull() } ?: BrowserOpenMode.OPEN_ONLY
        )

        val aba = tabManager.criarAba(nomeIA, url, iconeIA)
        configurarWebView(tabManager.obterWebView(aba.id) ?: return, aba.id)
        prefillTentado.remove(aba.id)
        tabManager.obterWebView(aba.id)?.tag = request
        tabManager.obterWebView(aba.id)?.loadUrl(url)
        selecionarAba(aba.id)
    }

    private fun criarNovaAbaManual() {
        val aba = tabManager.criarAba("Nova aba", "about:blank", "")
        val webView = tabManager.obterWebView(aba.id) ?: return
        configurarWebView(webView, aba.id)
        selecionarAba(aba.id)
        binding.editBrowserEndereco.setText("")
    }

    private fun carregarEnderecoDigitado(): Boolean {
        val texto = binding.editBrowserEndereco.text?.toString()?.trim().orEmpty()
        val endereco = if (texto.startsWith("https://") || texto.startsWith("http://")) texto else "https://$texto"
        val uri = runCatching { Uri.parse(endereco) }.getOrNull()
        if (uri?.let { it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() } != true) {
            Snackbar.make(binding.root, com.aibrain.app.R.string.browser_endereco_invalido, Snackbar.LENGTH_SHORT).show()
            return false
        }
        tabManager.webViewAtiva()?.loadUrl(uri.toString())
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(binding.editBrowserEndereco.windowToken, 0)
        return true
    }

    private fun navegarVoltar(): Boolean {
        val webView = tabManager.webViewAtiva() ?: return false
        if (!webView.canGoBack()) return false
        webView.goBack()
        return true
    }

    private fun selecionarAba(id: String) {
        tabManager.ativarNaTela(id, binding.containerWebViewBrowser) { webView ->
            configurarWebView(webView, id)
        } ?: return
        adapter.atualizar(tabManager.obterAbas(), tabManager.idAbaAtiva())
        atualizarControlesNavegacao()
    }

    private fun atualizarControlesNavegacao() {
        val webView = tabManager.webViewAtiva()
        binding.btnBrowserVoltar.isEnabled = webView?.canGoBack() == true
        binding.btnBrowserAvancar.isEnabled = webView?.canGoForward() == true
        val url = webView?.url.orEmpty()
        if (binding.editBrowserEndereco.text?.toString() != url && !binding.editBrowserEndereco.hasFocus()) {
            binding.editBrowserEndereco.setText(url.takeUnless { it == "about:blank" }.orEmpty())
        }
    }

    private fun fecharAba(id: String) {
        tabManager.removerAba(id)
        val proximaAtiva = tabManager.idAbaAtiva()
        if (proximaAtiva == null) {
            criarNovaAbaManual()
            return
        }
        selecionarAba(proximaAtiva)
    }

    // Fase 24 — os botões da barra superior foram removidos do layout;
    // atualizar/compartilhar/abrir externo continuam disponíveis no menu de
    // cada aba. Este helper permanece para uso futuro caso a barra retorne.
    @Suppress("unused")
    private fun configurarBarraSuperior() = Unit

    private fun compartilharPaginaAtual() {
        val url = tabManager.webViewAtiva()?.url ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(intent, null))
    }

    private fun abrirNoNavegadorExterno() {
        val url = tabManager.webViewAtiva()?.url ?: return
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    /**
     * Fase 21.15 — quando um site de IA bloqueia/falha ao carregar dentro do
     * WebView (erro de rede ou HTTP 4xx/5xx no frame principal), avisa o
     * usuário com um Snackbar com ação direta para abrir a mesma URL no
     * navegador externo (Fase 21.4) — nunca trava o app nem a aba, que
     * continua utilizável (pode fechar, trocar, tentar "Atualizar").
     */
    private fun avisarFalhaCarregamento(idAba: String) {
        val url = tabManager.obterWebView(idAba)?.url ?: return
        Snackbar.make(binding.root, com.aibrain.app.R.string.browser_falha_carregamento, Snackbar.LENGTH_LONG)
            .setAction(com.aibrain.app.R.string.browser_abrir_externo_desc) {
                startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            }
            .show()
    }

    // Fase 24 — os botões de voltar/avançar da barra superior foram removidos;
    // a navegação no histórico é feita pelos gestos de navegação do Android.
    @Suppress("unused")
    private fun atualizarEstadoNavegacao() = Unit

    /**
     * Aplica a configuração técnica (Fase 21.3) e os callbacks de UI sobre o
     * WebView recém-criado da aba [idAba].
     *
     * Fase 21.14 — garante que o navegador só renderiza o site oficial de
     * cada IA, sem modificar, injetar script em, ou alterar nenhuma página:
     * nenhum mecanismo de injeção de script ou conteúdo local existe em todo o
     * módulo Browser (nada é
     * injetado na página carregada); `shouldOverrideUrlLoading` é
     * sobrescrito explicitamente retornando `false` — toda navegação dentro
     * do domínio da IA continua sendo carregada pelo próprio WebView (nunca
     * reescrita ou redirecionada por este código); `allowFileAccessFromFileURLs`/
     * `allowUniversalAccessFromFileURLs` desligados impedem uma página
     * eventualmente maliciosa de escalar para acesso a arquivos locais via
     * `file://`.
     */
    private fun configurarWebView(webView: WebView, idAba: String) {
        WebViewSecurityPolicy.apply(webView)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                tabManager.atualizarAba(idAba) { it.copy(carregando = true, urlAtual = url ?: it.urlAtual) }
                if (tabManager.idAbaAtiva() == idAba) binding.progressBrowserAba.visibility = View.VISIBLE
                adapter.atualizar(tabManager.obterAbas(), tabManager.idAbaAtiva())
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                // O navegador interno só renderiza páginas HTTPS. Esquemas como
                // intent:, file:, content:, tel: e mailto: não devem ser aceitos
                // diretamente por um WebView de catálogo.
                return uri.scheme != "https"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tabManager.atualizarAba(idAba) { it.copy(carregando = false) }
                tentarPrefillUmaVez(view, idAba)
                tabManager.sincronizarEstadoNavegacao(idAba)
                if (tabManager.idAbaAtiva() == idAba) {
                    binding.progressBrowserAba.visibility = View.GONE
                    atualizarControlesNavegacao()
                }
                adapter.atualizar(tabManager.obterAbas(), tabManager.idAbaAtiva())
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) avisarFalhaCarregamento(idAba)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
                    avisarFalhaCarregamento(idAba)
                }
            }
        }

        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            iniciarDownload(url, contentDisposition, mimeType)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                title?.trim()?.takeIf { it.isNotBlank() && it != "about:blank" }?.let { titulo ->
                    tabManager.atualizarAba(idAba) { it.copy(tituloPagina = titulo.take(60)) }
                }
                adapter.atualizar(tabManager.obterAbas(), tabManager.idAbaAtiva())
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (tabManager.idAbaAtiva() == idAba) {
                    binding.progressBrowserAba.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                }
            }

            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                if (!isUserGesture || resultMsg == null) return false
                val aba = tabManager.criarAba("Nova aba", "about:blank", "")
                val novaWebView = tabManager.obterWebView(aba.id) ?: return false
                configurarWebView(novaWebView, aba.id)
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                transport.webView = novaWebView
                resultMsg.sendToTarget()
                selecionarAba(aba.id)
                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                callbackUploadArquivo = filePathCallback
                val tipoAceito = fileChooserParams?.acceptTypes?.firstOrNull { it.isNotBlank() } ?: "*/*"
                launcherUploadArquivo.launch(tipoAceito)
                return true
            }
        }
    }

    private fun iniciarDownload(url: String, contentDisposition: String?, mimeType: String?) {
        val uri = url.toUri()
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) {
            Snackbar.make(binding.root, "Download bloqueado: apenas HTTPS é permitido", Snackbar.LENGTH_SHORT).show()
            return
        }
        val requisicao = DownloadManager.Request(uri).apply {
            setMimeType(mimeType)
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
                addRequestHeader("cookie", it)
            }
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            val nomeArquivo = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nomeArquivo)
        }
        val gerenciador = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        gerenciador.enqueue(requisicao)
    }

    private fun tentarPrefillUmaVez(view: WebView?, idAba: String) {
        if (view == null || tabManager.idAbaAtiva() != idAba || idAba in prefillTentado) return
        val request = view.tag as? PrefillRequest ?: return
        prefillTentado += idAba
        if (request.capability != PrefillCapability.CONFIRMED || request.mode != BrowserOpenMode.PREFILL_ONLY) return
        PrefillAdapterRegistry.tryPrefill(view, request.prompt)
    }

    private data class PrefillRequest(
        val prompt: String,
        val capability: PrefillCapability,
        val mode: BrowserOpenMode
    )

    /** Fase 21.10 — salva a sessão de abas sempre que a tela sai de primeiro plano (background ou fechamento). */
    override fun onStop() {
        super.onStop()
        historyManager.salvarSessao(tabManager.obterAbas(), tabManager.idAbaAtiva())
    }

    /**
     * Fase 21.12 — em memória baixa, descarta o WebView de abas inativas
     * (dados preservados, recriados sob demanda em [selecionarAba]/
     * [ativarNaTela] ou nas ações do menu de aba). `TRIM_MEMORY_RUNNING_LOW`
     * é o primeiro nível que indica pressão de memória real do sistema
     * (ainda sem o app estar em background) — níveis abaixo dele (UI oculta,
     * app em foreground normal) não disparam descarte.
     *
     * Fase 21.13 — abas fixadas (pin) são poupadas nesse primeiro nível;
     * só em `TRIM_MEMORY_RUNNING_CRITICAL` (pressão mais severa, ainda com o
     * app em primeiro plano) elas também entram no descarte — a aba ativa
     * continua sempre preservada, fixada ou não.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                tabManager.descartarWebViewsInativas(incluirFixadas = true)
                adapter.atualizar(tabManager.obterAbas(), tabManager.idAbaAtiva())
            }
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                tabManager.descartarWebViewsInativas(incluirFixadas = false)
                adapter.atualizar(tabManager.obterAbas(), tabManager.idAbaAtiva())
            }
        }
    }

    override fun onDestroy() {
        tabManager.fecharTodas()
        super.onDestroy()
    }
}
