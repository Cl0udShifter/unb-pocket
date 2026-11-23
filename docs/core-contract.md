# Contrato Core/Backend <-> UI/Frontend: UnB Pocket

Este documento define os tipos canonicos, erros tratados, invariantes e interfaces publicas expostos pelo modulo `:core:model` (implementados em `:core:data` via `UnbPocketCoreFactory.create(context)`).

**Regra de isolamento:** A camada de UI (`:app`) depende exclusivamente de `:core:model` e da instancia `UnbPocketCore`. Entidades Room, DTOs de rede, cookies HTTP, `javax.faces.ViewState` e chaves `jscook_action` nunca atravessam essa fronteira.

---

## 1. Tipos Base e Envelopes Reativos (`com.unbpocket.core.model.common`)

### 1.1 `DataOrigin` e `SyncedData<T>`
Todo fluxo observado em cache offline-first retorna `Flow<SyncedData<T>?>` (ou `Flow<RuPreferencias>` / `Flow<List<DocumentoOficialEmitido>>`).

```kotlin
enum class DataOrigin {
    CACHE,    // Lido da persistencia local (Room)
    REMOTE,   // Obtido e parseado da rede nesta sincronizacao
    FALLBACK, // Dados de contingencia (ex: JPEG/PDF do RU ou tabela local padrao)
}

data class SyncedData<out T>(
    val value: T,
    val origin: DataOrigin,
    val syncedAtEpochMillis: Long,
    val staleWarning: CoreError? = null,
)
```

### 1.2 `CoreResult<T>`, `PortalHomeSyncSummary` e `PreloadProgress`

```kotlin
sealed interface CoreResult<out T> {
    data class Success<out T>(val value: T) : CoreResult<T>
    data class Failure(val error: CoreError) : CoreResult<Nothing>
}

data class PortalHomeSyncSummary(
    val syncedAtEpochMillis: Long,
    val turmasCount: Int,
    val atualizacoesCount: Int,
    val saldoRuAtualizado: Boolean,
    val anyDataChanged: Boolean = false,
)

enum class PreloadPhase {
    IDLE,
    RUNNING_ESSENTIAL,
    RUNNING_BACKGROUND,
    COMPLETED,
}

data class PreloadProgress(
    val phase: PreloadPhase = PreloadPhase.IDLE,
    val completedSteps: Int = 0,
    val totalSteps: Int = 0,
    val currentTarget: String? = null,
    val updatedAnyData: Boolean = false,
    val lastError: CoreError? = null,
)
```

---

## 2. Erros Tratados (`CoreError`)

Nenhuma excecao de rede, SQLite, PDF ou HTML chega crua a UI. Toda falha e convertida para `CoreError`:

| Tipo (`CoreError`) | Campos | Causa e comportamento esperado na UI |
| :--- | :--- | :--- |
| `InvalidCredentials` | - | CAS (`autenticacao.unb.br`) rejeitou matricula ou senha. Exibir erro no login. |
| `SessionExpired` | - | Sessao JSF de 40 min expirou e o re-login silencioso nao foi possivel. Solicitar login. |
| `NoStoredCredentials` | - | Tentativa de renovar sessao sem credenciais salvas no `KeystoreCredentialVault`. |
| `NetworkUnavailable` | - | Sem conectividade, timeout ou falha DNS. Manter exibicao do cache local (`SyncedData`). |
| `HttpFailure` | `statusCode`, `endpoint`, `message` | Resposta HTTP 4xx/5xx inesperada do SIGAA, CAS ou WP API do RU. |
| `ParseError` | `source`, `reason`, `recoverableFallbackAvailable` | Layout HTML ou PDF mudou. Quando `recoverableFallbackAvailable = true`, consultar cache/fallback (ex: JPEG do RU). |
| `ReadOnlyViolation` | `blockedTarget`, `reason` | Guard-rail `SigaaReadOnlyGuard` bloqueou requisicao mutavel ou host nao autorizado. |
| `PdfExtractionError` | `documentType`, `reason` | Falha ao extrair texto/imagens da Carteirinha ou PDF oficial. |
| `StorageError` | `operation`, `reason` | Falha de leitura/escrita em Room, DataStore, Keystore ou cache de arquivos. |
| `UnavailableForProfile` | `feature`, `reason` | Recurso inexistente para o perfil (ex: calouro sem historico/IRA ou sem movimentacao no RU). |
| `Unexpected` | `message` | Erro nao classificado ou adaptador ainda em etapa de implementacao. |

