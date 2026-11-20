package org.cqutosp.hydros.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.cqutosp.hydros.BuildConfig
import org.cqutosp.hydros.R
import org.cqutosp.hydros.data.update.*
import org.cqutosp.hydros.ui.components.*
import org.cqutosp.hydros.ui.state.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun publishedDate(value: String?): String = value?.let {
    runCatching { DateTimeFormatter.ofPattern("yyyy年M月d日").withZone(ZoneId.systemDefault()).format(Instant.parse(it)) }.getOrNull()
} ?: "发布日期未知"

@Composable
fun SoftwareUpdateScreen(
    updates: AppUpdateUiState,
    onHistory: () -> Unit = {},
    onInstall: () -> Unit = {},
    onContinue: () -> Unit = {},
    onCancel: () -> Unit = {},
    onCheck: () -> Unit,
) {
    val uri = LocalUriHandler.current
    val release = updates.release
    val install = updates.install
    PageColumn {
        when {
            updates.checking && install.phase == null -> InlineState(title = "正在检查最新版本…", loading = true)
            updates.hasUpdate || install.phase != null -> {
                // 顶部当前版本：大号数字 + 状态 chip（平铺，不用卡片）
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "当前版本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "v" + BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        StatusBadge(if (install.phase != null) "更新中" else "可更新")
                    }
                }
                if (release != null) {
                    Section("版本信息") {
                        MineRow("最新版本", supporting = release.name, icon = R.drawable.ic_update, tone = IconTone.PRIMARY)
                        SubtleDivider()
                        MineRow("发布日期", supporting = publishedDate(release.publishedAt), icon = R.drawable.ic_calendar, tone = IconTone.SECONDARY)
                    }
                }
                release?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Section("更新内容") { Markdown(notes, Modifier.padding(16.dp)) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    (install.error ?: updates.error)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (install.phase != null) {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    install.phase.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                install.percent?.let {
                                    Text(
                                        "${it.coerceIn(0, 100)}%",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            install.statusMessage?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (install.percent != null) {
                                LinearProgressIndicator(
                                    progress = { install.percent.coerceIn(0, 100) / 100f },
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)))
                            }
                        }
                        when (install.phase) {
                            InstallPhase.AWAITING_PERMISSION -> PrimaryAction("去允许安装", onClick = onContinue)
                            InstallPhase.AWAITING_CONFIRMATION -> PrimaryAction("继续安装", onClick = onContinue)
                            else -> Unit
                        }
                        if (install.canCancel) SecondaryAction("取消更新", onClick = onCancel)
                    } else if (release?.installAsset(Build.SUPPORTED_ABIS.toList()) != null) {
                        PrimaryAction("立即安装更新", icon = R.drawable.ic_download, onClick = onInstall)
                    } else {
                        Text(
                            "当前设备没有可用的更新包。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        release?.let {
                            SecondaryAction("前往更新", icon = R.drawable.ic_open_in_new, onClick = { uri.openUri(it.url) })
                        }
                    }
                    if (install.phase == null) {
                        TextAction("重新检查", onClick = onCheck, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
            updates.error != null -> InlineState(
                title = updates.error,
                icon = R.drawable.ic_info,
                action = "重试",
                onAction = onCheck
            )
            else -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Glyph(R.drawable.ic_check_circle, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                if (updates.checked) "当前已是最新版本" else "检查软件更新",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "v" + BuildConfig.VERSION_NAME,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    SecondaryAction("重新检查", icon = R.drawable.ic_refresh, onClick = onCheck)
                }
            }
        }
        Section("更多信息") {
            MineRow("更新历史", icon = R.drawable.ic_history, tone = IconTone.PRIMARY, onClick = onHistory)
            SubtleDivider()
            MineRow("源代码仓库", supporting = HYDROS_GITHUB_REPO, icon = R.drawable.ic_code,
                onClick = { uri.openUri(HYDROS_REPOSITORY) }, trailing = { Glyph(R.drawable.ic_open_in_new, Modifier.size(18.dp)) })
        }
    }
}

@Composable
fun ReleaseHistoryScreen(state: ReleaseHistoryUiState, onRelease: (AppRelease) -> Unit, onLoad: () -> Unit) {
    PageColumn {
        if (state.releases.isNotEmpty()) Section("全部版本") {
            state.releases.forEachIndexed { index, release ->
                if (index > 0) SubtleDivider()
                MineRow(
                    release.name,
                    supporting = publishedDate(release.publishedAt),
                    onClick = { onRelease(release) }
                )
            }
        }
        if (state.loading) InlineState(title = "正在加载…", loading = true)
        else if (state.error != null) InlineState(
            title = state.error,
            icon = R.drawable.ic_info,
            action = "重试",
            onAction = onLoad
        ) else if (state.loaded && state.releases.isEmpty() && !state.hasMore) InlineState(
            title = "暂无版本更新记录",
            icon = R.drawable.ic_history
        )
        if (!state.loading && state.error == null && state.hasMore) SecondaryAction("加载更多", onClick = onLoad)
    }
}

@Composable
fun ReleaseDetailScreen(release: AppRelease) {
    val uri = LocalUriHandler.current
    PageColumn {
        Section {
            MineRow("版本标签", supporting = release.tag, icon = R.drawable.ic_update, tone = IconTone.PRIMARY)
            SubtleDivider()
            MineRow("发布日期", supporting = publishedDate(release.publishedAt), icon = R.drawable.ic_calendar, tone = IconTone.SECONDARY)
        }
        Section("更新内容") { Markdown(release.notes.ifBlank { "暂无更新内容。" }, Modifier.padding(16.dp)) }
        SecondaryAction("在 GitHub 查看", icon = R.drawable.ic_open_in_new, onClick = { uri.openUri(release.url) })
    }
}
