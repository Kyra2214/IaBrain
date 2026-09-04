# Integração do Strix

O [Strix](https://github.com/usestrix/strix) entra no IaBrain como um **Security Provider** opcional e isolado. O catálogo continua contendo metadados e capacidades; ele não instala, chama ou executa o Strix.

```text
Brain Planner → Task Engine → Policy Layer → Strix Adapter → Strix Runner isolado
                                                        ↓
                                          Findings / OWASP / CVSS / evidências
                                                        ↓
                                                Critic / Validator
```

## Contrato

`StrixTarget` exige alvo, tipo, referência de autorização e escopo. `StrixPolicy` limita rede, validação de exploração, duração e quantidade de findings. `StrixSecurityPolicy` falha fechado quando não há autorização, quando um alvo de rede não tem rede habilitada ou quando a instrução contém possível segredo.

`StrixRunner` é uma interface injetável. `NonExecutingStrixRunner` é o runner padrão da primeira fase e não executa processos, instala dependências, baixa imagens ou acessa a rede. O runner real deverá operar fora do APK, preferencialmente em container/runner isolado, com permissões mínimas.

## Findings

`StrixFinding` registra título, severidade, categoria OWASP, CVSS, evidência, correção e validação. Findings críticos exigem CVSS alto e evidência suficiente antes de serem considerados válidos.

## Limites

A integração real somente pode testar aplicações próprias ou explicitamente autorizadas. Alvos públicos ou de terceiros não devem ser aceitos por padrão. O Adapter não deve usar `curl | bash`, executar shell diretamente, receber credenciais nos prompts ou permitir que um finding altere a política do host.

## Próximas fases

1. persistir assessments, findings e evidências no Room;
2. criar tela de escopo e aprovação humana;
3. implementar runner remoto/CI com container isolado;
4. importar relatório estruturado para o Validator;
5. integrar o resultado ao Quality Gate sem aplicar correções automaticamente.
