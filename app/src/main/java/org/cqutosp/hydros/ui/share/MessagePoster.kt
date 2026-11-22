package org.cqutosp.hydros.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import org.cqutosp.hydros.ui.components.htmlToAnnotatedString
import android.text.TextUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cqutosp.hydros.R
import org.cqutosp.hydros.data.messages.CampusMessage
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 把一条校园通知渲染成 1080px 宽的长图，保存到相册后交给系统分享。
 *
 * 排版全部用原生 Canvas + StaticLayout：StaticLayout 自带断行、避头尾与长英文折行，
 * 比手写测量可靠；先用同一组参数测量一次高度，再创建精确高度的 Bitmap 绘制一次。
 */
object MessagePoster {

    private const val WIDTH = 1080
    private const val PADDING = 72
    private const val CONTENT_WIDTH = WIDTH - PADDING * 2
    private const val MAX_BODY_CHARS = 2400
    private const val MAX_HEIGHT = 12000

    private val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F6CBD")
        textSize = 44f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6D7380")
        textSize = 26f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111318")
        textSize = 50f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A9099")
        textSize = 28f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33373F")
        textSize = 34f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A9099")
        textSize = 26f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ECEFF3")
        strokeWidth = 2f
    }

    fun render(
        message: CampusMessage,
        primary: Int,
        generatedAt: LocalDateTime = LocalDateTime.now(),
    ): Bitmap {
        // 公告正文常是后台拼好的 HTML，转成纯文本并去掉与标题重复的首行。
        val rawBody = message.body.ifBlank { message.summary }
        val body = htmlToAnnotatedString(rawBody, message.title).text.let {
            if (it.length > MAX_BODY_CHARS) it.take(MAX_BODY_CHARS) + "……" else it
        }.ifBlank { "（无正文）" }
        val titleLayout = layout(message.title, titlePaint, 3)
        val bodyLayout = layout(body, bodyPaint, Int.MAX_VALUE)
        val tag = "校园通知"
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primary
            textSize = 28f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val tagWidth = tagPaint.measureText(tag) + 44f
        val tagHeight = 56f

        val headerHeight = 120f
        val titleY = headerHeight + tagHeight + 36f
        val dateY = titleY + titleLayout.height + 24f
        val bodyY = dateY + 30f + 44f
        val footerY = bodyY + bodyLayout.height + 56f
        val height = (footerY + 96f).toInt().coerceAtMost(MAX_HEIGHT)

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // 品牌行
        canvas.drawText("Hydros+", PADDING.toFloat(), 86f, brandPaint)
        val right = "通知分享"
        canvas.drawText(right, WIDTH - PADDING - labelPaint.measureText(right), 86f, labelPaint)

        // 标签胶囊
        val tagLeft = PADDING.toFloat()
        val tagTop = headerHeight
        val tagRect = android.graphics.RectF(tagLeft, tagTop, tagLeft + tagWidth, tagTop + tagHeight)
        val tagBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(primary, 0x1F) }
        canvas.drawRoundRect(tagRect, tagHeight / 2f, tagHeight / 2f, tagBg)
        canvas.drawText(tag, tagLeft + 22f, tagTop + tagHeight / 2f - (tagPaint.ascent() + tagPaint.descent()) / 2f, tagPaint)

        // 标题（最多 3 行）
        canvas.save()
        canvas.translate(PADDING.toFloat(), titleY)
        titleLayout.draw(canvas)
        canvas.restore()

        // 日期
        canvas.drawText(message.publishedLabel, PADDING.toFloat(), dateY + 24f, datePaint)

        // 分隔线
        val dividerY = dateY + 44f
        canvas.drawLine(PADDING.toFloat(), dividerY, (WIDTH - PADDING).toFloat(), dividerY, linePaint)

        // 正文
        canvas.save()
        canvas.translate(PADDING.toFloat(), bodyY)
        bodyLayout.draw(canvas)
        canvas.restore()

        // 页脚
        canvas.drawLine(PADDING.toFloat(), footerY, (WIDTH - PADDING).toFloat(), footerY, linePaint)
        val stamp = generatedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        canvas.drawText("来自 Hydros+ · 校园热水 · 扫码即用", PADDING.toFloat(), footerY + 44f, footerPaint)
        canvas.drawText(stamp, PADDING.toFloat(), footerY + 84f, footerPaint)
        return bitmap
    }

    fun saveToGallery(context: Context, bitmap: Bitmap): Uri? = runCatching {
        val name = "hydros-message-" + System.currentTimeMillis() + ".png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Hydros+")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
            ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    }.getOrNull()

    private fun layout(text: String, paint: TextPaint, maxLines: Int): StaticLayout {
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.42f)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(if (maxLines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END)
        return builder.build()
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}

/** 顶栏右侧的「保存并分享」：生成图片 → 写入相册 → 拉起系统分享。 */
@Composable
fun MessageShareAction(
    message: CampusMessage,
    onResult: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f))
            .clickable(role = Role.Button) {
                scope.launch {
                    val uri = withContext(Dispatchers.IO) {
                        runCatching {
                            val bitmap = MessagePoster.render(message, primary)
                            MessagePoster.saveToGallery(context, bitmap)
                        }.getOrNull()
                    }
                    if (uri == null) {
                        onResult("生成图片失败，请稍后重试")
                        return@launch
                    }
                    val shared = runCatching {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "分享通知").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.isSuccess
                    onResult(if (shared) "已保存到相册，可直接分享" else "已保存到相册，未找到可用的分享应用")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_share),
            contentDescription = "保存并分享",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
