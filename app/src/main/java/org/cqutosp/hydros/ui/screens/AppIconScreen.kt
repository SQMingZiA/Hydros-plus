package org.cqutosp.hydros.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.cqutosp.hydros.MainActivity
import org.cqutosp.hydros.R
import org.cqutosp.hydros.data.storage.AppIconKeys
import org.cqutosp.hydros.ui.components.Glyph
import org.cqutosp.hydros.ui.components.IconTone
import org.cqutosp.hydros.ui.components.MineRow
import org.cqutosp.hydros.ui.components.PageColumn
import org.cqutosp.hydros.ui.components.Section
import org.cqutosp.hydros.ui.components.SubtleDivider

/**
 * 桌面图标选择页：默认图标与趣智校园预设，并提供「添加到桌面」快捷方式入口。
 *
 * 别名切换由 [org.cqutosp.hydros.ui.state.AppIconController.apply] 完成，本页只负责展示与回调；
 * 部分厂商桌面只认启动器缓存，切完不刷新时可用「添加到桌面」新建一个带当前图标的入口。
 */
@Composable
fun AppIconScreen(
    current: String,
    onSelect: (String) -> Unit,
) {
    val selectedKey = AppIconKeys.normalize(current)
    val context = LocalContext.current
    val pinSupported = remember(context) { canPinShortcut(context) }
    PageColumn {
        Section("桌面图标") {
            IconOptionRow(
                foreground = R.drawable.ic_launcher_foreground,
                background = colorResource(R.color.ic_launcher_background),
                title = "默认图标",
                isSelected = selectedKey == AppIconKeys.DEFAULT,
                onClick = { onSelect(AppIconKeys.DEFAULT) }
            )
            SubtleDivider()
            IconOptionRow(
                foreground = R.mipmap.ic_launcher_qzxy_foreground,
                background = colorResource(R.color.ic_launcher_qzxy_background),
                title = "趣智校园",
                isSelected = selectedKey == AppIconKeys.QZXY,
                onClick = { onSelect(AppIconKeys.QZXY) }
            )
        }
        // requestPinShortcut 仅部分桌面支持，不支持时整行隐藏。
        if (pinSupported) {
            Section {
                MineRow(
                    title = "添加到桌面",
                    supporting = "用当前图标在桌面新建一个快捷方式",
                    icon = R.drawable.ic_open_in_new,
                    tone = IconTone.PRIMARY,
                    onClick = { pinShortcut(context, selectedKey) }
                )
            }
        }
        Text(
            text = if (pinSupported) {
                "切换后桌面入口立即更新；部分桌面不会自动刷新图标，可用「添加到桌面」创建新入口。"
            } else {
                "切换后桌面入口立即更新，部分桌面需要重启应用或手动刷新才会显示新图标。"
            },
            modifier = Modifier.padding(top = 12.dp, start = 2.dp, end = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 当前桌面是否支持长按添加固定快捷方式（API 26+ 才可能有实现，这里只做能力查询）。 */
private fun canPinShortcut(context: Context): Boolean = runCatching {
    context.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true
}.getOrDefault(false)

/**
 * 用当前选中的图标请求桌面固定一个快捷方式。
 *
 * 快捷方式固定后指向 MainActivity（而不是可切换的 activity-alias），这样以后再切换图标也不会失效；
 * 图标则按当前选择显式指定，避免部分桌面沿用旧缓存位图。
 */
private fun pinShortcut(context: Context, key: String) {
    val manager = context.getSystemService(ShortcutManager::class.java) ?: return
    if (!manager.isRequestPinShortcutSupported) return
    val iconRes = if (key == AppIconKeys.QZXY) R.mipmap.ic_launcher_qzxy else R.mipmap.ic_launcher_default
    val label = context.getString(R.string.app_name)
    val launchIntent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
    val shortcut = ShortcutInfo.Builder(context, "hydros-launcher-" + key.lowercase())
        .setShortLabel(label)
        .setLongLabel(label)
        .setIcon(Icon.createWithResource(context, iconRes))
        .setIntent(launchIntent)
        .build()
    val accepted = runCatching { manager.requestPinShortcut(shortcut, null) }.getOrDefault(false)
    if (!accepted) {
        Toast.makeText(context, "当前桌面不支持添加快捷方式", Toast.LENGTH_SHORT).show()
    }
}

/** 一行图标选项：左侧图标预览、标题，右侧选中勾。 */
@Composable
private fun IconOptionRow(
    foreground: Int,
    background: Color,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { this.selected = isSelected }
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconPreview(foreground = foreground, background = background)
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (isSelected) {
            Glyph(
                R.drawable.ic_check,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 用「背景色 + 前景」合成自适应图标预览，避免直接解码 adaptive-icon XML。 */
@Composable
private fun IconPreview(foreground: Int, background: Color, size: Dp = 48.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(11.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        // 前景图按 108 单位画布出图，桌面只显示中间 72 单位可见区，这里同比例放大 1.5 倍还原真实观感。
        Image(
            painter = painterResource(foreground),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.5f)
        )
    }
}
