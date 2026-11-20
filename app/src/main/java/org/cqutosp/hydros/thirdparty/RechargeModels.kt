package org.cqutosp.hydros.thirdparty

import java.math.BigDecimal
import java.security.MessageDigest

/**
 * 充值通道，编号与官方 payWay 一致：2=支付宝 / 26=聚合收银台 / 36=校园卡。
 * 每个学校开通的子集不同，界面只能按服务端下发的列表展示，不能写死。
 */
enum class RechargeChannel(val payWay: String, val label: String) {
    ALIPAY("2", "支付宝"),
    JUHE("26", "聚合收银台"),
    CAMPUS("36", "校园卡");

    companion object {
        /** 服务端 payWay 列表 → 本项目能真正走完的通道，保持枚举声明顺序。 */
        fun fromPayWays(payWays: List<String>?): List<RechargeChannel> {
            if (payWays.isNullOrEmpty()) return emptyList()
            val enabled = payWays.mapNotNull { it.trim().takeIf(String::isNotBlank) }.toSet()
            return entries.filter { it.payWay in enabled }
        }

        fun ofPayWay(payWay: String): RechargeChannel? =
            entries.firstOrNull { it.payWay == payWay.trim() }
    }
}

/** 充值套餐。金额已规范化成服务端要的字符串形态；bonus 为 null 表示没有赠送。 */
data class RechargePackage(val id: Long, val amount: String, val bonus: String?)

/** 下单结果：支付宝订单交给上层拉起支付，其余通道打开浏览器。 */
sealed interface RechargeOrder {
    data class Alipay(val orderInfo: String) : RechargeOrder
    data class Browser(val url: String) : RechargeOrder
}

/** 支付宝 resultStatus 的语义，与官方一致。Hydros 没有支付宝 SDK，结果由上层回传。 */
enum class AlipayResult(val code: String, val label: String) {
    SUCCESS("9000", "支付成功"),
    PROCESSING("8000", "支付处理中"),
    CANCELLED("6001", "已取消"),
    NETWORK_ERROR("6002", "网络异常"),
    FAILED("4000", "支付失败"),
    REPEAT("5000", "重复请求"),
    UNKNOWN("", "支付结果未知");

    companion object {
        fun fromCode(code: String?): AlipayResult =
            entries.firstOrNull { it.code == code?.trim() } ?: UNKNOWN
    }
}

/**
 * 下单签名，算法与官方 native 实现一致：
 * key 升序、只拼 key+value、末尾追加 [SK]，最后取 32 位小写 MD5。
 * 只有 loginCode / payWay / rechargeMoney / telPhone 四个字段参与签名，
 * 认证字段随请求体发出但不进签名。
 */
object RechargeSigner {

    /** 官方 native 里的 sk 常量，末尾等号不是笔误。 */
    const val SK = "sign-kailu="

    fun sign(params: Map<String, String?>): String {
        val raw = StringBuilder()
        for (key in params.keys.sorted()) {
            raw.append(key)
            params[key]?.let { raw.append(it) }
        }
        raw.append(SK)
        return md5Hex(raw.toString().trim())
    }

    fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/**
 * 金额规范化与校验，规则与原版完全一致：0.01 元起、10000 元封顶、最多两位小数。
 * 返回值同时是 rechargeMoney 和签名输入的一部分，必须是稳定形态。
 */
object RechargeAmounts {

    const val MIN = "0.01"
    const val MAX = "10000"

    private val MIN_VALUE = BigDecimal(MIN)
    private val MAX_VALUE = BigDecimal(MAX)
    private val PATTERN = Regex("[0-9]{1,7}([.][0-9]{1,2})?")

    /** "10.0" → "10"，"010.50" → "10.5"，非法输入返回 null。 */
    fun normalize(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (!PATTERN.matches(text)) return null
        val value = text.toBigDecimalOrNull() ?: return null
        if (value < MIN_VALUE || value > MAX_VALUE) return null
        return value.stripTrailingZeros().toPlainString()
    }
}
