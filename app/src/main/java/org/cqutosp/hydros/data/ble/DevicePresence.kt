package org.cqutosp.hydros.data.ble

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeoutOrNull
import org.cqutosp.hydros.ui.state.DeviceUi

/**
 * 用一次短时间的蓝牙广播扫描确认设备是否真的在附近。
 *
 * 云端可能仍把设备标记为在线，但用户已经走远、收不到蓝牙广播时，不应该还能开始用水。
 * 扫描无法进行（没有附近设备权限、蓝牙关闭、预览/测试环境）时返回 null，由调用方沿用云端状态。
 */
class DevicePresence(private val scanner: NearbyScanner) {
    /** 扫描窗口内出现过的广播地址；扫描不可用时返回 null。 */
    suspend fun seenMacs(windowMs: Long = DEFAULT_WINDOW_MS): Set<String>? = try {
        val seen = linkedSetOf<String>()
        // 真实扫描流不会自行结束：超时说明确实完整扫描了一个窗口。
        val completed = withTimeoutOrNull(windowMs) {
            scanner.advertisements().collect { advertisement ->
                MacFormat.normalize(advertisement.mac)?.let(seen::add)
            }
        }
        // NoNearbyScanner（预览/单元测试）会立刻结束，不能据此判定设备不在附近。
        if (completed == null) seen else null
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 3_000L
    }
}

/** 设备可能使用的广播地址，同时覆盖二维码里的台账地址与扫描到的连接地址。 */
internal fun DeviceUi.nearbyAddresses(): Set<String> =
    (MacFormat.lookupVariants(connectMac) + MacFormat.lookupVariants(ledgerMac))
        .mapNotNull(MacFormat::normalize)
        .toSet()

/** 广播里是否出现了这台设备；地址不可解析时按「无法判断」处理，避免误判离线。 */
internal fun DeviceUi.matchesSeen(seen: Set<String>): Boolean {
    val addresses = nearbyAddresses()
    return addresses.isEmpty() || addresses.any { it in seen }
}
