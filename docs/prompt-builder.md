# Prompt Builder 2.0

## Objetivo

O Prompt Builder é o espaço central para criar, editar, reutilizar, duplicar e preparar prompts para uso manual em uma IA. A implementação evolui o `CriadorPromptsActivity` e mantém a Biblioteca, os favoritos, o histórico, o catálogo, o roteador e o gerador contextual existentes.

> O Builder prepara conteúdo para revisão humana. Ele não envia prompts automaticamente para uma IA externa.

## Arquitetura

```text
Ideia / Chat / Brain / Biblioteca
             ↓
CriadorPromptsActivity
             ↓
PromptBuilderDraft
             ↓
Variáveis + preview
             ↓
PromptBuilderDraft.toPrompt()
             ↓
PromptDadosLocaisRepository
             ↓
BibliotecaActivity / DetalhePromptActivity
             ↓
IAOpenContract → IAUrlResolver → BrowserActivity
```

O `PromptBuilderDraft` é um estado editável em memória. Ele não constitui uma biblioteca ou um sistema de persistência paralelo. O resultado salvo é um `Prompt` no armazenamento local já usado pelo projeto.

O `ContextualPromptGenerator` continua sendo o gerador determinístico dos prompts derivados do Chat e do fluxo de comandos. O Builder apenas recebe, organiza, edita e renderiza esse conteúdo.

## Modos de criação

O editor suporta três formas de escrita. O usuário pode usar somente campos estruturados, somente o campo de texto livre ou combinar os dois.

| Entrada | Comportamento |
|---|---|
| Novo prompt | Cria um draft vazio com título, objetivo, contexto, tarefa, restrições e formato opcionais |
| Texto livre | Preserva o texto escrito pelo usuário e permite placeholders `{{NOME}}` |
| Texto estruturado | Renderiza seções nomeadas de objetivo, contexto, tarefa, restrições e formato |
| Resultado do Chat | Recebe texto contextual, objetivo, comando e IA recomendada por Intent |
| Prompt da Biblioteca | Abre o mesmo prompt para edição |
| Duplicação | Abre o mesmo conteúdo com novo ID e título de cópia |
| IA do Brain | Abre o Builder com a IA selecionada como destino quando essa informação existe |

O usuário não precisa preencher todos os campos. Um texto livre pode ser salvo sem objetivo estruturado.

## Modelo profissional

`Prompt` recebeu campos estruturados opcionais sem quebrar prompts antigos. Os campos são `objetivo`, `contexto`, `tarefa`, `restricoes`, `formatoSaida`, `iaDestinoId`, `iaDestinoNome` e `comandoRelacionado`.

O campo `template` continua sendo o texto final armazenado e copiado pela Biblioteca. O Builder gera esse texto a partir do texto livre e das seções estruturadas. O título, a categoria, a descrição, as tags e o nível permanecem no mesmo modelo usado pela Biblioteca.

A data de criação é preservada em edições. Um novo prompt recebe a data atual. Uma duplicação recebe um novo ID e não altera o original.

## Variáveis

Placeholders devem usar o formato `{{NOME}}`. A detecção é feita automaticamente ao editar o texto e também pode ser acionada pelo botão **Detectar variáveis**.

Os nomes são normalizados para maiúsculas e espaços externos são removidos. Placeholders repetidos produzem uma única variável. O usuário informa valores no campo de variáveis, usando uma entrada por linha no formato `NOME=valor`.

A substituição ocorre somente na prévia. O template original permanece intacto. Valores vazios mantêm o placeholder visível, permitindo identificar pendências antes da cópia.

| Situação | Resultado |
|---|---|
| `{{NOME}}` sem valor | `{{NOME}}` permanece na prévia |
| `{{NOME}}` com `NOME=Alexandre` | `Alexandre` aparece na prévia |
| Placeholder repetido | Uma variável única é exibida |
| Variável removida | O nome deixa de ser administrado pelo draft |
| Nome alterado | O nome é normalizado e o valor é transferido |

## Preview

A seção **Prévia do prompt** mostra exatamente o conteúdo que será copiado ou entregue ao navegador. A prévia combina o texto livre com as seções estruturadas quando ambas existem.

A troca de variáveis não modifica o campo original. O usuário pode editar novamente o template e gerar uma nova prévia. Quando nenhum conteúdo foi informado, o Builder exibe um estado vazio e não salva um prompt sem conteúdo.

## IA de destino e capacidades

A seleção de IA é opcional. Quando presente, ela usa nomes e IDs do catálogo sincronizado. O Builder não mantém lista própria de provedores e não inventa capacidades.

