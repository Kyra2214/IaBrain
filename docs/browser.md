# Navegador 2.0 — Multi-tab e experiência de uso

## Visão geral

O Navegador interno é o executor visual das IAs selecionadas pelo Chat, Brain e Prompt Builder. Ele não escolhe IAs, não consulta um catálogo próprio e não monta endereços. A entrada continua passando por `IAOpenContract` e `IAUrlResolver` antes de chegar à `BrowserActivity`.

> O Browser controla navegação e abas. A decisão de qual IA abrir permanece fora dele.

## Fluxo de abertura

```text
Chat / Brain / Prompts
        ↓
IA selecionada
        ↓
IAOpenContract
        ↓
IAUrlResolver.resolve()
        ↓
BrowserActivity
        ↓
BrowserTabManager.criarAba()
        ↓
WebView independente
        ↓
Usuário navega, troca de aba ou abre nova aba
```

A Activity usa `singleTask`. Quando outro fluxo abre uma IA com um contrato resolvido, `onNewIntent()` cria uma nova aba em vez de reutilizar silenciosamente a aba atual. Abas anteriores permanecem vivas e preservam seu histórico.

## Modelo de aba

`AbaNavegador` representa o estado persistível de uma aba. O objeto não guarda a instância de `WebView`; a WebView fica no `BrowserTabManager` enquanto a aba está ativa na sessão.

| Campo | Finalidade |
|---|---|
| `id` | Identidade estável da aba |
| `nomeIA` | Nome fornecido pelo contrato ou fallback da aba |
| `urlAtual` | URL atual espelhada da WebView |
| `iconeIA` | Ícone ou logo associado à entrada |
| `urlInicial` | Endereço inicial usado pela ação Página inicial |
| `tituloPagina` | Título recebido pelo `WebViewClient` |
| `carregando` | Loading independente da aba |
| `historico` | URLs observadas durante a sessão |
| `podeVoltar` / `podeAvancar` | Estado dos controles de histórico |
| `posicaoScroll` | Posição restaurada ao trocar de aba |
| `fixada` | Proteção contra descarte em pressão moderada de memória |

`BrowserTabManager` mantém um `WebView` por identidade de aba. Trocar de aba remove a WebView do container visual, mas não a destrói nem recarrega sua URL. Fechar uma aba remove somente sua WebView, destrói suas referências e seleciona a próxima aba de maneira previsível.

Ao fechar a última aba, o navegador cria uma nova aba inicial vazia. A Activity não é encerrada por causa do fechamento de uma aba.

## Barra de abas

A barra horizontal exibe o título da página quando disponível e usa o nome da IA como fallback. Cada item possui indicação visual da aba ativa, botão de fechamento, ícone opcional, estado de carregamento e menu contextual.

O item `+` cria uma aba manual com `about:blank`. Essa aba não exige seleção de IA e permite que o usuário digite um endereço HTTP ou HTTPS. O menu contextual continua oferecendo fixar, atualizar, página inicial, compartilhar e abrir externamente.

## Navegação

A barra do navegador possui voltar, avançar, recarregar e endereço. O botão voltar chama `WebView.canGoBack()` antes de `goBack()`. O back do Android respeita a mesma regra; somente quando não há histórico a Activity executa o comportamento normal de retorno.

O botão avançar usa `canGoForward()` e fica desabilitado quando não há histórico futuro. Recarregar atua somente na WebView da aba ativa. A barra de endereço acompanha a URL após `onPageFinished` e aceita HTTP/HTTPS, adicionando `https://` quando o usuário digita apenas um host.

Esquemas perigosos ou não suportados são rejeitados. O WebViewClient não chama `loadUrl()` novamente para a mesma navegação interceptada: URLs HTTPS retornam `false` e seguem o ciclo normal do WebView; esquemas não HTTPS são bloqueados.

## target blank e window.open

A política de WebView habilita `setSupportMultipleWindows(true)` para que páginas possam solicitar novas janelas. `BrowserActivity.onCreateWindow()` aceita somente solicitações com gesto explícito do usuário. Cada solicitação aceita recebe uma nova aba e uma nova WebView através de `WebViewTransport`.

