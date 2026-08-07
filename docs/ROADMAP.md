# ROADMAP — AI BRAIN (Gerenciador de IAs)

> Regra de execução: **um submódulo por vez**, na ordem abaixo.
> Ordem: sempre do que **não precisa de nenhuma estrutura** para o que **depende de um conjunto** já pronto.
> Ao final de cada submódulo → projeto completo enviado em **.zip**.
>
> **Progresso geral: 107/107 submódulos (100%)**

---

## FASE 1 — Fundação do Projeto ✅ CONCLUÍDA
*Não depende de nenhum dado ou estrutura. É só o esqueleto.*

- **1.1** ✅ Criar projeto Android (Kotlin, Gradle, Material Design)
- **1.2** ✅ Estrutura de pastas MVVM (`model`, `view`, `viewmodel`, `repository`, `data`)
- **1.3** ✅ Configurar dependências base (Coroutines, ViewModel/LiveData ou StateFlow, Custom Tabs, Gson/Moshi)

---

## FASE 2 — Modelo de Dados (uma única IA) ✅ CONCLUÍDA
*Trabalha com um item isolado — ainda não é um conjunto.*

- **2.1** ✅ Data class `IA` (nome, logo, site, descrição, categoria, idiomas, gratuita/paga, notas)
- **2.2** ✅ `ia_catalogo.json` inicial com **1 IA de exemplo** (ex: ChatGPT)
- **2.3** ✅ Repositório que lê e faz parse do JSON local (leitura de um único registro)

---

## FASE 3 — Categorias ✅ CONCLUÍDA
*Primeiro conjunto: o conjunto fixo de categorias do app.*

- **3.1** ✅ Enum/lista fixa de categorias (💬 Conversa, 💻 Código, 🎥 Vídeo, 🖼️ Imagem, 🎨 Design, 🎵 Música, 🎙️ Voz, ✍️ Escrita, 📚 Estudos, 🌍 Tradução, 📊 Produtividade, 🤖 Agentes IA, 📈 Negócios, 🔎 Pesquisa, ⚙️ Automação)
- **3.2** ✅ Associar categorias e notas por categoria a cada IA no JSON
- **3.3** ✅ Popular `ia_catalogo.json` com um conjunto maior de IAs reais

---

## FASE 4 — Sistema de Notas e Ranking ✅ CONCLUÍDA
*Depende do conjunto de IAs + categorias já existir.*

- **4.1** ✅ Cálculo de nota por categoria (leitura das notas já no JSON)
- **4.2** ✅ Lógica de ranking (ordenar o conjunto de IAs por nota, por categoria)

---

## FASE 5 — Tela da IA (item único) ✅ CONCLUÍDA
*Exibição de um único item do conjunto — ainda não precisa de lista.*

- **5.1** ✅ Tela de detalhes da IA (logo, nome, descrição, notas, categorias, idiomas, tipo de acesso)
- **5.2** ✅ Botão "Abrir IA" com Custom Tabs (abrir site oficial)

---

## FASE 6 — Tela Inicial (o conjunto completo de IAs) ✅ CONCLUÍDA
*Agora sim: interface que manipula o conjunto inteiro.*

- **6.1** ✅ Listagem de IAs (RecyclerView/Compose LazyColumn)
- **6.2** ✅ Pesquisa por nome/função
- **6.3** ✅ Filtro por categoria
- **6.4** ✅ Seção de Ranking / Populares / Novidades (ordenações do conjunto)

---

## FASE 7 — Favoritos ✅ CONCLUÍDA
*Conjunto pessoal do usuário, derivado do conjunto principal.*

- **7.1** ✅ Salvar/remover IA favorita (armazenamento local — SharedPreferences)
- **7.2** ✅ Tela/lista de favoritos
- **7.3** ✅ Histórico de acesso (últimas IAs abertas)

---

## FASE 8 — Atualização Automática do Catálogo ✅ CONCLUÍDA
*Sincronização entre dois conjuntos: local vs remoto.*

