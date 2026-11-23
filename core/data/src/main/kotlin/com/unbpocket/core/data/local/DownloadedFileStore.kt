package com.unbpocket.core.data.local

import android.content.Context
import android.graphics.Bitmap
import com.unbpocket.core.parser.RgbImageBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Gerenciador de arquivos locais persistidos pelo Core (PDFs, imagens da carteirinha e materiais).
 */
class DownloadedFileStore(
    context: Context,
) : FileStore {
    private val rootDir = File(context.filesDir, "unb_pocket_files")

    override suspend fun saveBytes(
        subdir: String,
        fileName: String,
        bytes: ByteArray,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(rootDir, sanitizeSegment(subdir)).apply { mkdirs() }
        val file = File(dir, sanitizeFileName(fileName))
        file.writeBytes(bytes)
        file
    }

    override suspend fun saveRgbAsPng(
        subdir: String,
        fileName: String,
        image: RgbImageBuffer,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(rootDir, sanitizeSegment(subdir)).apply { mkdirs() }
        val file = File(dir, sanitizeFileName(fileName))
        val pixels = IntArray(image.width * image.height)
        for (i in pixels.indices) {
            val base = i * 3
            val r = image.rgbBytes[base].toInt() and 0xFF
            val g = image.rgbBytes[base + 1].toInt() and 0xFF
            val b = image.rgbBytes[base + 2].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bitmap = Bitmap.createBitmap(pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            bitmap.recycle()
        }
        file
    }

    override suspend fun clearSigaaUserFiles() = withContext(Dispatchers.IO) {
        if (!rootDir.exists()) return@withContext
        rootDir.listFiles()?.forEach { entry ->
            if (isSigaaUserDir(entry.name)) {
                entry.deleteRecursively()
            }
        }
        Unit
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        if (rootDir.exists()) {
            rootDir.deleteRecursively()
        }
    }

    private fun sanitizeSegment(segment: String): String =
        segment.replace(Regex("""[^a-zA-Z0-9._-]"""), "_").ifBlank { "default" }

    private fun sanitizeFileName(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("""[^a-zA-Z0-9._-]"""), "_")
            .ifBlank { "arquivo.bin" }

    internal companion object {
        fun isSigaaUserDir(name: String): Boolean =
            name == "carteirinha" ||
                name == "documentos" ||
                name == "materiais" ||
                name.startsWith("materiais_")
    }
}
