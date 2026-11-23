---
name: UnB Pocket - Monolito Editorial UnB
description: Sistema visual editorial utilitario em Jetpack Compose e Material 3 para o aplicativo UnB Pocket, combinando superfícies neutras em Dark/Light, serifa editorial e sans tabular.
colors:
  dark-ground: "#111112"
  dark-surface: "#1A1A1C"
  dark-surface-raised: "#232326"
  dark-border: "#2B2B30"
  dark-rule: "#26262A"
  dark-ink: "#F3F3F2"
  dark-muted: "#9A9AA0"
  dark-pill-active-bg: "#F2F2F0"
  dark-pill-active-ink: "#111112"
  dark-nav-bg: "#1C1C1E"
  dark-nav-border: "#2D2D32"
  light-ground: "#F5F5F3"
  light-surface: "#FFFFFF"
  light-surface-raised: "#ECECE9"
  light-border: "#E2E2DE"
  light-rule: "#ECECE8"
  light-ink: "#141413"
  light-muted: "#61615E"
  light-pill-active-bg: "#141413"
  light-pill-active-ink: "#FFFFFF"
  light-nav-bg: "#FFFFFF"
  light-nav-border: "#DFDFDA"
  tag-std-bg: "#EAEAE7"
  tag-std-ink: "#141414"
  tag-ovo-bg: "#F7ECC8"
  tag-ovo-ink: "#3E2D00"
  tag-veg-bg: "#DFF0E4"
  tag-veg-ink: "#0D4322"
  warning-dark-bg: "#332512"
  warning-dark-ink: "#F5B84D"
  warning-light-bg: "#FFF4DC"
  warning-light-ink: "#8A4F00"
  danger-dark-bg: "#3A1B1E"
  danger-dark-ink: "#FF8F98"
  danger-dark-text: "#EF5350"
  danger-light-bg: "#FFE4E6"
  danger-light-ink: "#9F1239"
  danger-light-text: "#C62828"
  info-dark-bg: "#1B2B3D"
  info-dark-ink: "#7DB9FF"
  info-light-bg: "#E3EFFC"
  info-light-ink: "#0B4C8C"
  qr-canvas: "#FFFFFF"
  qr-ink: "#0B0C0E"
typography:
  display-editorial:
    fontFamily: "Source Serif 4, Georgia, serif"
    fontSize: "28sp"
    fontWeight: 600
    lineHeight: 1.15
    letterSpacing: "-0.015em"
  metric-serif:
    fontFamily: "Source Serif 4, Georgia, serif"
    fontSize: "24sp"
    fontWeight: 700
    lineHeight: 1.15
    fontFeature: "tnum"
  metric-balance:
    fontFamily: "Inter, Roboto, sans-serif"
    fontSize: "34sp"
    fontWeight: 600
    lineHeight: 1.1
    fontFeature: "tnum"
  title:
    fontFamily: "Inter, Roboto, sans-serif"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: 1.3
  body:
    fontFamily: "Inter, Roboto, sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: 1.45
  label:
    fontFamily: "Inter, Roboto, sans-serif"
    fontSize: "12sp"
    fontWeight: 600
    lineHeight: 1.3
  overline:
    fontFamily: "Inter, Roboto, sans-serif"
    fontSize: "11sp"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "0.06em"
  mono:
    fontFamily: "JetBrains Mono, monospace"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: 1.35
rounded:
  tag: "8dp"
  inner: "12dp"
  card: "16dp"
  sheet: "24dp"
  capsule: "999dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
components:
  card-primary:
    backgroundColor: "{colors.dark-surface}"
    textColor: "{colors.dark-ink}"
    rounded: "{rounded.card}"
    padding: "16dp"
  card-inner-tile:
    backgroundColor: "{colors.dark-surface-raised}"
    textColor: "{colors.dark-ink}"
    rounded: "{rounded.inner}"
    padding: "12dp"
  pill-active:
    backgroundColor: "{colors.dark-pill-active-bg}"
    textColor: "{colors.dark-pill-active-ink}"
    rounded: "{rounded.inner}"
    padding: "8dp 12dp"
  pill-idle:
    backgroundColor: "{colors.dark-surface}"
    textColor: "{colors.dark-muted}"
    rounded: "{rounded.inner}"
    padding: "8dp 12dp"
  tag-dietary-vegan:
    backgroundColor: "{colors.tag-veg-bg}"
    textColor: "{colors.tag-veg-ink}"
    rounded: "{rounded.tag}"
    padding: "4dp 8dp"
  nav-capsule:
    backgroundColor: "{colors.dark-nav-bg}"
    textColor: "{colors.dark-muted}"
    rounded: "{rounded.capsule}"
    padding: "6dp"
  qr-frame:
    backgroundColor: "{colors.qr-canvas}"
    textColor: "{colors.qr-ink}"
    rounded: "{rounded.inner}"
    padding: "16dp"
