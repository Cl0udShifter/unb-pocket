package com.unbpocket.core.parser

import com.unbpocket.core.model.common.CoreResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseParserTest {
    @Test
    fun parsesStableReleasePrioritizingNonDebugApkAndSha256Digest() {
        val sha = "a".repeat(64)
        val payload = """
            {
              "tag_name": "v1.1.0",
              "name": "UnB Pocket v1.1.0",
              "body": "- Novidades da versao",
              "draft": false,
              "prerelease": false,
              "published_at": "2026-11-24T12:00:00Z",
              "html_url": "https://github.com/Cl0udShifter/unb-pocket/releases/tag/v1.1.0",
              "assets": [
                {
                  "name": "app-debug.apk",
                  "size": 25000000,
                  "browser_download_url": "https://github.com/Cl0udShifter/unb-pocket/releases/download/v1.1.0/app-debug.apk"
                },
                {
                  "name": "unb-pocket-v1.1.0.apk",
                  "size": 9300000,
                  "digest": "sha256:$sha",
                  "browser_download_url": "https://github.com/Cl0udShifter/unb-pocket/releases/download/v1.1.0/unb-pocket-v1.1.0.apk"
                }
              ]
            }
        """.trimIndent()

        val result = GitHubReleaseParser.parseLatestRelease(payload)
        assertTrue(result is CoreResult.Success)
        val release = (result as CoreResult.Success).value
        assertNotNull(release)
        assertEquals("v1.1.0", release!!.tagName)
        assertEquals("1.1.0", release.versionName)
        assertEquals("UnB Pocket v1.1.0", release.title)
        assertEquals("- Novidades da versao", release.releaseNotes)
        assertEquals("unb-pocket-v1.1.0.apk", release.apkFileName)
        assertEquals(9300000L, release.apkSizeBytes)
        assertEquals(sha, release.apkSha256)
    }

    @Test
    fun ignoresDraftPrereleaseAndReleasesWithoutApkAsset() {
        val prereleaseJson = """
            {
              "tag_name": "v1.2.0-beta.1",
              "draft": false,
              "prerelease": true,
              "assets": [{"name": "unb-pocket.apk", "browser_download_url": "https://github.com/a.apk"}]
            }
        """.trimIndent()
        val draftJson = """
            {
              "tag_name": "v1.2.0",
              "draft": true,
              "prerelease": false,
              "assets": [{"name": "unb-pocket.apk", "browser_download_url": "https://github.com/a.apk"}]
            }
        """.trimIndent()
        val noApkJson = """
            {
              "tag_name": "v1.2.0",
              "draft": false,
              "prerelease": false,
              "assets": [{"name": "checksums.txt", "browser_download_url": "https://github.com/checksums.txt"}]
            }
        """.trimIndent()

        assertNull((GitHubReleaseParser.parseLatestRelease(prereleaseJson) as CoreResult.Success).value)
        assertNull((GitHubReleaseParser.parseLatestRelease(draftJson) as CoreResult.Success).value)
        assertNull((GitHubReleaseParser.parseLatestRelease(noApkJson) as CoreResult.Success).value)
    }

    @Test
    fun comparesSemVerVersionsAccurately() {
        assertTrue(GitHubReleaseParser.isNewerVersion("1.0.1", "1.0.0"))
        assertTrue(GitHubReleaseParser.isNewerVersion("v1.10.0", "1.2.0"))
        assertTrue(GitHubReleaseParser.isNewerVersion("2.0.0", "1.9.9"))
        assertTrue(GitHubReleaseParser.isNewerVersion("1.0.0", "1.0.0-rc.1"))
        assertTrue(GitHubReleaseParser.isNewerVersion("1.0.0-beta.2", "1.0.0-beta.1"))

        assertFalse(GitHubReleaseParser.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(GitHubReleaseParser.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(GitHubReleaseParser.isNewerVersion("0.9.9", "1.0.0"))
        assertFalse(GitHubReleaseParser.isNewerVersion("1.0.0-rc.1", "1.0.0"))
    }
}
