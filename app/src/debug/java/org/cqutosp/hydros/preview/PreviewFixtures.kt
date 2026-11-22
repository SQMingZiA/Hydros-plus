package org.cqutosp.hydros.preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import org.cqutosp.hydros.data.storage.FeedbackSettings
import org.cqutosp.hydros.data.update.AppRelease
import org.cqutosp.hydros.data.update.ReleaseAsset
import org.cqutosp.hydros.thirdparty.BoundDorm
import org.cqutosp.hydros.thirdparty.GeneratedUseCode
import org.cqutosp.hydros.thirdparty.RechargeChannel
import org.cqutosp.hydros.thirdparty.RechargeOrder
import org.cqutosp.hydros.thirdparty.RechargePackage
import org.cqutosp.hydros.thirdparty.RechargeUi
import org.cqutosp.hydros.thirdparty.UseCodeState
import org.cqutosp.hydros.thirdparty.UseCodeUi
import org.cqutosp.hydros.ui.state.*
import org.cqutosp.hydros.ui.state.ProjectField
import org.cqutosp.hydros.ui.theme.ThemeMode
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Random

/**
 * 调试预览用的假数据，全部取自真实使用日志（南阳科技职业学院 4#楼 6 层的一间宿舍），
 * 只给 debug source set 用，不参与 release 构建。
 *
 * 数据一律固定随机种子，保证每次截图内容一致，方便对比改动前后的界面。
 */

/** 预览账号：南阳科技职业学院 4#楼 6 层的一间宿舍。 */
internal const val PREVIEW_SCHOOL = "南阳科技职业学院"
internal const val PREVIEW_STUDENT_NAME = "王小明"
internal const val PREVIEW_STUDENT_PHONE = "13800138000"
internal const val PREVIEW_STUDENT_ID = "2023101234"

/** 钱包余额 ¥30.09（单位毫元，界面按“分”显示）。 */
internal const val PREVIEW_BALANCE_MILLI = 30_090L

/** 洗浴热水表的预扣金额 ¥0.70（单位毫元）。 */
internal const val PREVIEW_DEPOSIT_MILLI = 700L

/** 每月用水限额 ¥60，配合模拟账单可以同时看到进度条与“预计超出限额”的建议。 */
internal const val PREVIEW_LIMIT_CENTS = 6_000L

// ---------------------------------------------------------------------------
// 设备
// ---------------------------------------------------------------------------

/** 洗浴热水表：日志里的描述形如「热水表:洗浴热水表-宿舍楼洗浴热水表-4#楼-6层-615房」。 */
private fun previewShower(id: String, room: String, mac: String, deviceId: String, status: DeviceStatus, signal: String = "良好") =
    DeviceUi(
        id = id,
        name = "洗浴热水表",
        location = "宿舍楼洗浴热水表-4#楼-6层-$room",
        category = DeviceCategory.SHOWER,
        status = status,
        signal = signal,
        channel = ControlChannel.CLOUD,
        depositMilli = PREVIEW_DEPOSIT_MILLI,
        deviceId = deviceId,
        ledgerMac = mac,
        connectMac = mac.chunked(2).joinToString(":"),
    )

/** 饮水机 / 热水机：日志里的描述形如「饮水机:2000直饮水-宿舍楼饮水机-4号楼-6层-6楼东直饮水」。 */
private fun previewDrink(
    id: String,
    name: String,
    location: String,
    mac: String,
    deviceId: String,
    status: DeviceStatus,
    signal: String = "中等",
    ble: Boolean = false,
) = DeviceUi(
    id = id,
    name = name,
    location = location,
    category = DeviceCategory.DRINK,
    status = status,
    signal = signal,
    channel = if (ble) ControlChannel.BLE else ControlChannel.CLOUD,
    deviceId = deviceId,
    ledgerMac = mac,
    connectMac = mac.chunked(2).joinToString(":"),
    isBle = ble,
)

/** 楼层热水（洗漱）：日志里显示成「6楼东热水中」，表示这台设备正在被占用。 */
private fun previewWash(id: String, name: String, location: String, mac: String, deviceId: String, status: DeviceStatus, signal: String = "中等") =
    DeviceUi(
        id = id,
        name = name,
        location = location,
        category = DeviceCategory.WASH,
        status = status,
        signal = signal,
        channel = ControlChannel.BLE,
        deviceId = deviceId,
        ledgerMac = mac,
        connectMac = mac.chunked(2).joinToString(":"),
        isBle = true,
    )

