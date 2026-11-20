package org.cqutosp.hydros.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.cqutosp.hydros.ui.components.PageColumn
import org.cqutosp.hydros.ui.components.RadioRow
import org.cqutosp.hydros.ui.components.Section
import org.cqutosp.hydros.ui.components.SubtleDivider
import org.cqutosp.hydros.ui.theme.FontChoice
import org.cqutosp.hydros.ui.theme.family

/**
 * 字体设置：内置苹方 / 跟随系统。当前值用存储键（[FontChoice] 的 name）表示。
 */
@Composable
fun FontSettingsScreen(current: String, onSelect: (String) -> Unit) {
    val selected = FontChoice.fromKey(current)
    PageColumn {
        Section("字体") {
            Column(Modifier.selectableGroup()) {
                FontChoice.entries.forEachIndexed { index, choice ->
                    RadioRow(
                        title = choice.label,
                        selected = choice == selected,
                        onClick = { onSelect(choice.name) },
                        supporting = when (choice) {
                            FontChoice.PINGFANG -> "内置苹方，不受系统主题影响"
                            FontChoice.SYSTEM -> "跟随系统字体"
                        }
                    )
                    if (index < FontChoice.entries.lastIndex) SubtleDivider()
                }
            }
        }
        Section("预览") {
            Column(
                Modifier.padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "热水已就绪，扫码即可开始洗澡",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = selected.family(),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "本月用水 3 次，支出 ¥12.60",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = selected.family(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
