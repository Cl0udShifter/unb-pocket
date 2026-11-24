package com.unbpocket.core.data.repository

import com.unbpocket.core.data.local.CacheMappers
import com.unbpocket.core.data.local.CardapioSemanalRuDto
import com.unbpocket.core.data.local.FileStore
import com.unbpocket.core.data.local.PdfExtractor
import com.unbpocket.core.data.local.RuAutoSyncMetaDto
import com.unbpocket.core.data.local.RuNotificationSchedulerPort
import com.unbpocket.core.data.local.RuPrefsSource
import com.unbpocket.core.data.local.SaldoExtratoRuDto
import com.unbpocket.core.data.local.SnapshotCacheStore
import com.unbpocket.core.data.local.TabelaPrecosRuDto
import com.unbpocket.core.data.session.SigaaSessionCoordinator
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.DataOrigin
import com.unbpocket.core.model.common.SyncedData
import com.unbpocket.core.model.contract.RuRepository
import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.CardapioParserStatus
import com.unbpocket.core.model.ru.CardapioSemanalRu
import com.unbpocket.core.model.ru.DiaSemanaRu
import com.unbpocket.core.model.ru.NoticiaRu
import com.unbpocket.core.model.ru.RuAutoSyncSummary
import com.unbpocket.core.model.ru.RuNotificacaoConfig
import com.unbpocket.core.model.ru.RuNotificacaoConteudo
import com.unbpocket.core.model.ru.RuPreferencias
import com.unbpocket.core.model.ru.SaldoExtratoRu
import com.unbpocket.core.model.ru.TabelaPrecosRu
import com.unbpocket.core.model.ru.TipoRefeicao
import com.unbpocket.core.network.UnbHttpTransport
import com.unbpocket.core.parser.CampusPdfLink
import com.unbpocket.core.parser.CasAndJsfParser
import com.unbpocket.core.parser.DiscentePortalParser
import com.unbpocket.core.parser.RuCalculator
import com.unbpocket.core.parser.RuMenuFreshness
import com.unbpocket.core.parser.RuMenuPdfParser
import com.unbpocket.core.parser.RuNotificationPlanner
import com.unbpocket.core.parser.RuWpMenuPage
import com.unbpocket.core.parser.RuWpParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repositorio do Modulo RU (`ru.unb.br` via WP REST API v2 + PDFs semanais + saldo SIGAA).
 */
