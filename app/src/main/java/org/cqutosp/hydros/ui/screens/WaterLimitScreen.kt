package org.cqutosp.hydros.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.ui.components.FormField
import org.cqutosp.hydros.ui.components.InfoRow
import org.cqutosp.hydros.ui.components.PageColumn
import org.cqutosp.hydros.ui.components.PrimaryAction
import org.cqutosp.hydros.ui.components.Section
import org.cqutosp.hydros.ui.components.SubtleDivider
import org.cqutosp.hydros.ui.components.SwitchRow
import org.cqutosp.hydros.ui.components.TextAction
import org.cqutosp.hydros.ui.state.WaterUsageReport
import org.cqutosp.hydros.ui.state.money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 用水限额独立页：本月用量概览 + 限额金额 + 超额后是否禁止开始用水。
 * 限额只在本地保存与计算，不上传网关。
 */
@Composable
fun WaterLimitScreen(
    limitCents: Long?,
    blockEnabled: Boolean,
    usage: WaterUsageReport,
    onSaveLimit: (Long?) -> Unit,
    onBlockLimit: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val limit = limitCents?.takeIf { it > 0 }
    var text by remember(limitCents) { mutableStateOf(limit?.let { money(it) }?.takeIf { it != "—" } ?: "") }
    var blocked by remember(blockEnabled) { mutableStateOf(blockEnabled) }

    val parsed = remember(text) {
        text.trim().toBigDecimalOrNull()
            ?.takeIf { it > BigDecimal.ZERO }
            ?.multiply(BigDecimal(100))
            ?.setScale(0, RoundingMode.HALF_UP)?.toLong()
    }
    val error = when {
        text.isBlank() -> null
        text.trim().toBigDecimalOrNull() == null -> "请输入数字金额"
        (text.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO) <= BigDecimal.ZERO -> "金额需要大于 0"
        parsed != null && parsed > 10_000_00L -> "每月限额最多 10000 元"
        else -> null
    }
    val reached = limit != null && usage.loaded && usage.totalCents >= limit
    val progress = if (limit != null) (usage.totalCents.toFloat() / limit.toFloat()).coerceIn(0f, 1f) else 0f

    PageColumn {
        Section("本月用水") {
            InfoRow("本月支出", if (usage.loaded) "¥ " + money(usage.totalCents) else "—")
            SubtleDivider()
            InfoRow("用水次数", usage.sessionCount.toString() + " 次")
            SubtleDivider()
            InfoRow("当前限额", limit?.let { "¥ " + money(it) } ?: "未设置")
        }
        if (limit != null) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (reached) "已达到限额" else "已用 " + ((progress * 100).toInt()).toString() + "%",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (reached) colors.error else colors.onSurface,
                    )
                    Text(
                        if (reached) "超出 ¥ " + money(usage.totalCents - limit)
                        else "剩余 ¥ " + money(limit - usage.totalCents),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().size(width = 0.dp, height = 6.dp),
                    color = if (reached) colors.error else colors.primary,
                    trackColor = colors.surfaceVariant,
                )
            }
        }
        Section("限额设置") {
            FormField(
                value = text,
                onValue = { input ->
                    text = input.filter { it.isDigit() || it == '.' }.take(8)
                },
                label = "每月限额（元，留空表示不限额）",
                numeric = true,
                error = error,
                maxLength = 8,
            )
            SubtleDivider()
            SwitchRow(
                title = "达到限额后禁止使用",
                checked = blocked,
                onCheckedChange = { enabled ->
                    blocked = enabled
                    onBlockLimit(enabled)
                },
                supporting = "开启后，本月支出达到限额时无法开始新的用水",
                icon = R.drawable.ic_lock,
            )
        }
        PrimaryAction(
            label = "保存限额",
            enabled = error == null,
            disabledReason = error,
            onClick = { onSaveLimit(parsed) },
        )
        if (limit != null) {
            TextAction(label = "取消限额", onClick = {
                text = ""
                onSaveLimit(null)
            })
        }
        Text(
            "限额与提醒都只在本机计算，不会上传到校园网关。达到限额时会提醒一次，开启「禁止使用」后可直接拦住新的用水。",
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
        )
    }
}
