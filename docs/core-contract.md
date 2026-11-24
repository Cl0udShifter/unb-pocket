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
* `RuPreferencias(campusSelecionado: CampusRu, alergenicosRestritos: Set<Alergenico>, ocultarPratosComAlergenicos: Boolean, noticiasVistasIds: Set<Long> = emptySet(), notificacoes: RuNotificacaoConfig = RuNotificacaoConfig())`
* `DiaSemanaRu`: `SEGUNDA(1)` .. `DOMINGO(7)` (`dayOfWeekIso`, `shortLabel`, `displayName`).
* `RuNotificacaoConfig(habilitado: Boolean = false, refeicoesPorDia: Map<DiaSemanaRu, Set<TipoRefeicao>> = emptyMap())` (com helpers `isEnabledFor(dia, refeicao)` e `hasAnySlotSelected()`; inicia desligado por padrao).
* `RuNotificacaoConteudo(campus: CampusRu, dataTexto: String, dayIndex: Int, refeicao: TipoRefeicao, titulo: String, mensagem: String)`
* `RuDeepLinkTarget(campus: CampusRu?, dataTexto: String?, dayIndex: Int?, refeicao: TipoRefeicao)` e chaves em `RuNotificationExtras` (`EXTRA_CAMPUS_SLUG`, `EXTRA_DATA_TEXTO`, `EXTRA_DAY_INDEX`, `EXTRA_MEAL_PAGE_INDEX`) para abertura direta da tela do cardapio no dia e refeicao da notificacao.
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
* `ArquivoBaixado(nomeArquivo: String, mimeType: String, localFilePath: String, tamanhoBytes: Long, downloadPublicoInfo: String? = null)`

### 3.4 Discente, Carteirinha e Documentos (`com.unbpocket.core.model.discente`)
* `PerfilDiscente(idDiscente: String, matricula: String, nomeCompleto: String, nomeAbreviado: String, bioPerfil: String?, fotoUrl: String?, semestreAtual: String, semestreEntrada: String, unidadeDepartamento: String, curso: String, nivel: String, status: String, emailTruncado: String?, idCursoPortalPublico: String?, fotoLocalPath: String? = null)`
  * `fotoLocalPath` guarda o caminho local em cache da foto de perfil do SIGAA (`#perfil-docente .foto img`, baixada com `forceDownload` apenas quando `fotoUrl` muda, quando o arquivo local esta ausente ou no refresh manual `refreshDiscenteResumo`), permanecendo `null` de forma nao-fatal se a conta nao tiver foto (`no_picture`) ou o download falhar para que a UI exiba as iniciais como fallback.
* `IndicesIntegralizacao(ira: Double?, mp: Double?, chObrigatoriaPendente: Int?, chOptativaPendente: Int?, chComplementarPendente: Int?, chTotalCurriculo: Int?, percentualIntegralizadoExato: Double?, percentualIntegralizadoTexto: String?)`
* `DiscenteResumo(perfil: PerfilDiscente, indices: IndicesIntegralizacao)`

