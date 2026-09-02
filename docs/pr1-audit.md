# Auditoria técnica — PR #1 — Projetos

**Repositório:** `Kyra2214/IaBrain`  
**PR:** [#1](https://github.com/Kyra2214/IaBrain/pull/1)  
**Branch auditada:** `feat/projects-workspace`  
**Commit de entrada:** `f9b1a5d feat: add local projects workspace`

## Resumo

O PR foi auditado como um todo, incluindo as alterações em componentes existentes, o domínio de projetos, Room, navegação global, integração com Chat/Brain/Browser/Prompts, importação ZIP, validação, CI Standard e o estado GitHub.

A implementação é consistente com o escopo da fase: cria uma área local de Projetos, registra contribuições e metadados do workspace, detecta conflitos por caminho/hash, classifica validações honestamente e mantém GitHub como integração opcional. Não há execução automática de código, push automático, merge automático, autenticação GitHub ou alteração do workflow E2E.

Foram encontrados e corrigidos cinco problemas reais de robustez/regressão introduzidos no PR. Após as correções, os testes unitários, a compilação dos testes Android, o empacotamento do APK e `git diff --check` passaram.

## Pontos positivos

A nova área reutiliza `ProjetoRepository`, `SalvarProjetoCompletoUseCase`, `AppDatabase`, Room, `GlobalNavigation` e o fluxo existente de recomendação. Não foi criado um catálogo paralelo, outro router, outro sistema de prompts, outra BrowserActivity ou outro histórico geral.

A migration 7→8 é aditiva: cria as tabelas novas e seus índices sem alterar ou apagar as tabelas anteriores. O estado GitHub é armazenado separadamente e aceita o estado desconectado, portanto o projeto local não depende de GitHub.

A importação calcula SHA-256, registra tamanho e origem, rejeita entradas inseguras e não extrai nem executa o conteúdo do ZIP. A análise de contribuições distingue novo, modificado, igual e conflito quando fontes diferentes entregam hashes incompatíveis.

O relatório de validação não confunde detecção estrutural com execução. A presença de testes ou de um arquivo de build não vira aprovação de teste/build. Ambiente e CI remoto permanecem como `NÃO EXECUTADO` ou `DEPENDE DE AMBIENTE EXTERNO`.

## Auditoria por área

| Área | Resultado |
|---|---|
| Projetos | Dashboard e detalhe integrados ao domínio local existente; GitHub opcional |
| Room / migration | Migration 7→8 aditiva, entidades e DAOs compatíveis; sem perda observada |
| ZIP | SHA-256, arquivos vazios/binários e entradas aninhadas são processados; traversal corrigido |
| SHA-256 | Calculado sobre os bytes efetivamente lidos do entry |
| Proveniência | Contribuição registra fonte/nome/data; arquivos preservam origem; correção aplicada |
| Conflitos | Detectados e registrados por caminho/hash; não há alegação de merge semântico |
| Validação | Estados locais, de ambiente e remotos separados honestamente |
| CI Standard | Perfis Android/Kotlin, React/TypeScript e Python, sem pipeline universal fixa |
| GitHub | Apenas modelo/estado opcional; não há autenticação, push ou PR automático no código do app |
| Segurança | Nenhuma execução automática; ZIP traversal corrigido |
| Navegação | Projetos adicionado como quinto destino sem alterar os quatro anteriores |
| Chat / Brain / Prompts | Reutiliza fluxos existentes; criação de projeto usa `CriarComIAActivity` e recomendador existente |
| Browser | Nenhum novo mecanismo de abertura de IA foi criado |
| Design System | Usa cores semânticas e superfície de card existente; há um warning de API Android deprecated |
| Testes | Testes específicos adicionados e suíte executada |

## Problemas encontrados e correções

### 🟠 HIGH — Caminhos Windows não eram rejeitados

**Arquivo:** `app/src/main/java/com/aibrain/app/brain/ProjetoWorkspace.kt`  
**Localização:** `ZipWorkspaceImporter.importar`, validação do caminho do entry.

A validação original rejeitava `/tmp/arquivo` e segmentos exatamente iguais a `..`, mas não rejeitava uma entrada com letra de unidade no formato `C:\arquivo`. Como o separador é normalizado antes da validação, isso poderia ser tratado como caminho relativo pelo fluxo de extração futuro.

**Correção:** rejeitar caminhos com padrão `^[A-Za-z]:/.*`, além de caminhos absolutos e qualquer segmento `..`. Foram adicionados testes para `C:\arquivo.txt` e `a/../../arquivo.txt`.

### 🟡 MEDIUM — Arquivos ausentes eram classificados como `IGUAL`

**Arquivo:** `app/src/main/java/com/aibrain/app/brain/ProjetoWorkspace.kt`  
**Localização:** `AnalisadorWorkspace.comparar`.

Arquivos presentes na base, mas ausentes em todas as contribuições, eram adicionados ao resultado como `IGUAL`. Isso contradizia o domínio de mudanças e ocultava uma remoção.

**Correção:** a classificação foi alterada para `TipoMudanca.REMOVIDO`, com teste de regressão específico.

### 🟡 MEDIUM — Collectors duplicados no dashboard

**Arquivo:** `app/src/main/java/com/aibrain/app/view/ProjetosActivity.kt`  
**Localização:** `onCreate`/`onResume` e `observarProjetos`.

A Activity iniciava um collector em `onCreate` e outro a cada `onResume`. Ao retornar repetidamente para a tela, múltiplos collectors poderiam reconstruir a mesma lista, consumir recursos e causar comportamento duplicado.

**Correção:** a coleta foi vinculada a `repeatOnLifecycle(Lifecycle.State.STARTED)` e o collector extra de `onResume` foi removido.

### 🟢 LOW — Proveniência de arquivo era reduzida a `ZIP`

**Arquivo:** `app/src/main/java/com/aibrain/app/view/ProjetoDetalheActivity.kt`  
**Localização:** fluxo de importação ZIP.

A contribuição preservava o nome do arquivo recebido, mas os `ArquivoWorkspace` eram criados com origem fixa `ZIP`. Isso não perdia a contribuição persistida, mas reduzia a informação exibida por arquivo.

**Correção:** o nome do arquivo selecionado é passado como origem do importador e continua armazenado também no registro da contribuição.

### 🟢 LOW — Activity acessava DAO diretamente

**Arquivo:** `app/src/main/java/com/aibrain/app/view/ProjetoDetalheActivity.kt`  
**Localização:** carregamento do projeto.

A tela usava `AppDatabase.getInstance(...).projetoDao().buscar(...)` diretamente, enquanto já existia `ProjetoRepository` para essa responsabilidade.

**Correção:** a Activity passou a usar `ProjetoRepository.buscar`, mantendo o acesso ao Room no repository.

### 🟢 LOW — Número de integração fixo em `1`

**Arquivos:** `ProjetoWorkspaceDaos.kt`, `ProjetoWorkspaceRepository.kt`, `ProjetoDetalheActivity.kt`.

Cada importação pelo detalhe usava o número fixo `1`, tornando incorreto o conceito de sessões numeradas após a segunda integração.

**Correção:** o DAO calcula `MAX(numero)` por projeto e o repository cria a próxima sessão de forma incremental. A correção não implementa merge nem altera o escopo remoto.

## Room e migration

A versão anterior era 7 e a nova é 8. A migration cria sete tabelas novas: contribuições, arquivos do workspace, integrações, validações, histórico, perfis CI e estado GitHub. Os campos anuláveis de GitHub correspondem corretamente ao estado desconectado e os campos de listas usam `RoomConverters` já existente.

As chaves primárias são coerentes: IDs nas entidades de evento e chave composta em arquivos por contribuição/caminho. Índices por `projetoId`, data e caminho foram criados. Não foi encontrada operação de remoção de projeto no PR que pudesse produzir órfãos em runtime; a ausência de foreign keys é uma limitação do modelo atual, não uma regressão demonstrada nesta fase.

A migração é aditiva e não altera dados anteriores. O schema exportado para a versão 8 foi gerado pelo build.

## ZIP e segurança

O importador lê entradas com `ZipFile`, normaliza barras, rejeita caminho absoluto, letra de unidade, segmentos `..` e caminho vazio, e então calcula SHA-256 sobre os bytes lidos. Não cria diretórios, não escreve os entries no workspace e não executa scripts, binários ou comandos recebidos.

A tela copia o ZIP selecionado apenas para um arquivo temporário em `cacheDir` para análise e não extrai código arbitrário. A importação de conteúdo é limitada a metadados e hashes nesta fase.

## Conflitos e limites reais

O código detecta conflito quando o mesmo caminho aparece em contribuições com hashes diferentes e registra o caminho e as fontes. Também registra a sessão de integração e seu status.

Ele **não** faz merge textual, resolução interativa, análise de AST, checagem real de imports, type checking ou merge semântico. Isso é correto para o escopo auditado e permanece documentado como limite, não como funcionalidade entregue.

## Validação e CI Standard

A presença de um arquivo de teste só produz `Testes disponíveis: OK` no sentido de presença estrutural; não afirma que o teste foi executado. Build e testes de ambiente permanecem `NÃO EXECUTADO`. CI remoto permanece `DEPENDE DE AMBIENTE EXTERNO`.

Os perfis CI são dados de configuração adaptáveis por stack. Os comandos configurados não são executados automaticamente pelo APK e não criam novos runners ou pipelines.

## Integração GitHub

O PR modela `StatusGithub` e persiste repositório, branch, última sincronização e mensagem de erro, mas não implementa autenticação, leitura remota, push, criação de branch no GitHub, CI remoto ou PR automático. Essa diferença está documentada e não é tratada como falha do PR.

## Itens futuros / fora do escopo

- Integração GitHub real e autenticação segura por GitHub App/fine-grained permissions.
- Obtenção de repositório remoto e sincronização.
- Execução de runners locais/externos por adaptador.
- Merge textual, resolução de conflitos e merge semântico.
- Análise de AST, imports, tipos, XML e dependências.
- CI remoto e auditoria de resultado.
- E2E: **FORA DO ESCOPO DESTA AUDITORIA / PENDENTE PARA FASE FUTURA**.
- Workflow `.github/workflows/android-e2e.yml`: não alterado.

Nenhum desses itens foi implementado durante a auditoria.

## Testes e validações executados

| Comando | Resultado | Observação |
|---|---|---|
| `./gradlew testDebugUnitTest --no-daemon` | **BUILD SUCCESSFUL** | Suíte unitária executada, sem falhas |
| `./gradlew compileDebugAndroidTestKotlin --no-daemon` | **BUILD SUCCESSFUL** | Compilação Android executada |
| `./gradlew assembleDebug --no-daemon` | **BUILD SUCCESSFUL** | APK debug empacotado |
| `git diff --check` | **Passou** | Nenhum erro de whitespace |

A suíte inclui os testes existentes de Brain, Chat, Prompt Builder, Browser e Projeto, além dos testes de workspace para conflitos, proveniência, remoções, ZIP, SHA-256, validação e CI Standard. O XML produzido pelo Gradle registra **110 testes, 0 skips, 0 falhas e 0 erros**; nenhuma falha foi reportada pelo comando.

Warnings relevantes:

- `startActivityForResult` está deprecated em `ProjetoDetalheActivity`; não impede compilação nem introduz falha funcional nesta auditoria.
- O processador Room reporta opções KAPT não reconhecidas (`room.schemaLocation`, `kapt.kotlin.generated`, `room.incremental`); o build passa e o schema da versão 8 foi gerado. Isso deve ser tratado em uma manutenção futura de configuração, não é bloqueador do PR.

## Veredito final

# MERGE

O PR está tecnicamente adequado para incorporação após as correções de segurança, classificação de mudanças, lifecycle, proveniência e numeração de integrações realizadas nesta auditoria. As limitações restantes são conhecidas, explicitamente documentadas e pertencem a fases futuras. O PR não implementa nem promete silenciosamente GitHub completo, merge automático, execução remota, merge semântico ou E2E.
