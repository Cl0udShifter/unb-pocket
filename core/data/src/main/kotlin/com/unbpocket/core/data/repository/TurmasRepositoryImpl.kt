package com.unbpocket.core.data.repository

import com.unbpocket.core.data.local.CacheMappers
import com.unbpocket.core.data.local.FileStore
import com.unbpocket.core.data.local.MapaFrequenciaTurmaDto
import com.unbpocket.core.data.local.NotasParciaisTurmaDto
import com.unbpocket.core.data.local.NoticiaTurmaAvaDto
import com.unbpocket.core.data.local.SnapshotCacheStore
import com.unbpocket.core.data.local.TurmaDetalheAvaDto
import com.unbpocket.core.data.session.SigaaNavigator
import com.unbpocket.core.data.session.SigaaSessionCoordinator
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.DataOrigin
import com.unbpocket.core.model.common.SyncedData
import com.unbpocket.core.model.contract.TurmasRepository
import com.unbpocket.core.model.turmas.ArquivoBaixado
import com.unbpocket.core.model.turmas.AtualizacaoTurmaFeed
import com.unbpocket.core.model.turmas.AvaliacaoTurma
import com.unbpocket.core.model.turmas.MapaFrequenciaTurma
import com.unbpocket.core.model.turmas.MaterialAulaAva
import com.unbpocket.core.model.turmas.NotasParciaisTurma
import com.unbpocket.core.model.turmas.NoticiaTurmaAva
import com.unbpocket.core.model.turmas.TarefaTurma
import com.unbpocket.core.model.turmas.TurmaDetalheAva
import com.unbpocket.core.model.turmas.TurmaResumo
import com.unbpocket.core.network.JsfPriority
import com.unbpocket.core.parser.AtestadoTurmaItem
import com.unbpocket.core.parser.CasAndJsfParser
import com.unbpocket.core.parser.DiscentePortalParser
import com.unbpocket.core.parser.ParsedAvaFormTurmaItem
import com.unbpocket.core.parser.ParsedAvaHome
import com.unbpocket.core.parser.ParsedAvaNoticiaRef
import com.unbpocket.core.parser.TurmaVirtualParser
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

/**
 * Repositorio da Tela de Turmas: lista do semestre, feed de atualizacoes,
 * enriquecimento via atestado/AVA, frequencia, notas parciais, avaliacoes, tarefas e downloads.
 */
