# Brain 2.0 — Central de descoberta de IAs

## Visão geral

O **Brain** é a central local de descoberta, entendimento, comparação e decisão do IaBrain. A experiência começa com uma pergunta simples: o usuário pode explorar o catálogo, buscar uma capacidade ou escolher um objetivo. Em seguida, o Brain apresenta os dados conhecidos, ordena as opções com regras determinísticas e explica a recomendação sem inventar avaliações, preços ou capacidades.

A implementação mantém a arquitetura Android existente em Views/XML. A home continua sendo `MainActivity`, a tela de Chat continua sendo `AIBrainActivity`, e a navegação global permanece sob responsabilidade de `GlobalNavigation`.

> O catálogo sincronizado é a fonte de verdade das IAs. O Brain interpreta e organiza seus dados, mas não cria um segundo catálogo.

## Fontes de verdade

As fontes utilizadas pelo Brain são as seguintes:

| Informação | Fonte | Papel no Brain |
|---|---|---|
| Nome, descrição, site, categorias, notas e acesso | `CatalogoRepository` e `ia_catalogo.json` sincronizado | Catálogo principal e detalhes |
| Alterações curadas | `CatalogoCuradoRepository` | Merge local sem apagar a base existente |
| Capacidades e especialidades operacionais | `IACapabilityRegistry` e tabelas Room `ia_capacidades` | Roteamento de comandos e integração operacional |
| Perfis de qualidade, velocidade e custo | `IARoutingProfileEntity` no Room | Sinais opcionais do `LocalAIRouter` |
| Favoritos e histórico | `FavoritosRepository` em `SharedPreferences` | Preferência persistente do usuário |
| Prompts gerados | `PromptRoomRepository` e `PromptRepository` | Biblioteca e rastreabilidade de prompts |
| Abertura de IA | `IAOpenContract` e `IAUrlResolver` | Resolução e validação do endereço oficial |
| Navegação externa controlada | `BrowserActivity` e `BrowserTabManager` | Abas, `OPEN_ONLY` e prefill controlado |

O JSON embarcado possui 22 IAs na versão atualmente inspecionada. O Brain não acrescenta campos fictícios ao catálogo. Quando uma informação não está cadastrada, a interface exibe ausência de informação em vez de uma suposição.

## Home de descoberta

A home apresenta o título Brain, a proposta de valor e uma busca ampla. Abaixo, o usuário encontra objetivos, destaques, favoritos, IAs com acesso gratuito e o catálogo completo.

Os objetivos disponíveis são derivados das categorias que realmente aparecem no catálogo. A seleção de um objetivo consulta `BrainDiscoveryEngine`, exibe até três opções e oferece comparação quando há pelo menos duas correspondências.

As seções de destaques e gratuitas são ordenadas pelas notas médias cadastradas. A seção de favoritos cruza os IDs persistidos pelo `FavoritosRepository` com o catálogo atual. Se não houver favoritos, o estado vazio informa a próxima ação esperada.

## Busca

`BrainDiscoveryEngine.correspondeBusca` pesquisa o nome, a descrição, as categorias, os rótulos das categorias, as chaves de notas, os casos de uso, as plataformas, o modelo de acesso, o status e o nível de acesso.

A normalização remove acentos e ignora diferença entre maiúsculas e minúsculas. O motor também reconhece aliases explícitos para termos relacionados, como `programação`, `programar`, `code`, `coding`, `development`, `programming`, `software`, `pesquisa`, `research`, `imagem`, `image`, `escrita` e `writing`.

A expansão é deliberadamente limitada. O nome `chatgpt`, por exemplo, não é convertido em um termo genérico que faria todas as IAs conversacionais aparecerem. O resultado da busca preserva a ordem do catálogo; a ordenação explícita fica sob controle do usuário.

## Filtros combináveis

Os filtros da home são aplicados com **AND**. Um resultado precisa satisfazer todos os critérios ativos.