---

# Design System: UnB Pocket (Direção V3: Monolito Editorial UnB)

## Overview

**Creative North Star: "Monolito Editorial UnB"**

UnB Pocket é um utilitário móvel nativo em Android (Jetpack Compose e Material Design 3) para consulta diária de cardápio e saldo do Restaurante Universitário (RU), horários e interior da Turma Virtual (SIGAA), rendimento acadêmico, documentos oficiais e Carteirinha Estudantil com QR Code. O sistema foi construído sobre o contrato offline-first e somente-leitura definido em `docs/core-contract.md`, consumindo exclusivamente `:core:model` e `UnbPocketCore`.

A identidade visual adota o rigor monocromático e modular inspirado na arquitetura de Brasília: superfícies planas em carvão neutro no modo escuro (`#111112`) e pedra clara no modo claro (`#F5F5F3`), sem tons esverdeados ou azulados no fundo, sem gradientes decorativos e sem sombras difusas em cartões em repouso. A personalidade da marca nasce do contraste entre a **serifa editorial** nos títulos de tela e nos índices acadêmicos (`IRA`, `Média Ponderada`) e a **sans-serif tabular** de alta legibilidade nos horários, saldos monetários e cardápios.

**Key Characteristics:**
- Dark-first verdadeiramente neutro (`#111112` / `#1A1A1C` / `#232326`) e Light em pedra quente (`#F5F5F3` / `#FFFFFF` / `#ECECE9`), com paleta fixa (Material You Dynamic Color desativado por padrão para preservar contraste e identidade).
- Navegação enxuta em **3 destinos principais** (`RU`, `Turmas`, `Discente`), alinhados 1:1 aos três repositórios do Core (`RuRepository`, `TurmasRepository`, `DiscenteRepository`), em cápsula inferior com área própria livre de sobreposição.
- Cor reservada estritamente para significado funcional: tags dietéticas do cardápio (`Padrão`, `Ovolacto`, `Vegano`), alertas de alergênicos, risco de reprovação por falta (`emRiscoReprovacaoPorFalta`), estado offline/stale e ação destrutiva de desconexão.
- Acesso rápido ao passe estudantil via ação direta **"Girar para QR Code da Catraca"** dentro do bloco do RU no módulo Discente, além de atalho opcional de QR rápido no topo.

---

## Colors

A paleta opera por inversão acromática de alto contraste para estrutura, seleção e leitura, enquanto cores saturadas ou pastéis ficam restritas a marcadores de estado e alimentação. Todos os pares texto/fundo atendem ao mínimo WCAG AA (`>= 4.5:1` para corpo e legendas; `>= 3:1` para títulos grandes).

### Primary (Seleção e Contraste Dominante)
- **Active Pill Dark** (`dark-pill-active-bg` `#F2F2F0` com `dark-pill-active-ink` `#111112`): usada no dia selecionado da faixa semanal (`DOM 22`, `SEG`), na opção ativa da cápsula de navegação inferior, no botão primário de login/emissão e no preenchimento da barra de integralização curricular no tema escuro.
- **Active Pill Light** (`light-pill-active-bg` `#141413` com `light-pill-active-ink` `#FFFFFF`): equivalente invertido no tema claro, mantendo o mesmo peso visual de seleção sem recorrer a cor de marca chamativa no cromo.

