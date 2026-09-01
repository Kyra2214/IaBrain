# IaBrain

Aplicativo Android para descobrir, comparar e organizar ferramentas de inteligência artificial. O IaBrain combina um catálogo pesquisável com favoritos, biblioteca de prompts, coleções, guias práticos, navegador interno e a experiência **Criar com IA**, que transforma uma ideia livre em funções de projeto e recomendações reais do catálogo.

![Tela Criar com IA](docs/images/criar-com-ia.jpg)

## Destaques

### Criar com IA

Descreva uma ideia como “quero criar um aplicativo para gerenciar uma rede de sorveteria” e toque em **Analisar**. O app interpreta o tipo de projeto, plataforma, complexidade e áreas envolvidas; em seguida, organiza recomendações por função, mostra alternativas, acesso, notas, motivos e uma stack final estimada.

A análise funciona localmente, sem backend obrigatório. Nenhum nome é inventado: cada recomendação aponta para um `iaId` existente no catálogo. Quando uma descrição de aplicativo não informa funções técnicas, o sistema sugere funções padrão de **Código, Design, Escrita e Imagem** para evitar resultados vazios.

A interface segue um fluxo de conversa: o texto enviado aparece como mensagem do usuário, a caixa é limpa após a análise e textos longos são exibidos com quebra automática de linha.

### Catálogo tradicional

O catálogo continua sendo o fluxo principal de exploração. É possível pesquisar por nome ou função, filtrar por categoria, avaliação e acesso, ordenar por ranking, popularidade ou novidade, favoritar IAs e abrir seus detalhes no navegador interno.

Também estão disponíveis **Coleções**, **Guias práticos**, **Biblioteca de Prompts**, **Criador de Prompts**, **Assistente de IA/curadoria** e uma área independente para conteúdo restrito.

## Download

