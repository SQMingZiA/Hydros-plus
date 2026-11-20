package org.cqutosp.hydros

import org.cqutosp.hydros.ui.state.BillUi
import org.cqutosp.hydros.ui.state.DeviceCategory
import org.cqutosp.hydros.ui.state.WaterTipLevel
import org.cqutosp.hydros.ui.state.WaterUsageReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * 用“模拟接口数据”校验智能用水报告的本地推导：
 * 只统计当月非充值账单、逐日热力图铺满整月、预计用量、环比与节水建议。
 */
class WaterReportTest {
    /** 固定用 2026 年 3 月（31 天、非当月），elapsedDays 恒为 31，结果可重复。 */
    private val month: YearMonth = YearMonth.of(2026, 3)

    private fun bill(
        day: Int,
        hour: Int,
        cents: Long,
        category: DeviceCategory? = DeviceCategory.SHOWER,
        recharge: Boolean = false,
        at: YearMonth = month,
    ): BillUi {
        val time = at.atDay(day).atTime(hour, 15)
        return BillUi(
            id = "sim-" + at + "-" + day + "-" + hour + "-" + cents + "-" + recharge,
            title = "模拟用水",
            location = "模拟宿舍",
            category = category,
            amountMilli = cents * 10,
            time = time,
            consumeDate = time.toString(),
            recharge = recharge,
        )
    }

    @Test
    fun monthUsageOnlyCountsConsumptionBillsOfThatMonth() {
        val bills = listOf(
            bill(2, 8, 120),
            bill(2, 21, 80),
            bill(5, 7, 200),
            bill(9, 12, 50, recharge = true),
            bill(3, 9, 90, at = month.minusMonths(1)),
        )
        val report = WaterUsageReport.of(bills, month, limitCents = 300)

        assertEquals(400L, report.totalCents)
        assertEquals(3, report.sessionCount)
        assertEquals("2026-03", report.month)
        assertEquals(31, report.daysInMonth)
        assertEquals(31, report.elapsedDays)
        // 热力图铺满整月：1 号无支出占位，2 号两笔合并
        assertEquals(31, report.dailyUsages.size)
        assertEquals(0L, report.dailyUsages[0].cents)
        assertEquals(200L, report.dailyUsages[1].cents)
        assertEquals(2, report.dailyUsages[1].sessions)
        assertEquals(2, report.activeDays)
        assertEquals(200L, report.activeDailyAverageCents)
        assertEquals(2, report.peakDay)
        assertEquals(200L, report.peakDayCents)
        assertEquals(7, report.peakHour)
        assertEquals(133L, report.averageCents)
        assertEquals(12L, report.dailyAverageCents)
        // 3 月已过去，预测值直接等于实际支出（历史月不再做预测），限额余额相应为负。
        assertEquals(400L, report.projectionCents)
        assertEquals(-100L, report.projectedRemainingCents)
        assertEquals(90L, report.previousMonthCents)
        assertEquals(344, report.monthOverMonthPercent)
        assertTrue(report.limitReached)
        assertEquals(1.0, report.limitProgress.toDouble(), 0.0001)
        assertTrue(report.score in 0..100)
        assertTrue(report.tips.size in 1..4)
        assertEquals(WaterTipLevel.WARNING, report.tips.first().level)
        assertEquals("该月已超出限额", report.tips.first().title)
    }

    @Test
    fun emptyMonthStillFillsHeatmapAndPromptsForLimit() {
        val report = WaterUsageReport.of(emptyList(), month, limitCents = null)

        assertEquals(0L, report.totalCents)
        assertEquals(0, report.sessionCount)
        assertEquals(31, report.dailyUsages.size)
        assertTrue(report.dailyUsages.all { it.cents == 0L && it.sessions == 0 })
        assertNull(report.monthOverMonthPercent)
        assertNull(report.peakDay)
        assertEquals(0.0, report.limitProgress.toDouble(), 0.0001)
        assertEquals(0, report.score)
        assertEquals(listOf("用水习惯良好", "还没有设置月度限额"), report.tips.map { it.title })
    }

    @Test
    fun halfMonthTrendNightSessionsAndCategoryAreDerived() {
        val bills = listOf(
            bill(10, 10, 300, category = DeviceCategory.SHOWER),
            bill(20, 23, 300, category = DeviceCategory.DRINK),
            bill(21, 2, 300, category = DeviceCategory.DRINK),
        )
        val report = WaterUsageReport.of(bills, month, limitCents = null)

        assertEquals(900L, report.totalCents)
        assertEquals(300L, report.firstHalfCents)
        assertEquals(600L, report.secondHalfCents)
        // 前半月日均 20 分、后半 16 天日均 37.5 分 → 约 +88%
        assertEquals(88, report.trendPercent)
        assertEquals(2, report.nightSessions)
        assertEquals(DeviceCategory.DRINK, report.topCategory)
        assertEquals(2, report.peakHour)
    }

    @Test
    fun loadedFlagPassesThroughForPlaceholderReport() {
        val loading = WaterUsageReport.of(emptyList(), month, limitCents = 0, loaded = false)
        assertEquals(false, loading.loaded)
        assertEquals("2026-03", loading.month)
        assertNull(loading.limitCents)
        assertNotNull(WaterUsageReport.empty(month, 5_000).month)
    }
}
