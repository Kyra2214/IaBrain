# Relatório completo de auditoria do IaBrain

**Data:** 3 de setembro de 2026  
**Repositório:** [`Kyra2214/IaBrain`](https://github.com/Kyra2214/IaBrain)  
**Commit avaliado:** `35ea8ad` — `Fix orchestration cycle test`  
**Escopo:** auditoria estática, validação de catálogo, build Android, testes unitários, E2E instrumentado em GitHub Actions e coleta de artefatos.

## Resumo executivo

A auditoria foi concluída sem executar instruções provenientes de arquivos de projeto ou logs como se fossem autoridade. O código foi compilado com sucesso após a correção de um erro no teste de ciclo da nova orquestração. A suíte unitária passou integralmente no ambiente local e no CI. O APK debug foi gerado com sucesso e os cinco catálogos foram validados.

O E2E instrumentado conseguiu iniciar o aplicativo no emulador remoto e executar o fluxo completo `AIBrainFullFlowE2ETest`, mas encontrou uma falha funcional: o teste esperava `SELECTED` no roteamento e recebeu `NO_COMPATIBLE_PROVIDER`. Portanto, o E2E não deve ser considerado aprovado. O problema aponta para a inicialização/seed do catálogo ou das capacidades no ambiente instrumentado, não para necessidade de instalar um modelo de IA local.

O screenshot final solicitado não foi produzido como imagem válida: a etapa de coleta executou depois que o dispositivo deixou de estar disponível para `adb`, gerando um arquivo PNG vazio. O relatório HTML, XML, logs instrumentados e diagnósticos do emulador foram preservados. Não apresento o arquivo vazio como print.

## Matriz de resultados

| Verificação | Resultado | Evidência |
|---|---:|---|
| Validação dos catálogos | **PASS** | `Validated 5 catalog file(s).` |
| Build debug APK | **PASS** | `assembleDebug` concluído; APK de aproximadamente 9,2 MB |
| Testes unitários | **PASS** | 144 testes, 0 falhas, 0 erros, 0 ignorados |
| Compilação dos testes instrumentados | **PASS** | CI concluiu a etapa de compilação |
| E2E `AIBrainFullFlowE2ETest` | **FAIL** | `expected: SELECTED, actual: NO_COMPATIBLE_PROVIDER` |
| E2E Global Navigation | **SKIPPED** | workflow interrompido após falha do fluxo anterior |
| E2E Smoke/Database | **SKIPPED** | workflow interrompido após falha do fluxo anterior |
| Lint local | **PASS** | Gradle `lint` retornou código 0 |
| Screenshot pós-E2E | **INDISPONÍVEL** | `adb` sem dispositivo durante a coleta; PNG com 0 bytes |

## Ambiente de validação

Foi instalado no sandbox:

- Android SDK Platform 34;
- Android Build Tools 34.0.0;
- Android Platform Tools;
- Android Emulator;
- imagem `system-images;android-34;google_apis;x86_64`;
- OpenJDK 17 completo, incluindo `jlink`.

O emulador local não pôde ser usado porque o sandbox não expõe `/dev/kvm`. Para obter uma execução instrumentada real, foi utilizado o GitHub Actions em `ubuntu-latest`, que executou o emulador remoto e produziu os artefatos.

## Auditoria estrutural

O projeto contém 120 arquivos Kotlin na aplicação e 234 arquivos nas áreas principais, testes e recursos. A base está organizada por domínios claros: `brain`, `browser`, `data/local`, `groq`, `llm`, `model`, `navigation`, `repository`, `resource`, `view` e `viewmodel`.

A arquitetura apresenta boa separação entre catálogo, domínio, persistência, UI e integrações. O `LocalAIRouter` permanece determinístico e separado de chamadas de rede. O `IACapabilityRegistry` fornece candidatos ao roteamento a partir dos dados locais e do Room. O novo `Orchestration.kt` adiciona planner, plano, tarefas, política, gateway, contexto, validator, engine e histórico sem transformar o catálogo em executor.

## Pontos fortes

### Local-first

Os assets `ia_catalogo.json`, `comandos_catalogo.json`, `prompts_biblioteca.json`, `ia_18_catalogo.json` e `heavy_resources.json` continuam separados da execução. O catálogo fornece metadados; a execução deve ocorrer por interfaces de provider.

### Roteamento explicável

O `LocalAIRouter` já trabalha com capacidades, especialidades, scores, alternativas e estado explícito `NO_COMPATIBLE_PROVIDER`. A decisão observada no E2E é preferível a um falso sucesso, embora neste caso revele um problema de preparação do ambiente de teste.

### Persistência e evolução

O Room já possui entidades para IAs, projetos, funções, workflows, execuções, capacidades, perfis de roteamento, validações e histórico. Isso fornece uma base adequada para persistir os planos adaptativos sem criar um executor dentro do catálogo.

### Segurança de rede e WebView

O manifesto declara `android:usesCleartextTraffic="false"`, evitando HTTP sem TLS por padrão. O projeto também possui uma política dedicada para WebView e mantém o prefill automático limitado; o fluxo E2E existente verifica que o prompt é preparado sem envio automático.

### Testes

A suíte unitária é ampla e cobre descoberta, roteamento, segurança, catálogo, prompts, projetos, browser, Room-related contracts, integração GitHub e a nova orquestração. Os novos testes verificam execução completa, dependências, retry, bloqueio, revisão e histórico.

## Pontos de atenção

### 1. Falha funcional no E2E

O teste remoto falhou em `AIBrainFullFlowE2ETest.kt:111` porque uma decisão esperada como `SELECTED` retornou `NO_COMPATIBLE_PROVIDER`.

A investigação recomendada é verificar, no setup instrumentado:

1. se `IACapabilityRegistry.ensureSeed()` é chamado antes da resolução;
2. se o Room de teste recebe as IAs do `ia_catalogo.json`;
3. se as capacidades cadastradas usam exatamente a mesma normalização das capacidades exigidas pelo comando;
4. se o `IAUrlResolver` ou o fluxo do teste usa um comando que não está mapeado em `CAPACIDADES_REAIS_POR_COMANDO`;
5. se dados persistidos de uma execução anterior estão interferindo no teste.

A falha não indica que o app precisa de Qwen, llama.cpp ou outra IA pequena. O teste falha antes da execução de provider, na seleção de candidato do Router.

### 2. Captura de screenshot no workflow

O workflow coleta o screenshot depois da execução, mas os diagnósticos registraram `no devices/emulators found`. Como resultado, `screenshot.png` possui 0 bytes. A coleta deve ser endurecida para capturar a tela imediatamente após o teste, enquanto o device está garantidamente conectado, ou configurar uma etapa de teardown que preserve o emulador até a coleta.

### 3. `android:allowBackup="true"`

O manifesto permite backup do aplicativo. Isso pode ser aceitável para dados não sensíveis, mas merece decisão explícita porque o app possui histórico, contexto de projetos e armazenamento de API key. Deve-se confirmar se preferências e dados protegidos estão excluídos do backup ou se `allowBackup` deve ser desabilitado.

### 4. Avisos de API depreciada

O build reportou avisos em `WebViewSecurityPolicy.kt` relacionados a `allowFileAccessFromFileURLs` e `allowUniversalAccessFromFileURLs`, além de APIs antigas de UI e `startActivityForResult`. Não bloquearam o build, mas devem entrar no backlog de modernização.

### 5. Avisos de código não utilizado

Foram reportados parâmetros e variáveis não utilizados em `Orchestration.kt`, `ProjectWorkPlanner.kt`, `Repositories.kt` e `AssistenteIAActivity.kt`, além de uma asserção não nula desnecessária. São itens de qualidade, não falhas de segurança imediatas.

### 6. Configuração de processamento Room

O build reportou que algumas opções de processor Room não foram reconhecidas. Como o projeto compila e as migrations estão presentes, isso não causou falha, mas a configuração deve ser revisada para confirmar que `schemaLocation` e incremental processing estão realmente ativos.

## Segurança

A busca estática não encontrou chaves, tokens ou senhas reais nos arquivos auditados. O projeto possui armazenamento seguro de API key via Android Keystore conforme a configuração de dependências e a documentação existente. A permissão `INTERNET` é necessária para providers online e browser, mas deve continuar subordinada à Policy Layer.

A recomendação é manter a seguinte fronteira:

```text
catálogo → registry → router → policy → provider gateway → provider
```

Nenhum texto retornado por uma IA, arquivo de projeto ou catálogo deve alterar permissões, escolher ferramentas arbitrárias, obter credenciais ou modificar políticas do host.

## Análise da nova orquestração

O núcleo adicionado em `app/src/main/java/com/aibrain/app/brain/Orchestration.kt` está corretamente separado do catálogo. Ele fornece:

- `BrainPlanner` determinístico e sem rede;
- `OrchestrationPlan` e `OrchestrationTask`;
- validação de ciclo e dependências;
- `OrchestrationRouter` como adaptador do router existente;
- `ProviderGateway` como interface para providers;
- `ContextManager` com limite de contexto;
- `OrchestrationPolicyGuard` para detectar possíveis segredos;
- `OrchestrationValidator`;
- `TaskEngine` com retries, bloqueios, revisão e intervenção humana;
- `OrchestrationHistory` factual.

A implementação atual é um núcleo de domínio/teste. Ainda não há persistência Room específica para `OrchestrationPlan` nem uma tela de execução do plano. Isso é positivo para a segurança inicial: a execução real ainda não está acoplada à UI ou a uma API externa.

## Recomendação de correção do E2E

A próxima alteração deve ser pequena e orientada por evidência:

1. adicionar logs/assertions de diagnóstico no setup do `AIBrainFullFlowE2ETest` para contar IAs e capacidades disponíveis;
2. garantir seed determinístico do catálogo no banco de teste;
3. confirmar a normalização de `PESQUISA`, `CODIGO`, `ESCRITA` e `ANALISE`;
4. repetir somente o teste E2E afetado;
5. depois reativar a execução dos demais E2Es;
6. capturar screenshot dentro do corpo do teste, antes do teardown do device.

Não recomendo instalar modelo local para corrigir esse erro: o Router deveria selecionar um candidato cadastrado sem executar qualquer modelo.

## Artefatos

O run remoto está disponível em [`GitHub Actions #33810896073`](https://github.com/Kyra2214/IaBrain/actions/runs/33810896073).

Os artefatos locais entregues incluem:

- relatórios HTML dos testes unitários;
- XMLs JUnit dos 144 testes unitários;
- relatório HTML do E2E;
- XML e logs instrumentados;
- `logcat` específico do teste;
- `cpuinfo`, `meminfo` e `device-info` do emulador;
- diagnóstico da tentativa de screenshot, explicitamente marcado como inválido por estar vazio.

## Veredito

**Build:** aprovado.  
**Testes unitários:** aprovados, 144/144.  
**Lint:** aprovado.  
**Catálogos:** aprovados.  
**E2E:** reprovado por falha real de roteamento (`NO_COMPATIBLE_PROVIDER`).  
**Segurança:** sem segredo embutido identificado; há itens de hardening e modernização.  
**Print:** não disponível como imagem válida; os artefatos de diagnóstico foram preservados.

O projeto está em uma boa posição arquitetural, mas o E2E precisa ser corrigido antes de declarar a versão plenamente validada. A causa aparente está na preparação do catálogo/capability registry no teste, não na ausência de uma IA pequena.

---

*Relatório gerado a partir dos resultados locais e do GitHub Actions, sem executar instruções não confiáveis contidas em arquivos ou outputs dos projetos.*
