# Explorer Intelligence v2.0 — fases 1 a 10

O Explorer deixa de ser apenas uma busca de catálogo e passa a ser uma camada de inteligência de descoberta que alimenta o catálogo, o Browser, o Router, a Factory e a memória do IaBrain.

## 1. China AI Radar
- Priorizar fontes e projetos do ecossistema chinês.
- Descobrir modelos, agentes, ferramentas, APIs, MCPs e projetos open-source/open-weight.
- A prioridade chinesa influencia o ranking, mas nunca ignora segurança ou confiança.

## 2. Global AI Radar
- Manter descoberta mundial por região.
- China é uma prioridade de cobertura, não uma exclusão de outros ecossistemas.
- Deduplicação usa URL oficial + nome normalizado.

## 3. AI Tools Catalog
- Diferenciar AI, MODEL, TOOL, AGENT, FRAMEWORK, MCP, API e DESKTOP.
- Registrar capacidades sem transformar ausência de informação em afirmação.

## 4. Agent Explorer
- Dar tratamento explícito a agentes, tool-use, browser/computer-use, workflows e MCP.
- Preparar integração futura com a orquestração existente sem substituir o runtime atual.

## 5. AI Workspace/Desktop
- Modelo de janelas persistíveis para um futuro workspace visual.
- Browser interno continua sendo o canal primário quando uma janela representa uma IA web.
- Esta fase não cria um segundo navegador nem depende de Chrome/Custom Tabs.

## 6. Connector Intelligence
- Perfil de canais Browser/API/Local.
- Seleção determinística Browser-first, depois API e Local conforme disponibilidade declarada.
- Nenhuma chave ou executor é criado automaticamente.

## 7. AI Lab
- Aceitar somente avaliações com evidência fornecida/medida.
- Registrar tarefa, score, latência e evidência.
- Não fabricar benchmarks nem transformar marketing em métrica interna.

## 8. Open-source Intelligence
- Verificar licença antes de tratar uma ideia/código como reutilizável.
- MIT/Apache/BSD/ISC/MPL verificados podem ser aprovados pelo domínio.
- Licença desconhecida exige REVIEW_REQUIRED; projeto fechado não é fonte de código para reutilização.

## 9. Explorer → Brain
- Handoff contém somente fatos conhecidos, capacidades e confiança.
- O Brain pode aprender sobre uma descoberta sem inventar capacidades ou avaliações.

## 10. Autonomous AI Radar
- Política semanal configurável.
- Execução automática fica separada da lógica de descoberta.
- O pipeline é determinístico e preparado para receber fontes remotas reais depois, sem tornar a rede obrigatória para o catálogo local.

## Contrato de segurança

- HTTPS obrigatório para pontos de entrada.
- Credenciais em URL são rejeitadas.
- localhost e faixas privadas básicas são rejeitados.
- Fragmentos de URL são rejeitados.
- Falhas ou dados incompletos não viram sucesso silencioso.

## Contrato de compatibilidade

O Explorer v2.0 é aditivo. O modelo `IA`, o `CatalogoRepository`, o `LocalAIRouter`, o BrowserActivity/WebView e os contratos anteriores continuam intactos. A promoção para o catálogo legado deve ocorrer por um adaptador explícito e validado, preservando favoritos, histórico e catálogos antigos.

## Regra de implementação

Toda descoberta externa deve seguir:

`descobrir → normalizar → validar → deduplicar → revisar licença → ranquear → catalogar → avaliar → entregar ao Brain`

O Explorer não executa uma API só porque encontrou sua documentação e não automatiza uma interface web sem um adaptador confirmado.
