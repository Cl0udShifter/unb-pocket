package com.unbpocket.core.data

import android.content.Context
import com.unbpocket.core.data.local.RuPreferencesStore
import com.unbpocket.core.data.local.UnbPocketDatabase
import com.unbpocket.core.data.security.KeystoreCredentialVault
import com.unbpocket.core.model.auth.AuthState
import com.unbpocket.core.model.auth.CredentialsInput
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.PortalHomeSyncSummary
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
import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.CardapioSemanalRu
import com.unbpocket.core.model.ru.NoticiaRu
import com.unbpocket.core.model.ru.RuPreferencias
import com.unbpocket.core.model.ru.SaldoExtratoRu
import com.unbpocket.core.model.ru.TabelaPrecosRu
import com.unbpocket.core.model.turmas.ArquivoBaixado
import com.unbpocket.core.model.turmas.AtualizacaoTurmaFeed
import com.unbpocket.core.model.turmas.AvaliacaoTurma
import com.unbpocket.core.model.turmas.MapaFrequenciaTurma
import com.unbpocket.core.model.turmas.NotasParciaisTurma
import com.unbpocket.core.model.turmas.NoticiaTurmaAva
import com.unbpocket.core.model.turmas.TarefaTurma
import com.unbpocket.core.model.turmas.TurmaDetalheAva
import com.unbpocket.core.model.turmas.TurmaResumo
import com.unbpocket.core.network.UnbHttpSession
import com.unbpocket.core.parser.RuCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Fabrica do ponto de entrada unico do Core/Backend para a camada Android (`:app`).
 */
object UnbPocketCoreFactory {
    fun create(context: Context): UnbPocketCore {
        val appContext = context.applicationContext
        val database = UnbPocketDatabase.build(appContext)
        val vault = KeystoreCredentialVault(appContext)
        val ruPreferences = RuPreferencesStore(appContext)
        val session = UnbHttpSession()
        return DefaultUnbPocketCore(
            database = database,
            vault = vault,
            ruPreferences = ruPreferences,
            session = session,
        )
    }
}

private class DefaultUnbPocketCore(
    private val database: UnbPocketDatabase,
    private val vault: KeystoreCredentialVault,
    private val ruPreferences: RuPreferencesStore,
    private val session: UnbHttpSession,
) : UnbPocketCore {
    override val auth: AuthRepository = DefaultAuthRepository(database, vault, session)
    override val ru: RuRepository = DefaultRuRepository(ruPreferences)
    override val turmas: TurmasRepository = DefaultTurmasRepository()
    override val discente: DiscenteRepository = DefaultDiscenteRepository()

    override suspend fun syncPortalHome(includeSaldoRu: Boolean): CoreResult<PortalHomeSyncSummary> =
        notConnectedYet("syncPortalHome")
}

private class DefaultAuthRepository(
    private val database: UnbPocketDatabase,
    private val vault: KeystoreCredentialVault,
    private val session: UnbHttpSession,
) : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override suspend fun login(credentials: CredentialsInput): CoreResult<AuthState.Authenticated> =
        notConnectedYet("AuthRepository.login")

    override suspend fun ensureValidSession(): CoreResult<AuthState.Authenticated> =
        if (vault.exists()) {
            notConnectedYet("AuthRepository.ensureValidSession")
        } else {
            CoreResult.Failure(CoreError.NoStoredCredentials)
        }

    override suspend fun logout(clearCachedData: Boolean): CoreResult<Unit> = runCatching {
        session.cookieJar.clear()
        vault.clear()
        if (clearCachedData) {
            database.snapshotDao().clearAll()
        }
        _authState.value = AuthState.Unauthenticated
    }.fold(
        onSuccess = { CoreResult.Success(Unit) },
        onFailure = { error ->
            CoreResult.Failure(CoreError.StorageError("logout", error.message ?: "Falha ao limpar sessao"))
        },
    )

    override suspend fun hasSavedCredentials(): Boolean = vault.exists()
}

private class DefaultRuRepository(
    private val ruPreferences: RuPreferencesStore,
) : RuRepository {
    override fun observePreferencias(): Flow<RuPreferencias> = ruPreferences.preferencias

    override suspend fun setCampusSelecionado(campus: CampusRu): CoreResult<Unit> = runCatching {
        ruPreferences.setCampus(campus)
    }.fold(
        onSuccess = { CoreResult.Success(Unit) },
        onFailure = { CoreResult.Failure(CoreError.StorageError("setCampusSelecionado", it.message.orEmpty())) },
    )

    override suspend fun setAlergenicosRestritos(
        alergenicos: Set<Alergenico>,
        ocultarPratos: Boolean,
    ): CoreResult<Unit> = runCatching {
        ruPreferences.setAlergenicos(alergenicos, ocultarPratos)
    }.fold(
        onSuccess = { CoreResult.Success(Unit) },
        onFailure = { CoreResult.Failure(CoreError.StorageError("setAlergenicosRestritos", it.message.orEmpty())) },
    )

    override fun observeCardapio(campus: CampusRu): Flow<SyncedData<CardapioSemanalRu>?> = flowOf(null)

    override suspend fun refreshCardapio(campus: CampusRu, force: Boolean): CoreResult<CardapioSemanalRu> =
        notConnectedYet("RuRepository.refreshCardapio")

    override fun observeSaldoExtrato(): Flow<SyncedData<SaldoExtratoRu>?> = flowOf(null)

    override suspend fun refreshSaldoExtrato(): CoreResult<SaldoExtratoRu> =
        notConnectedYet("RuRepository.refreshSaldoExtrato")

    override fun observeTabelaPrecos(): Flow<SyncedData<TabelaPrecosRu>?> = flowOf(
        SyncedData(
            value = RuCalculator.defaultPrecosResolucao0037,
            origin = com.unbpocket.core.model.common.DataOrigin.FALLBACK,
            syncedAtEpochMillis = 0L,
        ),
    )

    override suspend fun refreshTabelaPrecos(force: Boolean): CoreResult<TabelaPrecosRu> =
        CoreResult.Success(RuCalculator.defaultPrecosResolucao0037)

    override fun observeNoticias(): Flow<SyncedData<List<NoticiaRu>>?> = flowOf(null)

    override suspend fun refreshNoticias(perPage: Int): CoreResult<List<NoticiaRu>> =
        notConnectedYet("RuRepository.refreshNoticias")

    override suspend fun downloadCardapioPdfLocal(campus: CampusRu): CoreResult<String> =
        notConnectedYet("RuRepository.downloadCardapioPdfLocal")
}

