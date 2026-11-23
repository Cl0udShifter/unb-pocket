package com.unbpocket.core.model.contract

import com.unbpocket.core.model.auth.AuthState
import com.unbpocket.core.model.auth.CredentialsInput
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.PortalHomeSyncSummary
import com.unbpocket.core.model.common.PreloadProgress
import com.unbpocket.core.model.common.SyncedData
import com.unbpocket.core.model.discente.BoletimSemestre
import com.unbpocket.core.model.discente.CarteirinhaEstudantil
import com.unbpocket.core.model.discente.DiscenteResumo
import com.unbpocket.core.model.discente.DocumentoOficialEmitido
import com.unbpocket.core.model.discente.TipoDocumentoOficial
import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.CardapioSemanalRu
import com.unbpocket.core.model.ru.NoticiaRu
import com.unbpocket.core.model.ru.RuAutoSyncSummary
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato de autenticacao CAS SSO e gerenciamento seguro de sessao SIGAA.
 */
interface AuthRepository {
    val authState: StateFlow<AuthState>

    suspend fun login(credentials: CredentialsInput): CoreResult<AuthState.Authenticated>

    suspend fun ensureValidSession(): CoreResult<AuthState.Authenticated>

    suspend fun logout(clearCachedData: Boolean = true): CoreResult<Unit>

    suspend fun hasSavedCredentials(): Boolean
}

/**
 * Contrato da Tela 1 (Restaurante Universitario: cardapios dos 5 campi, saldo, precos e noticias).
 */
interface RuRepository {
    fun observePreferencias(): Flow<RuPreferencias>

    suspend fun setCampusSelecionado(campus: CampusRu): CoreResult<Unit>

    suspend fun setAlergenicosRestritos(
        alergenicos: Set<Alergenico>,
        ocultarPratos: Boolean,
    ): CoreResult<Unit>

    fun observeCardapio(campus: CampusRu): Flow<SyncedData<CardapioSemanalRu>?>

    suspend fun refreshCardapio(
        campus: CampusRu,
        force: Boolean = false,
    ): CoreResult<CardapioSemanalRu>

    suspend fun syncCardapiosIfNeeded(
        forceCheck: Boolean = false,
        includeExtras: Boolean = false,
    ): CoreResult<RuAutoSyncSummary>

    fun observeSaldoExtrato(): Flow<SyncedData<SaldoExtratoRu>?>

    suspend fun refreshSaldoExtrato(): CoreResult<SaldoExtratoRu>

    fun observeTabelaPrecos(): Flow<SyncedData<TabelaPrecosRu>?>

    suspend fun refreshTabelaPrecos(force: Boolean = false): CoreResult<TabelaPrecosRu>

    fun observeNoticias(): Flow<SyncedData<List<NoticiaRu>>?>

    suspend fun refreshNoticias(perPage: Int = 10): CoreResult<List<NoticiaRu>>

    suspend fun markNoticiasVistas(ids: Set<Long>): CoreResult<Unit>

    suspend fun downloadCardapioPdfLocal(campus: CampusRu): CoreResult<String>
}

/**
 * Contrato da Tela 2 (Turmas do semestre, feed, grade horaria e interior da Turma Virtual).
 */
interface TurmasRepository {
    fun observeTurmasSemestre(): Flow<SyncedData<List<TurmaResumo>>?>

    fun observeAtualizacoesFeed(): Flow<SyncedData<List<AtualizacaoTurmaFeed>>?>

    suspend fun refreshTurmasSemestre(
        enriquecerViaAtestado: Boolean = true,
    ): CoreResult<List<TurmaResumo>>

    fun observeTurmaDetalhe(frontEndIdTurma: String): Flow<SyncedData<TurmaDetalheAva>?>

    suspend fun refreshTurmaDetalhe(frontEndIdTurma: String): CoreResult<TurmaDetalheAva>

    suspend fun getNoticiaCompleta(
        frontEndIdTurma: String,
        idNoticia: String,
    ): CoreResult<NoticiaTurmaAva>

    fun observeFrequencia(frontEndIdTurma: String): Flow<SyncedData<MapaFrequenciaTurma>?>

    suspend fun refreshFrequencia(frontEndIdTurma: String): CoreResult<MapaFrequenciaTurma>

    fun observeNotasParciais(frontEndIdTurma: String): Flow<SyncedData<NotasParciaisTurma>?>

    suspend fun refreshNotasParciais(frontEndIdTurma: String): CoreResult<NotasParciaisTurma>

    fun observeAvaliacoes(frontEndIdTurma: String): Flow<SyncedData<List<AvaliacaoTurma>>?>

    suspend fun refreshAvaliacoes(frontEndIdTurma: String): CoreResult<List<AvaliacaoTurma>>

    fun observeTarefas(frontEndIdTurma: String): Flow<SyncedData<List<TarefaTurma>>?>

    suspend fun refreshTarefas(frontEndIdTurma: String): CoreResult<List<TarefaTurma>>

    suspend fun downloadMaterial(
        frontEndIdTurma: String,
        materialId: String,
    ): CoreResult<ArquivoBaixado>

    suspend fun downloadAnexoNoticia(
        frontEndIdTurma: String,
        anexoId: String,
    ): CoreResult<ArquivoBaixado>
}

/**
 * Contrato da Tela 3 (Perfil do Discente, indices, carteirinha QR, boletim e documentos).
 */
interface DiscenteRepository {
    fun observeDiscenteResumo(): Flow<SyncedData<DiscenteResumo>?>

    suspend fun refreshDiscenteResumo(): CoreResult<DiscenteResumo>

    fun observeCarteirinha(): Flow<SyncedData<CarteirinhaEstudantil>?>

    suspend fun refreshCarteirinha(force: Boolean = false): CoreResult<CarteirinhaEstudantil>

    fun observeBoletimGeral(): Flow<SyncedData<List<BoletimSemestre>>?>

    suspend fun refreshBoletimGeral(): CoreResult<List<BoletimSemestre>>

    fun observeDocumentosEmitidos(): Flow<List<DocumentoOficialEmitido>>

    suspend fun emitirDocumentoOficial(
        tipo: TipoDocumentoOficial,
    ): CoreResult<DocumentoOficialEmitido>
}

/**
 * Ponto de entrada unico exposto pelo modulo `:core:data` para a camada de UI (`:app`).
 */
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