- **8.1** ✅ Verificação de versão do catálogo remoto
- **8.2** ✅ Download do novo JSON
- **8.3** ✅ Merge/atualização (novas IAs, notas, descrições) sem sobrescrever favoritos/histórico

---

## FASE 9 — AI Brain (IA Auxiliar) ✅ CONCLUÍDA
*Opera sobre o conjunto inteiro para gerar recomendações — a fase mais dependente de estrutura.*

- **9.1** ✅ Lógica de recomendação (ex: usuário digita "quero criar vídeo" → filtra categoria + ranking)
- **9.2** ✅ Retorno estruturado: melhor opção, segunda opção, alternativas gratuitas
- **9.3** ✅ Geração de descrições curtas / sugestão de categoria para novas IAs

---

## FASE 10 — Otimização ✅ CONCLUÍDA
*Aplicada ao sistema já completo (todas as fases anteriores prontas).*

- **10.1** ✅ Cache de imagens e dados
- **10.2** ✅ Compressão/otimização de imagens
- **10.3** ✅ Modo offline (fallback para último JSON salvo)

---

## FASE 11 — Lapidação Visual e UX ✅ CONCLUÍDA
*Aplicada ao sistema já completo — suavização de interface e identidade visual.*

- **11.1** ✅ Correção de bug no `colors.xml` (tag `</resources>` ausente, quebrava o build)
- **11.2** ✅ Tipografia suavizada (sans-serif-medium nos títulos, sans-serif no corpo, letter-spacing) aplicada globalmente via tema
- **11.3** ✅ Cantos arredondados consistentes (busca, botões, cards, logo dos itens) via shape appearances do Material3
- **11.4** ✅ Paleta ampliada com tons suaves (texto mudo, variante de superfície, divisor) para melhor hierarquia visual
- **11.5** ✅ Transições com fade suave entre telas
- **11.6** ✅ Tela de boas-vindas (`WelcomeActivity`): nome do app, mensagem e botão "Entrar", como primeira tela do app
- **11.7** ✅ Logo/ícone do app: marca de rede neural (nó central + 6 conexões) em teal sobre navy, aplicada ao launcher (adaptativo + legado, todas as densidades) e em destaque na tela de boas-vindas

---

## FASE 12 — Melhorias Sugeridas ✅ CONCLUÍDA
*Backlog de sugestões da rodada de lapidação anterior — implementado.*

**Experiência do usuário**
- **12.1** ✅ Persistir "já viu a tela de boas-vindas" (SharedPreferences) para pular direto à listagem nas próximas aberturas
- **12.2** ✅ Estado de carregamento (indicador de progresso) no "Perguntar" do AI Brain
- **12.3** ✅ Feedback visual ao favoritar (Snackbar de confirmação)

**Arquitetura de código**
- **12.4** ✅ Lógica de filtro/pesquisa/ordenação migrada da `MainActivity` para `MainViewModel` — sobrevive a rotação de tela sem recarregar
- **12.5** ✅ `CatalogoRepository.carregarCatalogoSincronizado()` como ponto único de leitura + sincronização remota, reduzindo a orquestração manual de dois repositórios

**Acessibilidade**
- **12.6** ✅ Ícones do sistema substituídos por ícones vetoriais coerentes com a identidade visual (estrela, voltar, placeholder de imagem, marca AI Brain)
- **12.7** ✅ Contraste de `on_background_muted` sobre `surface` ajustado (AA)

**Modo claro**
- **12.8** ✅ `values/colors.xml` (claro) + `values-night/colors.xml` (escuro) — `Theme.Material3.DayNight` responde ao tema do sistema

**Testes**
- **12.9** ✅ Testes unitários para `MainViewModel` (filtro/pesquisa/ordenação), `RecomendadorIA` e `NotaUtils`

---

## FASE 13 — Refinamentos Pós-1.0 ✅ CONCLUÍDA
*Aplicada ao sistema já completo — ajustes de qualidade de código, precisão do AI Brain, dados do catálogo e pequenos ganhos de UX, sem introduzir novas dependências ou estruturas.*

