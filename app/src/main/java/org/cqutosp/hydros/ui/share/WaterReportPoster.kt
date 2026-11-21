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
import org.cqutosp.hydros.ui.state.WaterTip
import org.cqutosp.hydros.ui.state.WaterTipLevel
import org.cqutosp.hydros.ui.state.WaterUsageReport
import org.cqutosp.hydros.ui.state.money
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * 把智能用水报告画成一张 1080 宽的长图，用于保存到相册或分享。
 *
 * 之所以用原生 Canvas 而不是 Compose 截图：报告页是可滚动的长列表，屏幕外的内容拿不到，
 * 而且截图会带上系统状态栏与底栏。这里按同一套数据重绘，排版可在不同机型上保持一致。
 */
object WaterReportPoster {

    private const val WIDTH = 1080
    private const val MAX_HEIGHT = 5600
    private const val MARGIN = 72f

    private const val TEXT = 0xFF1B1B1F.toInt()
    private const val MUTED = 0xFF6D7380.toInt()
    private const val HAIRLINE = 0xFFECEFF3.toInt()
    private const val SURFACE = 0xFFF4F6F9.toInt()

    fun render(
        report: WaterUsageReport,
        limitCents: Long?,
        primary: Int,
        generatedAt: LocalDateTime = LocalDateTime.now(),
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, MAX_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFFFFF.toInt())
        val poster = Poster(canvas, primary)

        poster.brand()
        poster.month(report)
        poster.amountBlock(report)
        if (!report.loaded) {
            poster.section("用水分析")
            poster.paragraph("账单还在加载，请稍后再试。", MUTED)
            return poster.finish(bitmap)
        }
        poster.overview(report)
        poster.heatmap(report)
        if (report.isCurrentMonth) poster.projection(report, limitCents)
        poster.tips(report)
        poster.limit(report, limitCents)
        poster.footer(generatedAt)
        return poster.finish(bitmap)
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

    private class Poster(private val canvas: Canvas, primary: Int) {
        private val brand = 0xFF000000.toInt() or (primary and 0x00FFFFFF)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT }
        private val bold = Typeface.create("sans-serif", Typeface.BOLD)
        private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
        private var y = 56f

        private fun text(
            value: String,
            x: Float,
            baseline: Float,
            size: Float,
            color: Int,
            typeface: Typeface = regular,
            align: Paint.Align = Paint.Align.LEFT,
        ) {
            paint.reset()
            paint.isAntiAlias = true
            paint.textSize = size
            paint.color = color
            paint.typeface = typeface
            paint.textAlign = align
            canvas.drawText(value, x, baseline, paint)
        }

