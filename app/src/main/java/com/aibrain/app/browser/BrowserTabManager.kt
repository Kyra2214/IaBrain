package com.aibrain.app.browser

import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Fase 21.5 — gerencia o conjunto de [AbaNavegador] do navegador interno:
 * criar aba, remover aba, trocar aba ativa e controlar o ciclo de vida do
 * [WebView] associado a cada aba (Fase 21.2 aplicada a um conjunto).
 *
 * Cada aba tem exatamente um [WebView] próprio, mantido vivo enquanto a aba
 * existir — trocar a aba ativa (Fase 21.6/21.7) não recria nem destrói
 * nenhum WebView, só muda qual é exibido. `WebViewClient`/`WebChromeClient`
 * (callbacks de UI: barra superior, upload, download, geolocalização —
 * mesmo padrão da Fase 21.3/21.4) continuam responsabilidade da
 * `BrowserActivity`; ela recebe o WebView já criado via [obterWebView] e
 * aplica os listeners, evitando duplicar lógica de UI aqui. Descarte de
 * WebView por memória baixa (Fase 21.12) e persistência entre sessões do app
 * (Fase 21.10/21.11) ficam fora deste submódulo.
 *
 * Fase 21.7 — cookies, sessão de login e estado de formulário já persistem
 * sozinhos: como o WebView de cada aba nunca é recriado nem recarrega a URL
 * ao trocar de aba (só é anexado/desanexado da tela em [ativarNaTela]), o
 * `CookieManager` (compartilhado, Fase 21.5) e o estado interno do DOM de
 * cada WebView permanecem intactos. O que exige código nesta fase é o que
 * *não* é automático: posição de scroll (perdida ao desanexar a View) e o
 * espelhamento de histórico/navegação em [AbaNavegador] para uso da UI.
 */
class BrowserTabManager(context: Context) {

    private val appContext = context.applicationContext

    private val abas = mutableListOf<AbaNavegador>()
    private val webViews = mutableMapOf<String, WebView>()
    private var idAbaAtiva: String? = null

    /** Todas as abas abertas, na ordem de criação. */
    fun obterAbas(): List<AbaNavegador> = abas.toList()

    fun obterAbaAtiva(): AbaNavegador? = abas.firstOrNull { it.id == idAbaAtiva }

    fun idAbaAtiva(): String? = idAbaAtiva

    fun quantidadeAbas(): Int = abas.size

    /** Cria uma nova aba para [nomeIA]/[url], já com seu próprio WebView, e a torna ativa. */
    fun criarAba(nomeIA: String, url: String, iconeIA: String): AbaNavegador {
        val aba = AbaNavegador(nomeIA = nomeIA, urlAtual = url, iconeIA = iconeIA)
        abas.add(aba)
        webViews[aba.id] = criarWebView()
        idAbaAtiva = aba.id
        return aba
    }

    /**
     * Remove a aba e destroi seu WebView. Se a aba removida era a ativa, a
     * próxima aba (ou, na falta dela, a anterior) passa a ser a ativa.
     */
    fun removerAba(id: String): Boolean {
        val indice = abas.indexOfFirst { it.id == id }
        if (indice == -1) return false

        webViews.remove(id)?.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        abas.removeAt(indice)

        if (idAbaAtiva == id) {
            idAbaAtiva = abas.getOrNull(indice)?.id
                ?: abas.getOrNull(indice - 1)?.id
        }
        return true
    }

    /** Troca a aba ativa para [id] sem mexer em View nenhuma. Retorna false se a aba não existir. */
    fun trocarAbaAtiva(id: String): Boolean {
        if (abas.none { it.id == id }) return false
        idAbaAtiva = id
        return true
    }

