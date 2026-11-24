package com.unbpocket.app.ui.viewmodel

import com.unbpocket.core.model.auth.AuthState
import com.unbpocket.core.model.auth.CredentialsInput
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.DataOrigin
import com.unbpocket.core.model.common.PortalHomeSyncSummary
import com.unbpocket.core.model.common.PreloadPhase
import com.unbpocket.core.model.common.PreloadProgress
import com.unbpocket.core.model.common.SyncedData
import com.unbpocket.core.model.contract.AuthRepository
import com.unbpocket.core.model.contract.DiscenteRepository
import com.unbpocket.core.model.contract.RuRepository
import com.unbpocket.core.model.contract.TurmasRepository
import com.unbpocket.core.model.contract.UnbPocketCore
import com.unbpocket.core.model.discente.BoletimSemestre
import com.unbpocket.core.model.discente.CarteirinhaEstudantil
import com.unbpocket.core.model.discente.DiscenteResumo
import com.unbpocket.core.model.discente.DocumentoOficialEmitido
import com.unbpocket.core.model.discente.TipoDocumentoOficial
import com.unbpocket.core.model.notification.SigaaNotificacaoConfig
import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.CardapioParserStatus
import com.unbpocket.core.model.ru.CardapioSemanalRu
import com.unbpocket.core.model.ru.NoticiaRu
import com.unbpocket.core.model.ru.RuAutoSyncSummary
import com.unbpocket.core.model.ru.RuNotificacaoConfig
import com.unbpocket.core.model.ru.RuNotificacaoConteudo
import com.unbpocket.core.model.ru.RuPreferencias
import com.unbpocket.core.model.ru.SaldoExtratoRu
import com.unbpocket.core.model.ru.TabelaPrecosRu
import com.unbpocket.core.model.ru.TipoRefeicao
import com.unbpocket.core.model.turmas.ArquivoBaixado
import com.unbpocket.core.model.turmas.AtualizacaoTurmaFeed
import com.unbpocket.core.model.turmas.AvaliacaoTurma
import com.unbpocket.core.model.turmas.MapaFrequenciaTurma
import com.unbpocket.core.model.turmas.NotasParciaisTurma
import com.unbpocket.core.model.turmas.NoticiaTurmaAva
import com.unbpocket.core.model.turmas.TarefaTurma
import com.unbpocket.core.model.turmas.TurmaDetalheAva
import com.unbpocket.core.model.turmas.TurmaResumo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeUnbPocketCore : UnbPocketCore {
    val authFake = FakeAuthRepository()
    val ruFake = FakeRuRepository()
    val turmasFake = FakeTurmasRepository()
    val discenteFake = FakeDiscenteRepository()

    var syncPortalResult: CoreResult<PortalHomeSyncSummary> = CoreResult.Success(
        PortalHomeSyncSummary(
            syncedAtEpochMillis = 1_000L,
            turmasCount = 2,
            atualizacoesCount = 1,
            saldoRuAtualizado = true,
            anyDataChanged = true,
        ),
    )
    var silentRefreshResult: CoreResult<PortalHomeSyncSummary>? = null
    var startBackgroundPreloadCalls: Int = 0
    var syncPortalHomeCalls: Int = 0
    var silentRefreshCalls: Int = 0
    var lastSilentRefreshIncludeTurmaDetails: Boolean? = null
    var refreshDiscentePerfilESaldoCalls: Int = 0
    var lastRefreshPerfilForceFoto: Boolean? = null

    private val _preloadProgress = MutableStateFlow(PreloadProgress())
    override val preloadProgress: StateFlow<PreloadProgress> = _preloadProgress.asStateFlow()

    val sigaaNotificacoesFlow = MutableStateFlow(SigaaNotificacaoConfig())
    var autoEnableSigaaCalls: Int = 0

    override val auth: AuthRepository = authFake
    override val ru: RuRepository = ruFake
    override val turmas: TurmasRepository = turmasFake
    override val discente: DiscenteRepository = discenteFake

    override suspend fun syncPortalHome(includeSaldoRu: Boolean): CoreResult<PortalHomeSyncSummary> {
        syncPortalHomeCalls++
        return syncPortalResult
    }

    override suspend fun refreshDiscentePerfilESaldo(
        forceDownloadFoto: Boolean,
    ): CoreResult<DiscenteResumo> {
        refreshDiscentePerfilESaldoCalls++
        lastRefreshPerfilForceFoto = forceDownloadFoto
        val profileRes = discenteFake.refreshDiscenteResumo()
        if (profileRes is CoreResult.Success) {
            ruFake.refreshSaldoExtrato()
        }
        return profileRes
    }

    override fun startBackgroundPreload(includeTurmaDetails: Boolean) {
        startBackgroundPreloadCalls++
        _preloadProgress.value = PreloadProgress(
            phase = PreloadPhase.COMPLETED,
            completedSteps = 1,
            totalSteps = 1,
        )
    }

    override suspend fun silentRefreshAll(includeTurmaDetails: Boolean): CoreResult<PortalHomeSyncSummary> {
        silentRefreshCalls++
        lastSilentRefreshIncludeTurmaDetails = includeTurmaDetails
        when (val session = authFake.ensureValidSession()) {
            is CoreResult.Failure -> return session
            is CoreResult.Success -> Unit
        }
        val result = silentRefreshResult ?: syncPortalResult
        if (result is CoreResult.Success) {
            startBackgroundPreload(includeTurmaDetails = includeTurmaDetails)
        }
        return result
    }

    fun emitPreloadProgress(progress: PreloadProgress) {
        _preloadProgress.value = progress
    }

    override fun observeSigaaNotificacoesConfig(): Flow<SigaaNotificacaoConfig> = sigaaNotificacoesFlow

    override suspend fun setSigaaNotificacoesConfig(config: SigaaNotificacaoConfig): CoreResult<Unit> {
        sigaaNotificacoesFlow.value = config
        return CoreResult.Success(Unit)
    }

    override suspend fun autoEnableSigaaNotificacoesIfEligible(): CoreResult<Boolean> {
        autoEnableSigaaCalls++
        val current = sigaaNotificacoesFlow.value
        if (current.userConfigured || current.habilitado) return CoreResult.Success(false)
        sigaaNotificacoesFlow.value = current.copy(habilitado = true)
        return CoreResult.Success(true)
    }
}

class FakeAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    var savedCredentials: Boolean = false
    var nextLoginResult: CoreResult<AuthState.Authenticated> = CoreResult.Success(
        AuthState.Authenticated(
            matricula = "231012345",
            idDiscente = "100",
            sessionExpiresAtEpochMillis = 9_999L,
            hasStoredCredentials = true,
        ),
    )
    var nextEnsureResult: CoreResult<AuthState.Authenticated> = nextLoginResult

    override suspend fun login(credentials: CredentialsInput): CoreResult<AuthState.Authenticated> {
        if (nextLoginResult is CoreResult.Success) {
            savedCredentials = true
            _authState.value = (nextLoginResult as CoreResult.Success).value
        }
        return nextLoginResult
    }

    override suspend fun ensureValidSession(): CoreResult<AuthState.Authenticated> {
        if (nextEnsureResult is CoreResult.Success) {
            _authState.value = (nextEnsureResult as CoreResult.Success).value
        }
        return nextEnsureResult
    }

    override suspend fun logout(clearCachedData: Boolean): CoreResult<Unit> {
        savedCredentials = false
        _authState.value = AuthState.Unauthenticated
        return CoreResult.Success(Unit)
    }

    override suspend fun hasSavedCredentials(): Boolean = savedCredentials
}

class FakeRuRepository : RuRepository {
    val preferencias = MutableStateFlow(RuPreferencias())
    val cardapios = MutableStateFlow<Map<CampusRu, SyncedData<CardapioSemanalRu>>>(emptyMap())
    val saldoExtrato = MutableStateFlow<SyncedData<SaldoExtratoRu>?>(null)
    val tabelaPrecos = MutableStateFlow<SyncedData<TabelaPrecosRu>?>(null)
    val noticias = MutableStateFlow<SyncedData<List<NoticiaRu>>?>(null)

