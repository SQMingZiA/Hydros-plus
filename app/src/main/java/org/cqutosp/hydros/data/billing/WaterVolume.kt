package org.cqutosp.hydros.data.billing

import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 用水量换算。
 *
 * 校园热水网关并不保证每个接口都带用水量：有的只回金额，有的给 `consumeWater`、有的给
 * `waterVolume`，单位还可能是 mL / L / m³。这里统一收敛为「毫升」（Long），
 * 金额与用水量的关系按 0.027 元/L（27 元/吨）折算，和校园热水实际计费一致。
 */

/** 与网关约定：每升 27 厘元（1 升 = 0.027 元 = 27 元/吨）。 */
private const val MILLI_YUAN_PER_LITER = 27L

private val WATER_FIELDS = listOf(
    "consumeWaterStr", "waterConsumptionStr", "waterVolumeStr", "waterStr",
    "consumeWater", "consumeWaterNum", "consumeWaterCount",
    "waterConsumption", "waterVolume", "consumeVolumeStr", "consumeVolume",
    "waterAmount", "waterNum", "waterUsed", "water", "useWater",
)

/** 把网关给的用量文本（可能带单位）解析成毫升；解析不出来返回 null。 */
fun parseWaterMilliLiters(raw: String?): Long? {
    val text = raw?.replace(" ", "")?.replace("升", "L")?.takeIf(String::isNotEmpty) ?: return null
    val lower = text.lowercase()
    val (number, factor) = when {
        lower.endsWith("ml") -> lower.dropLast(2) to BigDecimal.ONE
        lower.endsWith("m³") || lower.endsWith("m3") -> lower.dropLast(2) to BigDecimal("1000000")
        lower.endsWith("l") -> lower.dropLast(1) to BigDecimal("1000")
        else -> lower to BigDecimal("1000")
    }
    return runCatching {
        val value = BigDecimal(number.trim())
        if (value.signum() < 0) return null
        value.multiply(factor).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }.getOrNull()
}

/** 从账单 JSON 里按候选键逐个找用水量。 */
fun readWaterMilliLiters(obj: JsonObject): Long? = WATER_FIELDS.firstNotNullOfOrNull { key ->
    val element = obj[key]
    parseWaterMilliLiters((element as? JsonPrimitive)?.content)
}

/** 用消费金额反推用水量（毫升）。金额为空或非正时返回 null。 */
fun estimateWaterMilliLiters(amountMilliYuan: Long?): Long? {
    if (amountMilliYuan == null || amountMilliYuan <= 0) return null
    return runCatching {
        BigDecimal.valueOf(amountMilliYuan).multiply(BigDecimal("1000"))
            .divide(BigDecimal.valueOf(MILLI_YUAN_PER_LITER), 0, RoundingMode.HALF_UP).longValueExact()
    }.getOrNull()
}

/** 95000 → "95 L"；12700 → "12.7 L"；null → null。 */
fun formatWaterVolume(milliLiters: Long?): String? {
    if (milliLiters == null || milliLiters < 0) return null
    // 整升直接显示整数；带小数的保留一位小数，避免估算值出现 "15.556 L" 这种噪声。
    val liters = BigDecimal.valueOf(milliLiters, 3)
    val text = if (liters.stripTrailingZeros().scale() <= 1) {
        liters.stripTrailingZeros().toPlainString()
    } else {
        liters.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
    return text + " L"
}

/** 账单用水量：优先真实值，缺失时按金额估算。 */
data class WaterVolume(val milliLiters: Long, val estimated: Boolean)

fun waterVolumeOf(actualMilliLiters: Long?, amountMilliYuan: Long?): WaterVolume? {
    if (actualMilliLiters != null && actualMilliLiters > 0) return WaterVolume(actualMilliLiters, false)
    val estimated = estimateWaterMilliLiters(amountMilliYuan) ?: return null
    return WaterVolume(estimated, true)
}