**Código / consistência**
- **13.1** ✅ Substituir referências totalmente qualificadas inline em `MainActivity.kt` (`com.aibrain.app.view.DetalheIAActivity...`, `com.google.android.material.snackbar.Snackbar...`, `com.aibrain.app.model.IA`) por imports no topo do arquivo — sem alterar comportamento

**AI Brain (Fase 9)**
- **13.2** ✅ Dar peso maior ao match exato de `categoria.chave`/`rotulo` do que às palavras-chave auxiliares em `detectarCategoria()`, reduzindo empates em textos curtos (ex: "editar áudio e vídeo")
- **13.3** ✅ Reconhecer as 2 categorias mais fortes de uma consulta (em vez de só 1) e mesclar o ranking das duas, mantendo o mesmo mecanismo offline de palavras-chave
- **13.4** ✅ Mensagem de fallback quando nenhuma categoria é detectada (`categoriaDetectada == null`), sugerindo exemplos de palavras-chave a partir do próprio `PALAVRAS_CHAVE_POR_CATEGORIA`

**Catálogo / dados**
- **13.5** ✅ Popular o campo `logo` de `ia_catalogo.json` com URLs reais (https), já que `ImagemCache` já sabe baixar por URL — hoje todos caem no placeholder por apontar para recursos locais inexistentes

**UX (mesmo estilo visual já estabelecido)**
- **13.6** ✅ Botão "Limpar filtros" para resetar pesquisa + categoria + ordenação de uma vez em `MainActivity`
- **13.7** ✅ Estado vazio (`lista_vazia`) com o `ic_image_placeholder` já existente (Fase 11) em vez de só texto, consistente com os ícones vetoriais das Fases 11/12

---

## FASE 14 — Robustez e Cobertura ✅ CONCLUÍDA
*Aplicada ao sistema já completo — reforça pontos que ficaram de fora da Fase 13: performance de listagem em catálogos grandes, visibilidade da sincronização remota, cobertura de teste dos novos recursos da Fase 13 e resiliência no carregamento de logos remotos.*

**Performance**
- **14.1** ✅ Paginação/lazy loading na listagem (`RecyclerView` da `MainActivity`) para catálogos grandes, evitando renderizar todas as IAs de uma vez

**Feedback visual**
- **14.2** ✅ Indicador de "atualizando catálogo" durante `sincronizarCatalogoRemoto()` (hoje a sincronização em segundo plano é silenciosa — o usuário não percebe quando o catálogo é atualizado)

**Testes**
- **14.3** ✅ Testes unitários para `MainViewModel.limparFiltros()` e o novo `LiveData<Boolean> filtroAtivo` (Fase 13.6), hoje sem cobertura

**Catálogo / dados**
- **14.4** ✅ Revisar timeout e cache de `ImagemCache` para logos remotos via Clearbit (Fase 13.5) — confirmar que um domínio sem logo (404) não gera tentativas repetidas de download a cada carregamento de tela

---

## FASE 15 — Filtros Rápidos no AI Brain ✅ CONCLUÍDA
*Aplicada sobre a tela do AI Brain (Fase 9) já completa — objetivo: encontrar a IA ideal em poucos toques, sem percorrer lista grande, mantendo a filosofia do projeto (interface simples, leve, rápida, intuitiva). Filtragem 100% local sobre o catálogo já carregado, sem chamada de rede, com atualização instantânea da lista.*

**Fundação de dados**
- **15.1** ✅ Classificação de acesso em 3 níveis (🟢 Gratuita / 🟡 Freemium / 🔴 Paga) — hoje o modelo `IA` só tem o booleano `gratuita`; definir a regra de derivação ou estender o `ia_catalogo.json`
- **15.2** ✅ Helper de idioma por filtro (🇧🇷 Português / 🇺🇸 Inglês / 🌍 Multilíngue) a partir de `IA.idiomas` já existente
- **15.3** ✅ Helper de faixa de avaliação (⭐ 10 / ⭐ 9+ / ⭐ 8+ / ⭐ 7+) reaproveitando `notaMedia()` (Fase 4.1)

