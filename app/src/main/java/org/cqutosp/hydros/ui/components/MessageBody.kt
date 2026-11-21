package org.cqutosp.hydros.ui.components

import android.graphics.Typeface as AndroidTypeface
import android.text.Html
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/** 消息正文可能是纯文本，也可能是网关后台拼好的 HTML 公告。 */
private val HTML_TAG = Regex("<\\s*[a-zA-Z][^>]*>")

fun looksLikeHtml(text: String): Boolean = HTML_TAG.containsMatchIn(text)

/** 列表摘要用：HTML 转纯文本并压掉多余空白。 */
fun htmlToPlainText(raw: String): String {
    if (!looksLikeHtml(raw)) return normalizePlain(raw)
    val spanned = runCatching { Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT) }.getOrNull()
        ?: return normalizePlain(raw)
    return normalizePlain(spanned.toString())
}

private fun normalizePlain(text: String): String = text
    .replace('\u00A0', ' ')
    .replace("\t", " ")
    .replace(Regex(" +\n"), "\n")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

/** 公告正文里常见的日期行，例如 2026-09-26 / 2026-9-26 09:24:40。 */
private val HEADER_DATE_LINE = Regex("^\\d{4}\\s*[-/.年]\\s*\\d{1,2}\\s*[-/.月]\\s*\\d{1,2}\\s*日?(\\s+\\d{1,2}:\\d{2}(:\\d{2})?)?$")

/** 只有时间的一行，例如 09:24:40。 */
private val HEADER_TIME_LINE = Regex("^\\d{1,2}:\\d{2}(:\\d{2})?$")

private fun sameLine(a: String, b: String): Boolean =
    a.replace(Regex("\\s+"), "") == b.replace(Regex("\\s+"), "")

/**
 * 计算正文开头需要丢掉的字符数：网关后台常把标题和日期也写进正文，
 * 而详情页已经单独显示了标题与时间，重复出现两次会很奇怪。
 */
private fun isHeadingLike(line: String): Boolean =
    line.length <= 40 && line.none { it == '。' || it == '！' || it == '？' || it == '；' || it == '.' || it == '!' || it == '?' || it == ';' }

private fun leadingHeaderEnd(text: String, title: String?): Int {
    if (title.isNullOrBlank()) return 0
    data class Line(val end: Int, val text: String)
    val lines = mutableListOf<Line>()
    var cursor = 0
    while (cursor < text.length && lines.size < 5) {
        val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
        val line = text.substring(cursor, lineEnd).trim()
        if (line.isNotEmpty()) lines += Line(lineEnd, line)
        cursor = lineEnd + 1
    }
    var dropped = 0
    while (dropped < lines.size && dropped < 3) {
        val line = lines[dropped].text
        val isHeader = sameLine(line, title) || HEADER_DATE_LINE.matches(line) || HEADER_TIME_LINE.matches(line)
        if (!isHeader) break
        dropped++
    }
    if (dropped == 0) {
        // 有的网关会在正文里再写一遍自己的小标题（和推送标题不同），紧跟一行日期，同样属于重复信息。
        val first = lines.getOrNull(0)
        val second = lines.getOrNull(1)
        if (first != null && second != null && isHeadingLike(first.text) &&
            (HEADER_DATE_LINE.matches(second.text) || HEADER_TIME_LINE.matches(second.text))
        ) {
            dropped = 2
        }
    }
    if (dropped == 0) return 0
    return (lines[dropped - 1].end + 1).coerceIn(0, text.length)
}

/**
 * Html.fromHtml 得到的 Spanned 转 AnnotatedString。
 *
 * 只保留加粗 / 斜体 / 下划线 / 删除线 / 等宽字体，颜色和字号交给主题：
 * 公告里写死的 #999999、12px 在深色模式或大字号下会很难读。
 * [skipLeadingTitle] 用于详情页去掉正文里重复的标题与日期。
 */
fun htmlToAnnotatedString(raw: String, skipLeadingTitle: String? = null): AnnotatedString {
    if (!looksLikeHtml(raw)) {
        val plain = normalizePlain(raw)
        val cut = leadingHeaderEnd(plain, skipLeadingTitle)
        return AnnotatedString(plain.substring(cut).trimStart())
    }
    val spanned: Spanned = runCatching { Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT) }
        .getOrNull() ?: return AnnotatedString(normalizePlain(raw))
    val full = spanned.toString().replace('\u00A0', ' ').replace("\t", " ")
    val start = full.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return AnnotatedString("")
    val end = full.indexOfLast { !it.isWhitespace() } + 1

    val builder = AnnotatedString.Builder()
    var cursorStyle: SpanStyle? = null
    var cursorStart = 0
    var out = 0
    for (i in start until end) {
        var style: SpanStyle? = null
        for (span in spanned.getSpans(i, i + 1, Any::class.java)) style = merge(style, styleOf(span))
        if (style != cursorStyle) {
            cursorStyle?.let { builder.addStyle(it, cursorStart, out) }
            cursorStyle = style
            cursorStart = out
        }
        builder.append(full[i])
        out++
    }
    cursorStyle?.let { builder.addStyle(it, cursorStart, out) }
    val annotated = builder.toAnnotatedString()
    val cut = leadingHeaderEnd(annotated.text, skipLeadingTitle)
    if (cut <= 0) return annotated
    // subSequence 会保留区间内的样式，只是把重复的标题/日期切掉。
    return AnnotatedString(annotated.text.substring(cut).trimStart())
}

/** 消息正文：自动识别纯文本 / HTML，统一用主题字号与正文色。 */
@Composable
fun MessageBody(text: String, modifier: Modifier = Modifier, skipTitle: String? = null) {
    Text(
        text = htmlToAnnotatedString(text, skipTitle),
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 24.sp,
    )
}

private fun styleOf(span: Any): SpanStyle? = when (span) {
    is StyleSpan -> when (span.style) {
        AndroidTypeface.BOLD -> SpanStyle(fontWeight = FontWeight.SemiBold)
        AndroidTypeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        AndroidTypeface.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic)
        else -> null
    }
    // Html 的 h1~h3 通过 RelativeSizeSpan 放大，这里改成加粗，避免字号脱主题。
    is RelativeSizeSpan -> if (span.sizeChange > 1.1f) SpanStyle(fontWeight = FontWeight.SemiBold) else null
    is UnderlineSpan -> SpanStyle(textDecoration = TextDecoration.Underline)
    is StrikethroughSpan -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    is TypefaceSpan -> SpanStyle(fontFamily = FontFamily.Monospace)
    else -> null
}

private fun merge(a: SpanStyle?, b: SpanStyle?): SpanStyle? = when {
    a == null -> b
    b == null -> a
    else -> a.merge(b)
}