Solicitações sem gesto do usuário são recusadas. O Browser não aceita pop-ups indiscriminadamente e não permite que uma nova janela destrua a aba de origem.

## Contrato de abertura e prefill

A Activity recebe a URL já resolvida pelo contrato. Ela não contém condicionais por nome de IA e não mantém mapa de domínios.

```text
IAOpenContract
  selectedAIId / selectedAIName
  officialResolvedUrl
  generatedPrompt
  prefillCapability
  openMode
        ↓
IAUrlResolver
        ↓
BrowserActivity
```

Quando `PrefillCapability` não é confirmado, `openMode` permanece `OPEN_ONLY`. O Browser pode abrir a página e preservar o prompt para cópia. Ele não executa JavaScript arbitrário, não procura textareas, não submete formulários, não clica em enviar, não automatiza login e não altera o DOM de IAs externas.

O prefill confirmado continua passando pelo `PrefillAdapterRegistry`, uma única vez e somente na aba ativa. O estado de tentativa é individual por aba.

## Segurança

A política comum em `WebViewSecurityPolicy` mantém JavaScript e DOM storage para compatibilidade com as aplicações web, mas bloqueia conteúdo misto, acesso a arquivos, acesso universal entre arquivos e cookies de terceiros. Reprodução de mídia exige gesto do usuário e geolocalização permanece desabilitada.

Downloads aceitam somente URLs HTTPS e são tratados pelo `DownloadManager`. Erros de rede e HTTP no frame principal produzem Snackbar amigável com opção de abrir a mesma URL no navegador externo. Stack traces não são exibidos.

O uso de JavaScript é uma configuração de compatibilidade da WebView. Não existe código de injeção, scraping de DOM ou automação de controles externos.

## Estado, persistência e ciclo de vida

`BrowserHistoryManager` persiste a lista de abas, aba ativa, URL atual, URL inicial, histórico, posição de scroll, título, loading e pin. A persistência usa `SharedPreferences` e JSON. Sessões antigas sem título ou loading continuam válidas porque os novos campos possuem valores padrão.

Ao recriar a Activity, o histórico restaura o conjunto de abas e cria novas WebViews apenas porque instâncias de WebView não são persistidas. A URL atual de cada aba é recarregada, e a aba ativa salva é reanexada ao container. Ao trocar entre Chat, Brain, Navegador e Prompts, `FLAG_ACTIVITY_REORDER_TO_FRONT` mantém a instância do Browser sem recriar as abas.

Em `onTrimMemory`, WebViews inativas podem ser descartadas para reduzir pressão de memória. A aba ativa sempre é preservada. Abas fixadas são preservadas em pressão moderada e só entram no descarte em pressão crítica. O estado mínimo permanece em `AbaNavegador` para recriação sob demanda.

Ao destruir uma aba ou encerrar a Activity, o manager remove a View do parent, chama `destroy()` e limpa as referências internas. Isso evita WebViews órfãs e vazamentos da Activity.

## Estados e acessibilidade

O progresso pertence à aba, e não ao navegador inteiro. A barra de abas mostra um indicador individual enquanto aquela WebView carrega. O nome da aba usa fallback quando a página não envia título.

Controles possuem content descriptions, áreas de toque de pelo menos 48dp e estados habilitado/desabilitado visíveis. A aba ativa combina superfície, contraste e hierarquia; a seleção não depende somente de cor. O endereço permanece editável como texto URI e a interface usa tokens do Design System.

## Testes e validação

`BrowserTabStateTest` verifica identidade independente, título, loading, histórico e posição de scroll por aba. As validações Gradle da fase permanecem focadas em testes unitários, compilação de testes Android, empacotamento debug e `git diff --check`. A suíte E2E não é reaberta nesta fase.

## Referências

[1]: design-system.md "Design System IaBrain"
[2]: brain.md "Brain 2.0 — Central de descoberta de IAs"
[3]: chat-orchestration.md "Chat 2.0 — Orquestração inteligente"
