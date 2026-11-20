package org.cqutosp.hydros.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.data.storage.AppIconKeys
import org.cqutosp.hydros.ui.components.Glyph
import org.cqutosp.hydros.ui.components.PageColumn
import org.cqutosp.hydros.ui.components.Section
import org.cqutosp.hydros.ui.components.SubtleDivider
import org.cqutosp.hydros.ui.components.TextAction

/**
 * 桌面图标选择页：默认图标、趣智校园预设，以及从相册选图的自定义入口。
 *
 * 别名切换由 AppIconController.apply 完成，本页只负责展示与回调；
 * 自定义图标受 launcher 限制只保存在本机用于预览，文案里已明确标注。
 */
@Composable
fun AppIconScreen(
    current: String,
    hasCustom: Boolean,
    onSelect: (String) -> Unit,
    onPickCustom: () -> Unit,
    customIcon: android.graphics.Bitmap? = null
) {
    val selectedKey = AppIconKeys.normalize(current)
    PageColumn {
        Section("桌面图标") {
            IconOptionRow(
                foreground = R.drawable.ic_launcher_foreground,
                background = colorResource(R.color.ic_launcher_background),
                title = "默认图标",
                supporting = "蓝色水滴",
                isSelected = selectedKey == AppIconKeys.DEFAULT,
                onClick = { onSelect(AppIconKeys.DEFAULT) }
            )
            SubtleDivider()
            IconOptionRow(
                foreground = R.drawable.ic_launcher_qzxy_foreground,
                background = colorResource(R.color.ic_launcher_qzxy_background),
                logoRes = R.drawable.qzxy_logo,
                title = "趣智校园",
                supporting = "红色「趣智」图标",
                isSelected = selectedKey == AppIconKeys.QZXY,
                onClick = { onSelect(AppIconKeys.QZXY) }
            )
            SubtleDivider()
            IconOptionRow(
                foreground = R.drawable.ic_launcher_custom_foreground,
                background = colorResource(R.color.ic_launcher_custom_background),
                title = "自定义",
                supporting = if (hasCustom) "已保存自定义图片，会生成带该图标的桌面快捷方式" else "从相册选一张图片，生成桌面快捷方式",
                isSelected = selectedKey == AppIconKeys.CUSTOM,
                bitmap = customIcon,
                onClick = { onSelect(AppIconKeys.CUSTOM) },
                trailing = {
                    TextAction(if (hasCustom) "重新选择" else "从相册选择", onClick = onPickCustom)
                }
            )
        }
        Text(
            text = "切换后桌面入口立即更新，部分桌面需要重启应用或手动刷新才会显示新图标。" +
                "\n" +
                "系统不允许应用在安装后改写自己的启动图标，因此自定义图片会以桌面快捷方式的形式添加（需桌面支持固定快捷方式）。",
            modifier = Modifier.padding(top = 12.dp, start = 2.dp, end = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 一行图标选项：左侧图标预览、标题/说明，右侧可选操作与选中勾。 */
@Composable
private fun IconOptionRow(
    foreground: Int,
    background: Color,
    title: String,
    logoRes: Int? = null,
    supporting: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    bitmap: android.graphics.Bitmap? = null,
    trailing: (@Composable () -> Unit)? = null
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
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
            )
        } else {
            IconPreview(foreground = foreground, background = background, logoRes = logoRes)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailing != null) {
            trailing()
        }
        if (isSelected) {
            Glyph(
                R.drawable.ic_check,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 用「背景色 + 前景 vector」合成自适应图标预览，避免直接解码 adaptive-icon XML。 */
@Composable
private fun IconPreview(foreground: Int, background: Color, size: Dp = 48.dp, logoRes: Int? = null) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(11.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        if (logoRes != null) {
            val logo = android.graphics.BitmapFactory.decodeResource(
                androidx.compose.ui.platform.LocalContext.current.resources,
                logoRes
            )
            if (logo != null) {
                Image(
                    bitmap = logo.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Image(
                painter = painterResource(foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