    val refreshedCampuses = mutableListOf<CampusRu>()
    var syncCardapiosCalls = 0
    var nextRefreshCardapioResult: CoreResult<CardapioSemanalRu>? = null
    var nextSyncCardapiosResult: CoreResult<RuAutoSyncSummary>? = null
    var nextRefreshNoticiasResult: CoreResult<List<NoticiaRu>>? = null

    override fun observePreferencias(): Flow<RuPreferencias> = preferencias

    override suspend fun setCampusSelecionado(campus: CampusRu): CoreResult<Unit> {
        preferencias.value = preferencias.value.copy(campusSelecionado = campus)
        return CoreResult.Success(Unit)
    }

    override suspend fun setAlergenicosRestritos(
        alergenicos: Set<Alergenico>,
        ocultarPratos: Boolean,
    ): CoreResult<Unit> {
        preferencias.value = preferencias.value.copy(
            alergenicosRestritos = alergenicos,
            ocultarPratosComAlergenicos = ocultarPratos,
        )
        return CoreResult.Success(Unit)
    }

    override suspend fun setNotificacoesConfig(config: RuNotificacaoConfig): CoreResult<Unit> {
        preferencias.value = preferencias.value.copy(notificacoes = config)
        return CoreResult.Success(Unit)
    }

    override suspend fun prepareNotificacaoRefeicao(
        refeicao: TipoRefeicao,
        nowEpochMillis: Long,
    ): CoreResult<RuNotificacaoConteudo?> = CoreResult.Success(null)

    override fun observeCardapio(campus: CampusRu): Flow<SyncedData<CardapioSemanalRu>?> =
        cardapios.map { it[campus] }

    override suspend fun refreshCardapio(campus: CampusRu, force: Boolean): CoreResult<CardapioSemanalRu> {
        refreshedCampuses += campus
        nextRefreshCardapioResult?.let { return it }
        if (!force) {
            cardapios.value[campus]?.value?.let { return CoreResult.Success(it) }
        }
        val fallback = CardapioSemanalRu(
            campus = campus,
            semanaLabel = "Semana fake",
            pdfUrl = "https://ru.unb.br/test.pdf",
            fallbackImageUrl = null,
            localPdfPath = null,
            modifiedGmt = "",
            parserStatus = CardapioParserStatus.PARSED_STRUCTURED,
            dias = emptyList(),
        )
        cardapios.value = cardapios.value + (campus to SyncedData(fallback, DataOrigin.REMOTE, 1000L))
        return CoreResult.Success(fallback)
    }

    override suspend fun syncCardapiosIfNeeded(
        forceCheck: Boolean,
        includeExtras: Boolean,
    ): CoreResult<RuAutoSyncSummary> {
        syncCardapiosCalls++
        nextSyncCardapiosResult?.let { return it }
        return CoreResult.Success(
            RuAutoSyncSummary(
                checkedAtEpochMillis = 2000L,
                skippedDueToCooldown = false,
                updatedCampi = emptySet(),
                upToDateCampi = cardapios.value.keys,
                failedCampi = emptyMap(),
            ),
        )
    }

    override fun observeSaldoExtrato(): Flow<SyncedData<SaldoExtratoRu>?> = saldoExtrato

    override suspend fun refreshSaldoExtrato(): CoreResult<SaldoExtratoRu> =
        saldoExtrato.value?.let { CoreResult.Success(it.value) }
            ?: CoreResult.Failure(CoreError.NoStoredCredentials)

    override fun observeTabelaPrecos(): Flow<SyncedData<TabelaPrecosRu>?> = tabelaPrecos

    override suspend fun refreshTabelaPrecos(force: Boolean): CoreResult<TabelaPrecosRu> =
        CoreResult.Success(
            TabelaPrecosRu(
                resolucaoReferencia = "Res. 0037/2025",
                modifiedGmt = "",
                grupos = emptyList(),
                formasPagamento = listOf("PIX"),
            ),
        )

    override fun observeNoticias(): Flow<SyncedData<List<NoticiaRu>>?> = noticias

    override suspend fun refreshNoticias(perPage: Int): CoreResult<List<NoticiaRu>> {
        val result = nextRefreshNoticiasResult ?: CoreResult.Success(noticias.value?.value.orEmpty())
        if (result is CoreResult.Success) {
            noticias.value = SyncedData(result.value, DataOrigin.REMOTE, 2000L)
        }
        return result
    }

