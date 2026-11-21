package org.cqutosp.hydros.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

/**
 * 更新索引文件：仓库根目录的 update.json。
 *
 * 为什么不直接读 releases API：Gitee 与 GitHub 的匿名 API 都有很低的调用额度（本次发版就触发了
 * 403 Rate Limit Exceeded），用户侧一旦被限流就会提示「服务暂时无法处理」。索引文件走的是仓库
 * 原始文件通道，没有这套额度，因此作为首选，API 只作为兜底。
 */
const val HYDROS_GITEE_MANIFEST = "$HYDROS_GITEE/raw/main/update.json"
const val HYDROS_GITHUB_MANIFEST = "https://raw.githubusercontent.com/$HYDROS_GITHUB_REPO/main/update.json"

private const val MANIFEST_PAGE_SIZE = 30
private const val MANIFEST_TTL_MS = 5 * 60 * 1000L

/** 索引里的资源只记录文件名与校验信息，下载地址按当前更新源拼，避免链到另一个仓库。 */
private data class ManifestAsset(val name: String, val size: Long, val sha256: String?)

class ManifestAppUpdates(
    private val manifestUrl: String,
    private val source: UpdateSource,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .callTimeout(15, TimeUnit.SECONDS)
        .build(),
) : AppUpdateSource {
    private var cached: List<AppRelease> = emptyList()
    private var cachedAt = 0L

    override suspend fun latest(): AppRelease? = load().firstOrNull()

    override suspend fun history(page: Int): ReleasePage {
        require(page > 0)
        val all = load()
        val start = (page - 1) * MANIFEST_PAGE_SIZE
        val slice = all.drop(start).take(MANIFEST_PAGE_SIZE)
        return ReleasePage(slice, all.size > start + slice.size)
    }

    override suspend fun release(tag: String): AppRelease? = load().firstOrNull { it.tag == tag }

    private suspend fun load(): List<AppRelease> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cached.isNotEmpty() && now - cachedAt < MANIFEST_TTL_MS) return@withContext cached
        val fresh = fetch()
        cached = fresh
        cachedAt = now
        fresh
    }

    private suspend fun fetch(): List<AppRelease> = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(manifestUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "HydrosPlus-Android")
            .build()
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
                        if (!response.isSuccessful) throw IOException("manifest HTTP " + response.code)
                        val root = Json.parseToJsonElement(response.body.string())
                        val list = when (root) {
                            is JsonArray -> root
                            is JsonObject -> root["releases"]?.jsonArray ?: JsonArray(emptyList())
                            else -> JsonArray(emptyList())
                        }
                        val releases = list.mapNotNull { element ->
                            runCatching { parseManifestRelease(element.jsonObject) }.getOrNull()
                        }
                        if (continuation.isActive) continuation.resume(releases)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
        })
    }

    private fun parseManifestRelease(json: JsonObject): AppRelease {
        val tag = json["tag"]?.jsonPrimitive?.contentOrNull
            ?: json["tag_name"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("tag")
        val version = json["version"]?.jsonPrimitive?.contentOrNull ?: tag.removePrefix("v")
        if (!Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(version)) throw IllegalArgumentException("version")
        val assets = json["assets"]?.jsonArray.orEmpty().mapNotNull { element ->
            val asset = element.jsonObject
            val name = asset["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (!name.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
            val parsed = ManifestAsset(
                name = name,
                size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0,
                sha256 = asset["sha256"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) },
            )
            ReleaseAsset(parsed.name, downloadUrl(tag, parsed.name), parsed.size, parsed.sha256)
        }
        return AppRelease(
            version = version,
            tag = tag,
            notes = json["notes"]?.jsonPrimitive?.contentOrNull ?: json["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            name = json["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: tag,
            publishedAt = json["published_at"]?.jsonPrimitive?.contentOrNull,
            assets = assets,
            source = source,
        )
    }

    private fun downloadUrl(tag: String, asset: String): String =
        source.releasesUrl + "/download/" + encode(tag) + "/" + encode(asset)

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