**Interface — barra de chips combináveis**
- **15.4** ✅ Barra horizontal de Chips (Material Design, rolagem horizontal, seleção visual clara) logo abaixo do campo de pergunta/pesquisa na tela do AI Brain, com os 4 grupos de filtro (Acesso, Categoria — reaproveitando o enum `Categoria` já existente —, Idioma, Avaliação)
- **15.5** ✅ Combinação simultânea de todos os filtros ativos (AND entre grupos) + termo de pesquisa, com atualização instantânea da lista exibida
- **15.6** ✅ Indicador da quantidade de filtros ativos + botão "Limpar filtros" reaproveitando o padrão visual já criado na Fase 13.6

**Performance**
- **15.7** ✅ Validar que a combinação de filtros continua leve em catálogos grandes, reaproveitando a paginação/lazy loading da Fase 14.1 se a lista de resultados crescer

---

## FASE 16 — Biblioteca de Prompts ✅ CONCLUÍDA
*Novo módulo, independente do catálogo de IAs (Fases 1-6), mas seguindo a mesma lógica de dependência: primeiro o item isolado (um único prompt), depois o conjunto (lista/pesquisa/filtro), depois os recursos pessoais derivados desse conjunto (favoritos/histórico), reaproveitando padrões já existentes no projeto.*

**Fundação de dados** *(item isolado — ainda não é um conjunto)*
- **16.1** ✅ Data class `Prompt` (título/nome, categoria, subcaso, descrição curta, objetivo, nível, compatibilidade/`melhor_para`, `template` com variáveis entre chaves — ex: `{ESTILO}`, `{PERSONAGEM}` —, lista `variaveis` (cada uma com nome e um valor `padrao` opcional, usado como fallback pela Fase 17), tags, data de criação) — o campo `template` é travado (texto fixo bem estruturado e detalhado) e só os valores em `variaveis` podem ser substituídos, base que a Fase 17 vai consumir sem alterar
- **16.2** ✅ Enum/lista fixa de categorias de prompts (💻 Programação, 🎥 Vídeo, 🖼️ Imagem, 🎨 Design, ✍️ Escrita, 📚 Estudos, 📊 Marketing, 📱 Redes Sociais, 💼 Negócios, 📄 Documentos, 🌍 Tradução, 🎙️ Voz, 🎵 Música, 🤖 Automação, 🧩 Engenharia de Prompt)
- **16.3** ✅ `prompts_biblioteca.json` inicial com **5 templates de exemplo por categoria** (cada um cobrindo um subcaso distinto — ex: em Imagem: retrato realista, still life de produto, paisagem cinematográfica, personagem 3D, foto esportiva)
- **16.4** ✅ Repositório que lê e faz parse do JSON local de prompts (reaproveitando o padrão do `CatalogoRepository` da Fase 2.3)

**Tela do Prompt** *(exibição de um único item — como a Fase 5 fez com a IA)*
- **16.5** ✅ Tela de detalhes do prompt (título, descrição, objetivo, nível, compatibilidade, texto completo, tags, data de criação)
- **16.6** ✅ Copiar prompt com um toque (Clipboard)
- **16.7** ✅ Editar antes de copiar (campo editável antes da cópia)
- **16.8** ✅ Compartilhar (Intent de compartilhamento do Android)

**Tela da Biblioteca** *(o conjunto completo de prompts — como a Fase 6 fez com as IAs)*
- **16.9** ✅ Nova seção "📚 Biblioteca de Prompts" na navegação principal
- **16.10** ✅ Popular `prompts_biblioteca.json` com **no mínimo 5 templates travados por categoria** (mais, se a categoria tiver muitos subcasos comuns de uso), todos reais e testados
- **16.11** ✅ Listagem de prompts (RecyclerView) com título, categoria, nível e compatibilidade
- **16.12** ✅ Pesquisa por palavra-chave (título, descrição, tags)
- **16.13** ✅ Filtro por categoria
- **16.14** ✅ Ordenação: Mais utilizados / Mais recentes / Favoritos (ASSUMINDO: "Mais bem avaliados" adiado — `Prompt` não tem campo de nota, ver ASSUMINDO no Changelog)

