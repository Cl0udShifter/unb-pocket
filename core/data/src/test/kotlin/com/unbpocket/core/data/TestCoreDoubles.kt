package com.unbpocket.core.data

import com.unbpocket.core.data.local.CredentialVault
import com.unbpocket.core.data.local.FileStore
import com.unbpocket.core.data.local.PdfExtractor
import com.unbpocket.core.data.local.RuPrefsSource
import com.unbpocket.core.data.local.SnapshotDao
import com.unbpocket.core.data.local.SnapshotEntity
import com.unbpocket.core.data.security.StoredCredentials
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.RuPreferencias
import com.unbpocket.core.parser.CarteirinhaPdfExtractedInput
import com.unbpocket.core.parser.PdfMenuPageData
import com.unbpocket.core.parser.RgbImageBuffer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.File
import java.nio.file.Files

class InMemorySnapshotDao : SnapshotDao {
    private val rows = MutableStateFlow<Map<String, SnapshotEntity>>(emptyMap())
    @Volatile
    var upsertCount: Int = 0

    override fun observeByKey(cacheKey: String): Flow<SnapshotEntity?> =
        rows.map { it[cacheKey] }

    override suspend fun getByKey(cacheKey: String): SnapshotEntity? =
        rows.value[cacheKey]

    override suspend fun upsert(entity: SnapshotEntity) {
        upsertCount++
        rows.value = rows.value + (entity.cacheKey to entity)
    }

    override suspend fun clearDomain(domain: String) {
        rows.value = rows.value.filterValues { it.domain != domain }
    }

    override suspend fun clearKey(cacheKey: String) {
        rows.value = rows.value - cacheKey
    }

    override suspend fun clearAll() {
        rows.value = emptyMap()
    }
}

class InMemoryCredentialVault : CredentialVault {
    private var stored: StoredCredentials? = null

    override fun save(matricula: String, password: String) {
        stored = StoredCredentials(matricula, password)
    }

    override fun load(): StoredCredentials? = stored

    override fun exists(): Boolean = stored != null

    override fun clear() {
        stored = null
    }
}

class InMemoryRuPrefsSource : RuPrefsSource {
    private val state = MutableStateFlow(RuPreferencias())
    override val preferencias: Flow<RuPreferencias> = state

    override suspend fun setCampus(campus: CampusRu) {
        state.value = state.value.copy(campusSelecionado = campus)
    }

    override suspend fun setAlergenicos(alergenicos: Set<Alergenico>, ocultarPratos: Boolean) {
        state.value = state.value.copy(
            alergenicosRestritos = alergenicos,
            ocultarPratosComAlergenicos = ocultarPratos,
        )
    }
}

class TempFileStore : FileStore {
    val rootDir: File = Files.createTempDirectory("unb_pocket_test").toFile()

    override suspend fun saveBytes(subdir: String, fileName: String, bytes: ByteArray): File {
        val dir = File(rootDir, subdir).apply { mkdirs() }
        return File(dir, fileName).apply { writeBytes(bytes) }
    }

    override suspend fun saveRgbAsPng(subdir: String, fileName: String, image: RgbImageBuffer): File {
        val dir = File(rootDir, subdir).apply { mkdirs() }
        // Grava cabecalho PNG minimo + payload para testes JVM sem depender de android.graphics.Bitmap
        return File(dir, fileName).apply { writeBytes(image.rgbBytes) }
    }

    override suspend fun clearSigaaUserFiles() {
        rootDir.listFiles()?.forEach { entry ->
            if (com.unbpocket.core.data.local.DownloadedFileStore.isSigaaUserDir(entry.name)) {
                entry.deleteRecursively()
            }
        }
    }

    override suspend fun clearAll() {
        rootDir.deleteRecursively()
    }
}

class FakePdfExtractor(
    var carteirinhaResult: CoreResult<CarteirinhaPdfExtractedInput> = CoreResult.Failure(
        CoreError.PdfExtractionError("Carteirinha Estudantil", "Nao configurado"),
    ),
    var textLinesResult: CoreResult<List<String>> = CoreResult.Success(listOf("Código de Verificação: 5c49c94b22")),
    var ruPagesResult: CoreResult<List<PdfMenuPageData>> = CoreResult.Failure(
        CoreError.PdfExtractionError("Cardapio RU", "Falha simulada de PDF"),
    ),
) : PdfExtractor {
    override suspend fun extractCarteirinha(pdfBytes: ByteArray): CoreResult<CarteirinhaPdfExtractedInput> =
        carteirinhaResult

    override suspend fun extractTextLines(pdfBytes: ByteArray, documentType: String): CoreResult<List<String>> =
        textLinesResult

    override suspend fun extractRuMenuPages(pdfBytes: ByteArray): CoreResult<List<PdfMenuPageData>> =
        ruPagesResult
}