    /**
     * Fase 21.7 — troca a aba ativa E realiza a troca visual dentro de [container]:
     * salva a posição de scroll da aba anterior (o WebView some da tela, mas
     * continua vivo em segundo plano), desanexa o WebView antigo, anexa o
     * WebView de [id] e restaura a posição de scroll salva dessa aba.
     * Nenhum WebView é recriado nem recarrega a URL — só troca de container.
     *
     * Fase 21.12 — se o WebView de [id] havia sido descartado por
     * [descartarWebViewsInativas] (memória baixa), é recriado aqui via
     * [garantirWebView] antes da troca visual; [aoRecriarWebView] é chamado
     * nesse caso para quem chama reaplicar `WebViewClient`/`WebChromeClient`
     * (um WebView novo não tem listeners).
     */
    fun ativarNaTela(id: String, container: ViewGroup, aoRecriarWebView: (WebView) -> Unit = {}): AbaNavegador? {
        val (novaWebView, recriada) = garantirWebView(id) ?: return null
        if (recriada) aoRecriarWebView(novaWebView)

        idAbaAtiva?.let { idAnterior ->
            if (idAnterior != id) {
                webViews[idAnterior]?.let { webViewAnterior ->
                    atualizarAba(idAnterior) { it.copy(posicaoScroll = webViewAnterior.scrollY) }
                    container.removeView(webViewAnterior)
                }
            }
        }

        idAbaAtiva = id
        (novaWebView.parent as? ViewGroup)?.let { paiAtual ->
            if (paiAtual !== container) paiAtual.removeView(novaWebView)
        }
        if (novaWebView.parent == null) {
            container.addView(
                novaWebView,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }

        val aba = obterAbaAtiva()
        if (!recriada) novaWebView.post { novaWebView.scrollTo(0, aba?.posicaoScroll ?: 0) }
        return aba
    }

    /** WebView já existente associado à aba, ou null se a aba não existir ou seu WebView tiver sido descartado (Fase 21.12). Não recria — use [garantirWebView] quando a recriação for aceitável. */
    fun obterWebView(id: String): WebView? = webViews[id]

    fun webViewAtiva(): WebView? = idAbaAtiva?.let { webViews[it] }

    /**
     * Fase 21.12 — garante que a aba [id] tenha um WebView vivo, recriando-o
     * (a partir da `urlAtual` salva) se havia sido descartado por
     * [descartarWebViewsInativas]. Retorna o WebView e se ele acabou de ser
     * recriado, para quem chama saber se precisa reaplicar
     * `WebViewClient`/`WebChromeClient` (um WebView novo nasce sem eles).
     * Retorna null se a aba não existir.
     */
    fun garantirWebView(id: String): Pair<WebView, Boolean>? {
        val aba = abas.firstOrNull { it.id == id } ?: return null
        val jaExistia = webViews.containsKey(id)
        val webView = webViews.getOrPut(id) { criarWebView() }
        if (!jaExistia) webView.loadUrl(aba.urlAtual)
        return webView to !jaExistia
    }

    /**
     * Fase 21.12 — descarta o WebView de abas inativas (mantém o da aba
     * ativa), preservando os dados de cada [AbaNavegador] (Fase 21.7: scroll,
     * histórico, navegação) intactos para recriação sob demanda via
     * [garantirWebView]/[ativarNaTela]. Chamado pela `BrowserActivity` quando
     * o sistema sinaliza memória baixa (`onTrimMemory`).
     *
     * Fase 21.13 — [incluirFixadas] controla a prioridade das abas fixadas
     * (pin, Fase 21.9): por padrão (`false`) elas são poupadas e só as
     * inativas não-fixadas são descartadas; `true` as inclui também, para o
     * nível de pressão de memória mais crítico, quando nem elas escapam — a
     * aba ativa nunca é descartada, fixada ou não.
     */
    fun descartarWebViewsInativas(incluirFixadas: Boolean = false) {
        abas.filter { it.id != idAbaAtiva && (incluirFixadas || !it.fixada) }
            .map { it.id }
            .forEach { id ->
                webViews.remove(id)?.let { webView ->
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.destroy()
                }
            }
    }

    /** Substitui os dados da aba (Fase 21.7: scroll, histórico, navegação) preservando o WebView. */
    fun atualizarAba(id: String, transformacao: (AbaNavegador) -> AbaNavegador) {
        val indice = abas.indexOfFirst { it.id == id }
        if (indice == -1) return
        abas[indice] = transformacao(abas[indice])
    }

    /**
     * Fase 21.7 — lê o estado de navegação atual do WebView da aba (URL, se
     * pode voltar/avançar) e espelha em [AbaNavegador], anexando a URL ao
     * histórico quando ela mudou. Chamado pela `BrowserActivity` a partir do
     * `WebViewClient.onPageFinished` da aba (integração de UI é Fase 21.8) —
     * o manager só sabe *como* espelhar o estado, não *quando*.
     */
    fun sincronizarEstadoNavegacao(id: String) {
        val webView = webViews[id] ?: return
        val urlAtual = webView.url ?: return
        atualizarAba(id) { aba ->
            aba.copy(
                urlAtual = urlAtual,
                podeVoltar = webView.canGoBack(),
                podeAvancar = webView.canGoForward(),
                historico = if (aba.historico.lastOrNull() == urlAtual) aba.historico
                    else aba.historico + urlAtual,
                ultimaAtualizacao = System.currentTimeMillis()
            )
        }
    }

    /** Fase 21.9 — alterna fixada (pin) da aba; prioridade contra descarte por memória é Fase 21.13. Retorna o novo estado, ou null se a aba não existir. */
    fun alternarFixada(id: String): Boolean? {
        val aba = abas.firstOrNull { it.id == id } ?: return null
        val novoEstado = !aba.fixada
        atualizarAba(id) { it.copy(fixada = novoEstado) }
        return novoEstado
    }

    /** Fase 21.9 — recarrega a URL oficial da IA (`urlInicial`) no WebView da aba, mesmo se não for a ativa. Fase 21.12 — recria o WebView primeiro se ele havia sido descartado, chamando [aoRecriarWebView] para quem chama reaplicar os listeners. */
    fun abrirPaginaInicial(id: String, aoRecriarWebView: (WebView) -> Unit = {}) {
        val aba = abas.firstOrNull { it.id == id } ?: return
        val (webView, recriada) = garantirWebView(id) ?: return
        if (recriada) aoRecriarWebView(webView)
        webView.loadUrl(aba.urlInicial)
    }

    /** Fase 21.9 — atualiza (reload) o WebView da aba individualmente, mesmo se não for a ativa. Fase 21.12 — recria o WebView primeiro se ele havia sido descartado (já nasce recarregado, sem precisar de `reload()` extra), chamando [aoRecriarWebView] para quem chama reaplicar os listeners. */
    fun atualizarWebViewDaAba(id: String, aoRecriarWebView: (WebView) -> Unit = {}) {
        val (webView, recriada) = garantirWebView(id) ?: return
        if (recriada) aoRecriarWebView(webView) else webView.reload()
    }

    /**
     * Fase 21.11 — recria as abas salvas por [BrowserHistoryManager.lerSessao]
     * (Fase 21.10): mesmo id/URL/histórico/scroll/pin de cada [AbaNavegador],
     * com um [WebView] novo por aba (nenhuma sessão de WebView sobrevive ao
     * processo ser recriado — só os dados). Só adiciona ao conjunto atual, sem
     * mesclar com nada; usada uma única vez, logo após o `BrowserTabManager`
     * ser criado. `idAtivaSalvo` define a aba ativa; se não for encontrada
     * entre as restauradas, a primeira assume esse papel. Carregar a URL de
     * cada WebView restaurado é responsabilidade de quem chama (mesmo padrão
     * de [criarAba], que também não decide isso aqui).
     */
    fun restaurarAbas(abasSalvas: List<AbaNavegador>, idAtivaSalvo: String?) {
        if (abasSalvas.isEmpty()) return
        abasSalvas.forEach { aba ->
            abas.add(aba)
            webViews[aba.id] = criarWebView()
        }
        idAbaAtiva = abasSalvas.firstOrNull { it.id == idAtivaSalvo }?.id ?: abasSalvas.first().id
    }

    /** Destroi todos os WebViews e limpa o conjunto de abas (ex: fechar o navegador por completo). */
    fun fecharTodas() {
        webViews.values.forEach { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        webViews.clear()
        abas.clear()
        idAbaAtiva = null
    }

    /**
     * Fase 21.14 — configuração técnica com as duas flags de acesso a
     * arquivo local (`allowFileAccessFromFileURLs`/
     * `allowUniversalAccessFromFileURLs`) explicitamente desligadas: impedem
     * que uma página `file://` (não usada pelo app, mas alcançável se algum
     * site malicioso tentasse redirecionar para lá) leia outros arquivos
     * locais ou escale para acesso universal de origem — nenhum efeito sobre
     * a navegação normal `https://` dos sites das IAs.
     */
    private fun criarWebView(): WebView {
        val webView = WebView(appContext)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            mediaPlaybackRequiresUserGesture = false
            setGeolocationEnabled(true)
            allowFileAccess = true
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
        }
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        return webView
    }
}
