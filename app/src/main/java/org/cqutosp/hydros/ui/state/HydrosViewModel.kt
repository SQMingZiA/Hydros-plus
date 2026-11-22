package org.cqutosp.hydros.ui.state

import org.cqutosp.hydros.data.feedback.*
import org.cqutosp.hydros.data.storage.FeedbackSettings
import org.cqutosp.hydros.data.storage.PostSessionDestination
import org.cqutosp.hydros.data.storage.NotificationSettings
import org.cqutosp.hydros.data.notify.HydrosNotifier
import org.cqutosp.hydros.data.notify.NotificationTopic
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.cqutosp.hydros.data.HydrosDependencies
import org.cqutosp.hydros.data.network.*
import org.cqutosp.hydros.data.messages.CampusMessage
import org.cqutosp.hydros.data.messages.MessageReceipts
import org.cqutosp.hydros.data.messages.RealMessageRepository
import org.cqutosp.hydros.data.repository.ReauthenticationNotice
import org.cqutosp.hydros.data.update.UpdateCheckSetting
import org.cqutosp.hydros.data.update.UpdateSource
import org.cqutosp.hydros.data.update.UpdateSourceSetting
import org.cqutosp.hydros.data.repository.BillHistory
import org.cqutosp.hydros.data.repository.DevicePreparation
import org.cqutosp.hydros.data.repository.NearbyDiscovery
import org.cqutosp.hydros.data.repository.NearbyDiscoveryState
import org.cqutosp.hydros.ui.theme.ThemeMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDateTime
import java.time.YearMonth

