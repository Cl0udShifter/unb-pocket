package com.unbpocket.core.data.repository

import com.unbpocket.core.data.local.AppUpdateCheckResultDto
import com.unbpocket.core.data.local.CacheMappers
import com.unbpocket.core.data.local.FileStore
import com.unbpocket.core.data.local.SnapshotCacheStore
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.DataOrigin
import com.unbpocket.core.model.common.SyncedData
import com.unbpocket.core.model.contract.AppUpdateRepository
import com.unbpocket.core.model.turmas.ArquivoBaixado
import com.unbpocket.core.model.update.AppReleaseInfo
import com.unbpocket.core.model.update.AppUpdateCheckResult
import com.unbpocket.core.model.update.AppUpdateDownloadProgress
import com.unbpocket.core.network.UnbHttpTransport
import com.unbpocket.core.parser.GitHubReleaseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Repositorio de atualizacoes in-app via GitHub Releases (`Cl0udShifter/unb-pocket`).
 * Executa apenas requisicoes GET de leitura (API de releases + download streaming do APK).
 */
class AppUpdateRepositoryImpl(
    private val transport: UnbHttpTransport,
    private val cacheStore: SnapshotCacheStore,
    private val fileStore: FileStore,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) : AppUpdateRepository {
    private val checkMutex = Mutex()
    private val downloadMutex = Mutex()

    private val _downloadProgress = MutableStateFlow<AppUpdateDownloadProgress?>(null)
    override val downloadProgress: StateFlow<AppUpdateDownloadProgress?> = _downloadProgress.asStateFlow()

    override fun observeLatestUpdateCheck(): Flow<SyncedData<AppUpdateCheckResult>?> =
        cacheStore.observe(
            cacheKey = SnapshotCacheStore.Keys.APP_UPDATE_CHECK,
            serializer = AppUpdateCheckResultDto.serializer(),
            fromDto = CacheMappers::fromDto,
        )

    override suspend fun checkForUpdates(
        currentVersionName: String,
        force: Boolean,
    ): CoreResult<AppUpdateCheckResult> = checkMutex.withLock {
        val normalizedCurrent = GitHubReleaseParser.normalizeVersion(currentVersionName)
            .ifBlank { currentVersionName.trim() }
        val now = clockMillis()

        if (!force) {
            val cached = cacheStore.get(
                cacheKey = SnapshotCacheStore.Keys.APP_UPDATE_CHECK,
                serializer = AppUpdateCheckResultDto.serializer(),
                fromDto = CacheMappers::fromDto,
            )?.value
            if (cached != null &&
                cached.currentVersionName == normalizedCurrent &&
                (now - cached.checkedAtEpochMillis).coerceAtLeast(0L) < CHECK_COOLDOWN_MILLIS
            ) {
                return@withLock CoreResult.Success(cached)
            }
        }

        val payload = when (val res = transport.get(transport.endpoints.githubLatestReleaseUrl, GITHUB_API_HEADERS)) {
            is CoreResult.Failure -> {
                if (res.error is CoreError.HttpFailure && (res.error as CoreError.HttpFailure).statusCode == 404) {
                    val emptyCheck = AppUpdateCheckResult(
                        currentVersionName = normalizedCurrent,
                        latestRelease = null,
                        hasUpdate = false,
                        checkedAtEpochMillis = now,
                    )
                    persistCheckResult(emptyCheck, now)
                    return@withLock CoreResult.Success(emptyCheck)
                }
                return@withLock res
            }
            is CoreResult.Success -> res.value
        }

        val parsedRelease = when (val parsed = GitHubReleaseParser.parseLatestRelease(payload.decodeUtf8())) {
            is CoreResult.Failure -> return@withLock parsed
            is CoreResult.Success -> parsed.value
        }

        val hasUpdate = parsedRelease != null &&
            GitHubReleaseParser.isNewerVersion(parsedRelease.versionName, normalizedCurrent)
        val result = AppUpdateCheckResult(
            currentVersionName = normalizedCurrent,
            latestRelease = parsedRelease,
            hasUpdate = hasUpdate,
            checkedAtEpochMillis = now,
        )
        persistCheckResult(result, now)
        CoreResult.Success(result)
    }

    override suspend fun downloadReleaseApk(release: AppReleaseInfo): CoreResult<ArquivoBaixado> =
        downloadMutex.withLock {
            val fileName = release.apkFileName.trim()
                .ifBlank { "unb-pocket-v${release.versionName}.apk" }
                .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }

            val targetFile = runCatching {
                fileStore.resolveFile(UPDATES_SUBDIR, fileName)
            }.getOrElse { err ->
                return@withLock CoreResult.Failure(
                    CoreError.StorageError("downloadReleaseApk", err.message ?: "Falha ao preparar arquivo APK."),
                )
            }

            if (isCachedApkValid(targetFile, release)) {
                val size = targetFile.length()
                _downloadProgress.value = AppUpdateDownloadProgress(
                    versionName = release.versionName,
                    bytesDownloaded = size,
                    totalBytes = size,
                    isCompleted = true,
                    localApkPath = targetFile.absolutePath,
                )
                return@withLock CoreResult.Success(
                    ArquivoBaixado(
                        nomeArquivo = targetFile.name,
                        mimeType = APK_MIME_TYPE,
                        localFilePath = targetFile.absolutePath,
                        tamanhoBytes = size,
                    ),
                )
            }

            _downloadProgress.value = AppUpdateDownloadProgress(
                versionName = release.versionName,
                bytesDownloaded = 0L,
                totalBytes = release.apkSizeBytes,
                isCompleted = false,
            )

            val streamResult = transport.downloadToFile(
                url = release.apkDownloadUrl,
                destinationFile = targetFile,
                headers = mapOf("Accept" to "application/octet-stream"),
                onProgress = { bytesRead, totalBytes ->
                    _downloadProgress.value = AppUpdateDownloadProgress(
                        versionName = release.versionName,
                        bytesDownloaded = bytesRead,
                        totalBytes = totalBytes ?: release.apkSizeBytes,
                        isCompleted = false,
                    )
                },
            )

            val meta = when (streamResult) {
                is CoreResult.Failure -> {
                    _downloadProgress.value = null
                    return@withLock streamResult
                }
                is CoreResult.Success -> streamResult.value
            }

            if (meta.bytesWritten <= 0L) {
                runCatching { targetFile.delete() }
                _downloadProgress.value = null
                return@withLock CoreResult.Failure(
                    CoreError.StorageError("downloadReleaseApk", "Arquivo APK baixado esta vazio."),
                )
            }

            val expectedSha = release.apkSha256?.lowercase()
            if (expectedSha != null && !meta.sha256Hex.equals(expectedSha, ignoreCase = true)) {
                runCatching { targetFile.delete() }
                _downloadProgress.value = null
                return@withLock CoreResult.Failure(
                    CoreError.StorageError(
                        operation = "downloadReleaseApk",
                        reason = "Falha na verificacao SHA-256 do APK baixado.",
                    ),
                )
            }

            _downloadProgress.value = AppUpdateDownloadProgress(
                versionName = release.versionName,
                bytesDownloaded = meta.bytesWritten,
                totalBytes = meta.bytesWritten,
                isCompleted = true,
                localApkPath = targetFile.absolutePath,
            )

            CoreResult.Success(
                ArquivoBaixado(
                    nomeArquivo = targetFile.name,
                    mimeType = APK_MIME_TYPE,
                    localFilePath = targetFile.absolutePath,
                    tamanhoBytes = meta.bytesWritten,
                ),
            )
        }

    private suspend fun persistCheckResult(result: AppUpdateCheckResult, now: Long) {
        cacheStore.put(
            cacheKey = SnapshotCacheStore.Keys.APP_UPDATE_CHECK,
            domain = SnapshotCacheStore.Domains.APP_UPDATE,
            origin = DataOrigin.REMOTE,
            syncedAtEpochMillis = now,
            value = result,
            serializer = AppUpdateCheckResultDto.serializer(),
            toDto = CacheMappers::toDto,
        )
    }

    private suspend fun isCachedApkValid(file: File, release: AppReleaseInfo): Boolean =
        withContext(Dispatchers.IO) {
            if (!file.exists() || file.length() <= 0L) return@withContext false
            val expectedSize = release.apkSizeBytes
            if (expectedSize != null && file.length() != expectedSize) return@withContext false
            val expectedSha = release.apkSha256?.lowercase() ?: return@withContext true
            val actualSha = runCatching { computeFileSha256(file) }.getOrNull() ?: return@withContext false
            actualSha.equals(expectedSha, ignoreCase = true)
        }

    private fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8 * 1024)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val CHECK_COOLDOWN_MILLIS: Long = 6 * 60 * 60 * 1000L
        const val APK_MIME_TYPE: String = "application/vnd.android.package-archive"
        private const val UPDATES_SUBDIR: String = "updates"

        private val GITHUB_API_HEADERS: Map<String, String> = mapOf(
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
        )
    }
}
