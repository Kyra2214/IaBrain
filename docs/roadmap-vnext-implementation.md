# IaBrain — Roadmap VNext Implementation

A próxima camada do IaBrain é construída sobre contratos compartilhados e armazenamento local-first.

## Fluxo dos 12 blocos

1. Integração de contribuições: revisão explícita por arquivo, staging e rollback.
2. Unified Prompt Actions: copiar, salvar e abrir reutilizam o mesmo contrato.
3. Prefill assistido: somente quando a capacidade estiver CONFIRMED; falha retorna ao fluxo copiar/abrir.
4. Workspace de projeto: contexto, arquivos, contribuições e validações permanecem associados ao projeto.
5. CI Standard: resultados separados em LOCAL, ENVIRONMENT e REMOTE, sem declarar como executado o que depende de ambiente externo.
6. GitHub Workspace: branch → commit → CI → revisão → aprovação; sem merge automático.
7. Chat contextual: recebe contexto de projeto, arquivos selecionados, contribuições e memória recente.
8. Multi-IA: candidatos são ordenados deterministicamente e toda execução externa exige aprovação do usuário.
9. Skills/Workflows: passos reutilizáveis, orientados ao projeto, sem envio automático.
10. Memória de projeto: decisões, arquitetura, problemas, soluções e preferências persistem localmente.
11. Contexto entre abas: snapshot de abas e prompt pode ser associado à origem sem destruir a sessão do navegador.
12. Task Center: tarefas possuem projeto, prioridade, estado, detalhe e histórico local.

## Guardrails

- IaBrain nunca envia prompt automaticamente.
- O navegador não possui catálogo paralelo e não cria URLs de IA.
- Prefill não é assumido por DOM genérico; exige capacidade confirmada e adaptador real.
- Código recebido de contribuição não é executado cegamente.
- GitHub é opcional para o funcionamento local.
- E2E continua fora do critério funcional enquanto a infraestrutura do emulador estiver instável.
- Nenhuma alteração é feita em `.github/workflows/android-e2e.yml` nesta etapa.

## Persistência VNext

Room foi evoluído para a versão 9 com entidades para tarefas, memória, skills, snapshots de contexto do navegador e histórico de ações de prompts. Acesso é concentrado em `WorkspaceVNextRepository`.

## Núcleo

`IaBrainWorkspaceOrchestrator` concentra os contratos de ações de prompt, resultado de prefill, relatório de validação, plano multi-IA, execução de skill, memória e tarefas. Isso evita criar routers, catálogos ou mecanismos paralelos.