---

## 3. Modelos de Dados por Dominio

### 3.1 Autenticacao (`com.unbpocket.core.model.auth`)
* `CredentialsInput(matricula: String, password: String, rememberCredentials: Boolean = true)`
* `AuthState`:
  * `Unauthenticated`
  * `Reauthenticating(matricula: String)`
  * `Authenticated(matricula: String, idDiscente: String?, sessionExpiresAtEpochMillis: Long?, hasStoredCredentials: Boolean)`

### 3.2 Restaurante Universitario (`com.unbpocket.core.model.ru`)
* `CampusRu`: `DARCY_RIBEIRO`, `CEILANDIA`, `GAMA`, `PLANALTINA`, `FAZENDA_AGUA_LIMPA` (cada um com `slug`, `displayName`, `headingKeyword`, `operatingDaysSummary`).
* `TipoRefeicao`: `CAFE_DA_MANHA(0)`, `ALMOCO(1)`, `JANTAR(2)`.
* `Alergenico`: `COGUMELO`, `LEITE`, `MEL`, `PIMENTA`, `SOJA`, `GLUTEN`, `AMENDOIM`, `OLEAGINOSA`, `OVO`, `SUINO` (com `id`, `labelPtBr`, `legendOrderIndex` 0..9).
* `GrupoAcessoRu`: `GRUPO_1`, `GRUPO_2`, `GRUPO_3`, `GRUPO_4`, `EXECUTIVO`, `DESCONHECIDO`.
* `CardapioParserStatus`: `PARSED_STRUCTURED`, `FALLBACK_IMAGE_ONLY`, `FALLBACK_PDF_ONLY`.
* `ItemCardapioRu(categoria: String, prato: String, alergenicos: List<Alergenico>)`
* `CardapioDiaRu(dataTexto: String, diaSemana: String, refeicoes: Map<TipoRefeicao, List<ItemCardapioRu>>)`
* `CardapioSemanalRu(campus: CampusRu, semanaLabel: String, pdfUrl: String, fallbackImageUrl: String?, localPdfPath: String?, modifiedGmt: String, parserStatus: CardapioParserStatus, dias: List<CardapioDiaRu>)`
* `TransacaoRu(dataHoraTexto: String, descricao: String, valorCentavos: Long)`
* `RefeicoesRestantes(cafesRestantes: Int, almocosOuJantaresRestantes: Int, trocoCafeCentavos: Long, trocoAlmocoJantarCentavos: Long, gratuito: Boolean)`
* `SaldoExtratoRu(saldoAtualCentavos: Long, saldoAnterior7DiasCentavos: Long?, grupoAcesso: GrupoAcessoRu, transacoesUltimos7Dias: List<TransacaoRu>, refeicoesRestantes: RefeicoesRestantes?)`
* `PrecoGrupoRu(grupo: GrupoAcessoRu, publicoAlvo: String, cafeCentavos: Long?, almocoCentavos: Long?, jantarCentavos: Long?, porKgCentavos: Long?)`
* `TabelaPrecosRu(resolucaoReferencia: String, modifiedGmt: String, grupos: List<PrecoGrupoRu>, formasPagamento: List<String>)`
* `NoticiaRu(id: Long, dateIso: String, title: String, excerptText: String, contentHtml: String, link: String, featuredImageUrl: String?, anexoPdfUrls: List<String>)`
* `RuPreferencias(campusSelecionado: CampusRu, alergenicosRestritos: Set<Alergenico>, ocultarPratosComAlergenicos: Boolean)`
* `RuAutoSyncSummary(checkedAtEpochMillis: Long, skippedDueToCooldown: Boolean, updatedCampi: Set<CampusRu>, upToDateCampi: Set<CampusRu>, failedCampi: Map<CampusRu, CoreError>, tabelaPrecosAtualizada: Boolean = false, noticiasAtualizadas: Boolean = false)` (com propriedade computada `anyUpdated: Boolean`)

