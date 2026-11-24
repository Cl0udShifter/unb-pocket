package com.unbpocket.core.parser

import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.update.AppReleaseInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Parser de payloads da GitHub Releases REST API (`/repos/{owner}/{repo}/releases/latest`)
 * e comparador SemVer puro para atualizacoes in-app do UnB Pocket.
 */
object GitHubReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseLatestRelease(rawJson: String): CoreResult<AppReleaseInfo?> = runCatching {
        val obj = json.parseToJsonElement(rawJson).jsonObject
        val draft = obj.boolean("draft") ?: false
        val prerelease = obj.boolean("prerelease") ?: false
        if (draft || prerelease) {
            return@runCatching null
        }

        val tagName = obj.string("tag_name").orEmpty().trim()
        if (tagName.isEmpty()) {
            return CoreResult.Failure(
                CoreError.ParseError("github/releases/latest", "Campo tag_name ausente na release."),
            )
        }

        val versionName = normalizeVersion(tagName)
        if (versionName.isEmpty()) {
            return CoreResult.Failure(
                CoreError.ParseError("github/releases/latest", "Versao invalida na tag '$tagName'."),
            )
        }

        val assets = (obj["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val selectedAsset = selectBestApkAsset(assets) ?: return@runCatching null

        val apkName = selectedAsset.string("name").orEmpty().trim()
        val apkDownloadUrl = selectedAsset.string("browser_download_url").orEmpty().trim()
        if (apkName.isEmpty() || apkDownloadUrl.isEmpty()) {
            return@runCatching null
        }

        val rawDigest = selectedAsset.string("digest").orEmpty().trim()
        val sha256 = rawDigest
            .takeIf { it.startsWith("sha256:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.length == 64 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' } }

        val title = obj.string("name").orEmpty().trim().ifEmpty { "UnB Pocket $tagName" }
        val notes = obj.string("body").orEmpty().trim()
        val publishedAt = obj.string("published_at").orEmpty().trim()
        val htmlUrl = obj.string("html_url").orEmpty().trim()

        AppReleaseInfo(
            tagName = tagName,
            versionName = versionName,
            title = title,
            releaseNotes = notes,
            publishedAtIso = publishedAt,
            htmlUrl = htmlUrl,
            apkDownloadUrl = apkDownloadUrl,
            apkFileName = apkName,
            apkSizeBytes = selectedAsset.long("size")?.takeIf { it > 0L },
            apkSha256 = sha256,
        )
    }.fold(
        onSuccess = { CoreResult.Success(it) },
        onFailure = { error ->
            CoreResult.Failure(
                CoreError.ParseError(
                    source = "github/releases/latest",
                    reason = error.message ?: "JSON invalido retornado pela API do GitHub.",
                ),
            )
        },
    )

    fun normalizeVersion(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").trim()

    /**
     * Retorna `true` se `candidateVersion` for estritamente mais nova que `currentVersion`
     * segundo SemVer (`major.minor.patch[-prerelease]`).
     */
    fun isNewerVersion(candidateVersion: String, currentVersion: String): Boolean {
        val candidate = parseSemVer(candidateVersion) ?: return false
        val current = parseSemVer(currentVersion) ?: return false
        return candidate > current
    }

    private fun selectBestApkAsset(assets: List<JsonObject>): JsonObject? {
        val apkAssets = assets.filter { asset ->
            val name = asset.string("name").orEmpty().trim().lowercase()
            val url = asset.string("browser_download_url").orEmpty().trim()
            name.endsWith(".apk") && url.isNotEmpty()
        }
        if (apkAssets.isEmpty()) return null

        return apkAssets.maxByOrNull { asset ->
            val name = asset.string("name").orEmpty().trim().lowercase()
            var score = 0
            if (!name.contains("debug")) score += 100
            if (name.startsWith("unb-pocket")) score += 30
            if (name.contains("release")) score += 20
            if (!name.contains("unsigned")) score += 10
            score
        }
    }

    private data class ParsedSemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: String?,
    ) : Comparable<ParsedSemVer> {
        override fun compareTo(other: ParsedSemVer): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            if (patch != other.patch) return patch.compareTo(other.patch)
            return when {
                preRelease == null && other.preRelease == null -> 0
                preRelease == null -> 1
                other.preRelease == null -> -1
                else -> comparePreRelease(preRelease, other.preRelease)
            }
        }

        private fun comparePreRelease(a: String, b: String): Int {
            val aParts = a.split('.')
            val bParts = b.split('.')
            val max = maxOf(aParts.size, bParts.size)
            for (i in 0 until max) {
                val pA = aParts.getOrNull(i) ?: return -1
                val pB = bParts.getOrNull(i) ?: return 1
                val nA = pA.toIntOrNull()
                val nB = pB.toIntOrNull()
                val cmp = when {
                    nA != null && nB != null -> nA.compareTo(nB)
                    nA != null -> -1
                    nB != null -> 1
                    else -> pA.compareTo(pB)
                }
                if (cmp != 0) return cmp
            }
            return 0
        }
    }

    private fun parseSemVer(raw: String): ParsedSemVer? {
        val clean = normalizeVersion(raw).substringBefore('+')
        if (clean.isEmpty()) return null
        val corePart = clean.substringBefore('-')
        val preRelease = clean.substringAfter('-', "").takeIf { it.isNotEmpty() }
        val nums = corePart.split('.')
        val major = nums.getOrNull(0)?.toIntOrNull() ?: return null
        val minor = nums.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = nums.getOrNull(2)?.toIntOrNull() ?: 0
        return ParsedSemVer(major, minor, patch, preRelease)
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        this[key]?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.long(key: String): Long? =
        this[key]?.jsonPrimitive?.longOrNull
}
