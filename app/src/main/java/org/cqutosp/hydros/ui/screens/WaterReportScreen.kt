package org.cqutosp.hydros.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.ui.components.*
import org.cqutosp.hydros.ui.state.WaterDayUsage
import org.cqutosp.hydros.ui.state.WaterTip
import org.cqutosp.hydros.ui.state.WaterTipLevel
import org.cqutosp.hydros.ui.state.WaterUsageReport
import org.cqutosp.hydros.ui.state.money
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * 智能用水报告详情：白底平铺排版，不用卡片容器。
 * 自上而下：月度支出 → 本月概览 → 按天热力图 → 预计本月 → 节水建议 → 用水限额 → 数据来源。
 */
@Composable
fun WaterReportScreen(
    report: WaterUsageReport,
    limitCents: Long?,
    onWaterLimit: (Long?) -> Unit,
    onMonth: (YearMonth) -> Unit = {},
) {
    var limitEditing by remember { mutableStateOf(false) }
    var showMonths by remember { mutableStateOf(false) }
    val selectedMonth = runCatching { YearMonth.parse(report.month) }.getOrNull()
    val currentMonth = YearMonth.now()
    val onSetLimit: () -> Unit = { limitEditing = true }
    val limit = limitCents?.takeIf { it > 0 }
    val reached = limit != null && report.limitReached
    val colors = MaterialTheme.colorScheme

    PageColumn {
        // 页头：月份切换 + 本月支出。整页只有这块用左侧主色竖条强调。
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonthStepButton(
                previous = true,
                enabled = selectedMonth != null,
                onClick = { selectedMonth?.minusMonths(1)?.let(onMonth) },
            )
            Row(
                Modifier
                    .weight(1f)
                    .clickable(enabled = selectedMonth != null) { showMonths = true }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    monthLabel(report.month),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
            }
            MonthStepButton(
                previous = false,
                enabled = selectedMonth != null && selectedMonth < currentMonth,
                onClick = { selectedMonth?.plusMonths(1)?.let(onMonth) },
            )
        }
        // 支出块：和主按钮同一种纯色主色底，白字，不再用淡色强调块。
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = colors.primary,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (report.isCurrentMonth) "本月支出" else "当月支出",
                    fontSize = 13.sp,
                    color = colors.onPrimary.copy(alpha = 0.78f),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (report.loaded) "¥ " + money(report.totalCents) else "—",
                        modifier = Modifier.weight(1f),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimary,
                        maxLines = 1,
                    )
                    if (report.loaded) {
                        val percent = report.monthOverMonthPercent
                        val label = when {
                            percent == null -> "上月无记录"
                            percent < 0 -> "环比 " + percent + "%"
                            percent > 0 -> "环比 +" + percent + "%"
                            else -> "环比 持平"
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.onPrimary.copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.onPrimary,
                            )
                        }
                    }
                }
                if (report.loaded) {
                    Text(
                        report.sessionCount.toString() + " 次用水 · 有支出 " + report.activeDays + " 天",
                        fontSize = 13.sp,
                        color = colors.onPrimary.copy(alpha = 0.78f),
                    )
                }
            }
        }
        if (!report.loaded) {
            // 账单未加载时只给一处占位，避免把 0 元当成真实数据展示。
            Section("用水分析") {
                InlineState(
                    title = "账单还在加载",
                    icon = R.drawable.ic_water,
                )
            }
            return@PageColumn
        }

        // 1) 本月概览：平铺信息行，行间 0.5dp 发丝线。
        Section("本月概览") {
            InfoRow("用水次数", report.sessionCount.toString() + " 次")
            SubtleDivider(horizontalPadding = 0.dp)
            InfoRow("日均支出", "¥ " + money(report.dailyAverageCents))
            SubtleDivider(horizontalPadding = 0.dp)
            InfoRow("有支出天数", report.activeDays.toString() + " 天 · 单日均 ¥" + money(report.activeDailyAverageCents))
            SubtleDivider(horizontalPadding = 0.dp)
            InfoRow(
                "最高单日",
                report.peakDay?.let { it.toString() + " 日 · ¥" + money(report.peakDayCents) } ?: "暂无用水"
            )
            SubtleDivider(horizontalPadding = 0.dp)
            InfoRow("最高时段", report.peakHourLabel ?: "暂无足够数据")
            SubtleDivider(horizontalPadding = 0.dp)
            InfoRow("主要用途", report.topCategory?.label ?: "暂无足够数据")
            if (report.previousMonthCents > 0) {
                SubtleDivider(horizontalPadding = 0.dp)
                InfoRow("上月支出", "¥ " + money(report.previousMonthCents))
            }
        }

        // 2) 每日用水热力图：以“天”为计量单位。
        Section("本月用水热力图") {
            WaterHeatmap(report)
        }

        // 3) 预计本月：只对当前自然月有意义，历史月份不展示。
        if (report.isCurrentMonth) {
            Section("预计本月") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricBlock("预计月末支出", "¥ " + money(report.projectionCents), Modifier.weight(1f))
                    MetricBlock("预计用水次数", report.projectedSessions.toString() + " 次", Modifier.weight(1f))
                }
                if (limit != null) {
                    val remaining = report.projectedRemainingCents ?: (limit - report.projectionCents)
                    SubtleDivider(horizontalPadding = 0.dp)
                    InfoRow("本月限额", "¥ " + money(limit))
                    SubtleDivider(horizontalPadding = 0.dp)
                    InfoRow(
                        if (remaining >= 0) "预计剩余额度" else "预计超出限额",
                        "¥ " + money(abs(remaining)),
                    )
                }
            }
        }

        // 4) 节水建议：图标直接平铺，不用圆角图标块。
        Section("节水建议") {
            if (report.sessionCount > 0) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "节水评分",
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        color = colors.onSurface,
                    )
                    StatusBadge(report.score.toString() + " / 100", primary = report.score >= 60)
                }
            }
            if (report.tips.isEmpty()) {
                InlineState(title = "本月暂无建议", icon = R.drawable.ic_water)
            } else {
                report.tips.forEachIndexed { index, tip ->
                    if (index > 0 || report.sessionCount > 0) SubtleDivider(horizontalPadding = 0.dp)
                    WaterTipRow(tip)
                }
            }
        }

        // 5) 用水限额：进度条 + 设置/调整入口。
        Section("用水限额") {
            if (limit != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "已达 " + (report.limitProgress * 100).toInt() + "%",
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        color = colors.onSurface,
                    )
                    Text(
                        if (reached) "已超限" else "剩余 ¥" + money(report.limitRemainingCents),
                        fontSize = 13.sp,
                        color = if (reached) colors.error else colors.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { report.limitProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (reached) colors.error else colors.primary,
                    trackColor = colors.surfaceVariant,
                )
                Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.CenterEnd) {
                    TextAction(label = "调整限额", onClick = onSetLimit)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "还没有设置月度限额",
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                    )
                    TextAction(label = "设置限额", onClick = onSetLimit)
                }
            }
        }

        Text(
            "数据来自本月账单，仅在本地计算",
            modifier = Modifier.padding(bottom = 8.dp),
            fontSize = 11.sp,
            color = colors.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }

    if (showMonths && selectedMonth != null) {
        BillMonthPickerSheet(
            month = selectedMonth,
            onDismiss = { showMonths = false },
            onConfirm = { picked ->
                showMonths = false
                if (picked != selectedMonth) onMonth(picked)
            },
        )
    }
    if (limitEditing) WaterLimitDialog(
        initialCents = limitCents,
        onDismiss = { limitEditing = false },
        onConfirm = { cents ->
            limitEditing = false
            onWaterLimit(cents)
        },
    )
}