    override suspend fun markNoticiasVistas(ids: Set<Long>): CoreResult<Unit> {
        if (ids.isEmpty()) return CoreResult.Success(Unit)
        preferencias.value = preferencias.value.copy(
            noticiasVistasIds = preferencias.value.noticiasVistasIds + ids,
        )
        return CoreResult.Success(Unit)
    }

    override suspend fun downloadCardapioPdfLocal(campus: CampusRu): CoreResult<String> =
        CoreResult.Failure(CoreError.ReadOnlyViolation("cardapio", "not used"))
}

class FakeTurmasRepository : TurmasRepository {
    val turmas = MutableStateFlow<SyncedData<List<TurmaResumo>>?>(null)
    val atualizacoes = MutableStateFlow<SyncedData<List<AtualizacaoTurmaFeed>>?>(null)
    val detalhe = MutableStateFlow<SyncedData<TurmaDetalheAva>?>(null)
    val frequencia = MutableStateFlow<SyncedData<MapaFrequenciaTurma>?>(null)
    val notas = MutableStateFlow<SyncedData<NotasParciaisTurma>?>(null)
    val avaliacoes = MutableStateFlow<SyncedData<List<AvaliacaoTurma>>?>(null)
    val tarefas = MutableStateFlow<SyncedData<List<TarefaTurma>>?>(null)

    var refreshDetalheCalls = 0
    var refreshFrequenciaCalls = 0
    var refreshNotasCalls = 0
    var getNoticiaCompletaCalls = 0
    val refreshSemestreEnriquecerHistory = mutableListOf<Boolean>()
    var nextRefreshSemestreResult: CoreResult<List<TurmaResumo>>? = null
    var nextRefreshDetalheResult: CoreResult<TurmaDetalheAva>? = null
    var onGetNoticiaCompleta: (suspend (String, String) -> CoreResult<NoticiaTurmaAva>)? = null
    var downloadedMaterialResult: CoreResult<ArquivoBaixado> = CoreResult.Success(
        ArquivoBaixado("slides.pdf", "application/pdf", "/tmp/slides.pdf", 120L),
    )

    override fun observeTurmasSemestre(): Flow<SyncedData<List<TurmaResumo>>?> = turmas
    override fun observeAtualizacoesFeed(): Flow<SyncedData<List<AtualizacaoTurmaFeed>>?> = atualizacoes
    override suspend fun refreshTurmasSemestre(enriquecerViaAtestado: Boolean): CoreResult<List<TurmaResumo>> {
        refreshSemestreEnriquecerHistory += enriquecerViaAtestado
        return nextRefreshSemestreResult ?: CoreResult.Success(turmas.value?.value.orEmpty())
    }

    override fun observeTurmaDetalhe(frontEndIdTurma: String): Flow<SyncedData<TurmaDetalheAva>?> = detalhe
    override suspend fun refreshTurmaDetalhe(frontEndIdTurma: String): CoreResult<TurmaDetalheAva> {
        refreshDetalheCalls++
        nextRefreshDetalheResult?.let { return it }
        return detalhe.value?.let { CoreResult.Success(it.value) }
            ?: CoreResult.Failure(CoreError.NetworkUnavailable)
    }

    override suspend fun getNoticiaCompleta(frontEndIdTurma: String, idNoticia: String): CoreResult<NoticiaTurmaAva> {
        getNoticiaCompletaCalls++
        return onGetNoticiaCompleta?.invoke(frontEndIdTurma, idNoticia)
            ?: CoreResult.Failure(CoreError.NetworkUnavailable)
    }

    override fun observeFrequencia(frontEndIdTurma: String): Flow<SyncedData<MapaFrequenciaTurma>?> = frequencia
    override suspend fun refreshFrequencia(frontEndIdTurma: String): CoreResult<MapaFrequenciaTurma> {
        refreshFrequenciaCalls++
        return CoreResult.Failure(CoreError.NetworkUnavailable)
    }

