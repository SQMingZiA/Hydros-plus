package org.cqutosp.hydros.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.data.messages.CampusMessage
import org.cqutosp.hydros.ui.components.GlassIconButton
import org.cqutosp.hydros.ui.components.Glyph
import org.cqutosp.hydros.ui.components.InlineState
import org.cqutosp.hydros.ui.components.MessageBody
import org.cqutosp.hydros.ui.components.PageColumn
import org.cqutosp.hydros.ui.components.SubtleDivider
import org.cqutosp.hydros.ui.components.TextAction
import org.cqutosp.hydros.ui.components.hydrosPressable
import org.cqutosp.hydros.ui.components.htmlToPlainText
import org.cqutosp.hydros.ui.components.rememberHydrosHaptics

/**
 * 校园消息（底部一级页）。
 *
 * 版式与首页/钱包一致：顶部一行概览与操作，下面是平铺的消息行，不用卡片容器。
 * 未读行加粗并带一个主色圆点；点开进入详情，进入即标记已读。
 */
@Composable
fun CampusMessagesScreen(
    messages: List<CampusMessage>,
    readRevisions: Collection<String>,
    loading: Boolean = false,
    loaded: Boolean = false,
    error: String? = null,
    onRefresh: () -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onOpen: (CampusMessage) -> Unit = {},
) {
    val readSet = readRevisions.toSet()
    val unread = messages.count { it.revision !in readSet }
    PageColumn {
        error?.let { InlineState("消息同步失败", it, action = "重新同步", onAction = onRefresh) }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                when {
                    loading && messages.isEmpty() -> "正在同步…"
                    messages.isEmpty() -> "暂无消息"
                    unread > 0 -> unread.toString() + " 条未读"
                    else -> "全部已读"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (unread > 0) FontWeight.Medium else FontWeight.Normal,
                color = if (unread > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (unread > 0) TextAction("全部已读", onClick = onMarkAllRead)
            GlassIconButton(onClick = onRefresh, description = "刷新消息") {
                Glyph(R.drawable.ic_refresh, modifier = Modifier.size(18.dp))
            }
        }

        if (loading && messages.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        if (loaded && messages.isEmpty() && error == null) {
            InlineState(
                "还没有校园通知",
                icon = R.drawable.ic_message_circle,
            )
        }

        messages.forEach { message ->
            MessageRow(
                message = message,
                unread = message.revision !in readSet,
                onClick = { onOpen(message) },
            )
        }

    }
    LaunchedEffect(Unit) { if (!loaded && !loading) onRefresh() }
}

@Composable
private fun MessageRow(message: CampusMessage, unread: Boolean, onClick: () -> Unit) {
    val haptics = rememberHydrosHaptics()
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .hydrosPressable { haptics.light(); onClick() }
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (unread) Box(
                Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            )
            Text(
                message.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            Text(
                message.publishedLabel.substringBefore(' '),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val summary = htmlToPlainText(message.summary.ifBlank { message.body })
        if (summary.isNotBlank()) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

/** 消息详情：正文可能是纯文本或 HTML 公告，进入即标记已读。 */
@Composable
fun CampusMessageDetailScreen(
    message: CampusMessage?,
    onRead: (CampusMessage) -> Unit = {},
) {
    LaunchedEffect(message?.revision) { message?.let(onRead) }
    val colors = MaterialTheme.colorScheme
    PageColumn {
        if (message == null) {
            InlineState("消息不存在", icon = R.drawable.ic_message_circle)
        } else {
            // 标题区：日期与公告标签同一行，标题独占一行，下面用发丝线与正文分开。
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        message.publishedLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.primary.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "校园通知",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = colors.primary,
                        )
                    }
                }
                Text(
                    message.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    lineHeight = 30.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            SubtleDivider(horizontalPadding = 0.dp, modifier = Modifier.padding(top = 18.dp))
            val body = message.body.ifBlank { message.summary }
            if (body.isBlank()) {
                Box(Modifier.padding(top = 20.dp).fillMaxWidth()) {
                    InlineState(title = "这条消息没有正文", icon = R.drawable.ic_message_circle)
                }
            } else {
                Box(Modifier.padding(top = 16.dp, bottom = 24.dp).fillMaxWidth()) {
                    MessageBody(body, skipTitle = message.title)
                }
            }
        }
    }
}
