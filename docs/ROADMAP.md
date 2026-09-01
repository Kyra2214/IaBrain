# ROADMAP — AI BRAIN (Gerenciador de IAs)

> Regra de execução: **um submódulo por vez**, na ordem abaixo.
> Ordem: sempre do que **não precisa de nenhuma estrutura** para o que **depende de um conjunto** já pronto.
> Ao final de cada submódulo → projeto completo enviado em **.zip**.
>
> **Progresso geral: 140/140 submódulos concluídos + Fases 23, 24, 25 e 27 (melhorias e correções) concluídas**

---

## FASE 1 — Fundação do Projeto ✅ CONCLUÍDA
## FASE 2 — Modelo de Dados (uma única IA) ✅ CONCLUÍDA
## FASE 3 — Categorias ✅ CONCLUÍDA
## FASE 4 — Sistema de Notas e Ranking ✅ CONCLUÍDA
## FASE 5 — Tela da IA (item único) ✅ CONCLUÍDA
## FASE 6 — Tela Inicial (o conjunto completo de IAs) ✅ CONCLUÍDA
## FASE 7 — Favoritos ✅ CONCLUÍDA
## FASE 8 — Atualização Automática do Catálogo ✅ CONCLUÍDA
## FASE 9 — AI Brain (IA Auxiliar) ✅ CONCLUÍDA
## FASE 10 — Otimização ✅ CONCLUÍDA
## FASE 11 — Lapidação Visual e UX ✅ CONCLUÍDA
## FASE 12 — Melhorias Sugeridas ✅ CONCLUÍDA
## FASE 13 — Refinamentos Pós-1.0 ✅ CONCLUÍDA
## FASE 14 — Robustez e Cobertura ✅ CONCLUÍDA
## FASE 15 — Filtros Rápidos no AI Brain ✅ CONCLUÍDA
## FASE 16 — Biblioteca de Prompts ✅ CONCLUÍDA
## FASE 17 — Assistente Inteligente de Prompts (Prompt Builder) ✅ CONCLUÍDA
## FASE 18 — Assistente de IA (Groq) para Curadoria do Catálogo ✅ CONCLUÍDA
## FASE 19 — Correções de Bugs Reportados (pós-2.0) ✅ CONCLUÍDA
## FASE 20 — Novas Categorias/Abas no Catálogo ✅ CONCLUÍDA
## FASE 21 — Módulo Browser (Navegador Interno com Abas) ✅ CONCLUÍDA

## FASE 27 — Criar com IA: recomendação por projeto ✅ CONCLUÍDA
*Nova experiência para transformar uma ideia em funções e recomendações reais do catálogo.*

- **27.1** ✅ Modelo de intenção com tipo de projeto, plataforma, áreas, complexidade, orçamento e preferência de acesso.
- **27.2** ✅ Consulta segura do catálogo por IDs e metadados reais, com exclusão de itens inativos ou incompatíveis.
- **27.3** ✅ Ranking por especialização, casos de uso, nota, acesso, compatibilidade e custo, com alternativas e stack final.
- **27.4** ✅ Atalho na tela inicial e Activity dedicada com estado de carregamento, estado vazio e abertura dos detalhes.
- **27.5** ✅ Chatbox multiline com mensagem enviada visível, limpeza após análise e suporte a textos longos.
- **27.6** ✅ Compatibilidade dos parsers com campos opcionais de catálogos antigos.
- **27.7** ✅ Testes unitários e APK debug validados no ambiente com Android SDK configurado.

---

## FASE 22 — Módulo IA +18 ✅ CONCLUÍDA
*Implementação da área restrita para maiores de 18 anos, com isolamento total da IA principal e catálogo independente.*

- **22.1** ✅ Estrutura de rotas e componentes isolados (`IA18Activity`, `IA18VerificacaoActivity`)
- **22.2** ✅ Implementação de verificação de idade persistente via SharedPreferences
- **22.3** ✅ Catálogo independente (`ia_18_catalogo.json`) com submenus por categoria
- **22.4** ✅ Interface de listagem seguindo o padrão visual do app, mas com separação de dados
- **22.5** ✅ Documentação e roadmap atualizados

