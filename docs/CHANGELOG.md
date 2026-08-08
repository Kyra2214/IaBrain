# CHANGELOG — AI BRAIN

## FASE 26 — Adição Real ao Catálogo pela Curadoria ✅ CONCLUÍDA
*Solicitação do usuário: ao pesquisar IAs e tocar em "Adicionar ao catálogo", a IA deve de fato entrar no catálogo — e categorias novas (ex.: "Saúde Mental") devem criar sua própria aba automaticamente.*

- **Causa do bug (Fase 18.8)**: "Adicionar ao catálogo" apenas copiava um snippet JSON para a área de transferência — a inserção era propositalmente manual/revisada pelo curador. A IA nunca entrava de fato no app.
- **Persistência real** (`SnippetCatalogoIA.paraIA` + `CatalogoCuradoRepository.adicionarUma`): a sugestão da Groq agora vira uma [IA] completa — `id` único com prefixo `curada-`, logo via favicon do domínio, descrição enviada pela Groq, categorias, idiomas e nota — e é salva em disco. A IA aparece na listagem principal na hora e sobrevive a reinícios do app. O snippet continua sendo copiado como backup para revisão manual.
- **Categorias novas viram aba própria** (`CategoriaDinamica` + chips da tela principal): quando a categoria sugerida não existe no enum fixo, ela é mantida como chave textual capitalizada (ex.: "Saúde Mental") e ganha automaticamente um chip de filtro na tela principal, funcionando também no carrossel e na tela de detalhes. Categorias que casam com uma fixa (case/acento insensível) são mapeadas para a chave do enum.
- **Prompt de curadoria estendido** (`PromptCuradoriaIA` + `ParserCuradoriaIA`): a Groq agora devolve também uma descrição curta e recebe a lista de categorias existentes, usando categoria nova apenas quando nenhuma fixa se encaixa.
- **Feedback ao usuário**: Snackbars de sucesso ("X foi adicionada ao catálogo"), "já está no catálogo" (proteção contra toque duplo no mesmo item) e de falha.
- **Testes**: suíte completa com 70 testes passando (novos testes de `SnippetCatalogoIA` e `CategoriaDinamica`).

## FASE 25 — Geração de Prompts com IA (Groq) e Correções Visuais ✅ CONCLUÍDA
*Solicitação do usuário: (1) corrigir o link escuro e o balão "Remover API key" cortado na tela do Assistente de IA; (2) permitir que o Criador de Prompts use a API key já configurada no app para gerar prompts automaticamente a partir do texto digitado.*

- **Link da Groq ilegível** (`activity_assistente_ia.xml`): o botão "Gerar API key grátis →" usava `textColor=@color/primary`, que no modo escuro é quase preto sobre o card claro — trocado para `@color/secondary` (verde-água), visível nos dois temas.
- **Balão "Remover API key" cortado** (`activity_assistente_ia.xml` + `AssistenteIAActivity.kt`): as Snackbars eram ancoradas ao `NestedScrollView` raiz, ficando cortadas na parte de baixo da tela; a raiz virou `CoordinatorLayout` e as Snackbars passaram a ser ancoradas a ele.
- **Gerar prompt com a IA** (`CriadorPromptsActivity.kt` + `activity_criador_prompts.xml` + `PromptGeneratorGroq.kt`): novo chip "⚡ Gerar com IA" no cabeçalho do Criador de Prompts; quando ligado, o texto digitado é enviado à Groq (com a mesma API key cadastrada no Assistente de IA, via `AssistenteIARepository`) e a IA devolve um prompt completo, exibido como mensagem do assistente — o prompt gerado pode ser salvo na Biblioteca e a IA de destino recomendada continua funcionando.
- **Modo clássico preservado**: com o chip desligado, o fluxo original do Prompt Builder (identificação → template → perguntas → variáveis → IA) funciona exatamente como antes.
- **Sem chave configurada**: ao gerar com IA sem API key, a conversa orienta o usuário a configurá-la no Assistente de IA; indicador de progresso durante a geração e proteção contra duplo envio.
- **Prompt de sistema** (`PromptGeneratorGroq`): instrução fixa de engenharia de prompts (papel, objetivo, contexto, formato de saída e restrições) com fallback automático entre os modelos gratuitos da Groq.
- **Testes**: 4 novos testes para o gerador; suíte completa com 56 testes passando.

