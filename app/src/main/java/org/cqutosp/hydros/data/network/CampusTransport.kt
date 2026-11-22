package org.cqutosp.hydros.data.network

import org.cqutosp.hydros.data.feedback.*
import org.cqutosp.hydros.ui.state.HydrosLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.math.BigDecimal

@Serializable data class Credentials(val loginCode: String, val userId: String, val accountId: String, val projectId: String, val phone: String) {
    val key get() = "$projectId:$accountId:$userId"
    fun fields() = mapOf("loginCode" to loginCode, "userId" to userId, "accountId" to accountId,
        "projectId" to projectId, "telephone" to phone, "telPhone" to phone, "phoneSystem" to "android", "version" to "6.5.28")
    fun headers() = mapOf("Config-Project" to projectId, "Config-Keys" to
        "module_list,advertise_type,question_list,service_phone_list,banner_list_app,activity_list_app,aliCard_popup_config")
}
open class ApiFailure(val code: Int?, override val message: String, val expired: Boolean = false) : Exception(message)
/** Only an explicit rejection before a potentially successful command permits a channel change. */
class CloudDeviceOffline : ApiFailure(306, "设备无法通过云端开启")
/** 设备查询结果显示离线：设备不在线时既不能进入可用状态，也不能开始计费。 */
class DeviceOffline : ApiFailure(306, "设备当前离线，暂时无法开始使用，请稍后重试")
/** 一次会话探测的结论：有效、确认失效，或暂时无法判断（网络/网关异常）。 */
enum class SessionVerdict { VALID, EXPIRED, UNKNOWN }
interface CampusTransport {
    suspend fun get(path: String, fields: Map<String, String> = emptyMap(), auth: Credentials? = null): ApiResponse<JsonElement>
    suspend fun post(path: String, fields: Map<String, String> = emptyMap(), auth: Credentials? = null): ApiResponse<JsonElement>
    /** 轻量鉴权探测：只判断凭证是否仍被服务端接受，绝不再触发"登录已失效"回调。 */
    suspend fun probe(path: String, auth: Credentials): ApiResponse<JsonElement> = get(path, auth = auth)
}
class HttpCampusTransport(private val api: CampusApi, private val isCurrent: (Credentials) -> Boolean = { true },
    private val recovery: NetworkRecovery? = null,
    private val verifySession: (suspend (Credentials) -> SessionVerdict)? = null,
    private val onExpired: suspend (Credentials) -> Unit) : CampusTransport {
    override suspend fun get(path: String, fields: Map<String, String>, auth: Credentials?) = call(false, path, fields, auth)
    override suspend fun post(path: String, fields: Map<String, String>, auth: Credentials?) = call(true, path, fields, auth)
    override suspend fun probe(path: String, auth: Credentials) = call(false, path, emptyMap(), auth, observeExpiry = false)
    private suspend fun call(post: Boolean, path: String, fields: Map<String, String>, auth: Credentials?,
        observeExpiry: Boolean = true): ApiResponse<JsonElement> {
        try {
        if (auth != null && !isCurrent(auth)) throw ApiFailure(null, "登录状态已变化", true)
        // Business telephone overrides authentication telephone only for SMS to a new number.
        val params = (auth?.fields() ?: emptyMap()) + fields
        suspend fun execute(budget: QueryRetryBudget?): retrofit2.Response<ApiResponse<JsonElement>> {
            val request: suspend (CampusApi) -> retrofit2.Response<ApiResponse<JsonElement>> = { selected ->
                if (auth != null && !isCurrent(auth)) throw ApiFailure(null, "登录状态已变化", true)
                val send: suspend () -> retrofit2.Response<ApiResponse<JsonElement>> = {
                    val response = if (post) selected.post(path, params, auth?.headers() ?: emptyMap())
                        else selected.get(path, params, auth?.headers() ?: emptyMap())
                    if (budget != null && transientHttpStatus(response.code())) {
                        response.errorBody()?.close()
                        throw QueryHttpFailure(response.code(), response.headers()["Retry-After"]?.toLongOrNull()
                            ?.takeIf { it in 0..3 }?.times(1_000)
                            ?: response.headers()["Retry-After"]?.let { Long.MAX_VALUE })
                    }
                    response
                }
                if (budget == null) send() else budget.attempt(send)
            }
            return recovery?.execute(api, path, post, request,
                canFallback = { budget?.available != false },
                canArmDeadline = { budget?.fallbackRoom != false },
                onRouteExhausted = { budget?.routeExhausted = true }) ?: request(api)
        }
        val response = if (isReadOnlyCampusRequest(path, post)) retryReadQuery { execute(it) } else execute(null)
        if (auth != null && !isCurrent(auth)) throw ApiFailure(null, "登录状态已变化", true)
        if (auth != null && response.code() in listOf(401, 403))
            throw expiryFailure(path, auth, response.code(), "登录已失效，请重新登录", observeExpiry)
        if (!response.isSuccessful) throw AppFailure(feedbackOperationFor(path), FailureStage.RESPONSE, FailureReason.REJECTED, ApiFailure(response.code(), "HTTP rejection"))
        val body = response.body() ?: throw ApiFailure(null, "服务返回为空")
        if (auth != null && !body.ok && isSessionExpiry(body.displayMessage))
            throw expiryFailure(path, auth, body.errorCode, body.displayMessage, observeExpiry)
        return body
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error is QueryHttpFailure)
                throw AppFailure(feedbackOperationFor(path), FailureStage.RESPONSE, FailureReason.REJECTED, error)
            if (error is java.io.IOException || error is kotlinx.serialization.SerializationException)
                throw classifyFailure(error, feedbackOperationFor(path), FailureStage.NETWORK)
            throw error
        }
    }

    /**
     * 状态码/文案疑似表示登录失效时先做二次探测再决定是否登出：
     * 只有确认失效才回调 onExpired；探测成功（误报）或网络异常则保留本地凭证并抛出普通错误，
     * 避免服务端一条业务文案或网关错误页就把用户永久踢出登录。
     */
    private suspend fun expiryFailure(path: String, auth: Credentials, code: Int?, serverMessage: String?,
        observeExpiry: Boolean): ApiFailure {
        if (!observeExpiry) return ApiFailure(code, serverMessage ?: "登录已失效，请重新登录", true)
        return when (verifySession?.invoke(auth)) {
            SessionVerdict.VALID -> {
                HydrosLog.log("auth", "疑似会话过期但探测成功（$path，code=$code），判定为误报，保留登录状态")
                ApiFailure(null, "服务暂时不可用，请稍后重试", false)
            }
            SessionVerdict.UNKNOWN -> {
                HydrosLog.log("auth", "疑似会话过期但探测未完成（$path，code=$code），保留登录状态并按网络异常处理")
                ApiFailure(null, "网络异常，请稍后重试", false)
            }
            else -> {
                HydrosLog.log("auth", "探测确认会话已失效（$path，code=$code），清除本地登录状态")
                onExpired(auth)
                ApiFailure(code, serverMessage ?: "登录已失效，请重新登录", true)
            }
        }
    }

    private fun feedbackOperationFor(path: String) = when {
        path == "user/verification/code/get" -> FailureOperation.CODE
        path.startsWith("user/phone") || path.startsWith("user/password") -> FailureOperation.ACCOUNT
        path.startsWith("user/") -> FailureOperation.LOGIN
        path == "account/wallet" -> FailureOperation.WALLET
        path.endsWith("bill/detail") -> FailureOperation.DETAIL
        path.contains("bill") -> FailureOperation.BILLS
        path.startsWith("device/") -> FailureOperation.SCAN
        path.contains("query/rateOrder/using") -> FailureOperation.DEVICE
        path.contains("downRate") -> FailureOperation.START
        path.startsWith("order/") -> FailureOperation.FINISH
        else -> FailureOperation.PROFILE
    }
}
val ApiResponse<*>.ok get() = success && (errorCode == null || errorCode == 0)
fun ApiResponse<JsonElement>.requireData(): JsonObject { requireSuccess(); return data as? JsonObject ?: throw ApiFailure(errorCode, "服务返回缺少必要数据") }
val ApiResponse<*>.displayMessage get() = errorMessage?.takeIf(String::isNotBlank) ?: msg
fun ApiResponse<*>.requireSuccess() { if (!ok) throw ApiFailure(errorCode, displayMessage ?: "操作未完成") }
fun isSessionExpiry(message: String?): Boolean = message?.let {
    Regex("未登录|请.*登录|登录.*(失效|过期|超时|其他|异常)|账号.*(其他|别处)|token.*(失效|过期)|认证.*(失效|过期|失败)", RegexOption.IGNORE_CASE).containsMatchIn(it)
} ?: false
fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull?.takeUnless { it == "null" }
fun JsonObject.flag(key: String): Boolean? = (get(key) as? JsonPrimitive)?.booleanOrNull
fun JsonObject.number(key: String): Int? = text(key)?.toIntOrNull()
fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject
fun yuanMilli(value: String?): Long? = value?.toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ZERO }?.let {
    runCatching { it.movePointRight(3).longValueExact() }.getOrNull()
}
