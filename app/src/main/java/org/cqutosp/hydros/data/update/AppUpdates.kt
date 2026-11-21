package org.cqutosp.hydros.data.update

import org.cqutosp.hydros.data.feedback.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.cqutosp.hydros.data.network.retryReadQuery
import org.cqutosp.hydros.data.network.QueryHttpFailure
import org.cqutosp.hydros.data.network.transientHttpStatus
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// 正式仓库与 Releases 都在 GitHub（Hydros+），Gitee 仅作同步镜像。
const val HYDROS_GITHUB_REPO = "SQMingZiA/Hydros-plus"
const val HYDROS_REPOSITORY = "https://github.com/$HYDROS_GITHUB_REPO"
const val HYDROS_RELEASES = "$HYDROS_REPOSITORY/releases"
private const val HYDROS_RELEASE_DOWNLOAD_PREFIX = "$HYDROS_RELEASES/download/"

const val HYDROS_GITEE_REPO = "sqmingzia/Hydros-plus"
const val HYDROS_GITEE = "https://gitee.com/$HYDROS_GITEE_REPO"
const val HYDROS_GITEE_RELEASES = "$HYDROS_GITEE/releases"
const val HYDROS_GITEE_API = "https://gitee.com/api/v5/repos/$HYDROS_GITEE_REPO"
private const val HYDROS_GITEE_DOWNLOAD_PREFIX = "$HYDROS_GITEE_RELEASES/download/"

data class ReleaseAsset(val name: String, val url: String, val size: Long, val sha256: String?)

data class AppRelease(
    val version: String, val tag: String, val notes: String,
    val name: String = "v$version", val publishedAt: String? = null,
    val assets: List<ReleaseAsset> = emptyList(),
    val source: UpdateSource = UpdateSource.GITHUB,
) {
    /** 发布页地址按来源生成，Gitee 的版本不会链到 GitHub。 */
    val url: String get() = source.releaseUrl(tag)
}

data class ReleasePage(val releases: List<AppRelease>, val hasMore: Boolean)

fun interface AppUpdateSource {
    suspend fun latest(): AppRelease?
    suspend fun history(page: Int): ReleasePage = ReleasePage(emptyList(), false)
    suspend fun release(tag: String): AppRelease? = latest()?.takeIf { it.tag == tag }
}

/** Only published, stable GitHub releases can announce an update. */
class GitHubAppUpdates(
    private val client: OkHttpClient = OkHttpClient.Builder().retryOnConnectionFailure(false).callTimeout(15, TimeUnit.SECONDS).build(),
    private val endpoint: String = "https://api.github.com/repos/$HYDROS_GITHUB_REPO/releases/latest",
    private val manifest: AppUpdateSource = ManifestAppUpdates(HYDROS_GITHUB_MANIFEST, UpdateSource.GITHUB),
) : AppUpdateSource {
    /** API 有匿名额度（本次发版已撞到 403），失败时回落到仓库里的 update.json。 */
    override suspend fun latest(): AppRelease? =
        runCatching { latestFromApi() }.getOrNull() ?: manifest.latest()

    override suspend fun history(page: Int): ReleasePage {
        val fromApi = runCatching { historyFromApi(page) }.getOrNull()
        if (fromApi != null && (fromApi.releases.isNotEmpty() || page > 1)) return fromApi
        return manifest.history(page)
    }

    override suspend fun release(tag: String): AppRelease? =
        runCatching { releaseFromApi(tag) }.getOrNull() ?: manifest.release(tag)

    private suspend fun latestFromApi(): AppRelease? = withContext(Dispatchers.IO) {
        request(endpoint, allowMissing = true)?.let { parseUpdateData { parseRelease(it.jsonObject, UpdateSource.GITHUB) } }
    }

    private suspend fun historyFromApi(page: Int): ReleasePage = withContext(Dispatchers.IO) {
        require(page > 0)
        val entries = parseUpdateData { request("${endpoint.removeSuffix("/latest")}?per_page=30&page=$page")!!.jsonArray }
        ReleasePage(entries.mapNotNull { parseUpdateData { parseRelease(it.jsonObject, UpdateSource.GITHUB) } }, entries.size == 30)
    }

    private suspend fun releaseFromApi(tag: String): AppRelease? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(tag, "UTF-8").replace("+", "%20")
        request("${endpoint.removeSuffix("/latest")}/tags/$encoded", allowMissing = true)?.let { parseUpdateData { parseRelease(it.jsonObject, UpdateSource.GITHUB) } }
    }

    private suspend fun request(url: String, allowMissing: Boolean = false): JsonElement? = retryReadQuery { budget ->
        budget.attempt {
            val request = Request.Builder().url(url)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "HydrosPlus-Android").build()
            suspendCancellableCoroutine<JsonElement?> { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!continuation.isActive) return
                            try {
                                val result = when {
                                    allowMissing && response.code == 404 -> null
                                    transientHttpStatus(response.code) -> throw QueryHttpFailure(response.code,
                                        response.header("Retry-After")?.toLongOrNull()?.takeIf { it in 0..3 }?.times(1_000)
                                            ?: response.header("Retry-After")?.let { Long.MAX_VALUE })
                                    !response.isSuccessful -> throw AppFailure(FailureOperation.UPDATE, FailureStage.RESPONSE, FailureReason.REJECTED)
                                    else -> parseUpdateData { Json.parseToJsonElement(response.body.string()) }
                                }
                                if (continuation.isActive) continuation.resume(result)
                            } catch (error: Exception) {
                                if (continuation.isActive) continuation.resumeWithException(error)
                            }
                        }
                    }
                })
            }
        }
    }
}