### 3.3 Turmas e Turma Virtual (`com.unbpocket.core.model.turmas`)
* `DiaSemanaSigaa`: `SEGUNDA(2)` .. `SABADO(7)`.
* `TurnoSigaa`: `MANHA('M')`, `TARDE('T')`, `NOITE('N')`.
* `HorarioSlot(diaSemana: DiaSemanaSigaa, turno: TurnoSigaa, tempoNumero: Int, inicio: String, fim: String, codigoOriginal: String)`
* `TurmaResumo(idTurma: String, frontEndIdTurma: String, semestre: String, codigoDisciplina: String?, nomeDisciplina: String, numeroTurma: String?, cargaHorariaHoras: Int?, docenteNome: String?, local: String, codigoHorarioRaw: String, horarioSlots: List<HorarioSlot>, horarioExtenso: List<String>)`
* `AtualizacaoTurmaFeed(dataTexto: String, disciplinaSemestre: String, idTurma: String, tituloNoticia: String)`
* `AnexoArquivoSigaa(id: String, nomeArquivo: String)`
* `NoticiaTurmaResumoAva(idNoticia: String, dataHoraTexto: String, titulo: String)`
* `NoticiaTurmaAva(idNoticia: String?, titulo: String, dataHoraTexto: String, conteudoHtml: String, conteudoTexto: String, autor: String?, anexos: List<AnexoArquivoSigaa>)`
* `MaterialAulaAva(materialId: String, nome: String, tipoExtensao: String, topicoIndex: Int)`
* `TopicoAulaAva(index: Int, titulo: String, periodoTexto: String?, conteudoTexto: String, materiais: List<MaterialAulaAva>)`
* `TurmaDetalheAva(idTurma: String, frontEndIdTurma: String, codigoDisciplina: String, nomeDisciplina: String, periodoTurmaTexto: String, aulasMinistradas: Int, aulasTotal: Int, percentualAndamento: Int, ultimaNoticia: NoticiaTurmaAva?, historicoNoticiasResumo: List<NoticiaTurmaResumoAva>, topicosAula: List<TopicoAulaAva>)`
* `RegistroFrequenciaDia(dataTexto: String, situacaoTexto: String, faltasNoDia: Int, presente: Boolean)`
* `MapaFrequenciaTurma(presencasRegistradas: Int, aulasComRegistro: Int, percentualSobreRegistradas: Int, aulasDefinidasCh: Int, percentualSobreCh: Int, faltasComputadas: Int, limiteMaximoFaltas: Int, faltasRestantes: Int, emRiscoReprovacaoPorFalta: Boolean, registrosDiarios: List<RegistroFrequenciaDia>)`
* `UnidadeNotaTurma(nome: String, valor: String?)`
* `NotasParciaisTurma(notasDisponiveis: Boolean, mensagemAviso: String?, unidades: List<UnidadeNotaTurma>, mediaFinal: String?, faltas: Int?, situacao: String?)`
* `AvaliacaoTurma(dataTexto: String, horaTexto: String?, descricao: String)`
* `TarefaTurma(titulo: String, periodoEntrega: String, notaMaxima: String?, possuiArquivo: Boolean, emGrupo: Boolean)`
* `ArquivoBaixado(nomeArquivo: String, mimeType: String, localFilePath: String, tamanhoBytes: Long)`

