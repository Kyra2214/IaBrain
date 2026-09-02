# AIBrainFullFlowE2ETest

A suíte `AIBrainFullFlowE2ETest` valida a integração interna do IaBrain usando o catálogo local e o banco Room compartilhado pela aplicação. O cenário usa uma IA existente no asset `ia_catalogo.json`, resolve uma pergunta de texto livre para o comando real `/implement`, executa o `LocalAIRouter`, gera um prompt contextual e cria um `IAOpenContract`.

O contrato é resolvido pelo `IAUrlResolver`, que valida a URL oficial fornecida pelo catálogo. Em seguida, o teste abre a `BrowserActivity` pelo intent real, verifica os extras recebidos e envia um segundo intent pela rota `singleTask` para confirmar que uma nova aba é adicionada sem destruir a anterior.

O prefill é validado de forma condicional. Como o `PrefillAdapterRegistry` atual não possui adaptadores confirmados, o cenário esperado é `PrefillCapability.UNKNOWN`, `canPrefillPrompt = false` e `BrowserOpenMode.OPEN_ONLY`: o prompt permanece disponível para revisão e cópia pelo usuário.

O E2E não acessa login, API, site de IA, internet para decidir o resultado e não automatiza nenhum botão de envio. O fluxo termina com a IA aberta, o prompt preparado e o controle mantido pelo usuário.

## Fluxo coberto

```text
Pergunta
→ TextoLivreIntent
→ /implement
→ RoomCommandResolver
→ IACapabilityRegistry / catálogo Room
→ LocalAIRouter / RoutingDecision
→ PromptGenerationSpecBuilder
→ ContextualPromptGenerator
→ IAOpenContract
→ IAUrlResolver
→ BrowserActivity
→ nova aba
→ preservação da aba anterior
→ prefill somente quando houver suporte confirmado
→ sem envio automático
```

A execução CI utiliza `./gradlew connectedDebugAndroidTest --stacktrace`, que inclui esta suíte e as demais classes do source set `app/src/androidTest/`.