**Recursos pessoais do usuário** *(conjunto derivado — como a Fase 7 fez com os favoritos de IA)*
- **16.15** ✅ Favoritar prompts (reaproveitando o armazenamento local — SharedPreferences — da Fase 7.1)
- **16.16** ✅ Histórico de utilização (últimos prompts copiados/usados)

---

## FASE 17 — Assistente Inteligente de Prompts (Prompt Builder) ✅ CONCLUÍDA
*Depende da Biblioteca de Prompts (Fase 16) já existir — opera sobre esse conjunto para **preencher templates travados**, nunca para redigir prompts do zero, seguindo o mesmo padrão da IA auxiliar da Fase 9 (a fase mais dependente de estrutura). Na prática as Fases 16 e 17 tendem a ser entregues juntas: a Fase 16 sozinha (navegação/favoritos) já é útil, mas o valor completo só aparece com o builder.*

**Nova aba**
- **17.1** ✅ Aba "🤖 Criador de Prompts" na navegação principal
- **17.2** ✅ Interface de conversa (campo de mensagem, histórico da sessão, respostas da IA, botão "Gerar prompt")

**Templates travados por categoria** *(fundação — estende o `Prompt` da Fase 16.1, ainda um item isolado)*
- **17.3** ✅ Definir, para cada categoria da Fase 16.2, **um conjunto de templates travados (mínimo 5, podendo ser mais)**, cada um cobrindo um subcaso específico da categoria — ex. em Imagem: retrato realista, still life de produto, paisagem cinematográfica, personagem 3D, foto esportiva — texto base pronto e detalhado, só os campos entre chaves mudam (já satisfeito por `prompts_biblioteca.json`, ver ASSUMINDO no Changelog)
- **17.4** ✅ Estender `prompts_biblioteca.json` (Fase 16.3/16.10) para o formato de template travado, com valor padrão opcional por variável (fallback da Fase 17.10), ex.:
  ```json
  {
    "categoria": "imagem",
    "subcaso": "Foto esportiva profissional",
    "template": "Crie uma imagem {ESTILO} de {PERSONAGEM} realizando {AÇÃO} em {CENÁRIO}, com iluminação {LUZ}, estilo {QUALIDADE}.",
    "variaveis": [
      { "nome": "ESTILO", "padrao": "realista" },
      { "nome": "PERSONAGEM" },
      { "nome": "AÇÃO" },
      { "nome": "CENÁRIO", "padrao": "estádio lotado" },
      { "nome": "LUZ", "padrao": "cinematográfica" },
      { "nome": "QUALIDADE", "padrao": "fotografia esportiva profissional" }
    ],
    "melhor_para": ["Midjourney", "DALL-E", "Leonardo"]
  }
  ```
- **17.5** ✅ Popular a Biblioteca (Fase 16) com **no mínimo 5 templates travados por categoria**, cobrindo os subcasos mais comuns de cada uma das 15 categorias da Fase 16.2

**Arquitetura do fluxo** *(conjunto — a IA opera sobre a biblioteca inteira de templates)*
- **17.6** ✅ Fluxo fixo e sequencial do assistente:
  ```
  Usuário → Identifica intenção → Busca template correto na Biblioteca
          → Faz no máximo 3 perguntas → Substitui variáveis → Entrega prompt final
  ```