data class HydrosUiState(val initializing: Boolean = true, val signedIn: Boolean = false,
    val onboardingCompleted: Boolean = false, val reauthentication: ReauthenticationNotice? = null,
    val account: AccountUi = AccountUi(), val wallet: WalletUi = WalletUi(), val devices: List<DeviceUi> = emptyList(),
    val connectedDeviceIds: List<String> = emptyList(), val favoriteDeviceIds: Set<String> = emptySet(),
    val deviceRemarks: Map<String, String> = emptyMap(),
    val nearby: NearbyDiscoveryState = NearbyDiscoveryState(),
    val observation: SessionObservation = SessionObservation(),
    val bills: List<BillUi> = emptyList(), val session: SessionUi? = null, val connectingId: String? = null,
    val connectionStage: ConnectionStage = ConnectionStage.IDLE, val theme: ThemeMode = ThemeMode.SYSTEM,
    val feedback: FeedbackSettings = FeedbackSettings(),
    val fontSizeScale: Float = 1f,
    val homeConnectCardVisible: Boolean = true, val waterLimitCents: Long? = null,
    val waterLimitNotifiedMonth: String? = null, val waterLimitBlock: Boolean = false,
    val waterUsage: WaterUsageReport = WaterUsageReport(),
    val avatarPath: String? = null,
    val month: String = YearMonth.now().toString(), val billType: BillType = BillType.CONSUMPTION,
    val loadingBills: Boolean = false,
    val messageSeverity: FeedbackSeverity = FeedbackSeverity.ERROR, val messageSource: String = "user",
    val messageAction: RecoveryAction? = null, val connectionAction: RecoveryAction? = null,
    val networkMessage: String? = null, val connectionMessage: String? = null,
    val walletLoading: Boolean = false, val walletLoaded: Boolean = false, val accountError: String? = null,
    val deviceActionError: String? = null,
    val billDetail: BillUi? = null, val billDetailSync: Map<String, BillDetailUi> = emptyMap(),
    val walletError: String? = null, val billsError: String? = null, val message: String? = null, val working: Boolean = false,
    val postSessionDestination: PostSessionDestination = PostSessionDestination.BILL,
    val resolvingPostSessionDestination: Boolean = false, val postSessionBillId: String? = null,
    val demoMode: Boolean = false, val liquidGlass: Boolean = true,
    /** 启动时是否自动检查新版本。 */ val updateCheckEnabled: Boolean = true,
    /** 系统通知偏好：总开关 + 分类开关。 */ val notificationSettings: NotificationSettings = NotificationSettings(),
    val fontFamily: String = "PINGFANG", val fontWeightStep: Int = 0, val gradientPreset: String = "DEFAULT", val gradientEnabled: Boolean = true,
    val accentColor: Long? = null, val wallpaperPath: String? = null,
    val wallpaperDim: Float = 0f, val wallpaperBlur: Float = 0f,
    /** 上次登录的账号（脱敏前的手机号），用于登录页回填，避免升级/重新登录时还要手打账号。 */
    val lastAccount: String? = null,
    /** 用户选的更新源（默认 Gitee 镜像）。 */
    val updateSource: UpdateSource = UpdateSource.GITEE,
    /** 校园消息（网关推送）：列表、加载态与已读回执。 */
    val messages: List<CampusMessage> = emptyList(), val messagesLoading: Boolean = false,
    val messagesLoaded: Boolean = false, val messagesError: String? = null,
    val messageReadRevisions: List<String> = emptyList()) {
    /** 未读消息条数，用于「我的」页与消息页的角标。 */
    val unreadMessageCount: Int get() {
        val read = messageReadRevisions.toSet()
        return messages.count { it.revision !in read }
    }
}
class HydrosViewModel(
    val graph: HydrosDependencies,
) : ViewModel() {
    private var postSessionNavigationDismissed = false
    private var lastActionError: Exception? = null
    private val mutable = MutableStateFlow(HydrosUiState())
    val state = mutable.asStateFlow()
    private val refreshJobs = mutableListOf<Job>()
    /** 演示模式下用户自己追加的在线设备，退出演示模式后清空。必须先于 init 初始化，init 里会用到。 */
    private val customDemoDevices = mutableListOf<DeviceUi>()
    /** 演示模式下本地模拟的会话，不经过真实网关与蓝牙。 */
    private var demoSession: SessionUi? = null
    private val billHistory = BillHistory(graph.wallet, graph.auth.credentials, viewModelScope)
    private var walletGeneration = 0
    private val discovery = NearbyDiscovery(graph.nearbyScanner, graph.devices, graph.auth.credentials, viewModelScope)
    private val preparation = DevicePreparation(
        graph.auth, graph.devices, graph.sessions, graph.store, viewModelScope,
        ensureWatch = graph.ensureWatch,
    )
    init {
        viewModelScope.launch {
            graph.sessions.observation.collect { value -> mutable.update { it.copy(observation = value) } }
        }
        viewModelScope.launch {
            billHistory.state.collect { history ->
                // 演示数据模式下保留本地模拟账单，避免真实查询把它覆盖掉。
                if (mutable.value.demoMode) return@collect
                val previousBills = mutable.value.bills
                mutable.update { current ->
                    // 用水报告只依据消费账单，切到充值记录时保留上一次的消费快照。
                    val usage = if (history.type == BillType.CONSUMPTION && !history.loading) {
                        runCatching { WaterUsageReport.of(history.bills, YearMonth.parse(history.month), current.waterLimitCents) }
                            .getOrDefault(current.waterUsage)
                    } else current.waterUsage.copy(limitCents = current.waterLimitCents?.takeIf { it > 0 })
                    current.copy(month = history.month, billType = history.type,
                        bills = history.bills, loadingBills = history.loading,
                        billsError = history.error?.let(::friendly),
                        billDetail = history.detailBill,
                        waterUsage = usage,
                        billDetailSync = history.details.mapValues { (_, detail) ->
                            BillDetailUi(detail.fetching, detail.error?.let { failureFeedback(it, FailureOperation.DETAIL).text })
                        })
                }
                notifyWaterLimit()
                // 同一月份里多出新账单时提醒一次；首次加载与切换月份不算新账单。
                if (history.type == BillType.CONSUMPTION && previousBills.isNotEmpty() &&
                    history.month == previousBills.first().month.toString() && history.bills.size > previousBills.size) {
                    val newest = history.bills.firstOrNull { bill -> previousBills.none { it.id == bill.id } }
                    if (newest != null) {
                        postNotification(
                            NotificationTopic.BILLING, "账单已结算",
                            newest.title + " · ¥" + money(newest.amountCents) + "，可在钱包里查看详情",
                            0x4E05,
                        )
                    }
                }
            }
        }
        graph.networkRecovery?.let { recovery ->
            viewModelScope.launch {
                recovery.cellularRequests.collect { count ->
                    mutable.update { it.copy(networkMessage = if (count > 0) "正在尝试移动网络…" else null) }
                }
            }
        }
        viewModelScope.launch {
            var previous = emptyList<DeviceUi>()
            discovery.state.collect { nearby ->
                val resolved = nearby.devices.filter { it !in previous }
                previous = nearby.devices
                mutable.update { current -> current.copy(
                    nearby = nearby,
                    devices = current.devices.filterNot { old -> resolved.any { it.id == old.id } } + resolved,
                    message = current.message,
                ) }
            }
        }
        viewModelScope.launch {
            try {
                val theme = runCatching { ThemeMode.valueOf(graph.store.theme()) }.getOrDefault(ThemeMode.SYSTEM)
                val onboarding = graph.store.onboardingCompleted()
                val feedback = graph.store.feedback()
                val fontSizeScale = graph.store.fontSizeScale()
                val postSessionDestination = runCatching { graph.store.postSessionDestination() }.getOrDefault(PostSessionDestination.BILL)
                val homeConnectCardVisible = runCatching { graph.store.homeConnectCardVisible() }.getOrDefault(true)
                val waterLimitCents = runCatching { graph.store.waterLimitCents() }.getOrNull()
                val notified = runCatching { graph.store.waterLimitNotifiedMonth() }.getOrNull()
                val waterLimitBlock = runCatching { graph.store.waterLimitBlock() }.getOrDefault(false)
                val avatarPath = runCatching { graph.store.avatarPath() }.getOrNull()
                val demo = runCatching { graph.store.demoMode() }.getOrDefault(false)
                val liquidGlass = runCatching { graph.store.liquidGlass() }.getOrDefault(true)
                val updateCheckEnabled = UpdateCheckSetting.enabled()
                val notificationSettings = runCatching { graph.store.notifications() }.getOrDefault(NotificationSettings())
                val fontFamily = runCatching { graph.store.fontFamily() }.getOrDefault("PINGFANG")
                val fontWeightStep = runCatching { graph.store.fontWeightStep() }.getOrDefault(0)
                val gradientPreset = runCatching { graph.store.gradientPreset() }.getOrDefault("DEFAULT")
                val gradientEnabled = runCatching { graph.store.gradientEnabled() }.getOrDefault(true)
                val accentColor = runCatching { graph.store.accentColor() }.getOrNull()
                val wallpaperPath = runCatching { graph.store.wallpaperPath() }.getOrNull()
                val wallpaperDim = runCatching { graph.store.wallpaperDim() }.getOrDefault(0f)
                val wallpaperBlur = runCatching { graph.store.wallpaperBlur() }.getOrDefault(0f)
                val lastAccount = runCatching { graph.store.lastAccount() }.getOrNull()
                val updateSource = UpdateSourceSetting.current()
                mutable.update { it.copy(theme=theme, onboardingCompleted=onboarding, feedback=feedback, updateSource=updateSource,
                    fontSizeScale=fontSizeScale, postSessionDestination=postSessionDestination,
                    homeConnectCardVisible=homeConnectCardVisible, waterLimitCents=waterLimitCents,
                    waterLimitNotifiedMonth=notified, waterLimitBlock=waterLimitBlock, avatarPath=avatarPath, fontFamily=fontFamily,
                    fontWeightStep=fontWeightStep,
                    gradientPreset=gradientPreset, gradientEnabled=gradientEnabled, accentColor=accentColor,
                    wallpaperPath=wallpaperPath, wallpaperDim=wallpaperDim, wallpaperBlur=wallpaperBlur,
                    lastAccount=lastAccount,
                    liquidGlass=liquidGlass,
                    updateCheckEnabled=updateCheckEnabled,
                    notificationSettings = notificationSettings,
                    waterUsage=it.waterUsage.copy(limitCents = waterLimitCents?.takeIf { value -> value > 0 })) }
                if (demo) {
                    HydrosLog.log("demo", "启动时恢复演示数据模式")
                    runCatching { applyDemoData() }
                        .onFailure { HydrosLog.log("demo", "恢复演示数据失败：" + it) }
                } else graph.restore()
                graph.auth.authState.collectLatest { authentication ->
                    val auth = authentication.credentials
                    stopDiscovery()
                    refreshJobs.forEach { it.cancel() }; refreshJobs.clear()
                    preparation.cancel()
                    walletGeneration++
                    if (authentication.reauthentication != null) {
                        mutable.update { it.copy(signedIn = false, reauthentication = authentication.reauthentication,
                            initializing = false, loadingBills = false,
                            walletError = null, billsError = null, message = null) }
                        return@collectLatest
                    }
                    if (auth == null && runCatching { graph.store.hasStoredCredentials() }.getOrDefault(false)) {
                        // 密文还在、只是这次没解出来：保留本地账户与设备数据，不触发任何清理。
                        HydrosLog.log("auth", "启动时无法解出本地凭证，保留账户数据与登录入口")
                    }
                    graph.sessions.restore(auth)
                    val saved = auth?.let { graph.store.account(it.key) }
                    mutable.update { current -> HydrosUiState(initializing=false, signedIn=auth != null,
                        onboardingCompleted=current.onboardingCompleted, reauthentication=authentication.reauthentication,
                        theme=current.theme, feedback=current.feedback, fontSizeScale=current.fontSizeScale,
                        postSessionDestination=current.postSessionDestination,
                        homeConnectCardVisible=current.homeConnectCardVisible, waterLimitCents=current.waterLimitCents,
                        waterLimitNotifiedMonth=current.waterLimitNotifiedMonth, waterLimitBlock=current.waterLimitBlock,
                        avatarPath=current.avatarPath,
                        liquidGlass=current.liquidGlass,
                        updateCheckEnabled=current.updateCheckEnabled,
                        notificationSettings = current.notificationSettings,
                        fontFamily=current.fontFamily, fontWeightStep=current.fontWeightStep,
                        gradientPreset=current.gradientPreset,
                        gradientEnabled=current.gradientEnabled, accentColor=current.accentColor,
                        wallpaperPath=current.wallpaperPath, wallpaperDim=current.wallpaperDim,
                        wallpaperBlur=current.wallpaperBlur, lastAccount=current.lastAccount,
                        waterUsage=current.waterUsage.copy(limitCents = current.waterLimitCents?.takeIf { value -> value > 0 }),
                        messages=if (auth != null) current.messages else emptyList(),
                        messagesLoaded=auth != null && current.messagesLoaded,
                        messageReadRevisions=if (auth != null) current.messageReadRevisions else emptyList(),
                        account=AccountUi(phone=auth?.phone ?: ""),
                        // 演示模式下保留本地方案，否则每次登录态刷新都会把演示设备与账单清空。
                        devices=if (current.demoMode) current.devices else saved?.devices ?: emptyList(),
                        connectedDeviceIds=if (current.demoMode) current.connectedDeviceIds else saved?.recent ?: emptyList(),
                        favoriteDeviceIds=if (current.demoMode) current.favoriteDeviceIds else saved?.favorites ?: emptySet(),
                        deviceRemarks=if (current.demoMode) current.deviceRemarks else saved?.deviceRemarks ?: emptyMap(),
                        demoMode=current.demoMode,
                        wallet=if (current.demoMode) current.wallet else WalletUi(),
                        walletLoaded=if (current.demoMode) current.walletLoaded else false,
                        bills=if (current.demoMode) current.bills else emptyList(),
                        session=if (current.demoMode) current.session ?: demoSession else graph.sessions.session.value) }
                    if(auth != null && !mutable.value.demoMode) { refresh(); if(graph.sessions.session.value != null) graph.ensureWatch() }
                }
            } catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(initializing=false, message=failureFeedback(e, FailureOperation.RESTORE, FailureStage.STORAGE).text) } }
        }
        viewModelScope.launch {
            var prior: SessionUi? = null
            graph.sessions.session.collect { value ->
                val auth = graph.auth.credentials.value
                val finished = when {
                    value != null || auth == null -> null
                    else -> prior?.takeIf { it.ownerKey == auth.key }
                        ?: mutable.value.session?.takeIf { it.ownerKey == auth.key }
                }
                // 只有会话真正切换（开始/结束）时才提醒，应用重启恢复同一条会话不算新事件。
                val previousSession = prior
                if (previousSession != null && value != null && value.device.id != previousSession.device.id) {
                    postNotification(NotificationTopic.DEVICE, "已开始使用 " + value.device.name,
                        value.device.location + " · 计时已开始", 0x4E03, "session")
                }
                if (previousSession != null && value == null) {
                    postNotification(NotificationTopic.DEVICE, "用水已结束",
                        previousSession.device.name + " 的使用已结束，账单会在结算后出现在钱包里", 0x4E04, "wallet")
                }
                if(value == null || auth?.key == value.ownerKey || auth == null) mutable.update { current -> current.copy(
                    session=if (current.demoMode) demoSession ?: value else value,
                    resolvingPostSessionDestination = if (value != null) false else current.resolvingPostSessionDestination,
                    postSessionBillId = if (value == null) current.postSessionBillId else null) }
                // Refresh first: the bill lookup replaces the list selection it may still be loading.
                if(prior != null && value == null && auth != null) refresh()
                if (finished != null && graph.store.postSessionDestination() == PostSessionDestination.BILL) {
                    graph.scope.launch {
                        postSessionNavigationDismissed = false
                        mutable.update { it.copy(resolvingPostSessionDestination = true) }
                        resolveFinishedBill(finished)
                    }
                }
                prior = value
            }
        }
        viewModelScope.launch {
            preparation.state.collect { connection ->
                mutable.update { current ->
                    val ready = connection.device?.takeIf { connection.stage == ConnectionStage.READY }
                    val devices = if (ready == null) current.devices else {
                        current.devices.filterNot { it.id == ready.id } + ready
                    }
                    current.copy(
                        connectingId = connection.device?.id,
                        connectionStage = connection.stage,
                        connectionMessage = connection.error?.let { failureFeedback(it, FailureOperation.DEVICE).text } ?: connection.message,
                        connectionAction = connection.error?.let { failureFeedback(it, FailureOperation.DEVICE).action },
                        devices = devices,
                        connectedDeviceIds = if (ready == null) current.connectedDeviceIds else connection.recentDeviceIds,
                        message = current.message,
                    )
                }
            }
        }
    }
    private fun friendly(error: Exception) = failureFeedback(error, FailureOperation.BILLS).text
    private fun resolveFinishedBill(session: SessionUi) {
        viewModelScope.launch {
            // Wrap the result: withTimeoutOrNull returns null on timeout, while a completed lookup
            // legitimately returns null when no bill matches.
            val located = try {
                withTimeoutOrNull(8_000) { LocatedBill(billHistory.locateSessionBill(session)) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { LocatedBill(null) }
            val bill = located?.bill
            if (graph.auth.credentials.value?.key != session.ownerKey) return@launch
            if (postSessionNavigationDismissed) {
                mutable.update { it.copy(resolvingPostSessionDestination = false) }
                return@launch
            }
            // Publish the located month together with the bill id so the detail page can render it at once.
            val history = billHistory.state.value
            mutable.update { it.copy(month = history.month, billType = history.type,
                bills = history.bills, loadingBills = history.loading, billsError = history.error?.let(::friendly),
                resolvingPostSessionDestination = false, postSessionBillId = bill?.id) }
        }
    }
    suspend fun acknowledgeReauthentication(id: Long) {
        graph.auth.acknowledgeReauthentication(id)
        // Restoring the device can wait on an ongoing close operation. Consume the UI notice now.
        mutable.update { if (it.reauthentication?.id == id) it.copy(reauthentication = null) else it }
    }
    fun dismissMessage() { mutable.update { it.copy(message=null, messageSeverity=FeedbackSeverity.ERROR, messageSource="user", messageAction=null) } }
    fun clearAllData(clear: () -> Boolean): Boolean {
        HydrosLog.log("data", "请求清除所有数据")
        if (state.value.session != null || graph.sessions.session.value != null) {
            mutable.update { it.copy(message = "请先结束当前设备会话，再清除所有数据。") }
            return false
        }
        if (state.value.working) {
            mutable.update { it.copy(message = "请等待当前操作完成后重试。") }
            return false
        }
        cancelConnection()
        stopDiscovery()
        val requested = runCatching(clear).getOrDefault(false)
        if (!requested) mutable.update { it.copy(message = "清除失败，请重试") }
        return requested
    }
    suspend fun action(allowRecovery: Boolean = false, operation: FailureOperation = FailureOperation.SETTINGS, block: suspend () -> Unit): Boolean {
        if(mutable.value.working || (!allowRecovery && state.value.reauthentication != null)) {
            // No request was attempted; callers must not report the previous action's failure.
            lastActionError = null
            return false
        }
        val owner = graph.auth.credentials.value
        lastActionError = null
        mutable.update { it.copy(working=true, message=null,
            deviceActionError=if (operation == FailureOperation.START) null else it.deviceActionError) }
        return try { block(); true } catch(e: CancellationException) { throw e }
        catch(e: Exception) {
            lastActionError = e
            if ((e !is ApiFailure || !e.expired) && state.value.reauthentication == null && graph.auth.credentials.value == owner) {
                val deviceAction = operation in listOf(FailureOperation.START, FailureOperation.FINISH)
                if (operation == FailureOperation.FINISH) graph.sessions.reportActionFailure(e)
                val feedback = if (deviceAction) graph.sessions.session.value?.let(::sessionFeedback) ?: failureFeedback(e, operation)
                    else failureFeedback(e, operation)
                mutable.update { it.copy(message=feedback.text, messageSeverity=feedback.severity,
                    messageSource=if (deviceAction) "device" else "user", messageAction=feedback.action,
                    deviceActionError=if (operation == FailureOperation.START && graph.sessions.session.value == null) feedback.text else it.deviceActionError) }
            }
            false
        }
        finally { mutable.update { it.copy(working=false) } }
    }
    suspend fun login(phone: String, secret: String, sms: Boolean): Boolean =
        action(operation = FailureOperation.LOGIN) { graph.auth.login(phone, secret, sms) }
    suspend fun sendCode(phone: String, type: Int) = action(operation = FailureOperation.CODE) { graph.auth.sendCode(phone, type) }
    suspend fun saveAccount(form: org.cqutosp.hydros.ui.screens.AccountForm, phone: String, old: String, password: String, code: String) = action(operation = FailureOperation.ACCOUNT) {
        if(state.value.session != null) throw ApiFailure(null, "请先结束当前设备使用，再修改账户")
        when(form) {
            org.cqutosp.hydros.ui.screens.AccountForm.PHONE -> graph.auth.savePhone(phone, code)
            org.cqutosp.hydros.ui.screens.AccountForm.PASSWORD -> {
                if (old.isNotEmpty()) graph.auth.savePassword(old, password, null)
                else graph.auth.savePassword(null, password, code)
            }
        }
    }
    fun theme(mode: ThemeMode) { graph.scope.launch {
        action { graph.store.theme(mode.name); mutable.update { it.copy(theme=mode) } }
    } }
    fun postSessionDestination(destination: PostSessionDestination) { graph.scope.launch {
        action {
            graph.store.postSessionDestination(destination)
            mutable.update { it.copy(postSessionDestination = destination) }
        }
    } }
    fun consumePostSessionBill() { mutable.update { it.copy(postSessionBillId = null) } }
    fun dismissPostSessionNavigation() {
        postSessionNavigationDismissed = true
        mutable.update { it.copy(resolvingPostSessionDestination = false, postSessionBillId = null) }
    }
    fun hapticFeedback(enabled: Boolean) {
        updateFeedback { it.copy(hapticFeedbackEnabled = enabled) }
    }
    suspend fun fontSizeScale(value: Float): Boolean = action {
        graph.store.fontSizeScale(value)
        mutable.update { it.copy(fontSizeScale = value) }
    }
    /** 首页"连接身边的设备"卡片开关：关闭后首页不再展示该卡片。 */
    fun homeConnectCardVisible(enabled: Boolean) {
        graph.scope.launch { action {
            graph.store.homeConnectCardVisible(enabled)
            mutable.update { it.copy(homeConnectCardVisible = enabled) }
        } }
    }
    /** 底部导航液态玻璃开关：关闭后改为普通磨砂质感。 */
    /** 切换更新源：立刻生效，下次检查更新就用新源。 */
    fun updateSource(source: UpdateSource) {
        UpdateSourceSetting.set(source)
        HydrosLog.log("update", "更新源切换为 " + source.label)
        mutable.update { it.copy(updateSource = source) }
    }

    fun liquidGlass(enabled: Boolean) { graph.scope.launch { action {
        graph.store.liquidGlass(enabled)
        mutable.update { it.copy(liquidGlass = enabled) }
    } } }
    /** 开发者页：暂停/恢复启动时的自动版本检测。 */
    fun updateCheckEnabled(enabled: Boolean) {
        UpdateCheckSetting.set(enabled)
        HydrosLog.log("update", if (enabled) "已恢复自动版本检测" else "已暂停自动版本检测")
        mutable.update { it.copy(updateCheckEnabled = enabled) }
    }
    /** 系统通知总开关；关闭后所有分类通知都不再发送。 */
    fun notificationMaster(enabled: Boolean) = updateNotificationSettings { it.copy(enabled = enabled) }
    /** 单个通知分类的开关。 */
    fun notificationTopic(topic: NotificationTopic, enabled: Boolean) = updateNotificationSettings { settings ->
        when (topic) {
            NotificationTopic.WATER -> settings.copy(waterAlerts = enabled)
            NotificationTopic.BILLING -> settings.copy(billing = enabled)
            NotificationTopic.CAMPUS -> settings.copy(campus = enabled)
            NotificationTopic.DEVICE -> settings.copy(device = enabled)
            NotificationTopic.UPDATE -> settings.copy(updates = enabled)
        }
    }
    private fun updateNotificationSettings(transform: (NotificationSettings) -> NotificationSettings) {
        graph.scope.launch { action {
            val next = transform(mutable.value.notificationSettings).copy(lastPromptedAt = System.currentTimeMillis())
            mutable.update { it.copy(notificationSettings = next) }
            graph.store.notifications(next)
            HydrosLog.log("notify", "通知设置已更新：总开关" + (if (next.enabled) "开" else "关") +
                "，用水" + onOff(next.waterAlerts) + "，账单" + onOff(next.billing) +
                "，校园" + onOff(next.campus) + "，设备" + onOff(next.device) + "，更新" + onOff(next.updates))
        } }
    }
    private fun onOff(value: Boolean) = if (value) "开" else "关"
    /** 所有系统通知的唯一出口：偏好、权限、渠道三者都允许时才真正发出。 */
    private fun postNotification(topic: NotificationTopic, title: String, body: String, id: Int, deepLink: String? = null) {
        val context = graph.appContext ?: return
        val posted = HydrosNotifier.notify(context, mutable.value.notificationSettings, topic, title, body, id, deepLink)
        HydrosLog.log("notify", (if (posted) "已发送 " else "已跳过 ") + topic.label + "：" + title)
    }
    /** 通知管理页的测试入口：即使分类关闭也只在总开关关闭时报错，便于用户确认渠道是否正常。 */
    fun testNotification() {
        val context = graph.appContext
        if (context == null || !mutable.value.notificationSettings.enabled) {
            mutable.update { it.copy(message = "请先开启通知总开关，再发送测试通知",
                messageSeverity = FeedbackSeverity.WARNING, messageSource = "notify", messageAction = null) }
            return
        }
        val enabled = NotificationTopic.entries.firstOrNull { HydrosNotifier.canPost(context, mutable.value.notificationSettings, it) }
        if (enabled == null) {
            mutable.update { it.copy(message = "系统通知权限或渠道被关闭，请先在系统设置里允许 Hydros+ 发送通知",
                messageSeverity = FeedbackSeverity.WARNING, messageSource = "notify", messageAction = null) }
            return
        }
        postNotification(enabled, "测试通知", "如果你看到这条通知，说明「" + enabled.label + "」渠道工作正常。", 0x4E10)
        mutable.update { it.copy(message = "测试通知已发送，可下拉通知栏查看",
            messageSeverity = FeedbackSeverity.SUCCESS, messageSource = "notify", messageAction = null) }
    }
    /** 本次进程内已经提醒过的版本，避免每次回到首页都重复弹同一条更新通知。 */
    private var notifiedUpdateVersion: String? = null
    /** 发现新版本时提醒一次。 */
    fun notifyUpdateAvailable(version: String) {
        if (version.isBlank() || version == notifiedUpdateVersion) return
        notifiedUpdateVersion = version
        postNotification(NotificationTopic.UPDATE, "发现新版本 " + version,
            "Hydros+ " + version + " 已发布，可在「软件更新」里查看更新内容并安装。", 0x4E11, "software-update")
    }
    /** 只清理缓存与临时文件，账号、设备、设置与头像都保留。 */
    fun clearCache(clear: () -> Boolean): Boolean {
        HydrosLog.log("data", "请求清除缓存")
        val cleared = runCatching(clear).getOrDefault(false)
        mutable.update { it.copy(
            message = if (cleared) "缓存已清理，账号与设置不受影响" else "清理缓存失败，请重试",
            messageSeverity = if (cleared) FeedbackSeverity.SUCCESS else FeedbackSeverity.ERROR,
            messageSource = "user", messageAction = null) }
        return cleared
    }
    /** 保存自定义头像路径（由界面把相册图片复制到应用私有目录后回填），传 null 恢复默认头像。 */
    fun avatarPath(path: String?) {
        graph.scope.launch { action {
            graph.store.avatarPath(path)
            mutable.update { it.copy(avatarPath = path) }
        } }
    }
    /** 应用字体："PINGFANG" 内置苹方，"SYSTEM" 跟随系统字体。 */
    fun fontFamily(value: String) { graph.scope.launch { action {
        mutable.update { it.copy(fontFamily = value) }
        graph.store.fontFamily(value)
    } } }
    /** 字体粗细档位：0 为设计稿原始字重，+1/+2 逐级加粗，-1 变细。 */
    fun fontWeightStep(step: Int) { graph.scope.launch { action {
        val clamped = step.coerceIn(-1, 2)
        mutable.update { it.copy(fontWeightStep = clamped) }
        graph.store.fontWeightStep(clamped)
    } } }
    /** 首页渐变背景预设，见 GradientPreset。 */
    fun gradientPreset(value: String) { graph.scope.launch { action {
        mutable.update { it.copy(gradientPreset = value) }
        graph.store.gradientPreset(value)
    } } }
    /** 是否启用首页渐变背景，关闭后使用纯色页面。 */
    fun gradientEnabled(value: Boolean) { graph.scope.launch { action {
        mutable.update { it.copy(gradientEnabled = value) }
        graph.store.gradientEnabled(value)
    } } }
    /** 自定义强调色 ARGB 值，null 表示默认主题色。 */
    fun accentColor(value: Long?) { graph.scope.launch { action {
        mutable.update { it.copy(accentColor = value) }
        graph.store.accentColor(value)
    } } }
    /** 壁纸绝对路径，null 表示不使用壁纸。 */
    fun wallpaperPath(value: String?) { graph.scope.launch { action {
        mutable.update { it.copy(wallpaperPath = value) }
        graph.store.wallpaperPath(value)
    } } }
    /** 壁纸压暗程度 0f..0.6f。 */
    fun wallpaperDim(value: Float) { graph.scope.launch { action {
        mutable.update { it.copy(wallpaperDim = value) }
        graph.store.wallpaperDim(value)
    } } }
    /** 壁纸模糊半径（dp）0f..25f。 */
    fun wallpaperBlur(value: Float) { graph.scope.launch { action {
        mutable.update { it.copy(wallpaperBlur = value) }
        graph.store.wallpaperBlur(value)
    } } }
    /** 设置每月用水限额（分），传 null 表示取消限额。 */
    fun waterLimit(cents: Long?) {
        graph.scope.launch { action {
            graph.store.waterLimitCents(cents)
            graph.store.waterLimitNotifiedMonth(null)
            mutable.update { current -> current.copy(waterLimitCents = cents, waterLimitNotifiedMonth = null,
                waterUsage = current.waterUsage.copy(limitCents = cents?.takeIf { it > 0 })) }
            notifyWaterLimit()
        } }
    }
    /** 是否在达到限额后禁止开始新的用水。 */
    fun waterLimitBlock(enabled: Boolean) {
        graph.scope.launch {
            runCatching { graph.store.waterLimitBlock(enabled) }
            HydrosLog.log("water", if (enabled) "开启超额禁止使用" else "关闭超额禁止使用")
            mutable.update { it.copy(waterLimitBlock = enabled) }
        }
    }
    /** 当月支出达到限额时提醒一次，避免每次刷新都打扰。 */
    private fun notifyWaterLimit() {
        val current = state.value
        val limit = current.waterLimitCents?.takeIf { it > 0 } ?: return
        val usage = current.waterUsage
        if (!usage.loaded || usage.totalCents < limit) return
        if (current.waterLimitNotifiedMonth == usage.month) return
        val overspend = usage.totalCents - limit
        val text = if (overspend > 0) "本月用水支出 ¥" + money(usage.totalCents) + "，已超出限额 ¥" + money(limit)
            else "本月用水支出 ¥" + money(usage.totalCents) + "，已达设定的用水限额 ¥" + money(limit)
        mutable.update { it.copy(waterLimitNotifiedMonth = usage.month, message = text,
            messageSeverity = FeedbackSeverity.WARNING, messageSource = "water-limit", messageAction = null) }
        postNotification(NotificationTopic.WATER, "用水限额提醒", text, 0x4E01, "water/report")
        graph.scope.launch { runCatching { graph.store.waterLimitNotifiedMonth(usage.month) } }
    }
    fun reduceMotion(enabled: Boolean) {
        updateFeedback { it.copy(reduceMotionEnabled = enabled) }
    }
    /** 关闭用水动画后，设备改为点击即开始使用，不再需要长按。 */
    fun waterAnimationDisabled(enabled: Boolean) {
        updateFeedback { it.copy(waterAnimationDisabled = enabled) }
    }
    private fun updateFeedback(transform: (FeedbackSettings) -> FeedbackSettings) {
        graph.scope.launch { action {
            val feedback = graph.store.updateFeedback(transform)
            mutable.update { it.copy(feedback = feedback) }
        } }
    }
    fun completeOnboarding() { graph.scope.launch { action {
        graph.store.onboardingCompleted(true)
        mutable.update { it.copy(onboardingCompleted = true) }
    } } }
    /** 开发者选项：用本地演示数据替代网关数据，便于无设备时检查界面。 */
    fun demoMode(enabled: Boolean) {
        // 先同步改状态，保证开关与设备列表立即生效；持久化放到后台，避免慢 IO 让界面看起来没反应。
        HydrosLog.log("demo", if (enabled) "开启演示数据模式" else "关闭演示数据模式")
        if (enabled) applyDemoData() else {
            demoSession = null
            mutable.update { it.copy(demoMode = false, devices = emptyList(), favoriteDeviceIds = emptySet(),
                session = null,
                bills = emptyList(), loadingBills = false, billsError = null,
                wallet = WalletUi(), walletLoaded = false, walletError = null,
                waterUsage = it.waterUsage.copy(limitCents = it.waterLimitCents?.takeIf { value -> value > 0 })) }
            customDemoDevices.clear()
        }
        graph.scope.launch {
            runCatching { graph.store.demoMode(enabled) }
                .onFailure { HydrosLog.log("demo", "保存演示模式失败：" + it) }
            if (!enabled && state.value.signedIn) refresh()
        }
    }
    /** 开发者选项：在演示模式里新增一台在线设备，方便无真实设备时调试首页与设备页。 */
    fun addDemoDevice(name: String, location: String, category: DeviceCategory, online: Boolean = true) {
        val title = name.trim().ifBlank { "演示设备" }
        val place = location.trim().ifBlank { "演示位置" }
        val index = customDemoDevices.size + 1
        val device = DemoData.customDevice(index, title, place, category, online)
        customDemoDevices += device
        HydrosLog.log("demo", "新增演示设备 " + title + "（" + place + "）")
        mutable.update { it.copy(demoMode = true,
            devices = it.devices.filterNot { item -> item.id == device.id } + device,
            favoriteDeviceIds = it.favoriteDeviceIds + device.id,
            message = "已新增演示设备「" + title + "」", messageSeverity = FeedbackSeverity.SUCCESS,
            messageSource = "user") }
    }
    /** 开发者选项：修改演示设备的名称、位置、用途与在线状态。 */
    fun updateDemoDevice(id: String, name: String, location: String, category: DeviceCategory, online: Boolean) {
        val index = customDemoDevices.indexOfFirst { it.id == id }
        if (index < 0) return
        val title = name.trim().ifBlank { "演示设备" }
        val place = location.trim().ifBlank { "演示位置" }
        val current = customDemoDevices[index]
        val updated = customDemoDevices[index].copy(
            name = title,
            location = place,
            category = category,
            status = if (online) DeviceStatus.AVAILABLE else DeviceStatus.OFFLINE,
            signal = if (online) "良好" else "离线",
            isBle = category == DeviceCategory.SHOWER,
        )
        customDemoDevices[index] = updated
        HydrosLog.log("demo", "修改演示设备 " + title + (if (online) "（在线）" else "（离线）"))
        mutable.update { currentState ->
            currentState.copy(
                devices = currentState.devices.map { if (it.id == id) updated else it },
                nearby = currentState.nearby.copy(devices = currentState.nearby.devices.map { if (it.id == id) updated else it }),
                session = currentState.session?.takeIf { it.device.id != id || online },
                message = if (current.status != updated.status) "演示设备「" + title + "」已切换为" + updated.status.label else null,
                messageSeverity = FeedbackSeverity.SUCCESS,
                messageSource = "user",
            )
        }
        if (!online) {
            mutable.update { it.copy(connectedDeviceIds = it.connectedDeviceIds - id) }
            if (demoSession?.device?.id == id) demoSession = null
        }
    }

    /** 开发者选项：只切换演示设备的在线状态。 */
    fun setDemoDeviceOnline(id: String, online: Boolean) {
        val device = customDemoDevices.firstOrNull { it.id == id } ?: return
        updateDemoDevice(id, device.name, device.location, device.category, online)
    }

    /** 开发者选项：删除自己在演示模式里新增的设备。 */
    fun removeDemoDevice(id: String) {
        if (customDemoDevices.none { it.id == id }) return
        customDemoDevices.removeAll { it.id == id }
        HydrosLog.log("demo", "删除演示设备 " + id)
        mutable.update { it.copy(devices = it.devices.filterNot { item -> item.id == id },
            favoriteDeviceIds = it.favoriteDeviceIds - id) }
    }

    private fun applyDemoData() {
        val now = LocalDateTime.now()
        val month = YearMonth.from(now)
        val bills = DemoData.bills(now) + DemoData.rechargeBills(now)
        val limit = state.value.waterLimitCents ?: DemoData.limitCents
        val usage = DemoData.report(limit, now)
        mutable.update { it.copy(demoMode = true, devices = DemoData.devices + customDemoDevices,
            favoriteDeviceIds = DemoData.favoriteIds + customDemoDevices.map { item -> item.id }, wallet = DemoData.wallet, walletLoaded = true, walletError = null,
            bills = bills, month = month.toString(), billType = BillType.CONSUMPTION,
            loadingBills = false, billsError = null, waterUsage = usage) }
    }
    /** 开发者选项：清除引导完成标记，下次启动重新展示引导。 */
    fun resetOnboarding() {
        graph.scope.launch {
            runCatching { graph.store.onboardingCompleted(false) }
            HydrosLog.log("developer", "重置新手引导")
            mutable.update { it.copy(message = "已重置新手引导，下次启动会重新显示", messageSeverity = FeedbackSeverity.SUCCESS, messageSource = "user") }
        }
    }
    fun clearLogs() {
        HydrosLog.clear()
    }
    fun logout() {
        graph.scope.launch {
            action {
                if(graph.sessions.session.value != null) throw ApiFailure(null, "请先结束或恢复当前设备会话")
                cancelConnection(); graph.auth.logout()
            }
        }
    }
    fun toggleFavorite(deviceId: String) {
        graph.scope.launch {
            action(operation = FailureOperation.FAVORITE) {
                val auth = graph.auth.requireCredentials()
                // 详情页里点收藏的设备可能还没进 recent，只要当前会话里认得这台设备就允许收藏，
                // 并把设备快照一起写进账号，重启后「我的设备」才不会丢。
                val known = state.value.devices.find { it.id == deviceId }
                val saved = graph.store.updateAccount(auth.key) {
                    when {
                        deviceId in it.favorites -> it.copy(favorites = it.favorites - deviceId, deviceRemarks = it.deviceRemarks - deviceId)
                        known != null || deviceId in it.recent -> it.copy(
                            favorites = it.favorites + deviceId,
                            devices = known?.let { device -> it.devices.filterNot { d -> d.id == deviceId } + device } ?: it.devices,
                        )
                        else -> throw ApiFailure(null, "设备不存在，请重新扫描后再收藏")
                    }
                }
                if(graph.auth.credentials.value == auth) mutable.update { it.copy(favoriteDeviceIds=saved.favorites, deviceRemarks=saved.deviceRemarks) }
            }
        }
    }
    suspend fun saveDeviceRemark(deviceId: String, value: String): Boolean {
        val ok = action(operation = FailureOperation.REMARK) {
            val auth = graph.auth.requireCredentials()
            if (state.value.devices.none { it.id == deviceId }) throw ApiFailure(null, "设备不存在，请重新扫描")
            if (value.length > DEVICE_REMARK_MAX_LENGTH) throw ApiFailure(null, "设备备注最多 $DEVICE_REMARK_MAX_LENGTH 字")
            val remark = value.trim()
            val saved = graph.store.updateAccount(auth.key) {
                if (deviceId !in it.favorites) throw ApiFailure(null, "请先收藏设备，再设置备注")
                it.copy(deviceRemarks = if (remark.isEmpty()) it.deviceRemarks - deviceId else it.deviceRemarks + (deviceId to remark))
            }
            if (graph.auth.credentials.value != auth) throw ApiFailure(null, "账号已切换，请重试")
            mutable.update { it.copy(deviceRemarks = saved.deviceRemarks) }
        }
        return ok
    }
    suspend fun scan(mac: String, rssi: Int? = null): String? {
        val auth = graph.auth.credentials.value ?: return null
        val device = graph.devices.find(auth, mac, if(rssi != null) mac else null, rssi)
        if(graph.auth.credentials.value != auth) return null
        mutable.update { it.copy(devices=it.devices.filterNot { d -> d.id == device.id } + device) }
        return device.id
    }
    fun discoverNearby() {
        if (state.value.demoMode) {
            demoDiscovery()
            return
        }
        discovery.start()
    }
    fun stopDiscovery() {
        if (state.value.demoMode) {
            mutable.update { it.copy(nearby = it.nearby.copy(searching = false)) }
            return
        }
        discovery.stop()
    }
    /** 演示模式下不访问蓝牙，直接返回固定的模拟设备，便于无硬件时体验扫描流程。 */
    private fun demoDiscovery() {
        HydrosLog.log("demo", "演示模式下返回模拟附近设备")
        mutable.update {
            it.copy(
                nearby = it.nearby.copy(
                    scanGeneration = it.nearby.scanGeneration + 1,
                    searching = true,
                    hasSearched = true,
                    completed = false,
                    devices = emptyList(),
                    error = null,
                    failedLookups = 0,
                    scanFailed = false,
                ),
                message = null,
            )
        }
        graph.scope.launch {
            delay(700)
            val pool = DemoData.devices + customDemoDevices
            mutable.update { current ->
                if (!current.demoMode) return@update current
                current.copy(
                    devices = current.devices + pool.filterNot { found -> current.devices.any { it.id == found.id } },
                    nearby = current.nearby.copy(searching = false, hasSearched = true, completed = true, devices = pool),
                )
            }
        }
    }
    fun connect(device: DeviceUi) {
        if (state.value.demoMode) {
            demoConnect(device)
            return
        }
        discovery.stop()
        if (state.value.deviceActionError != null) preparation.cancel()
        mutable.update { it.copy(deviceActionError=null) }
        preparation.prepare(device)
    }

    /** 演示模式下的“连接”：不访问蓝牙与网关，在线设备直接进入已连接状态。 */
    private fun demoConnect(device: DeviceUi) {
        val online = device.status != DeviceStatus.OFFLINE
        HydrosLog.log("demo", "模拟连接 " + device.name + (if (online) "（在线）" else "（离线）"))
        mutable.update {
            it.copy(connectingId = device.id, connectionStage = ConnectionStage.CONNECTING,
                connectionMessage = "正在连接演示设备…", deviceActionError = null)
        }
        graph.scope.launch {
            delay(450)
            mutable.update { current ->
                if (!current.demoMode) return@update current
                if (online) current.copy(
                    connectionStage = ConnectionStage.READY,
                    connectedDeviceIds = (current.connectedDeviceIds + device.id).distinct(),
                    connectionMessage = "演示设备已就绪，可以直接开始用水",
                    deviceActionError = null,
                ) else current.copy(
                    connectionStage = ConnectionStage.FAILED,
                    connectionMessage = "演示设备已设置为离线，请先在开发者页面切换为在线",
                )
            }
        }
    }

    /** 演示模式下的“开始用水”：本地生成会话与计时，不发送真实开启指令。 */
    private fun demoStart(device: DeviceUi) {
        if (device.status == DeviceStatus.OFFLINE) {
            mutable.update { it.copy(message = "演示设备当前离线，请先在开发者页面切换为在线", messageSeverity = FeedbackSeverity.WARNING, messageSource = "user") }
            return
        }
        if (demoSession != null || state.value.session != null) return
        val session = SessionUi(device = device, ownerKey = "demo", startedAt = System.currentTimeMillis(), stage = SessionStage.USING)
        demoSession = session
        HydrosLog.log("demo", "开始演示用水 " + device.name)
        mutable.update {
            it.copy(session = session, connectedDeviceIds = (it.connectedDeviceIds + device.id).distinct(),
                connectionStage = ConnectionStage.READY,
                message = "演示设备已开始用水（模拟计费）", messageSeverity = FeedbackSeverity.SUCCESS, messageSource = "user")
        }
    }

    /** 演示模式下的“结束用水”：生成一笔演示账单并刷新用水报告。 */
    private fun demoFinish() {
        val session = demoSession ?: return
        HydrosLog.log("demo", "结束演示用水 " + session.device.name)
        mutable.update { it.copy(session = it.session?.copy(stage = SessionStage.SETTLING), working = true) }
        graph.scope.launch {
            delay(900)
            val now = LocalDateTime.now()
            val cents = when (session.device.category) {
                DeviceCategory.SHOWER -> 42L
                DeviceCategory.DRINK -> 18L
                else -> 21L
            }
            val bill = BillUi(
                id = "demo-bill-" + now.toLocalTime().toSecondOfDay(),
                title = session.device.name,
                location = session.device.location,
                category = session.device.category,
                amountMilli = cents * 10,
                time = now,
                consumeDate = now.toString(),
                orderNo = "demo-" + now.toLocalTime().toSecondOfDay(),
                durationText = "演示用水",
            )
            demoSession = null
            mutable.update { current ->
                val bills = (listOf(bill) + current.bills).filterNot { it.id == bill.id }
                val limit = current.waterLimitCents ?: DemoData.limitCents
                current.copy(
                    session = null,
                    working = false,
                    bills = bills,
                    wallet = current.wallet.copy(totalMilli = (current.wallet.totalMilli ?: 0L) - bill.amountMilli!!),
                    walletLoaded = true,
                    waterUsage = WaterUsageReport.of(bills, YearMonth.from(now), limit),
                    message = "演示用水已结束，已生成一笔演示账单", messageSeverity = FeedbackSeverity.SUCCESS, messageSource = "user",
                )
            }
        }
    }
    /** Resume the user's existing connection intent after returning from settings or a network interruption. */
    fun recoverConnection(deviceId: String) {
        if (!state.value.signedIn || state.value.reauthentication != null || state.value.session != null || state.value.working) return
        val pending = preparation.state.value
        val device = pending.device ?: return
        if (pending.stage != ConnectionStage.FAILED || device.id != deviceId) return
        val error = pending.error ?: return
        if (generateSequence<Throwable>(error) { it.cause }.any { it is javax.net.ssl.SSLException || it is java.net.ProtocolException }) return
        val failure = classifyFailure(error, FailureOperation.DEVICE, FailureStage.NETWORK)
        if (failure.reason !in setOf(FailureReason.NETWORK, FailureReason.TIMEOUT, FailureReason.DISCONNECTED,
                FailureReason.BLUETOOTH_OFF, FailureReason.PERMISSION)) return
        connect(device)
    }
    fun cancelConnection() {
        preparation.cancel()
    }
    fun start(device: DeviceUi) {
        if (state.value.working || state.value.session != null) return
        // 达到本月限额且开启了“禁止使用”时，直接拦下，不向网关发起开启请求。
        val limit = state.value.waterLimitCents?.takeIf { it > 0 }
        val usage = state.value.waterUsage
        if (limit != null && state.value.waterLimitBlock && usage.loaded && usage.totalCents >= limit) {
            HydrosLog.log("session", "用水限额已触发，拦截开始用水")
            mutable.update {
                it.copy(message = "本月用水支出已达限额 ¥" + money(limit) + "，已禁止开始新的用水；可在「用水限额」里调整限额或关闭限制",
                    messageSeverity = FeedbackSeverity.WARNING, messageSource = "water-limit", messageAction = null)
            }
            return
        }
        if (state.value.demoMode) {
            demoStart(device)
            return
        }
        HydrosLog.log("session", "开始使用 " + device.name)
        graph.scope.launch {
            action(operation = FailureOperation.START) {
                HydrosLog.log("session", "提交开启请求 " + device.deviceId)
                preparation.startSession(device.id)
            }
        }
    }
    fun finish() { if(!state.value.working) graph.scope.launch {
        if (state.value.demoMode) { demoFinish(); return@launch }
        if (graph.sessions.session.value == null) return@launch
        action(allowRecovery = true, operation = FailureOperation.FINISH) { graph.ensureWatch(); graph.sessions.finish(graph.auth.credentials.value) }
    } }
    /** A foreground return may complete an interrupted read, but never replays a user command. */
    fun recoverQueries(visibleBillId: String? = null) {
        val current = state.value
        if (!current.signedIn || current.reauthentication != null || current.working) return
        if ((current.accountError != null || current.walletError != null || current.billsError != null) &&
            !current.walletLoading && !current.loadingBills && refreshJobs.none { it.isActive }) refresh()
        val detail = visibleBillId?.let { current.billDetailSync[it] }
        if (visibleBillId != null && detail?.error != null && detail.fetching != true)
            loadDetail(visibleBillId, force = true)
    }
    fun refresh() {
        if (state.value.demoMode) { HydrosLog.log("demo", "演示模式下忽略刷新请求"); return }
        HydrosLog.log("refresh", "刷新账户、钱包与账单")
        val auth = graph.auth.credentials.value ?: return
        refreshJobs.removeAll { it.isCompleted }
        val generation=++walletGeneration
        refreshJobs += viewModelScope.launch {
            try {
                val account=graph.account.load(auth)
                if(graph.auth.credentials.value==auth && generation==walletGeneration) mutable.update { it.copy(account=account, accountError=null) }
            } catch(e: CancellationException) { throw e } catch(e: Exception) {
                if(graph.auth.credentials.value==auth && generation==walletGeneration) mutable.update { it.copy(accountError=failureFeedback(e, FailureOperation.PROFILE).text) }
            }
        }
        refreshJobs += viewModelScope.launch {
            mutable.update { it.copy(walletError=null, walletLoading=true) }
            try {
                val wallet=graph.wallet.load(auth)
                if(graph.auth.credentials.value==auth && generation==walletGeneration) mutable.update { it.copy(wallet=wallet, walletLoaded=true, walletLoading=false, walletError=null) }
            } catch(e: CancellationException) { throw e } catch(e: Exception) {
                if(graph.auth.credentials.value==auth && generation==walletGeneration) mutable.update { it.copy(walletError=failureFeedback(e, FailureOperation.WALLET).text, walletLoading=false) }
            }
        }
        billHistory.refresh()
        loadMessages()
    }
    fun loadMonth(month: String) = billHistory.selectMonth(month)
    fun selectBillType(type: BillType) = billHistory.selectType(type)

    private val messageRepository: org.cqutosp.hydros.data.messages.MessageRepository? by lazy {
        graph.transport?.let { org.cqutosp.hydros.data.messages.RealMessageRepository(it) }
    }

    /** 拉取校园推送消息；首次同步只建立基线，不把历史消息当作未读。 */
    fun loadMessages() {
        if (mutable.value.demoMode) { HydrosLog.log("message", "演示模式下不请求校园消息"); return }
        if (mutable.value.messagesLoading) return
        val auth = graph.auth.credentials.value ?: return
        viewModelScope.launch {
            mutable.update { it.copy(messagesLoading = true, messagesError = null) }
            try {
                val repo = messageRepository ?: throw ApiFailure(null, "当前版本无法连接消息服务")
                val list = repo.load(auth)
                var receipts = runCatching { graph.store.messageReceipts() }.getOrDefault(MessageReceipts())
                if (!receipts.initialized) {
                    receipts = receipts.withBaseline(list)
                    runCatching { graph.store.messageReceipts(receipts) }
                }
                if (graph.auth.credentials.value != auth) return@launch
                val previous = mutable.value.messages
                mutable.update {
                    it.copy(
                        messages = list, messagesLoaded = true, messagesLoading = false, messagesError = null,
                        messageReadRevisions = receipts.read,
                    )
                }
                HydrosLog.log("message", "消息同步完成：共 " + list.size + " 条")
                // 首次同步只建立基线；之后新增且未读的消息才发系统通知，历史消息不补发。
                val arrivals = list.filter { message -> previous.none { it.revision == message.revision } && message.revision !in receipts.read }
                if (arrivals.isNotEmpty()) {
                    val first = arrivals.first()
                    postNotification(
                        NotificationTopic.CAMPUS,
                        if (arrivals.size == 1) first.title else "收到 " + arrivals.size + " 条校园通知",
                        if (arrivals.size == 1) first.summary else first.title + " 等 " + arrivals.size + " 条消息",
                        0x4E02,
                        "message/" + first.id,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (graph.auth.credentials.value != auth) return@launch
                HydrosLog.log("message", "消息同步失败：" + e.message)
                mutable.update {
                    it.copy(messagesLoading = false, messagesLoaded = true,
                        messagesError = failureFeedback(e, FailureOperation.PROFILE).text)
                }
            }
        }
    }

    fun markMessageRead(revision: String) = markMessagesRead(listOf(revision))

    fun markAllMessagesRead() = markMessagesRead(mutable.value.messages.map { it.revision })

    private fun markMessagesRead(revisions: Collection<String>) {
        if (revisions.isEmpty()) return
        val read = (mutable.value.messageReadRevisions + revisions).distinct().takeLast(MessageReceipts.MAX_ITEMS)
        mutable.update { it.copy(messageReadRevisions = read) }
        viewModelScope.launch {
            runCatching {
                val receipts = graph.store.messageReceipts()
                graph.store.messageReceipts(receipts.copy(read = read))
            }.onFailure { HydrosLog.log("message", "保存消息已读失败：" + it.message) }
        }
    }

    fun loadDetail(id: String, force: Boolean = false) {
        viewModelScope.launch { billHistory.loadDetail(id, force = force) }
    }
}
/** Distinguishes a timed-out lookup from a completed lookup that found no matching bill. */
private class LocatedBill(val bill: BillUi?)
fun phoneError(value: String): String? = if(Regex("1[3-9]\\d{9}").matches(value)) null else "请输入 11 位手机号"
fun passwordError(value: String): String? = if(value.length in 6..32 && value.any { !it.isWhitespace() }) null else "请输入 6–32 位密码"
fun codeError(value: String): String? = if(Regex("\\d{6}").matches(value)) null else "请输入 6 位验证码"
