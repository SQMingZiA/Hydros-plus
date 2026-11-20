package org.cqutosp.hydros.thirdparty

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.cqutosp.hydros.data.network.ApiFailure
import org.cqutosp.hydros.data.network.ApiResponse
import org.cqutosp.hydros.data.network.CampusTransport
import org.cqutosp.hydros.data.network.Credentials
import org.cqutosp.hydros.data.network.displayMessage
import org.cqutosp.hydros.data.network.isSessionExpiry
import org.cqutosp.hydros.data.network.ok
import org.cqutosp.hydros.data.network.text
import java.math.BigDecimal
import java.math.RoundingMode

/** 收银台 / 校园卡下单响应里可能出现的 URL 字段，按顺序取第一个非空的。 */
private val PAY_URL_KEYS = listOf("cashierUrl", "campusRechargeUrl", "payUrl", "url", "jumpUrl", "h5Url")

/** 钱包余额字段的候选名，服务端按学校换过字段名。 */
private val BALANCE_KEYS = listOf("money", "balance", "amount")

private fun centsOf(value: String?): Long? = value?.toBigDecimalOrNull()
    ?.takeIf { it >= BigDecimal.ZERO }
    ?.setScale(2, RoundingMode.DOWN)
    ?.movePointRight(2)
    ?.longValueExact()

/**
 * 充值数据层。三个只读接口 + 三个下单接口，全部基于 [CampusTransport]，不新增依赖。
 * 下单只发服务端下发的套餐金额，本地绝不改写余额。
 */
class RechargeRepository(private val transport: CampusTransport) {

    /** 本校开通的充值通道（GET settlement/recharge/pay/mode）。 */
    suspend fun payChannels(auth: Credentials): List<RechargeChannel> {
        val response = transport.get("settlement/recharge/pay/mode", auth = auth)
        response.requireRechargeSuccess()
        val data = response.data as? JsonObject ?: throw ApiFailure(null, "服务端未返回支付方式")
        val rows = data["payWay"] as? JsonArray ?: return emptyList()
        val codes = rows.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
        return RechargeChannel.fromPayWays(codes)
    }

    /** 充值套餐（GET settlement/recharge/package/list）。金额非法的套餐直接跳过。 */
    suspend fun packages(auth: Credentials): List<RechargePackage> {
        val response = transport.get("settlement/recharge/package/list", auth = auth)
        response.requireRechargeSuccess()
        val rows = response.data as? JsonArray ?: throw ApiFailure(null, "服务端未返回充值套餐")
        return rows.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val id = row.text("typeId")?.toLongOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            val amount = RechargeAmounts.normalize(row.text("amount")) ?: return@mapNotNull null
            val bonus = RechargeAmounts.normalize(row.text("givenMoney"))
            RechargePackage(id, amount, bonus)
        }.distinctBy { it.id }
    }

    /**
     * 下单。签名只覆盖 loginCode / payWay / rechargeMoney / telPhone；
     * 请求体额外带账号字段（官方基类注入的那一组），少一个服务端就回 8。
     */
    suspend fun createOrder(auth: Credentials, channel: RechargeChannel, amount: String): RechargeOrder {
        val money = RechargeAmounts.normalize(amount) ?: throw ApiFailure(null, "请选择有效的充值金额")
        val signature = RechargeSigner.sign(
            mapOf(
                "loginCode" to auth.loginCode,
                "payWay" to channel.payWay,
                "rechargeMoney" to money,
                "telPhone" to auth.phone,
            )
        )
        val fields = auth.fields() + mapOf(
            "payWay" to channel.payWay,
            "rechargeMoney" to money,
            "signature" to signature,
        )
        val path = when (channel) {
            RechargeChannel.ALIPAY -> "settlement/alipay/recharge/balance"
            RechargeChannel.JUHE -> "settlement/juhe/recharge/balance"
            RechargeChannel.CAMPUS -> "settlement/campus/jump/address"
        }
        val response = transport.post(path, fields, auth)
        response.requireRechargeSuccess()
        return parseOrder(response.data)
    }

    /**
     * 重新拉服务端余额（GET account/wallet），返回分。
     * 解析不出来时返回 null，让界面提示稍后再试，绝不本地加减。
     */
    suspend fun refreshBalance(auth: Credentials): Long? {
        val response = transport.get("account/wallet", auth = auth)
        response.requireRechargeSuccess()
        val wallet = response.data as? JsonObject ?: return null
        return BALANCE_KEYS.firstNotNullOfOrNull { key -> centsOf(wallet.text(key)) }
    }

    /**
     * 下单响应 data 有三种形态：支付宝是裸 orderInfo 字符串；
     * 聚合 / 校园卡是对象（cashierUrl / campusRechargeUrl 等）；
     * 个别学校也会把 URL 直接塞进字符串。先看形态，再逐个候选键找地址。
     */
    private fun parseOrder(data: JsonElement?): RechargeOrder = when (data) {
        null, is JsonNull -> throw ApiFailure(null, "服务端未返回支付信息")
        is JsonPrimitive -> {
            val value = data.contentOrNull?.trim().orEmpty()
            when {
                value.isEmpty() -> throw ApiFailure(null, "服务端未返回支付信息")
                value.startsWith("http", ignoreCase = true) -> RechargeOrder.Browser(value)
                else -> RechargeOrder.Alipay(value)
            }
        }
        is JsonObject -> {
            val url = PAY_URL_KEYS.firstNotNullOfOrNull { key -> data.text(key)?.takeIf(String::isNotBlank) }
            val orderInfo = data.text("orderInfo")?.takeIf(String::isNotBlank)
            when {
                url != null -> RechargeOrder.Browser(url)
                orderInfo != null -> RechargeOrder.Alipay(orderInfo)
                else -> throw ApiFailure(null, "服务端未返回支付订单")
            }
        }
        else -> throw ApiFailure(null, "服务端未返回支付信息")
    }

    /** success=false 时抛 ApiFailure，登录过期打 expired=true（用 Hydros 统一判定）。 */
    private fun ApiResponse<*>.requireRechargeSuccess() {
        if (ok) return
        val message = displayMessage?.takeIf(String::isNotBlank) ?: "充值请求未完成"
        throw ApiFailure(errorCode, message, isSessionExpiry(message))
    }
}
