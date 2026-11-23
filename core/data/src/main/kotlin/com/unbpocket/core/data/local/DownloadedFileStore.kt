package com.unbpocket.core.data.local

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.unbpocket.core.parser.RgbImageBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Gerenciador de arquivos locais persistidos pelo Core (PDFs, imagens da carteirinha e materiais).
 */
class DownloadedFileStore(
    private val context: Context,
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

    override suspend fun saveToPublicDownloads(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): String? = withContext(Dispatchers.IO) {
        val safeName = sanitizePublicFileName(fileName)
        val safeMime = mimeType.ifBlank { "application/octet-stream" }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_DOWNLOAD_SUBDIR"
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, safeMime)
                    put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching null
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                        out.flush()
                    } ?: throw IOException("Falha ao abrir OutputStream no MediaStore.")

                    val readyValues = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(uri, readyValues, null, null)
                    val actualName = queryDisplayName(resolver, uri) ?: safeName
                    "Downloads/$PUBLIC_DOWNLOAD_SUBDIR/$actualName"
                } catch (t: Throwable) {
                    runCatching { resolver.delete(uri, null, null) }
                    throw t
                }
            } else {
                if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    return@runCatching null
                }
                @Suppress("DEPRECATION")
                val baseDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(baseDownloads, PUBLIC_DOWNLOAD_SUBDIR).apply { mkdirs() }
                val targetFile = uniqueTargetFile(targetDir, safeName)
                targetFile.writeBytes(bytes)
                "Downloads/$PUBLIC_DOWNLOAD_SUBDIR/${targetFile.name}"
            }
        }.getOrNull()
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

    private fun sanitizePublicFileName(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("""[\r\n\t\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "arquivo.bin" }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }

    private fun uniqueTargetFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val dotIndex = fileName.lastIndexOf('.')
        val base = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val ext = if (dotIndex > 0) fileName.substring(dotIndex) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($counter)$ext")
            counter++
        }
        return candidate
    }

    internal companion object {
        private const val PUBLIC_DOWNLOAD_SUBDIR = "UnB Pocket"

        fun isSigaaUserDir(name: String): Boolean =
            name == "carteirinha" ||
                name == "documentos" ||
                name == "materiais" ||
                name.startsWith("materiais_")
    }
}