class RuRepositoryImpl(
    private val transport: UnbHttpTransport,
    private val coordinator: SigaaSessionCoordinator,
    private val cacheStore: SnapshotCacheStore,
    private val prefsSource: RuPrefsSource,
    private val fileStore: FileStore,
    private val pdfExtractor: PdfExtractor,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val notificationScheduler: RuNotificationSchedulerPort = RuNotificationSchedulerPort { },
) : RuRepository {
    private val syncMutex = Mutex()

    override fun observePreferencias(): Flow<RuPreferencias> = prefsSource.preferencias

    override suspend fun setCampusSelecionado(campus: CampusRu): CoreResult<Unit> = runCatching {
        prefsSource.setCampus(campus)
    }.fold(
        onSuccess = { CoreResult.Success(Unit) },
        onFailure = { CoreResult.Failure(CoreError.StorageError("setCampusSelecionado", it.message.orEmpty())) },
    )

    override suspend fun setAlergenicosRestritos(
        alergenicos: Set<Alergenico>,
        ocultarPratos: Boolean,
    ): CoreResult<Unit> = runCatching {
        prefsSource.setAlergenicos(alergenicos, ocultarPratos)
    }.fold(
        onSuccess = { CoreResult.Success(Unit) },
        onFailure = { CoreResult.Failure(CoreError.StorageError("setAlergenicosRestritos", it.message.orEmpty())) },
    )

    override suspend fun setNotificacoesConfig(config: RuNotificacaoConfig): CoreResult<Unit> = runCatching {
        prefsSource.setNotificacoes(config)
        notificationScheduler.syncSchedule(config)
    }.fold(
        onSuccess = { CoreResult.Success(Unit) },
        onFailure = { CoreResult.Failure(CoreError.StorageError("setNotificacoesConfig", it.message.orEmpty())) },
    )

    override suspend fun prepareNotificacaoRefeicao(
        refeicao: TipoRefeicao,
        nowEpochMillis: Long,
    ): CoreResult<RuNotificacaoConteudo?> = runCatching {
        val prefs = prefsSource.preferencias.first()
        val today = RuMenuFreshness.localDateInBrasilia(nowEpochMillis)
        val diaSemana = DiaSemanaRu.fromDayOfWeekIso(today.dayOfWeek.value)
        if (diaSemana == null || !prefs.notificacoes.isEnabledFor(diaSemana, refeicao)) {
            return@runCatching null
        }

        val campus = prefs.campusSelecionado
        var cardapio = observeCardapio(campus).first()?.value
        val needsRemoteCheck = cardapio == null || RuNotificationPlanner.findDayIndexForDate(cardapio, today) == null
        if (needsRemoteCheck) {
            val refreshed = refreshCardapio(campus, force = false)
            if (refreshed is CoreResult.Success) {
                cardapio = refreshed.value
            }
        }

        RuNotificationPlanner.buildNotificationContentOrFallback(
            cardapio = cardapio,
            preferencias = prefs,
            refeicao = refeicao,
            nowEpochMillis = nowEpochMillis,
        )
    }.fold(
        onSuccess = { CoreResult.Success(it) },
        onFailure = { CoreResult.Failure(CoreError.StorageError("prepareNotificacaoRefeicao", it.message.orEmpty())) },
    )

    override suspend fun markNoticiasVistas(ids: Set<Long>): CoreResult<Unit> = runCatching {
        prefsSource.markNoticiasVistas(ids)
    }.fold(
        onSuccess = { CoreResult.Success(Unit) },
        onFailure = { CoreResult.Failure(CoreError.StorageError("markNoticiasVistas", it.message.orEmpty())) },
    )

    override fun observeCardapio(campus: CampusRu): Flow<SyncedData<CardapioSemanalRu>?> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.cardapio(campus),
            serializer = CardapioSemanalRuDto.serializer(),
            fromDto = CacheMappers::fromDto,
        )

    override suspend fun refreshCardapio(
        campus: CampusRu,
        force: Boolean,
    ): CoreResult<CardapioSemanalRu> = syncMutex.withLock {
        val now = clockMillis()
        val parsedPage = when (val res = fetchMenuPage(now)) {
            is CoreResult.Failure -> return@withLock res
            is CoreResult.Success -> res.value
        }

        val campusLink = parsedPage.campusLinks[campus]
            ?: return@withLock CoreResult.Failure(
                CoreError.ParseError("wp/v2/pages/12853", "Cardapio nao encontrado para ${campus.displayName}."),
            )

        val cached = getCachedCardapio(campus)
        if (!force && isCacheUpToDate(cached, parsedPage.modifiedGmt, campusLink.pdfUrl)) {
            return@withLock CoreResult.Success(cached!!)
        }

        when (val res = fetchAndPersistCampusMenu(campus, parsedPage.modifiedGmt, campusLink)) {
            is CoreResult.Failure -> res
            is CoreResult.Success -> CoreResult.Success(res.value.first)
        }
    }

    override suspend fun syncCardapiosIfNeeded(
        forceCheck: Boolean,
        includeExtras: Boolean,
    ): CoreResult<RuAutoSyncSummary> = syncMutex.withLock {
        val now = clockMillis()
        val cachedByCampus = CampusRu.entries.associateWith { campus -> getCachedCardapio(campus) }
        val lastCheckEpochMillis = cacheStore.get(
            cacheKey = SnapshotCacheStore.Keys.RU_AUTO_SYNC_META,
            serializer = RuAutoSyncMetaDto.serializer(),
            fromDto = { it.lastCheckEpochMillis },
        )?.value

        val shouldCheck = RuMenuFreshness.shouldCheckRemote(
            cachedByCampus = cachedByCampus,
            lastCheckEpochMillis = lastCheckEpochMillis,
            nowEpochMillis = now,
            forceCheck = forceCheck,
        )
        if (!shouldCheck) {
            return@withLock CoreResult.Success(
                RuAutoSyncSummary(
                    checkedAtEpochMillis = lastCheckEpochMillis ?: now,
                    skippedDueToCooldown = true,
                    updatedCampi = emptySet(),
                    upToDateCampi = cachedByCampus.filterValues { it != null }.keys,
                    failedCampi = emptyMap(),
                ),
            )
        }

        val parsedPage = when (val res = fetchMenuPage(now)) {
            is CoreResult.Failure -> return@withLock res
            is CoreResult.Success -> res.value
        }

        cacheStore.put(
            cacheKey = SnapshotCacheStore.Keys.RU_AUTO_SYNC_META,
            domain = SnapshotCacheStore.Domains.META,
            origin = DataOrigin.REMOTE,
            syncedAtEpochMillis = now,
            value = RuAutoSyncMetaDto(lastCheckEpochMillis = now),
            serializer = RuAutoSyncMetaDto.serializer(),
            toDto = { it },
        )

        val updatedCampi = linkedSetOf<CampusRu>()
        val upToDateCampi = linkedSetOf<CampusRu>()
        val failedCampi = linkedMapOf<CampusRu, CoreError>()

        for (campus in CampusRu.entries) {
            val campusLink = parsedPage.campusLinks[campus]
            if (campusLink == null) {
                failedCampi[campus] = CoreError.ParseError(
                    "wp/v2/pages/12853",
                    "Cardapio nao encontrado para ${campus.displayName}.",
                )
                continue
            }

            val cached = cachedByCampus[campus]
            if (isCacheUpToDate(cached, parsedPage.modifiedGmt, campusLink.pdfUrl)) {
                upToDateCampi += campus
                continue
            }

            when (val res = fetchAndPersistCampusMenu(campus, parsedPage.modifiedGmt, campusLink)) {
                is CoreResult.Failure -> failedCampi[campus] = res.error
                is CoreResult.Success -> {
                    val (_, changed) = res.value
                    if (changed) {
                        updatedCampi += campus
                    } else {
                        upToDateCampi += campus
                    }
                }
            }
        }

        val tabelaAtualizada = if (includeExtras) {
            (refreshTabelaPrecosWithDiff() as? CoreResult.Success)?.value?.second == true
        } else {
            false
        }
        val noticiasAtualizadas = if (includeExtras) {
            (refreshNoticiasWithDiff(perPage = 10) as? CoreResult.Success)?.value?.second == true
        } else {
            false
        }

        CoreResult.Success(
            RuAutoSyncSummary(
                checkedAtEpochMillis = now,
                skippedDueToCooldown = false,
                updatedCampi = updatedCampi,
                upToDateCampi = upToDateCampi,
                failedCampi = failedCampi,
                tabelaPrecosAtualizada = tabelaAtualizada,
                noticiasAtualizadas = noticiasAtualizadas,
            ),
        )
    }

    private suspend fun fetchMenuPage(nowEpochMillis: Long): CoreResult<RuWpMenuPage> {
        val pagePayload = when (val res = transport.get(transport.endpoints.ruCardapiosPageUrl)) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }
        val referenceDate = RuMenuFreshness.localDateInBrasilia(nowEpochMillis)
        return RuWpParser.parseMenuPage(pagePayload.decodeUtf8(), referenceDate = referenceDate)
    }

    private suspend fun getCachedCardapio(campus: CampusRu): CardapioSemanalRu? =
        cacheStore.get(
            cacheKey = SnapshotCacheStore.Keys.cardapio(campus),
            serializer = CardapioSemanalRuDto.serializer(),
            fromDto = CacheMappers::fromDto,
        )?.value

    private fun isCacheUpToDate(
        cached: CardapioSemanalRu?,
        modifiedGmt: String,
        pdfUrl: String,
    ): Boolean =
        cached != null &&
            cached.modifiedGmt == modifiedGmt &&
            cached.pdfUrl == pdfUrl &&
            cached.parserStatus == CardapioParserStatus.PARSED_STRUCTURED

    private suspend fun fetchAndPersistCampusMenu(
        campus: CampusRu,
        modifiedGmt: String,
        campusLink: CampusPdfLink,
    ): CoreResult<Pair<CardapioSemanalRu, Boolean>> {
        val pdfPayload = when (val res = transport.get(campusLink.pdfUrl)) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }

        val localPdfFile = runCatching {
            fileStore.saveBytes("pdfs", "cardapio_${campus.slug}.pdf", pdfPayload.bytes)
        }.getOrElse { error ->
            return CoreResult.Failure(CoreError.StorageError("saveCardapioPdf", error.message.orEmpty()))
        }

        val fallbackImageUrl = findFallbackImageUrl(campusLink.pdfUrl, campus)
        val parsedMenuResult = when (val pages = pdfExtractor.extractRuMenuPages(pdfPayload.bytes)) {
            is CoreResult.Failure -> pages
            is CoreResult.Success -> RuMenuPdfParser.parsePages(
                campus = campus,
                semanaLabel = campusLink.semanaLabel,
                pdfUrl = campusLink.pdfUrl,
                fallbackImageUrl = fallbackImageUrl,
                localPdfPath = localPdfFile.absolutePath,
                modifiedGmt = modifiedGmt,
                pages = pages.value,
            )
        }

        val now = clockMillis()
        return when (parsedMenuResult) {
            is CoreResult.Success -> {
                val changed = cacheStore.put(
                    cacheKey = SnapshotCacheStore.Keys.cardapio(campus),
                    domain = SnapshotCacheStore.Domains.RU,
                    origin = DataOrigin.REMOTE,
                    syncedAtEpochMillis = now,
                    value = parsedMenuResult.value,
                    serializer = CardapioSemanalRuDto.serializer(),
                    toDto = CacheMappers::toDto,
                )
                CoreResult.Success(parsedMenuResult.value to changed)
            }
            is CoreResult.Failure -> {
                val fallback = CardapioSemanalRu(
                    campus = campus,
                    semanaLabel = campusLink.semanaLabel,
                    pdfUrl = campusLink.pdfUrl,
                    fallbackImageUrl = fallbackImageUrl,
                    localPdfPath = localPdfFile.absolutePath,
                    modifiedGmt = modifiedGmt,
                    parserStatus = if (fallbackImageUrl != null) {
                        CardapioParserStatus.FALLBACK_IMAGE_ONLY
                    } else {
                        CardapioParserStatus.FALLBACK_PDF_ONLY
                    },
                    dias = emptyList(),
                )
                val changed = cacheStore.put(
                    cacheKey = SnapshotCacheStore.Keys.cardapio(campus),
                    domain = SnapshotCacheStore.Domains.RU,
                    origin = DataOrigin.FALLBACK,
                    syncedAtEpochMillis = now,
                    value = fallback,
                    serializer = CardapioSemanalRuDto.serializer(),
                    toDto = CacheMappers::toDto,
                )
                CoreResult.Success(fallback to changed)
            }
        }
    }

    override fun observeSaldoExtrato(): Flow<SyncedData<SaldoExtratoRu>?> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.RU_SALDO,
            serializer = SaldoExtratoRuDto.serializer(),
            fromDto = CacheMappers::fromDto,
        )

    override suspend fun refreshSaldoExtrato(): CoreResult<SaldoExtratoRu> =
        coordinator.withAuthenticatedJsf { nav ->
            val homeHtml = when (val home = nav.getPortalHomeHtml()) {
                is CoreResult.Failure -> return@withAuthenticatedJsf home
                is CoreResult.Success -> home.value
            }
            when (val res = refreshSaldoExtratoWithDiff(nav, homeHtml)) {
                is CoreResult.Failure -> res
                is CoreResult.Success -> CoreResult.Success(res.value.first)
            }
        }

    internal suspend fun refreshSaldoExtratoWithDiff(
        nav: com.unbpocket.core.data.session.SigaaNavigator,
        homeHtml: String,
    ): CoreResult<Pair<SaldoExtratoRu, Boolean>> {
        val extratoParams = CasAndJsfParser.extractExtratoRuFormParams(homeHtml)
            ?: return CoreResult.Failure(
                CoreError.UnavailableForProfile(
                    feature = "Saldo RU",
                    reason = "Formulario #formExibirExtrato nao disponivel na home do discente.",
                ),
            )

        val payload = when (val res = nav.postDiscenteForm(extratoParams)) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }

        val precos = getLatestPricesOrDefault()
        val parsed = DiscentePortalParser.parseExtratoRu(payload.decodeSigaaIso88591(), precos)
        return when (parsed) {
            is CoreResult.Failure -> parsed
            is CoreResult.Success -> {
                val changed = cacheStore.put(
                    cacheKey = SnapshotCacheStore.Keys.RU_SALDO,
                    domain = SnapshotCacheStore.Domains.RU,
                    origin = DataOrigin.REMOTE,
                    syncedAtEpochMillis = nav.nowMillis(),
                    value = parsed.value,
                    serializer = SaldoExtratoRuDto.serializer(),
                    toDto = CacheMappers::toDto,
                )
                CoreResult.Success(parsed.value to changed)
            }
        }
    }

    override fun observeTabelaPrecos(): Flow<SyncedData<TabelaPrecosRu>?> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.RU_PRECOS,
            serializer = TabelaPrecosRuDto.serializer(),
            fromDto = CacheMappers::fromDto,
        ).map { cached ->
            cached ?: SyncedData(
                value = RuCalculator.defaultPrecosResolucao0037,
                origin = DataOrigin.FALLBACK,
                syncedAtEpochMillis = 0L,
            )
        }

    override suspend fun refreshTabelaPrecos(force: Boolean): CoreResult<TabelaPrecosRu> =
        when (val res = refreshTabelaPrecosWithDiff()) {
            is CoreResult.Failure -> res
            is CoreResult.Success -> CoreResult.Success(res.value.first)
        }

    private suspend fun refreshTabelaPrecosWithDiff(): CoreResult<Pair<TabelaPrecosRu, Boolean>> {
        val payload = when (val res = transport.get(transport.endpoints.ruPrecosPageUrl)) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }
        val tabela = RuWpParser.parsePricesPage(payload.decodeUtf8())
        val changed = cacheStore.put(
            cacheKey = SnapshotCacheStore.Keys.RU_PRECOS,
            domain = SnapshotCacheStore.Domains.RU,
            origin = DataOrigin.REMOTE,
            syncedAtEpochMillis = clockMillis(),
            value = tabela,
            serializer = TabelaPrecosRuDto.serializer(),
            toDto = CacheMappers::toDto,
        )
        return CoreResult.Success(tabela to changed)
    }

    override fun observeNoticias(): Flow<SyncedData<List<NoticiaRu>>?> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.RU_NOTICIAS,
            serializer = CacheMappers.noticiaRuListSerializer,
            fromDto = { dtos -> dtos.map(CacheMappers::fromDto) },
        )

    override suspend fun refreshNoticias(perPage: Int): CoreResult<List<NoticiaRu>> =
        when (val res = refreshNoticiasWithDiff(perPage)) {
            is CoreResult.Failure -> res
            is CoreResult.Success -> CoreResult.Success(res.value.first)
        }

    private suspend fun refreshNoticiasWithDiff(
        perPage: Int,
    ): CoreResult<Pair<List<NoticiaRu>, Boolean>> {
        val payload = when (val res = transport.get(transport.endpoints.ruNoticiasUrl(perPage))) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }
        val parsed = RuWpParser.parseNoticias(payload.decodeUtf8())
        return when (parsed) {
            is CoreResult.Failure -> parsed
            is CoreResult.Success -> {
                val changed = cacheStore.put(
                    cacheKey = SnapshotCacheStore.Keys.RU_NOTICIAS,
                    domain = SnapshotCacheStore.Domains.RU,
                    origin = DataOrigin.REMOTE,
                    syncedAtEpochMillis = clockMillis(),
                    value = parsed.value,
                    serializer = CacheMappers.noticiaRuListSerializer,
                    toDto = { list -> list.map(CacheMappers::toDto) },
                )
                CoreResult.Success(parsed.value to changed)
            }
        }
    }

    override suspend fun downloadCardapioPdfLocal(campus: CampusRu): CoreResult<String> {
        val cachedPath = cacheStore.get(
            cacheKey = SnapshotCacheStore.Keys.cardapio(campus),
            serializer = CardapioSemanalRuDto.serializer(),
            fromDto = CacheMappers::fromDto,
        )?.value?.localPdfPath
        if (!cachedPath.isNullOrBlank() && java.io.File(cachedPath).exists()) {
            return CoreResult.Success(cachedPath)
        }
        return when (val refreshed = refreshCardapio(campus, force = true)) {
            is CoreResult.Failure -> refreshed
            is CoreResult.Success -> {
                val path = refreshed.value.localPdfPath
                if (path != null) {
                    CoreResult.Success(path)
                } else {
                    CoreResult.Failure(CoreError.StorageError("downloadCardapioPdfLocal", "PDF local indisponivel."))
                }
            }
        }
    }

    private suspend fun getLatestPricesOrDefault(): TabelaPrecosRu =
        cacheStore.get(
            cacheKey = SnapshotCacheStore.Keys.RU_PRECOS,
            serializer = TabelaPrecosRuDto.serializer(),
            fromDto = CacheMappers::fromDto,
        )?.value ?: RuCalculator.defaultPrecosResolucao0037

    private suspend fun findFallbackImageUrl(pdfUrl: String, campus: CampusRu): String? {
        val mediaRes = transport.get(transport.endpoints.ruMediaPdfsUrl)
        if (mediaRes !is CoreResult.Success) return null
        val mediaItems = (RuWpParser.parseMediaList(mediaRes.value.decodeUtf8()) as? CoreResult.Success)?.value
            ?: return null
        val exactMatch = mediaItems.firstOrNull { it.sourceUrl == pdfUrl }
            ?: mediaItems.firstOrNull {
                CasAndJsfParser.normalizeLabel(it.title)
                    .contains(CasAndJsfParser.normalizeLabel(campus.headingKeyword))
            }
        return exactMatch?.fallbackFullImageUrl
    }
}