### 3.4 Discente, Carteirinha e Documentos (`com.unbpocket.core.model.discente`)
* `PerfilDiscente(idDiscente: String, matricula: String, nomeCompleto: String, nomeAbreviado: String, bioPerfil: String?, fotoUrl: String?, semestreAtual: String, semestreEntrada: String, unidadeDepartamento: String, curso: String, nivel: String, status: String, emailTruncado: String?, idCursoPortalPublico: String?)`
* `IndicesIntegralizacao(ira: Double?, mp: Double?, chObrigatoriaPendente: Int?, chOptativaPendente: Int?, chComplementarPendente: Int?, chTotalCurriculo: Int?, percentualIntegralizadoExato: Double?, percentualIntegralizadoTexto: String?)`
* `DiscenteResumo(perfil: PerfilDiscente, indices: IndicesIntegralizacao)`
* `CarteirinhaEstudantil(nomeCompleto: String, matricula: String, cpfTexto: String?, curso: String, nivel: String, validadeTexto: String, qrPayload16Digitos: String, qrImageLocalPath: String, foto3x4LocalPath: String?, pdfLocalPath: String)`
* `NotaDisciplinaBoletim(codigo: String, disciplina: String, nota1: String?, resultadoMencao: String?, faltas: Int?, situacao: String)`
* `BoletimSemestre(semestre: String, disciplinas: List<NotaDisciplinaBoletim>)`
* `TipoDocumentoOficial`: `ATESTADO_MATRICULA`, `DECLARACAO_VINCULO`, `HISTORICO_ESCOLAR`, `PASSE_LIVRE_ESTUDANTIL`, `CARTEIRINHA_ESTUDANTIL`
* `DocumentoOficialEmitido(tipo: TipoDocumentoOficial, titulo: String, codigoVerificacao: String?, mimeType: String, localFilePath: String, emitidoEmEpochMillis: Long)`

---

## 4. Interfaces Publicas Expostas (`com.unbpocket.core.model.contract`)

### 4.1 `UnbPocketCore`
```kotlin
interface UnbPocketCore {
    val auth: AuthRepository
    val ru: RuRepository
    val turmas: TurmasRepository
    val discente: DiscenteRepository

    val preloadProgress: StateFlow<PreloadProgress>

    suspend fun syncPortalHome(includeSaldoRu: Boolean = true): CoreResult<PortalHomeSyncSummary>

    fun startBackgroundPreload(includeTurmaDetails: Boolean = true)

    suspend fun silentRefreshAll(includeTurmaDetails: Boolean = true): CoreResult<PortalHomeSyncSummary>
}
```
* `syncPortalHome` executa a Fase 1 da pre-carga usando `discente.jsf` (reutilizando o HTML capturado no login CAS quando recem-autenticado, sem nova requisicao redundante) + Atestado de Matricula + `#formExibirExtrato` opcional, atualizando `DiscenteResumo`, `TurmaResumo`, `AtualizacaoTurmaFeed` e `SaldoExtratoRu` no Room quando houver alteracao estrutural (`anyDataChanged`).
* `startBackgroundPreload` inicia de forma nao-bloqueante a Fase 2 no escopo do Core (`CarteirinhaEstudantil`, `BoletimSemestre` e, para cada turma matriculada, detalhes AVA, frequencia, notas parciais, avaliacoes e tarefas), reportando progresso em `preloadProgress`. Em `logout`, a pre-carga ativa e cancelada imediatamente.
* `silentRefreshAll` implementa o fluxo Stale-While-Revalidate na abertura do app com credenciais salvas: valida/renova a sessao (`ensureValidSession`), executa `syncPortalHome` e dispara `startBackgroundPreload`. Alteracoes reais propagam via Room `Flow`; payloads identicos mantem o estado visual intacto sem sobrescrita nem banners transientes de falha.