O destino recebido do Chat ou do Brain é preservado durante a edição. Alterar a IA de destino não altera silenciosamente o conteúdo do prompt. A indicação de compatibilidade futura deve continuar consultando `IACapabilityRegistry`; esta fase não replica seu registro.

Se a IA não estiver disponível, o usuário continua podendo editar, salvar e copiar. O botão **Abrir IA** informa a indisponibilidade sem bloquear o editor.

## Salvamento e Biblioteca

`PromptDadosLocaisRepository` recebeu `salvarOuAtualizarPrompt`. O método substitui o prompt quando o ID já existe e insere um novo item quando o ID é novo.

A `BibliotecaActivity` agora combina os prompts do asset embutido com os prompts locais. Um prompt local com o mesmo ID substitui a versão embutida na apresentação. A pesquisa, os filtros por categoria, a ordenação por uso, favoritos e histórico continuam sob responsabilidade da Biblioteca existente.

Favoritos são preservados por ID. Copiar e compartilhar continuam registrando uso pelo `PromptDadosLocaisRepository`. O histórico não é duplicado pelo Builder.

## Editar e duplicar

A tela de detalhe oferece **Editar prompt**, **Duplicar prompt** e **Abrir IA**. Editar mantém o ID e a data de criação. Duplicar cria um draft sem ID, com título sinalizado como cópia, e salva como prompt independente.

A edição ocorre no Builder, não diretamente sobre o asset imutável. Isso permite que prompts embarcados sejam personalizados localmente sem alterar o arquivo do APK.

## Integração com Chat

Quando o Chat conclui uma recomendação, o botão **Criar prompt** abre o Builder com:

- texto contextual gerado pelo `ContextualPromptGenerator`;
- objetivo original;
- comando relacionado;
- ID e nome da IA selecionada.

O usuário pode revisar o conteúdo, preencher variáveis, alterar a estrutura, salvar ou abrir a IA. O Chat não envia o prompt automaticamente.

## Integração com Brain e Browser

O caminho de abertura é único:

```text
Prompt Builder
      ↓
IAOpenContract
      ↓
IAUrlResolver
      ↓
BrowserActivity
      ↓
Nova aba do Browser 2.0
```

O Builder nunca monta URL. A resolução valida o endereço oficial no catálogo e determina capacidades de prefill. Sem confirmação de prefill, o contrato permanece em `OPEN_ONLY`.

Abrir a IA preserva o prompt no contrato para cópia e eventual evolução futura. A tela não usa JavaScript, seletores frágeis, login automatizado, submit ou clique automático.

## Estados da interface

O editor utiliza estados explícitos. O estado vazio orienta o início da escrita. Durante o salvamento, o botão é desabilitado e exibe **Salvando…**. Sucesso e falha são comunicados por Snackbar. Variáveis vazias continuam visíveis na prévia, sem impedir a edição ou o salvamento.

A ausência de IA de destino é tratada como informação contextual. Ela não impede criar, editar, salvar ou copiar.

## Ciclo de vida e rascunho

O draft atual permanece na Activity enquanto o editor está aberto. O armazenamento persistente só é alterado por uma ação explícita de salvar ou pelo fluxo conversacional legado que já salvava o resultado entregue.

Não foi criado um novo mecanismo de rascunho em `SharedPreferences`. O projeto não possuía um contrato de draft persistente que pudesse ser reutilizado sem criar uma segunda infraestrutura. A saída segura desta fase é preservar o conteúdo dentro da Activity e persistir somente mediante ação do usuário.

## Segurança e limites

O Builder nunca afirma que um prompt foi enviado. Copiar apenas coloca o texto na área de transferência. Abrir IA apenas cria uma nova aba pelo Browser 2.0.

O conteúdo do prompt não é executado pelo aplicativo. Variáveis são substituições textuais locais. Nenhum campo é usado para executar comando, URL interna ou código.

## Testes

`PromptBuilderDraftTest` verifica detecção sem duplicação, normalização, edição e remoção de variáveis, renderização estruturada, preservação do template original e independência de IDs em duplicações.

A validação de integração usa os mesmos comandos Gradle das fases anteriores: testes unitários, compilação de testes Android, empacotamento debug e `git diff --check`.

## Referências

[1]: design-system.md "Design System IaBrain"
[2]: brain.md "Brain 2.0 — Central de descoberta de IAs"
[3]: chat-orchestration.md "Chat 2.0 — Orquestração inteligente"
[4]: browser.md "Navegador 2.0 — Multi-tab e experiência de uso"