- **17.7** ✅ Detecção de tipo de tarefa e categoria a partir do texto do usuário (reaproveitando `detectarCategoria()` das Fases 13.2/13.3)
- **17.8** ✅ Busca do template mais adequado **entre os templates da categoria detectada**, por correspondência de palavras-chave do subcaso (ex: "gol", "estádio" → subcaso "foto esportiva" dentro de Imagem) — nunca gerar um prompt do zero se já existir template compatível
- **17.9** ✅ Perguntas de refinamento — **no máximo 3**, uma por variável ainda não informada pelo usuário e sem valor `padrao` suficiente no template — priorizando rapidez e simplicidade
- **17.10** ✅ Tratamento de fallback: se o usuário não responder a uma pergunta, usar o valor `padrao` da variável (Fase 17.4); se a categoria detectada não tiver nenhum template com correspondência de subcaso, usar o template mais próximo da categoria e avisar isso na explicação final (reaproveitando o padrão de mensagem de fallback da Fase 13.4) — o fluxo nunca trava por falta de resposta

**Geração do prompt final**
- **17.11** ✅ Substituição **apenas** dos campos de `variaveis` no `template` (texto fixo permanece intacto), mantendo o objetivo original do usuário e a categoria escolhida
- **17.12** ✅ Adaptação do prompt final para a IA de destino, usando `melhor_para` do template (ChatGPT, Claude, Grok, Gemini, IAs de imagem, vídeo, código)
- **17.13** ✅ Indicação da IA recomendada para aquele prompt (reaproveitando o `RecomendadorIA` da Fase 9)
- **17.14** ✅ Resposta do assistente sempre enxuta: entrega **somente** o prompt final + uma breve explicação, sem textos longos

**Recursos**
- **17.15** ✅ Salvar o prompt gerado como novo item na Biblioteca (Fase 16)
- **17.16** ✅ Favoritar / Copiar / Editar antes de usar (reaproveitando 16.6-16.7 e 16.15)
- **17.17** ✅ Abrir a IA recomendada diretamente pelo Custom Tabs (reaproveitando o botão "Abrir IA" da Fase 5.2)

**Integração**
- **17.18** ✅ `CriadorPromptsActivity` (Fase 17.2) passa a consumir o pipeline completo do Prompt Builder já implementado em `brain/*` (Fases 17.6-17.17) em vez de só registrar a mensagem do usuário no histórico: mantém uma `SessaoConstrutorPrompt` por conversa, chama em sequência `identificarIntencao` → `avancarBuscaTemplateComFallback` → pergunta pendente (`proximaVariavelPendente`/`textoPergunta`, com `registrarResposta` a cada resposta do usuário) → `avancarSubstituicaoVariaveis` → `avancarAdaptacaoIADestino` → `avancarRecomendacaoIA` → `gerarMensagemRespostaFinal`, exibindo cada mensagem no `ChatAdapter` conforme a sessão avança de estágio; liga os botões de ação ao resultado final via `intentParaDetalhePromptGerado` (17.16), `PromptDadosLocaisRepository.salvarPromptGerado` (17.15) e `abrirIARecomendadaNoNavegador` (17.17)

**Regras fixas do AI Brain Prompt Builder** *(restrições que todo o fluxo 17.6-17.14 deve obedecer)*
- Nunca criar um prompt do zero se existir um template compatível na Biblioteca (Fase 16)
- Sempre procurar primeiro na biblioteca de templates
- Fazer no máximo 3 perguntas
- Não alterar o objetivo original do usuário
- Alterar apenas os campos/variáveis necessários do template
- Manter o prompt dentro da categoria escolhida
- Se uma variável não for respondida ou não houver template com correspondência exata, usar o valor `padrao` ou o template mais próximo — nunca travar o fluxo
- Entregar somente o prompt final e uma breve explicação
- Não gerar textos longos

---

## FASE 18 — Assistente de IA (Groq) para Curadoria do Catálogo ✅ CONCLUÍDA
*Novo módulo, independente do AI Brain offline (Fase 9) — não o substitui. Objetivo: dar ao curador (você) uma ferramenta ONLINE, opcional, que pesquisa e sugere NOVAS IAs (nome + site) para adicionar manualmente ao `ia_catalogo.json`. O `RecomendadorIA` offline (Fase 9) continua sendo o que atende o usuário final na tela AI Brain, sempre funcional sem internet e sem chave — a Groq nunca entra nesse caminho.*

