package com.aibrain.app.browser

import android.Manifest
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aibrain.app.cache.ImagemCache
import com.aibrain.app.databinding.ActivityBrowserBinding
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

    private var callbackUploadArquivo: ValueCallback<Array<Uri>>? = null
    private var callbackGeolocalizacao: GeolocationPermissions.Callback? = null
    private var origemGeolocalizacao: String? = null

    private val launcherUploadArquivo: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val resultado = if (uri != null) arrayOf(uri) else null
            callbackUploadArquivo?.onReceiveValue(resultado)
            callbackUploadArquivo = null
        }

    private val launcherPermissaoLocalizacao: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
            val origem = origemGeolocalizacao
            if (origem != null) {
                callbackGeolocalizacao?.invoke(origem, concedida, false)
            }
            callbackGeolocalizacao = null
            origemGeolocalizacao = null
        }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_NOME_IA = "extra_nome_ia"
        const val EXTRA_ICONE_IA = "extra_icone_ia"

        /** Helper — nova aba com a IA informada, reaproveitando a instância singleTask já aberta. */
        fun criarIntent(context: android.content.Context, nomeIA: String, url: String, iconeIA: String): Intent {
            return Intent(context, BrowserActivity::class.java).apply {
                putExtra(EXTRA_NOME_IA, nomeIA)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_ICONE_IA, iconeIA)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tabManager = BrowserTabManager(applicationContext)
        historyManager = BrowserHistoryManager(applicationContext)
        adapter = BrowserAdapter(
            escopo = lifecycleScope,
            imagemCache = ImagemCache(applicationContext),
            aoSelecionarAba = { aba -> selecionarAba(aba.id) },
            aoFecharAba = { aba -> fecharAba(aba.id) },
            aoNovaAba = { finish() }, // ASSUMINDO: volta ao catálogo para escolher a próxima IA (cadastro de aba sem IA é Fase 21.9)
            aoFixarAba = { aba ->
                tabManager.alternarFixada(aba.id)
                adapter.atualizar(tabManager.obterAbas(), tabManager.idAbaAtiva())
            },
            aoAtualizarAba = { aba -> tabManager.atualizarWebViewDaAba(aba.id) { webView -> configurarWebView(webView, aba.id) } },
            aoAbrirPaginaInicialAba = { aba -> tabManager.abrirPaginaInicial(aba.id) { webView -> configurarWebView(webView, aba.id) } }
        )
        binding.recyclerAbasBrowser.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerAbasBrowser.adapter = adapter

        configurarBarraSuperior()
        restaurarSessaoSalva()
        if (intent.getStringExtra(EXTRA_URL) != null) {
            abrirNovaAbaDoIntent(intent)
        } else {
            tabManager.idAbaAtiva()?.let { selecionarAba(it) }
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

        val aba = tabManager.criarAba(nomeIA, url, iconeIA)
        configurarWebView(tabManager.obterWebView(aba.id) ?: return, aba.id)
        tabManager.obterWebView(aba.id)?.loadUrl(url)
        selecionarAba(aba.id)
    }

    private fun selecionarAba(id: String) {
        val aba = tabManager.ativarNaTela(id, binding.containerWebViewBrowser) { webView ->
            configurarWebView(webView, id)
        } ?: return
        binding.txtBrowserTitulo.text = aba.nomeIA
        atualizarEstadoNavegacao()
        adapter.atualizar(tabManager.obterAbas(), tabManager.idAbaAtiva())
    }

    private fun fecharAba(id: String) {
        tabManager.removerAba(id)
        val proximaAtiva = tabManager.idAbaAtiva()
        if (proximaAtiva == null) {
            finish()
            return
        }
        selecionarAba(proximaAtiva)
    }

    private fun configurarBarraSuperior() {
        binding.btnBrowserVoltar.setOnClickListener {
            tabManager.webViewAtiva()?.let { if (it.canGoBack()) it.goBack() }
        }
        binding.btnBrowserAvancar.setOnClickListener {
            tabManager.webViewAtiva()?.let { if (it.canGoForward()) it.goForward() }
        }
        binding.btnBrowserAtualizar.setOnClickListener {
            tabManager.webViewAtiva()?.reload()
        }
        binding.btnBrowserCompartilhar.setOnClickListener {
            compartilharPaginaAtual()
        }
        binding.btnBrowserAbrirExterno.setOnClickListener {
            abrirNoNavegadorExterno()
        }
        atualizarEstadoNavegacao()
    }

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

    private fun atualizarEstadoNavegacao() {
        val webView = tabManager.webViewAtiva()
        val podeVoltar = webView?.canGoBack() ?: false
        val podeAvancar = webView?.canGoForward() ?: false
        binding.btnBrowserVoltar.isEnabled = podeVoltar
        binding.btnBrowserVoltar.alpha = if (podeVoltar) 1f else 0.4f
        binding.btnBrowserAvancar.isEnabled = podeAvancar
        binding.btnBrowserAvancar.alpha = if (podeAvancar) 1f else 0.4f
    }

    /**
     * Aplica a configuração técnica (Fase 21.3) e os callbacks de UI sobre o
     * WebView recém-criado da aba [idAba].
     *
     * Fase 21.14 — garante que o navegador só renderiza o site oficial de
     * cada IA, sem modificar, injetar script em, ou alterar nenhuma página:
     * nenhuma chamada a `evaluateJavascript`/`loadDataWithBaseURL`/
     * `addJavascriptInterface` existe em todo o módulo Browser (nada é
     * injetado na página carregada); `shouldOverrideUrlLoading` é
     * sobrescrito explicitamente retornando `false` — toda navegação dentro
     * do domínio da IA continua sendo carregada pelo próprio WebView (nunca
     * reescrita ou redirecionada por este código); `allowFileAccessFromFileURLs`/
     * `allowUniversalAccessFromFileURLs` desligados impedem uma página
     * eventualmente maliciosa de escalar para acesso a arquivos locais via
     * `file://`.
     */
    private fun configurarWebView(webView: WebView, idAba: String) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            mediaPlaybackRequiresUserGesture = false // reprodução de vídeo inline
            setGeolocationEnabled(true)
            allowFileAccess = true
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                // Fase 21.14 — nunca intercepta/redireciona: toda URL é carregada
                // normalmente dentro deste WebView, sem alterar o destino da navegação.
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tabManager.sincronizarEstadoNavegacao(idAba)
                if (tabManager.idAbaAtiva() == idAba) {
                    atualizarEstadoNavegacao()
                    val nomeIA = tabManager.obterAbaAtiva()?.nomeIA
                    if (nomeIA.isNullOrBlank()) {
                        binding.txtBrowserTitulo.text = view?.title ?: ""
                    }
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

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (origin == null || callback == null) return
                val temPermissao = ContextCompat.checkSelfPermission(
                    this@BrowserActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (temPermissao) {
                    callback.invoke(origin, true, false)
                } else {
                    callbackGeolocalizacao = callback
                    origemGeolocalizacao = origin
                    launcherPermissaoLocalizacao.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        }
    }

    private fun iniciarDownload(url: String, contentDisposition: String?, mimeType: String?) {
        val requisicao = DownloadManager.Request(url.toUri()).apply {
            setMimeType(mimeType)
            addRequestHeader("cookie", CookieManager.getInstance().getCookie(url))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            val nomeArquivo = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nomeArquivo)
        }
        val gerenciador = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        gerenciador.enqueue(requisicao)
    }

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