## FASE 24 — Interface do Navegador e Barra de Navegação ✅ CONCLUÍDA
*Solicitação do usuário: remover a faixa cinza superior do navegador interno (com o nome "AI Brain") e deixar a área IA +18 integrada à barra de navegação do próprio app.*

- **Barra superior removida** (`activity_browser.xml`): a faixa cinza com botões voltar/avançar/atualizar e o nome "AI Brain" foi removida; o site da IA agora ocupa a tela inteira, e voltar/avançar no histórico é feito pelos gestos de navegação do próprio Android.
- **Menu de aba ampliado** (`menu_aba_navegador.xml` + `BrowserAdapter.kt`): as ações "Compartilhar" e "Abrir no navegador externo", que ficavam na barra removida, continuam disponíveis no menu de contexto de cada aba (toque longo).
- **Ícone dedicado IA +18** (`ic_ia18.xml`): criado selo "18+" próprio para o botão da área +18 na barra de navegação do app, substituindo o ícone genérico reutilizado; o botão mantém o destaque rosa (`#FF4081`) e abre a área +18 dentro do próprio app, igual às demais abas.
- **Barra cinza do sistema removida** (`Theme.AIBrain.NoActionBar` + `AndroidManifest.xml`): a faixa cinza no topo de todas as telas era a ActionBar padrão do Android exibindo o rótulo "AI Brain"; criado tema sem barra de título e aplicado às 12 activities — o cabeçalho de cada tela é o desenho próprio do app.
- **Navegação interna na área +18** (`IA18Activity`): tocar em uma IA do catálogo +18 agora abre o site no navegador interno do app (`BrowserActivity`), com abas, exatamente como as demais IAs do catálogo principal — antes disparava o navegador externo do Android.

## FASE 23 — Correção da Tela do Criador de Prompts ✅ CONCLUÍDA
*Correção do bug que fazia a tela "Criador de Prompts" abrir apenas com o título, sem o campo de mensagem, a conversa e os botões.*

- **Causa**: o layout antigo usava cores do tema claro (fundo branco/hint escuro) incompatíveis com o modo escuro do dispositivo e uma cadeia de constraints ancorada a um container intermediário oculto (`containerAcoesResultado`), que empurrava a entrada de mensagem e as ações para fora da área visível.
- **Layout reescrito** (`activity_criador_prompts.xml`): raiz em `LinearLayout` vertical com o histórico da conversa ocupando todo o espaço livre (`layout_weight=1`), estado vazio sobreposto ao centro, e ações/entrada como irmãos diretos — sem nenhuma âncora dependente de views ocultas.
- **Cores explícitas do tema escuro**: `boxBackgroundColor=@color/surface` (escuro), hint `@color/on_background_muted` e texto `@color/on_background` (claros), garantindo visibilidade no modo claro e no modo escuro.
- **Consistência de código**: corrigido comentário KDoc aberto no `CriadorPromptsActivity` (`brain/*`), ids inexistentes no `IA18Adapter` (+18), import errado duplicado no `IA18ViewModel` e `getChildAt` no `AIBrainActivity`.
- **Busca sem acentos**: a pesquisa do catálogo agora normaliza acentos (`normalizarBusca()`), permitindo encontrar "artísticas" digitando "artistica".
- **Testes**: suíte completa de 52 testes unitários passando, com `org.json` real adicionado aos testes locais e `isReturnDefaultValues` habilitado.

## FASE 22 — Módulo IA +18 ✅ CONCLUÍDA
*Implementação da área restrita para maiores de 18 anos, com isolamento total e catálogo independente.*

- **Arquitetura Isolada**: Criado novo pacote `view` para atividades da área 18+ e repositório dedicado `IA18Repository`.
- **Controle de Acesso**: Implementada `IA18VerificacaoActivity` para confirmação de idade, com persistência em `SharedPreferences`.
- **Catálogo Independente**: Criado `ia_18_catalogo.json` em assets, separado do catálogo principal.
- **Categorias e Submenus**: Implementada estrutura de submenus com descrições de categorias e filtragem dinâmica.
- **UI Consistente**: Reaproveitamento do padrão visual de cards e chips do app, mas com dados e rotas totalmente independentes.
- **Integração Segura**: Adicionado botão de acesso na `MainActivity` com destaque visual (cor rosa/secondary) e redirecionamento automático para verificação se necessário.

## FASE 19 — Correções de Bugs Reportados ✅ CONCLUÍDA
... (conteúdo anterior preservado)

## FASE 21 — Módulo Browser ✅ CONCLUÍDA
... (conteúdo anterior preservado)
