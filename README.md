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

O banco está na versão 1 e não usa `fallbackToDestructiveMigration`. Futuras versões devem adicionar migrations explícitas sem apagar dados do usuário. A separação de repositories permite acrescentar posteriormente `RemoteDataSource` e sincronização com uma API/PostgreSQL sem reescrever o domínio ou a UI; nenhum backend ou PostgreSQL faz parte desta versão.
