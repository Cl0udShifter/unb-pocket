package com.unbpocket.core.data.repository

import com.unbpocket.core.data.local.CacheMappers
import com.unbpocket.core.data.local.CardapioSemanalRuDto
import com.unbpocket.core.data.local.FileStore
import com.unbpocket.core.data.local.PdfExtractor
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
import com.unbpocket.core.model.ru.NoticiaRu
import com.unbpocket.core.model.ru.RuPreferencias
import com.unbpocket.core.model.ru.SaldoExtratoRu
import com.unbpocket.core.model.ru.TabelaPrecosRu
import com.unbpocket.core.network.UnbHttpTransport
import com.unbpocket.core.parser.CasAndJsfParser
import com.unbpocket.core.parser.DiscentePortalParser
import com.unbpocket.core.parser.RuCalculator
import com.unbpocket.core.parser.RuMenuPdfParser
import com.unbpocket.core.parser.RuWpParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
) : RuRepository {
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

    override fun observeCardapio(campus: CampusRu): Flow<SyncedData<CardapioSemanalRu>?> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.cardapio(campus),
            serializer = CardapioSemanalRuDto.serializer(),
            fromDto = CacheMappers::fromDto,
        )

    override suspend fun refreshCardapio(
        campus: CampusRu,
        force: Boolean,
    ): CoreResult<CardapioSemanalRu> {
        val pagePayload = when (val res = transport.get(transport.endpoints.ruCardapiosPageUrl)) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }

        val parsedPage = when (val parsed = RuWpParser.parseMenuPage(pagePayload.decodeUtf8())) {
            is CoreResult.Failure -> return parsed
            is CoreResult.Success -> parsed.value
        }

        val campusLink = parsedPage.campusLinks[campus]
            ?: return CoreResult.Failure(
                CoreError.ParseError("wp/v2/pages/12853", "Cardapio nao encontrado para ${campus.displayName}."),
            )

        val cached = cacheStore.get(
            cacheKey = SnapshotCacheStore.Keys.cardapio(campus),
            serializer = CardapioSemanalRuDto.serializer(),
            fromDto = CacheMappers::fromDto,
        )?.value

        if (!force &&
            cached != null &&
            cached.modifiedGmt == parsedPage.modifiedGmt &&
            cached.pdfUrl == campusLink.pdfUrl &&
            cached.parserStatus == CardapioParserStatus.PARSED_STRUCTURED
        ) {
            return CoreResult.Success(cached)
        }

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
                modifiedGmt = parsedPage.modifiedGmt,
                pages = pages.value,
            )
        }

        val now = clockMillis()
        return when (parsedMenuResult) {
            is CoreResult.Success -> {
                cacheStore.put(
                    cacheKey = SnapshotCacheStore.Keys.cardapio(campus),
                    domain = SnapshotCacheStore.Domains.RU,
                    origin = DataOrigin.REMOTE,
                    syncedAtEpochMillis = now,
                    value = parsedMenuResult.value,
                    serializer = CardapioSemanalRuDto.serializer(),
                    toDto = CacheMappers::toDto,
                )
                parsedMenuResult
            }
            is CoreResult.Failure -> {
                val fallback = CardapioSemanalRu(
                    campus = campus,
                    semanaLabel = campusLink.semanaLabel,
                    pdfUrl = campusLink.pdfUrl,
                    fallbackImageUrl = fallbackImageUrl,
                    localPdfPath = localPdfFile.absolutePath,
                    modifiedGmt = parsedPage.modifiedGmt,
                    parserStatus = if (fallbackImageUrl != null) {
                        CardapioParserStatus.FALLBACK_IMAGE_ONLY
                    } else {
                        CardapioParserStatus.FALLBACK_PDF_ONLY
                    },
                    dias = emptyList(),
                )
                cacheStore.put(
                    cacheKey = SnapshotCacheStore.Keys.cardapio(campus),
                    domain = SnapshotCacheStore.Domains.RU,
                    origin = DataOrigin.FALLBACK,
                    syncedAtEpochMillis = now,
                    value = fallback,
                    serializer = CardapioSemanalRuDto.serializer(),
                    toDto = CacheMappers::toDto,
                )
                CoreResult.Success(fallback)
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
            refreshSaldoExtratoFromHomeHtml(nav, homeHtml)
        }

    internal suspend fun refreshSaldoExtratoFromHomeHtml(
        nav: com.unbpocket.core.data.session.SigaaNavigator,
        homeHtml: String,
    ): CoreResult<SaldoExtratoRu> {
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
        if (parsed is CoreResult.Success) {
            cacheStore.put(
                cacheKey = SnapshotCacheStore.Keys.RU_SALDO,
                domain = SnapshotCacheStore.Domains.RU,
                origin = DataOrigin.REMOTE,
                syncedAtEpochMillis = nav.nowMillis(),
                value = parsed.value,
                serializer = SaldoExtratoRuDto.serializer(),
                toDto = CacheMappers::toDto,
            )
        }
        return parsed
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

    override suspend fun refreshTabelaPrecos(force: Boolean): CoreResult<TabelaPrecosRu> {
        val payload = when (val res = transport.get(transport.endpoints.ruPrecosPageUrl)) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }
        val tabela = RuWpParser.parsePricesPage(payload.decodeUtf8())
        cacheStore.put(
            cacheKey = SnapshotCacheStore.Keys.RU_PRECOS,
            domain = SnapshotCacheStore.Domains.RU,
            origin = DataOrigin.REMOTE,
            syncedAtEpochMillis = clockMillis(),
            value = tabela,
            serializer = TabelaPrecosRuDto.serializer(),
            toDto = CacheMappers::toDto,
        )
        return CoreResult.Success(tabela)
    }

    override fun observeNoticias(): Flow<SyncedData<List<NoticiaRu>>?> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.RU_NOTICIAS,
            serializer = CacheMappers.noticiaRuListSerializer,
            fromDto = { dtos -> dtos.map(CacheMappers::fromDto) },
        )

    override suspend fun refreshNoticias(perPage: Int): CoreResult<List<NoticiaRu>> {
        val payload = when (val res = transport.get(transport.endpoints.ruNoticiasUrl(perPage))) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }
        val parsed = RuWpParser.parseNoticias(payload.decodeUtf8())
        if (parsed is CoreResult.Success) {
            cacheStore.put(
                cacheKey = SnapshotCacheStore.Keys.RU_NOTICIAS,
                domain = SnapshotCacheStore.Domains.RU,
                origin = DataOrigin.REMOTE,
                syncedAtEpochMillis = clockMillis(),
                value = parsed.value,
                serializer = CacheMappers.noticiaRuListSerializer,
                toDto = { list -> list.map(CacheMappers::toDto) },
            )
        }
        return parsed
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