| Filtro | Dados considerados | Regra de ausência |
|---|---|---|
| Categoria | `IA.categorias` | Categorias aparecem somente se existirem no catálogo |
| Acesso | `IA.acesso` | O valor é derivado de `acesso` ou, em catálogos antigos, de `gratuita` |
| Capacidade | Categorias e chaves de notas cadastradas | Não há capacidade sintética fora desses dados |
| Plataforma | `IA.plataformas` | O grupo fica oculto quando nenhuma plataforma está cadastrada |
| Texto | Índice de busca descrito acima | Nenhuma correspondência produz estado vazio |

A seleção ou remoção de um chip recalcula apenas a lista filtrada no `MainViewModel`. A paginação continua sendo uma janela sobre o resultado já calculado, evitando carregar o catálogo inteiro a cada interação.

## Ranking e recomendação

A recomendação por objetivo reutiliza `detectarCategorias` e as notas existentes do `RecomendadorIA`. O `BrainDiscoveryEngine` aplica a nota da categoria como sinal principal. A categoria principal curada e a presença nos favoritos funcionam como sinais adicionais de desempate.

O score interno é um valor ordinal para ordenar as opções. Ele não é apresentado como porcentagem de qualidade. A saída inclui a opção mais bem posicionada e até três alternativas.

A justificativa é derivada dos mesmos dados que produziram o ranking. Ela pode mencionar a nota cadastrada, a categoria principal, o filtro de acesso atendido e a preferência de favorito. Se esses sinais não existirem, o Brain informa que não há dados suficientes.

O `LocalAIRouter` não foi duplicado. Ele permanece dedicado a comandos, capacidades operacionais e decisões de roteamento. O `RoomCommandResolver` continua sendo o caminho de texto livre para comandos existentes, e o `IACapabilityRegistry` garante que o Room possa ser inicializado a partir do catálogo sincronizado quando ainda não houver IAs persistidas localmente.

## Detalhes da IA

`DetalheIAActivity` exibe nome, logo, descrição, acesso, especialidades, capacidades derivadas do catálogo, idiomas, casos de uso, notas por categoria e uma explicação textual baseada nos dados disponíveis.

A tela possui quatro ações principais:

| Ação | Comportamento |
|---|---|
| Favoritar | Alterna o ID no `FavoritosRepository` e atualiza a descrição acessível do botão |
| Abrir IA | Cria `IAOpenContract`, chama `IAUrlResolver` e abre `BrowserActivity` |
| Comparar | Busca IAs relacionadas no catálogo e abre comparação com até três opções |
| Criar prompt / Perguntar no Chat | Encaminha o contexto para os fluxos já existentes sem enviar automaticamente |

## Abertura de IAs

O Brain não monta URLs, não escolhe domínio, não corrige endereço e não controla sites externos. O caminho utilizado é:

```text
Brain ou DetalheIAActivity
  → IAOpenContract
  → IAUrlResolver
  → BrowserActivity
  → BrowserTabManager
```

O contrato preserva o modo `OPEN_ONLY` quando não existe capacidade de prefill confirmada. O `BrowserActivity` mantém o comportamento de abas múltiplas. Nenhum prompt é enviado automaticamente e nenhum JavaScript é injetado em páginas externas.

## Favoritos e coleções

Favoritos e histórico continuam usando `FavoritosRepository`; nenhuma camada de armazenamento paralela foi criada. A tela de favoritos permite alternar a ordenação entre ranking das notas cadastradas e nome, além de abrir detalhes, desfavoritar e visualizar o histórico.

A estrutura existente de coleções continua sob `ColecaoRepository` e `ColecoesActivity`. Esta fase não cria uma segunda persistência para coleções porque a estrutura existente já atende ao escopo atual.

## Comparação

`CompararIAsActivity` usa `BrainDiscoveryEngine.comparar` para gerar linhas referentes às categorias, notas, acesso e, quando presentes, plataformas, API e login.