private class DefaultTurmasRepository : TurmasRepository {
    override fun observeTurmasSemestre(): Flow<SyncedData<List<TurmaResumo>>?> = flowOf(null)
    override fun observeAtualizacoesFeed(): Flow<SyncedData<List<AtualizacaoTurmaFeed>>?> = flowOf(null)
    override suspend fun refreshTurmasSemestre(enriquecerViaAtestado: Boolean): CoreResult<List<TurmaResumo>> =
        notConnectedYet("TurmasRepository.refreshTurmasSemestre")
    override fun observeTurmaDetalhe(frontEndIdTurma: String): Flow<SyncedData<TurmaDetalheAva>?> = flowOf(null)
    override suspend fun refreshTurmaDetalhe(frontEndIdTurma: String): CoreResult<TurmaDetalheAva> =
        notConnectedYet("TurmasRepository.refreshTurmaDetalhe")
    override suspend fun getNoticiaCompleta(frontEndIdTurma: String, idNoticia: String): CoreResult<NoticiaTurmaAva> =
        notConnectedYet("TurmasRepository.getNoticiaCompleta")
    override fun observeFrequencia(frontEndIdTurma: String): Flow<SyncedData<MapaFrequenciaTurma>?> = flowOf(null)
    override suspend fun refreshFrequencia(frontEndIdTurma: String): CoreResult<MapaFrequenciaTurma> =
        notConnectedYet("TurmasRepository.refreshFrequencia")
    override fun observeNotasParciais(frontEndIdTurma: String): Flow<SyncedData<NotasParciaisTurma>?> = flowOf(null)
    override suspend fun refreshNotasParciais(frontEndIdTurma: String): CoreResult<NotasParciaisTurma> =
        notConnectedYet("TurmasRepository.refreshNotasParciais")
    override fun observeAvaliacoes(frontEndIdTurma: String): Flow<SyncedData<List<AvaliacaoTurma>>?> = flowOf(null)
    override suspend fun refreshAvaliacoes(frontEndIdTurma: String): CoreResult<List<AvaliacaoTurma>> =
        notConnectedYet("TurmasRepository.refreshAvaliacoes")
    override fun observeTarefas(frontEndIdTurma: String): Flow<SyncedData<List<TarefaTurma>>?> = flowOf(null)
    override suspend fun refreshTarefas(frontEndIdTurma: String): CoreResult<List<TarefaTurma>> =
        notConnectedYet("TurmasRepository.refreshTarefas")
    override suspend fun downloadMaterial(frontEndIdTurma: String, materialId: String): CoreResult<ArquivoBaixado> =
        notConnectedYet("TurmasRepository.downloadMaterial")
    override suspend fun downloadAnexoNoticia(frontEndIdTurma: String, anexoId: String): CoreResult<ArquivoBaixado> =
        notConnectedYet("TurmasRepository.downloadAnexoNoticia")
}

private class DefaultDiscenteRepository : DiscenteRepository {
    override fun observeDiscenteResumo(): Flow<SyncedData<DiscenteResumo>?> = flowOf(null)
    override suspend fun refreshDiscenteResumo(): CoreResult<DiscenteResumo> =
        notConnectedYet("DiscenteRepository.refreshDiscenteResumo")
    override fun observeCarteirinha(): Flow<SyncedData<CarteirinhaEstudantil>?> = flowOf(null)
    override suspend fun refreshCarteirinha(force: Boolean): CoreResult<CarteirinhaEstudantil> =
        notConnectedYet("DiscenteRepository.refreshCarteirinha")
    override fun observeBoletimGeral(): Flow<SyncedData<List<BoletimSemestre>>?> = flowOf(null)
    override suspend fun refreshBoletimGeral(): CoreResult<List<BoletimSemestre>> =
        notConnectedYet("DiscenteRepository.refreshBoletimGeral")
    override fun observeDocumentosEmitidos(): Flow<List<DocumentoOficialEmitido>> = flowOf(emptyList())
    override suspend fun emitirDocumentoOficial(tipo: TipoDocumentoOficial): CoreResult<DocumentoOficialEmitido> =
        notConnectedYet("DiscenteRepository.emitirDocumentoOficial")
}

private fun notConnectedYet(operation: String): CoreResult.Failure =
    CoreResult.Failure(
        CoreError.Unexpected("$operation sera conectado na fase de implementacao dos adaptadores remotos."),
    )
