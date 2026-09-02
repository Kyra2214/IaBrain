# Chat 2.0 — Orquestração inteligente

## Objetivo

O Chat é o ponto central de entrada para tarefas do IaBrain. O usuário pode escrever uma solicitação em linguagem natural sem conhecer nomes de IAs, categorias, capacidades, comandos ou regras de ranking.

A tela não implementa um segundo mecanismo de inteligência. Ela coordena os componentes existentes e apresenta uma decisão compreensível. O catálogo sincronizado e o Room continuam sendo as fontes de dados do Brain.

## Fluxo

```text
Pergunta do usuário
        ↓
TextoLivreIntent / SlashCommandParser
        ↓
RoomCommandResolver
        ↓
RoutingRequest com comando e capacidades reais
        ↓
IACapabilityRegistry.candidates()
        ↓
LocalAIRouter.route()
        ↓
RoutingDecision: IA, alternativas, score e motivos
        ↓
Resposta simples + compatibilidade
        ↓
PromptGenerationSpecBuilder.from()
        ↓
ContextualPromptGenerator.generate()
        ↓
IAOpenContract
        ↓
IAUrlResolver.resolve()
        ↓
BrowserActivity
```

O `RoomCommandResolver` dá prioridade ao parser de slash command. Se a entrada não começa com `/`, `TextoLivreIntent` procura uma intenção semântica clara e devolve o slug de um comando já existente. Se não houver correspondência clara, o Chat não escolhe uma IA arbitrariamente.

## Intenção, comando e tarefa

A **intenção** descreve o que o usuário quer fazer. O **comando** é a representação operacional já cadastrada no asset de comandos. A **tarefa** é a interpretação apresentada ao usuário, derivada do comando e das categorias disponíveis.

| Exemplo | Intenção semântica | Comando existente | Capacidades consultadas |
|---|---|---|---|
| Criar aplicativo Android em Kotlin | Implementar | `/implement` | `CODIGO` |
| Pesquisar artigos científicos | Pesquisar | `/research` | `PESQUISA` |
| Criar imagem de dragão medieval | Criar conteúdo visual | `/creative` | `IMAGEM` |
| Escrever currículo profissional | Criar documento | `/document` | `ESCRITA` |
| Analisar uma planilha | Analisar dados | `/analyzedata` | `ANALISE` |

As capacidades são chaves existentes no catálogo de IAs. A ponte entre o comando e essas chaves fica no `RoomCommandResolver`; comandos não incluídos nessa ponte continuam usando as capacidades cadastradas no grafo Room do próprio comando.

## Prioridade de resolução

A ordem de decisão é fixa:

1. `SlashCommandParser` interpreta um comando explícito, como `/implement criar aplicativo Android`.
2. `TextoLivreIntent` identifica uma intenção semântica somente quando há termos suficientemente claros.
3. `RoomCommandResolver` consulta a definição e o grafo de capacidades no Room.
4. Uma entrada ambígua retorna `null` e produz uma solicitação de esclarecimento.

O Chat não transforma termos genéricos, como “uma coisa para minha empresa”, em uma seleção arbitrária. A resposta oferece uma direção útil sem inventar tarefa ou capacidade.

## Ranking e recomendação

O Chat chama `IACapabilityRegistry.candidates()` e `LocalAIRouter.route()`. O router calcula compatibilidade de comando, capacidades exigidas, especialização e métricas locais. A decisão contém a IA selecionada, alternativas, confiança heurística e motivos.

O Chat considera uma recomendação válida quando a decisão possui uma candidata cadastrada e há compatibilidade por comando ou capacidade. A interface não apresenta o score interno nem converte confiança em percentual. A compatibilidade exibida usa rótulos relativos: **Alta**, **Boa** ou **Compatível**.

A recomendação apresenta uma IA principal e no máximo três alternativas provenientes da própria decisão do router. A explicação utiliza os motivos retornados pelo componente existente, como capacidade exigida, comando suportado e ranking determinístico local.

## Catálogo e favoritos

O Chat não copia o catálogo e não cria um banco próprio. O `IACapabilityRegistry` continua consultando o Room e garante a carga do catálogo sincronizado quando necessário. Favoritos permanecem sob responsabilidade do `FavoritosRepository` e dos componentes já usados pelo Brain.

A recomendação visual da tela usa os objetos `IA` presentes no catálogo para renderizar cards. A seleção operacional vem da `RoutingDecision`. O Chat não mantém uma lista alternativa de provedores.

## Prompt contextual

