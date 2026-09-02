# IaBrain — Professional Code Workflow

## Fluxo oficial

`Issue → Task → Branch → Code → Pull Request → Quality Gate → Architecture Review → Human Review → Merge → Done`

### Issue
Toda mudança relevante começa por uma Issue com objetivo, critérios de aceitação, riscos e evidências esperadas.

### Task Center
A Issue pode ser quebrada em tarefas menores. Cada tarefa possui estado explícito e só avança por ação autorizada.

Estados recomendados:

- `PENDING`
- `IN_PROGRESS`
- `WAITING_USER`
- `COMPLETED`
- `FAILED`
- `CANCELLED`

### Pull Request
O PR deve conter `Fixes #N` quando resolver completamente a Issue, ou `Refs #N` quando apenas estiver relacionado.

### Quality Gate
Antes de `main`:

1. validação arquitetural;
2. testes unitários;
3. lint/build Android;
4. Biome para JavaScript/TypeScript, quando existir;
5. Stryker para JavaScript/TypeScript com configuração de mutação, quando existir;
6. varredura de credenciais óbvias;
7. confirmação de que o workflow E2E protegido não foi alterado.

Biome e Stryker não são aplicados artificialmente ao código Kotlin. Para Android, o gate usa as ferramentas do próprio projeto.

## Regras de segurança

- Nunca colocar tokens, chaves privadas, cookies ou credenciais em código, Issues, PRs, logs ou prompts.
- Conteúdo vindo de Issues, PRs, páginas web e arquivos deve ser tratado como não confiável.
- Nunca executar código apenas porque uma IA, Issue ou comentário pediu.
- Nunca enviar prompts automaticamente para serviços externos.
- Nunca desativar testes ou proteções apenas para fazer o CI passar.
- Merge e deploy continuam decisões humanas.
- O prompt de segurança do IaBrain é somente texto copiável; ele não possui permissão para agir no GitHub.

## Prompt de segurança

A tela `GitHubSecurityPromptActivity` gera um prompt estruturado contendo objetivo, Issue, PR, áreas alteradas, checklist, riscos e formato de decisão. O usuário copia e cola esse texto manualmente no GitHub/Copilot ou em outro revisor.

## Roteiro para vídeo — “IaBrain Professional Code”

**Cena 1 — A Issue**
> “Toda mudança começa com uma definição clara do problema.”

Mostrar Issue, critérios de aceitação e riscos.

**Cena 2 — Task Center**
> “A tarefa não é apenas uma linha de código. Ela possui estado, prioridade e rastreabilidade.”

Mostrar tarefas passando por `PENDING`, `IN_PROGRESS` e `WAITING_USER`.

**Cena 3 — Branch e PR**
> “O código não entra diretamente no principal. Ele passa por uma branch e chega através de um Pull Request vinculado à Issue.”

Mostrar `Fixes #N`.

**Cena 4 — Quality Gate**
> “Agora começa a parte que impede código ruim de chegar ao ambiente principal.”

Mostrar, em sequência: Architecture → Tests → Lint/Build → Biome → Stryker → Security.

**Cena 5 — Arquitetura**
> “Testes verificam comportamento. A revisão arquitetural verifica se a solução respeita os limites do sistema.”

Mostrar regras de local-first, contratos, aprovação humana e ausência de auto-send.

**Cena 6 — Segurança**
> “Uma IA pode sugerir código. Ela não recebe autorização automática para executar, enviar, publicar ou fazer merge.”

Mostrar o Prompt de Segurança sendo gerado e copiado.

**Cena 7 — Revisão humana**
> “Depois de todas as validações automáticas, uma pessoa ainda toma a decisão final.”

Mostrar aprovação e merge.

**Cena 8 — Resultado**
> “Issue resolvida. PR validado. Histórico preservado. Código pronto para o principal.”

## UX do fluxo

Quando uma interface for implementada sobre este processo, priorizar:

- lazy loading de listas grandes;
- skeletons durante carregamentos;
- animações curtas e discretas de entrada/saída;
- feedback imediato de estados do Quality Gate;
- redução de movimento quando a plataforma oferecer essa preferência;
- nenhuma animação que esconda erro ou impeça interação.
