package org.cqutosp.hydros

import org.cqutosp.hydros.data.update.*
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AppUpdatesTest {
    /** 测试里禁用 update.json 回落，避免单元测试去请求真实网络。 */
    private object EmptyManifest : AppUpdateSource {
        override suspend fun latest(): AppRelease? = null
        override suspend fun history(page: Int) = ReleasePage(emptyList(), false)
        override suspend fun release(tag: String): AppRelease? = null
    }

    @Test fun comparesNumericVersionsAndDoesNotAnnounceEqualOrOlderVersions() {
        assertTrue(isNewerRelease("0.10.0", "0.9.0"))
        assertTrue(isNewerRelease("1.0.0", "0.99.99"))
        assertTrue(isNewerRelease("0.1.0", "0.1.0-beta.1"))
        assertFalse(isNewerRelease("0.1.0", "0.1.0"))
        assertFalse(isNewerRelease("0.1.0", "0.2.0"))
        assertFalse(isNewerRelease("invalid", "0.1.0"))
        assertFalse(isNewerRelease("999999999999999999999.0.0", "0.1.0"))
    }
    @Test fun selectsOnlyCompatibleApksAndHandlesUniversalFallback() {
        fun asset(name: String) = ReleaseAsset(name, "https://github.com/file", 100, null)
        fun release(vararg names: String) = AppRelease("0.2.0", "v0.2.0", "", assets = names.map(::asset))
        assertEquals("app-arm64-v8a.apk", release("app-x86.apk", "app-arm64-v8a.apk", "app-universal.apk").installAsset(listOf("arm64-v8a"))?.name)
        assertNull(release("app-x86_64.apk").installAsset(listOf("x86")))
        assertEquals("app-universal.apk", release("app-x86.apk", "app-universal.apk").installAsset(listOf("arm64-v8a"))?.name)
        assertEquals("app-release.apk", release("app-release.apk").installAsset(listOf("arm64-v8a"))?.name)
        assertNull(release("one.apk", "two.apk").installAsset(listOf("arm64-v8a")))
    }

    @Test fun parsesReleaseMetadataAndPaginatesStableHistory() = runTest {
        val server = MockWebServer(); server.start()
        try {
            val source = GitHubAppUpdates(endpoint = server.url("/releases/latest").toString(), manifest = EmptyManifest)
            val release = """{"draft":false,"prerelease":false,"tag_name":"v0.2.0","name":"Hydros 0.2.0","published_at":"2026-10-15T00:00:00Z","assets":[{"name":"app.apk","size":123,"browser_download_url":"https://github.com/SQMingZiA/Hydros-plus/releases/download/v0.2.0/app.apk","digest":"sha256:${"ab".repeat(32)}"},{"name":"bad.apk","browser_download_url":"https://example.com/bad.apk"}]}"""
            server.enqueue(MockResponse.Builder().body(release).build())
            val latest = source.latest()!!
            assertEquals("Hydros 0.2.0", latest.name)
            assertEquals("2026-10-15T00:00:00Z", latest.publishedAt)
            assertEquals(1, latest.assets.size)
            assertEquals("ab".repeat(32), latest.assets.single().sha256)
            server.takeRequest()
            server.enqueue(MockResponse.Builder().body("[$release,{\"draft\":false,\"prerelease\":true,\"tag_name\":\"v9.0.0-beta\"}]").build())
            val page = source.history(2)
            assertEquals(listOf("0.2.0"), page.releases.map { it.version })
            assertFalse(page.hasMore)
            assertEquals("/releases?per_page=30&page=2", server.takeRequest().target)
        } finally { server.close() }
    }

    @Test fun readsPublishedReleaseAndDistinguishesNoReleaseFromFailure() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val source = GitHubAppUpdates(endpoint = server.url("/releases/latest").toString(), manifest = EmptyManifest)
            server.enqueue(MockResponse.Builder().body("""{"draft":false,"prerelease":false,"tag_name":"v0.2.0","body":"更新说明"}""").build())
            val release = source.latest()!!
            assertEquals("0.2.0", release.version)
            assertEquals("更新说明", release.notes)
            assertEquals("https://github.com/SQMingZiA/Hydros-plus/releases/tag/v0.2.0", release.url)
            server.enqueue(MockResponse.Builder().code(404).build())
            assertNull(source.latest())
            // API 限流/被拒时回落到索引；索引也是空的话就当作「没有新版本」，不再把异常抛到界面。
            server.enqueue(MockResponse.Builder().code(403).build())
            assertNull(source.latest())
            server.enqueue(MockResponse.Builder().body("""{"draft":false,"prerelease":true,"tag_name":"v9.0.0-beta.1"}""").build())
            assertNull(source.latest())
            server.enqueue(MockResponse.Builder().body("""{"draft":true,"prerelease":false,"tag_name":"v9.0.0"}""").build())
            assertNull(source.latest())
            server.enqueue(MockResponse.Builder().body("""{"draft":false,"prerelease":false,"tag_name":"broken"}""").build())
            assertNull(source.latest())
        } finally { server.close() }
    }

    @Test fun parsesGiteeReleaseAndFallsBackToHistoryList() = runTest {
        val server = MockWebServer(); server.start()
        try {
            val gitee = GiteeAppUpdates(apiBase = server.url("/api").toString(), manifest = EmptyManifest)
            server.enqueue(MockResponse.Builder().body("""{"tag_name":"v1.1.8","name":"Hydros+ 1.1.8","body":"说明","assets":[{"name":"app.apk","size":10,"browser_download_url":"https://gitee.com/sqmingzia/Hydros-plus/releases/download/v1.1.8/app.apk"}]}""").build())
            val release = gitee.latest()!!
            assertEquals("1.1.8", release.version)
            assertEquals("Hydros+ 1.1.8", release.name)
            assertEquals(1, release.assets.size)
            assertEquals("/api/releases/latest", server.takeRequest().target)
            // Gitee 的 latest 在还没有正式版时返回 404，此时退回 release 列表的第一条。
            server.enqueue(MockResponse.Builder().code(404).build())
            server.enqueue(MockResponse.Builder().body("""[{"tag_name":"v1.1.7","name":"Hydros+ 1.1.7"}]""").build())
            assertEquals("1.1.7", gitee.latest()!!.version)
            assertEquals("/api/releases/latest", server.takeRequest().target)
            assertEquals("/api/releases?per_page=30&page=1&direction=desc", server.takeRequest().target)
        } finally { server.close() }
    }

    @Test fun defaultSourcePrefersGitHubAndFallsBackToGitee() = runTest {
        val giteeRelease = AppRelease("1.1.8", "v1.1.8", "notes")
        val githubDown = object : AppUpdateSource {
            override suspend fun latest(): AppRelease? = throw java.io.IOException("offline")
            override suspend fun history(page: Int) = ReleasePage(emptyList(), false)
            override suspend fun release(tag: String): AppRelease? = null
        }
        val giteeUp = object : AppUpdateSource {
            override suspend fun latest(): AppRelease? = giteeRelease
            override suspend fun history(page: Int) = ReleasePage(listOf(giteeRelease), false)
            override suspend fun release(tag: String): AppRelease? = giteeRelease
        }
        val source = DefaultAppUpdates(github = githubDown, gitee = giteeUp)
        assertEquals(giteeRelease, source.latest())
        assertEquals(listOf("1.1.8"), source.history(1).releases.map { it.version })
    }
}
