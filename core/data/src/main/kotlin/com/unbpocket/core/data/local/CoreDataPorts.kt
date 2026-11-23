package com.unbpocket.core.data.local

import com.unbpocket.core.data.security.StoredCredentials
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.RuPreferencias
import com.unbpocket.core.parser.CarteirinhaPdfExtractedInput
import com.unbpocket.core.parser.PdfMenuPageData
import com.unbpocket.core.parser.RgbImageBuffer
import kotlinx.coroutines.flow.Flow
import java.io.File

interface CredentialVault {
    fun save(matricula: String, password: String)
    fun load(): StoredCredentials?
    fun exists(): Boolean
    fun clear()
}

interface RuPrefsSource {
    val preferencias: Flow<RuPreferencias>
    suspend fun setCampus(campus: CampusRu)
    suspend fun setAlergenicos(alergenicos: Set<Alergenico>, ocultarPratos: Boolean)
    suspend fun markNoticiasVistas(ids: Set<Long>)
}

interface FileStore {
    suspend fun saveBytes(subdir: String, fileName: String, bytes: ByteArray): File
    suspend fun saveToPublicDownloads(fileName: String, mimeType: String, bytes: ByteArray): String? = null
    suspend fun saveRgbAsPng(subdir: String, fileName: String, image: RgbImageBuffer): File
    suspend fun clearSigaaUserFiles()
    suspend fun clearAll()
}

interface PdfExtractor {
    suspend fun extractCarteirinha(pdfBytes: ByteArray): CoreResult<CarteirinhaPdfExtractedInput>
    suspend fun extractTextLines(pdfBytes: ByteArray, documentType: String): CoreResult<List<String>>
    suspend fun extractRuMenuPages(pdfBytes: ByteArray): CoreResult<List<PdfMenuPageData>>
}