/** 同一间宿舍楼里的 9 台设备：4 台洗浴热水表、3 台直饮水/热水机、2 台楼层热水。 */
internal fun previewDeviceList(): List<DeviceUi> = listOf(
    previewShower("4-615", "615房", "3C61A8E06150", "615", DeviceStatus.BUSY),
    previewShower("4-613", "613房", "3C61A8E06130", "613", DeviceStatus.AVAILABLE),
    previewShower("4-606", "606房", "3C61A8E06060", "606", DeviceStatus.OFFLINE, signal = "未测量"),
    previewShower("4-605", "605房", "3C61A8E06050", "605", DeviceStatus.AVAILABLE, signal = "较弱"),
    previewDrink("4-6drink", "2000直饮水", "宿舍楼饮水机-4号楼-6层-6楼东直饮水", "3C61A8E0D601", "601", DeviceStatus.AVAILABLE, signal = "良好"),
    previewDrink("4-6hot", "2000热水", "宿舍楼饮水机-4号楼-6层-6楼东热水", "3C61A8E0D602", "602", DeviceStatus.AVAILABLE, ble = true),
    previewWash("4-6east", "6楼东热水", "宿舍楼热水-4号楼-6层-东侧", "3C61A8E0D603", "603", DeviceStatus.BUSY, signal = "较弱"),
    previewWash("4-6west", "6楼西热水", "宿舍楼热水-4号楼-6层-西侧", "3C61A8E0D604", "604", DeviceStatus.BUSY),
    previewDrink("4-5drink", "2000直饮水", "宿舍楼饮水机-4号楼-5层-5楼东直饮水", "3C61A8E0D501", "501", DeviceStatus.OFFLINE, signal = "未测量"),
)

/** 收藏 3 台：常去的 615 房热水表、6 楼东直饮水、605 房热水表。 */
internal fun previewFavoriteIds(): Set<String> = setOf("4-615", "4-6drink", "4-605")

/** 最近使用 2 台，首页与“我的”都会展示。 */
internal fun previewRecentIds(): List<String> = listOf("4-615", "4-6drink")

/** 设备备注，展示在设备名下方。 */
internal fun previewDeviceRemarks(): Map<String, String> = mapOf(
    "4-615" to "宿舍热水",
    "4-6drink" to "6楼东饮水",
    "4-6east" to "室友常用",
)

// ---------------------------------------------------------------------------
// 进行中的会话
// ---------------------------------------------------------------------------

/** 615 房热水表上正在进行的一次使用（已用 7 分钟），用于设备详情页与使用页。 */
internal fun previewSession(device: DeviceUi = previewDeviceList().first()): SessionUi {
    val startedAt = System.currentTimeMillis() - 7 * 60_000L
    val start = LocalDateTime.now().minusMinutes(7).withNano(0)
    return SessionUi(
        device = device,
        ownerKey = "preview-account",
        startedAt = startedAt,
        stage = SessionStage.USING,
        orderNo = "NY20261120001",
        consumeDate = start.toString(),
    )
}

internal fun previewObservation(session: SessionUi): SessionObservation = SessionObservation(
    deviceId = session.device.id,
    startedAt = session.startedAt,
    lastConfirmedAt = System.currentTimeMillis() - 5_000L,
)

// ---------------------------------------------------------------------------
// 账单
// ---------------------------------------------------------------------------

/** 真实日志里的用水时段：7-8、12-13、18-19、21-23 点。 */
private val PREVIEW_HOURS = intArrayOf(7, 7, 8, 12, 12, 13, 18, 18, 19, 21, 21, 22, 22, 23)

/**
 * 本月 36 条、上月 25 条、上上月 28 条账单。
 * 金额取真实日志里的四档实扣：0.105 / 0.175 / 0.42 / 0.70 元（amountMilli 单位是毫元）。
 */
internal fun previewBills(month: YearMonth = YearMonth.now()): List<BillUi> =
    previewMonthBills(month, count = 36, seed = 20_261_120L, offset = 0) +
        previewMonthBills(month.minusMonths(1), count = 25, seed = 20_261_101L, offset = 100) +
        previewMonthBills(month.minusMonths(2), count = 28, seed = 20_261_015L, offset = 200)