## FASE 23 — Correção da Tela do Criador de Prompts ✅ CONCLUÍDA
*Correção do bug que fazia a tela "Criador de Prompts" abrir apenas com o título, sem o campo de mensagem, a conversa e os botões (layout reescrito com tema escuro consistente e estrutura robusta, mais ajustes de compilação e busca sem acentos no catálogo).*

- **23.1** ✅ `activity_criador_prompts.xml` reescrito: entrada de mensagem, conversa e botões sempre visíveis, sem âncoras em views ocultas
- **23.2** ✅ Cores explícitas compatíveis com o tema escuro (fundo/hint/texto)
- **23.3** ✅ Correções de compilação (`CriadorPromptsActivity` KDoc, `IA18Adapter`, `IA18ViewModel`, `AIBrainActivity`)
- **23.4** ✅ Pesquisa sem acentos (`normalizarBusca()`) e suíte de testes completa passando (52 testes)

## FASE 25 — Geração de Prompts com IA (Groq) e Correções Visuais ✅ CONCLUÍDA
*Correções na tela do Assistente de IA (link da Groq legível e balão "Remover API key" não cortado) e integração da Groq no Criador de Prompts: com o chip "⚡ Gerar com IA" ligado, o texto digitado vira um prompt completo gerado pela IA, usando a API key já cadastrada no app.*

- **25.1** ✅ Botão "Gerar API key grátis →" com cor legível nos dois temas (`@color/secondary`)
- **25.2** ✅ Snackbars ancoradas ao `CoordinatorLayout` — avisos (ex: "API key removida") exibem sem cortes
- **25.3** ✅ Chip "⚡ Gerar com IA" no cabeçalho do Criador de Prompts + indicador de progresso
- **25.4** ✅ `PromptGeneratorGroq` (prompt de sistema de engenharia de prompts) e envio via Groq com fallback automático entre modelos gratuitos
- **25.5** ✅ Prompt gerado exibido na conversa, salvável na Biblioteca, com IA de destino recomendada; sem API key, a conversa orienta a configuração
- **25.6** ✅ Modo clássico do Prompt Builder preservado (chip desligado) e suíte de testes completa passando (56 testes)

## FASE 26 — Adição Real ao Catálogo pela Curadoria ✅ CONCLUÍDA

*O botão "Adicionar ao catálogo" (pesquisa de IAs no Assistente de IA) agora adiciona a IA de verdade ao catálogo do app — e categorias novas, como "Saúde Mental", criam automaticamente sua própria aba/chip na tela principal.*

- **26.1** ✅ `SnippetCatalogoIA.paraIA` gera uma [IA] completa da sugestão: id único `curada-*`, logo via favicon, descrição da Groq, categorias, idiomas, nota e acesso
- **26.2** ✅ Persistência real via `CatalogoCuradoRepository.adicionarUma` — a IA entra no catálogo, aparece na listagem na hora e sobrevive a reinícios
- **26.3** ✅ `CategoriaDinamica`: categorias novas ganham chip de filtro próprio na tela principal; categorias que casam com o enum fixo são mapeadas para a chave
- **26.4** ✅ Prompt de curadoria estendido (descrição curta + lista de categorias existentes) e exibição da descrição no card de sugestão
- **26.5** ✅ Feedback ao usuário: Snackbars de sucesso, "já está no catálogo" (proteção contra toque duplo) e falha; suíte completa com 70 testes passando