### 4.2 `AuthRepository`
* `val authState: StateFlow<AuthState>`
* `suspend fun login(credentials: CredentialsInput): CoreResult<AuthState.Authenticated>`
* `suspend fun ensureValidSession(): CoreResult<AuthState.Authenticated>`
* `suspend fun logout(clearCachedData: Boolean = true): CoreResult<Unit>` (com `clearCachedData = true`, limpa apenas dados privados da conta SIGAA: `DISCENTE`, `TURMAS`, `RU_SALDO` e arquivos de carteirinha/documentos/materiais, preservando cardapios, precos, noticias e PDFs publicos do RU)
* `suspend fun hasSavedCredentials(): Boolean`

### 4.3 `RuRepository` (Tela 1: RU)
* `fun observePreferencias(): Flow<RuPreferencias>`
* `suspend fun setCampusSelecionado(campus: CampusRu): CoreResult<Unit>`
* `suspend fun setAlergenicosRestritos(alergenicos: Set<Alergenico>, ocultarPratos: Boolean): CoreResult<Unit>`
* `fun observeCardapio(campus: CampusRu): Flow<SyncedData<CardapioSemanalRu>?>`
* `suspend fun refreshCardapio(campus: CampusRu, force: Boolean = false): CoreResult<CardapioSemanalRu>`
* `suspend fun syncCardapiosIfNeeded(forceCheck: Boolean = false, includeExtras: Boolean = false): CoreResult<RuAutoSyncSummary>`
  * Executa verificacao automatica em modo Stale-While-Revalidate para os 5 campi (`CampusRu.entries`) com 1 requisicao GET unica para `wp/v2/pages/12853` usando a data corrente em `America/Sao_Paulo`, baixando novamente apenas PDFs cuja URL ou `modifiedGmt` mudaram.
  * Politica de cooldown: `6 horas` quando todos os cardapios em cache ainda estao vigentes (`today <= endDate`), reduzindo para `30 minutos` caso algum campus esteja ausente ou com semana expirada (`today > endDate`, por exemplo segunda-feira enquanto o RU ainda nao publicou o novo arquivo). Em caso de atraso na publicacao ou falha parcial/offline, o cache anterior permanece intacto e falhas por campus sao isoladas em `failedCampi`.
  * A atualizacao periodica tambem e agendada em segundo plano via `WorkManager` (`RuMenuSyncWorker`, periodicidade de 6h com `NetworkType.CONNECTED`, `forceCheck = true`, `includeExtras = true`), complementada por checagem silenciosa (`forceCheck = false`) no `ON_RESUME` da aplicacao e ao entrar na aba RU.
* `fun observeSaldoExtrato(): Flow<SyncedData<SaldoExtratoRu>?>`
* `suspend fun refreshSaldoExtrato(): CoreResult<SaldoExtratoRu>`
* `fun observeTabelaPrecos(): Flow<SyncedData<TabelaPrecosRu>?>`
* `suspend fun refreshTabelaPrecos(force: Boolean = false): CoreResult<TabelaPrecosRu>`
* `fun observeNoticias(): Flow<SyncedData<List<NoticiaRu>>?>`
* `suspend fun refreshNoticias(perPage: Int = 10): CoreResult<List<NoticiaRu>>`
* `suspend fun downloadCardapioPdfLocal(campus: CampusRu): CoreResult<String>`