private fun previewMonthBills(month: YearMonth, count: Int, seed: Long, offset: Int): List<BillUi> {
    val random = Random(seed)
    val devices = previewDeviceList()
    val currentMonth = month == YearMonth.now()
    val lastDay = if (currentMonth) maxOf(1, LocalDate.now().dayOfMonth - 1) else month.lengthOfMonth()
    return List(count) { index ->
        val category = when {
            index % 7 == 0 -> DeviceCategory.DRINK
            index % 7 == 3 -> DeviceCategory.WASH
            else -> DeviceCategory.SHOWER
        }
        // 账单标题取设备名、location 取完整位置，界面上拼起来就是日志里的
        // 「饮水机:2000直饮水-宿舍楼饮水机-4号楼-6层-6楼东直饮水」。
        val device = when (category) {
            DeviceCategory.DRINK -> if (index % 5 == 0) devices[8] else devices[4 + index % 2]
            DeviceCategory.WASH -> devices[6 + index % 2]
            DeviceCategory.SHOWER -> devices[index % 4]
        }
        val amountMilli = when (category) {
            DeviceCategory.DRINK -> if (index % 3 == 0) 175L else 105L
            DeviceCategory.WASH -> 175L
            DeviceCategory.SHOWER -> if (index % 9 == 0) 700L else 420L
        }
        val durationText = when (category) {
            DeviceCategory.DRINK -> "${20 + random.nextInt(40)}秒"
            DeviceCategory.WASH -> "${3 + random.nextInt(6)}分钟"
            else -> "${6 + random.nextInt(20)}分钟"
        }
        val day = 1 + random.nextInt(if (currentMonth) lastDay else month.lengthOfMonth())
        val time = month.atDay(day).atTime(PREVIEW_HOURS[index % PREVIEW_HOURS.size], random.nextInt(60))
        BillUi(
            id = "ny-${offset + index}",
            title = device.name,
            location = device.location,
            category = category,
            amountMilli = amountMilli,
            time = time,
            consumeDate = time.toString(),
            orderNo = "NY%04d".format(offset + index),
            durationText = durationText,
        )
    }
}

/** 账单详情页示例：本月一条 615 房的热水消费。 */
internal fun previewBillForDetail(month: YearMonth = YearMonth.now()): BillUi {
    val bills = previewBills(month)
    return bills.firstOrNull { it.month == month && it.category == DeviceCategory.SHOWER && it.amountMilli == 420L }
        ?: bills.first()
}

// ---------------------------------------------------------------------------
// 账户 / 水报告状态
// ---------------------------------------------------------------------------

internal fun previewAccount(): AccountUi =
    AccountUi(PREVIEW_STUDENT_NAME, PREVIEW_STUDENT_PHONE, PREVIEW_STUDENT_ID, PREVIEW_SCHOOL,
        operator = "广西泰力电器有限公司",
        projectFields = listOf(
            ProjectField("项目 ID", "3824"),
            ProjectField("项目名称", PREVIEW_SCHOOL),
            ProjectField("客服电话", "150****5891"),
            ProjectField("租户 ID", "893"),
            ProjectField("运营商家", "广西泰力电器有限公司"),
            ProjectField("是否中学", "0"),
            ProjectField("坐标", "112.15628,32.679582"),
            ProjectField("数据源名称", "wit-order-2"),
            ProjectField("是否迁移", "1"),
            ProjectField("迁移时间", "2025-01-17 09:28:29"),
        ))

/**
 * 截图用的一整份 UI 状态：设备、收藏、最近使用、备注、进行中的会话、三个月的账单与用水报告。
 * 其余字段保持默认值，只填页面真正会读到的部分。
 */
internal fun previewUiState(
    month: YearMonth = YearMonth.now(),
    avatarPath: String? = null,
    limitCents: Long? = PREVIEW_LIMIT_CENTS,
): HydrosUiState {
    val devices = previewDeviceList()
    val bills = previewBills(month)
    val session = previewSession(devices.first())
    return HydrosUiState(
        initializing = false,
        signedIn = true,
        onboardingCompleted = true,
        account = previewAccount(),
        wallet = WalletUi(PREVIEW_BALANCE_MILLI),
        devices = devices,
        connectedDeviceIds = previewRecentIds(),
        favoriteDeviceIds = previewFavoriteIds(),
        deviceRemarks = previewDeviceRemarks(),
        observation = previewObservation(session),
        bills = bills,
        session = session,
        theme = ThemeMode.LIGHT,
        feedback = FeedbackSettings(),
        fontSizeScale = 1f,
        homeConnectCardVisible = true,
        waterLimitCents = limitCents,
        waterUsage = WaterUsageReport.of(bills, month, limitCents),
        avatarPath = avatarPath,
        month = month.toString(),
    )
}