### 3.5 Notificacoes SIGAA e Painel Discente (`com.unbpocket.core.model.notification`)
* `SigaaSyncIntervalHours`: `H2(2L, "A cada 2h")`, `H4(4L, "A cada 4h")`, `H6(6L, "A cada 6h")`, `H12(12L, "A cada 12h")`.
* `AulaLeadMinutes`: `MIN_15(15, "15 min antes")`, `MIN_30(30, "30 min antes")`, `MIN_60(60, "1h antes")`.
* `SigaaNotificacaoTarget`: `TURMAS("TURMAS")`, `DISCENTE("DISCENTE")`.
* `SigaaNotificacaoTipo`: `AULA_PROXIMA`, `TURMA_AVISO_OU_MATERIAL`, `AVALIACAO_OU_TAREFA`, `NOTA_PARCIAL_ALTERADA`, `RISCO_FALTAS` (destino `TURMAS`); `RU_SALDO_BAIXO`, `RU_EXTRATO_MOVIMENTACAO`, `BOLETIM_ATUALIZADO`, `INDICES_ATUALIZADOS` (destino `DISCENTE`).
* `SigaaNotificacaoConfig(habilitado: Boolean = false, turmasHabilitado: Boolean = true, aulasLembrete: Boolean = true, antecedenciaAulaMinutos: Int = 30, avisosEMateriaisNovos: Boolean = true, provasETarefasLembrete: Boolean = true, notasParciaisAlteradas: Boolean = true, riscoFaltaAlerta: Boolean = true, turmasSilenciadas: Set<String> = emptySet(), discenteHabilitado: Boolean = true, saldoRuBaixoAlerta: Boolean = true, limiteRefeicoesRuBaixo: Int = 2, extratoRuMovimentacaoAlerta: Boolean = true, boletimAlteradoAlerta: Boolean = true, indicesAlteradosAlerta: Boolean = true, syncIntervalHours: SigaaSyncIntervalHours = SigaaSyncIntervalHours.H4, somenteWifi: Boolean = false, userConfigured: Boolean = false)` (com `LIMITE_REFEICOES_OPCOES = [1, 2, 3, 5]` e helpers `isTurmasAtivo`, `isDiscenteAtivo`, `hasAnyEnabled`, `hasAnyLocalScheduleEnabled`, `hasAnyRemoteSyncEnabled`, `needsTurmaDetailsSync`, `needsBoletimSync`, `needsDiscenteSaldoSync`).
* `SigaaNotificacaoConteudo(dedupKey: String, notificationId: Int, tipo: SigaaNotificacaoTipo, titulo: String, resumo: String, detalhesExpandido: String? = null, target: SigaaNotificacaoTarget, triggerAtEpochMillis: Long)`.
* `SigaaDeepLinkTarget(target: SigaaNotificacaoTarget)` e chaves em `SigaaNotificationExtras` (`EXTRA_TARGET_TAB`, `EXTRA_NOTIFICATION_TYPE`) para navegacao direta as abas `TURMAS` ou `DISCENTE` ao tocar na notificacao.
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

    suspend fun refreshDiscentePerfilESaldo(forceDownloadFoto: Boolean = false): CoreResult<DiscenteResumo>

    fun startBackgroundPreload(includeTurmaDetails: Boolean = true)

    suspend fun silentRefreshAll(includeTurmaDetails: Boolean = true): CoreResult<PortalHomeSyncSummary>

    fun observeSigaaNotificacoesConfig(): Flow<SigaaNotificacaoConfig>

    suspend fun setSigaaNotificacoesConfig(config: SigaaNotificacaoConfig): CoreResult<Unit>

    suspend fun autoEnableSigaaNotificacoesIfEligible(): CoreResult<Boolean>
}
```
* `syncPortalHome` executa a Fase 1 da pre-carga usando `discente.jsf` (reutilizando o HTML capturado no login CAS quando valido, com fallback automatico para `verPortalDiscente.do` se o redirecionamento pos-CAS cair em tela intermediaria) + Atestado de Matricula (apenas quando houver turmas ainda sem `codigoDisciplina`/`docenteNome` em cache) + `#formExibirExtrato` opcional, atualizando `DiscenteResumo`, `TurmaResumo`, `AtualizacaoTurmaFeed` e `SaldoExtratoRu` no Room quando houver alteracao estrutural (`anyDataChanged`).
* `refreshDiscentePerfilESaldo` atualiza apenas os dados da tela Perfil Discente (`DiscenteResumo` + `SaldoExtratoRu`) em um unico lock JSF e uma unica chamada `verPortalDiscente.do`, reutilizando a foto de perfil em cache quando `forceDownloadFoto = false` e a URL nao mudou.
* `startBackgroundPreload` inicia de forma nao-bloqueante a Fase 2 no escopo do Core (`CarteirinhaEstudantil`, `BoletimSemestre` e, quando `includeTurmaDetails = true`, detalhes AVA, frequencia, notas parciais, avaliacoes e tarefas reutilizando a sessao AVA ativa por turma), reportando progresso em `preloadProgress`. Em `logout`, a pre-carga ativa e cancelada imediatamente.
* `silentRefreshAll` implementa o fluxo Stale-While-Revalidate na abertura do app com credenciais salvas: valida/renova a sessao (`ensureValidSession`), executa `syncPortalHome` e dispara `startBackgroundPreload`, avaliando diferencas de notificacoes (`RU_SALDO_BAIXO`, extrato, feed e mudancas pos-preload) quando os alertas estiverem habilitados. Alteracoes reais propagam via Room `Flow`; payloads identicos mantem o estado visual intacto sem sobrescrita nem banners transientes de falha.
* `observeSigaaNotificacoesConfig` e `setSigaaNotificacoesConfig` leem e persistem preferencias locais de notificacoes de `Aulas & Turmas` e `Painel Discente` via DataStore (`sigaa_notifications`), populando o baseline inicial no Room sem disparos em lote na primeira ativacao (avaliando de imediato condicoes ativas como saldo RU baixo), sincronizando `AlarmManager` (lembretes de aula com 15/30/60 min e provas/tarefas na vespera as 20h e no dia as 07h ou 1h antes, com WorkManager de contingencia quando alarmes exatos estiverem bloqueados e tolerancia para atrasos de Doze) e `WorkManager` periodico (`2h`/`4h`/`6h`/`12h`, com opcao somente Wi-Fi, preservando a janela ativa com `KEEP` sempre que intervalo e rede nao mudarem).
* `autoEnableSigaaNotificacoesIfEligible` ativa automaticamente o mestre SIGAA na autenticacao quando a permissao de notificacao ja estiver concedida e `userConfigured == false`, mantendo o usuario livre para desativar depois.

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
* `suspend fun setNotificacoesConfig(config: RuNotificacaoConfig): CoreResult<Unit>`
  * Persiste `RuNotificacaoConfig` no DataStore e sincroniza o agendamento de alarmes locais via `AlarmManager` (`RuMealAlarmScheduler` + `RuNotificationAlarmReceiver`, com WorkManager de contingencia quando alarmes exatos estiverem bloqueados, catch-up de refeicoes do dia dentro da janela e re-agendamento automatico apos boot, troca de hora ou permissao concedida) para disparar `1h` antes de cada refeicao habilitada (`06h` Cafe da Manha, `10h` Almoco, `16h` Jantar no fuso `America/Sao_Paulo`).
