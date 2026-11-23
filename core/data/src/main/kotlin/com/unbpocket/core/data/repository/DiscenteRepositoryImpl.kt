package com.unbpocket.core.data.repository

import com.unbpocket.core.data.local.CacheMappers
import com.unbpocket.core.data.local.CarteirinhaEstudantilDto
import com.unbpocket.core.data.local.DiscenteResumoDto
import com.unbpocket.core.data.local.FileStore
import com.unbpocket.core.data.local.PdfExtractor
import com.unbpocket.core.data.local.SnapshotCacheStore
import com.unbpocket.core.data.session.SigaaNavigator
import com.unbpocket.core.data.session.SigaaSessionCoordinator
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.DataOrigin
import com.unbpocket.core.model.common.SyncedData
import com.unbpocket.core.model.contract.DiscenteRepository
import com.unbpocket.core.model.discente.BoletimSemestre
import com.unbpocket.core.model.discente.CarteirinhaEstudantil
import com.unbpocket.core.model.discente.DiscenteResumo
import com.unbpocket.core.model.discente.DocumentoOficialEmitido
import com.unbpocket.core.model.discente.TipoDocumentoOficial
import com.unbpocket.core.parser.DiscenteDocumentosParser
import com.unbpocket.core.parser.DiscentePortalParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Repositorio da Tela do Discente: perfil, IRA/MP, integralizacao,
 * carteirinha QR Code (`img4`), boletim geral e emissao de documentos oficiais.
 */
