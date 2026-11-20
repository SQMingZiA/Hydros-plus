package org.cqutosp.hydros.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.cqutosp.hydros.R
import org.cqutosp.hydros.thirdparty.UseCodeUi
import org.cqutosp.hydros.ui.components.*

/** 候选码领取时限，与 muguang 一致：3 分钟。 */
private const val REDEEM_TTL_SECONDS = 3 * 60

@Composable
fun UseCodeScreen(
    ui: UseCodeUi,
    onRefresh: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onGenerate: () -> Unit,
    onClaim: (String) -> Unit,
) {
    val current = ui.state
    val currentCode = current?.useCode
    val candidate = ui.candidate?.useCode
    var clock by remember(candidate, ui.candidateAt) { mutableStateOf(System.currentTimeMillis()) }

    // 候选码只在 3 分钟内可领取，每秒刷新一次剩余时间。
    LaunchedEffect(candidate, ui.candidateAt) {
        if (candidate == null) return@LaunchedEffect
        clock = System.currentTimeMillis()
        while (true) {
            clock = System.currentTimeMillis()
            if (remainingSeconds(ui.candidateAt, clock) <= 0) break
            delay(1_000)
        }
    }

    val remaining = remainingSeconds(ui.candidateAt, clock)
    val expired = candidate != null && remaining <= 0

    PageColumn {
        Section("我的使用码") {
            CodeSurface {
                when {
                    currentCode != null -> UseCodeText(currentCode)
                    current != null -> {
                        Text(
                            text = "暂未获取到使用码",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        TextAction(
                            label = "刷新",
                            enabled = !ui.loading,
                            onClick = onRefresh
                        )
                    }
                    ui.loading -> InlineState("正在获取使用码", loading = true)
                    else -> InlineState(
                        title = "暂未获取到使用码",
                        action = "刷新",
                        onAction = onRefresh
                    )
                }
            }
            if (current != null) {
                InfoRow("使用码状态", if (current.enabled) "已启用" else "已停用")
                if (current.resetAvailability != null) {
                    SubtleDivider()
                    InfoRow("今日换码", availabilityText(current.resetAvailability))
                }
                SwitchRow(
                    title = "开启使用码",
                    checked = current.enabled,
                    onCheckedChange = onSetEnabled,
                    enabled = !ui.busy,
                    icon = R.drawable.ic_lock
                )
            }
        }

        if (candidate != null) {
            Section(if (expired) "候选码已超时" else "待领取的使用码") {
                CodeSurface {
                    UseCodeText(candidate, highlighted = !expired)
                    Text(
                        text = if (expired) "已超时，请重新换一个" else "请在 %s 内领取".format(clockText(remaining)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
                InfoRow("今日还能换", ui.candidate?.remainTimes?.let { "$it 次" } ?: "—")
                ActionRow(Modifier.padding(bottom = 14.dp)) {
                    SecondaryAction(
                        label = "换一个",
                        modifier = Modifier.weight(1f),
                        enabled = !ui.busy,
                        onClick = onGenerate
                    )
                    PrimaryAction(
                        label = "确定领取",
                        modifier = Modifier.weight(1f),
                        enabled = !ui.busy,
                        loading = ui.busy,
                        disabledReason = if (expired) "已超时，请重新换一个" else null,
                        onClick = { onClaim(candidate) }
                    )
                }
            }
        } else {
            Section("生成使用码") {
                PrimaryAction(
                    label = "生成新使用码",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    enabled = !ui.busy,
                    loading = ui.busy,
                    onClick = onGenerate
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextAction(
                        label = "刷新",
                        enabled = !ui.loading,
                        onClick = onRefresh
                    )
                }
            }
        }

        ui.error?.let { InlineState(title = "操作未完成", description = it, icon = R.drawable.ic_info) }
        ui.notice?.let { InlineState(title = it, icon = R.drawable.ic_check_circle) }

    }
}

/** 使用码展示底板：浅主色圆角块，内容水平、垂直居中。 */
@Composable
private fun CodeSurface(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        shape = RoundedCornerShape(10.dp),
        color = colors.primary.copy(alpha = 0.07f)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

/** 大号等宽使用码，后三位（手机号后三位）用强调色。 */
@Composable
private fun UseCodeText(code: String, highlighted: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = remember(code, highlighted, colors.primary, colors.onSurface) {
            highlightCode(code, colors.primary, colors.onSurface, highlighted)
        },
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 5.sp,
            textAlign = TextAlign.Center
        ),
        color = colors.onSurface
    )
}

private fun highlightCode(code: String, tailColor: Color, bodyColor: Color, highlighted: Boolean): AnnotatedString =
    buildAnnotatedString {
        val body = code.dropLast(3)
        val tail = code.takeLast(3)
        if (body.isNotEmpty()) {
            withStyle(SpanStyle(color = bodyColor.copy(alpha = if (highlighted) 1f else 0.5f))) { append(body) }
        }
        withStyle(SpanStyle(color = tailColor.copy(alpha = if (highlighted) 1f else 0.5f), fontWeight = FontWeight.Bold)) {
            append(tail)
        }
    }

private fun remainingSeconds(startedAt: Long, now: Long): Int {
    if (startedAt <= 0L) return 0
    val elapsed = ((now - startedAt) / 1000).coerceAtLeast(0)
    return (REDEEM_TTL_SECONDS - elapsed).coerceAtLeast(0).toInt()
}

private fun clockText(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

private fun availabilityText(value: Int?): String = when {
    value == null -> "今日可领取"
    value <= 0 -> "今日已领取，明天再来"
    else -> "今日可领取"
}
