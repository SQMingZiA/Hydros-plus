package org.cqutosp.hydros.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.data.storage.FontSizeOption
import org.cqutosp.hydros.data.storage.PostSessionDestination
import org.cqutosp.hydros.ui.components.*
import org.cqutosp.hydros.ui.theme.LocalSystemDensity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DisplaySettingsScreen(savedScale: Float, onPreview: (Float?) -> Unit, onSave: suspend (Float) -> Boolean,
    postSessionDestination: PostSessionDestination, onPostSessionDestination: (PostSessionDestination) -> Unit,
    homeConnectCardVisible: Boolean = true, onHomeConnectCardVisible: (Boolean) -> Unit = {}) {
    val options = FontSizeOption.entries
    var draft by remember(savedScale) { mutableFloatStateOf(savedScale) }
    var saving by remember { mutableStateOf(false) }
    val index = options.indexOfFirst { it.scale == draft }.coerceAtLeast(0)
    val haptics = rememberHydrosHaptics()
    val scope = rememberCoroutineScope()
    val latestPreview by rememberUpdatedState(onPreview)
    DisposableEffect(Unit) { onDispose { latestPreview(null) } }
    PageColumn {
        Section("首页") {
            SwitchRow(
                title = "连接身边的设备卡片",
                supporting = "关闭后首页不显示",
                checked = homeConnectCardVisible,
                onCheckedChange = onHomeConnectCardVisible,
                icon = R.drawable.ic_scan
            )
        }
        Section("字体大小") {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "A",
                        modifier = Modifier.heightIn(min = 44.dp).wrapContentHeight(Alignment.CenterVertically),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalDensity provides LocalSystemDensity.current) {
                            Slider(
                                value = index.toFloat(), valueRange = 0f..3f, steps = 2, enabled = !saving,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    activeTickColor = Color.Transparent,
                                    inactiveTickColor = Color.Transparent,
                                ),
                                modifier = Modifier.fillMaxWidth().semantics {
                                    contentDescription = "字体大小"
                                    stateDescription = options[index].label
                                },
                                onValueChange = { value ->
                                    val next = options[value.roundToInt().coerceIn(0, 3)].scale
                                    if (next != draft) { haptics.medium(); draft = next; onPreview(next) }
                                },
                                onValueChangeFinished = {
                                    if (draft != savedScale) {
                                        val selected = draft
                                        saving = true
                                        scope.launch {
                                            try {
                                                if (!onSave(selected)) draft = savedScale
                                            } finally { saving = false; onPreview(null) }
                                        }
                                    } else onPreview(null)
                                },
                            )
                            Box(Modifier.fillMaxWidth()) {
                                // "标准" 是第二档：居中在前 2/3 宽度的中点，即 0..3 的第 1 个停靠点。
                                Text("标准", Modifier.fillMaxWidth(2f / 3f), textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(
                        "A",
                        modifier = Modifier.heightIn(min = 44.dp).wrapContentHeight(Alignment.CenterVertically),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Section("结算后跳转") {
            Column(Modifier.selectableGroup()) {
                PostSessionDestination.entries.forEachIndexed { index, destination ->
                    RadioRow(
                        title = destination.label,
                        selected = destination == postSessionDestination,
                        onClick = { onPostSessionDestination(destination) }
                    )
                    if (index < PostSessionDestination.entries.lastIndex) SubtleDivider()
                }
            }
        }
    }
}