class DiscenteRepositoryImpl(
    private val coordinator: SigaaSessionCoordinator,
    private val cacheStore: SnapshotCacheStore,
    private val fileStore: FileStore,
    private val pdfExtractor: PdfExtractor,
) : DiscenteRepository {
    override fun observeDiscenteResumo(): Flow<SyncedData<DiscenteResumo>?> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.DISCENTE_RESUMO,
            serializer = DiscenteResumoDto.serializer(),
            fromDto = CacheMappers::fromDto,
        )

    override suspend fun refreshDiscenteResumo(): CoreResult<DiscenteResumo> =
        coordinator.withAuthenticatedJsf { nav ->
            val homeHtml = when (val home = nav.getPortalHomeHtml()) {
                is CoreResult.Failure -> return@withAuthenticatedJsf home
                is CoreResult.Success -> home.value
            }
            persistResumoFromHomeHtml(nav, homeHtml)
        }

    internal suspend fun persistResumoFromHomeHtml(
        nav: SigaaNavigator,
        homeHtml: String,
    ): CoreResult<DiscenteResumo> {
        val parsed = DiscentePortalParser.parseDiscenteResumo(homeHtml)
        if (parsed is CoreResult.Success) {
            cacheStore.put(
                cacheKey = SnapshotCacheStore.Keys.DISCENTE_RESUMO,
                domain = SnapshotCacheStore.Domains.DISCENTE,
                origin = DataOrigin.REMOTE,
                syncedAtEpochMillis = nav.nowMillis(),
                value = parsed.value,
                serializer = DiscenteResumoDto.serializer(),
                toDto = CacheMappers::toDto,
            )
        }
        return parsed
    }

    override fun observeCarteirinha(): Flow<SyncedData<CarteirinhaEstudantil>?> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.DISCENTE_CARTEIRINHA,
            serializer = CarteirinhaEstudantilDto.serializer(),
            fromDto = CacheMappers::fromDto,
        )

    override suspend fun refreshCarteirinha(force: Boolean): CoreResult<CarteirinhaEstudantil> {
        if (!force) {
            val cached = cacheStore.get(
                cacheKey = SnapshotCacheStore.Keys.DISCENTE_CARTEIRINHA,
                serializer = CarteirinhaEstudantilDto.serializer(),
                fromDto = CacheMappers::fromDto,
            )?.value
            if (cached != null && File(cached.qrImageLocalPath).exists() && File(cached.pdfLocalPath).exists()) {
                return CoreResult.Success(cached)
            }
        }

        return coordinator.withAuthenticatedJsf { nav ->
            emitAndSaveCarteirinhaLocked(nav)
        }
    }

    override fun observeBoletimGeral(): Flow<SyncedData<List<BoletimSemestre>>?> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.DISCENTE_BOLETIM,
            serializer = CacheMappers.boletimListSerializer,
            fromDto = { dtos -> dtos.map(CacheMappers::fromDto) },
        )

    override suspend fun refreshBoletimGeral(): CoreResult<List<BoletimSemestre>> =
        coordinator.withAuthenticatedJsf { nav ->
            val payload = when (val res = nav.postMenuDiscenteAction(ACTION_RELATORIO_NOTAS)) {
                is CoreResult.Failure -> return@withAuthenticatedJsf res
                is CoreResult.Success -> res.value
            }

            val parsed = DiscenteDocumentosParser.parseBoletimGeral(payload.decodeSigaaIso88591())
            if (parsed is CoreResult.Success) {
                cacheStore.put(
                    cacheKey = SnapshotCacheStore.Keys.DISCENTE_BOLETIM,
                    domain = SnapshotCacheStore.Domains.DISCENTE,
                    origin = DataOrigin.REMOTE,
                    syncedAtEpochMillis = nav.nowMillis(),
                    value = parsed.value,
                    serializer = CacheMappers.boletimListSerializer,
                    toDto = { list -> list.map(CacheMappers::toDto) },
                )
            }
            parsed
        }

    override fun observeDocumentosEmitidos(): Flow<List<DocumentoOficialEmitido>> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.DISCENTE_DOCUMENTOS,
            serializer = CacheMappers.documentoListSerializer,
            fromDto = { dtos -> dtos.map(CacheMappers::fromDto) },
        ).map { it?.value.orEmpty() }

    override suspend fun emitirDocumentoOficial(
        tipo: TipoDocumentoOficial,
    ): CoreResult<DocumentoOficialEmitido> = coordinator.withAuthenticatedJsf { nav ->
        val emitted = when (tipo) {
            TipoDocumentoOficial.CARTEIRINHA_ESTUDANTIL -> {
                val carteirinha = when (val res = emitAndSaveCarteirinhaLocked(nav)) {
                    is CoreResult.Failure -> return@withAuthenticatedJsf res
                    is CoreResult.Success -> res.value
                }
                CoreResult.Success(
                    DocumentoOficialEmitido(
                        tipo = tipo,
                        titulo = tipo.displayName,
                        codigoVerificacao = carteirinha.qrPayload16Digitos,
                        mimeType = tipo.mimeType,
                        localFilePath = carteirinha.pdfLocalPath,
                        emitidoEmEpochMillis = nav.nowMillis(),
                    ),
                )
            }
            TipoDocumentoOficial.ATESTADO_MATRICULA ->
                emitTextOrPdfDocument(nav, tipo, ACTION_ATESTADO_MATRICULA, "atestado_matricula.html")
            TipoDocumentoOficial.DECLARACAO_VINCULO ->
                emitTextOrPdfDocument(nav, tipo, ACTION_DECLARACAO_VINCULO, "declaracao_vinculo.pdf")
            TipoDocumentoOficial.HISTORICO_ESCOLAR ->
                emitTextOrPdfDocument(nav, tipo, ACTION_HISTORICO_ESCOLAR, "historico_escolar.pdf")
            TipoDocumentoOficial.PASSE_LIVRE_ESTUDANTIL ->
                emitTextOrPdfDocument(nav, tipo, ACTION_PASSE_LIVRE, "passe_livre_estudantil.html")
        }

        val doc = when (emitted) {
            is CoreResult.Failure -> return@withAuthenticatedJsf emitted
            is CoreResult.Success -> emitted.value
        }

        val currentDocs = cacheStore.get(
            cacheKey = SnapshotCacheStore.Keys.DISCENTE_DOCUMENTOS,
            serializer = CacheMappers.documentoListSerializer,
            fromDto = { dtos -> dtos.map(CacheMappers::fromDto) },
        )?.value.orEmpty()

        val updatedDocs = currentDocs.filterNot { it.tipo == tipo } + doc
        cacheStore.put(
            cacheKey = SnapshotCacheStore.Keys.DISCENTE_DOCUMENTOS,
            domain = SnapshotCacheStore.Domains.DISCENTE,
            origin = DataOrigin.REMOTE,
            syncedAtEpochMillis = nav.nowMillis(),
            value = updatedDocs,
            serializer = CacheMappers.documentoListSerializer,
            toDto = { list -> list.map(CacheMappers::toDto) },
        )

        CoreResult.Success(doc)
    }

    private suspend fun emitAndSaveCarteirinhaLocked(
        nav: SigaaNavigator,
    ): CoreResult<CarteirinhaEstudantil> {
        val payload = when (val res = nav.postMenuDiscenteAction(ACTION_CARTEIRINHA)) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }

        if (!payload.isPdf()) {
            return CoreResult.Failure(
                CoreError.PdfExtractionError(
                    documentType = "Carteirinha Estudantil",
                    reason = "SIGAA nao retornou um PDF valido para a carteirinha.",
                ),
            )
        }

        val extracted = when (val res = pdfExtractor.extractCarteirinha(payload.bytes)) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }

        val parsed = when (val res = DiscenteDocumentosParser.parseCarteirinha(extracted)) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }

        val carteirinha = runCatching {
            val pdfFile = fileStore.saveBytes("carteirinha", "carteirinha.pdf", payload.bytes)
            val qrFile = fileStore.saveRgbAsPng("carteirinha", "qr_img4.png", parsed.qrImage)
            val fotoFile = parsed.foto3x4?.let { fileStore.saveRgbAsPng("carteirinha", "foto_3x4.png", it) }

            CarteirinhaEstudantil(
                nomeCompleto = parsed.nomeCompleto,
                matricula = parsed.matricula,
                cpfTexto = parsed.cpfTexto,
                curso = parsed.curso,
                nivel = parsed.nivel,
                validadeTexto = parsed.validadeTexto,
                qrPayload16Digitos = parsed.qrPayload16Digitos,
                qrImageLocalPath = qrFile.absolutePath,
                foto3x4LocalPath = fotoFile?.absolutePath,
                pdfLocalPath = pdfFile.absolutePath,
            )
        }.getOrElse { error ->
            return CoreResult.Failure(
                CoreError.StorageError("saveCarteirinha", error.message ?: "Falha ao gravar arquivos da carteirinha."),
            )
        }

        cacheStore.put(
            cacheKey = SnapshotCacheStore.Keys.DISCENTE_CARTEIRINHA,
            domain = SnapshotCacheStore.Domains.DISCENTE,
            origin = DataOrigin.REMOTE,
            syncedAtEpochMillis = nav.nowMillis(),
            value = carteirinha,
            serializer = CarteirinhaEstudantilDto.serializer(),
            toDto = CacheMappers::toDto,
        )
        return CoreResult.Success(carteirinha)
    }

    private suspend fun emitTextOrPdfDocument(
        nav: SigaaNavigator,
        tipo: TipoDocumentoOficial,
        jscookAction: String,
        defaultFileName: String,
    ): CoreResult<DocumentoOficialEmitido> {
        val payload = when (val res = nav.postMenuDiscenteAction(jscookAction)) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }

        if (tipo.mimeType == "application/pdf" && !payload.isPdf()) {
            return CoreResult.Failure(
                CoreError.PdfExtractionError(
                    documentType = tipo.displayName,
                    reason = "Resposta do SIGAA nao e um documento PDF valido.",
                ),
            )
        }

        val verificationCode = if (payload.isPdf()) {
            val linesRes = pdfExtractor.extractTextLines(payload.bytes, tipo.displayName)
            if (linesRes is CoreResult.Success) {
                DiscenteDocumentosParser.extractVerificationCode(linesRes.value.joinToString("\n"))
            } else {
                null
            }
        } else {
            DiscenteDocumentosParser.extractVerificationCode(payload.decodeSigaaIso88591())
        }

        val fileName = payload.extractAttachmentFileName(defaultFileName)
        return runCatching {
            val savedFile = fileStore.saveBytes("documentos", fileName, payload.bytes)
            DocumentoOficialEmitido(
                tipo = tipo,
                titulo = tipo.displayName,
                codigoVerificacao = verificationCode,
                mimeType = tipo.mimeType,
                localFilePath = savedFile.absolutePath,
                emitidoEmEpochMillis = nav.nowMillis(),
            )
        }.fold(
            onSuccess = { CoreResult.Success(it) },
            onFailure = { error ->
                CoreResult.Failure(
                    CoreError.StorageError("emitirDocumento", error.message ?: "Erro ao salvar documento."),
                )
            },
        )
    }

    private companion object {
        const val ACTION_RELATORIO_NOTAS =
            "menu_form_menu_discente_discente_menu:A]#{ relatorioNotasAluno.gerarRelatorio }"
        const val ACTION_CARTEIRINHA =
            "menu_form_menu_discente_discente_menu:A]#{ portalDiscente.emitiCarteiraEstudantilUnb }"
        const val ACTION_ATESTADO_MATRICULA =
            "menu_form_menu_discente_discente_menu:A]#{ portalDiscente.atestadoMatriculaUnb }"
        const val ACTION_DECLARACAO_VINCULO =
            "menu_form_menu_discente_discente_menu:A]#{ declaracaoVinculo.emitirDeclaracao}"
        const val ACTION_HISTORICO_ESCOLAR =
            "menu_form_menu_discente_discente_menu:A]#{ portalDiscente.historico }"
        const val ACTION_PASSE_LIVRE =
            "menu_form_menu_discente_discente_menu:A]#{ portalDiscente.passeEstudantilUnb }"
    }
}