### Secondary (Marcadores Dietéticos e Semânticos)
- **Vegano** (`tag-veg-bg` `#DFF0E4` com `tag-veg-ink` `#0D4322`): badge exclusivamente para pratos veganos no cardápio do RU (aplicada com o mesmo alto contraste nos temas claro e escuro).
- **Ovolacto** (`tag-ovo-bg` `#F7ECC8` com `tag-ovo-ink` `#3E2D00`): badge exclusiva para opções ovolactovegetarianas.
- **Padrão** (`tag-std-bg` `#EAEAE7` com `tag-std-ink` `#141414`): badge neutra para a proteína padrão do dia.
- **Risco / Alerta** (`warning-dark-bg` `#332512` / `warning-dark-ink` `#F5B84D`; `warning-light-bg` `#FFF4DC` / `warning-light-ink` `#8A4F00`): usada para `emRiscoReprovacaoPorFalta`, avisos de fallback do RU (`FALLBACK_IMAGE_ONLY`, `FALLBACK_PDF_ONLY`) e cache desatualizado (`staleWarning`).
- **Alergênico / Erro / Destrutivo** (`danger-dark-bg` `#3A1B1E` / `danger-dark-ink` `#FF8F98` e `danger-dark-text` `#EF5350`; `danger-light-bg` `#FFE4E6` / `danger-light-ink` `#9F1239` e `danger-light-text` `#C62828`): usada exclusivamente em pratos que contêm alergênicos marcados em `RuPreferencias.alergenicosRestritos`, erros críticos de sessão/rede (`CoreError`) e no botão `Desconectar conta`.
- **Informação / Documentos Salvos** (`info-dark-bg` `#1B2B3D` / `info-dark-ink` `#7DB9FF`; `info-light-bg` `#E3EFFC` / `info-light-ink` `#0B4C8C`): usada em badges sutis de status sincronizado ou links de download no AVA.

### Neutral (Estrutura de Superfícies)
- **Dark Theme**:
  - `dark-ground` (`#111112`): fundo principal da janela e área segura atrás das barras do sistema (edge-to-edge).
  - `dark-surface` (`#1A1A1C`): fundo de cartões primários (Cardápio, Turma, Rendimento Acadêmico, Saldo RU).
  - `dark-surface-raised` (`#232326`): blocos aninhados de segundo nível (caixas de `IRA`/`MP`, bloco de valor `R$ 8,65`, linhas de transações recentes, segmentos selecionados em controles internos).
  - `dark-border` (`#2B2B30`) e `dark-rule` (`#26262A`): contornos `1dp` dos cartões e divisores internos entre seções de cardápio.
  - `dark-ink` (`#F3F3F2`) e `dark-muted` (`#9A9AA0`): hierarquia de texto primário e metadados/horários.
- **Light Theme**:
  - `light-ground` (`#F5F5F3`): fundo em pedra neutra.
  - `light-surface` (`#FFFFFF`): superfície dos cartões principais.
  - `light-surface-raised` (`#ECECE9`): caixas internas de métricas, transações e trilho de barras de progresso.
  - `light-border` (`#E2E2DE`) e `light-rule` (`#ECECE8`): contornos e divisores `1dp`.
  - `light-ink` (`#141413`) e `light-muted` (`#61615E`): texto principal e secundário.
- **QR Surface Invariant**:
  - `qr-canvas` (`#FFFFFF`) e `qr-ink` (`#0B0C0E`): fundo e módulos do QR Code da Carteirinha permanecem sempre branco puro e preto absoluto, inclusive no tema escuro, com margem silenciosa mínima de `16dp`.

### Named Rules
**The Neutral Dark Ground Rule.** Nenhum fundo ou superfície estrutural do tema escuro pode receber matiz verde, azul-marinho ou violeta. A base é estritamente carvão neutro (`#111112`, `#1A1A1C`, `#232326`).

**The Semantic Restraint Rule.** Cores cromáticas nunca decoram navegação, ícones de cabeçalho ou cartões em repouso. Toda cor fora da escala acromática comunica dieta, alergênico, risco acadêmico, erro ou status de sincronização.

**The Pure White QR Rule.** A área de exibição do QR Code da catraca nunca é invertida no modo escuro; usa sempre `qr-canvas` (`#FFFFFF`) e `qr-ink` (`#0B0C0E`) para garantir leitura óptica imediata nas catracas do RU e BCE.

---

## Typography

**Display Font:** `Source Serif 4` (Regular 400 e SemiBold 600/700 empacotados em `app/src/main/res/font/`, com fallback `FontFamily.Serif` do sistema).
**Body Font:** `Inter` (Regular 400, Medium 500, SemiBold 600, empacotada em `res/font/`, com fallback `Roboto` / `FontFamily.SansSerif`).
**Label/Mono Font:** `JetBrains Mono` (Medium 500, com fallback `FontFamily.Monospace`).