Os valores são mostrados por IA e mantêm lacunas explícitas. Uma nota cadastrada aparece como `10/10`; uma categoria presente sem nota aparece como `✓`; uma informação ausente aparece como `—`. A comparação orientada à tarefa prioriza as categorias detectadas no objetivo, sem alterar os valores originais.

## Integração com Chat e Prompts

A ação **Perguntar no Chat** abre a `AIBrainActivity` com texto pré-preenchido por `BrainChatContext`. O usuário ainda precisa tocar em **Perguntar**. Isso transporta contexto sem duplicar o motor de conversa e sem execução automática.

A ação **Criar prompt para esta IA** abre `CriadorPromptsActivity` com `EXTRA_COMANDO`. A geração continua no fluxo existente, que utiliza `PromptGenerationSpec`, `PromptGenerationSpecBuilder`, `ContextualPromptGenerator`, `PromptRoomRepository` e a biblioteca de prompts.

A abertura de uma recomendação produzida pelo Prompt Builder também passou a usar `IAOpenContract` e `IAUrlResolver`. Assim, os fluxos de Brain, Prompt Builder e Browser mantêm a mesma política de abertura.

## Performance e estados

O `MainViewModel` mantém busca, categoria, acesso, capacidade, plataforma, ordenação, favoritos e catálogo completo durante mudanças de configuração. Os resultados publicados continuam paginados em blocos de 20 itens.

A sincronização continua sendo feita pelo `CatalogoRepository.carregarCatalogoSincronizado`. Atualizações remotas e curadas são mescladas no mecanismo existente. Dados locais de favoritos, histórico, prompts e relacionamentos Room não são removidos pela nova UI.

A home possui estados distintos para carregamento, catálogo vazio, resultado vazio e erro. O estado vazio de busca orienta o usuário a ajustar os filtros. O erro de catálogo permanece acompanhado da mensagem de falha existente; a recuperação por atualização continua disponível quando configurada.

## Testes

Os testes de `BrainDiscoveryEngine` cobrem busca por nome, categoria, capacidade derivada e descrição; ausência de resultados; filtros unitários e combinados; remoção de filtros; ranking; desempate por favorito; ausência de notas; justificativas e comparação com lacunas.

Os testes anteriores de `RecomendadorIA`, `MainViewModel`, `LocalAIRouter`, `IAOpenContract` e Prompt Builder foram preservados. A fase não reabre nem altera o workflow E2E.

## Arquivos principais

| Arquivo | Responsabilidade |
|---|---|
| `app/src/main/java/com/aibrain/app/brain/BrainDiscovery.kt` | Busca, filtros, recomendação e comparação |
| `app/src/main/java/com/aibrain/app/viewmodel/MainViewModel.kt` | Estado da home e paginação |
| `app/src/main/java/com/aibrain/app/MainActivity.kt` | Dashboard e eventos da home |
| `app/src/main/java/com/aibrain/app/view/DetalheIAActivity.kt` | Detalhes e ações de uma IA |
| `app/src/main/java/com/aibrain/app/view/CompararIAsActivity.kt` | Tabela de comparação |
| `app/src/main/java/com/aibrain/app/brain/IACapabilityRegistry.kt` | Integração do catálogo com o Room operacional |
| `app/src/main/java/com/aibrain/app/brain/AbridorIARecomendada.kt` | Abertura segura de recomendação existente |
| `app/src/test/java/com/aibrain/app/brain/BrainDiscoveryTest.kt` | Testes da nova camada pura |

## Fora do escopo desta fase

Não foram implementadas coleções novas, campos sintéticos de plataforma, avaliações externas, preços inventados ou capacidades que não aparecem no catálogo/Room. Também não houve migração para Compose ou Navigation 3, alteração do WebView externo, login automático, envio automático de prompts ou reabertura do E2E.

## Referências

[1]: design-system.md "Design System IaBrain"
