package com.unbpocket.core.data

import android.content.Context
import com.unbpocket.core.data.local.CredentialVault
import com.unbpocket.core.data.local.DownloadedFileStore
import com.unbpocket.core.data.local.FileStore
import com.unbpocket.core.data.local.PdfExtractor
import com.unbpocket.core.data.local.RuPreferencesStore
import com.unbpocket.core.data.local.RuPrefsSource
import com.unbpocket.core.data.local.SnapshotCacheStore
import com.unbpocket.core.data.local.SnapshotDao
import com.unbpocket.core.data.local.UnbPocketDatabase
import com.unbpocket.core.data.pdf.AndroidPdfBoxExtractor
import com.unbpocket.core.data.repository.DiscenteRepositoryImpl
import com.unbpocket.core.data.repository.RuRepositoryImpl
import com.unbpocket.core.data.repository.TurmasRepositoryImpl
import com.unbpocket.core.data.security.KeystoreCredentialVault
import com.unbpocket.core.data.session.SigaaSessionCoordinator
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.PortalHomeSyncSummary
import com.unbpocket.core.model.contract.AuthRepository
import com.unbpocket.core.model.contract.DiscenteRepository
import com.unbpocket.core.model.contract.RuRepository
import com.unbpocket.core.model.contract.TurmasRepository
import com.unbpocket.core.model.contract.UnbPocketCore
import com.unbpocket.core.network.UnbHttpTransport

/**
 * Fabrica do ponto de entrada unico do Core/Backend para a camada Android (`:app`).
 */
object UnbPocketCoreFactory {
    fun create(context: Context): UnbPocketCore {
        val appContext = context.applicationContext
        val database = UnbPocketDatabase.build(appContext)
        return createWithPorts(
            transport = UnbHttpTransport(),
            snapshotDao = database.snapshotDao(),
            vault = KeystoreCredentialVault(appContext),
            ruPrefs = RuPreferencesStore(appContext),
            fileStore = DownloadedFileStore(appContext),
            pdfExtractor = AndroidPdfBoxExtractor(appContext),
        )
    }

    internal fun createWithPorts(
        transport: UnbHttpTransport,
        snapshotDao: SnapshotDao,
        vault: CredentialVault,
        ruPrefs: RuPrefsSource,
        fileStore: FileStore,
        pdfExtractor: PdfExtractor,
        clockMillis: () -> Long = { System.currentTimeMillis() },
    ): UnbPocketCore {
        val cacheStore = SnapshotCacheStore(snapshotDao)
        val coordinator = SigaaSessionCoordinator(
            transport = transport,
            vault = vault,
            cacheStore = cacheStore,
            fileStore = fileStore,
            clockMillis = clockMillis,
        )
        val ruRepo = RuRepositoryImpl(
            transport = transport,
            coordinator = coordinator,
            cacheStore = cacheStore,
            prefsSource = ruPrefs,
            fileStore = fileStore,
            pdfExtractor = pdfExtractor,
            clockMillis = clockMillis,
        )
        val turmasRepo = TurmasRepositoryImpl(
            coordinator = coordinator,
            cacheStore = cacheStore,
            fileStore = fileStore,
        )
        val discenteRepo = DiscenteRepositoryImpl(
            coordinator = coordinator,
            cacheStore = cacheStore,
            fileStore = fileStore,
            pdfExtractor = pdfExtractor,
        )
        return DefaultUnbPocketCore(
            coordinator = coordinator,
            ruRepo = ruRepo,
            turmasRepo = turmasRepo,
            discenteRepo = discenteRepo,
            clockMillis = clockMillis,
        )
    }
}

private class DefaultUnbPocketCore(
    private val coordinator: SigaaSessionCoordinator,
    private val ruRepo: RuRepositoryImpl,
    private val turmasRepo: TurmasRepositoryImpl,
    private val discenteRepo: DiscenteRepositoryImpl,
    private val clockMillis: () -> Long,
) : UnbPocketCore {
    override val auth: AuthRepository = coordinator
    override val ru: RuRepository = ruRepo
    override val turmas: TurmasRepository = turmasRepo
    override val discente: DiscenteRepository = discenteRepo

    override suspend fun syncPortalHome(includeSaldoRu: Boolean): CoreResult<PortalHomeSyncSummary> =
        coordinator.withAuthenticatedJsf { nav ->
            val homeHtml = when (val home = nav.getPortalHomeHtml()) {
                is CoreResult.Failure -> return@withAuthenticatedJsf home
                is CoreResult.Success -> home.value
            }

            when (val resumoRes = discenteRepo.persistResumoFromHomeHtml(nav, homeHtml)) {
                is CoreResult.Failure -> return@withAuthenticatedJsf resumoRes
                is CoreResult.Success -> Unit
            }

            val saldoAtualizado = if (includeSaldoRu) {
                when (val saldoRes = ruRepo.refreshSaldoExtratoFromHomeHtml(nav, homeHtml)) {
                    is CoreResult.Failure -> {
                        if (saldoRes.error is CoreError.SessionExpired) return@withAuthenticatedJsf saldoRes
                        false
                    }
                    is CoreResult.Success -> true
                }
            } else {
                false
            }

            val turmas = when (
                val turmasRes = turmasRepo.persistHomeTurmasAndFeed(
                    nav = nav,
                    homeHtml = homeHtml,
                    enriquecerViaAtestado = true,
                )
            ) {
                is CoreResult.Failure -> return@withAuthenticatedJsf turmasRes
                is CoreResult.Success -> turmasRes.value
            }

            val feedCount = com.unbpocket.core.parser.DiscentePortalParser.parseAtualizacoesFeed(homeHtml).size
            CoreResult.Success(
                PortalHomeSyncSummary(
                    syncedAtEpochMillis = clockMillis(),
                    turmasCount = turmas.size,
                    atualizacoesCount = feedCount,
                    saldoRuAtualizado = saldoAtualizado,
                ),
            )
        }
}
