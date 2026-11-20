package org.cqutosp.hydros.thirdparty

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 已绑定的寝室：把寝室号与一台代表性设备的 MAC 记下来，
 * 之后扫描到同寝室的设备可以直接识别，不必每次重新扫码。
 */
@Serializable
data class BoundDorm(
    val room: String,
    val deviceMac: String,
    val label: String = "",
    val boundAt: Long = 0L,
)

/** 12 位十六进制 MAC 规范化为 AA:BB:CC:DD:EE:FF；格式不合法返回 null。 */
fun normalizeDormMac(raw: String): String? {
    val hex = raw.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.uppercase()
    if (hex.length != 12) return null
    return hex.chunked(2).joinToString(":")
}

/** 寝室号只裁剪首尾空白，保留用户原本的写法。 */
fun normalizeDormRoom(raw: String): String = raw.trim()

/**
 * 寝室绑定的本地持久化。
 * 不走 HydrosStore（已存在的文件不能改），单独用 SharedPreferences 存一段 JSON。
 */
class DormBindingStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): BoundDorm? = withContext(Dispatchers.IO) {
        preferences.getString(KEY, null)?.let { raw ->
            runCatching { json.decodeFromString<BoundDorm>(raw) }.getOrNull()
        }
    }

    suspend fun save(bound: BoundDorm) {
        withContext(Dispatchers.IO) {
            preferences.edit().putString(KEY, json.encodeToString(bound)).apply()
        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            preferences.edit().remove(KEY).apply()
        }
    }

    companion object {
        const val FILE = "hydros.thirdparty"
        const val KEY = "bound.dorm"
    }
}