/**
 * 备用更新源：Gitee 的 releases API（GitHub 不可用时兜底）。接口结构与 GitHub 接近，但资源下载地址前缀不同，
 * 因此解析时两种前缀都接受。
 */
class GiteeAppUpdates(
    private val client: OkHttpClient = OkHttpClient.Builder().retryOnConnectionFailure(false).callTimeout(15, TimeUnit.SECONDS).build(),
    private val apiBase: String = HYDROS_GITEE_API,
    private val manifest: AppUpdateSource = ManifestAppUpdates(HYDROS_GITEE_MANIFEST, UpdateSource.GITEE),
) : AppUpdateSource {
    /** 索引文件优先（Gitee 的匿名 API 很容易被限流），失败再退回 API。 */
    override suspend fun latest(): AppRelease? =
        runCatching { manifest.latest() }.getOrNull() ?: apiLatest()

    override suspend fun history(page: Int): ReleasePage {
        val fromManifest = runCatching { manifest.history(page) }.getOrNull()
        if (fromManifest != null && (fromManifest.releases.isNotEmpty() || page > 1)) return fromManifest
        return apiHistory(page)
    }

    override suspend fun release(tag: String): AppRelease? =
        runCatching { manifest.release(tag) }.getOrNull() ?: apiRelease(tag)

    private suspend fun apiLatest(): AppRelease? = withContext(Dispatchers.IO) {
        request("$apiBase/releases/latest", allowMissing = true)
            ?.let { parseUpdateData { parseRelease(it.jsonObject, UpdateSource.GITEE) } }
            ?: apiHistory(1).releases.firstOrNull()
    }

    private suspend fun apiHistory(page: Int): ReleasePage = withContext(Dispatchers.IO) {
        require(page > 0)
        val entries = parseUpdateData {
            request("$apiBase/releases?per_page=30&page=$page&direction=desc")?.jsonArray
        } ?: JsonArray(emptyList())
        ReleasePage(entries.mapNotNull { parseUpdateData { parseRelease(it.jsonObject, UpdateSource.GITEE) } }, entries.size == 30)
    }

    private suspend fun apiRelease(tag: String): AppRelease? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(tag, "UTF-8").replace("+", "%20")
        request("$apiBase/releases/tags/$encoded", allowMissing = true)
            ?.let { parseUpdateData { parseRelease(it.jsonObject, UpdateSource.GITEE) } }
    }

    private suspend fun request(url: String, allowMissing: Boolean = false): JsonElement? = retryReadQuery { budget ->
        budget.attempt {
            val request = Request.Builder().url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "HydrosPlus-Android").build()
            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!continuation.isActive) return
                            try {
                                val result = when {
                                    allowMissing && response.code == 404 -> null
                                    transientHttpStatus(response.code) -> throw QueryHttpFailure(
                                        response.code,
                                        response.header("Retry-After")?.toLongOrNull()?.takeIf { it in 0..3 }?.times(1_000)
                                            ?: response.header("Retry-After")?.let { Long.MAX_VALUE }
                                    )
                                    !response.isSuccessful -> throw AppFailure(FailureOperation.UPDATE, FailureStage.RESPONSE, FailureReason.REJECTED)
                                    else -> parseUpdateData { Json.parseToJsonElement(response.body.string()) }
                                }
                                if (continuation.isActive) continuation.resume(result)
                            } catch (error: Exception) {
                                if (continuation.isActive) continuation.resumeWithException(error)
                            }
                        }
                    }
                })
            }
        }
    }
}