* `suspend fun prepareNotificacaoRefeicao(refeicao: TipoRefeicao, nowEpochMillis: Long = System.currentTimeMillis()): CoreResult<RuNotificacaoConteudo?>`
  * Verifica se `(dia atual, refeicao)` esta ativo em `RuNotificacaoConfig`; caso necessario tenta sincronizar o cardapio do campus ativo e monta o resumo com os pratos principais aplicando `alergenicosRestritos` e `ocultarPratosComAlergenicos`. Retorna lembrete em modo fallback seguro caso o cardapio esteja em imagem/PDF ou sem pratos para a data, e retorna `null` apenas quando a refeicao estiver desativada no dia.
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
* `suspend fun markNoticiasVistas(ids: Set<Long>): CoreResult<Unit>` (persiste os IDs das noticias ja visualizadas pelo usuario em `RuPreferencias.noticiasVistasIds`)
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
7. **Notificacoes em Segundo Plano e Isolamento de Logout:** Com o app fechado, `SigaaNotificationAlarmReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`) e `SigaaBackgroundSyncWorker` consultam o Core para disparar lembretes locais e checar alteracoes reais contra o baseline salvo no Room (evitando repeticao de saldo baixo do RU enquanto nao houver nova queda ou recarga). Em `logout`, alarmes, job SIGAA no `WorkManager`, baseline e preferencias de notificacao SIGAA sao limpos, preservando integralmente as notificacoes do cardapio do RU.