        private fun wrap(value: String, maxWidth: Float, size: Float, typeface: Typeface): List<String> {
            paint.reset()
            paint.isAntiAlias = true
            paint.textSize = size
            paint.typeface = typeface
            val lines = mutableListOf<String>()
            val line = StringBuilder()
            value.forEach { ch ->
                val next = line.toString() + ch
                if (paint.measureText(next) > maxWidth && line.isNotEmpty()) {
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

        fun brand() {
            text("Hydros+", MARGIN, y + 46f, 46f, brand, bold)
            text("智能用水报告", WIDTH - MARGIN, y + 46f, 28f, MUTED, regular, Paint.Align.RIGHT)
            y += 76f
        }

        fun month(report: WaterUsageReport) {
            text(monthLabel(report.month), MARGIN, y + 44f, 40f, TEXT, bold)
            y += 64f
        }

        fun amountBlock(report: WaterUsageReport) {
            val height = 236f
            paint.reset()
            paint.isAntiAlias = true
            paint.color = brand
            canvas.drawRoundRect(RectF(MARGIN, y, WIDTH - MARGIN, y + height), 28f, 28f, paint)

            val white = 0xFFFFFFFF.toInt()
            text(if (report.isCurrentMonth) "本月支出" else "当月支出", MARGIN + 32f, y + 52f, 28f, white.withAlpha(0.78f))
            val amount = if (report.loaded) "¥ " + money(report.totalCents) else "—"
            text(amount, MARGIN + 32f, y + 134f, 84f, white, bold)
            val percent = report.monthOverMonthPercent
            val badge = when {
                !report.loaded -> null
                percent == null -> "上月无记录"
                percent < 0 -> "环比 " + percent + "%"
                percent > 0 -> "环比 +" + percent + "%"
                else -> "环比 持平"
            }
            if (badge != null) {
                val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white.withAlpha(0.18f) }
                val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 26f; typeface = medium; this.color = white; textAlign = Paint.Align.CENTER
                }
                val width = badgeText.measureText(badge) + 44f
                val rect = RectF(WIDTH - MARGIN - 32f - width, y + 96f, WIDTH - MARGIN - 32f, y + 148f)
                canvas.drawRoundRect(rect, 26f, 26f, badgePaint)
                canvas.drawText(badge, rect.centerX(), rect.centerY() + 9f, badgeText)
            }
            if (report.loaded) {
                text(
                    report.sessionCount.toString() + " 次用水 · 有支出 " + report.activeDays + " 天",
                    MARGIN + 32f,
                    y + 202f,
                    28f,
                    white.withAlpha(0.78f),
                )
            }
            y += height + 28f
        }

        fun section(title: String) {
            y += 26f
            paint.reset()
            paint.isAntiAlias = true
            paint.color = HAIRLINE
            canvas.drawRect(MARGIN, y, WIDTH - MARGIN, y + 1.5f, paint)
            y += 44f
            text(title, MARGIN, y + 34f, 32f, TEXT, medium)
            y += 56f
        }

        fun paragraph(value: String, color: Int) {
            wrap(value, WIDTH - MARGIN * 2, 30f, regular).forEach { line ->
                text(line, MARGIN, y + 34f, 30f, color)
                y += 46f
            }
        }

        private fun row(label: String, value: String, valueColor: Int = TEXT) {
            text(label, MARGIN, y + 34f, 32f, MUTED)
            text(value, WIDTH - MARGIN, y + 34f, 32f, valueColor, medium, Paint.Align.RIGHT)
            y += 66f
            paint.reset()
            paint.isAntiAlias = true
            paint.color = HAIRLINE
            canvas.drawRect(MARGIN, y, WIDTH - MARGIN, y + 1.5f, paint)
            y += 4f
        }

        fun overview(report: WaterUsageReport) {
            section("本月概览")
            row("用水次数", report.sessionCount.toString() + " 次")
            report.waterText?.let { row("用水量", it) }
            row("日均支出", "¥ " + money(report.dailyAverageCents))
            row("有支出天数", report.activeDays.toString() + " 天 · 单日均 ¥" + money(report.activeDailyAverageCents))
            row(
                "最高单日",
                report.peakDay?.let { it.toString() + " 日 · ¥" + money(report.peakDayCents) } ?: "暂无用水",
            )
            row("最高时段", report.peakHourLabel ?: "暂无足够数据")
            row("主要用途", report.topCategory?.label ?: "暂无足够数据")
            if (report.previousMonthCents > 0) row("上月支出", "¥ " + money(report.previousMonthCents))
        }

        fun heatmap(report: WaterUsageReport) {
            section("本月用水热力图")
            val month = runCatching { java.time.YearMonth.parse(report.month) }.getOrNull()
            val days = month?.lengthOfMonth() ?: report.daysInMonth
            val daily = if (report.dailyUsages.size == days) {
                report.dailyUsages
            } else {
                (1..days).map { WaterDayUsage(it, 0L, 0) }
            }
            val byDay = daily.associateBy { it.day }
            val levels = heatLevels(daily)
            val gutter = 10f
            val cell = (WIDTH - MARGIN * 2 - gutter * 6) / 7f
            val leading = month?.atDay(1)?.dayOfWeek?.value?.minus(1) ?: 0
            val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
            weekdays.forEachIndexed { index, label ->
                text(
                    label,
                    MARGIN + index * (cell + gutter) + cell / 2f,
                    y + 30f,
                    26f,
                    MUTED,
                    regular,
                    Paint.Align.CENTER,
                )
            }
            y += 48f
            val cells = buildList<Int?> {
                repeat(leading.coerceIn(0, 6)) { add(null) }
                daily.forEach { add(it.day) }
            }
            cells.chunked(7).forEach { week ->
                week.forEachIndexed { index, day ->
                    if (day == null) return@forEachIndexed
                    val left = MARGIN + index * (cell + gutter)
                    val level = levels[day] ?: 0
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.color = if (level <= 0) SURFACE else blend(level)
                    canvas.drawRoundRect(RectF(left, y, left + cell, y + cell), 14f, 14f, paint)
                    val cents = byDay[day]?.cents ?: 0L
                    text(
                        day.toString(),
                        left + cell / 2f,
                        y + cell / 2f + 11f,
                        30f,
                        if (level >= 4) 0xFFFFFFFF.toInt() else TEXT,
                        regular,
                        Paint.Align.CENTER,
                    )
                }
                y += cell + gutter
            }
            y += 10f
            text("颜色越深表示当天支出越高", MARGIN, y + 30f, 26f, MUTED)
            y += 46f
        }

        fun projection(report: WaterUsageReport, limitCents: Long?) {
            section("预计本月")
            val column = (WIDTH - MARGIN * 2 - 24f) / 2f
            text("预计月末支出", MARGIN, y + 30f, 28f, MUTED)
            text("¥ " + money(report.projectionCents), MARGIN, y + 88f, 46f, TEXT, bold)
            text("预计用水次数", MARGIN + column + 24f, y + 30f, 28f, MUTED)
            text(report.projectedSessions.toString() + " 次", MARGIN + column + 24f, y + 88f, 46f, TEXT, bold)
            y += 118f
            if (limitCents != null && limitCents > 0) {
                val remaining = report.projectedRemainingCents ?: (limitCents - report.projectionCents)
                row("本月限额", "¥ " + money(limitCents))
                row(if (remaining >= 0) "预计剩余额度" else "预计超出限额", "¥ " + money(abs(remaining)))
            }
        }

        fun tips(report: WaterUsageReport) {
            section("节水建议")
            if (report.sessionCount > 0) {
                text("节水评分", MARGIN, y + 34f, 32f, TEXT)
                text(report.score.toString() + " / 100", WIDTH - MARGIN, y + 34f, 32f, brand, medium, Paint.Align.RIGHT)
                y += 62f
            }
            if (report.tips.isEmpty()) {
                paragraph("本月暂无建议", MUTED)
                return
            }
            report.tips.forEach { tip ->
                val color = if (tip.level == WaterTipLevel.WARNING) 0xFFC42B1C.toInt() else TEXT
                text(tip.title, MARGIN, y + 34f, 32f, color, medium)
                y += 50f
                wrap(tip.detail, WIDTH - MARGIN * 2, 30f, regular).forEach { line ->
                    text(line, MARGIN, y + 32f, 30f, MUTED)
                    y += 44f
                }
                y += 18f
            }
        }

        fun limit(report: WaterUsageReport, limitCents: Long?) {
            section("用水限额")
            val limit = limitCents?.takeIf { it > 0 }
            if (limit == null) {
                paragraph("还没有设置月度限额", MUTED)
                return
            }
            val reached = report.limitReached
            text("已达 " + (report.limitProgress * 100).toInt() + "%", MARGIN, y + 34f, 32f, TEXT)
            text(
                if (reached) "已超限" else "剩余 ¥" + money(report.limitRemainingCents),
                WIDTH - MARGIN,
                y + 34f,
                28f,
                if (reached) 0xFFC42B1C.toInt() else MUTED,
                regular,
                Paint.Align.RIGHT,
            )
            y += 54f
            val barHeight = 16f
            paint.reset()
            paint.isAntiAlias = true
            paint.color = SURFACE
            canvas.drawRoundRect(RectF(MARGIN, y, WIDTH - MARGIN, y + barHeight), 8f, 8f, paint)
            paint.color = if (reached) 0xFFC42B1C.toInt() else brand
            val progress = report.limitProgress.coerceIn(0f, 1f)
            canvas.drawRoundRect(
                RectF(MARGIN, y, MARGIN + (WIDTH - MARGIN * 2) * progress, y + barHeight),
                8f,
                8f,
                paint,
            )
            y += barHeight + 60f
        }

        fun footer(generatedAt: LocalDateTime) {
            y += 12f
            paint.reset()
            paint.isAntiAlias = true
            paint.color = HAIRLINE
            canvas.drawRect(MARGIN, y, WIDTH - MARGIN, y + 1.5f, paint)
            y += 40f
            text(
                "数据来自账单，仅在本地计算 · 生成于 " +
                    generatedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                MARGIN,
                y + 30f,
                26f,
                MUTED,
            )
            y += 62f
            text("Hydros+ · 校园热水 · 扫码即用", WIDTH / 2f, y + 30f, 26f, MUTED.withAlpha(0.8f), regular, Paint.Align.CENTER)
            y += 60f
        }

        fun finish(full: Bitmap): Bitmap {
            val height = (y + MARGIN).toInt().coerceIn(1200, MAX_HEIGHT)
            val cropped = Bitmap.createBitmap(full, 0, 0, WIDTH, height)
            if (cropped !== full) full.recycle()
            return cropped
        }

        private fun blend(level: Int): Int {
            val alpha = when (level) {
                1 -> 0.18f
                2 -> 0.38f
                3 -> 0.62f
                else -> 0.9f
            }
            return mix(SURFACE, brand, alpha)
        }

        private fun mix(from: Int, to: Int, ratio: Float): Int {
            val r = ((from shr 16 and 0xFF) * (1 - ratio) + (to shr 16 and 0xFF) * ratio).toInt()
            val g = ((from shr 8 and 0xFF) * (1 - ratio) + (to shr 8 and 0xFF) * ratio).toInt()
            val b = ((from and 0xFF) * (1 - ratio) + (to and 0xFF) * ratio).toInt()
            return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }

        private fun heatLevels(daily: List<WaterDayUsage>): Map<Int, Int> {
            val active = daily.filter { it.cents > 0 }.sortedBy { it.cents }
            if (active.isEmpty()) return emptyMap()
            val n = active.size
            return active.mapIndexed { index, usage ->
                usage.day to (((index + 1) * 4 + n - 1) / n).coerceIn(1, 4)
            }.toMap()
        }

        private fun Int.withAlpha(alpha: Float): Int {
            val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
            return (a shl 24) or (this and 0x00FFFFFF)
        }

        private fun monthLabel(month: String): String {
            if (month.isBlank()) return "本月"
            val parts = month.split("-")
            return if (parts.size == 2) parts[0] + "年" + parts[1].trimStart('0') + "月" else month
        }
    }
}
