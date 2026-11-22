package org.cqutosp.hydros.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import org.cqutosp.hydros.ui.state.WaterDayUsage
import org.cqutosp.hydros.ui.state.WaterTipLevel
import org.cqutosp.hydros.ui.state.WaterUsageReport
import org.cqutosp.hydros.ui.state.money
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 把智能用水报告画成一张 1080 宽的长图，用于保存到相册或分享。
 *
 * 之所以用原生 Canvas 而不是 Compose 截图：报告页是可滚动的长列表，屏幕外的内容拿不到，
 * 而且截图会带上系统状态栏与底栏。这里按同一套数据重绘，排版可在不同机型上保持一致。
 *
 * 排版分两遍：第一遍只在 null 画布上累加纵坐标，得到真实内容高度后再按该高度创建位图并落笔，
 * 这样底部不会被裁掉、也不会留下大片空白。两遍调用的绘制函数与光标推进完全一致。
 */
object WaterReportPoster {

    private const val WIDTH = 1080
    private const val MAX_HEIGHT = 5600
    private const val MARGIN = 72f
    private val CONTENT = WIDTH - MARGIN * 2

    private const val TEXT = 0xFF1B1B1F.toInt()
    private const val MUTED = 0xFF6D7380.toInt()
    private const val HAIRLINE = 0xFFECEFF3.toInt()
    private const val SURFACE = 0xFFF6F8FB.toInt()
    private const val WARNING = 0xFFC42B1C.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    fun render(
        report: WaterUsageReport,
        limitCents: Long?,
        primary: Int,
        generatedAt: LocalDateTime = LocalDateTime.now(),
    ): Bitmap {
        // 第一遍：空画布，只测量内容高度。
        val measure = Poster(null, primary)
        measure.layout(report, limitCents, generatedAt)
        val height = measure.height().coerceIn(1200, MAX_HEIGHT)

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(WHITE)
        // 第二遍：真实落笔，排版与第一遍逐像素一致。
        Poster(canvas, primary).layout(report, limitCents, generatedAt)
        return bitmap
    }