**Character:** O contraste entre a serifa humanista clássica nos cabeçalhos principais e índices acadêmicos (`Cardápio RU`, `Aulas & Turmas`, `3.2102`) e a sans-serif neutra nos dados operacionais reproduce fielmente a voz editorial das telas de referência, mantendo densidade limpa em telas pequenas de smartphone.

### Hierarchy
- **Display Editorial** (`display-editorial`, 400/600, `28sp`, line-height `32sp`, tracking `-0.015em`): títulos de topo de tela (`Cardápio RU`, `Aulas & Turmas`, `Perfil Discente`). Máximo de 2 linhas mesmo com `fontScale = 1.3`.
- **Metric Serif** (`metric-serif`, 700, `24sp`, line-height `28sp`, `fontFeatureSettings = "tnum"`): valores numéricos de prestígio acadêmico (`IRA`, `Média Ponderada`).
- **Metric Balance** (`metric-balance`, 600, `34sp`, line-height `38sp`, `fontFeatureSettings = "tnum"`): saldo monetário do RU (`R$ 8,65`) e contagem principal de refeições. Sempre em Sans tabular para leitura instantânea.
- **Title** (`title`, 600, `16sp`, line-height `21sp`): subtítulos de seção (`Jantar`, `Almôndegas`, títulos das disciplinas em `REDES SEM FIO` ou `Sentence Case` nos detalhes do AVA).
- **Body** (`body`, 400, `14sp`, line-height `20sp`): nomes dos pratos, locais de aula (`FT AT-31/12`), textos de notícias do AVA e linhas de transação.
- **Label** (`label`, 600, `12sp`, line-height `16sp`): textos de chips de dia (`SEG 16`), segmented switches, badges dietéticas (`Padrão`, `Ovolacto`, `Vegano`) e botões.
- **Overline** (`overline`, 600, `11sp`, line-height `13sp`, tracking `+0.06em`, `ALL CAPS`): cabeçalhos internos de agrupamento dentro de cartões (`PRATOS PRINCIPAIS`, `GUARNIÇÃO & ACOMPANHAMENTOS`, `RENDIMENTO ACADÊMICO`, `TRANSAÇÕES RECENTES`).
- **Mono** (`mono`, 500, `12sp`, line-height `16sp`): matrícula (`190026308`), códigos de horário SIGAA (`24M12`), código de disciplina (`ENE0145`), payload de 16 dígitos da Carteirinha e código de verificação de documentos oficiais.

### Named Rules
**The Serif Voice Rule.** A serifa (`Source Serif 4`) é usada exclusivamente no título principal da tela (`Display Editorial`) e nos dois índices acadêmicos (`IRA` e `MP`). Rótulos de controle, horários, botões e valores monetários usam sempre Sans.

**The Tabular Integrity Rule.** Todo valor numérico variável ou comparável (preços, saldo em centavos, horários `08:00 - 09:50`, faltas `12/15`, porcentagem `74.4%`, `IRA`/`MP`) aplica `fontFeatureSettings = "tnum"`.

---

## Layout

O layout segue estritamente as diretrizes Android nativas (`edge-to-edge` com `WindowInsets` de status bar, navigation bar e IME):

- **Margens e Ritmo Espacial:**
  - Margem horizontal externa padrão: `16dp` nas duas bordas em largura compacta (`< 600dp`).
  - Espaçamento vertical entre blocos primários: `16dp`; entre cartões irmãos ou itens dentro de listas internas: `8dp` a `12dp`.
  - Padding interno padrão de cartões primários: `16dp`.
- **Alvos de Toque e Escala:**
  - Todo elemento clicável (chips de dia, ícones do topo, linhas de documento, botão `Girar para QR`, abas da cápsula inferior) possui área clicável mínima de `48dp x 48dp` (ou altura mínima `48dp`).
  - Layouts devem comportar `font_scale = 1.3` sem truncar valores críticos (usar `FlowRow` ou empilhamento vertical automático quando etiquetas e textos lado a lado excederem a largura útil).
