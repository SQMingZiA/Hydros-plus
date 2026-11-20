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

/**
 * 桌面图标选择页：默认图标与趣智校园预设。
 *
 * 别名切换由 [org.cqutosp.hydros.ui.state.AppIconController.apply] 完成，本页只负责展示与回调。
 */
@Composable
fun AppIconScreen(
    current: String,
    onSelect: (String) -> Unit,
) {
    val selectedKey = AppIconKeys.normalize(current)
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
                foreground = R.drawable.ic_launcher_qzxy_foreground,
                background = colorResource(R.color.ic_launcher_qzxy_background),
                logoRes = R.drawable.qzxy_logo,
                title = "趣智校园",
                isSelected = selectedKey == AppIconKeys.QZXY,
                onClick = { onSelect(AppIconKeys.QZXY) }
            )
        }
        Text(
            text = "切换后桌面入口立即更新，部分桌面需要重启应用或手动刷新才会显示新图标。",
            modifier = Modifier.padding(top = 12.dp, start = 2.dp, end = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 一行图标选项：左侧图标预览、标题，右侧选中勾。 */
@Composable
private fun IconOptionRow(
    foreground: Int,
    background: Color,
    title: String,
    logoRes: Int? = null,
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
        IconPreview(foreground = foreground, background = background, logoRes = logoRes)
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
