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

/**
 * Html.fromHtml 得到的 Spanned 转 AnnotatedString。
 *
 * 只保留加粗 / 斜体 / 下划线 / 删除线 / 等宽字体，颜色和字号交给主题：
 * 公告里写死的 #999999、12px 在深色模式或大字号下会很难读。
 */
fun htmlToAnnotatedString(raw: String): AnnotatedString {
    if (!looksLikeHtml(raw)) return AnnotatedString(normalizePlain(raw))
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
    return builder.toAnnotatedString()
}

/** 消息正文：自动识别纯文本 / HTML，统一用主题字号与正文色。 */
@Composable
fun MessageBody(text: String, modifier: Modifier = Modifier) {
    Text(
        text = htmlToAnnotatedString(text),
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
