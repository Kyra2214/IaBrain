# Projetos — workspace local do IaBrain

## Visão geral

A aba **Projetos** transforma o IaBrain em uma camada local de organização, integração e validação de trabalhos produzidos por diferentes IAs. Um projeto pode existir sem GitHub conectado. O GitHub é uma fonte e um destino opcionais, não uma dependência estrutural.

> O IaBrain não executa código recebido de uma IA cegamente. Ele registra a origem, analisa alterações, marca conflitos e informa quais validações realmente foram executadas.

## Modelo de projeto

O projeto base já existente no Room continua sendo a identidade principal. A nova infraestrutura amplia o mesmo banco com informações de workspace.

| Conceito | Persistência | Finalidade |
|---|---|---|
| Projeto | `projetos` | Nome, descrição, plataforma, complexidade e status |
| Função | `projeto_funcoes` | Áreas ou responsabilidades recomendadas |
| IA vinculada | `projeto_ias` | Relação entre função e IA recomendada |
| Contexto | `projeto_contextos` | Objetivo, stack e estado do projeto |
| Contribuição | `projeto_contribuicoes` | Origem, data e status de cada entrega |
| Arquivo de workspace | `projeto_arquivos_workspace` | Caminho, hash, tamanho e proveniência |
| Integração | `projeto_integracoes` | Sessão, fontes, conflitos e resultado |
| Validação | `projeto_validacoes` | Nome, nível, status e detalhes da verificação |
| Histórico | `projeto_historico` | Importação, integração, validação e eventos remotos |
| CI Profile | `projeto_ci_profiles` | Adaptador de validação específico da stack |
| GitHub | `projeto_github` | Estado remoto opcional e branch conhecida |

A migração Room 7→8 cria as novas tabelas sem apagar projetos existentes.

## Entradas de código

O workspace aceita contribuições ZIP e está preparado para contribuições obtidas de um repositório GitHub. Também registra fontes internas do IaBrain, como Chat, Brain, Prompt Builder e Browser, sem exigir que uma IA seja executada por dentro do aplicativo.

O importador ZIP valida caminhos antes de registrar arquivos. Caminhos absolutos e caminhos que tentam sair do pacote com `..` são rejeitados. Cada arquivo recebe hash SHA-256, tamanho e origem. O conteúdo não é sobrescrito silenciosamente.

O fluxo conceitual é:

```text
ZIP ou GitHub
     ↓
Contribuição com proveniência
     ↓
Workspace local
     ↓
Análise por caminho e hash
     ↓
Integração ou conflito explícito
     ↓
Validação
```

## Análise semântica mínima

O `AnalisadorWorkspace` compara a versão base com as contribuições agrupando arquivos por caminho e comparando hashes.

| Situação | Classificação |
|---|---|
| Caminho só aparece na contribuição | Novo |
| Caminho existe e o hash mudou | Modificado |
| Caminho existe e o hash é igual | Igual |
| Mais de uma fonte entrega hashes diferentes para o mesmo caminho | Conflito |
| Mais de uma fonte entrega o mesmo hash | Igual com múltiplas proveniências |

A análise também identifica a presença de documentação, testes, configuração e arquivos de dependência. Detecção de imports quebrados, tipos incompatíveis, XML conflitante e dependências incompatíveis deve ser realizada pelo adaptador de stack ou runner disponível; a tela não declara sucesso sem essa execução.

## Sessão de integração

Uma integração é persistida como uma sessão numerada do projeto. Ela registra as fontes participantes, o status e os caminhos em conflito. Uma sessão com conflito não é marcada como concluída automaticamente.

A intervenção humana é necessária quando fontes diferentes alteram o mesmo arquivo de maneiras incompatíveis. O sistema preserva as contribuições para comparação posterior.

## Validação antes do GitHub

O `ValidadorProjeto` produz um relatório com níveis explícitos.

| Nível | Exemplos | Estado quando não executado |
|---|---|---|
| Local | Estrutura, arquivos, documentação, duplicidade e diff check | `NÃO EXECUTADO` ou `NÃO VERIFICADO` |
| Ambiente | Gradle, Node, Python, banco local, emulator e build | `NÃO EXECUTADO` |
| Remoto | API, OAuth, Firebase, GitHub Actions e CI hospedado | `DEPENDE DE AMBIENTE EXTERNO` |

O relatório não converte ausência de execução em aprovação. Um projeto pode criar branch ou preparar alterações com aviso, mas não pode ser tratado como aprovado em CI remoto quando esse CI não foi executado.

## Dashboard e workspace

O dashboard lista os projetos persistidos localmente e oferece criação de projeto pelo fluxo existente de intenção e recomendação. O detalhe do projeto apresenta identidade, stack, estado do GitHub, contribuições recebidas, quantidade de arquivos, perfil de CI e ações de importar ZIP e validar localmente.

A navegação global passa a conter **Chat → Navegador → Brain → Prompts → Projetos**. A aba Projetos usa a mesma superfície, tipografia, espaçamento e navegação do Design System existente.

## GitHub opcional

O estado GitHub é explícito e pode ser `DESCONECTADO`, `CONECTADO`, `SINCRONIZANDO` ou `ERRO`. O projeto continua funcional em todos esses estados para operações locais.

O fluxo remoto previsto é:

```text
Workspace local
      ↓
Validação local e de ambiente
      ↓
Branch de trabalho
      ↓
Commit auditável
      ↓
GitHub opcional
      ↓
CI remoto
      ↓
Auditoria
      ↓
PR
      ↓
Aprovação humana
      ↓
Merge
```

A implementação desta fase não envia alterações automaticamente para `main`, não armazena PAT em texto e não declara conexão GitHub quando ela não foi configurada. Uma futura integração deve usar permissões mínimas, preferencialmente GitHub App ou fine-grained permissions.

## Segurança

Antes de executar qualquer runner, o sistema deve analisar origem, scripts, comandos perigosos, arquivos sensíveis, workflows e alterações de dependências. Arquivos `.github/workflows`, scripts de shell, Dockerfiles e manifests de dependências devem aparecer como mudanças auditáveis.

O APK não promete compilar qualquer stack. Ele pode realizar análise interna e delegar a validação de ambiente a um runner local ou remoto compatível.

## Limites desta fase

A importação ZIP registra e analisa metadados e hashes, mas ainda não faz merge textual automático de arquivos. A integração GitHub está modelada como estado opcional, sem autenticação ou envio remoto nesta fase. A execução de Gradle, Node ou Python ocorre somente quando um runner compatível estiver disponível; a UI não simula essa execução.

O workflow E2E existente permanece estacionado e não foi alterado.

## Referências

[1]: design-system.md "Design System IaBrain"
[2]: brain.md "Brain 2.0 — Central de descoberta de IAs"
[3]: chat-orchestration.md "Chat 2.0 — Orquestração inteligente"
[4]: browser.md "Navegador 2.0 — Multi-tab e experiência de uso"
[5]: prompt-builder.md "Prompt Builder 2.0"