// ---------------------------------------------------------------------------
// 第三方面板：使用码 / 寝室绑定 / 充值
// ---------------------------------------------------------------------------

internal fun previewUseCodeUi(): UseCodeUi = UseCodeUi(
    loading = false,
    state = UseCodeState(useCode = "483920", enabled = true, resetAvailability = 2),
    candidate = GeneratedUseCode(useCode = "917264", remainTimes = 2),
    candidateAt = System.currentTimeMillis(),
)

/** 已绑定的寝室：4#楼 615 房的热水表。 */
internal fun previewDorm(): BoundDorm = BoundDorm(
    room = "4#楼 615 房",
    deviceMac = "3C:61:A8:E0:61:50",
    label = "宿舍热水",
    boundAt = LocalDate.of(2026, 10, 20).atStartOfDay().toInstant(ZoneOffset.ofHours(8)).toEpochMilli(),
)

internal fun previewRechargePackages(): List<RechargePackage> = listOf(
    RechargePackage(id = 1, amount = "20", bonus = null),
    RechargePackage(id = 2, amount = "50", bonus = "5"),
    RechargePackage(id = 3, amount = "100", bonus = "20"),
    RechargePackage(id = 4, amount = "200", bonus = "60"),
)

internal fun previewRechargeUi(): RechargeUi = RechargeUi(
    loading = false,
    packages = previewRechargePackages(),
    channels = listOf(RechargeChannel.ALIPAY, RechargeChannel.JUHE),
    selectedPackageId = 2,
    selectedChannel = RechargeChannel.ALIPAY,
    balanceCents = 3_009,
    notice = "到账金额以服务端余额为准，支付结果稍后刷新。",
)

/** 已下单待支付的一张订单，用于截图“待支付”区块。 */
internal fun previewPendingOrder(): RechargeOrder =
    RechargeOrder.Browser("https://pay.example.edu.cn/cashier/preview")

// ---------------------------------------------------------------------------
// 应用更新 / 发布历史
// ---------------------------------------------------------------------------

internal fun previewRelease(version: String = "1.5.0"): AppRelease = AppRelease(
    version = version,
    tag = "v$version",
    notes = """
        ### 本次更新
        - 扫码页重新设计：取景框、四角角标与扫描线动画
        - 首页收藏设备支持自定义备注
        - 修复弱网下账单刷新失败的问题
    """.trimIndent(),
    publishedAt = "2026-11-18T02:30:00Z",
    assets = listOf(
        ReleaseAsset(
            name = "Hydros-v$version.apk",
            url = "https://github.com/CQUT-OpenProject/Hydros/releases/download/v$version/Hydros-$version.apk",
            size = 18_432_000L,
            sha256 = null,
        ),
    ),
)

internal fun previewUpdateState(): AppUpdateUiState = AppUpdateUiState(
    checked = true,
    hasUpdate = true,
    release = previewRelease(),
    lastChecked = Instant.now(),
)

internal fun previewReleaseHistory(): ReleaseHistoryUiState = ReleaseHistoryUiState(
    loaded = true,
    releases = listOf(previewRelease("1.5.0"), previewRelease("1.4.2"), previewRelease("1.4.0")),
    hasMore = true,
)

// ---------------------------------------------------------------------------
// 头像
// ---------------------------------------------------------------------------

/** 生成一张示例头像，验证“我的”页头像位置与裁切效果。 */
internal fun demoAvatarFile(context: android.content.Context): String {
    val file = File(context.filesDir, "preview-avatar.jpg")
    if (file.exists()) return file.absolutePath
    val size = 256
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(),
            0xFF1677FF.toInt(), 0xFF39A0FF.toInt(), Shader.TileMode.CLAMP)
    }
    canvas.drawPaint(background)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 132f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("水", size / 2f, size / 2f + 46f, text)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    bitmap.recycle()
    return file.absolutePath
}