O botão **Criar prompt** materializa a decisão atual por meio de `PromptGenerationSpecBuilder.from(request, decision)`. O `RoutingRequest.rawUserRequest` preserva a pergunta original. O spec preserva o comando, a IA selecionada e as capacidades exigidas.

`ContextualPromptGenerator` gera uma prévia explícita. O prompt pode conter o cabeçalho padrão de desenvolvimento quando o comando caracteriza uma tarefa de desenvolvimento. O usuário pode revisar e copiar o conteúdo. O prompt não é enviado automaticamente.

O prompt é salvo pelo `PromptRoomRepository` no mesmo caminho de biblioteca já existente. Nenhum gerador paralelo foi criado.

## Abertura da IA

A ação **Abrir IA** usa exclusivamente o contrato de abertura:

```text
IAOpenContract → IAUrlResolver → BrowserActivity → BrowserTabManager
```

A tela não monta URL, não escolhe domínio e não injeta JavaScript. O resolver valida HTTPS, busca o endereço no catálogo atual e consulta capacidades de prefill no Room.

Quando o prefill não está confirmado, o contrato permanece em `OPEN_ONLY`. O navegador pode abrir a página e o Chat pode copiar o prompt. O aplicativo não pode clicar em enviar, submeter formulário, automatizar login ou controlar a IA externa.

## Escolher outra IA

O botão **Escolher outra IA** retorna à listagem do Brain existente. O Chat não cria um seletor de IA alternativo. A exploração manual continua disponível pela navegação global e pela home do Brain.

## Estados da interface

Durante o processamento, a interface exibe mensagens curtas: “Entendendo seu pedido…” e “Encontrando a IA mais adequada…”. Após a decisão, exibe a pergunta original, a resposta da IaBrain, a tarefa/comando resolvido, a compatibilidade e os cards de recomendação.

| Estado | Mensagem | Ação disponível |
|---|---|---|
| Catálogo carregando | Indicador de carregamento | Aguardar |
| Pedido ambíguo | “Não consegui interpretar esse pedido” | Reformular ou esclarecer |
| Sem correspondência | “Não encontrei uma IA compatível” | Escolher outra IA / explorar Brain |
| Recomendação encontrada | IA, alternativas e justificativa | Criar prompt, abrir IA ou escolher outra |
| Prompt preparado | Prévia selecionável | Copiar ou abrir IA |
| Falha de abertura | Mensagem do contrato/resolver | Permanecer no app e tentar novamente |

Exceções técnicas não são mostradas ao usuário. O fluxo captura falhas de resolução e exibe estado humano sem stack trace.

## Conversa e contexto

A entrada pré-preenchida por outra tela usa `BrainChatContext`. O campo é carregado sem disparar o processamento automaticamente. O usuário ainda toca em **Perguntar**.

O turno atual mantém a pergunta e o prompt pendente na Activity durante a decisão. A arquitetura existente de histórico e biblioteca continua preservada. Não foi criado um banco de mensagens paralelo nesta fase.

## Acessibilidade e performance

A UI usa os estilos, cores semânticas, áreas de toque e estados definidos pelo Design System. A pergunta permanece selecionável, ações são botões Material e a recomendação não depende somente de cor.

A resolução, consulta ao Room, ranking e geração de prompt ocorrem em `lifecycleScope`. A Activity não bloqueia a Main Thread. O Chat usa o catálogo já carregado e não chama um serviço externo para interpretar a intenção, resolver o comando, ranquear ou gerar o prompt.

## Limites de automação

O Chat prepara decisões e conteúdo para revisão humana. Ele não envia prompt, não clica em controles externos, não executa JavaScript, não automatiza login, não altera páginas externas e não afirma que uma IA é “a melhor do mundo”.

Nenhuma capacidade, avaliação, preço, plataforma ou IA é inventada. Quando a informação não existe no catálogo ou no Room, a interface preserva o estado ausente.

## Testes

`ChatOrchestrationTest` verifica os exemplos de linguagem natural, prioridade do comando explícito, comportamento ambíguo, correspondência por capacidades reais, ranking determinístico, alternativas, preservação da pergunta no prompt e contrato `OPEN_ONLY` sem prefill confirmado.

A suíte E2E permanece estacionada. As validações desta fase priorizam testes unitários, compilação dos testes Android, empacotamento debug e `git diff --check`.

## Referências

[1]: design-system.md "Design System IaBrain"
[2]: brain.md "Brain 2.0 — Central de descoberta de IAs"