- **Arquitetura de Navegação e Adaptação:**
  - **Compacto (`< 600dp`)**: barra inferior em cápsula centralizada (`nav-capsule`) com **3 destinos** (`RU`, `Turmas`, `Discente`), posicionada em uma faixa própria de rodapé sobre `ground` (com padding/insets adequados ou conteúdo com `contentPadding` inferior suficiente) para que nenhum cartão ou linha do último bloco fique encoberto atrás da cápsula.
  - **Expandido / Tablet (`>= 600dp`)**: a cápsula inferior dá lugar a uma `NavigationRail` lateral esquerda alinhada à grade de `maxWidth = 840dp` centrada (ou duas colunas em `Discente` e `Turmas`).
  - **Fluxos Secundários e Autenticação:**
    - **Login CAS**: tela dedicada fora da navegação principal (logo/título editorial `UnB Pocket`, aviso explícito de operação somente-leitura, campos de matrícula e senha com toggle de visibilidade, checkbox "Salvar credenciais no Cofre Seguro (Keystore)" e feedback inline para `CoreError.InvalidCredentials`).
    - **Interior da Turma Virtual (AVA)**: navegação por push (`NavHost`) com `TopAppBar` de retorno (respeitando predictive back do Android) e abas internas limpas para `Tópicos e Materiais`, `Notícias`, `Frequência` e `Notas / Tarefas`.
    - **Visualizador de PDF / Fallback do RU**: quando `CardapioParserStatus` for `FALLBACK_IMAGE_ONLY` ou `FALLBACK_PDF_ONLY`, exibir aviso discreto no topo do cartão do cardápio e renderizar a imagem de contingência com zoom ou botão de abertura local do PDF.

---

## Elevation & Depth

A hierarquia visual do Monolito Editorial UnB é **plana por padrão (Flat-by-Default)** e transmite profundidade por degraus tonais e contornos `1dp`, nunca por sombras pesadas ou efeitos de vidro/blur em cartões em repouso:

- **Nível 0 (Canvas):** `dark-ground` (`#111112`) / `light-ground` (`#F5F5F3`).
- **Nível 1 (Cartões Primários):** `dark-surface` (`#1A1A1C`) / `light-surface` (`#FFFFFF`) com borda `1dp` em `dark-border` (`#2B2B30`) / `light-border` (`#E2E2DE`) e `shadowElevation = 0.dp`.
- **Nível 2 (Tiles Internos e Caixas de Métrica):** `dark-surface-raised` (`#232326`) / `light-surface-raised` (`#ECECE9`) sem borda externa (separação puramente tonal).
- **Nível 3 (Elementos Flutuantes e Modais):** reservado exclusivamente para a **Cápsula de Navegação**, `ModalBottomSheet` (seletor de campus, filtro de alergênicos, detalhes de notícia, seletor de documentos) e `Snackbar`. A cápsula inferior utiliza contorno `1dp` (`dark-nav-border` `#2D2D32` / `light-nav-border` `#DFDFDA`) com elevação tonal discreta (`2.dp` no claro).

**The Flat-By-Default Rule.** Cartões de conteúdo (cardápio, aulas, rendimento acadêmico, extrato) possuem `0.dp` de sombra em repouso. Profundidade vem do contraste entre `ground -> surface -> surface-raised` e da linha `1dp`.

---

## Shapes

O sistema usa uma escada de raios concêntricos de 4 níveis (quanto menor o elemento interno, menor o raio):

- **Cartões Primários (`card`):** `16dp` (`RoundedCornerShape(16.dp)`).
- **Tiles Internos e Botões de Dia (`inner`):** `12dp` (`RoundedCornerShape(12.dp)`), usado em blocos de `IRA`/`MP`, card de saldo `R$ 8,65`, linhas de transação, botões de ação e tiles da faixa de 7 dias (`SEG 16`..`DOM 22`).
- **Badges e Tags (`tag`):** `8dp` (`RoundedCornerShape(8.dp)`), usado em `Padrão`, `Ovolacto`, `Vegano`, status de matrícula (`ATIVO`, `2026.2`) e alertas de alergênicos.
- **Cápsula e Controles Segmentados de Ícone (`capsule`):** `999dp` (`CircleShape`), usado na cápsula de navegação inferior, no alternador de refeição (`Café / Almoço / Jantar`) e no avatar circular do perfil discente.
- **Sheets Modais (`sheet`):** `24dp` nos cantos superiores (`RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)`).

