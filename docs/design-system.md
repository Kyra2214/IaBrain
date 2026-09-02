# Design System IaBrain

## Princípios

O IaBrain combina **inteligência artificial, descoberta, tecnologia e confiança** em uma interface premium, clara e legível. A prioridade visual é clareza, consistência, usabilidade e, somente depois, estética e efeitos.

A UI atual permanece em XML/Views e consome a arquitetura existente. Este documento é a fonte de orientação para novas telas e refinamentos futuros.

## Paleta semântica

As cores devem ser usadas por papel, não por componente específico. Os valores ficam centralizados em `res/values/colors.xml` e `res/values-night/colors.xml`.

| Papel | Light | Dark | Uso |
|---|---|---|---|
| `primary` | Azul profundo | Azul claro | Ações e identidade estrutural |
| `secondary` | Azul-cyan controlado | Cyan claro | CTA principal, seleção e progresso |
| `tertiary` | Violeta sóbrio | Violeta claro | Destaques auxiliares |
| `background` | Branco azulado | Azul-marinho profundo | Fundo geral |
| `surface` | Branco | Azul profundo elevado | Cards, campos e superfícies |
| `surface_container` | Cinza azulado claro | Azul intermediário | Cards e navegação |
| `surface_variant` | Azul-cinza suave | Azul-cinza escuro | Campos e variações de superfície |
| `on_background` / `on_surface` | Azul quase preto | Branco azulado | Texto principal |
| `on_background_muted` / `on_surface_variant` | Cinza azulado | Azul-cinza claro | Texto secundário e auxiliar |
| `outline` / `outline_variant` | Cinza azulado | Azul-cinza médio | Bordas, divisores e foco |
| `success`, `warning`, `error`, `info` | Papéis semânticos | Contraste equivalente | Estados e feedback |

O modo escuro é planejado independentemente do modo claro: superfícies têm profundidade própria, textos mantêm contraste elevado e o cyan não domina a tela.

## Tipografia

Os estilos reutilizáveis vivem em `res/values/themes.xml` e não devem ser substituídos por tamanhos arbitrários em cada layout.

| Estilo | Função |
|---|---|
| `TextAppearance.AIBrain.Display` | Marca, onboarding e títulos de maior impacto |
| `TextAppearance.AIBrain.TituloTela` | Títulos de telas |
| `TextAppearance.AIBrain.Titulo` | Títulos de seção e cabeçalhos |
| `TextAppearance.AIBrain.TituloSecao` | Seções de cards e resultados |
| `TextAppearance.AIBrain.Corpo` | Perguntas, descrições e conteúdo principal |
| `TextAppearance.AIBrain.Label` | Labels de campos e componentes |
| `TextAppearance.AIBrain.Legenda` | Texto auxiliar, ajuda e metadados |
| `TextAppearance.AIBrain.Status` | Estado selecionado, categoria e feedback curto |

A família padrão é `sans-serif`; títulos usam `sans-serif-medium`. A hierarquia deve continuar legível com aumento de fonte do sistema.

## Espaçamento e formas

Os tokens ficam em `res/values/dimens.xml`. A escala base usa múltiplos de 4dp: `space_xs` (4dp), `space_sm` (8dp), `space_md` (16dp), `space_lg` (24dp), `space_xl` (32dp), `space_2xl` (40dp) e `space_3xl` (48dp).

A escala de formas é `shape_extra_small` (4dp), `shape_small` (10dp), `shape_medium` (16dp), `shape_large` (24dp) e `shape_full` para componentes realmente pill-shaped. Cards usam normalmente `shape_medium`; a navegação e o onboarding podem usar `shape_large`.

Áreas de toque usam pelo menos `icon_button_size` (48dp) quando o componente é interativo. O padding inferior das listas considera a `global_navigation_height` para que o conteúdo não fique escondido pela navegação.

## Componentes recorrentes

| Componente | Regra |
|---|---|
| App bar | Título de tela, botão de retorno de 48dp e espaçamento `space_md` |
| CTA primário | `Widget.AIBrain.Button`, reservado para a ação principal |
| Botão outlined | `Widget.AIBrain.Button.Outlined`, para ações alternativas |
| Botão tonal | `Widget.AIBrain.Button.Tonal`, para ações de apoio e atualização |
| Card IaBrain | Superfície semântica, raio médio, borda/elevacão discreta e padding consistente |
| Campo | `bg_search_rounded` ou componente Material com superfície e outline semântico |
| Chip | Seleção visível por preenchimento, outline e texto; não depender só da cor |
| Estado | Texto explicativo e ação de recuperação quando aplicável |
| Item de IA | Ícone, nome, descrição, categorias e favorito com hierarquia clara |
| Aba de navegador | Superfície escura própria, título truncado e botão de fechar com área acessível |

## Navegação global

A ordem funcional continua sendo **Chat → Navegador → Brain → Prompts/Comandos**. `GlobalNavigation` permanece responsável pelo roteamento. O refinamento visual usa superfície elevada, ripple semântico, labels sempre visíveis, item selecionado destacado e itens inativos legíveis.

Não criar uma segunda navegação ou mover regras de negócio para os layouts.

## Estados

O loading usa progresso discreto e animação leve. Empty states explicam o próximo passo. Erros mostram o problema em linguagem humana e oferecem recuperação. Success usa feedback breve. Disabled reduz contraste sem apagar a compreensão da ação. Selected combina cor, preenchimento e hierarquia para não depender exclusivamente de cor.

## Regras de arquitetura

A UI deve consumir `IAOpenContract`, `IAUrlResolver`, `RoomCommandResolver`, `LocalAIRouter`, `RoutingDecision`, `PromptGenerationSpec`, `PromptGenerationSpecBuilder`, `ContextualPromptGenerator`, `PromptEntity`, `IACapabilityRegistry`, `BrowserActivity`, Room, catálogo local, navegação global e multi-tab. Nenhum desses componentes deve ser duplicado para fins visuais.

Não hardcode URLs de IAs, não faça login automático, não insira JavaScript em páginas externas e não envie prompts automaticamente. O Design System se aplica somente à interface pertencente ao IaBrain; o conteúdo externo continua sob responsabilidade da página carregada no WebView.

## Checklist para novas telas

Antes de entregar uma tela, verificar se há hierarquia clara, CTA principal evidente, alinhamento, tokens de espaçamento, cores com função, contraste, content descriptions, áreas de toque adequadas, estado vazio/erro/loading e comportamento em telas pequenas. Também confirmar que os IDs funcionais e o fluxo global permanecem intactos.