**Fundação — armazenamento da chave e tela**
- **18.1** ✅ Armazenamento local da API key da Groq (SharedPreferences, mesmo princípio leve do `FavoritosRepository`/`PromptDadosLocaisRepository`) — salvar/remover/verificar existência
- **18.2** ✅ Nova tela "Assistente de IA" (`AssistenteIAActivity`): campo de API key (mascarado, mostra "já configurada" quando existe), botão "Salvar API key", botão "Remover API key", bloco de instruções "Como conseguir sua API key grátis" (passo a passo até console.groq.com/keys), aviso de que é apoio educativo/curadoria — mesmo padrão visual das outras telas do app

**Onboarding**
- **18.3** ✅ Primeira abertura do app: `WelcomeActivity` (Fase 11.6) passa a rotear para `AssistenteIAActivity` em vez de `MainActivity` quando não há API key configurada ainda; aberturas seguintes (ou já com chave salva) seguem direto para `MainActivity` como hoje. A tela continua acessível a qualquer momento por um botão na navegação principal.

**Cliente Groq**
- **18.4** ✅ Cliente HTTP para a API da Groq (`HttpURLConnection` nativo, mesmo padrão sem dependências extras do `AtualizacaoRepository`/`CatalogoRepository`), autenticado com a chave salva (18.1)
- **18.5** ✅ Lista fixa de modelos gratuitos da Groq, em ordem de preferência, com fallback automático: se a chamada com o modelo atual falhar (erro de API, modelo desativado/renomeado), tenta o próximo da lista antes de reportar falha ao usuário

**Curadoria de novas IAs**
- **18.6** ✅ Campo de pergunta livre na tela do Assistente (ex: "liste IAs de tecnologia") + prompt de sistema fixo instruindo a Groq a responder em formato estruturado (nome + site + categoria sugerida) para IAs que ainda não estão no catálogo local
- **18.7** ✅ Parser da resposta estruturada da Groq → lista de sugestões (nome, site, categoria sugerida), com tratamento de resposta malformada (nunca crasha, mostra erro amigável)
- **18.8** ✅ Tela/lista de sugestões retornadas, cada uma com botão "Adicionar ao catálogo" — pré-preenche os campos a partir da sugestão da Groq, mas a inserção final no `ia_catalogo.json` continua manual/revisada por você (curadoria, não automática), reaproveitando o mesmo princípio de revisão humana já usado no projeto para dados do catálogo

ASSUMINDO: a Groq (Fase 18) é estritamente um recurso de CURADORIA offline-first — usado só por você para descobrir e revisar candidatas a entrar no catálogo; o `RecomendadorIA` (Fase 9) que atende o usuário final na tela AI Brain permanece 100% offline, sem qualquer dependência da chave ou da API da Groq, preservando a proposta original do app ("leve, funcional offline"). A inserção efetiva no `ia_catalogo.json` não é automática (a Groq pode errar nome/site) — fica sempre como uma ação explícita do curador, mesmo que a Fase 18.8 pré-preencha os campos para agilizar.

---


✅ Catálogo · ✅ Categorias · ✅ Notas/Ranking · ✅ Tela da IA · ✅ Tela inicial + Pesquisa
✅ Favoritos · ✅ Atualização automática · ✅ AI Brain auxiliar · ✅ Otimização/Offline · ✅ Lapidação visual/UX · ✅ Melhorias sugeridas · ✅ Refinamentos pós-1.0

**Conceito:** *"Um Google das inteligências artificiais: encontre a IA certa para cada tarefa."*

---

## VERSÃO 2.0 ✅ CONCLUÍDA — Escopo estendido
📚 Biblioteca de Prompts (Fase 16) · 🤖 Assistente Inteligente de Prompts (Fase 17) · 🧑‍💻 Assistente de IA (Groq) para Curadoria do Catálogo (Fase 18)

**Conceito:** *"Não apenas um catálogo de inteligências artificiais, mas também uma biblioteca inteligente de prompts profissionais — permitindo que qualquer usuário obtenha resultados melhores, independentemente da IA escolhida."*
