package com.unbpocket.core.model.update

/**
 * Metadados de uma release estavel publicada no GitHub Releases do UnB Pocket.
 */
data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val publishedAtIso: String,
    val htmlUrl: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long?,
    val apkSha256: String? = null,
)

/**
 * Resultado da verificacao de atualizacao comparando a versao instalada com a ultima release estavel.
 */
data class AppUpdateCheckResult(
    val currentVersionName: String,
    val latestRelease: AppReleaseInfo?,
    val hasUpdate: Boolean,
    val checkedAtEpochMillis: Long,
)

/**
 * Progresso reativo do download em streaming do APK de atualizacao.
 */
data class AppUpdateDownloadProgress(
    val versionName: String,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val isCompleted: Boolean = false,
    val localApkPath: String? = null,
) {
    val progressFraction: Float?
        get() = totalBytes?.takeIf { it > 0L }?.let { total ->
            (bytesDownloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}