class TurmasRepositoryImpl(
    private val coordinator: SigaaSessionCoordinator,
    private val cacheStore: SnapshotCacheStore,
    private val fileStore: FileStore,
) : TurmasRepository {
    private val runtimeAvaCache = ConcurrentHashMap<String, ParsedAvaHome>()
    @Volatile
    private var currentAvaHomeTurmaId: String? = null
    @Volatile
    private var currentAvaMenuTurmaId: String? = null
    @Volatile
    private var currentAvaMenuActions: Map<String, Map<String, String>> = emptyMap()

    internal fun clearRuntimeState() {
        runtimeAvaCache.clear()
        currentAvaHomeTurmaId = null
        currentAvaMenuTurmaId = null
        currentAvaMenuActions = emptyMap()
    }

    override fun observeTurmasSemestre(): Flow<SyncedData<List<TurmaResumo>>?> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.TURMAS_LIST,
            serializer = CacheMappers.turmaResumoListSerializer,
            fromDto = { dtos -> dtos.map(CacheMappers::fromDto) },
        )

    override fun observeAtualizacoesFeed(): Flow<SyncedData<List<AtualizacaoTurmaFeed>>?> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.TURMAS_FEED,
            serializer = CacheMappers.feedListSerializer,
            fromDto = { dtos -> dtos.map(CacheMappers::fromDto) },
        )

    override suspend fun refreshTurmasSemestre(enriquecerViaAtestado: Boolean): CoreResult<List<TurmaResumo>> =
        coordinator.withAuthenticatedJsf { nav ->
            val homeHtml = when (val home = nav.getPortalHomeHtml()) {
                is CoreResult.Failure -> return@withAuthenticatedJsf home
                is CoreResult.Success -> home.value
            }
            when (
                val res = persistHomeTurmasAndFeedWithDiff(
                    nav = nav,
                    homeHtml = homeHtml,
                    enriquecerViaAtestado = enriquecerViaAtestado,
                    reuseHomeHtmlForAtestado = true,
                )
            ) {
                is CoreResult.Failure -> res
                is CoreResult.Success -> CoreResult.Success(res.value.first)
            }
        }

    internal suspend fun getCachedTurmasSnapshot(): List<TurmaResumo> =
        cacheStore.get(
            cacheKey = SnapshotCacheStore.Keys.TURMAS_LIST,
            serializer = CacheMappers.turmaResumoListSerializer,
            fromDto = { dtos -> dtos.map(CacheMappers::fromDto) },
        )?.value.orEmpty()

    internal suspend fun persistHomeTurmasAndFeedWithDiff(
        nav: SigaaNavigator,
        homeHtml: String,
        enriquecerViaAtestado: Boolean,
        reuseHomeHtmlForAtestado: Boolean = false,
    ): CoreResult<Pair<List<TurmaResumo>, Boolean>> {
        val now = nav.nowMillis()
        val feed = DiscentePortalParser.parseAtualizacoesFeed(homeHtml)
        val feedChanged = cacheStore.put(
            cacheKey = SnapshotCacheStore.Keys.TURMAS_FEED,
            domain = SnapshotCacheStore.Domains.TURMAS,
            origin = DataOrigin.REMOTE,
            syncedAtEpochMillis = now,
            value = feed,
            serializer = CacheMappers.feedListSerializer,
            toDto = { list -> list.map(CacheMappers::toDto) },
        )

        val homeTurmas = DiscentePortalParser.parseTurmasDaHome(homeHtml).map { it.resumo }
        val existingByFrontEndId = getCachedTurmasSnapshot().associateBy { it.frontEndIdTurma }
        val needsAtestadoEnrichment = enriquecerViaAtestado && homeTurmas.any { turma ->
            val prev = existingByFrontEndId[turma.frontEndIdTurma]
            prev == null || prev.codigoDisciplina.isNullOrBlank() || prev.docenteNome.isNullOrBlank()
        }

        val atestadoItems: List<AtestadoTurmaItem> = if (needsAtestadoEnrichment) {
            when (
                val atestadoRes = nav.postMenuDiscenteAction(
                    jscookAction = ACTION_ATESTADO_MATRICULA,
                    existingHomeHtml = if (reuseHomeHtmlForAtestado) homeHtml else null,
                )
            ) {
                is CoreResult.Failure -> {
                    if (atestadoRes.error is CoreError.SessionExpired) return atestadoRes
                    emptyList()
                }
                is CoreResult.Success -> {
                    TurmaVirtualParser.parseAtestadoMatricula(atestadoRes.value.decodeSigaaIso88591()).turmas
                }
            }
        } else {
            emptyList()
        }

        val merged = homeTurmas.map { turma ->
            val prev = existingByFrontEndId[turma.frontEndIdTurma]
            val match = findAtestadoMatch(turma, atestadoItems)
            turma.copy(
                codigoDisciplina = match?.codigoDisciplina ?: prev?.codigoDisciplina,
                numeroTurma = match?.numeroTurma ?: prev?.numeroTurma,
                cargaHorariaHoras = prev?.cargaHorariaHoras,
                docenteNome = match?.docenteNome ?: prev?.docenteNome,
            )
        }

        val turmasChanged = cacheStore.put(
            cacheKey = SnapshotCacheStore.Keys.TURMAS_LIST,
            domain = SnapshotCacheStore.Domains.TURMAS,
            origin = DataOrigin.REMOTE,
            syncedAtEpochMillis = now,
            value = merged,
            serializer = CacheMappers.turmaResumoListSerializer,
            toDto = { list -> list.map(CacheMappers::toDto) },
        )

        return CoreResult.Success(merged to (feedChanged || turmasChanged))
    }

    override fun observeTurmaDetalhe(frontEndIdTurma: String): Flow<SyncedData<TurmaDetalheAva>?> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.turmaDetalhe(frontEndIdTurma),
            serializer = TurmaDetalheAvaDto.serializer(),
            fromDto = CacheMappers::fromDto,
        )

    override suspend fun refreshTurmaDetalhe(frontEndIdTurma: String): CoreResult<TurmaDetalheAva> =
        when (val res = refreshTurmaDetalheWithDiff(frontEndIdTurma, JsfPriority.INTERACTIVE)) {
            is CoreResult.Failure -> res
            is CoreResult.Success -> CoreResult.Success(res.value.first)
        }

    internal suspend fun refreshTurmaDetalheWithDiff(
        frontEndIdTurma: String,
        priority: JsfPriority,
    ): CoreResult<Pair<TurmaDetalheAva, Boolean>> =
        coordinator.withAuthenticatedJsf(priority = priority) { nav ->
            when (val entered = enterAvaHomeLocked(nav, frontEndIdTurma)) {
                is CoreResult.Failure -> entered
                is CoreResult.Success -> CoreResult.Success(entered.value.first.detalhe to entered.value.second)
            }
        }

    override suspend fun getNoticiaCompleta(
        frontEndIdTurma: String,
        idNoticia: String,
    ): CoreResult<NoticiaTurmaAva> {
        val cached = cacheStore.get(
            cacheKey = SnapshotCacheStore.Keys.turmaNoticia(frontEndIdTurma, idNoticia),
            serializer = NoticiaTurmaAvaDto.serializer(),
            fromDto = CacheMappers::fromDto,
        )?.value
        if (cached != null) {
            val hasAllAnexoRefs = cached.anexos.isEmpty() ||
                runtimeAvaCache[frontEndIdTurma]?.anexoRefs?.keys?.containsAll(cached.anexos.map { it.id }) == true
            if (hasAllAnexoRefs) {
                return CoreResult.Success(cached)
            }
        }

        val fetched = coordinator.withAuthenticatedJsf { nav ->
            val directRef = if (currentAvaHomeTurmaId == frontEndIdTurma) {
                runtimeAvaCache[frontEndIdTurma]?.noticiaRefs?.get(idNoticia)
            } else {
                null
            }

            if (directRef != null) {
                currentAvaHomeTurmaId = null
                when (val directRes = fetchAndCacheNoticia(nav, frontEndIdTurma, idNoticia, directRef)) {
                    is CoreResult.Success -> return@withAuthenticatedJsf directRes
                    is CoreResult.Failure -> {
                        if (directRes.error is CoreError.SessionExpired ||
                            directRes.error is CoreError.NetworkUnavailable
                        ) {
                            return@withAuthenticatedJsf directRes
                        }
                    }
                }
            }

            val (avaHome, _) = when (val entered = enterAvaHomeLocked(nav, frontEndIdTurma)) {
                is CoreResult.Failure -> return@withAuthenticatedJsf entered
                is CoreResult.Success -> entered.value
            }

            val ref = avaHome.noticiaRefs[idNoticia]
                ?: return@withAuthenticatedJsf CoreResult.Failure(
                    CoreError.UnavailableForProfile("Noticia AVA", "Noticia $idNoticia nao encontrada na turma."),
                )

            currentAvaHomeTurmaId = null
            fetchAndCacheNoticia(nav, frontEndIdTurma, idNoticia, ref)
        }

        return when (fetched) {
            is CoreResult.Success -> fetched
            is CoreResult.Failure -> if (cached != null && fetched.error is CoreError.NetworkUnavailable) {
                CoreResult.Success(cached)
            } else {
                fetched
            }
        }
    }

    private suspend fun fetchAndCacheNoticia(
        nav: SigaaNavigator,
        frontEndIdTurma: String,
        idNoticia: String,
        ref: ParsedAvaNoticiaRef,
    ): CoreResult<NoticiaTurmaAva> {
        val payload = when (val res = nav.postAvaForm(ref.formParams)) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }

        val parsed = when (val p = TurmaVirtualParser.parseAvaNoticiaCompleta(payload.decodeSigaaIso88591(), idNoticia)) {
            is CoreResult.Failure -> return p
            is CoreResult.Success -> p.value
        }

        if (parsed.anexoRefs.isNotEmpty()) {
            val prev = runtimeAvaCache[frontEndIdTurma]
            if (prev != null) {
                runtimeAvaCache[frontEndIdTurma] = prev.copy(
                    anexoRefs = prev.anexoRefs + parsed.anexoRefs,
                )
            }
        }

        cacheStore.put(
            cacheKey = SnapshotCacheStore.Keys.turmaNoticia(frontEndIdTurma, idNoticia),
            domain = SnapshotCacheStore.Domains.TURMAS,
            origin = DataOrigin.REMOTE,
            syncedAtEpochMillis = nav.nowMillis(),
            value = parsed.noticia,
            serializer = NoticiaTurmaAvaDto.serializer(),
            toDto = CacheMappers::toDto,
        )
        return CoreResult.Success(parsed.noticia)
    }

    override fun observeFrequencia(frontEndIdTurma: String): Flow<SyncedData<MapaFrequenciaTurma>?> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.turmaFrequencia(frontEndIdTurma),
            serializer = MapaFrequenciaTurmaDto.serializer(),
            fromDto = CacheMappers::fromDto,
        )

    override suspend fun refreshFrequencia(frontEndIdTurma: String): CoreResult<MapaFrequenciaTurma> =
        when (val res = refreshFrequenciaWithDiff(frontEndIdTurma, JsfPriority.INTERACTIVE)) {
            is CoreResult.Failure -> res
            is CoreResult.Success -> CoreResult.Success(res.value.first)
        }

    internal suspend fun refreshFrequenciaWithDiff(
        frontEndIdTurma: String,
        priority: JsfPriority,
    ): CoreResult<Pair<MapaFrequenciaTurma, Boolean>> =
        fetchAndCacheAvaMenuWithDiff(
            frontEndIdTurma = frontEndIdTurma,
            menuLabel = "frequencia",
            cacheKey = SnapshotCacheStore.Keys.turmaFrequencia(frontEndIdTurma),
            serializer = MapaFrequenciaTurmaDto.serializer(),
            toDto = CacheMappers::toDto,
            priority = priority,
            parser = TurmaVirtualParser::parseAvaFrequencia,
        )

    override fun observeNotasParciais(frontEndIdTurma: String): Flow<SyncedData<NotasParciaisTurma>?> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.turmaNotas(frontEndIdTurma),
            serializer = NotasParciaisTurmaDto.serializer(),
            fromDto = CacheMappers::fromDto,
        )

    override suspend fun refreshNotasParciais(frontEndIdTurma: String): CoreResult<NotasParciaisTurma> =
        when (val res = refreshNotasParciaisWithDiff(frontEndIdTurma, JsfPriority.INTERACTIVE)) {
            is CoreResult.Failure -> res
            is CoreResult.Success -> CoreResult.Success(res.value.first)
        }

    internal suspend fun refreshNotasParciaisWithDiff(
        frontEndIdTurma: String,
        priority: JsfPriority,
    ): CoreResult<Pair<NotasParciaisTurma, Boolean>> =
        fetchAndCacheAvaMenuWithDiff(
            frontEndIdTurma = frontEndIdTurma,
            menuLabel = "ver notas",
            cacheKey = SnapshotCacheStore.Keys.turmaNotas(frontEndIdTurma),
            serializer = NotasParciaisTurmaDto.serializer(),
            toDto = CacheMappers::toDto,
            priority = priority,
            parser = TurmaVirtualParser::parseAvaNotasParciais,
        )

    override fun observeAvaliacoes(frontEndIdTurma: String): Flow<SyncedData<List<AvaliacaoTurma>>?> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.turmaAvaliacoes(frontEndIdTurma),
            serializer = CacheMappers.avaliacaoListSerializer,
            fromDto = { dtos -> dtos.map(CacheMappers::fromDto) },
        )

    override suspend fun refreshAvaliacoes(frontEndIdTurma: String): CoreResult<List<AvaliacaoTurma>> =
        when (val res = refreshAvaliacoesWithDiff(frontEndIdTurma, JsfPriority.INTERACTIVE)) {
            is CoreResult.Failure -> res
            is CoreResult.Success -> CoreResult.Success(res.value.first)
        }

    internal suspend fun refreshAvaliacoesWithDiff(
        frontEndIdTurma: String,
        priority: JsfPriority,
    ): CoreResult<Pair<List<AvaliacaoTurma>, Boolean>> =
        fetchAndCacheAvaMenuWithDiff(
            frontEndIdTurma = frontEndIdTurma,
            menuLabel = "avaliacoes",
            cacheKey = SnapshotCacheStore.Keys.turmaAvaliacoes(frontEndIdTurma),
            serializer = CacheMappers.avaliacaoListSerializer,
            toDto = { list -> list.map(CacheMappers::toDto) },
            priority = priority,
            parser = TurmaVirtualParser::parseAvaAvaliacoes,
        )

    override fun observeTarefas(frontEndIdTurma: String): Flow<SyncedData<List<TarefaTurma>>?> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.turmaTarefas(frontEndIdTurma),
            serializer = CacheMappers.tarefaListSerializer,
            fromDto = { dtos -> dtos.map(CacheMappers::fromDto) },
        )

    override suspend fun refreshTarefas(frontEndIdTurma: String): CoreResult<List<TarefaTurma>> =
        when (val res = refreshTarefasWithDiff(frontEndIdTurma, JsfPriority.INTERACTIVE)) {
            is CoreResult.Failure -> res
            is CoreResult.Success -> CoreResult.Success(res.value.first)
        }

    internal suspend fun refreshTarefasWithDiff(
        frontEndIdTurma: String,
        priority: JsfPriority,
    ): CoreResult<Pair<List<TarefaTurma>, Boolean>> =
        fetchAndCacheAvaMenuWithDiff(
            frontEndIdTurma = frontEndIdTurma,
            menuLabel = "tarefas",
            cacheKey = SnapshotCacheStore.Keys.turmaTarefas(frontEndIdTurma),
            serializer = CacheMappers.tarefaListSerializer,
            toDto = { list -> list.map(CacheMappers::toDto) },
            priority = priority,
            parser = TurmaVirtualParser::parseAvaTarefas,
        )

    override suspend fun downloadMaterial(
        frontEndIdTurma: String,
        materialId: String,
    ): CoreResult<ArquivoBaixado> = coordinator.withAuthenticatedJsf { nav ->
        val directRef = if (currentAvaHomeTurmaId == frontEndIdTurma) {
            runtimeAvaCache[frontEndIdTurma]?.materialRefs?.get(materialId)
        } else {
            null
        }

        if (directRef != null) {
            when (val directRes = nav.postAvaForm(directRef.formParams)) {
                is CoreResult.Success -> {
                    if (!isUnexpectedHtmlAttachment(directRes.value, directRef.material.tipoExtensao)) {
                        return@withAuthenticatedJsf saveMaterialPayload(
                            frontEndIdTurma = frontEndIdTurma,
                            material = directRef.material,
                            payload = directRes.value,
                        )
                    }
                }

                is CoreResult.Failure -> {
                    if (directRes.error is CoreError.SessionExpired ||
                        directRes.error is CoreError.NetworkUnavailable
                    ) {
                        currentAvaHomeTurmaId = null
                        return@withAuthenticatedJsf directRes
                    }
                }
            }
            currentAvaHomeTurmaId = null
        }

        val (avaHome, _) = when (val entered = enterAvaHomeLocked(nav, frontEndIdTurma)) {
            is CoreResult.Failure -> return@withAuthenticatedJsf entered
            is CoreResult.Success -> entered.value
        }

        val ref = avaHome.materialRefs[materialId]
            ?: return@withAuthenticatedJsf CoreResult.Failure(
                CoreError.UnavailableForProfile("Material AVA", "Material $materialId nao encontrado na turma."),
            )

        val payload = when (val res = nav.postAvaForm(ref.formParams)) {
            is CoreResult.Failure -> return@withAuthenticatedJsf res
            is CoreResult.Success -> res.value
        }

        if (isUnexpectedHtmlAttachment(payload, ref.material.tipoExtensao)) {
            currentAvaHomeTurmaId = null
            return@withAuthenticatedJsf CoreResult.Failure(
                CoreError.ParseError(
                    source = "ava/material",
                    reason = "O SIGAA nao retornou o arquivo solicitado para ${ref.material.nome}.",
                ),
            )
        }

        saveMaterialPayload(
            frontEndIdTurma = frontEndIdTurma,
            material = ref.material,
            payload = payload,
        )
    }

    override suspend fun downloadAnexoNoticia(
        frontEndIdTurma: String,
        anexoId: String,
    ): CoreResult<ArquivoBaixado> = coordinator.withAuthenticatedJsf { nav ->
        var ref = runtimeAvaCache[frontEndIdTurma]?.anexoRefs?.get(anexoId)
        if (ref == null) {
            val entered = enterAvaHomeLocked(nav, frontEndIdTurma)
            if (entered is CoreResult.Failure) return@withAuthenticatedJsf entered
            ref = (entered as CoreResult.Success).value.first.anexoRefs[anexoId]
        }

        val resolvedRef = ref ?: return@withAuthenticatedJsf CoreResult.Failure(
            CoreError.UnavailableForProfile("Anexo AVA", "Anexo $anexoId nao encontrado."),
        )

        val downloadUrl = resolvedRef.downloadUrl
        val formParams = resolvedRef.formParams
        val payloadResult = when {
            downloadUrl != null -> nav.getSigaaBinary(downloadUrl)
            formParams != null -> nav.postAvaForm(formParams)
            else -> CoreResult.Failure(CoreError.ParseError("ava/anexo", "Anexo sem URL ou formulario."))
        }

        val payload = when (payloadResult) {
            is CoreResult.Failure -> return@withAuthenticatedJsf payloadResult
            is CoreResult.Success -> payloadResult.value
        }

        saveDownloadedPayload(
            frontEndIdTurma = frontEndIdTurma,
            fallbackName = resolvedRef.anexo.nomeArquivo,
            payload = payload,
        )
    }

    private suspend fun <T, D> fetchAndCacheAvaMenuWithDiff(
        frontEndIdTurma: String,
        menuLabel: String,
        cacheKey: String,
        serializer: kotlinx.serialization.KSerializer<D>,
        toDto: (T) -> D,
        priority: JsfPriority,
        parser: (String) -> CoreResult<T>,
    ): CoreResult<Pair<T, Boolean>> = coordinator.withAuthenticatedJsf(priority = priority) { nav ->
        val directMenuParams = if (currentAvaMenuTurmaId == frontEndIdTurma) {
            currentAvaMenuActions[menuLabel]
        } else {
            null
        }

        if (directMenuParams != null) {
            currentAvaHomeTurmaId = null
            when (val directRes = nav.postAvaForm(directMenuParams)) {
                is CoreResult.Failure -> {
                    currentAvaMenuTurmaId = null
                    currentAvaMenuActions = emptyMap()
                    if (directRes.error is CoreError.SessionExpired ||
                        directRes.error is CoreError.NetworkUnavailable
                    ) {
                        return@withAuthenticatedJsf directRes
                    }
                }
                is CoreResult.Success -> {
                    val directHtml = directRes.value.decodeSigaaIso88591()
                    val directParsed = parser(directHtml)
                    if (directParsed is CoreResult.Success) {
                        updateActiveAvaMenuState(frontEndIdTurma, directHtml)
                        val menuChanged = cacheStore.put(
                            cacheKey = cacheKey,
                            domain = SnapshotCacheStore.Domains.TURMAS,
                            origin = DataOrigin.REMOTE,
                            syncedAtEpochMillis = nav.nowMillis(),
                            value = directParsed.value,
                            serializer = serializer,
                            toDto = toDto,
                        )
                        return@withAuthenticatedJsf CoreResult.Success(directParsed.value to menuChanged)
                    }
                    currentAvaMenuTurmaId = null
                    currentAvaMenuActions = emptyMap()
                }
            }
        }

        val (avaHome, detailChanged) = when (val entered = enterAvaHomeLocked(nav, frontEndIdTurma)) {
            is CoreResult.Failure -> return@withAuthenticatedJsf entered
            is CoreResult.Success -> entered.value
        }

        val menuParams = avaHome.menuActions[menuLabel]
            ?: return@withAuthenticatedJsf CoreResult.Failure(
                CoreError.UnavailableForProfile("Menu AVA", "Opcao '$menuLabel' indisponivel nesta turma."),
            )

        currentAvaHomeTurmaId = null
        val menuPayload = when (val res = nav.postAvaForm(menuParams)) {
            is CoreResult.Failure -> {
                currentAvaMenuTurmaId = null
                currentAvaMenuActions = emptyMap()
                return@withAuthenticatedJsf res
            }
            is CoreResult.Success -> res.value
        }

        val menuHtml = menuPayload.decodeSigaaIso88591()
        updateActiveAvaMenuState(frontEndIdTurma, menuHtml)

        when (val parsed = parser(menuHtml)) {
            is CoreResult.Failure -> parsed
            is CoreResult.Success -> {
                val menuChanged = cacheStore.put(
                    cacheKey = cacheKey,
                    domain = SnapshotCacheStore.Domains.TURMAS,
                    origin = DataOrigin.REMOTE,
                    syncedAtEpochMillis = nav.nowMillis(),
                    value = parsed.value,
                    serializer = serializer,
                    toDto = toDto,
                )
                CoreResult.Success(parsed.value to (detailChanged || menuChanged))
            }
        }
    }

    private fun updateActiveAvaMenuState(frontEndIdTurma: String, avaHtml: String) {
        val nextActions = TurmaVirtualParser.parseAvaMenuActions(avaHtml)
        if (nextActions.isNotEmpty()) {
            currentAvaMenuTurmaId = frontEndIdTurma
            currentAvaMenuActions = nextActions
        } else {
            currentAvaMenuTurmaId = null
            currentAvaMenuActions = emptyMap()
        }
    }

    private suspend fun enterAvaHomeLocked(
        nav: SigaaNavigator,
        frontEndIdTurma: String,
    ): CoreResult<Pair<ParsedAvaHome, Boolean>> {
        val homeHtml = when (val res = nav.getPortalHomeHtml()) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }

        val targetTurma = DiscentePortalParser.parseTurmasDaHome(homeHtml)
            .firstOrNull { it.resumo.frontEndIdTurma == frontEndIdTurma }
            ?: return CoreResult.Failure(
                CoreError.UnavailableForProfile(
                    feature = "Turma Virtual",
                    reason = "Turma $frontEndIdTurma nao encontrada no portal atual.",
                ),
            )

        val avaPayload = when (val res = nav.postDiscenteForm(targetTurma.formParams)) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }

        val parsedAva = when (val parsed = TurmaVirtualParser.parseAvaHome(avaPayload.decodeSigaaIso88591(), frontEndIdTurma)) {
            is CoreResult.Failure -> return parsed
            is CoreResult.Success -> parsed.value
        }

        runtimeAvaCache[frontEndIdTurma] = parsedAva
        currentAvaHomeTurmaId = frontEndIdTurma
        currentAvaMenuTurmaId = frontEndIdTurma
        currentAvaMenuActions = parsedAva.menuActions
        val now = nav.nowMillis()
        val detailChanged = cacheStore.put(
            cacheKey = SnapshotCacheStore.Keys.turmaDetalhe(frontEndIdTurma),
            domain = SnapshotCacheStore.Domains.TURMAS,
            origin = DataOrigin.REMOTE,
            syncedAtEpochMillis = now,
            value = parsedAva.detalhe,
            serializer = TurmaDetalheAvaDto.serializer(),
            toDto = CacheMappers::toDto,
        )

        val listEnriched = enrichTurmasListFromFormTurma(parsedAva.formTurmaItems, now)
        return CoreResult.Success(parsedAva to (detailChanged || listEnriched))
    }

    private suspend fun enrichTurmasListFromFormTurma(
        formItems: List<ParsedAvaFormTurmaItem>,
        now: Long,
    ): Boolean {
        if (formItems.isEmpty()) return false
        val existing = cacheStore.get(
            cacheKey = SnapshotCacheStore.Keys.TURMAS_LIST,
            serializer = CacheMappers.turmaResumoListSerializer,
            fromDto = { dtos -> dtos.map(CacheMappers::fromDto) },
        )?.value ?: return false

        val byFrontEndId = formItems.associateBy { it.frontEndIdTurma }
        val updated = existing.map { turma ->
            val item = byFrontEndId[turma.frontEndIdTurma] ?: return@map turma
            turma.copy(
                codigoDisciplina = turma.codigoDisciplina ?: item.codigoDisciplina,
                cargaHorariaHoras = item.cargaHorariaHoras ?: turma.cargaHorariaHoras,
            )
        }
        return cacheStore.put(
            cacheKey = SnapshotCacheStore.Keys.TURMAS_LIST,
            domain = SnapshotCacheStore.Domains.TURMAS,
            origin = DataOrigin.REMOTE,
            syncedAtEpochMillis = now,
            value = updated,
            serializer = CacheMappers.turmaResumoListSerializer,
            toDto = { list -> list.map(CacheMappers::toDto) },
        )
    }

    private suspend fun saveMaterialPayload(
        frontEndIdTurma: String,
        material: MaterialAulaAva,
        payload: com.unbpocket.core.network.HttpPayload,
    ): CoreResult<ArquivoBaixado> {
        val cleanExt = material.tipoExtensao
            .trim()
            .lowercase()
            .takeIf {
                it.isNotEmpty() &&
                    it !in setOf("indicator", "file", "spacer", "loading", "spinner", "ajax", "blank")
            }
            ?: if (payload.isPdf()) "pdf" else null
        val fallbackName = if (cleanExt != null && !material.nome.endsWith(".$cleanExt", ignoreCase = true)) {
            "${material.nome}.$cleanExt"
        } else {
            material.nome
        }
        return saveDownloadedPayload(
            frontEndIdTurma = frontEndIdTurma,
            fallbackName = fallbackName,
            payload = payload,
        )
    }

    private fun isUnexpectedHtmlAttachment(
        payload: com.unbpocket.core.network.HttpPayload,
        expectedExt: String,
    ): Boolean {
        if (expectedExt.lowercase() in setOf("html", "htm")) return false
        val isHtml = payload.contentType?.contains("text/html", ignoreCase = true) == true
        if (!isHtml) return false
        val disposition = payload.contentDisposition.orEmpty()
        return !disposition.contains("attachment", ignoreCase = true) &&
            !disposition.contains("filename", ignoreCase = true)
    }

    private suspend fun saveDownloadedPayload(
        frontEndIdTurma: String,
        fallbackName: String,
        payload: com.unbpocket.core.network.HttpPayload,
    ): CoreResult<ArquivoBaixado> {
        val rawName = payload.extractAttachmentFileName(fallbackName)
        val fileName = if (!rawName.contains('.') && payload.isPdf()) "$rawName.pdf" else rawName
        val mime = resolveMimeType(
            headerContentType = payload.contentType,
            fileName = fileName,
            isPdf = payload.isPdf(),
        )

        return runCatching {
            val file = fileStore.saveBytes("materiais/$frontEndIdTurma", fileName, payload.bytes)
            val publicInfo = fileStore.saveToPublicDownloads(fileName, mime, payload.bytes)
            ArquivoBaixado(
                nomeArquivo = file.name,
                mimeType = mime,
                localFilePath = file.absolutePath,
                tamanhoBytes = file.length(),
                downloadPublicoInfo = publicInfo,
            )
        }.fold(
            onSuccess = { CoreResult.Success(it) },
            onFailure = { err ->
                CoreResult.Failure(
                    CoreError.StorageError("downloadArquivo", err.message ?: "Erro ao salvar arquivo."),
                )
            },
        )
    }

    private fun resolveMimeType(
        headerContentType: String?,
        fileName: String,
        isPdf: Boolean,
    ): String {
        val rawHeader = headerContentType?.substringBefore(';')?.trim().orEmpty()
        if (rawHeader.isNotBlank() &&
            !rawHeader.equals("application/octet-stream", ignoreCase = true) &&
            !rawHeader.equals("application/force-download", ignoreCase = true)
        ) {
            return rawHeader
        }
        if (isPdf) return "application/pdf"
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "7z" -> "application/x-7z-compressed"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "mp4" -> "video/mp4"
            else -> rawHeader.ifBlank { "application/octet-stream" }
        }
    }

    private fun findAtestadoMatch(
        turma: TurmaResumo,
        atestadoItems: List<AtestadoTurmaItem>,
    ): AtestadoTurmaItem? {
        val normName = CasAndJsfParser.normalizeLabel(turma.nomeDisciplina)
        val rawCode = turma.codigoHorarioRaw.substringBefore('(').trim()
        return atestadoItems.firstOrNull { item ->
            CasAndJsfParser.normalizeLabel(item.nomeDisciplina) == normName &&
                (rawCode.isEmpty() || item.horarioRaw.contains(rawCode, ignoreCase = true))
        } ?: atestadoItems.firstOrNull {
            CasAndJsfParser.normalizeLabel(it.nomeDisciplina) == normName
        }
    }

    private companion object {
        const val ACTION_ATESTADO_MATRICULA =
            "menu_form_menu_discente_discente_menu:A]#{ portalDiscente.atestadoMatriculaUnb }"
    }
}
