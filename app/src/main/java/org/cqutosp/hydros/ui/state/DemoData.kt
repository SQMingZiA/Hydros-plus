package org.cqutosp.hydros.ui.state

import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.min

/**
 * 开发者模式下的演示数据：不访问校园网关，直接给出一组贴近真实的设备与账单，
 * 便于在无设备、无网络的环境里检查首页、钱包、用水报告等界面。
 */
object DemoData {
    val favoriteIds: Set<String> = setOf("demo-shower-615", "demo-drink-6f")

    val devices: List<DeviceUi> = listOf(
        DeviceUi(
            id = "demo-shower-615", name = "宿舍热水", location = "宿舍楼洗浴热水表-4#楼-6层-615房",
            category = DeviceCategory.SHOWER, status = DeviceStatus.AVAILABLE, signal = "良好",
            channel = ControlChannel.CLOUD, depositMilli = 7000, deviceId = "4-615",
            ledgerMac = "3C:61:A8:E0:61:50", connectMac = "3C:61:A8:E0:61:51",
            bigTypeId = "4", smallTypeId = "615", isBle = true
        ),
        DeviceUi(
            id = "demo-drink-6f", name = "6楼东饮水", location = "宿舍楼饮水机-4号楼-6层-6楼东直饮水",
            category = DeviceCategory.DRINK, status = DeviceStatus.AVAILABLE, signal = "良好",
            channel = ControlChannel.CLOUD, depositMilli = 1800, deviceId = "6F-E",
            ledgerMac = "A4:C1:38:11:22:33", connectMac = "A4:C1:38:11:22:34",
            bigTypeId = "5", smallTypeId = "6FE", isBle = false
        ),
        DeviceUi(
            id = "demo-wash-3f", name = "3楼公共洗漱", location = "宿舍楼洗漱台-4号楼-3层-公共洗漱区",
            category = DeviceCategory.WASH, status = DeviceStatus.BUSY, signal = "一般",
            channel = ControlChannel.CLOUD, depositMilli = 500, deviceId = "3F-W",
            ledgerMac = "A4:C1:38:11:22:41", connectMac = "A4:C1:38:11:22:42",
            bigTypeId = "6", smallTypeId = "3FW", isBle = false
        ),
    )

    /** 用户在演示模式里手动新增的设备；online 决定它在演示里的在线状态。 */
    fun customDevice(index: Int, name: String, location: String, category: DeviceCategory, online: Boolean = true): DeviceUi {
        val id = "demo-custom-" + index + "-" + System.currentTimeMillis()
        val (big, small) = when (category) {
            DeviceCategory.SHOWER -> "4" to ("9" + index + "5")
            DeviceCategory.DRINK -> "5" to ("D" + index)
            else -> "6" to ("W" + index)
        }
        val deposit = when (category) {
            DeviceCategory.SHOWER -> 7000L
            DeviceCategory.DRINK -> 1800L
            else -> 500L
        }
        return DeviceUi(
            id = id, name = name, location = location, category = category,
            status = if (online) DeviceStatus.AVAILABLE else DeviceStatus.OFFLINE,
            signal = if (online) "良好" else "离线", channel = ControlChannel.CLOUD,
            depositMilli = deposit, deviceId = "DEMO-" + index,
            ledgerMac = "3C:61:A8:E0:7" + index + ":50",
            connectMac = "3C:61:A8:E0:7" + index + ":51",
            bigTypeId = big, smallTypeId = small, isBle = category == DeviceCategory.SHOWER,
        )
    }

    val wallet: WalletUi = WalletUi(totalMilli = 30090)

    /** 本月若干条消费 + 上月几条，金额贴近真实热水/直饮水单价。 */
    fun bills(now: LocalDateTime = LocalDateTime.now()): List<BillUi> {
        val today = now.toLocalDate()
        val times = listOf(
            Triple(0, 12, 1), Triple(0, 21, 15), Triple(1, 7, 20), Triple(2, 18, 9), Triple(2, 22, 40),
            Triple(4, 8, 5), Triple(5, 19, 32), Triple(6, 13, 18), Triple(8, 21, 6), Triple(9, 7, 48),
            Triple(11, 20, 25), Triple(13, 18, 52), Triple(15, 9, 12), Triple(17, 22, 3), Triple(19, 12, 44),
            Triple(21, 20, 8), Triple(24, 7, 33), Triple(28, 19, 55),
        )
        return times.mapIndexed { index, (daysAgo, hour, minute) ->
            val date = today.minusDays(daysAgo.toLong())
            val time = date.atTime(hour, minute)
            val shower = index % 3 != 2
            val cents = if (shower) 42 else 18
            BillUi(
                id = "demo-bill-" + index,
                title = if (shower) "洗浴热水表" else "2000直饮水",
                location = if (shower) "宿舍楼洗浴热水表-4#楼-6层-615房" else "宿舍楼饮水机-4号楼-6层-6楼东直饮水",
                category = if (shower) DeviceCategory.SHOWER else DeviceCategory.DRINK,
                amountMilli = cents.toLong() * 10,
                time = time,
                consumeDate = time.toLocalDate().toString(),
                orderNo = "demo-" + (1000 + index),
                durationText = if (shower) (8 + index % 9).toString() + "分钟" else null,
                recharge = false,
                // 演示数据给真实用水量，报告页/详情页就能显示「实测」而不是估算。
                waterMilliLiters = if (shower) 52000L + (index % 5) * 9000L else 6800L + (index % 4) * 1200L,
            )
        }.sortedByDescending { it.time }
    }

    fun rechargeBills(now: LocalDateTime = LocalDateTime.now()): List<BillUi> = listOf(
        BillUi(
            id = "demo-recharge-1", title = "支付宝充值", location = "聚合收银台",
            category = null, amountMilli = 5000, time = now.minusDays(3),
            consumeDate = now.minusDays(3).toLocalDate().toString(), orderNo = "demo-recharge-1", recharge = true
        ),
        BillUi(
            id = "demo-recharge-2", title = "校园卡充值", location = "校园卡",
            category = null, amountMilli = 2000, time = now.minusDays(12),
            consumeDate = now.minusDays(12).toLocalDate().toString(), orderNo = "demo-recharge-2", recharge = true
        ),
    )

    /** 演示模式的用水报告：用真实模型从演示账单推导，保证与钱包页数字一致。 */
    fun report(limitCents: Long?, now: LocalDateTime = LocalDateTime.now()): WaterUsageReport {
        val month = YearMonth.from(now)
        val list = bills(now) + rechargeBills(now)
        return runCatching { WaterUsageReport.of(list, month, limitCents) }.getOrDefault(WaterUsageReport())
    }

    val limitCents: Long = 6000

    fun clampDay(day: Int, max: Int): Int = min(day, max)
}
