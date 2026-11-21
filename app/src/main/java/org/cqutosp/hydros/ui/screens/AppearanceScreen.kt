package org.cqutosp.hydros.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.cqutosp.hydros.R
import org.cqutosp.hydros.ui.components.IconTone
import org.cqutosp.hydros.ui.components.MineRow
import org.cqutosp.hydros.ui.components.PageColumn
import org.cqutosp.hydros.ui.components.RadioRow
import org.cqutosp.hydros.ui.components.Section
import org.cqutosp.hydros.ui.components.SubtleDivider
import org.cqutosp.hydros.ui.theme.ThemeMode

/**
 * 应用外观总入口：深浅模式 + 主题色 / 强调色 / 字体 / 壁纸。
 *
 * 除 [theme] 外的回调都有默认值，预览壳与测试可以继续零散参数调用。
 */
@Composable
fun AppearanceScreen(
    theme: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    fontLabel: String = "苹方",
    gradientLabel: String = "默认",
    accentLabel: String = "跟随主题",
    wallpaperOn: Boolean = false,
    onOpenFont: () -> Unit = {},
    onOpenThemeColor: () -> Unit = {},
    onOpenAccent: () -> Unit = {},
    onOpenWallpaper: () -> Unit = {},
) {
    PageColumn {
        Section("深浅模式") {
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    RadioRow(
                        title = mode.label,
                        selected = mode == theme,
                        onClick = { onTheme(mode) }
                    )
                    if (index < ThemeMode.entries.lastIndex) SubtleDivider()
                }
            }
        }
        Section("个性化") {
            MineRow(
                title = "主题色",
                supporting = accentLabel,
                icon = R.drawable.ic_palette,
                tone = IconTone.PRIMARY,
                onClick = onOpenAccent
            )
            SubtleDivider()
            MineRow(
                title = "渐变背景",
                supporting = gradientLabel,
                icon = R.drawable.ic_water,
                tone = IconTone.SECONDARY,
                onClick = onOpenThemeColor
            )
            SubtleDivider()
            MineRow(
                title = "字体",
                supporting = fontLabel,
                icon = R.drawable.ic_type,
                tone = IconTone.TERTIARY,
                onClick = onOpenFont
            )
            SubtleDivider()
            MineRow(
                title = "背景壁纸",
                supporting = if (wallpaperOn) "已启用自定义壁纸" else "使用渐变背景",
                icon = R.drawable.ic_photo,
                tone = IconTone.NEUTRAL,
                onClick = onOpenWallpaper
            )
        }
    }
}