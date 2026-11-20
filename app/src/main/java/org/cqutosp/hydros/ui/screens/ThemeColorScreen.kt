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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.ui.components.Glyph
import org.cqutosp.hydros.ui.components.PageColumn
import org.cqutosp.hydros.ui.components.Section
import org.cqutosp.hydros.ui.components.SubtleDivider
import org.cqutosp.hydros.ui.components.SwitchRow
import org.cqutosp.hydros.ui.theme.GradientPreset

/**
 * 主题色设置：开关渐变背景 + 选择渐变预设。
 *
 * @param preset 当前预设键（[GradientPreset] 的 name）。
 */
@Composable
fun ThemeColorScreen(
    preset: String,
    enabled: Boolean,
    onPreset: (String) -> Unit,
    onEnabled: (Boolean) -> Unit,
) {
    val current = GradientPreset.fromKey(preset)
    PageColumn {
        Section("渐变背景") {
            SwitchRow(
                title = "启用渐变背景",
                checked = enabled,
                onCheckedChange = onEnabled,
                supporting = "关闭后页面使用纯色背景"
            )
        }
        Section("渐变色彩") {
            Column(Modifier.selectableGroup()) {
                GradientPreset.entries.forEachIndexed { index, item ->
                    GradientPresetRow(
                        preset = item,
                        selected = item == current,
                        onClick = { onPreset(item.name) }
                    )
                    if (index < GradientPreset.entries.lastIndex) SubtleDivider()
                }
            }
        }
    }
}

@Composable
private fun GradientPresetRow(preset: GradientPreset, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SwatchDot(preset.start)
            SwatchDot(preset.end)
        }
        Text(
            text = preset.label,
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
private fun SwatchDot(color: Color) {
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    )
}