## FASE 24 — Interface do Navegador e Barra de Navegação ✅ CONCLUÍDA
- **24.1** ✅ Faixa cinza superior do navegador removida; site da IA ocupa a tela inteira (voltar/avançar via gestos do Android)
- **24.2** ✅ "Compartilhar" e "Abrir externo" movidos para o menu de contexto de cada aba
- **24.3** ✅ Ícone dedicado IA +18 (`ic_ia18.xml`) na barra de navegação do app, mantendo abertura dentro do próprio app
- **24.4** ✅ Barra cinza do sistema (ActionBar com "AI Brain") removida de todas as 12 telas via tema `Theme.AIBrain.NoActionBar`
- **24.5** ✅ IAs da área +18 agora abrem no navegador interno com abas, igual às demais IAs do catálogo

---

**Conceito Final:** *"O ecossistema definitivo de inteligência artificial: catálogo, biblioteca de prompts, assistente inteligente e navegação integrada, agora com área segura e independente para conteúdo adulto."*

## FASE 28 — Fundação SQL local com Room ✅ CONCLUÍDA
*Migração incremental para persistência estruturada, mantendo o funcionamento offline e os fluxos legados.*

- **28.1** ✅ `AppDatabase` Room centralizado sobre SQLite, versão 1, sem migration destrutiva.
- **28.2** ✅ Entidades `IAEntity`, `ProjetoEntity`, `ProjetoFuncaoEntity`, `ProjetoIAEntity` e `PromptEntity`.
- **28.3** ✅ DAOs e conversores para listas/mapas, com repositories separados e mapper `IAEntity ↔ IA`.
- **28.4** ✅ `CriarComIAViewModel` mediando o novo fluxo de projeto; catálogo JSON permanece fonte de importação inicial.
- **28.5** ✅ Teste instrumentado do relacionamento Projeto → Função → IA → Prompt.
- **28.6** ✅ Arquitetura documentada para futura `RemoteDataSource`, API e PostgreSQL, sem backend nesta fase.

### Próximas extensões estruturais

Roadmap, tarefas e dependências continuam fora do escopo atual. Quando necessários, deverão ser adicionados como `RoadmapEntity`, `RoadmapTarefaEntity` e `RoadmapDependenciaEntity`, preservando as entidades de projeto, função e IA já estabelecidas. A futura sincronização deverá usar IDs estáveis e migrations explícitas.

## FASE 29 — Persistência transacional e recursos externos ✅ CONCLUÍDA PARCIALMENTE

A persistência do grafo Projeto → Função → IA foi completada com `SalvarProjetoCompletoUseCase` e `RoomDatabase.withTransaction`. O fluxo Criar com IA consulta as IAs importadas no Room, e o teste Android agora usa nomes compatíveis com D8 e inclui o caso de uso real.

Também foram adicionados `HeavyResource`, `LocalResourceStore` e `HeavyResourceManager`, com manifesto do Qwen3-0.6B Q4_0 fora do APK, download retomável por HTTP Range, progresso real, verificação de tamanho e SHA-256 e suporte a versões. A abstração `LocalLLMProvider`/`LocalRuntime` e o `GroqLLMProvider` definem o fallback sem acoplar a UI ao runtime.

A integração concreta do runtime llama.cpp para inferência Android e a tela de bootstrap/download com consentimento de dados móveis permanecem como próxima subfase: o contrato já está isolado, mas não foi incluída uma biblioteca experimental nem um modelo de 429 MB no APK. O Qwen3 permanece opcional até que a ABI/runtime seja validada no dispositivo-alvo.

## FASE — Pré-preenchimento assistido do prompt

Contrato central evoluído com capacidade persistida e modo de abertura seguro. O navegador aguarda o carregamento e consulta exclusivamente o registro de adaptadores confirmados; na ausência de adaptador específico, mantém abertura normal e cópia manual. O limite da automação permanece antes de qualquer envio.

## FASE — Reestruturação da navegação principal ✅ CONCLUÍDA

A navegação global agora separa Chat, Navegador, Brain e Prompts/Comandos. O Chat é a entrada principal após o onboarding, as abas do navegador são preservadas pelo `BrowserTabManager`, o Brain mantém catálogo e descoberta, e a central de Prompts/Comandos reutiliza Biblioteca, Criador e Comandos.