/** 7 列（周一起）的日历热力图，格子里显示日号，点按可查看当天支出。 */
@Composable
private fun WaterHeatmap(report: WaterUsageReport) {
    val colors = MaterialTheme.colorScheme
    val month = runCatching { YearMonth.parse(report.month) }.getOrNull()
    val days = month?.lengthOfMonth() ?: report.daysInMonth
    val daily = if (report.dailyUsages.size == days) {
        report.dailyUsages
    } else {
        (1..days).map { WaterDayUsage(it, 0L, 0) }
    }
    val byDay = daily.associateBy { it.day }
    val levels = dailyHeatLevels(daily)
    val defaultDay = daily.lastOrNull { it.cents > 0 }?.day ?: daily.lastOrNull()?.day ?: 1
    var selectedDay by remember(daily) { mutableStateOf(defaultDay) }
    val today = LocalDate.now()
    val todayDay = if (month != null && YearMonth.from(today) == month) today.dayOfMonth else -1
    val leading = month?.atDay(1)?.dayOfWeek?.value?.minus(1) ?: 0
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        val cells: List<Int?> = buildList<Int?> {
            repeat(leading.coerceIn(0, 6)) { add(null) }
            daily.forEach { add(it.day) }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    val usage = day?.let { byDay[it] }
                    if (usage == null) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        HeatmapCell(
                            usage = usage,
                            level = levels[usage.day] ?: 0,
                            selected = selectedDay == usage.day,
                            today = usage.day == todayDay,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedDay = usage.day },
                        )
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("少", fontSize = 11.sp, color = colors.onSurfaceVariant)
            (1..4).forEach { level ->
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(heatLevelColor(level)))
            }
            Text("多", fontSize = 11.sp, color = colors.onSurfaceVariant)
        }
        Text(
            selectedDayLabel(report, byDay[selectedDay]),
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeatmapCell(
    usage: WaterDayUsage,
    level: Int,
    selected: Boolean,
    today: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(4.dp)
    val textColor = when {
        level >= 4 -> colors.onPrimary
        else -> colors.onSurface
    }
    // 选中用主色描边，今天用淡主色描边；两者同时出现时以选中为准。
    val borderColor = when {
        selected -> colors.primary
        today -> colors.primary.copy(alpha = 0.45f)
        else -> null
    }
    val borderWidth = if (selected) 2.dp else 1.5.dp
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(heatLevelColor(level))
            .then(if (borderColor != null) Modifier.border(borderWidth, borderColor, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(usage.day.toString(), fontSize = 11.sp, color = textColor)
    }
}

/** 无支出用 surfaceVariant，有支出的天按四分位取 primary 的四档深度。 */
@Composable
private fun heatLevelColor(level: Int): Color {
    val colors = MaterialTheme.colorScheme
    if (level <= 0) return colors.surfaceVariant
    return colors.primary.copy(alpha = levelAlpha(level)).compositeOver(colors.surfaceVariant)
}

private fun levelAlpha(level: Int): Float = when (level) {
    1 -> 0.18f
    2 -> 0.38f
    3 -> 0.62f
    else -> 0.9f
}

/** 按支出排名把有支出的天分成 1..4 档（0 档 = 无支出），保证月份内总有深浅层次。 */
private fun dailyHeatLevels(daily: List<WaterDayUsage>): Map<Int, Int> {
    val active = daily.filter { it.cents > 0 }.sortedBy { it.cents }
    if (active.isEmpty()) return emptyMap()
    val n = active.size
    return active.mapIndexed { index, usage ->
        usage.day to (((index + 1) * 4 + n - 1) / n).coerceIn(1, 4)
    }.toMap()
}

private fun selectedDayLabel(report: WaterUsageReport, usage: WaterDayUsage?): String {
    if (usage == null) return "点按日期查看当天用水"
    val day = monthLabel(report.month) + " " + usage.day + " 日"
    return if (usage.cents > 0) {
        day + " · 支出 ¥" + money(usage.cents) + " · " + usage.sessions + " 次用水"
    } else {
        day + " · 当天没有用水记录"
    }
}

/** 建议行：左侧线性图标（INFO 主色 / NOTICE 三级色 / WARNING 错误色）+ 标题与说明。 */
@Composable
private fun WaterTipRow(tip: WaterTip) {
    val colors = MaterialTheme.colorScheme
    val tint = when (tip.level) {
        WaterTipLevel.INFO -> colors.primary
        WaterTipLevel.NOTICE -> colors.tertiary
        WaterTipLevel.WARNING -> colors.error
    }
    val icon = when (tip.level) {
        WaterTipLevel.INFO -> R.drawable.ic_info
        WaterTipLevel.NOTICE, WaterTipLevel.WARNING -> R.drawable.ic_notifications
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Glyph(icon, modifier = Modifier.size(20.dp), tint = tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                tip.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (tip.level == WaterTipLevel.WARNING) colors.error else colors.onSurface,
            )
            Text(tip.detail, fontSize = 13.sp, color = colors.onSurfaceVariant)
        }
    }
}

/** 预计本月里的数字块：13sp 灰标签 + 22sp Bold 数值，无容器。 */
@Composable
private fun MetricBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** 报告页头部的上一月/下一月按钮；与钱包账单共用同一个月份。 */
@Composable
private fun MonthStepButton(previous: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .size(40.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(
            R.drawable.ic_chevron,
            modifier = Modifier.size(20.dp).rotate(if (previous) 180f else 0f),
            description = if (previous) "上一月" else "下一月",
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
        )
    }
}

private fun monthLabel(month: String): String {
    if (month.isBlank()) return "本月"
    val parts = month.split("-")
    return if (parts.size == 2) parts[0] + "年" + parts[1].trimStart('0') + "月" else month
}