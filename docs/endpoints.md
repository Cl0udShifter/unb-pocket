# Documentacao de Extracao de Dados e Endpoints: UnB-Pocket

Este documento especifica todos os fluxos de rede, formularios JSF, seletores HTML, endpoints REST e algoritmos de extracao de PDFs mapeados via **REA (Reverse Engineering Assistant)** nos portais **SIGAA UnB** (`sigaa.unb.br`, `autenticacao.unb.br`, `sig.unb.br`) e **Restaurante Universitario UnB** (`ru.unb.br`).

> [!IMPORTANT]
> **Contrato Estrito de Somente Leitura (Read-Only):**
> Embora o SIGAA utilize requisicoes HTTP `POST` para navegar entre telas (devido a arquitetura Apache MyFaces / JSF 1.2 + RichFaces + JSCookMenu), o aplicativo **UnB-Pocket** deve executar exclusivamente os `POST` de navegacao e emissao listados neste documento. Nunca envie formularios de alteracao de estado (como `retiradaMatricula`, `trancamentoMatricula`, `trancamentoPrograma`, `novoTopicoForum`, `btEnviarEmailNee`, ou o botao `Confirmar` de `dados_discente.jsf`).

---

## Sumario

1. [Arquitetura de Autenticacao CAS SSO e Sessao JSF](#1-arquitetura-de-autenticacao-cas-sso-e-sessao-jsf)
2. [Modulo 1: Portal Discente - Tela Inicial (`discente.jsf`)](#2-modulo-1-portal-discente---tela-inicial-discentejsf)
   - [2.1 Perfil e Dados Institucionais](#21-perfil-e-dados-institucionais)
   - [2.2 Indices Academicos (IRA e MP) e Integralizacao Curricular](#22-indices-academicos-ira-e-mp-e-integralizacao-curricular)
   - [2.3 Extrato e Saldo do Restaurante Universitario no SIGAA](#23-extrato-e-saldo-do-restaurante-universitario-no-sigaa)
   - [2.4 Feed das 10 Ultimas Atualizacoes das Turmas](#24-feed-das-10-ultimas-atualizacoes-das-turmas)
   - [2.5 Turmas do Semestre e Decodificador de Horarios SIGAA](#25-turmas-do-semestre-e-decodificador-de-horarios-sigaa)
   - [2.6 Forum do Curso (Listagem e Leitura de Topicos)](#26-forum-do-curso-listagem-e-leitura-de-topicos)
3. [Modulo 2: Interior da Turma Virtual (`/sigaa/ava/index.jsf`)](#3-modulo-2-interior-da-turma-virtual-sigaaavaindexjsf)
   - [3.1 Entrada na Turma Virtual e Lista Completa de Turmas com Codigo/CH](#31-entrada-na-turma-virtual-e-lista-completa-de-turmas-com-codigoch)
   - [3.2 Andamento das Aulas, Noticias da Turma e Download de Anexos](#32-andamento-das-aulas-noticias-da-turma-e-download-de-anexos)
   - [3.3 Topicos de Aula e Download de Materiais/Arquivos](#33-topicos-de-aula-e-download-de-materiaisarquivos)
   - [3.4 Mapa de Frequencias (Controle de Faltas)](#34-mapa-de-frequencias-controle-de-faltas)
   - [3.5 Notas Parciais, Avaliacoes e Tarefas da Turma](#35-notas-parciais-avaliacoes-e-tarefas-da-turma)
   - [3.6 Lista de Participantes (Docentes e Colegas) e Foruns da Turma](#36-lista-de-participantes-docentes-e-colegas-e-foruns-da-turma)
4. [Modulo 3: Historico, Boletim Geral, Documentos Oficiais e QR Code da Carteirinha](#4-modulo-3-historico-boletim-geral-documentos-oficiais-e-qr-code-da-carteirinha)
   - [4.1 Relatorio Geral de Notas de Todos os Semestres (`Consultar Minhas Notas`)](#41-relatorio-geral-de-notas-de-todos-os-semestres-consultar-minhas-notas)
   - [4.2 Todas as Turmas Anteriores (`/sigaa/portais/discente/turmas.jsf`)](#42-todas-as-turmas-anteriores-sigaaportaisdiscenteturmasjsf)
   - [4.3 Carteirinha Estudantil em PDF e Extracao do QR Code (`img4`)](#43-carteirinha-estudantil-em-pdf-e-extracao-do-qr-code-img4)
   - [4.4 Atestado de Matricula (com Nome dos Professores e Grade Semanal)](#44-atestado-de-matricula-com-nome-dos-professores-e-grade-semanal)
   - [4.5 Declaracao de Vinculo, Historico Escolar Oficial e Passe Livre Estudantil](#45-declaracao-de-vinculo-historico-escolar-oficial-e-passe-livre-estudantil)
   - [4.6 Meu Plano de Matriculas e Estrutura Curricular](#46-meu-plano-de-matriculas-e-estrutura-curricular)
5. [Modulo 4: Caixa Postal, Bolsas, Auxilios, Pesquisa e Extensao](#5-modulo-4-caixa-postal-bolsas-auxilios-pesquisa-e-extensao)
   - [5.1 Caixa Postal do SIGAA (`sig.unb.br/cxpostal`)](#51-caixa-postal-do-sigaa-sigunbbrcxpostal)
   - [5.2 Minhas Bolsas, Bolsa Auxilio e Cadastro Unico](#52-minhas-bolsas-bolsa-auxilio-e-cadastro-unico)
   - [5.3 Projetos de Pesquisa, Acoes de Extensao e Emissao de Certificados](#53-projetos-de-pesquisa-acoes-de-extensao-e-emissao-de-certificados)
6. [Modulo 5: Restaurante Universitario (`ru.unb.br`) - API WordPress e Parser de PDF com Alergenicos](#6-modulo-5-restaurante-universitario-ruunbbr---api-wordpress-e-parser-de-pdf-com-alergenicos)
   - [6.1 Endpoints Nativos da WordPress REST API (`wp-json/wp/v2`)](#61-endpoints-nativos-da-wordpress-rest-api-wp-jsonwpv2)
   - [6.2 Tabela de Precos por Grupo e Calculo de Refeicoes Restantes](#62-tabela-de-precos-por-grupo-e-calculo-de-refeicoes-restantes)
   - [6.3 Estrutura Interna dos PDFs Semanais dos 5 Campi](#63-estrutura-interna-dos-pdfs-semanais-dos-5-campi)
   - [6.4 Algoritmo de Extracao Vetorial + Mapeamento Textual dos 10 Icones de Alergenicos](#64-algoritmo-de-extracao-vetorial--mapeamento-textual-dos-10-icones-de-alergenicos)

---

## 1. Arquitetura de Autenticacao CAS SSO e Sessao JSF

O SIGAA da UnB utiliza o servidor **Apereo CAS (Central Authentication Service)** hospedado em `https://autenticacao.unb.br/sso-server/login`. Nao ha Cloudflare, reCAPTCHA ou Turnstile no fluxo de login.

### 1.1 Cadeia Completa de Redirecionamento do Login

```mermaid
sequenceDiagram
    participant App as UnB-Pocket (CookieJar)
    participant SIGAA as sigaa.unb.br
    participant CAS as autenticacao.unb.br
    participant SIG as sig.unb.br

    App->>SIGAA: 1. GET /sigaa/portais/discente/discente.jsf
    SIGAA-->>App: 302 Location: https://autenticacao.unb.br/sso-server/login?service=https%3A%2F%2Fsig.unb.br%2Fsigaa%2Flogin%2Fcas
    App->>CAS: 2. GET /sso-server/login?service=...
    CAS-->>App: 200 OK (HTML com #login-form, lt, execution, _eventId e action com jsessionid)
    App->>CAS: 3. POST /sso-server/login;jsessionid=...?service=... (username, password, lt, execution, _eventId=submit)
    CAS-->>App: 302 Location: https://sig.unb.br/sigaa/login/cas?ticket=ST-...
    App->>SIG: 4. GET /sigaa/login/cas?ticket=ST-...
    SIG-->>App: 301 Location: https://sigaa.unb.br/sigaa/login/cas?ticket=ST-...
    App->>SIGAA: 5. GET /sigaa/login/cas?ticket=ST-... (Define cookie JSESSIONID em sigaa.unb.br)
    SIGAA-->>App: 302 Location: https://sigaa.unb.br/sigaa/paginaInicial.do
    App->>SIGAA: 6. GET /sigaa/paginaInicial.do
    SIGAA-->>App: 302 Location: https://sigaa.unb.br/sigaa/telasPosSelecaoVinculos.jsf
    App->>SIGAA: 7. GET /sigaa/telasPosSelecaoVinculos.jsf
    SIGAA-->>App: 302 Location: https://sigaa.unb.br/sigaa/verPortalDiscente.do
    App->>SIGAA: 8. GET /sigaa/verPortalDiscente.do
    SIGAA-->>App: 302 Location: https://sigaa.unb.br/sigaa/portais/discente/discente.jsf
    App->>SIGAA: 9. GET /sigaa/portais/discente/discente.jsf
    SIGAA-->>App: 200 OK (Portal Discente autenticado, charset ISO-8859-1)
```

### 1.2 Extracao dos Tokens do Formulario CAS (Passo 2 -> Passo 3)

Ao fazer `GET https://autenticacao.unb.br/sso-server/login?service=https%3A%2F%2Fsig.unb.br%2Fsigaa%2Flogin%2Fcas`, extraia do HTML:

* **`action` do formulario**: `form#login-form[action]` (ex: `/sso-server/login;jsessionid=2171F88C6CF92C3F46109546E661C7CD.sso-server2?service=https%3A%2F%2Fsig.unb.br%2Fsigaa%2Flogin%2Fcas`)
* **`lt` (Login Ticket)**: `input[name="lt"][value]` (ex: `LT-21918-UrCcW0fPHElmffezVeif4JWm7bZNNd-sso-server2`)
* **`execution`**: `input[name="execution"][value]` (ex: `e1s1`)
* **`_eventId`**: `input[name="_eventId"][value]` (sempre `submit`)

**Requisicao de Login (Passo 3):**
* **Metodo e URL**: `POST https://autenticacao.unb.br<action_extraido>`
* **Content-Type**: `application/x-www-form-urlencoded`
* **Payload**:
  ```http
  username=<MATRICULA>&password=<SENHA>&lt=<LT>&execution=<EXECUTION>&_eventId=submit&submit=Submit
  ```

### 1.3 Como Funciona o Estado JSF (`javax.faces.ViewState` e `jscook_action`)

1. **Codificacao de Caracteres**: Todas as paginas do `sigaa.unb.br` sao entregues em `text/html;charset=ISO-8859-1` (`latin1`).
2. **`javax.faces.ViewState`**: Toda pagina JSF possui ao menos um `<input type="hidden" name="javax.faces.ViewState" id="javax.faces.ViewState" value="j_id1">`. Esse valor incrementa a cada `POST` na mesma sessao (`j_id1`, `j_id2`, `j_id3`...). **Sempre leia o `javax.faces.ViewState` da pagina atual antes de disparar um `POST`**.
3. **Reset Limpo para a Home (`GET /sigaa/verPortalDiscente.do`)**: Sempre que quiser voltar para a home do Portal Discente e obter um `javax.faces.ViewState` atualizado antes de chamar outro item do menu superior, faca um `GET https://sigaa.unb.br/sigaa/verPortalDiscente.do`. Ele redireciona (`302`) para `/sigaa/portais/discente/discente.jsf`.
4. **Acionamento do Menu Superior (`JSCookMenu`)**:
   Na home `discente.jsf`, o formulario `<form id="menu:form_menu_discente" action="/sigaa/portais/discente/discente.jsf" method="post">` contem:
   * `menu:form_menu_discente = menu:form_menu_discente`
   * `id = <ID_INTERNO_DISCENTE>` (extraido de `<input type="hidden" name="id" value="331956">`)
   * `jscook_action = <STRING_DA_ACAO>` (ex: `menu_form_menu_discente_discente_menu:A]#{ relatorioNotasAluno.gerarRelatorio }`)
   * `javax.faces.ViewState = <VIEWSTATE_ATUAL>`
5. **Tempo de Sessao**: O SIGAA inicia a sessao com timeout de **40 minutos** (`#spanRelogio` = `00:40`), renovado a cada requisicao autenticada.

---

## 2. Modulo 1: Portal Discente - Tela Inicial (`discente.jsf`)

* **Endpoint**: `GET https://sigaa.unb.br/sigaa/portais/discente/discente.jsf` (ou via `GET https://sigaa.unb.br/sigaa/verPortalDiscente.do`)
* **Autenticacao**: Cookie `JSESSIONID` do dominio `sigaa.unb.br`

### 2.1 Perfil e Dados Institucionais

| Campo | Seletor CSS / Localizacao no DOM | Exemplo Real Capturado |
| :--- | :--- | :--- |
| **`idDiscente`** | `#menu\:form_menu_discente input[name="id"]` (`value`) | `"331956"` |
| **`semestreAtual`** | `#info-usuario p.periodo-atual strong` | `"2026.2"` |
| **`nomeAbreviado`** | `#info-usuario p.usuario span` | `"Daniel Alexandro C. Junior"` |
| **`nomeCompleto`** | `#perfil-docente p.info-docente span.nome b` | `"Daniel Alexandro Contreira Junior"` |
| **`bioPerfil`** | Texto em `#perfil-docente p.info-docente` apos `span.nome` | `"Conhecimento em lingua inglesa..."` |
| **`fotoUrl`** | `#perfil-docente div.foto img[src]` | `"https://arquivos.unb.br/arquivos/.../fotro.png"` |
| **`unidadeDepartamento`** | `#info-usuario p.unidade` | `"DEPTO ENGENHARIA ELETRICA (11.01.01.11.03)"` |
| **`matricula`** | `#agenda-docente table tr` onde `td[0] == "Matrícula:"` -> `td[1]` | `"190026308"` |
| **`curso`** | `#agenda-docente table tr` onde `td[0] == "Curso:"` -> `td[1]` | `"ENGENHARIA DE REDES DE COMUNICAÇÃO/ENE - Bacharelado - MT"` |
| **`nivel`** | `#agenda-docente table tr` onde `td[0] == "Nível:"` -> `td[1]` | `"GRADUAÇÃO"` |
| **`status`** | `#agenda-docente table tr` onde `td[0] == "Status:"` -> `td[1]` | `"ATIVO"` |
| **`emailTruncado`** | `#agenda-docente table tr` onde `td[0] == "E-Mail:"` -> `td[1]` | `"daniel.contreira16@..."` |
| **`semestreEntrada`** | `#agenda-docente table tr` onde `td[0] == "Entrada:"` -> `td[1]` | `"2019.1"` |
| **`idCursoPortalPublico`** | Regex no script do menu: `curso/portal\.jsf\?id=(\d+)` | `"414330"` |

### 2.2 Indices Academicos (IRA e MP) e Integralizacao Curricular

Estao presentes diretamente na tabela `#agenda-docente` da tela inicial `discente.jsf`:

| Campo | Seletor / Regra de Extracao | Exemplo Real Capturado |
| :--- | :--- | :--- |
| **`ira`** | `td` seguinte a `<td><acronym title="Índice de Rendimento Acadêmico">IRA:</acronym></td>` | `3.2102` |
| **`mp`** | `td` seguinte a `<td><acronym title="Média Ponderada">MP:</acronym></td>` | `3.4563` |
| **`chObrigatoriaPendente`** | Linha `CH. Obrigatória Pendente` -> `td[1]` | `420` (horas) |
| **`chOptativaPendente`** | Linha `CH. Optativa Pendente` -> `td[1]` | `540` (horas) |
| **`chTotalCurriculo`** | Linha `CH. Total Currículo` -> `td[1]` | `3750` (horas) |
| **`chComplementarPendente`** | Linha `CH. Complementar Pendente` -> `td[1]` | `0` (horas) |
| **`percentualIntegralizadoExato`** | Atributo `style` da barra de progresso: `width:\s*([\d.]+)%` | `74.4` (%) |
| **`percentualIntegralizadoTexto`** | Texto da linha abaixo da barra (`74% Integralizado`) | `"74% Integralizado"` |

**Sub-endpoint opcional - Relatorio Detalhado de Indices Academicos:**
* **Metodo e URL**: `POST https://sigaa.unb.br/sigaa/portais/discente/discente.jsf`
* **Payload**:
  ```http
  menu:form_menu_discente=menu:form_menu_discente&id=<idDiscente>&jscook_action=menu_form_menu_discente_discente_menu:A]#{ indiceAcademicoMBean.selecionaDiscente }&javax.faces.ViewState=<ViewState>
  ```
* **Retorno**: Redireciona/renderiza `/sigaa/graduacao/discente/relatorio_indices_discente.jsf` com cabecalho institucional, cidade (`BRASÍLIA`), tabela `IRA` e `MP`, explicacao oficial das formulas e codigo de autenticacao do documento (ex: `documento 3276304`, `código de verificação d67de6ce99`).

### 2.3 Extrato e Saldo do Restaurante Universitario no SIGAA

Na tela inicial `discente.jsf`, logo abaixo da tabela de Integralizacoes, existe o formulario `<form id="formExibirExtrato">`.

* **Como extrair o parametro dinamico do botao**:
  Localize `#formExibirExtrato a[onclick]` e extraia a chave de `{'formExibirExtrato:j_id_jsp_...':'formExibirExtrato:j_id_jsp_...'}` (atualmente `formExibirExtrato:j_id_jsp_340461267_282`).
* **Requisicao**:
  * **Metodo e URL**: `POST https://sigaa.unb.br/sigaa/portais/discente/discente.jsf`
  * **Payload**:
    ```http
    formExibirExtrato=formExibirExtrato&formExibirExtrato:j_id_jsp_340461267_282=formExibirExtrato:j_id_jsp_340461267_282&javax.faces.ViewState=<ViewState>
    ```
* **Estrutura HTML Retornada**:
  O SIGAA substitui o formulario `#formExibirExtrato` por um `<div style="background-color: #EFF3FA; margin: 5px">` contendo `<h4>Extrato no Restaurante Universitário</h4>`, `<p>(Últimos 7 dias)</p>` e uma `<table width="100%">` com 3 colunas por linha (`Data/Hora`, `Descricao`, `Valor`):
  ```html
  <tr>
    <td align="right"><div>22/11/2026 20:43</div></td>
    <td align="right"><div>Saldo</div></td>
    <td align="right"><div>R$ 8,65</div></td>
  </tr>
  <tr>
    <td align="right"><div>16/11/2026 17:37</div></td>
    <td align="right"><div>Grupo 4 Jantar</div></td>
    <td align="right"><div>-R$ 2,50</div></td>
  </tr>
  <tr>
    <td align="right"><div>15/11/2026 20:43</div></td>
    <td align="right"><div>Saldo Anterior</div></td>
    <td align="right"><div>R$ 11,15</div></td>
  </tr>
  ```
* **Dados Derivados para o UnB-Pocket**:
  1. **`saldoAtual`**: Linha onde `td[1] == "Saldo"` -> `td[2]` (`R$ 8,65` -> `8.65`).
  2. **`saldoAnterior7Dias`**: Linha onde `td[1] == "Saldo Anterior"` -> `td[2]` (`R$ 11,15` -> `11.15`).
  3. **`grupoAcessoRu`**: Regex `Grupo\s+(\d+|I{1,3}|IV)` nas linhas de transacao (ex: `"Grupo 4"` -> Grupo IV, onde Cafe custa `R$ 1,50` e Almoco/Jantar custam `R$ 2,50`).
  4. **`refeicoesRestantes`**: Com `R$ 8,65` no `Grupo 4`, o app calcula automaticamente: **3 Almocos/Jantares** (`3 x R$ 2,50 = R$ 7,50`, sobra `R$ 1,15`) ou **5 Cafes da Manha** (`5 x R$ 1,50 = R$ 7,50`).

### 2.4 Feed das 10 Ultimas Atualizacoes das Turmas

No topo do painel `#turmas-portal` em `discente.jsf`, existe o formulario `#formAtualizacoesTurmas` com `<div id="atualizacoes-turma"><div class="rotator">`.
Visualmente o navegador mostra apenas 1 noticia por vez, mas **as 10 ultimas atualizacoes ja estao todas presentes no HTML inicial** como elementos `<table>` filhos de `div.rotator` (1 com `display: table` e 9 com `display: none`).

* **Seletor de cada item**: `#atualizacoes-turma div.rotator > table`
* **Campos por item**:
  * **`data`**: Texto antes do `-` no primeiro `<tr><td>` (ex: `"22/11/2026"`).
  * **`disciplinaSemestre`**: Texto da tag `<a>` no primeiro `<tr><td>` (ex: `"LABORATÓRIO DE PRINCÍPIOS DE COMUNICAÇÃO (2026.2)"`).
  * **`idTurma`**: Extraido via regex `'idTurma':'(\d+)'` do atributo `onclick` da tag `<a>` (ex: `"1616625"`).
  * **`tituloNoticia`**: Texto do segundo `<tr><td>` (ex: `"Nova Notícia: Experimento 11 - Roteiro"`).

### 2.5 Turmas do Semestre e Decodificador de Horarios SIGAA

Na tabela principal de `#turmas-portal` em `discente.jsf`:

* **Seletor das linhas de turmas**: `#turmas-portal > table:nth-of-type(2) > tbody > tr`
* **Estrutura de cada par de `<tr>`**:
  1. **Linha de dados (`tr.odd` ou `tr`)**:
     * **`nomeDisciplina`**: `td.descricao form a` (ex: `"REDES SEM FIO"`).
     * **`formId`**: `td.descricao form[id]` (ex: `"form_acessarTurmaVirtualj_id_3"`).
     * **`actionKey`**: Extraido do `onclick` em `td.descricao form a`: chave que comeca com `form_acessarTurmaVirtual` (ex: `"form_acessarTurmaVirtualj_id_3:j_id_jsp_340461267_298j_id_3"`).
     * **`frontEndIdTurma`**: Extraido do `onclick`: `'frontEndIdTurma':'([A-F0-9]{40})'` (ex: `"7D4F1E83699CC81005304EC1877AE1F7888882CD"`).
     * **`local`**: `td.info:nth-of-type(2)` (ex: `"FT AT-31/12 (Espaço Sérgio Barroso)"`).
     * **`codigoHorario`**: Texto principal de `td.info:nth-of-type(3)` antes da `<img src="/shared/img/geral/ajuda.gif">` (ex: `"24M12"` ou `"3M12 (10/08/2026 - 14/12/2026)"`).
     * **`horarioExtensoPopUp`**: Texto dentro de `td.info:nth-of-type(3) div.popUp` separado por `<br>` (ex: `"Segunda-feira 08:00 às 09:50"`, `"Quarta-feira 08:00 às 09:50"`).
  2. **Linha oculta seguinte (`<tr><td colspan="5" id="linha_<idTurma>" style="display: none;">`)**:
     * **`idTurma`**: Extraido do `id` da `<td>` (`linha_1614167` -> `"1614167"`).

#### Regra de Decodificacao do Codigo de Horario SIGAA (`[Dias][Turno][Tempos]`)
Todo codigo de horario da UnB segue a gramatica `([2-7]+)([MTN])([1-6]+)`:
* **Dias da semana**: `2` = Segunda, `3` = Terca, `4` = Quarta, `5` = Quinta, `6` = Sexta, `7` = Sabado.
* **Turnos e Faixas de Horario**:
  * **Manha (`M`)**:
    * `M1`: `08:00 - 08:55` | `M2`: `08:55 - 09:50` | `M3`: `10:00 - 10:55` | `M4`: `10:55 - 11:50` | `M5`: `12:00 - 12:55`
  * **Tarde (`T`)**:
    * `T1`: `12:55 - 13:50` | `T2`: `14:00 - 14:55` | `T3`: `14:55 - 15:50` | `T4`: `16:00 - 16:55` | `T5`: `16:55 - 17:50` | `T6`: `18:00 - 18:55`
  * **Noite (`N`)**:
    * `N1`: `19:00 - 19:50` | `N2`: `19:50 - 20:40` | `N3`: `20:50 - 21:40` | `N4`: `21:40 - 22:30`

### 2.6 Forum do Curso (Listagem e Leitura de Topicos)

No painel `#forum-portal` da tela inicial `discente.jsf`:

* **Seletor da tabela**: `#forum-portal table.listagem tbody tr`
* **Campos de cada topico na listagem**:
  * **`titulo`**: `td:nth-child(1) a` (ex: `"Levantamento de demanda por disciplina optativa - Redes Sem Fio"`).
  * **`actionKey`**: `td:nth-child(1) a[id]` (ex: `"j_id_jsp_340461267_341:mostrarForumMensagemCursoj_id_1"`).
  * **`idForumMensagem`**: Regex `'idForumMensagem':'(\d+)'` no `onclick` (ex: `"24412414"`).
  * **`idForum`**: Regex `'id':'(\d+)'` no `onclick` (ex: `"711370"`).
  * **`nomeAutor`**: `td:nth-child(2) acronym[title]` (ex: `"JOAO JOSE COSTA GONDIM"`).
  * **`usuarioAutor`**: Texto de `td:nth-child(2) acronym` (ex: `"33374600468"`).
  * **`respostas`**: `td:nth-child(3)` (ex: `4`).
  * **`dataUltimaPostagem`**: `td:nth-child(4)` (ex: `"16/11/2026 14:17:47"`).

**Como abrir um topico do Forum do Curso para ler as mensagens e respostas:**
* **Metodo e URL**: `POST https://sigaa.unb.br/sigaa/portais/discente/discente.jsf`
* **Payload**:
  ```http
  j_id_jsp_340461267_341=j_id_jsp_340461267_341&<actionKey>=<actionKey>&idForumMensagem=<idForumMensagem>&id=<idForum>&javax.faces.ViewState=<ViewState>
  ```
  *(Nota: obtenha o `id` do `<form>` pai de `#forum-portal table.listagem`, atualmente `j_id_jsp_340461267_341`).*
* **Estrutura HTML das mensagens do topico**:
  Cada postagem/resposta e renderizada em pares de `<tr>`:
  * **Linha 1 (Cabecalho da mensagem)**: Data/hora (`16/11/2026 16:33:11`) e autor (`Postado por: JOAO JOSE COSTA GONDIM`).
  * **Linha 2 (Corpo da mensagem)**: Foto do autor na primeira `<td>` e texto completo da mensagem na segunda `<td>`.

---

## 3. Modulo 2: Interior da Turma Virtual (`/sigaa/ava/index.jsf`)

### 3.1 Entrada na Turma Virtual e Lista Completa de Turmas com Codigo/CH

Para entrar no Ambiente Virtual de Aprendizagem (AVA / Turma Virtual) de uma disciplina a partir de `discente.jsf`:

* **Metodo e URL**: `POST https://sigaa.unb.br/sigaa/portais/discente/discente.jsf`
* **Payload**:
  ```http
  <formId>=<formId>&<actionKey>=<actionKey>&frontEndIdTurma=<frontEndIdTurma>&javax.faces.ViewState=<ViewState>
  ```
  Exemplo real para `LABORATÓRIO DE PRINCÍPIOS DE COMUNICAÇÃO`:
  ```http
  form_acessarTurmaVirtualj_id_2=form_acessarTurmaVirtualj_id_2&form_acessarTurmaVirtualj_id_2:j_id_jsp_340461267_298j_id_2=form_acessarTurmaVirtualj_id_2:j_id_jsp_340461267_298j_id_2&frontEndIdTurma=B23FC005EF33B52CE0E03706BB07F27A40C8BCD0&javax.faces.ViewState=j_id1
  ```

> [!TIP]
> **Descoberta importante no DOM da Turma Virtual (`#formTurma`):**
> Na home `discente.jsf`, a tabela de turmas **nao** informa o codigo da disciplina (`ENE0295`) nem a carga horaria (`30h`). Porem, ao abrir qualquer Turma Virtual, o modal oculto `<form id="formTurma">` no final da pagina lista **todas as turmas do semestre do aluno** no formato:
> `CODIGO - NOME DA DISCIPLINA (CHh) (SEMESTRE)` junto com o respectivo `frontEndIdTurma`!
> Exemplo extraido de `#formTurma a.linkTurma span`:
> * `ECL0014 - CIENCIAS DO AMBIENTE (30h) (2026.2)` (`48DAD70AD75C30DAA0F2E07924128A1A0E4E8DAD`)
> * `CIC0191 - INTRODUÇÃO AO DESENVOLVIMENTO DE JOGOS (60h) (2026.2)` (`E9C2241F28B98545974ADB76802231DAD9541231`)
> * `ENE0295 - LABORATÓRIO DE PRINCÍPIOS DE COMUNICAÇÃO (30h) (2026.2)` (`B23FC005EF33B52CE0E03706BB07F27A40C8BCD0`)
> * `ENE0064 - REDES SEM FIO (60h) (2026.2)` (`7D4F1E83699CC81005304EC1877AE1F7888882CD`)
> * `ENE0211 - TEORIA DA INFORMACAO (60h) (2026.2)` (`A9033012F7DDB6554924A2B7B9CEFBB398E3154D`)
>
> E possivel trocar de uma Turma Virtual diretamente para outra sem voltar a home enviando `POST https://sigaa.unb.br/sigaa/ava/index.jsf` com os dados de `#formTurma` e o `frontEndIdTurma` desejado.

### 3.2 Andamento das Aulas, Noticias da Turma e Download de Anexos

Na tela principal da Turma Virtual:

1. **Cabecalho da Turma (`#nomeTurma`)**:
   * `#linkCodigoTurma`: `"ENE0295 - "`
   * `#linkNomeTurma`: `"LABORATÓRIO DE PRINCÍPIOS DE COMUNICAÇÃO"`
   * `#linkPeriodoTurma`: `"- T04 (2026.2 - 2T45)"`
2. **Andamento das Aulas (`#barraDireita`)**:
   * Texto `Aulas (Ministradas/Total): <i>24 / 32</i>` -> `aulasMinistradas = 24`, `aulasTotal = 32`.
   * Barra `.progress-bar`: `75%` (`% de Carga Horária Ministrada`).
3. **Ultima Noticia Completa (`#ultimaNoticia`)**:
   * `h4`: Titulo e data/hora (`"Experimento 11 - Roteiro - 22/11/2026 09:34"`).
   * `span.conteudoNoticia`: HTML completo da noticia escrita pelo professor.
   * `a[href^="/sigaa/verArquivo"]`: Link direto de download do anexo da noticia!
     * Formato: `GET https://sigaa.unb.br/sigaa/verArquivo?idArquivo=4245329&key=61b389b1ce00f824fe8986e3b7d2e95e`
     * Texto do link: Nome do arquivo (`"Exp11_Sincronismo.zip"`).
   * Autor: `small i` apos `Cadastrado por:` (`"LEONARDO AGUAYO"`).
4. **Historico de Noticias da Turma (`#barraDireita` ou Menu `Notícias`)**:
   * Na barra direita (`#j_id_jsp_1879301362_155_body`), cada noticia possui data/hora, titulo em `<i>` e um `<form>` com `<input type="hidden" name="id" value="24442200">` para abrir qualquer noticia anterior.
   * Ou pelo menu lateral esquerdo `#formMenu`: enviando `POST /sigaa/ava/index.jsf` com `formMenu=formMenu`, `formMenu:j_id_jsp_1879301362_88=formMenu:j_id_jsp_1879301362_88` (`Notícias`) e `javax.faces.ViewState`.

### 3.3 Topicos de Aula e Download de Materiais/Arquivos

No painel central `#formAva`:

* **Seletor dos Topicos de Aula**: `div.topico-aula` (`div[id$=":topico_aula"]`)
  * **Titulo e Periodo do Topico**: `div.titulo` (ex: `"Experimentos (10/08/2026 - 14/12/2026)"`).
  * **Conteudo/Texto do Topico**: `div.conteudotopico > p`.
  * **Itens de Material / Arquivo Anexado**: `div.conteudotopico div.item`
    * **Tipo do arquivo (icone)**: `img[src*="/porta_arquivos/icones/"]` (`zip.png`, `pdf.png`, `doc.png`, `ppt.png`, `txt.png`).
    * **Nome do material**: Texto de `a` (ex: `"Experimento 01"`).
    * **Parametros de Download do Arquivo**: Extraidos do `onclick` da tag `<a>`:
      ```javascript
      jsfcljs(document.getElementById('formAva'), {
        'formAva:j_id_jsp_1879301362_314:0:listaMateriais:0:j_id_jsp_1879301362_377': 'formAva:j_id_jsp_1879301362_314:0:listaMateriais:0:j_id_jsp_1879301362_377',
        'id': '445F1CEF4EFBE7DDCC8AB673E127D7A5C24C9C3D'
      }, '_blank');
      ```
    * **Requisicao para baixar o arquivo**:
      * **Metodo e URL**: `POST https://sigaa.unb.br/sigaa/ava/index.jsf`
      * **Payload**:
        ```http
        formAva=formAva&formAva:idTopicoSelecionado=0&<actionKeyMaterial>=<actionKeyMaterial>&id=<HASH_40_CHARS>&javax.faces.ViewState=<ViewState>
        ```
      * **Resposta**: Stream binario do arquivo com header `Content-Disposition: attachment; filename="..."`.

### 3.4 Mapa de Frequencias (Controle de Faltas)

Dentro da Turma Virtual, o menu lateral esquerdo `<form id="formMenu" action="/sigaa/ava/index.jsf" method="post">` possui os links de navegacao da turma.

* **Requisicao para abrir Frequencia**:
  * **Metodo e URL**: `POST https://sigaa.unb.br/sigaa/ava/index.jsf`
  * **Payload**:
    ```http
    formMenu=formMenu&formMenu:j_id_jsp_1879301362_71=formMenu:j_id_jsp_1879301362_94&formMenu:j_id_jsp_1879301362_97=formMenu:j_id_jsp_1879301362_97&javax.faces.ViewState=<ViewState>
    ```
    *(Dica: Em vez de fixar o sufixo `1879301362_97`, selecione no HTML de `#formMenu` a tag `<a>` cujo `div.itemMenu` interno tem texto exato `"Frequência"` e extraia a chave de seu `onclick`).*
* **Dados Entregues no `fieldset` (`Mapa de Frequências`)**:
  * Quando o professor ja lancou chamadas: tabela com `Data` e `Situação` (`Presente` ou quantidade de `Faltas` naquele dia), alem do resumo no rodape.
  * Resumo estatistico sempre presente:
    * `Presenças Registradas: <int>` (ex: `32`)
    * `Número de Aulas com Registro de Frequência: <int>` (ex: `32`)
    * `Porcentagem de Frequência em relação as Aulas com Registro de Frequência: <int>%` (ex: `100%`)
    * `Número de Aulas definidas pela CH do Componente: <int>` (ex: `32` para 30h, `64` para 60h)
    * `Porcentagem de Frequência em relação a CH: <int>%` (ex: `100%`)
  * **Regra de Calculo para o App**: O limite maximo de faltas na UnB e `25%` de `Número de Aulas definidas pela CH do Componente` (ex: `8` faltas em disciplina de 30h/32 aulas; `16` faltas em disciplina de 60h/64 aulas).

### 3.5 Notas Parciais, Avaliacoes e Tarefas da Turma

Todos acionados via `POST https://sigaa.unb.br/sigaa/ava/index.jsf` usando o formulario `#formMenu` (extraindo a chave do `onclick` da tag `<a>` correspondente ao texto do `.itemMenu`):

| Item do Menu Lateral | Texto de `.itemMenu` | Chave observada no DOM | O que entrega |
| :--- | :--- | :--- | :--- |
| **Ver Notas** | `"Ver Notas"` | `formMenu:j_id_jsp_1879301362_99` | Tabela de unidades/avaliacoes (`Nota 1`, `Nota 2`, `Recuperacao`, `Media Final`, `Faltas`, `Situacao`) ou alerta `"Ainda não foram lançadas notas."` |
| **Avaliacoes** | `"Avaliações"` | `formMenu:j_id_jsp_1879301362_126` | Lista de provas/exames cadastrados no calendario da turma (`Data`, `Hora`, `Descricao`) |
| **Tarefas** | `"Tarefas"` | `formMenu:j_id_jsp_1879301362_128` | Lista de entregas/trabalhos (`Titulo`, `Periodo de Entrega`, `Nota Maxima`, `Possui Arquivo`, `Em Grupo`) |
| **Questionarios** | `"Questionários"` | `formMenu:j_id_jsp_1879301362_130` | Lista de questionarios online da turma com prazo de abertura/fechamento |
| **Arquivos** | `"Arquivos"` | `formMenu:j_id_jsp_1879301362_124` | Lista consolidada de todos os arquivos enviados pelo docente na turma |

### 3.6 Lista de Participantes (Docentes e Colegas) e Foruns da Turma

* **Participantes (`Turma > Participantes`)**:
  * **Acionamento**: `POST https://sigaa.unb.br/sigaa/ava/index.jsf` com a chave do link `.itemMenu == "Participantes"` (`formMenu:j_id_jsp_1879301362_82`).
  * **Estrutura Retornada**:
    1. **Grupo `Docentes (N)`**:
       * Foto do professor (`img`)
       * Status online/offline (`img[alt*="SIGAA"]`)
       * Nome completo e link para pagina publica SIAPE (`a[href*="/sigaa/public/docente/portal.jsf"]`, ex: `LEONARDO AGUAYO`, `siape=2793461`)
       * `Departamento` (`DEPTO ENGENHARIA ELETRICA`), `Formação` (`DOUTORADO`), `Usuário(a)`, `E-Mail` (`aguayo@unb.br`).
    2. **Grupo `Discentes (N)`**:
       * Tabela em grade (2 alunos por `<tr>`):
       * Foto do colega, Status (`Usuário On-Line no SIGAA` / `Usuário Off-Line no SIGAA`), `Nome Completo`, `Curso` (ex: `ENGENHARIA ELÉTRICA/ENE`, `ENGENHARIA DE COMPUTAÇÃO/CIC`), `Matrícula`, `Usuário(a)` e `E-mail`.
* **Foruns da Turma (`Turma > Fóruns`)**:
  * **Acionamento**: `POST https://sigaa.unb.br/sigaa/ava/index.jsf` com a chave do link `.itemMenu == "Fóruns"` (`formMenu:j_id_jsp_1879301362_85`).

---

## 4. Modulo 3: Historico, Boletim Geral, Documentos Oficiais e QR Code da Carteirinha

Todas as acoes abaixo sao disparadas a partir de `discente.jsf` submetendo o formulario `#menu:form_menu_discente`:

* **URL**: `POST https://sigaa.unb.br/sigaa/portais/discente/discente.jsf`
* **Payload Padrao**:
  ```http
  menu:form_menu_discente=menu:form_menu_discente&id=<idDiscente>&jscook_action=<JSCOOK_ACTION>&javax.faces.ViewState=<ViewState>
  ```

### 4.1 Relatorio Geral de Notas de Todos os Semestres (`Consultar Minhas Notas`)

* **`jscook_action`**:
  ```text
  menu_form_menu_discente_discente_menu:A]#{ relatorioNotasAluno.gerarRelatorio }
  ```
* **Retorno (`200 OK`, HTML)**:
  Renderiza a pagina `Relatório de Notas do(a) Aluno(a)` contendo uma `<table class="tabelaRelatorio">` para **cada semestre cursado pelo aluno desde o ingresso** (`2026.2`, `2026.1`, `2025.2`, `2025.1`, `2024.2`, `2024.1`, `2023.2`, `2023.1`, `2022.2`, `2022.1`, `2021.2`, `2021.1`, `2020.2`, `2020.1`, `2019.2`, `2019.1`).
* **Estrutura de cada tabela de semestre**:
  * **`semestre`**: `table > caption` (ex: `"2026.1"`).
  * **Colunas (`tbody > tr`)**:
    1. **`codigo`**: `td:nth-child(1)` (ex: `"ENE0045"`)
    2. **`disciplina`**: `td:nth-child(2)` (ex: `"ELETRÔNICA"`)
    3. **`nota1`**: `td:nth-child(3)` (ex: `"SS"`, `"-"`)
    4. **`resultadoMencao`**: `td:nth-child(4)` (Mencao final da UnB: `"SS"`, `"MS"`, `"MM"`, `"MI"`, `"II"`, `"SR"`, ou `"--"`)
    5. **`faltas`**: `td:nth-child(5)` (ex: `0`, `14`, `46`)
    6. **`situacao`**: `td:nth-child(6)` (`"APROVADO(A)"`, `"REPROVADO(A)"`, `"REPROVADO(A) POR FALTAS"`, ou `"--"`)

### 4.2 Todas as Turmas Anteriores (`/sigaa/portais/discente/turmas.jsf`)

* **Metodo e URL**: `GET https://sigaa.unb.br/sigaa/portais/discente/turmas.jsf` (nao precisa de `POST`, pode ser acessado via `GET` direto com o cookie `JSESSIONID`!)
* **Retorno (`200 OK`, HTML)**:
  Tabela `Todas as Turmas Virtuais (70)` agrupada por semestre (`2026.2` ate `2019.1`).
* **Colunas de cada linha**:
  * **`codigoNome`**: `td:nth-child(1)` (ex: `"ENE0045 - ELETRÔNICA"`)
  * **`numeroTurma`**: `td:nth-child(2)` (ex: `"01"`)
  * **`cargaHoraria`**: `td:nth-child(3)` (ex: `"60h"`)
  * **`horario`**: `td:nth-child(4)` (ex: `"24T23 (16/03/2026 - 18/07/2026)"`)
  * **`linkTurmaVirtual`**: `td:nth-child(5) a[onclick]` (permite abrir a Turma Virtual de semestres passados para baixar materiais antigos).

### 4.3 Carteirinha Estudantil em PDF e Extracao do QR Code (`img4`)

* **`jscook_action`**:
  ```text
  menu_form_menu_discente_discente_menu:A]#{ portalDiscente.emitiCarteiraEstudantilUnb }
  ```
* **Retorno (`200 OK`, Binario PDF)**:
  * `Content-Type: application/pdf`
  * `Content-Disposition: attachment; filename=CarteirinhaDiscenteUNB_190026308.pdf`
  * Tamanho aproximado: `532 KB` (1 pagina de `240 x 310 pt`).

#### Anatomia Interna do PDF da Carteirinha Estudantil (Verificada via REA + `pdfplumber`)

O PDF da Carteirinha Estudantil contem **texto vetorial selecionavel** e exatamente **4 objetos de imagem (`XObject /Subtype /Image` compactados com `/FlateDecode`)**:

| Objeto / Campo | Coordenadas no PDF (`x0, top, x1, bottom`) | Resolucao Nativa (`srcsize`) | Conteudo |
| :--- | :--- | :--- | :--- |
| **Texto Vetorial** | Linhas de texto da pagina 1 | Vetorial | Nome completo, Matricula (`190026308`), Validade (`VALIDADE Março 2027`), CPF (`033.145.241-37`), Nivel (`GRADUAÇÃO`), Curso (`ENGENHARIA DE REDES DE COMUNICAÇÃO`) |
| **`img0`** | `(0.0, 0.0, 240.0, 307.0)` | `1050 x 1388` RGB | Arte de fundo oficial da Identidade Estudantil UnB |
| **`img2`** | `(171.0, 47.0, 212.0, 100.0)` | `500 x 625` RGB | **Foto 3x4 oficial do estudante** em alta resolucao |
| **`img4`** | `(55.0, 154.0, 185.0, 278.0)` | **`250 x 250` RGB** | **QR Code da Carteirinha Estudantil** (`187.500` bytes descomprimidos) |
| **`img5`** | `(91.0, 252.0, 148.25, 302.0)` | `300 x 262` RGB | Rubrica do Secretario de Administracao Academica (SAA/UnB) |

#### Como Extrair o QR Code (Imagem PNG/Base64 + Payload Numerico Decodificado)

O objeto `img4` (`srcsize == (250, 250)`) e um bitmap RGB puro (`250 * 250 * 3 = 187.500 bytes`) que codifica o **identificador numerico de 16 digitos da carteirinha do aluno** (ex: `"7744502674419362"`), utilizado pelas catracas e leitores do RU/Biblioteca.

```python
import io
import base64
import numpy as np
import pdfplumber
from PIL import Image
import cv2

def extrair_dados_e_qrcode_carteirinha(pdf_bytes: bytes) -> dict:
    with pdfplumber.open(io.BytesIO(pdf_bytes)) as pdf:
        page = pdf.pages[0]
        linhas = [line.strip() for line in (page.extract_text() or "").splitlines() if line.strip()]

        qr_png_b64 = None
        qr_payload = None
        foto_png_b64 = None

        for im in page.images:
            w, h = im["srcsize"]
            raw = im["stream"].get_data()

            # Converte os bytes RGB descomprimidos do FlateDecode em PIL.Image
            if len(raw) == w * h * 3:
                arr = np.frombuffer(raw, dtype=np.uint8).reshape((h, w, 3))
                pil_img = Image.fromarray(arr)
            else:
                pil_img = Image.open(io.BytesIO(raw)).convert("RGB")

            # img4 (250x250) = QR Code da Carteirinha
            if (w, h) == (250, 250) or im["name"] == "img4":
                buf = io.BytesIO()
                pil_img.save(buf, format="PNG")
                qr_png_b64 = base64.b64encode(buf.getvalue()).decode("ascii")

                # Decodifica o texto do QR Code (ex: "7744502674419362")
                gray = np.array(pil_img.convert("L"))
                detector = cv2.QRCodeDetector()
                val, _, _ = detector.detectAndDecode(gray)
                qr_payload = val or None

            # img2 (500x625) = Foto 3x4 do aluno
            elif (w, h) == (500, 625) or im["name"] == "img2":
                buf = io.BytesIO()
                pil_img.save(buf, format="PNG")
                foto_png_b64 = base64.b64encode(buf.getvalue()).decode("ascii")

        return {
            "linhas_texto": linhas,
            "qr_code_valor": qr_payload,      # Ex: "7744502674419362" (pode ser re-renderizado em SVG no app!)
            "qr_code_png_base64": qr_png_b64, # Imagem 250x250 original extraida sem perda
            "foto_3x4_png_base64": foto_png_b64
        }
```

### 4.4 Atestado de Matricula (com Nome dos Professores e Grade Semanal)

* **`jscook_action`**:
  ```text
  menu_form_menu_discente_discente_menu:A]#{ portalDiscente.atestadoMatriculaUnb }
  ```
* **Retorno (`200 OK`, HTML)**:
  Esta pagina e a melhor fonte para enriquecer a grade horaria do aluno porque entrega em uma unica requisicao:
  1. **Cabecalho do Vínculo**: `Período Letivo: 2026.2 (10/08/2026 à 14/12/2026)`, `Nível: GRADUAÇÃO`, `Matrícula: 190026308`, `Vínculo: REGULAR`, `Nome`, `Curso: ENGENHARIA DE REDES DE COMUNICAÇÃO/ENE - BACHARELADO - DIURNO`.
  2. **Tabela `Turmas Matriculadas: N`** (com o **Nome do Professor** e o **Codigo da Disciplina**):
     * `td:nth-child(1)`: **Codigo** (`ECL0014`, `CIC0191`, `ENE0295`, `ENE0064`, `ENE0211`)
     * `td:nth-child(2)`: **Disciplina + Professor + Tipo + Local** (ex: `"LABORATÓRIO DE PRINCÍPIOS DE COMUNICAÇÃO"`, docente `"LEONARDO AGUAYO"`, `"Tipo: DISCIPLINA Local: SG-11 A1-09/12 (Lab. de Princípios de Comunicação)"`)
     * `td:nth-child(3)`: **Numero da Turma** (`"04"`, `"01"`)
     * `td:nth-child(4)`: **Status** (`"MATRICULADO(A)"`)
     * `td:nth-child(5)`: **Horario** (`"2T45"`, `"24M12"`, `"35T23"`)
  3. **Tabela Semanal `Tabela de Horários:`**:
     Matriz pronta com colunas `Horários | Dom | Seg | Ter | Qua | Qui | Sex | Sab` e linhas para cada bloco de 55 minutos (`08:00 - 08:55`, `08:55 - 09:50`, `14:00 - 14:55`, `14:55 - 15:50`, `16:00 - 16:55`, `16:55 - 17:50`) preenchidas com o codigo da disciplina ou `---`.
  4. **Autenticacao do Documento**: Codigo de verificacao (ex: `3c610b1b69`) verificavel em `https://sigaa.unb.br/sigaa/documentos/`.

### 4.5 Declaracao de Vinculo, Historico Escolar Oficial e Passe Livre Estudantil

Todos acionados via `POST https://sigaa.unb.br/sigaa/portais/discente/discente.jsf` no `#menu:form_menu_discente`:

| Documento | Valor de `jscook_action` | Formato Retornado | Conteudo Extraivel |
| :--- | :--- | :--- | :--- |
| **Declaracao de Vinculo** | `menu_form_menu_discente_discente_menu:A]#{ declaracaoVinculo.emitirDeclaracao}` | `application/pdf` (`declaracao_190026308.pdf`) | PDF oficial de 1 pagina assinado eletronicamente com nome, matricula, curso/turno, data e codigo de verificacao (`5c49c94b22`). |
| **Historico Escolar Oficial** | `menu_form_menu_discente_discente_menu:A]#{ portalDiscente.historico }` | `application/pdf` (`historico_190026308.pdf`) | PDF oficial de 4 paginas contendo: Curriculo (`6351/1 - 2018.2`), Periodo Letivo Atual (`16`), Prazo para Conclusao Padrao/Maximo (`2026.1 / 2031.1`), Prorrogacoes (`5 períodos letivos`), IRA/MP, todas as disciplinas cursadas/matriculadas com mencao, frequencia e carga horaria, ENADE, componentes obrigatorios pendentes e codigo de autenticacao. |
| **Declaracao Passe Livre Estudantil** | `menu_form_menu_discente_discente_menu:A]#{ portalDiscente.passeEstudantilUnb }` | `text/html` | Semelhante ao Atestado de Matricula, acrescido de `Mãe`, `Data de Nascimento`, `Período Letivo Atual: 16`, tabela de turmas, grade de horarios e codigo de verificacao para o Transporte Urbano do DF (BRB Mobilidade). |

### 4.6 Meu Plano de Matriculas e Estrutura Curricular

* **Meu Plano de Matriculas**:
  * **`jscook_action`**: `menu_form_menu_discente_discente_menu:A]#{ planoMatriculaBean.gerar }`
  * **Retorno (`text/html`)**: Pagina `Portal Discente > Plano de Matrícula 2026.2` com tabela `Plano de Matrícula` contendo colunas `Turma`, `Componente Curricular`, `Cadastrada em`, `Situação` (ex: `MATRICULADO`, `INDEFERIDO`, `EM ESPERA`), `Analisada em`, `Analisada por`.
* **Estrutura Curricular / Fluxograma do Curso**:
  * **`jscook_action`**: `menu_form_menu_discente_discente_menu:A]#{ curriculo.popularBuscaGeral }` (abre a busca de estrutura curricular em `discente.jsf`).
  * **Atalho Publico Direto do Curso do Aluno (sem precisar de login)**:
    Usando o `idCursoPortalPublico` extraido do menu da home (`414330` para Engenharia de Redes):
    `GET https://sigaa.unb.br/sigaa/public/curso/portal.jsf?id=414330&lc=pt_BR` e `GET https://sigaa.unb.br/sigaa/public/curso/curriculo.jsf?lc=pt_BR&id=414330` entregam os curriculos ativos do curso e a lista de disciplinas por nivel/semestre e pre-requisitos.

---

## 5. Modulo 4: Caixa Postal, Bolsas, Auxilios, Pesquisa e Extensao

### 5.1 Caixa Postal do SIGAA (`sig.unb.br/cxpostal`)

* **Endpoint de Entrada**: `GET https://sigaa.unb.br/sigaa/abrirCaixaPostal.jsf?sistema=2`
* **Redirecionamento**: Redireciona automaticamente para `https://sig.unb.br/cxpostal/caixa_postal.jsf` autenticando via CAS.
* **Estrutura da Tabela `#formCaixaPostal` (`table` com caption `Caixa de Entrada`)**:
  * Total de registros: exibido em `em` (ex: `"36 Registro(s) Encontrado(s)"`).
  * Paginacao: `<select>` com `Pag. 1`, `Pag. 2`, etc.
  * **Colunas de cada mensagem (`tbody > tr`)**:
    1. **`lida`**: `td:nth-child(2) img[alt]` (`"Não Lida"` vs `"Lida"`).
    2. **`assunto`**: `td:nth-child(2) a:nth-of-type(2)` (ex: `"Mensagem enviada pela Coordenação do curso ENGENHA..."`).
    3. **`remetente`**: `td:nth-child(3) a` (ex: `"caio.lopessilveira / FT/ENE/SEC (11.01.01.11.03.07)"`).
    4. **`possuiAnexo`**: Presenca de `img[alt="Mensagem com arquivo anexado"]` em `td:nth-child(4)`.
    5. **`dataHora`**: `td:nth-child(5) a` (ex: `"16/11/2026 13:14"`).
    6. **Leitura da Mensagem**: Clicar/enviar o postback JSF do link em `td:nth-child(2)` abre o texto completo da mensagem e o link de download do arquivo anexo.

### 5.2 Minhas Bolsas, Bolsa Auxilio e Cadastro Unico

Acionados via `POST https://sigaa.unb.br/sigaa/portais/discente/discente.jsf` no `#menu:form_menu_discente`:

1. **Minhas Bolsas na Instituicao**:
   * **`jscook_action`**: `menu_form_menu_discente_discente_menu:A]#{ relatorioBolsasDiscenteBean.listarBolsasInstituicao }`
   * **Tabela Retornada (`caption: "Bolsas encontradas"`)**: Colunas `Tipo da Bolsa`, `Unidade`, `Início`, `Fim`.
2. **Acompanhar Solicitacao de Bolsa Auxilio**:
   * **`jscook_action`**: `menu_form_menu_discente_discente_menu:A]#{ bolsaAuxilioMBean.acompanharSituacaoBolsaAuxilio }`
   * **Tabelas Retornadas**:
     * **Tabela 1 (`PERÍODOS DE DIVULGAÇÃO DO RESULTADO`)**: Colunas `Tipo da Bolsa` (ex: `ALIMENTAÇÃO`, `AUXÍLIO EMERGENCIAL`, `AUXÍLIO MORADIA`, `AUXÍLIO SOCIOECONÔMICO`, `AVALIAÇÃO SOCIOECONÔMICA`, `BOLSA PERMANÊNCIA MEC`, `TRANSPORTE`), `Resultados` (data de divulgacao ex: `31/08/2026`), `Município` (`BRASÍLIA`), `Discentes permitidos`.
     * **Tabela 2 (`Solicitações Encontradas`)**: Colunas `Ano/Período da Solicitação`, `Tipo da Bolsa Auxílio`, `Data da solicitação`, `Situação da Bolsa Auxílio` e acao `Visualizar avaliação da solicitação`.
3. **Consultar Adesoes ao Cadastro Unico**:
   * **`jscook_action`**: `menu_form_menu_discente_discente_menu:A]#{ adesaoCadastroUnico.listarCadastroUnico }`
   * **Tabela Retornada (`caption: "Minhas Adesões (N)"`)**: Colunas `Ano-Período`, `Prioritário` (`Sim`/`Não`), `Data da adesão`, `Situação`.

### 5.3 Projetos de Pesquisa, Acoes de Extensao e Emissao de Certificados

Acionados via `POST https://sigaa.unb.br/sigaa/portais/discente/discente.jsf` no `#menu:form_menu_discente`:

1. **Projetos de Pesquisa que Participo**:
   * **`jscook_action`**: `menu_form_menu_discente_discente_menu:A]#{membroProjeto.listarMeusProjetoPesquisa}`
   * **Tabela Retornada (`Lista dos projetos que o usuário participa`)**: Colunas `Projeto`, `Categoria`, `Função`, `Ch`, `Início`, `Fim`, `Situação`.
2. **Minhas Acoes de Extensao como Membro da Equipe**:
   * **`jscook_action`**: `menu_form_menu_discente_discente_menu:A]#{ atividadeExtensao.carregarAcoesDiscenteLogado }`
   * **Tabela Retornada (`Lista de Ações como Membro da Equipe`)**: Colunas `Código`, `Título`, `Data Cadastro`.
3. **Certificados e Declaracoes Autenticados de Extensao**:
   * **`jscook_action`**: `menu_form_menu_discente_discente_menu:A]#{documentosAutenticadosExtensao.participacoesDiscenteUsuarioLogado}`
   * **Tabelas Retornadas (`Portal Discente > Documentos autenticados de Extensão`)**:
     * **Tabela 1**: `Lista de participações como membro(a) de equipe organizadora das Ações de Extensão` (`Projeto`, `Categoria`, `Função`, `Início`, `Fim` + botao de emitir certificado/declaracao).
     * **Tabela 2**: `Lista de participações como público alvo das Ações de Extensão` (ex: `EV1797-2023 - Desvendando a Mente dos Cibercrimonosos: Explorando o Pensamento Adversário`, `Categoria`, `Participação`, `Data do Cadastro`, `Frequência` + botao de emitir certificado).
     * **Tabela 3**: `Lista de participações como discente das Ações de Extensão` (`Ação`, `Vínculo`, `Início`, `Fim`).

---

## 6. Modulo 5: Restaurante Universitario (`ru.unb.br`) - API WordPress e Parser de PDF com Alergenicos

A inspecao via REA revelou que o portal `https://ru.unb.br` utiliza WordPress com a **REST API JSON v2 totalmente aberta (`Access-Control-Allow-Origin: *`)**, eliminando a necessidade de fazer scraping do HTML bruto da pagina para descobrir atualizacoes ou noticias.

### 6.1 Endpoints Nativos da WordPress REST API (`wp-json/wp/v2`)

#### A. Endpoint Principal de Cardapios dos 5 Campi (Pagina ID `12853`)
* **Metodo e URL**: `GET https://ru.unb.br/wp-json/wp/v2/pages/12853`
* **Autenticacao**: Nenhuma (Publico, CORS liberado `*`).
* **Campos Chave do JSON**:
  * **`modified_gmt`**: Timestamp ISO-8601 da ultima atualizacao da pagina (ex: `"2026-11-19T11:19:39"`). O app pode consultar apenas `GET https://ru.unb.br/wp-json/wp/v2/pages/12853?_fields=id,modified_gmt,content` para verificar em menos de 2 KB se o cardapio de domingo ja foi atualizado!
  * **`content.rendered`**: Fragmento HTML limpo contendo os 5 blocos `<h3 class="elementor-heading-title">Cardápio <Campus></h3>` seguidos imediatamente pelos paragrafos `<p><a href="https://ru.unb.br/wp-content/uploads/YYYY/MM/<arquivo>.pdf">ISM - DD/MM/YYYY À DD/MM/YYYY</a></p>`.

**Mapeamento dos 5 Campi em `content.rendered`:**
1. **`Cardápio Darcy Ribeiro`** (ex: `Darcy-Ribeiro-Semana-02-23-11-a-29-11.pdf` - Segunda a Domingo)
2. **`Cardápio Ceilândia`** (ex: `Ceilandia-Semana-02-23-11-a-29-11-1.pdf` - Segunda a Sabado)
3. **`Cardápio Gama`** (ex: `Gama-Semana-02-23-11-a-29-11.pdf` - Segunda a Sexta)
4. **`Cardápio Planaltina`** (ex: `Planaltina-Semana-02-23-11-a-29-11.pdf` - Segunda a Domingo)
5. **`Cardápio Fazenda Água Limpa`** (ex: `Fazenda-Agua-Limpa-Semana-02-23-11-a-29-11.pdf` - Segunda a Sexta)

#### B. Endpoint de Midia / PDFs Recentes e Imagens JPG Pre-Renderizadas
* **Metodo e URL**: `GET https://ru.unb.br/wp-json/wp/v2/media?per_page=15&mime_type=application/pdf`
* **O que entrega**:
  Lista ordenada por data de upload (`date_gmt` decrescente) de todos os PDFs enviados ao site do RU.
  * **`source_url`**: URL direta do PDF (ex: `"https://ru.unb.br/wp-content/uploads/2026/11/Darcy-Ribeiro-Semana-02-23-11-a-29-11.pdf"`).
  * **`title.rendered`**: Titulo do anexo (ex: `"Darcy Ribeiro - Semana 02 - 23-11 à 29-11"`).
  * **`media_details.sizes.full.source_url`**: Imagem JPEG de alta resolucao (`1497 x 1059 px`) gerada automaticamente pelo WordPress a partir do PDF (`...-pdf.jpg`).

#### C. Endpoint de Noticias e Editais Gerais do RU
* **Metodo e URL**: `GET https://ru.unb.br/wp-json/wp/v2/posts?per_page=10&_embed`
* **O que entrega**:
  Array JSON com todas as noticias e editais publicados pela Diretoria do Restaurante Universitario (DRU/DAC):
  * **`id`**: ID da noticia (ex: `23408`)
  * **`date`**: Data de publicacao (ex: `"2026-05-28T..."`)
  * **`title.rendered`**: Titulo (ex: `"Resultado final do Edital Nº 07/2026"`)
  * **`excerpt.rendered`** e **`content.rendered`**: Resumo e corpo completo em HTML (incluindo links para PDFs de editais).
  * **`link`**: URL publica da noticia.
  * **`_embedded['wp:featuredmedia'][0].source_url`**: URL da imagem de capa da noticia.

### 6.2 Tabela de Precos por Grupo e Calculo de Refeicoes Restantes

* **Endpoint REST da pagina de precos**: `GET https://ru.unb.br/wp-json/wp/v2/pages?slug=precos-e-grupos&_fields=id,modified_gmt,content`
* **Valores Vigentes (Resolucao n. 0037/2025 do CAD/UnB)**:

| Grupo de Acesso | Publico | Cafe da Manha | Almoco | Jantar |
| :--- | :--- | :--- | :--- | :--- |
| **Grupo I** | Estudantes PNAES (ate 1,5 SM), CadUnico, Cota Escola Publica Baixa Renda, Indigenas, Quilombolas, LedoC, PeC-G, Cota Trans | **Gratuito (R$ 0,00)** | **Gratuito (R$ 0,00)** | **Gratuito (R$ 0,00)** |
| **Grupo II** | Estudantes de graduacao e pos-graduacao ampla concorrencia, estagiarios DGP/UnB e residentes | **R$ 2,00** | **R$ 4,50** | **R$ 4,50** |
| **Grupo III** | Servidores, terceirizados e visitantes em geral | **R$ 7,05** | **R$ 15,20** | **R$ 15,20** |
| **Grupo IV** | Estudantes de graduacao oriundos de cotas de escola publica (Exceto Baixa Renda) | **R$ 1,50** | **R$ 2,50** | **R$ 2,50** |
| **Executivo** | Restaurante Executivo (somente almoco por quilo) | - | **R$ 60,42 / kg** | - |

* **Formas de pagamento aceitas no RU**: Pix, Dinheiro, Cartao de Debito e Cartao de Credito.

### 6.3 Estrutura Interna dos PDFs Semanais dos 5 Campi

Cada arquivo PDF semanal (`842.04 x 595.56 pt`, paisagem A4) possui **3 paginas**:
* **Pagina 1 (`page_index = 0`)**: **Cafe da manha**
  * Linhas de composicao: `BEBIDAS` (2 sub-linhas: leite/bebida de soja e cafe/cha), `PANIFICAÇÃO`, `OPÇÃO EXTRA`, `GORDURA`, `COMPLEMENTO PADRÃO`, `COMPLEMENTO OVOLACTOVEGETARIANO`, `COMPLEMENTO VEGETARIANO ESTRITO`, `FRUTA`.
* **Pagina 2 (`page_index = 1`)**: **Almoco**
  * Linhas de composicao: `SALADA 1`, `SALADA 2`, `MOLHO PARA SALADA`, `PRATO PRINCIPAL PADRÃO`, `PRATO PRINCIPAL OVOLACTOVEGETARIANO`, `PRATO PRINCIPAL VEGETARIANO ESTRITO`, `GUARNIÇÃO`, `ACOMPANHAMENTOS`, `SOBREMESA`, `BEBIDA (Refresco de)`.
* **Pagina 3 (`page_index = 2`)**: **Jantar**
  * Linhas de composicao: `SALADA 1`, `SALADA 2`, `MOLHO PARA SALADA`, `PRATO PRINCIPAL PADRÃO`, `PRATO PRINCIPAL OVOLACTOVEGETARIANO`, `PRATO PRINCIPAL VEGETARIANO ESTRITO`, `SOPA`, `TORRADA`, `ACOMPANHAMENTOS`, `SOBREMESA`, `BEBIDA (Refresco de)`.

### 6.4 Algoritmo de Extracao Vetorial + Mapeamento Textual dos 10 Icones de Alergenicos

#### Como os Icones de Alergenicos sao Armazenados no PDF (Descoberta via REA)

1. **Legenda Fixa no Rodape de Cada Pagina (`top > 530 pt`)**:
   Toda pagina do PDF contem os **10 icones de referencia** (`srcsize` `~85 x 85 px`) alinhados horizontalmente da esquerda para a direita (`x0` de `69.6` ate `758.5`), exatamente acima dos 10 rotulos textuais:
   1. `COGUMELO` (`Cogumelo`, `x0 ~ 69.6`)
   2. `LEITE` (`Leite e derivados`, `x0 ~ 150.0`)
   3. `MEL` (`Mel`, `x0 ~ 222.7`)
   4. `PIMENTA` (`Pimenta`, `x0 ~ 299.3`)
   5. `SOJA` (`Soja`, `x0 ~ 375.5`)
   6. `GLUTEN` (`Trigo/Glúten`, `x0 ~ 452.6`)
   7. `AMENDOIM` (`Amendoim`, `x0 ~ 528.8`)
   8. `OLEAGINOSA` (`Oleaginosa`, `x0 ~ 605.4`)
   9. `OVO` (`Ovo`, `x0 ~ 682.0`)
   10. `SUINO` (`Suíno`, `x0 ~ 758.5`)
2. **Icones Dentro das Celulas da Tabela (`85 < top < 520 pt`)**:
   * Quando um prato possui **1 alergenico**, o PDF insere um XObject de imagem quadrado de `~51 x 51 px` (renderizado em `16.7 x 16.7 pt`).
   * Quando um prato possui **2 ou 3 alergenicos** (ex: `Ovos mexidos` com Ovo + Leite, ou `Broa de milho` com Leite + Ovo + Trigo/Gluten), o autor do cardapio **concatena os icones em uma unica faixa horizontal** de `104 x 52 px` (2 icones) ou `156 x 52 px` (3 icones)!
3. **Estrategia de Classificacao Exata (100% de Precisao)**:
   * Extraia as 10 imagens da legenda no rodape da propria pagina (`top > 530`), ordene por `x0` crescente e redimensione cada uma para um tensor RGB `32 x 32`.
   * Para cada imagem dentro da grade da tabela (`85 < top < 520`), calcule quantos icones existem na faixa: `n_icones = max(1, round(width / height))`.
   * Fatie a faixa horizontalmente em `n_icones` quadrados iguais, redimensione cada fatia para `32 x 32` RGB e compare via **Erro Quadratico Medio (MSE)** contra os 10 icones da legenda da mesma pagina.
   * Por fim, verifique em qual celula `(row_idx, col_idx)` da tabela o centro `(cx, cy)` da imagem cai (ou para celulas mescladas horizontalmente como `Leite integral ou Bebida de soja` e `Manteiga ou creme vegetal`, associe ao prato cujo texto esta imediatamente a esquerda de `im["x0"]`).

#### Codigo Python Completo e Verificado do Parser de Cardapio + Alergenicos

```python
import io
import re
import numpy as np
import pdfplumber
from PIL import Image

ALERGENICOS_ORDEM_LEGENDA = [
    {"id": "COGUMELO",   "nome": "Cogumelo"},
    {"id": "LEITE",      "nome": "Leite e derivados"},
    {"id": "MEL",        "nome": "Mel"},
    {"id": "PIMENTA",    "nome": "Pimenta"},
    {"id": "SOJA",       "nome": "Soja"},
    {"id": "GLUTEN",     "nome": "Trigo/Glúten"},
    {"id": "AMENDOIM",   "nome": "Amendoim"},
    {"id": "OLEAGINOSA", "nome": "Oleaginosa"},
    {"id": "OVO",        "nome": "Ovo"},
    {"id": "SUINO",      "nome": "Suíno"},
]

REFEICOES_POR_PAGINA = ["CAFE_DA_MANHA", "ALMOCO", "JANTAR"]

def _decodificar_xobject_rgb(im_dict: dict) -> np.ndarray:
    stream = im_dict["stream"]
    data = stream.get_data()
    w, h = im_dict["srcsize"]
    if len(data) == w * h * 3:
        return np.frombuffer(data, dtype=np.uint8).reshape((h, w, 3))
    return np.array(Image.open(io.BytesIO(data)).convert("RGB"))

def _classificar_imagem_alergenico(im_dict: dict, referencias_legenda: dict) -> list[str]:
    arr = _decodificar_xobject_rgb(im_dict)
    h, w, _ = arr.shape
    n_icones = max(1, round(w / h))
    largura_fatia = w / n_icones
    detectados = []
    for i in range(n_icones):
        fatia = arr[:, int(i * largura_fatia):int((i + 1) * largura_fatia), :]
        pil_32 = Image.fromarray(fatia).resize((32, 32), Image.Resampling.BILINEAR)
        vec = np.array(pil_32, dtype=np.float32)
        melhor_id = min(
            referencias_legenda,
            key=lambda alg_id: float(np.mean((vec - referencias_legenda[alg_id]) ** 2))
        )
        if melhor_id not in detectados:
            detectados.append(melhor_id)
    return detectados

def parsear_pdf_cardapio_ru(pdf_bytes: bytes, campus: str) -> dict:
    resultado = {"campus": campus, "dias": {}}

    with pdfplumber.open(io.BytesIO(pdf_bytes)) as pdf:
        for page_idx, page in enumerate(pdf.pages):
            refeicao_key = REFEICOES_POR_PAGINA[page_idx] if page_idx < 3 else f"PAGINA_{page_idx+1}"

            # 1. Carrega os 10 icones da legenda no rodape (top > 530) ordenados por x0
            imgs_legenda = sorted([im for im in page.images if im["top"] > 530], key=lambda x: x["x0"])
            referencias = {}
            for meta, im in zip(ALERGENICOS_ORDEM_LEGENDA, imgs_legenda):
                arr = _decodificar_xobject_rgb(im)
                pil_32 = Image.fromarray(arr).resize((32, 32), Image.Resampling.BILINEAR)
                referencias[meta["id"]] = np.array(pil_32, dtype=np.float32)

            # 2. Localiza a tabela principal da refeicao
            tables = page.find_tables()
            if not tables:
                continue
            table = tables[0]
            texto_linhas = table.extract()

            # 3. Identifica as colunas de dias da semana e datas no cabecalho
            colunas_dias = {}
            for r_idx in range(min(4, len(texto_linhas))):
                for c_idx, val in enumerate(texto_linhas[r_idx]):
                    if not val:
                        continue
                    m_data = re.search(r"(\d{2}/\d{2}/\d{4})", val)
                    if m_data:
                        colunas_dias.setdefault(c_idx, {})["data"] = m_data.group(1)
                    elif any(d in val.upper() for d in ["FEIRA", "SÁBADO", "SABADO", "DOMINGO"]):
                        colunas_dias.setdefault(c_idx, {})["dia_semana"] = val.replace("\n", " ").strip()

            # 4. Pre-classifica todos os icones de alergenicos dentro da area da tabela (85 < top < 520)
            icones_celulas = []
            for im in page.images:
                if 85 < im["top"] < 520:
                    cx = (im["x0"] + im["x1"]) / 2.0
                    cy = (im["top"] + im["bottom"]) / 2.0
                    alergenicos_ids = _classificar_imagem_alergenico(im, referencias)
                    icones_celulas.append({"cx": cx, "cy": cy, "alergenicos": alergenicos_ids})

            # 5. Percorre as linhas de composicao da refeicao
            ultimo_nome_item = None
            for r_idx, (row_obj, row_texts) in enumerate(zip(table.rows, texto_linhas)):
                # Ignora linhas de cabecalho
                if r_idx < 4:
                    continue
                nome_item = next(
                    (t.replace("\n", " ").strip() for t in row_texts[1:4] if t and t.strip()),
                    None
                ) or ultimo_nome_item
                if not nome_item:
                    continue
                ultimo_nome_item = nome_item

                # Verifica se a linha inteira e mesclada para todos os dias (ex: BEBIDAS / GORDURA)
                textos_dias = [row_texts[c] for c in colunas_dias if c < len(row_texts)]
                texto_compartilhado = next((t for t in textos_dias if t and t.strip()), "")

                for c_idx, meta_dia in colunas_dias.items():
                    data_str = meta_dia.get("data", f"COL_{c_idx}")
                    dia_obj = resultado["dias"].setdefault(data_str, {
                        "data": data_str,
                        "dia_semana": meta_dia.get("dia_semana", ""),
                        "refeicoes": {}
                    })
                    ref_obj = dia_obj["refeicoes"].setdefault(refeicao_key, [])

                    cell_bbox = row_obj.cells[c_idx] if c_idx < len(row_obj.cells) else None
                    prato_raw = (row_texts[c_idx] if c_idx < len(row_texts) and row_texts[c_idx] else texto_compartilhado) or ""
                    prato_limpo = re.sub(r"\s+", " ", prato_raw).strip()

                    # Coleta os alergenicos cujas coordenadas (cx, cy) caem dentro do bbox da celula
                    alergenicos_prato = []
                    if cell_bbox:
                        x0, top, x1, bottom = cell_bbox
                        for ic in icones_celulas:
                            if x0 <= ic["cx"] <= x1 and top <= ic["cy"] <= bottom:
                                for a_id in ic["alergenicos"]:
                                    if a_id not in alergenicos_prato:
                                        alergenicos_prato.append(a_id)
                    # Caso a linha seja mesclada horizontalmente (ex: Leite integral ou Bebida de soja)
                    if not alergenicos_prato and row_obj.cells:
                        valid_bboxes = [b for b in row_obj.cells[4:] if b is not None]
                        if len(valid_bboxes) == 1:
                            x0, top, x1, bottom = valid_bboxes[0]
                            for ic in icones_celulas:
                                if x0 <= ic["cx"] <= x1 and top <= ic["cy"] <= bottom:
                                    for a_id in ic["alergenicos"]:
                                        if a_id not in alergenicos_prato:
                                            alergenicos_prato.append(a_id)

                    if prato_limpo:
                        ref_obj.append({
                            "categoria": nome_item,
                            "prato": prato_limpo,
                            "alergenicos": alergenicos_prato
                        })

    return resultado
```

#### Exemplo Real de Saida JSON Gerada pelo Algoritmo Acima (`Darcy Ribeiro - 23/11/2026`)

```json
{
  "campus": "Darcy Ribeiro",
  "dias": {
    "23/11/2026": {
      "data": "23/11/2026",
      "dia_semana": "2ª FEIRA",
      "refeicoes": {
        "CAFE_DA_MANHA": [
          { "categoria": "BEBIDAS", "prato": "Leite integral ou Bebida de soja", "alergenicos": ["LEITE", "SOJA"] },
          { "categoria": "BEBIDAS", "prato": "Café ou chá", "alergenicos": [] },
          { "categoria": "PANIFICAÇÃO", "prato": "Pão francês ou Pão careca ou Pão integral", "alergenicos": ["GLUTEN"] },
          { "categoria": "GORDURA", "prato": "Manteiga ou creme vegetal", "alergenicos": ["LEITE"] },
          { "categoria": "COMPLEMENTO PADRÃO", "prato": "Salsicha ao molho", "alergenicos": ["SUINO"] },
          { "categoria": "COMPLEMENTO OVOLACTOVEGETARIANO", "prato": "Queijo muçarela", "alergenicos": ["LEITE"] },
          { "categoria": "COMPLEMENTO VEGETARIANO ESTRITO", "prato": "Pasta de ervilha com hortelã", "alergenicos": [] },
          { "categoria": "FRUTA", "prato": "Banana", "alergenicos": [] }
        ],
        "ALMOCO": [
          { "categoria": "SALADA 1", "prato": "Alface crespa", "alergenicos": [] },
          { "categoria": "SALADA 2", "prato": "Tomate", "alergenicos": [] },
          { "categoria": "MOLHO PARA SALADA", "prato": "Salsa", "alergenicos": [] },
          { "categoria": "PRATO PRINCIPAL PADRÃO", "prato": "Silveirinha de carne", "alergenicos": [] },
          { "categoria": "PRATO PRINCIPAL OVOLACTOVEGETARIANO", "prato": "Bolinho de lentilha gratinado", "alergenicos": ["LEITE", "GLUTEN"] },
          { "categoria": "PRATO PRINCIPAL VEGETARIANO ESTRITO", "prato": "Silveirinha de soja", "alergenicos": ["SOJA"] },
          { "categoria": "GUARNIÇÃO", "prato": "Macarrão temperado", "alergenicos": ["GLUTEN"] },
          { "categoria": "ACOMPANHAMENTOS", "prato": "Arroz branco e integral Feijão carioca", "alergenicos": [] },
          { "categoria": "SOBREMESA", "prato": "Maçã", "alergenicos": [] },
          { "categoria": "BEBIDA (Refresco de)", "prato": "Guaraná", "alergenicos": [] }
        ],
        "JANTAR": [
          { "categoria": "SALADA 1", "prato": "Acelga com alface roxa", "alergenicos": [] },
          { "categoria": "SALADA 2", "prato": "Chuchu cozido com pimentões coloridos", "alergenicos": [] },
          { "categoria": "MOLHO PARA SALADA", "prato": "Mostarda e mel /Limão", "alergenicos": ["MEL"] },
          { "categoria": "PRATO PRINCIPAL PADRÃO", "prato": "Frango a pizzaiolo", "alergenicos": ["LEITE"] },
          { "categoria": "PRATO PRINCIPAL OVOLACTOVEGETARIANO", "prato": "Fricassê de palmito com legumes", "alergenicos": ["LEITE", "OVO"] },
          { "categoria": "PRATO PRINCIPAL VEGETARIANO ESTRITO", "prato": "Berinjela recheada com homus de grão de bico", "alergenicos": [] },
          { "categoria": "SOPA", "prato": "Caldo verde", "alergenicos": [] },
          { "categoria": "TORRADA", "prato": "Torrada", "alergenicos": ["GLUTEN"] },
          { "categoria": "ACOMPANHAMENTOS", "prato": "Arroz branco e integral Feijão carioca", "alergenicos": [] },
          { "categoria": "SOBREMESA", "prato": "Laranja", "alergenicos": [] },
          { "categoria": "BEBIDA (Refresco de)", "prato": "Guaraná", "alergenicos": [] }
        ]
      }
    }
  }
}
```
