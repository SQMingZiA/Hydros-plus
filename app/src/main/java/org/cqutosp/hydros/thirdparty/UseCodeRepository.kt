package org.cqutosp.hydros.thirdparty

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.cqutosp.hydros.data.network.ApiFailure
import org.cqutosp.hydros.data.network.ApiResponse
import org.cqutosp.hydros.data.network.CampusTransport
import org.cqutosp.hydros.data.network.Credentials
import org.cqutosp.hydros.data.network.displayMessage
import org.cqutosp.hydros.data.network.isSessionExpiry
import org.cqutosp.hydros.data.network.number
import org.cqutosp.hydros.data.network.ok
import org.cqutosp.hydros.data.network.requireSuccess
import org.cqutosp.hydros.data.network.text

/** 使用码当前状态；[useCode] 为 null 表示还没有生效的使用码。 */
data class UseCodeState(
    val useCode: String?,
    val enabled: Boolean,
    val resetAvailability: Int?,
)

/** 「换一个」得到的候选码，此时尚未生效；[remainTimes] 为今日剩余换码次数。 */
data class GeneratedUseCode(
    val useCode: String?,
    val remainTimes: Int?,
)

/**
 * 使用码端口（移植自 muguang）：
 * GET  account/useCode/new                 查询当前使用码
 * POST account/useCode/new/status/update   开启/关闭（表单 useCodeStatus=1/0）
 * POST account/useCode/new/generate        换一个（仅生成候选码，不生效，每天限 20 次，3 分钟内不领取作废）
 * POST account/useCode/new/set             确定领取（表单 useCode，唯一真正替换当前使用码的步骤）
 */
class UseCodeRepository(private val transport: CampusTransport) {

    suspend fun status(auth: Credentials): UseCodeState =
        transport.get(PATH, auth = auth).readState()

    suspend fun setEnabled(auth: Credentials, enabled: Boolean): UseCodeState {
        transport.post(STATUS_PATH, mapOf(STATUS_FIELD to if (enabled) "1" else "0"), auth).requireSuccess()
        return status(auth)
    }

    suspend fun generate(auth: Credentials): GeneratedUseCode {
        val response = transport.post(GENERATE_PATH, auth = auth)
        val message = response.displayMessage
        val expired = isSessionExpiry(message)
        if (!response.ok) throw ApiFailure(response.errorCode, message ?: GENERATE_FAILURE, expired)
        val data = response.data as? JsonObject ?: throw ApiFailure(null, message ?: GENERATE_FAILURE, expired)
        val code = data.text("useCode")?.takeIf { it.isNotBlank() }
            ?: throw ApiFailure(null, message ?: GENERATE_FAILURE, expired)
        return GeneratedUseCode(useCode = code, remainTimes = data.number("remainTimes"))
    }

    suspend fun claim(auth: Credentials, code: String): UseCodeState {
        transport.post(CLAIM_PATH, mapOf("useCode" to code), auth).requireSuccess()
        return status(auth)
    }

    private fun ApiResponse<JsonElement>.readState(): UseCodeState {
        val message = displayMessage
        if (!ok) throw ApiFailure(errorCode, message ?: STATUS_FAILURE, isSessionExpiry(message))
        val data = data as? JsonObject ?: throw ApiFailure(errorCode, "服务返回缺少必要数据")
        return UseCodeState(
            useCode = data.text("useCode")?.takeIf { it.isNotBlank() },
            // 服务用 1 表示开启；缺失或 0 都按关闭处理。
            enabled = data.number("useCodeStatus") == 1,
            resetAvailability = data.number("resetAvailability"),
        )
    }

    private companion object {
        const val PATH = "account/useCode/new"
        const val STATUS_PATH = "$PATH/status/update"
        const val GENERATE_PATH = "$PATH/generate"
        const val CLAIM_PATH = "$PATH/set"
        const val STATUS_FIELD = "useCodeStatus"
        const val GENERATE_FAILURE = "换码失败，请重试"
        const val STATUS_FAILURE = "操作失败，请重试"
    }
}