    override fun observeNotasParciais(frontEndIdTurma: String): Flow<SyncedData<NotasParciaisTurma>?> = notas
    override suspend fun refreshNotasParciais(frontEndIdTurma: String): CoreResult<NotasParciaisTurma> {
        refreshNotasCalls++
        return CoreResult.Failure(CoreError.NetworkUnavailable)
    }

    override fun observeAvaliacoes(frontEndIdTurma: String): Flow<SyncedData<List<AvaliacaoTurma>>?> = avaliacoes
    override suspend fun refreshAvaliacoes(frontEndIdTurma: String): CoreResult<List<AvaliacaoTurma>> =
        CoreResult.Success(emptyList())

    override fun observeTarefas(frontEndIdTurma: String): Flow<SyncedData<List<TarefaTurma>>?> = tarefas
    override suspend fun refreshTarefas(frontEndIdTurma: String): CoreResult<List<TarefaTurma>> =
        CoreResult.Success(emptyList())

    override suspend fun downloadMaterial(frontEndIdTurma: String, materialId: String): CoreResult<ArquivoBaixado> =
        downloadedMaterialResult

    override suspend fun downloadAnexoNoticia(frontEndIdTurma: String, anexoId: String): CoreResult<ArquivoBaixado> =
        downloadedMaterialResult
}

class FakeDiscenteRepository : DiscenteRepository {
    val resumo = MutableStateFlow<SyncedData<DiscenteResumo>?>(null)
    val carteirinha = MutableStateFlow<SyncedData<CarteirinhaEstudantil>?>(null)
    val boletim = MutableStateFlow<SyncedData<List<BoletimSemestre>>?>(null)
    val docs = MutableStateFlow<List<DocumentoOficialEmitido>>(emptyList())

    val emittedRequestedTypes = mutableListOf<TipoDocumentoOficial>()
    var refreshCarteirinhaCalls = 0
    var nextRefreshCarteirinhaResult: CoreResult<CarteirinhaEstudantil>? = null
    var nextRefreshBoletimResult: CoreResult<List<BoletimSemestre>>? = null

    override fun observeDiscenteResumo(): Flow<SyncedData<DiscenteResumo>?> = resumo
    override suspend fun refreshDiscenteResumo(): CoreResult<DiscenteResumo> =
        resumo.value?.let { CoreResult.Success(it.value) }
            ?: CoreResult.Failure(CoreError.NoStoredCredentials)

    override fun observeCarteirinha(): Flow<SyncedData<CarteirinhaEstudantil>?> = carteirinha
    override suspend fun refreshCarteirinha(force: Boolean): CoreResult<CarteirinhaEstudantil> {
        refreshCarteirinhaCalls++
        nextRefreshCarteirinhaResult?.let { return it }
        val item = CarteirinhaEstudantil(
            nomeCompleto = "Estudante UnB",
            matricula = "231012345",
            cpfTexto = "000.000.000-00",
            curso = "Engenharia de Computação",
            nivel = "GRADUAÇÃO",
            validadeTexto = "03/2027",
            qrPayload16Digitos = "1234567890123456",
            qrImageLocalPath = "/tmp/qr.png",
            foto3x4LocalPath = null,
            pdfLocalPath = "",
        )
        carteirinha.value = SyncedData(item, DataOrigin.REMOTE, 2000L)
        return CoreResult.Success(item)
    }

    override fun observeBoletimGeral(): Flow<SyncedData<List<BoletimSemestre>>?> = boletim
    override suspend fun refreshBoletimGeral(): CoreResult<List<BoletimSemestre>> =
        nextRefreshBoletimResult ?: CoreResult.Success(boletim.value?.value.orEmpty())

    override fun observeDocumentosEmitidos(): Flow<List<DocumentoOficialEmitido>> = docs
    override suspend fun emitirDocumentoOficial(tipo: TipoDocumentoOficial): CoreResult<DocumentoOficialEmitido> {
        emittedRequestedTypes += tipo
        val doc = DocumentoOficialEmitido(
            tipo = tipo,
            titulo = tipo.displayName,
            codigoVerificacao = "ABC123",
            mimeType = tipo.mimeType,
            localFilePath = "/tmp/doc.pdf",
            emitidoEmEpochMillis = 3000L,
        )
        docs.value = listOf(doc) + docs.value
        return CoreResult.Success(doc)
    }
}
