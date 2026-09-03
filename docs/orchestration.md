# Orquestração adaptativa

O IaBrain mantém `ia_catalogo.json` e o catálogo Room como fonte de informações. O catálogo não executa providers.

```text
catálogo → IACapabilityRegistry → LocalAIRouter → ProviderGateway → IA local/API
```

O núcleo em `brain/Orchestration.kt` fornece:

- `BrainPlanner`, planner local e determinístico;
- `OrchestrationPlan` e `OrchestrationTask` com dependências;
- validação de IDs, dependências e ciclos;
- `OrchestrationRouter`, adaptador do `LocalAIRouter` para tarefas;
- `ContextManager` com limite de contexto;
- `OrchestrationPolicyGuard` para bloquear possíveis segredos;
- `ProviderGateway`, contrato sem dependência de API específica;
- `OrchestrationValidator` com estados de sucesso, falha, revisão, bloqueio e intervenção humana;
- `TaskEngine` com retries controlados, dependências, estados adaptativos e histórico;
- `OrchestrationHistory` para eventos factuais da execução.

O `TaskEngine` não é uma fila linear. Ele aceita retorno por `RETRYING`, pausa em `WAITING_HUMAN`, sinaliza `NEEDS_REVISION` para o planner e bloqueia tarefas sem candidato compatível. O host permanece dono das transições; providers retornam apenas dados.

## Integração futura com Room

As entidades atuais de workflows, execuções, histórico e validações devem ser estendidas antes de criar tabelas duplicadas. A primeira implementação usa contratos puros e histórico em memória para permitir testes sem Android, rede ou provider real. A persistência Room e o `ProviderGateway` concreto devem entrar em fases separadas com migrations explícitas.

## Segurança

Nenhuma instrução retornada por provider, prompt ou arquivo de projeto altera políticas do host. Providers concretos devem ficar atrás do `ProviderGateway`; a UI não deve chamar SDKs diretamente. A política pode bloquear rede, contexto sensível, custos, ferramentas, tempo e operações que exigem aprovação.
