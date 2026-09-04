# IaBrain — AI API Intelligence

## Objetivo

Criar um pool local de APIs de IA verificadas, com prioridade para o ecossistema chinês, sem remover o navegador nativo do IaBrain.

## Fluxo

`descobrir → catalogar → validar segurança → verificar credenciais → testar → marcar utilizável → sincronizar → rotear → detectar quota → trocar/fallback → medir → revisar`

## Catálogo

O catálogo guarda provedor, modelo, capacidades, endpoint, documentação, região, tipo de acesso e estado de disponibilidade. Chaves de API nunca são armazenadas no catálogo.

Tipos de acesso: `FREE_PERMANENT`, `FREE_TIER`, `PROMOTIONAL_CREDITS`, `PAID`, `BROWSER_ONLY`, `UNKNOWN`.

## Verificação real

Quando uma API exige chave, o usuário fornece a credencial por mecanismo próprio de credenciais do IaBrain. O `AiApiHttpTestRunner` envia apenas um probe mínimo (`Hello from IaBrain`, até 8 tokens). HTTP 2xx com corpo não vazio pode virar `VERIFIED`; 401/403 vira `CREDENTIAL_REQUIRED`; respostas de quota podem virar `QUOTA_EXHAUSTED`.

Uma API somente entra no roteamento automático como utilizável quando está ativa e possui verificação `VERIFIED`.

## Sincronização

TTL de 24 horas. A abertura do app agenda a sincronização em background; o catálogo local continua disponível mesmo sem internet. O catálogo remoto é uma fonte de atualização, não uma dependência de inicialização.

## Failover

O projeto pode escolher:

- perguntar antes de trocar;
- trocar automaticamente para uma API compatível verificada;
- continuar somente com APIs gratuitas;
- usar o navegador nativo como fallback;
- parar.

Se uma API acabar quota no meio da execução, o resultado é uma falha explícita e o roteador pode procurar uma alternativa. Não existe sucesso falso.

## Navegador

O navegador nativo/WebView continua sendo um canal de execução. A camada de APIs reduz a dependência dele, mas não o substitui.

## Dados e aprendizado

As métricas locais registram sucesso, falhas, latência e falhas de quota por modelo. Uma fronteira `AiApiStrongReviewer` permite que uma IA forte revise o catálogo posteriormente, sem misturar essa revisão com as regras determinísticas de segurança e disponibilidade.

## Fontes iniciais

O seed é deliberadamente conservador e deve ser sincronizado/verificado antes de ser tratado como disponibilidade real. Free tier, créditos e limites são temporais e podem mudar pelo provedor.
