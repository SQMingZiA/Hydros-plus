package org.cqutosp.hydros.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.ui.components.ActionRow
import org.cqutosp.hydros.ui.components.decodeWallpaperBitmap
import org.cqutosp.hydros.ui.components.IconTile
import org.cqutosp.hydros.ui.components.PageColumn
import org.cqutosp.hydros.ui.components.PrimaryAction
import org.cqutosp.hydros.ui.components.SecondaryAction
import org.cqutosp.hydros.ui.components.Section
import kotlin.math.roundToInt

/**
 * 壁纸设置：预览 + 从相册选择/移除 + 压暗/模糊滑杆。
 *
 * @param path 壁纸绝对路径，null 表示使用渐变背景。
 */
@Composable
fun WallpaperScreen(
    path: String?,
    dim: Float,
    blur: Float,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onDim: (Float) -> Unit,
    onBlur: (Float) -> Unit,
) {
    val preview = remember(path) { loadWallpaper(path) }
    var dimDraft by remember(dim) { mutableFloatStateOf(dim) }
    var blurDraft by remember(blur) { mutableFloatStateOf(blur) }
    PageColumn {
        Section("壁纸") {
            Column(
                Modifier.padding(top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = "壁纸预览",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (dimDraft > 0f) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = dimDraft.coerceIn(0f, 1f)))
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconTile(R.drawable.ic_photo, size = 44.dp, iconSize = 22.dp)
                            Text(
                                text = "未设置壁纸",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "选择一张照片作为页面背景",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                ActionRow {
                    PrimaryAction(
                        label = "从相册选择",
                        modifier = Modifier.weight(1f),
                        onClick = onPick
                    )
                    SecondaryAction(
                        label = "移除背景",
                        modifier = Modifier.weight(1f),
                        enabled = path != null,
                        onClick = onClear
                    )
                }
            }
        }
        Section("显示效果") {
            Column(
                Modifier.padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EffectSlider(
                    label = "压暗",
                    value = dimDraft,
                    valueRange = 0f..0.6f,
                    valueText = "${(dimDraft * 100).roundToInt()}%",
                    enabled = path != null,
                    onValueChange = { dimDraft = it },
                    onValueChangeFinished = { onDim(dimDraft) }
                )
                EffectSlider(
                    label = "模糊",
                    value = blurDraft,
                    valueRange = 0f..25f,
                    valueText = "${blurDraft.roundToInt()}",
                    enabled = path != null,
                    onValueChange = { blurDraft = it },
                    onValueChangeFinished = { onBlur(blurDraft) }
                )
            }
        }
    }
}

@Composable
private fun EffectSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

private fun loadWallpaper(path: String?): ImageBitmap? {
    val file = path?.takeIf { it.isNotBlank() } ?: return null
    return decodeWallpaperBitmap(file, maxSize = 1600)
}