---

## Components

### Top Editorial Header
Cabeçalho plano integrado diretamente ao `ground` (sem `TopAppBar` preenchida pesada):
- **Esquerda:** título em `Display Editorial` (`28sp`, Source Serif 4) e linha contextual logo abaixo (`UnB · DARCY` com botão dropdown discreto de campus no RU, ou `Semestre 2026.2 · Horário Oficial e Turmas` em Turmas) + pílula compacta de status/saldo quando útil.
- **Direita:** no máximo 3 ícones de `20dp` em `48dp` de alvo de toque (`Notícias/Avisos` com ponto de não-lido, `Alternar Tema`, `Sincronizar`). Durante sincronização ativa, o ícone de refresh gira suavemente sem bloquear a leitura do cache.

### Seletor Semanal de 7 Dias (RU e Turmas)
- **Layout:** `Row` de 7 colunas iguais (`Modifier.weight(1f)`) com `4dp` de espaçamento em `RU` (`SEG 16`..`DOM 22`), evitando cortes horizontais no sétimo dia em telas de `360dp`; em `Turmas`, grade de 6 colunas (`SEG`..`SÁB`) com indicador circular `4dp` abaixo do dia atual.
- **Estados:** selecionado com fundo `pill-active-bg` e texto `pill-active-ink`; inativo com fundo `surface` (ou `pill-idle-bg`), texto `muted` e borda `1dp`.

### Alternador Segmentado (Refeições e Visão de Turmas)
- **Variante Ícones (RU - Café / Almoço / Jantar):** pílula `capsule` (`44dp` de altura) em `surface` com borda `1dp`; o item ativo recebe cápsula interna `pill-active-bg` (`#F2F2F0` no escuro / `#141413` no claro).
- **Variante Texto (Turmas - Aulas do Dia / Turmas do Semestre):** contêiner `12dp` com borda `1dp` e item ativo preenchido em `surface-raised` com texto `ink` em `600`.

### Card Agrupado de Cardápio (RU)
- **Estrutura:** um único cartão `16dp` em `surface` dividido internamente por `rule` (`1dp`) e espaçamento de `16dp` entre categorias (`PRATOS PRINCIPAIS`, `GUARNIÇÃO & ACOMPANHAMENTOS`, `SOPA DO DIA`, `SALADAS`, `SOBREMESA & BEBIDA`).
- **Linhas de Prato Principal:** largura fixa mínima de `72dp` para a badge dietética à esquerda (`Padrão`, `Ovolacto`, `Vegano`) alinhada em coluna, seguida pelo nome do prato à direita em `14sp`.
- **Alerta de Alergênico:** quando um prato dispara um item de `RuPreferencias.alergenicosRestritos`, exibe badge compacta em `danger-soft` com o nome do alergênico (`Contém Leite`, `Glúten`) à direita ou abaixo da linha, e um banner resumido no topo do card quando houver correspondências.

### Cards de Aula e Interior da Turma Virtual
- **Cartão de Aula (`Turmas`):** cartão `16dp` com linha superior mostrando ícone de relógio + intervalo decodificado (`08:00 - 09:50`, `tnum`) e chevron à direita; título da disciplina em `15sp–16sp` `700`; linha de localização com ícone de pino (`FT AT-31/12`); código SIGAA em `mono` (`24M12`) e alerta de falta (`warning-soft` somente quando `emRiscoReprovacaoPorFalta = true`).
- **Interação:** expandir o card revela resumo rápido (`aulasMinistradas/aulasTotal`, faltas restantes) e botão direto **"Abrir Turma Virtual"**.

### Cartão de Rendimento Acadêmico (`Discente`)
- **Estrutura:** cartão `16dp` com overline `RENDIMENTO ACADÊMICO`, dois tiles internos `12dp` lado a lado (`IRA` e `MÉDIA PONDERADA`) com valores em `metric-serif` (`24sp` Source Serif 4 Bold `tnum`), seguidos pela barra linear de `Integralização Curricular` (`8dp` de altura, fundo `surface-raised`, preenchimento `pill-active-bg`) e rodapé com `Concluídas`, `Total` e horas pendentes.

