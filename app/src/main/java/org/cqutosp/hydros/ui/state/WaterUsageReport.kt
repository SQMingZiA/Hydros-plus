package org.cqutosp.hydros.ui.state

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 节水建议的语气等级，页面据此决定图标色调与文字颜色。 */
enum class WaterTipLevel { INFO, NOTICE, WARNING }

/** 一条本地推导出的节水建议，不依赖网络。 */
data class WaterTip(val title: String, val detail: String, val level: WaterTipLevel)

/** 某一天的用水汇总，供热力图使用；没有支出的天 cents = 0、sessions = 0。 */
data class WaterDayUsage(val day: Int, val cents: Long, val sessions: Int)

/** 由当月消费账单推导出的智能用水报告，全部在本地计算，不额外请求服务端。 */
data class WaterUsageReport(
    val loaded: Boolean = false,
    val month: String = "",
    val elapsedDays: Int = 0,
    val daysInMonth: Int = 0,
    val totalCents: Long = 0,
    val sessionCount: Int = 0,
    val averageCents: Long = 0,
    val dailyAverageCents: Long = 0,
    val projectionCents: Long = 0,
    val peakCents: Long = 0,
    val peakHour: Int? = null,
    val topCategory: DeviceCategory? = null,
    val firstHalfCents: Long = 0,
    val secondHalfCents: Long = 0,
    val limitCents: Long? = null,
    /** 整月逐日明细，长度等于当月天数，供日历热力图使用。 */
    val dailyUsages: List<WaterDayUsage> = emptyList(),
    /** 有支出的天数。 */
    val activeDays: Int = 0,
    /** 只按有支出天数计算的日均支出（分）。 */
    val activeDailyAverageCents: Long = 0,
    /** 单日支出最高的日期（几号），本月无支出时为空。 */
    val peakDay: Int? = null,
    val peakDayCents: Long = 0,
    /** 夜间（23:00–05:00）用水次数。 */
    val nightSessions: Int = 0,
    /** 预计本月用水次数：当前日均次数 × 当月天数。 */
    val projectedSessions: Int = 0,
    /** 预计支出相对限额的剩余额度（分），超出为负；未设限额时为空。 */
    val projectedRemainingCents: Long? = null,
    /** 节水评分，0..100，规则见 of() 内注释。 */
    val score: Int = 0,
    /** 上一个自然月的用水支出（分）。 */
    val previousMonthCents: Long = 0,
    /** 相对上月的支出变化百分比，上月无记录时为空。 */
    val monthOverMonthPercent: Int? = null,
    /** 最多 4 条节水建议；没有问题时给 1 条正向鼓励。 */
    val tips: List<WaterTip> = emptyList(),
    /** 报告月份是否为当前自然月；历史月份不展示预测类信息。 */
    val isCurrentMonth: Boolean = true,
    /** 本月累计用水量（毫升）：网关给的值优先，缺失的单按单价估算。 */
    val totalWaterMilliLiters: Long = 0,
    /** 计入用水量的记录数。 */
    val waterRecordCount: Int = 0,
    /** 其中由金额估算出用水量的记录数。 */
    val estimatedWaterRecordCount: Int = 0,
) {
    /** 本月用水量的展示文本，如 "128.4 L"；没有记录时为空。 */
    val waterText: String? get() = org.cqutosp.hydros.data.billing.formatWaterVolume(
        totalWaterMilliLiters.takeIf { waterRecordCount > 0 && it > 0 }
    )
    /** 用水量里是否包含估算值，页面据此加「含估算」。 */
    val hasEstimatedWater: Boolean get() = estimatedWaterRecordCount > 0
    /** 限额进度，0f..1f；未设置限额时为 0f。 */
    val limitProgress: Float get() {
        val limit = limitCents?.takeIf { it > 0 } ?: return 0f
        return (totalCents.toFloat() / limit).coerceIn(0f, 1f)
    }
    val limitReached: Boolean get() = limitCents?.let { it > 0 && totalCents >= it } == true
    /** 距离限额还剩多少（分），已超出为负数；未设置限额时为空。 */
    val limitRemainingCents: Long? get() = limitCents?.let { it - totalCents }
    val peakHourLabel: String? get() = peakHour?.let { "%02d:00–%02d:00".format(Locale.CHINA, it, (it + 1) % 24) }
    /** 后半月日均相对前半月日均的变化百分比；当月不足 16 天或前半月无支出时为空。 */
    val trendPercent: Int? get() = halfTrendPercent(firstHalfCents, secondHalfCents, elapsedDays)
    companion object {
        fun of(bills: List<BillUi>, month: YearMonth, limitCents: Long?, loaded: Boolean = true): WaterUsageReport {
            val usage = bills.filter { !it.recharge && it.month == month && it.amountCents != null }
            val total = usage.sumOf { it.amountCents ?: 0L }
            val count = usage.size
            val days = month.lengthOfMonth()
            val isCurrentMonth = YearMonth.from(LocalDate.now()) == month
            val elapsed = if (isCurrentMonth) LocalDate.now().dayOfMonth else days
            val dailyAverage = if (elapsed > 0) total / elapsed else 0L
            // 历史月份已过完，没有“预计”可言，预测值直接等于实际值。
            val projection = if (isCurrentMonth) dailyAverage * days else total
            val monthWord = if (isCurrentMonth) "本月" else "该月"
            val peak = usage.groupingBy { it.time.hour }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
                .firstOrNull()?.key
            val topCategory = usage.filter { it.category != null }.groupingBy { it.category!! }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<DeviceCategory, Int>> { it.value }.thenBy { it.key.ordinal })
                .firstOrNull()?.key
            val firstHalf = usage.filter { it.time.dayOfMonth <= 15 }.sumOf { it.amountCents ?: 0L }
            val secondHalf = usage.filter { it.time.dayOfMonth > 15 }.sumOf { it.amountCents ?: 0L }

            // 按天聚合到整月：没有支出的天也占一格，热力图才能“以天为计量单位”。
            val byDay = usage.groupBy { it.time.dayOfMonth }
            val dailyUsages = (1..days).map { day ->
                val items = byDay[day].orEmpty()
                WaterDayUsage(day = day, cents = items.sumOf { it.amountCents ?: 0L }, sessions = items.size)
            }
            val activeDays = dailyUsages.count { it.cents > 0 }
            val activeDailyAverage = if (activeDays > 0) total / activeDays else 0L
            val peakDayUsage = dailyUsages.filter { it.cents > 0 }
                .sortedWith(compareByDescending<WaterDayUsage> { it.cents }.thenBy { it.day })
                .firstOrNull()
            val nightSessions = usage.count { it.time.hour >= 23 || it.time.hour < 5 }
            val projectedSessions = if (!isCurrentMonth) count
            else if (elapsed > 0) (count.toDouble() / elapsed * days).roundToInt() else count
            val previousMonthCents = bills
                .filter { !it.recharge && it.month == month.minusMonths(1) && it.amountCents != null }
                .sumOf { it.amountCents ?: 0L }
            val monthOverMonth = if (previousMonthCents > 0) {
                (((total - previousMonthCents).toDouble() / previousMonthCents) * 100).roundToInt()
            } else null
            val limit = limitCents?.takeIf { it > 0 }

            // 节水评分 0..100，三项相加，全部基于本月账单本地计算：
            // 1) 40 分：日均支出相对“限额均摊到每天”的比值。比值 ≤ 0.8 满分，0.8→1.6 线性扣到 0；
            //    未设限额时无法判断额度是否宽裕，取 28 分中性基线。
            // 2) 30 分：夜间（23:00–05:00）用水占比。≤ 10% 满分，≥ 50% 归零。
            // 3) 30 分：单次金额波动（变异系数 = 标准差 / 均值）。≤ 0.25 满分，≥ 0.9 归零。
            val score = if (count == 0) 0 else {
                val dailyBudget = limit?.let { it.toDouble() / days }?.takeIf { it > 0.0 }
                val limitScore = if (dailyBudget != null) {
                    val ratio = dailyAverage / dailyBudget
                    (40 * ((1.6 - ratio) / 0.8)).coerceIn(0.0, 40.0)
                } else 28.0
                val nightRatio = nightSessions.toDouble() / count
                val nightScore = (30 * ((0.5 - nightRatio) / 0.4)).coerceIn(0.0, 30.0)
                val amounts = usage.map { (it.amountCents ?: 0L).toDouble() }
                val mean = amounts.average()
                val cv = if (mean > 0.0) {
                    sqrt(amounts.sumOf { (it - mean) * (it - mean) } / amounts.size) / mean
                } else 0.0
                val stableScore = (30 * ((0.9 - cv) / 0.65)).coerceIn(0.0, 30.0)
                (limitScore + nightScore + stableScore).roundToInt().coerceIn(0, 100)
            }

            // 用水量：网关下发的实测值优先，缺失时用金额按单价折算，并记录估算条数。
            val waterRecords = usage.mapNotNull { bill ->
                val actual = bill.waterMilliLiters?.takeIf { it > 0 }
                val value = actual ?: org.cqutosp.hydros.data.billing.estimateWaterMilliLiters(bill.amountMilli)
                value?.let { it to (actual == null) }
            }
            val totalWater = waterRecords.sumOf { it.first }
            val estimatedWater = waterRecords.count { it.second }

            val average = if (count > 0) total / count else 0L
            val peakAmount = usage.maxOfOrNull { it.amountCents ?: 0L } ?: 0L
            val trend = halfTrendPercent(firstHalf, secondHalf, elapsed)
            val problems = mutableListOf<WaterTip>()
            if (limit != null && projection > limit) {
                problems += WaterTip(
                    title = if (isCurrentMonth) "预计本月会超出限额" else "该月已超出限额",
                    detail = if (isCurrentMonth) {
                        "日均 ¥" + money(dailyAverage) + "，预计 " + money(projection) +
                            "，超出限额 ¥" + money(projection - limit) + "。"
                    } else {
                        "支出 ¥" + money(total) + "，超出限额 ¥" + money(total - limit) + "。"
                    },
                    level = WaterTipLevel.WARNING,
                )
            }
            if (count >= 3 && nightSessions >= 2 && nightSessions * 2 >= count) {
                problems += WaterTip(
                    title = "夜间用水偏多",
                    detail = monthWord + " " + nightSessions + " 次用水在 23:00 后，占 " +
                        (nightSessions * 100 / count) + "%。",
                    level = WaterTipLevel.NOTICE,
                )
            }
            if (count >= 3 && average > 0 && peakAmount.toDouble() >= average.toDouble() * 1.8) {
                problems += WaterTip(
                    title = "单次用水金额偏高",
                    detail = "单次最高 ¥" + money(peakAmount) + "，是单次均值 ¥" + money(average) + " 的 " +
                        "%.1f".format(Locale.CHINA, peakAmount.toDouble() / average) + " 倍。",
                    level = WaterTipLevel.NOTICE,
                )
            }
            if (trend != null && trend >= 30) {
                problems += WaterTip(
                    title = "后半月支出上升",
                    detail = "16 日以后日均支出比前半月高 " + trend + "%。",
                    level = WaterTipLevel.NOTICE,
                )
            }
            if (activeDays > 0 && activeDays <= (elapsed / 5).coerceAtLeast(1) && activeDailyAverage >= (dailyAverage * 2).coerceAtLeast(1L)) {
                problems += WaterTip(
                    title = "用水集中在少数几天",
                    detail = monthWord + "只有 " + activeDays + " 天产生水费，单日均 ¥" + money(activeDailyAverage) + "。",
                    level = WaterTipLevel.INFO,
                )
            }
            // 最多 4 条：优先保留问题类建议；没有问题时给 1 条正向鼓励，再补一条未设限额的提示。
            val tips = buildList {
                addAll(problems.take(4))
                if (problems.isEmpty()) {
                    add(
                        WaterTip(
                            title = "用水习惯良好",
                            detail = "日均 ¥" + money(dailyAverage) + "，夜间用水 " + nightSessions + " 次。",
                            level = WaterTipLevel.INFO,
                        )
                    )
                }
                if (limit == null && size < 4) {
                    add(
                        WaterTip(
                            title = "还没有设置月度限额",
                            detail = "设置后在接近额度时提醒。",
                            level = WaterTipLevel.INFO,
                        )
                    )
                }
            }.take(4)

            return WaterUsageReport(
                loaded = loaded, month = month.toString(), elapsedDays = elapsed, daysInMonth = days,
                totalCents = total, sessionCount = count,
                averageCents = average,
                dailyAverageCents = dailyAverage, projectionCents = projection,
                peakCents = peakAmount,
                peakHour = peak, topCategory = topCategory, firstHalfCents = firstHalf, secondHalfCents = secondHalf,
                limitCents = limit,
                dailyUsages = dailyUsages,
                activeDays = activeDays,
                activeDailyAverageCents = activeDailyAverage,
                peakDay = peakDayUsage?.day,
                peakDayCents = peakDayUsage?.cents ?: 0L,
                nightSessions = nightSessions,
                projectedSessions = projectedSessions,
                projectedRemainingCents = limit?.let { it - projection },
                score = score,
                previousMonthCents = previousMonthCents,
                monthOverMonthPercent = monthOverMonth,
                tips = tips,
                isCurrentMonth = isCurrentMonth,
                totalWaterMilliLiters = totalWater,
                waterRecordCount = waterRecords.size,
                estimatedWaterRecordCount = estimatedWater,
            )
        }
        fun empty(month: YearMonth, limitCents: Long?): WaterUsageReport {
            val days = month.lengthOfMonth()
            val elapsed = if (YearMonth.from(LocalDate.now()) == month) LocalDate.now().dayOfMonth else days
            return WaterUsageReport(loaded = false, month = month.toString(), elapsedDays = elapsed, daysInMonth = days,
                limitCents = limitCents?.takeIf { it > 0 })
        }
    }
}

/** 后半月日均相对前半月日均的变化百分比；当月不足 16 天或前半月无支出时为空。 */
private fun halfTrendPercent(firstHalfCents: Long, secondHalfCents: Long, elapsedDays: Int): Int? {
    if (firstHalfCents <= 0) return null
    val secondDays = elapsedDays - 15
    if (secondDays <= 0) return null
    val firstDaily = firstHalfCents / 15.0
    val secondDaily = secondHalfCents.toDouble() / secondDays
    return (((secondDaily - firstDaily) / firstDaily) * 100).roundToInt()
}
