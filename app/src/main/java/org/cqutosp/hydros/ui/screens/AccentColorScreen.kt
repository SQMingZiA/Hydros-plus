package org.cqutosp.hydros.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.ui.components.FormField
import org.cqutosp.hydros.ui.components.Glyph
import org.cqutosp.hydros.ui.components.PageColumn
import org.cqutosp.hydros.ui.components.PrimaryAction
import org.cqutosp.hydros.ui.components.Section

/** 预设强调色（ARGB），第一项“默认”对应 accent = null。 */
private val AccentSwatches: List<Long> = listOf(
    0xFF2F6BEA, 0xFF6D5BD0, 0xFF8E24AA, 0xFFE91E63,
    0xFFFF5A5F, 0xFFFF7A00, 0xFFFF9F0A, 0xFF34C759,
    0xFF00B578, 0xFF00B8D9, 0xFF0EA5E9, 0xFF3D5AFE,
)

/**
 * 强调色设置：默认色 + 12 个预设色 + 自定义 #RRGGBB。
 *
 * @param accent 当前强调色 ARGB，null 表示默认主题色。
 */
@Composable
fun AccentColorScreen(accent: Long?, onAccent: (Long?) -> Unit) {
    var hexInput by remember(accent) { mutableStateOf(accent?.let(::accentHex) ?: "") }
    var error by remember(accent) { mutableStateOf<String?>(null) }
    PageColumn {
        Section("强调色") {
            Column(Modifier.selectableGroup()) {
                DefaultAccentRow(selected = accent == null, onClick = { onAccent(null) })
                Column(
                    Modifier.padding(top = 8.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AccentSwatches.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { value ->
                                AccentDot(
                                    value = value,
                                    selected = accent == value,
                                    onClick = { onAccent(value) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        Section("自定义颜色") {
            Column(Modifier.padding(bottom = 12.dp)) {
                FormField(
                    value = hexInput,
                    onValue = { input ->
                        hexInput = input.uppercase()
                            .filter { it == '#' || it.isDigit() || it in 'A'..'F' }
                            .take(7)
                        error = null
                    },
                    label = "颜色值 #RRGGBB",
                    error = error,
                    maxLength = 7
                )
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                PrimaryAction(
                    label = "应用自定义颜色",
                    modifier = Modifier.padding(top = 8.dp),
                    enabled = hexInput.isNotBlank(),
                    onClick = {
                        val value = parseAccentHex(hexInput)
                        if (value == null) {
                            error = "请输入 #RRGGBB 格式的颜色"
                        } else {
                            error = null
                            onAccent(value)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DefaultAccentRow(selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Glyph(
                R.drawable.ic_palette,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "默认",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (selected) {
            Glyph(
                R.drawable.ic_check,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AccentDot(
    value: Long,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = accentHex(value)
    Column(
        modifier = modifier.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = "颜色 $label" }
                .clip(CircleShape)
                .background(Color(value.toInt()))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Glyph(R.drawable.ic_check, modifier = Modifier.size(20.dp), tint = Color.White)
            }
        }
    }
}

/** ARGB → "#RRGGBB"。 */
private fun accentHex(value: Long): String =
    "#" + (value and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

/** "#RRGGBB" → ARGB（不透明），格式不合法返回 null。 */
private fun parseAccentHex(input: String): Long? {
    val text = input.trim()
    if (!Regex("^#[0-9A-Fa-f]{6}$").matches(text)) return null
    return text.drop(1).toLong(16) or 0xFF000000L
}