Baixe a versão de demonstração na página de [Releases](https://github.com/Kyra2214/IaBrain/releases). O APK de debug é destinado a testes e pode exigir a autorização de instalação de fontes desconhecidas no Android.

## Requisitos

- JDK 17 ou superior;
- Android SDK com API 34;
- acesso à internet para baixar dependências e, durante o uso, sincronizar o catálogo e acessar os serviços selecionados.

## Build e testes

O projeto inclui Gradle Wrapper. Em um clone limpo, execute:

```bash
./gradlew testDebugUnitTest
./gradlew lint
./gradlew assembleDebug
./gradlew assembleRelease
```

O APK de debug é gerado em `app/build/outputs/apk/debug/app-debug.apk`. A versão release possui minificação habilitada; valide-a em um dispositivo ou emulador antes de distribuir.

## Arquitetura da recomendação por projeto

O fluxo **texto → interpretação → funções → consulta ao catálogo → ranking → apresentação** está dividido em responsabilidades reutilizáveis:

| Componente | Responsabilidade |
|---|---|
| `ProjetoIntentParser` | Interpreta texto livre, tipo, plataforma, áreas, complexidade, orçamento e preferência de acesso. |
| `CatalogoQuery` | Filtra apenas itens existentes, ativos e compatíveis com a intenção. |
| `RecomendadorProjeto` | Ranqueia especialização, casos de uso, nota, acesso, compatibilidade e custo. |
| `ProjetoRecommendation` | Representa funções, recomendações, alternativas e stack final. |

Os campos opcionais do modelo `IA` incluem plataforma, modelo de acesso, API, login, status, última verificação e casos de uso. Catálogos JSON antigos continuam válidos por meio de defaults compatíveis.

## Catálogos

O catálogo base fica em `app/src/main/assets/ia_catalogo.json`. Os arquivos de publicação em `para-subir-no-github/` devem ser mantidos sincronizados com os assets. Antes de publicar uma nova versão, incremente `versao`, valide o JSON e confira IDs, URLs HTTPS, categorias, idiomas, acesso e notas.

```bash
python3 scripts/validate_catalog.py
```

## API key da Groq

A chave é opcional e usada somente no Assistente de IA e nos recursos de curadoria e geração assistida. Nunca inclua chaves no código-fonte, assets, logs ou issues. No aplicativo, a chave é tratada como segredo do usuário e armazenada no Android Keystore.

## Segurança do navegador

O navegador interno é restringido a HTTPS, bloqueia conteúdo misto, arquivos locais e domínios fora da allowlist configurada. Capacidades como geolocalização, cookies de terceiros, downloads e upload devem ser habilitadas apenas quando necessárias para o domínio atual.

## Estrutura

- `app/src/main/java/com/aibrain/app/model`: modelos de domínio;
- `app/src/main/java/com/aibrain/app/brain`: parsing e motores de recomendação;
- `app/src/main/java/com/aibrain/app/repository`: leitura, sincronização e composição do catálogo;
- `app/src/main/java/com/aibrain/app/viewmodel`: estado de tela e filtros;
- `app/src/main/java/com/aibrain/app/browser`: abas e WebView;
- `app/src/test`: testes unitários;
- `docs`: changelog, roadmap e imagens de documentação.

## Contribuição

Antes de abrir um pull request, execute testes, lint e build. Mudanças no catálogo devem incluir validação do JSON. Mudanças de segurança ou no WebView devem incluir testes de regressão e uma descrição do impacto.

## Status

O projeto está em evolução ativa. Consulte o [roadmap](docs/ROADMAP.md) e o [changelog](docs/CHANGELOG.md) para o histórico das fases implementadas.

## Arquitetura de dados local-first

Os dados estruturados de projetos, funções, vínculos entre funções e IAs e prompts gerados utilizam Room sobre SQLite:

```text
UI
 ↓
ViewModel
 ↓
Repository
 ↓
DAO
 ↓
Room / SQLite
```

A fundação está em `data/local`, com `AppDatabase`, entidades, DAOs, conversores e repositories separados. O JSON continua sendo a fonte inicial do catálogo e pode ser importado para a tabela local de IAs. A migração foi incremental: favoritos, histórico, catálogo curado e o Prompt Builder legado continuam preservados enquanto os novos dados de projeto passam a ter estrutura SQL.

O banco Room está na **versão 3** e possui as migrations explícitas **1 → 2** e **2 → 3**; não usa `fallbackToDestructiveMigration`. A versão 3 adiciona o contexto persistente do projeto. Futuras versões devem adicionar migrations explícitas sem apagar dados do usuário. A separação de repositories permite acrescentar posteriormente `RemoteDataSource` e sincronização com uma API/PostgreSQL sem reescrever o domínio ou a UI; nenhum backend ou PostgreSQL faz parte desta versão.

## Modelo local e recursos pesados

O IaBrain é **local-first**, mas o APK **não contém o modelo LLM**. O manifesto `app/src/main/assets/heavy_resources.json` descreve o primeiro recurso externo: **Qwen3-0.6B GGUF Q4_0**, com 428.970.080 bytes e SHA-256 validado. O arquivo é baixado sob demanda para `filesDir/resources/llm/`, nunca para `assets/` nem para dentro do APK.

O `HeavyResourceManager` faz download real com progresso baseado em bytes recebidos, tenta retomar downloads HTTP por `Range`, verifica espaço antes de uma integração de UI futura, e só considera o recurso pronto quando tamanho e SHA-256 conferem. Arquivos inválidos são removidos. A versão do manifesto permite atualizar o recurso sem publicar novo APK; uma nova versão deve ser baixada e validada antes de substituir a anterior.

A arquitetura do gerador é:

```text
PromptGenerator
 ├── LocalLLMProvider (Qwen3 via runtime GGUF isolado)
 └── GroqLLMProvider (fallback externo)
```

O Qwen3 é usado inicialmente somente como **prompt engine**. A camada `LocalRuntime` isola a futura implementação llama.cpp compatível com Android/ABI; nenhum runtime experimental foi acoplado ao aplicativo sem validação específica. Se o modelo não estiver instalado ou o runtime local falhar, o `PromptGenerator` usa Groq quando houver chave e conectividade. O Project Brain completo continua fora do escopo.

## Catálogo como grafo de capacidades

O catálogo de comandos permanece persistido no Room e agora possui uma camada de grafo preparada para orquestração futura. A migration atual é a **5**, com migrations explícitas `1 → 2`, `2 → 3`, `3 → 4` e `4 → 5`. A versão 5 adiciona capacidades, relacionamentos tipados, parâmetros, vínculos comando/IA, workflows, handoffs e registros de execução/métricas. O `SlashCommandParser` interpreta comandos como `/research tema="Android offline"` sem criar um segundo motor de chat.

A base atual preserva os **344 comandos** existentes. Não foram criados milhares de registros artificiais: a expansão para 3.000–5.000 comandos deverá ocorrer por curadoria e novos seeds versionados, mantendo pesquisa paginada e consultas leves para a interface.

## LocalAIRouter v1

O `SlashCommandParser` pode encaminhar uma solicitação ao `RoomCommandResolver`, que consulta o comando e suas capacidades persistidas. O `LocalAIRouter` então calcula uma `RoutingDecision` determinística, usando uma `RoutingPolicy` centralizada, score explicável, confiança heurística e até três alternativas. Ele **não executa modelos, não chama Groq, não faz HTTP e não depende de API**; a execução será responsabilidade de um futuro `AIExecutor`.

O roteador usa compatibilidade direta de comando, capacidades, especialidades, qualidade, velocidade, contexto e custo conhecidos. Quando não há candidato, retorna `NO_COMPATIBLE_PROVIDER` em vez de mascarar a ausência como sucesso. O modelo suporta evolução futura para handoff, workflows, métricas e roteamento multi-IA sem duplicar o motor de chat.

## IA Capability Registry v1

O catálogo de IAs pode alimentar o roteamento por meio do `IACapabilityRegistry`: `Room` → capacidades, especialidades e comandos persistidos → `RoutingCandidate` → `LocalAIRouter` → `RoutingDecision`. O Registry acessa o Room; o `LocalAIRouter` permanece puro, determinístico e sem rede, APIs ou execução de providers. A migration atual é a **6**, com a migration explícita `5 → 6` criando `ia_capacidades`. Métricas de qualidade, velocidade e custo usam defaults locais documentados (`0.5`, `0.5`, `0.0`) enquanto não houver medições reais.

## IA Routing Profile v1

Cada IA pode possuir um `IARoutingProfileEntity` persistido no Room com `qualityScore`, `speedScore`, `costScore`, `reliabilityScore`, `contextScore`, `enabled` e `updatedAt`. O `IACapabilityRegistry` transforma esse perfil, as capacidades, especialidades e comandos suportados em `RoutingCandidate`; o `LocalAIRouter` continua puro e determinístico. Scores fora de `0.0..1.0` falham explicitamente. IAs sem perfil recebem defaults identificáveis por `isDefaultProfile`, sem transformar categorias em métricas.

## Geração contextual de prompts

Após a seleção do `LocalAIRouter`, `PromptGenerationSpecBuilder` transforma a solicitação, a `RoutingDecision`, o comando, as capacidades e o contexto em uma especificação explícita. `ContextualPromptGenerator` produz um prompt determinístico específico para a tarefa e para a IA escolhida, sem substituir o chat nem chamar providers. A persistência reutiliza `PromptEntity`, registrando `iaId`, `funcaoId`, `modeloGeracao` e a origem `ROUTER_COMMAND:<comando>` para rastreabilidade.

## Padrão universal de desenvolvimento

Prompts gerados para criação, implementação ou execução de desenvolvimento começam automaticamente com o cabeçalho `MODO DE EXECUÇÃO SILENCIOSA`, seguido das regras universais e das seções `PROJETO`, `FASE`, `MÓDULO`, `SUBMÓDULO`, `OBJETIVO`, `IMPLEMENTAÇÃO`, `CRITÉRIOS DE CONCLUSÃO` e `REGRA`. A detecção é feita no domínio por comando ou objetivo. Prompts comuns, como pesquisa, não recebem esse cabeçalho.