    /** 存入系统相册的 Pictures/Hydros，返回分享用的 content Uri；失败返回 null。 */
    fun saveToGallery(context: Context, bitmap: Bitmap): Uri? = runCatching {
        val resolver = context.contentResolver
        val name = "hydros-water-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Hydros")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri
    }.getOrNull()

    fun share(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(send, "分享用水报告")) }
    }

    private class Poster(private val canvas: Canvas?, primary: Int) {
        private val accent = (primary and 0x00FFFFFF) or 0xFF000000.toInt()
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bold = Typeface.create("sans-serif", Typeface.BOLD)
        private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
        private var y = 56f

        /** 当前内容底边的纵坐标（已含底部留白）。 */
        fun height(): Int = (y + MARGIN).toInt()

        fun layout(report: WaterUsageReport, limitCents: Long?, generatedAt: LocalDateTime) {
            header(report)
            amountCard(report)
            if (!report.loaded) {
                placeholder("账单还在加载，请稍后再试。")
                footer(generatedAt)
                return
            }
            if (report.sessionCount == 0) {
                emptyBlock(report)
                limitBlock(report, limitCents)
                footer(generatedAt)
                return
            }
            overview(report)
            heatmap(report)
            if (report.isCurrentMonth) projection(report, limitCents)
            tips(report)
            limitBlock(report, limitCents)
            footer(generatedAt)
        }

        // ---------- 文本 ----------

        private fun drawText(
            value: String,
            x: Float,
            baseline: Float,
            size: Float,
            color: Int,
            typeface: Typeface = regular,
            align: Paint.Align = Paint.Align.LEFT,
        ) {
            val target = canvas ?: return
            textPaint.reset()
            textPaint.isAntiAlias = true
            textPaint.textSize = size
            textPaint.color = color
            textPaint.typeface = typeface
            textPaint.textAlign = align
            target.drawText(value, x, baseline, textPaint)
        }

        private fun measureText(value: String, size: Float, typeface: Typeface = regular): Float {
            textPaint.reset()
            textPaint.textSize = size
            textPaint.typeface = typeface
            return textPaint.measureText(value)
        }

        /** 中文逐字符测宽换行：不依赖断词词典，任何长句都不会溢出右边界。 */
        private fun wrap(value: String, maxWidth: Float, size: Float, typeface: Typeface = regular): List<String> {
            textPaint.reset()
            textPaint.textSize = size
            textPaint.typeface = typeface
            val lines = mutableListOf<String>()
            val line = StringBuilder()
            value.forEach { ch ->
                val next = line.toString() + ch
                if (textPaint.measureText(next) > maxWidth && line.isNotEmpty()) {
                    lines += line.toString()
                    line.clear()
                    line.append(ch)
                } else {
                    line.append(ch)
                }
            }
            if (line.isNotEmpty()) lines += line.toString()
            return lines
        }

        // ---------- 图形 ----------

        private fun fillRound(rect: RectF, radius: Float, color: Int) {
            val target = canvas ?: return
            shapePaint.reset()
            shapePaint.isAntiAlias = true
            shapePaint.color = color
            target.drawRoundRect(rect, radius, radius, shapePaint)
        }

        private fun strokeRound(rect: RectF, radius: Float, width: Float, color: Int) {
            val target = canvas ?: return
            shapePaint.reset()
            shapePaint.isAntiAlias = true
            shapePaint.color = color
            shapePaint.style = Paint.Style.STROKE
            shapePaint.strokeWidth = width
            target.drawRoundRect(rect, radius, radius, shapePaint)
        }

        private fun hairline(lineY: Float) {
            val target = canvas ?: return
            shapePaint.reset()
            shapePaint.isAntiAlias = true
            shapePaint.color = HAIRLINE
            target.drawRect(MARGIN, lineY, WIDTH - MARGIN, lineY + 1.5f, shapePaint)
        }

        // ---------- 顶部 ----------

        private fun header(report: WaterUsageReport) {
            drawText("Hydros+", MARGIN, y + 42f, 44f, accent, bold)
            drawText("智能用水报告", WIDTH - MARGIN, y + 40f, 26f, MUTED, regular, Paint.Align.RIGHT)
            y += 72f
            drawText(monthLabel(report.month), MARGIN, y + 30f, 34f, MUTED, medium)
            y += 46f
            hairline(y)
            y += 44f
        }

        // ---------- 主卡片 ----------

        private fun amountCard(report: WaterUsageReport) {
            val height = 306f
            fillRound(RectF(MARGIN, y, WIDTH - MARGIN, y + height), 64f, accent)

            drawText(
                if (report.isCurrentMonth) "本月支出" else "当月支出",
                MARGIN + 40f,
                y + 64f,
                26f,
                WHITE.withAlpha(0.82f),
            )
            val amountBaseline = y + 178f
            drawText("¥", MARGIN + 40f, amountBaseline - 20f, 40f, WHITE, medium)
            val symbolWidth = measureText("¥", 40f, medium) + 14f
            drawText(
                if (report.loaded) money(report.totalCents) else "—",
                MARGIN + 40f + symbolWidth,
                amountBaseline,
                84f,
                WHITE,
                bold,
            )

            val percent = report.monthOverMonthPercent
            val badge = when {
                !report.loaded -> null
                percent == null -> "上月无记录"
                percent < 0 -> "环比 " + percent + "%"
                percent > 0 -> "环比 +" + percent + "%"
                else -> "环比 持平"
            }
            if (badge != null) badgePill(badge)

            if (report.loaded) {
                drawText(
                    report.sessionCount.toString() + " 次用水 · 有支出 " + report.activeDays + " 天",
                    MARGIN + 40f,
                    y + 258f,
                    26f,
                    WHITE.withAlpha(0.82f),
                )
            }
            y += height + 26f
        }

        private fun badgePill(value: String) {
            val textSize = 24f
            val padH = 22f
            val pillHeight = 56f
            val pillWidth = measureText(value, textSize, medium) + padH * 2
            val right = WIDTH - MARGIN - 36f
            val top = y + 36f
            val rect = RectF(right - pillWidth, top, right, top + pillHeight)
            fillRound(rect, pillHeight / 2f, WHITE.withAlpha(0.18f))
            drawText(value, rect.centerX(), rect.centerY() + 8.5f, textSize, WHITE, medium, Paint.Align.CENTER)
        }

        // ---------- 区块标题 ----------

        private fun section(title: String) {
            y += 42f
            drawText(title, MARGIN, y + 30f, 30f, TEXT, bold)
            y += 54f
        }

        // ---------- 本月概览 ----------

        private fun overview(report: WaterUsageReport) {
            section("本月概览")
            val entries = buildList {
                add("用水次数" to report.sessionCount.toString() + " 次")
                report.waterText?.let { water -> add("用水量" to (water + (if (report.hasEstimatedWater) "（含估算）" else ""))) }
                add("日均支出" to "¥ " + money(report.dailyAverageCents))
                add("有支出天数" to report.activeDays.toString() + " 天 · 单日均 ¥ " + money(report.activeDailyAverageCents))
                add(
                    "最高单日" to (report.peakDay?.let { it.toString() + " 日 · ¥ " + money(report.peakDayCents) } ?: "暂无用水"),
                )
                add("最高时段" to (report.peakHourLabel ?: "暂无足够数据"))
                add("主要用途" to (report.topCategory?.label ?: "暂无足够数据"))
                add("上月支出" to (if (report.previousMonthCents > 0) "¥ " + money(report.previousMonthCents) else "上月无记录"))
            }
            entries.forEachIndexed { index, entry ->
                metricRow(entry.first, entry.second)
                if (index < entries.lastIndex) hairline(y - 1f)
            }
        }

        private fun metricRow(label: String, value: String, valueColor: Int = TEXT) {
            val labelWidth = measureText(label, 26f)
            val maxValueWidth = CONTENT - labelWidth - 48f
            val lines = wrap(value, maxValueWidth, 30f, bold)
            val lineHeight = 40f
            val rowHeight = (64f + (lines.size - 1) * lineHeight).coerceAtLeast(64f)
            drawText(label, MARGIN, y + 38f, 26f, MUTED)
            lines.forEachIndexed { index, line ->
                drawText(
                    line,
                    WIDTH - MARGIN,
                    y + 39f + index * lineHeight,
                    30f,
                    valueColor,
                    bold,
                    Paint.Align.RIGHT,
                )
            }
            y += rowHeight
        }

        // ---------- 热力图 ----------

        private fun heatmap(report: WaterUsageReport) {
            section("本月用水热力图")
            val month = runCatching { YearMonth.parse(report.month) }.getOrNull()
            val days = month?.lengthOfMonth() ?: report.daysInMonth
            val daily = if (report.dailyUsages.size == days) {
                report.dailyUsages
            } else {
                (1..days).map { WaterDayUsage(day = it, cents = 0L, sessions = 0) }
            }
            val sessionsByDay = daily.associate { it.day to it.sessions }
            val levels = heatLevels(daily)
            val gutter = 12f
            val cell = (CONTENT - gutter * 6) / 7f

            val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
            weekdays.forEachIndexed { index, label ->
                drawText(
                    label,
                    MARGIN + index * (cell + gutter) + cell / 2f,
                    y + 26f,
                    24f,
                    MUTED,
                    regular,
                    Paint.Align.CENTER,
                )
            }
            y += 42f

            val leading = month?.atDay(1)?.dayOfWeek?.value?.minus(1) ?: 0
            val today = if (report.isCurrentMonth) LocalDate.now().dayOfMonth else -1
            val cells = buildList<Int?> {
                repeat(leading.coerceIn(0, 6)) { add(null) }
                daily.forEach { add(it.day) }
            }
            cells.chunked(7).forEach { week ->
                week.forEachIndexed { index, day ->
                    if (day == null) return@forEachIndexed
                    val left = MARGIN + index * (cell + gutter)
                    val rect = RectF(left, y, left + cell, y + cell)
                    val level = levels[day] ?: 0
                    fillRound(rect, 18f, if (level == 0) SURFACE else heatColor(level))
                    if (day == today) {
                        strokeRound(
                            RectF(left + 2f, y + 2f, left + cell - 2f, y + cell - 2f),
                            16f,
                            2.5f,
                            if (level >= 3) WHITE else accent,
                        )
                    }
                    drawText(
                        day.toString(),
                        left + cell / 2f,
                        y + cell / 2f + 9f,
                        26f,
                        when {
                            level >= 3 -> WHITE
                            level == 0 -> MUTED
                            else -> TEXT
                        },
                        regular,
                        Paint.Align.CENTER,
                    )
                    // 有支出的格子右下角标次数，让热力图不止有颜色。
                    val sessions = sessionsByDay[day] ?: 0
                    if (sessions > 1) {
                        drawText(
                            sessions.toString(),
                            left + cell - 12f,
                            y + cell - 12f,
                            18f,
                            if (level >= 3) WHITE.withAlpha(0.9f) else accent,
                            medium,
                            Paint.Align.RIGHT,
                        )
                    }
                }
                y += cell + gutter
            }
            y += 4f
            legend()
        }

        private fun legend() {
            drawText("少", MARGIN, y + 24f, 24f, MUTED)
            var x = MARGIN + measureText("少", 24f) + 14f
            for (level in 1..4) {
                fillRound(RectF(x, y + 2f, x + 26f, y + 28f), 7f, heatColor(level))
                x += 36f
            }
            drawText("多", x + 2f, y + 24f, 24f, MUTED)
            drawText("颜色越深表示当天支出越高", WIDTH - MARGIN, y + 24f, 24f, MUTED, regular, Paint.Align.RIGHT)
            y += 46f
        }

        // ---------- 预计本月 ----------

        private fun projection(report: WaterUsageReport, limitCents: Long?) {
            section("预计本月")
            val column = (CONTENT - 40f) / 2f
            metricBlock(MARGIN, "预计月末支出", "¥ " + money(report.projectionCents))
            metricBlock(MARGIN + column + 40f, "预计用水次数", report.projectedSessions.toString() + " 次")
            y += 108f

            val limit = limitCents?.takeIf { it > 0 }
            detailRow("本月限额", limit?.let { "¥ " + money(it) } ?: "未设置限额")
            hairline(y - 1f)
            if (limit != null) {
                val remaining = report.projectedRemainingCents ?: (limit - report.projectionCents)
                if (remaining >= 0) {
                    detailRow("预计剩余额度", "¥ " + money(remaining))
                } else {
                    detailRow("预计超出限额", "¥ " + money(-remaining), WARNING)
                }
                y += 12f
                progressBar(
                    ratio = (report.projectionCents.toFloat() / limit).coerceIn(0f, 1f),
                    color = if (remaining < 0) WARNING else accent,
                )
            }
            y += 34f
        }

        private fun metricBlock(x: Float, label: String, value: String) {
            drawText(label, x, y + 28f, 26f, MUTED)
            drawText(value, x, y + 88f, 40f, TEXT, bold)
        }

        private fun detailRow(label: String, value: String, valueColor: Int = TEXT) {
            drawText(label, MARGIN, y + 32f, 26f, MUTED)
            drawText(value, WIDTH - MARGIN, y + 34f, 30f, valueColor, bold, Paint.Align.RIGHT)
            y += 60f
        }

        // ---------- 节水建议 ----------

        private fun tips(report: WaterUsageReport) {
            section("节水建议")
            drawText("节水评分", MARGIN, y + 30f, 26f, MUTED)
            val suffix = "/ 100"
            val suffixWidth = measureText(suffix, 24f)
            drawText(
                report.score.toString(),
                WIDTH - MARGIN - suffixWidth - 10f,
                y + 40f,
                38f,
                accent,
                bold,
                Paint.Align.RIGHT,
            )
            drawText(suffix, WIDTH - MARGIN, y + 40f, 24f, MUTED, regular, Paint.Align.RIGHT)
            y += 66f

            val tips = report.tips.take(2)
            if (tips.isEmpty()) {
                drawText("本月暂无建议", MARGIN, y + 28f, 26f, MUTED)
                y += 50f
                return
            }
            tips.forEach { tip ->
                val color = if (tip.level == WaterTipLevel.WARNING) WARNING else TEXT
                drawText(tip.title, MARGIN, y + 30f, 30f, color, bold)
                y += 48f
                wrap(tip.detail, CONTENT, 26f, regular).forEach { line ->
                    drawText(line, MARGIN, y + 28f, 26f, MUTED)
                    y += 40f
                }
                y += 26f
            }
        }

        // ---------- 用水限额 ----------

        private fun limitBlock(report: WaterUsageReport, limitCents: Long?) {
            section("用水限额")
            val limit = limitCents?.takeIf { it > 0 }
            if (limit == null) {
                drawText("未设置限额", MARGIN, y + 32f, 30f, TEXT, medium)
                y += 56f
                wrap("在报告页设置月度限额后，接近额度时会收到提醒。", CONTENT, 26f).forEach { line ->
                    drawText(line, MARGIN, y + 28f, 26f, MUTED)
                    y += 40f
                }
                y += 10f
                return
            }
            val remaining = report.limitRemainingCents ?: (limit - report.totalCents)
            drawText("已用 " + (report.limitProgress * 100).toInt() + "%", MARGIN, y + 32f, 26f, MUTED)
            drawText(
                if (report.limitReached) "已超限 ¥ " + money(-remaining) else "剩余 ¥ " + money(remaining),
                WIDTH - MARGIN,
                y + 34f,
                30f,
                if (report.limitReached) WARNING else TEXT,
                bold,
                Paint.Align.RIGHT,
            )
            y += 62f
            progressBar(report.limitProgress, if (report.limitReached) WARNING else accent)
            drawText("限额 ¥ " + money(limit), MARGIN, y + 26f, 24f, MUTED)
            drawText(
                "已用 ¥ " + money(report.totalCents),
                WIDTH - MARGIN,
                y + 26f,
                24f,
                MUTED,
                regular,
                Paint.Align.RIGHT,
            )
            y += 44f
        }

        private fun progressBar(ratio: Float, color: Int) {
            val height = 14f
            fillRound(RectF(MARGIN, y, WIDTH - MARGIN, y + height), height / 2f, SURFACE)
            val width = CONTENT * ratio.coerceIn(0f, 1f)
            if (width > 0f) {
                fillRound(RectF(MARGIN, y, MARGIN + width, y + height), height / 2f, color)
            }
            y += height + 40f
        }

        // ---------- 空状态 / 页脚 ----------

        private fun emptyBlock(report: WaterUsageReport) {
            val height = 188f
            fillRound(RectF(MARGIN, y, WIDTH - MARGIN, y + height), 28f, SURFACE)
            drawText("本月还没有用水记录", WIDTH / 2f, y + 84f, 30f, TEXT, bold, Paint.Align.CENTER)
            drawText(
                monthLabel(report.month) + " · 开始用水后这里会生成完整的用水分析",
                WIDTH / 2f,
                y + 132f,
                24f,
                MUTED,
                regular,
                Paint.Align.CENTER,
            )
            y += height + 40f
        }

        private fun placeholder(value: String) {
            val height = 132f
            fillRound(RectF(MARGIN, y, WIDTH - MARGIN, y + height), 28f, SURFACE)
            drawText(value, WIDTH / 2f, y + 76f, 28f, MUTED, regular, Paint.Align.CENTER)
            y += height + 40f
        }

        private fun footer(generatedAt: LocalDateTime) {
            y += 16f
            hairline(y)
            y += 44f
            drawText(
                "数据来自账单，仅在本地计算 · 生成于 " +
                    generatedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                MARGIN,
                y + 26f,
                24f,
                MUTED,
            )
            y += 54f
            drawText(
                "Hydros+ · 校园热水 · 扫码即用",
                WIDTH / 2f,
                y + 26f,
                24f,
                MUTED.withAlpha(0.78f),
                regular,
                Paint.Align.CENTER,
            )
            y += 56f
        }

        // ---------- 颜色 ----------

        private fun heatColor(level: Int): Int = mix(
            WHITE,
            accent,
            when (level) {
                1 -> 0.16f
                2 -> 0.34f
                3 -> 0.58f
                else -> 0.84f
            },
        )

        private fun mix(from: Int, to: Int, ratio: Float): Int {
            val r = ((from shr 16 and 0xFF) * (1 - ratio) + (to shr 16 and 0xFF) * ratio).toInt()
            val g = ((from shr 8 and 0xFF) * (1 - ratio) + (to shr 8 and 0xFF) * ratio).toInt()
            val b = ((from and 0xFF) * (1 - ratio) + (to and 0xFF) * ratio).toInt()
            return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }

        private fun Int.withAlpha(alpha: Float): Int {
            val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
            return (a shl 24) or (this and 0x00FFFFFF)
        }

        // ---------- 等级 ----------

        /** 有支出天按金额四分位分档：同金额同档，全部相等时统一取中间档。 */
        private fun heatLevels(daily: List<WaterDayUsage>): Map<Int, Int> {
            val active = daily.filter { it.cents > 0 }
            if (active.isEmpty()) return emptyMap()
            val amounts = active.map { it.cents }.sorted()
            if (amounts.first() == amounts.last()) return active.associate { it.day to 2 }
            fun quantile(ratio: Float): Long = amounts[((amounts.size - 1) * ratio).toInt().coerceIn(0, amounts.lastIndex)]
            val q1 = quantile(0.25f)
            val q2 = quantile(0.5f)
            val q3 = quantile(0.75f)
            return active.associate { usage ->
                usage.day to (when {
                    usage.cents <= q1 -> 1
                    usage.cents <= q2 -> 2
                    usage.cents <= q3 -> 3
                    else -> 4
                })
            }
        }

        private fun monthLabel(month: String): String {
            if (month.isBlank()) return "本月"
            val parts = month.split("-")
            return if (parts.size == 2) parts[0] + "年" + parts[1].trimStart('0') + "月" else month
        }
    }
}