### 4.4 `TurmasRepository` (Tela 2: Turmas)
* `fun observeTurmasSemestre(): Flow<SyncedData<List<TurmaResumo>>?>`
* `fun observeAtualizacoesFeed(): Flow<SyncedData<List<AtualizacaoTurmaFeed>>?>`
* `suspend fun refreshTurmasSemestre(enriquecerViaAtestado: Boolean = true): CoreResult<List<TurmaResumo>>`
* `fun observeTurmaDetalhe(frontEndIdTurma: String): Flow<SyncedData<TurmaDetalheAva>?>`
* `suspend fun refreshTurmaDetalhe(frontEndIdTurma: String): CoreResult<TurmaDetalheAva>`
* `suspend fun getNoticiaCompleta(frontEndIdTurma: String, idNoticia: String): CoreResult<NoticiaTurmaAva>`
* `fun observeFrequencia(frontEndIdTurma: String): Flow<SyncedData<MapaFrequenciaTurma>?>`
* `suspend fun refreshFrequencia(frontEndIdTurma: String): CoreResult<MapaFrequenciaTurma>`
* `fun observeNotasParciais(frontEndIdTurma: String): Flow<SyncedData<NotasParciaisTurma>?>`
* `suspend fun refreshNotasParciais(frontEndIdTurma: String): CoreResult<NotasParciaisTurma>`
* `fun observeAvaliacoes(frontEndIdTurma: String): Flow<SyncedData<List<AvaliacaoTurma>>?>`
* `suspend fun refreshAvaliacoes(frontEndIdTurma: String): CoreResult<List<AvaliacaoTurma>>`
* `fun observeTarefas(frontEndIdTurma: String): Flow<SyncedData<List<TarefaTurma>>?>`
* `suspend fun refreshTarefas(frontEndIdTurma: String): CoreResult<List<TarefaTurma>>`
* `suspend fun downloadMaterial(frontEndIdTurma: String, materialId: String): CoreResult<ArquivoBaixado>`
* `suspend fun downloadAnexoNoticia(frontEndIdTurma: String, anexoId: String): CoreResult<ArquivoBaixado>`

### 4.5 `DiscenteRepository` (Tela 3: Discente)
* `fun observeDiscenteResumo(): Flow<SyncedData<DiscenteResumo>?>`
* `suspend fun refreshDiscenteResumo(): CoreResult<DiscenteResumo>`
* `fun observeCarteirinha(): Flow<SyncedData<CarteirinhaEstudantil>?>`
* `suspend fun refreshCarteirinha(force: Boolean = false): CoreResult<CarteirinhaEstudantil>`
* `fun observeBoletimGeral(): Flow<SyncedData<List<BoletimSemestre>>?>`
* `suspend fun refreshBoletimGeral(): CoreResult<List<BoletimSemestre>>`
* `fun observeDocumentosEmitidos(): Flow<List<DocumentoOficialEmitido>>`
* `suspend fun emitirDocumentoOficial(tipo: TipoDocumentoOficial): CoreResult<DocumentoOficialEmitido>`

---

## 5. Invariantes Operacionais do Core

1. **Serializacao JSF (`ViewState`) com Preempcao por Prioridade:** Toda requisicao autenticada ao `sigaa.unb.br` executa sob `UnbHttpSession.withJsfLock(priority)` com dois niveis (`INTERACTIVE` e `BACKGROUND_PRELOAD`). Acoes explicitas do usuario passam na frente de etapas pendentes da pre-carga em segundo plano sem quebrar a exclusao mutua sobre `javax.faces.ViewState`. Chamadas publicas ao `ru.unb.br` correm em paralelo.
2. **Encoding `ISO-8859-1`:** Respostas HTML do SIGAA sao decodificadas com `Charset.forName("ISO-8859-1")` antes de entrarem no Jsoup.
3. **Contrato Read-Only:** `SigaaReadOnlyInterceptor` valida host, metodo e payload antes de abrir socket. Qualquer `POST` fora da whitelist ou com token mutavel aborta com `CoreError.ReadOnlyViolation`.
4. **Valores Monetarios em Centavos (`Long`):** Saldo, extrato e tarifas do RU usam `Long` em centavos para evitar imprecisao de ponto flutuante.
5. **Credenciais Seguras:** `KeystoreCredentialVault` cifra matricula e senha via Android Keystore (`AES/GCM/NoPadding`) dentro de `noBackupFilesDir`, isolado de backups automaticos.
6. **Cache com Comparacao Estrutural (Diff-Before-Write):** `SnapshotCacheStore` compara `payloadJson` e `origin` antes de gravar no Room; snapshots identicos nao disparam upsert nem reemissoes visuais desnecessarias na UI.