### Cartão Interativo Saldo RU / QR Code da Catraca (`Girar para QR`)
- **Estado Frontal (Saldo):** exibe overline `RESTAURANTE UNIVERSITÁRIO`, `Saldo Disponível` (e `GrupoAcessoRu` no canto direito), tile interno `12dp` com valor centralizado `R$ 8,65` em `metric-balance` (`34sp` tabular) + subtítulo com refeições restantes calculadas (`3 almoços/jantares ou 5 cafés`), botão tonal largura total **"Girar para QR Code da Catraca"** (`48dp` altura, `12dp` raio) e lista `TRANSAÇÕES RECENTES` em tiles `12dp`.
- **Estado Verso (QR Code):** animação suave de rotação/transição (`250ms`, respeitando `Remove animations` do sistema com crossfade simples) revelando o QR Code de 16 dígitos dentro de `qr-frame` branco puro (`#FFFFFF`), o código `1900 2630 8202 6204` em `mono`, validade e controle para aumentar brilho da tela na catraca.

### Navegação em Cápsula Inferior
- **Composição:** cápsula flutuante `capsule` (`60dp` de altura, padding interno `6dp`, fundo `nav-bg`, borda `1dp` `nav-border`) ancorada com `16dp` acima da `navigationBarsPadding()` em área de rodapé dedicada (sem cobrir o conteúdo rolável).
- **Itens:** 3 destinos (`RU`, `Turmas`, `Discente`), com largura mínima `64dp` e altura `48dp` por item; o item selecionado recebe fundo em cápsula `pill-active-bg` e ícone `pill-active-ink`. Suporta `contentDescription` e tooltip de acessibilidade em todos os ícones.

### Estados de Dados e Contingência (`SyncedData` e `CoreError`)
- **Loading Inicial:** esqueletos tonais planos em `surface-raised` mantendo exatamente a altura dos cartões reais (sem shimmer colorido nem spinner central).
- **Offline / Cache (`DataOrigin.CACHE`):** banner compacto ou linha discreta no cabeçalho indicando `"Offline · Atualizado às HH:mm"` mantendo todos os dados em cache totalmente navegáveis.
- **Erro (`CoreError`):** `SessionExpired` ou `InvalidCredentials` redirecionam ou exibem prompt inline de reautenticação; `NetworkUnavailable` e `HttpFailure` mantêm o cache visível com ação de `Tentar novamente` em `Snackbar` ou linha discreta no bloco afetado; `UnavailableForProfile` exibe estado vazio explicativo em linguagem natural (ex.: calouro sem IRA ainda calculado mostra `-` em vez de `0.0000`).

---

## Do's and Don'ts

### Do:
- **Do** manter o modo escuro estritamente em carvão neutro (`#111112`, `#1A1A1C`, `#232326`) e o modo claro em pedra neutra (`#F5F5F3`, `#FFFFFF`, `#ECECE9`).
- **Do** usar `Source Serif 4` nos títulos principais das telas e nos valores de `IRA` e `Média Ponderada`, e `Inter` com `tnum` em todos os horários, preços, transações e cardápios.
- **Do** reservar `contentPadding` inferior suficiente (ou container de rodapé dedicado) para que nenhum conteúdo da lista role escondido atrás da cápsula de navegação inferior.
- **Do** renderizar o QR Code da Carteirinha sempre sobre `#FFFFFF` com margem de `16dp` e módulos `#0B0C0E`, mesmo com o tema escuro ativado.
- **Do** formatar todos os valores monetários do RU diretamente a partir de centavos (`Long`) e expor o estado de origem (`CACHE`, `REMOTE`, `FALLBACK`) de `SyncedData<T>`.

### Don't:
- **Don't** aplicar matiz verde, azul ou violeta nos fundos ou cartões do tema escuro.
- **Don't** usar gradientes em cartões de saldo, botões ou fundos de tela, nem sombras difusas em cartões em repouso.
- **Don't** usar emojis como ícones de categoria, badges ou mensagens de erro; utilize ícones vetoriais em traço uniforme (`1.75dp`).
- **Don't** ocultar ou zerar dados quando offline (`NetworkUnavailable`); preserve os dados locais de `SyncedData` com carimbo de horário.
- **Don't** vazar detalhes de Room, OkHttp, `ViewState` ou `jscook_action` para composables ou ViewModels de UI; consuma exclusivamente `:core:model` e `UnbPocketCore`.
