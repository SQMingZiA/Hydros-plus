package org.cqutosp.hydros.data.messages

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.cqutosp.hydros.data.network.ApiFailure
import org.cqutosp.hydros.data.network.CampusTransport
import org.cqutosp.hydros.data.network.Credentials
import org.cqutosp.hydros.data.network.obj
import org.cqutosp.hydros.data.network.requireSuccess
import org.cqutosp.hydros.data.network.text

/**
 * 校园网关的推送消息。
 *
 * 协议见 `project/push/list`：pushId / pushTitle / pushTime / pushContent(摘要) / fullContent(正文)。
 * 正文为空时退回摘要。用 "id|publishedAt" 作为版本号，同一条消息被服务端改写也能识别。
 */
data class CampusMessage(
    val id: String,
    val title: String,
    val publishedAt: String,
    val summary: String,
    val body: String,
) {
    val revision: String get() = id + "|" + publishedAt
    /** 列表里展示的时间文本，尽力格式化为 "2026-11-20 09:30"。 */
    val publishedLabel: String get() = publishedAt.replace('T', ' ').take(16)
}

interface MessageRepository {
    suspend fun load(auth: Credentials): List<CampusMessage>
}

class RealMessageRepository(private val transport: CampusTransport) : MessageRepository {
    override suspend fun load(auth: Credentials): List<CampusMessage> {
        val response = transport.get("project/push/list", auth = auth)
        response.requireSuccess()
        val rows = when (val data = response.data) {
            is JsonArray -> data
            is JsonObject -> (data["list"] as? JsonArray) ?: (data["records"] as? JsonArray)
            ?: (data["rows"] as? JsonArray) ?: throw ApiFailure(null, "消息列表格式不正确")
            else -> throw ApiFailure(null, "消息列表格式不正确")
        }
        return rows.mapNotNull { item -> parseMessage(item as? JsonObject ?: return@mapNotNull null) }
            .distinctBy { it.revision }
            .sortedByDescending { it.publishedAt }
    }
}

private fun parseMessage(obj: JsonObject): CampusMessage? {
    val id = obj.text("pushId")?.takeIf(String::isNotBlank) ?: return null
    val publishedAt = obj.text("pushTime")?.takeIf(String::isNotBlank) ?: return null
    val summary = obj.text("pushContent").orEmpty()
    val body = obj.text("fullContent")?.takeIf(String::isNotBlank) ?: summary
    return CampusMessage(
        id = id,
        title = obj.text("pushTitle")?.takeIf(String::isNotBlank) ?: "校园通知",
        publishedAt = publishedAt,
        summary = summary,
        body = body,
    )
}

/**
 * 消息回执：记录首次同步基线、历史出现过的版本号、以及已读版本号。
 * 首次同步只建基线，不把历史消息当新消息弹出。
 */
data class MessageReceipts(
    val initialized: Boolean = false,
    val known: List<String> = emptyList(),
    val read: List<String> = emptyList(),
) {
    private val knownSet get() = known.toSet()
    fun unreadOf(messages: List<CampusMessage>): List<CampusMessage> {
        val readSet = read.toSet()
        return messages.filter { it.revision !in readSet }
    }
    fun markRead(revisions: Collection<String>): MessageReceipts {
        val merged = (read + revisions).distinct().takeLast(MAX_ITEMS)
        return copy(read = merged)
    }
    fun markAllRead(messages: List<CampusMessage>): MessageReceipts = markRead(messages.map { it.revision })
    /** 建立/更新基线：新版本号并入 known，最多保留最近 300 条。 */
    fun withBaseline(messages: List<CampusMessage>): MessageReceipts {
        val merged = (known + messages.map { it.revision }).distinct().takeLast(MAX_ITEMS)
        return copy(initialized = true, known = merged)
    }
    companion object { const val MAX_ITEMS = 300 }
}