/**
 * 按用户选定的更新源优先检查，另一个源兜底；两个都失败才把异常抛给界面。
 * 默认 Gitee（国内更稳），用户可以在「软件更新 → 更多信息 → 更新源」里切换。
 */
class DefaultAppUpdates(
    private val source: () -> UpdateSource = { UpdateSource.GITEE },
    private val gitee: AppUpdateSource = GiteeAppUpdates(),
    private val github: AppUpdateSource = GitHubAppUpdates(),
) : AppUpdateSource {
    private fun primary(): AppUpdateSource =
        if (source() == UpdateSource.GITHUB) github else gitee

    private fun fallback(): AppUpdateSource =
        if (source() == UpdateSource.GITHUB) gitee else github

    override suspend fun latest(): AppRelease? {
        val first = try { primary().latest() } catch (error: Exception) { null }
        first?.let { return it }
        return fallback().latest()
    }

    override suspend fun history(page: Int): ReleasePage = try {
        primary().history(page).takeIf { it.releases.isNotEmpty() } ?: fallback().history(page)
    } catch (error: Exception) {
        fallback().history(page)
    }

    override suspend fun release(tag: String): AppRelease? {
        val first = try { primary().release(tag) } catch (error: Exception) { null }
        return first ?: fallback().release(tag)
    }
}

private inline fun <T> parseUpdateData(block: () -> T): T = try { block() }
catch (error: AppFailure) { throw error }
catch (error: IllegalArgumentException) { throw AppFailure(FailureOperation.UPDATE, FailureStage.RESPONSE, FailureReason.INVALID_DATA, error) }

internal fun parseRelease(json: JsonObject, source: UpdateSource = UpdateSource.GITHUB): AppRelease? {
    if (json["draft"]?.jsonPrimitive?.booleanOrNull == true ||
        json["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return null
    val tag = json["tag_name"]?.jsonPrimitive?.contentOrNull ?: throw AppFailure(FailureOperation.UPDATE, FailureStage.RESPONSE, FailureReason.INVALID_DATA)
    val version = tag.removePrefix("v")
    if (!Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(version)) throw AppFailure(FailureOperation.UPDATE, FailureStage.RESPONSE, FailureReason.INVALID_DATA)
    val assets = json["assets"]?.jsonArray.orEmpty().mapNotNull { element ->
        val asset = element.jsonObject
        val name = asset["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val hosted = url.startsWith(HYDROS_RELEASE_DOWNLOAD_PREFIX) || url.startsWith(HYDROS_GITEE_DOWNLOAD_PREFIX)
        if (!name.endsWith(".apk", ignoreCase = true) || !hosted) return@mapNotNull null
        ReleaseAsset(name, url, asset["size"]?.jsonPrimitive?.longOrNull ?: 0,
            asset["digest"]?.jsonPrimitive?.contentOrNull?.takeIf { it.matches(Regex("sha256:[a-fA-F0-9]{64}")) }?.substringAfter(':'))
    }
    return AppRelease(version, tag, json["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        json["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: tag,
        json["published_at"]?.jsonPrimitive?.contentOrNull, assets, source)
}

/** Prefer a matching split, then an explicitly universal or sole unqualified APK. */
fun AppRelease.installAsset(abis: List<String>): ReleaseAsset? {
    val known = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    fun matches(name: String, abi: String): Boolean =
        (abi != "x86" || !Regex("(^|[-_.])${Regex.escape("x86_64")}(?=[-_.]|$)", RegexOption.IGNORE_CASE).containsMatchIn(name)) &&
            Regex("(^|[-_.])${Regex.escape(abi)}(?=[-_.]|$)", RegexOption.IGNORE_CASE).containsMatchIn(name)
    for (abi in abis) assets.singleOrNull { asset -> known.firstOrNull { matches(asset.name, it) } == abi }?.let { return it }
    assets.singleOrNull { it.name.contains("universal", ignoreCase = true) }?.let { return it }
    return assets.filter { asset -> known.none { matches(asset.name, it) } }.singleOrNull()
}

/** Compare numeric version components; 0.10.0 must be newer than 0.9.0. */
fun isNewerRelease(latest: String, installed: String): Boolean {
    fun parts(value: String) = value.removePrefix("v").substringBefore('-').substringBefore('+')
        .split('.').takeIf { it.size == 3 }?.map { it.toLongOrNull() }
        ?.takeIf { values -> values.all { it != null && it >= 0 } }?.filterNotNull()
    val next = parts(latest) ?: return false
    val current = parts(installed) ?: return false
    for (index in next.indices) {
        if (next[index] != current[index]) return next[index] > current[index]
    }
    return '-' in installed && '-' !in latest
}