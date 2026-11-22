package org.cqutosp.hydros.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.cqutosp.hydros.R
import org.cqutosp.hydros.data.HydrosApplication
import org.cqutosp.hydros.data.messages.CampusMessage
import org.cqutosp.hydros.data.storage.AndroidAppDataControl
import org.cqutosp.hydros.ui.state.HydrosViewModel
import org.cqutosp.hydros.ui.HydrosBottomBar
import org.cqutosp.hydros.ui.HomeWalletTabs
import org.cqutosp.hydros.ui.HeaderChip
import org.cqutosp.hydros.ui.NewVersionChip
import org.cqutosp.hydros.ui.components.GlassIconButton
import org.cqutosp.hydros.ui.components.InfoRow
import org.cqutosp.hydros.ui.components.Section
import org.cqutosp.hydros.ui.components.SubtleDivider
import org.cqutosp.hydros.ui.components.Glyph
import org.cqutosp.hydros.ui.components.HoldActionHost
import org.cqutosp.hydros.ui.components.HomeBackdrop
import org.cqutosp.hydros.ui.components.HydrosPage
import org.cqutosp.hydros.ui.components.PageColumn
import org.cqutosp.hydros.ui.legal.LegalDocuments
import org.cqutosp.hydros.ui.scanner.CameraStatus
import org.cqutosp.hydros.ui.scanner.ScanState
import org.cqutosp.hydros.ui.screens.AccountForm
import org.cqutosp.hydros.ui.screens.AccountFormScreen
import org.cqutosp.hydros.ui.screens.AboutScreen
import org.cqutosp.hydros.ui.screens.AppLogsScreen
import org.cqutosp.hydros.ui.screens.AppearanceScreen
import org.cqutosp.hydros.ui.screens.BillDetailScreen
import org.cqutosp.hydros.ui.screens.CampusMessageDetailScreen
import org.cqutosp.hydros.ui.screens.CampusMessagesScreen
import org.cqutosp.hydros.ui.screens.NotificationsScreen
import org.cqutosp.hydros.ui.share.MessageShareAction
import org.cqutosp.hydros.data.notify.NotificationTopic
import org.cqutosp.hydros.data.storage.NotificationSettings
import org.cqutosp.hydros.ui.screens.DeviceDetailScreen
import org.cqutosp.hydros.ui.screens.DeveloperScreen
import org.cqutosp.hydros.ui.screens.DisplaySettingsScreen
import org.cqutosp.hydros.ui.screens.DormBindingScreen
import org.cqutosp.hydros.ui.screens.DormNearbyDevice
import org.cqutosp.hydros.ui.screens.FeedbackSettingsScreen
import org.cqutosp.hydros.ui.screens.HomeScreen
import org.cqutosp.hydros.ui.screens.LegalDocumentScreen
import org.cqutosp.hydros.ui.screens.LicensesIndexScreen
import org.cqutosp.hydros.ui.screens.ReferencedProjectsScreen
import org.cqutosp.hydros.ui.screens.WaterReportShareAction
import org.cqutosp.hydros.ui.screens.LoginScreen
import org.cqutosp.hydros.ui.screens.ManageAccountScreen
import org.cqutosp.hydros.ui.screens.MineScreen
import org.cqutosp.hydros.ui.screens.OnboardingScreen
import org.cqutosp.hydros.ui.screens.OpenSourceLicensesScreen
import org.cqutosp.hydros.ui.screens.ProjectLicenseScreen
import org.cqutosp.hydros.ui.screens.RechargeScreen
import org.cqutosp.hydros.ui.screens.ReleaseDetailScreen
import org.cqutosp.hydros.ui.screens.ReleaseHistoryScreen
import org.cqutosp.hydros.ui.screens.ScanContent
import org.cqutosp.hydros.ui.screens.SessionScreen
import org.cqutosp.hydros.ui.screens.SoftwareUpdateScreen
import org.cqutosp.hydros.ui.screens.UpdateSourceScreen
import org.cqutosp.hydros.ui.screens.UseCodeScreen
import org.cqutosp.hydros.ui.components.LocalLiquidGlassEnabled
import org.cqutosp.hydros.ui.screens.WalletScreen
import org.cqutosp.hydros.ui.screens.WaterLimitScreen
import org.cqutosp.hydros.ui.screens.WaterReportScreen
import org.cqutosp.hydros.ui.share.WaterReportPoster
import org.cqutosp.hydros.ui.theme.HydrosTheme
import org.cqutosp.hydros.ui.theme.ThemeMode
import java.time.YearMonth

/**
 * 调试专用：用 PreviewFixtures 里的“真实日志数据”渲染单个页面，方便逐页截图验收视觉。
 * 不参与 release 构建，也不需要登录或连校园网。
 *
 * 启动方式：
 * adb shell am start -n org.cqutosp.hydros/.preview.ScreenPreviewActivity --es screen water
 *
 * screen 取值（默认 water）：
 * 首页 home / 钱包 wallet / 我的 mine / 用水报告 water / 使用码 usecode / 绑定寝室 dorm /
 * 充值 recharge / 待支付订单 rechargepending / 设备详情 device / 设备使用 session /
 * 显示设置 display / 应用外观 appearance / 反馈设置 feedback / 关于 about / 软件更新 update /
 * 更新历史 releases / 更新详情 releasedetail / 开发者 developer / 开源许可 licenses /
 * 本项目许可证 licensesproject / 第三方依赖许可证 licensesthird / 服务协议 legal /
 * 隐私政策 privacy / 引导页 onboarding / 登录 login / 管理账户 account /
 * 更换手机号 accountphone / 修改密码 accountpassword / 扫一扫 scan / 账单详情 billdetail
 */
class ScreenPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "water"
        // 调试用：--ez liquidGlass false 可预览“普通磨砂”底栏。
        val liquidGlass = intent.getBooleanExtra("liquidGlass", true)
        val month = YearMonth.now()
        val state = previewUiState(month = month, avatarPath = demoAvatarFile(this))
        val appData = AndroidAppDataControl(applicationContext)
        val device = previewDeviceList().first()
        val session = previewSession(device)
        val release = previewRelease()
        setContent {
            HydrosTheme(mode = ThemeMode.LIGHT) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLiquidGlassEnabled provides liquidGlass
                ) {
                when (screen) {
                    // ----- 首页 / 钱包 -----
                    "home" -> HydrosPage(
                        "首页",
                        titleContent = {
                            Box(Modifier.fillMaxWidth()) {
                                HomeWalletTabs(progress = 0f, onSelect = {})
                                NewVersionChip(
                                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd),
                                    onClick = {},
                                )
                            }
                        },
                        background = { HomeBackdrop() },
                        bottomBar = {
                            HydrosBottomBar(
                                route = "home",
                                onNavigate = {},
                                onScan = {},
                                onHeight = {}
                            )
                        }
                    ) {
                        HomeScreen(
                            state = state,
                            onScan = {},
                            onDevice = {},
                            onSession = {},
                            onScanNearby = {},
                            onStopNearby = {},
                        )
                    }
                    "homeunread" -> HydrosPage(
                        "首页",
                        titleContent = {
                            Box(Modifier.fillMaxWidth()) {
                                HomeWalletTabs(progress = 0f, onSelect = {})
                                HeaderChip(
                                    text = "收到新消息",
                                    icon = R.drawable.ic_message_circle,
                                    tag = "message-chip",
                                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd),
                                    onClick = {},
                                )
                            }
                        },
                        background = { HomeBackdrop() },
                        bottomBar = {
                            HydrosBottomBar(route = "home", onNavigate = {}, onScan = {}, onHeight = {}, messageUnread = 2)
                        }
                    ) {
                        HomeScreen(
                            state = state,
                            onScan = {},
                            onDevice = {},
                            onSession = {},
                            onScanNearby = {},
                            onStopNearby = {},
                        )
                    }
                    "wallet" -> HydrosPage(
                        "钱包",
                        titleContent = {
                            Box(Modifier.fillMaxWidth()) {
                                HomeWalletTabs(progress = 1f, onSelect = {})
                                NewVersionChip(
                                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd),
                                    onClick = {},
                                )
                            }
                        },
                        background = { HomeBackdrop() },
                        bottomBar = {
                            HydrosBottomBar(route = "home", onNavigate = {}, onScan = {}, onHeight = {})
                        },
                    ) {
                        WalletScreen(state = state, onBill = {})
                    }

                    // ----- 我的 -----
                    "mine" -> HydrosPage("我的", background = { HomeBackdrop() },
                        bottomBar = { HydrosBottomBar(route = "mine", onNavigate = {}, onScan = {}, onHeight = {}) },
                    ) {
                        MineScreen(
                            state = state.copy(
                                messages = previewMessages(),
                                messageReadRevisions = listOf("qm-3|2026-11-19 09:00"),
                            ),
                            onManageAccount = {},
                            onAppearance = {},
                            onAbout = {},
                            hasUpdate = true,
                            onWaterReport = {},
                            onWaterLimit = {},
                            onAvatarPath = {},
                        )
                    }
                    "water" -> HydrosPage(
                        "智能用水报告",
                        onBack = {},
                        actions = { WaterReportShareAction(state.waterUsage, PREVIEW_LIMIT_CENTS) },
                    ) {
                        WaterReportScreen(
                            report = state.waterUsage,
                            limitCents = PREVIEW_LIMIT_CENTS,
                            onWaterLimit = {},
                            onOpenLimit = {},
                        )
                    }
                    "waterposter" -> HydrosPage("用水报告长图", onBack = {}) {
                        val poster = remember {
                            WaterReportPoster.render(
                                report = state.waterUsage,
                                limitCents = PREVIEW_LIMIT_CENTS,
                                primary = 0xFF2F6BEA.toInt(),
                            )
                        }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 16.dp)
                        ) {
                            Image(
                                bitmap = poster.asImageBitmap(),
                                contentDescription = "智能用水报告长图",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    "waterlimit" -> HydrosPage("用水限额", onBack = {}) {
                        WaterLimitScreen(
                            limitCents = PREVIEW_LIMIT_CENTS,
                            blockEnabled = true,
                            usage = state.waterUsage,
                            onSaveLimit = {},
                            onBlockLimit = {},
                        )
                    }
                    "account" -> HydrosPage("管理账户", onBack = {}) {
                        ManageAccountScreen(
                            state = state,
                            onPhone = {},
                            onPassword = {},
                            onLogout = {},
                            onSession = {},
                        )
                    }
                    "accountphone" -> HydrosPage(AccountForm.PHONE.title, onBack = {}) {
                        AccountFormScreen(
                            form = AccountForm.PHONE,
                            account = state.account,
                            onSend = { _, _ -> true },
                            onSave = { _, _, _, _ -> true },
                        )
                    }
                    "accountpassword" -> HydrosPage(AccountForm.PASSWORD.title, onBack = {}) {
                        AccountFormScreen(
                            form = AccountForm.PASSWORD,
                            account = state.account,
                            onSend = { _, _ -> true },
                            onSave = { _, _, _, _ -> true },
                        )
                    }

                    // ----- 第三方面板 -----
                    "usecode" -> HydrosPage("我的使用码", onBack = {}) {
                        UseCodeScreen(
                            ui = previewUseCodeUi(),
                            onRefresh = {},
                            onSetEnabled = {},
                            onGenerate = {},
                            onClaim = {},
                        )
                    }
                    "dorm" -> HydrosPage("绑定寝室", onBack = {}) {
                        DormBindingScreen(
                            bound = previewDorm(),
                            editing = false,
                            error = null,
                            onSave = { _, _, _ -> },
                            onClear = {},
                        )
                    }
                    "dormedit" -> HydrosPage("绑定寝室", onBack = {}) {
                        DormBindingScreen(
                            bound = null,
                            editing = true,
                            error = null,
                            onSave = { _, _, _ -> },
                            onClear = {},
                            nearbyDevices = listOf(
                                DormNearbyDevice("d1", "宿舍热水", "3C:61:A8:E0:61:50", "宿舍楼洗浴热水表-4#楼-6层-615房"),
                                DormNearbyDevice("d2", "2000直饮水", "3C:61:A8:E0:61:51", "宿舍楼饮水机-4号楼-6层-6楼东直饮水"),
                                DormNearbyDevice("d3", "6楼东热水", "3C:61:A8:E0:61:52", "宿舍楼热水-4号楼-6层-东侧"),
                            ),
                            nearbySearching = false,
                            onScanNearby = {},
                        )
                    }
                    "recharge" -> HydrosPage("充值",
                        background = { HomeBackdrop() },
                        actions = {
                            GlassIconButton(onClick = {}, description = "接口信息") {
                                Glyph(R.drawable.ic_info, modifier = Modifier.size(20.dp))
                            }
                        },
                        bottomBar = {
                            HydrosBottomBar(route = "recharge", onNavigate = {}, onScan = {}, onHeight = {})
                        },
                    ) {
                        RechargeScreen(
                            ui = previewRechargeUi(),
                            merchant = "广西泰力电器有限公司",
                            pendingOrder = null,
                            onOpen = {},
                            onRefresh = {},
                            onSelectPackage = {},
                            onSelectChannel = {},
                            onSubmit = {},
                            onConsumeOrder = {},
                            onPaymentResult = {},
                        )
                    }
                    "rechargeinfo" -> HydrosPage("接口信息", onBack = {}) {
                        PageColumn {
                            Section("项目信息") {
                                previewAccount().projectFields.forEachIndexed { index, field ->
                                    if (index > 0) SubtleDivider()
                                    InfoRow(field.label, field.value)
                                }
                            }
                        }
                    }
                    "rechargepending" -> HydrosPage("充值", onBack = {}) {
                        RechargeScreen(
                            ui = previewRechargeUi(),
                            pendingOrder = previewPendingOrder(),
                            onOpen = {},
                            onRefresh = {},
                            onSelectPackage = {},
                            onSelectChannel = {},
                            onSubmit = {},
                            onConsumeOrder = {},
                            onPaymentResult = {},
                        )
                    }

                    // ----- 设备 / 会话 / 账单 -----
                    "device" -> HydrosPage("设备详情", onBack = {}) {
                        DeviceDetailScreen(
                            device = device,
                            state = state,
                            onConnect = {},
                            onStart = {},
                            onSession = {},
                            onBack = {},
                            onFavorite = {},
                            onSaveRemark = { _, _ -> true },
                        )
                    }
                    "session" -> HydrosPage("设备使用", onBack = {}) {
                        SessionScreen(
                            session = session,
                            onFinish = {},
                            onHome = {},
                            remarks = state.deviceRemarks,
                            observation = state.observation,
                        )
                    }
                    "billdetail" -> HydrosPage("账单详情", onBack = {}) {
                        BillDetailScreen(
                            bill = previewBillForDetail(month),
                            onWallet = {},
                            backLabel = "返回钱包",
                        )
                    }

                    // ----- 设置 -----
                    "display" -> HydrosPage("显示设置", onBack = {}) {
                        DisplaySettingsScreen(
                            savedScale = state.fontSizeScale,
                            onPreview = {},
                            onSave = { true },
                            postSessionDestination = state.postSessionDestination,
                            onPostSessionDestination = {},
                            homeConnectCardVisible = state.homeConnectCardVisible,
                            onHomeConnectCardVisible = {},
                        )
                    }
                    "appearance" -> HydrosPage("应用外观", onBack = {}) {
                        AppearanceScreen(theme = state.theme, onTheme = {})
                    }
                    "devmodel" -> {
                        val app = LocalContext.current.applicationContext as HydrosApplication
                        val vm = viewModel<HydrosViewModel>(factory = remember(app) {
                            object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                    HydrosViewModel(app.dependencies) as T
                            }
                        })
                        val live by vm.state.collectAsStateWithLifecycle()
                        HydrosPage("开发者（调试）") {
                            DeveloperScreen(
                                demoMode = live.demoMode,
                                onDemoMode = vm::demoMode,
                                devices = live.devices,
                                onAddDemoDevice = vm::addDemoDevice,
                                onUpdateDemoDevice = vm::updateDemoDevice,
                                onRemoveDemoDevice = vm::removeDemoDevice,
                            )
                        }
                    }
                    "homedemo" -> {
                        val app = LocalContext.current.applicationContext as HydrosApplication
                        val vm = viewModel<HydrosViewModel>(factory = remember(app) {
                            object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                    HydrosViewModel(app.dependencies) as T
                            }
                        })
                        val live by vm.state.collectAsStateWithLifecycle()
                        LaunchedEffect(Unit) {
                            if (!live.demoMode) vm.demoMode(true)
                        }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(400)
                            vm.discoverNearby()
                        }
                        HydrosPage("首页", background = { org.cqutosp.hydros.ui.components.HomeBackdrop() }) {
                            HomeScreen(
                                state = live,
                                onScan = {},
                                onDevice = {},
                                onSession = {},
                                onScanNearby = vm::discoverNearby,
                                onStopNearby = vm::stopDiscovery,
                                onNearbyDevice = {},
                                onWallet = {},
                            )
                        }
                    }
                    "demorun" -> {
                        val app = LocalContext.current.applicationContext as HydrosApplication
                        val vm = viewModel<HydrosViewModel>(factory = remember(app) {
                            object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                    HydrosViewModel(app.dependencies) as T
                            }
                        })
                        val live by vm.state.collectAsStateWithLifecycle()
                        LaunchedEffect(Unit) {
                            if (!live.demoMode) vm.demoMode(true)
                            kotlinx.coroutines.delay(300)
                            vm.connect(live.devices.first { it.id == "demo-shower-615" })
                        }
                        HoldActionHost {
                        HydrosPage("设备详情（调试）") {
                            DeviceDetailScreen(
                                device = live.devices.firstOrNull { it.id == "demo-shower-615" },
                                state = live,
                                onConnect = vm::connect,
                                onStart = vm::start,
                                onSession = {},
                                onBack = {},
                                onFavorite = vm::toggleFavorite,
                                onSaveRemark = vm::saveDeviceRemark,
                            )
                        }
                        }
                    }
                    "demosession" -> {
                        val app = LocalContext.current.applicationContext as HydrosApplication
                        val vm = viewModel<HydrosViewModel>(factory = remember(app) {
                            object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                    HydrosViewModel(app.dependencies) as T
                            }
                        })
                        val live by vm.state.collectAsStateWithLifecycle()
                        LaunchedEffect(Unit) {
                            if (!live.demoMode) vm.demoMode(true)
                            kotlinx.coroutines.delay(300)
                            live.devices.firstOrNull { it.id == "demo-shower-615" }?.let(vm::start)
                        }
                        SessionScreen(session = live.session, onFinish = vm::finish, onHome = {})
                    }
                    "feedback" -> HydrosPage("反馈设置", onBack = {}) {
                        FeedbackSettingsScreen(
                            settings = state.feedback,
                            onHaptic = {},
                            onReduceMotion = {},
                        )
                    }

                    // ----- 关于 / 更新 / 许可 / 法律 -----
                    "about" -> HydrosPage("关于 Hydros", onBack = {}) {
                        AboutScreen(
                            updates = previewUpdateState(),
                            onUpdates = {},
                            onLicenses = {},
                            onProjectLicense = {},
                            onTerms = {},
                            onPrivacy = {},
                            onDeveloper = {},
                            appData = appData,
                            onClear = { true },
                            onClearCache = { true },
                        )
                    }
                    "update" -> HydrosPage("软件更新", onBack = {}) {
                        SoftwareUpdateScreen(updates = previewUpdateState(), onCheck = {},
                            updateSource = org.cqutosp.hydros.data.update.UpdateSource.GITEE)
                    }
                    "updatesource" -> HydrosPage("更新源", onBack = {}) {
                        UpdateSourceScreen(
                            selected = org.cqutosp.hydros.data.update.UpdateSource.GITEE,
                            onSelect = {},
                        )
                    }
                    "releases" -> HydrosPage("更新历史", onBack = {}) {
                        ReleaseHistoryScreen(
                            state = previewReleaseHistory(),
                            onRelease = {},
                            onLoad = {},
                            source = org.cqutosp.hydros.data.update.UpdateSource.GITEE,
                        )
                    }
                    "releasedetail" -> HydrosPage(release.name, onBack = {}) {
                        ReleaseDetailScreen(release.copy(source = org.cqutosp.hydros.data.update.UpdateSource.GITEE))
                    }
                    "developer" -> HydrosPage("开发者页面", onBack = {}) {
                        DeveloperScreen(
                            demoMode = true,
                            updateCheckEnabled = true,
                            buildInfo = listOf(
                                "版本" to "1.5.0",
                                "构建时间" to "2026年11月20日 16:20",
                                "Android SDK" to "36",
                                "系统版本" to "16",
                                "机型" to "Xiaomi 24031PN0DC",
                                "应用 ID" to "org.cqutosp.hydros",
                            ),
                            devices = previewDeviceList(),
                            onAddDemoDevice = { _, _, _, _ -> },
                            onUpdateDemoDevice = { _, _, _, _, _ -> },
                            onRemoveDemoDevice = {},
                        )
                    }
                    "devlogs" -> HydrosPage("应用日志", onBack = {}) {
                        AppLogsScreen(
                            logs = listOf(
                                "16:20:41  demo  启动时恢复演示数据模式",
                                "16:20:42  device  开始使用 宿舍热水",
                                "16:20:53  bill  账单刷新完成：本月 36 条",
                                "16:21:02  water  用水报告已更新：¥ 13.04",
                                "16:21:11  network  查询使用码成功（483920）",
                                "16:21:19  thirdparty  寝室绑定：4#楼 615 房",
                                "16:21:26  recharge  刷新余额成功：¥ 30.09",
                            ),
                        )
                    }
                    "messages" -> HydrosPage("消息", background = { HomeBackdrop() },
                        bottomBar = { HydrosBottomBar(route = "messages", onNavigate = {}, onScan = {}, onHeight = {}, messageUnread = 2) },
                    ) {
                        CampusMessagesScreen(
                            messages = previewMessages(),
                            readRevisions = listOf("qm-3|2026-11-19 09:00"),
                            loaded = true,
                            onOpen = {},
                        )
                    }
                    "messagedetail" -> HydrosPage("消息详情", onBack = {},
                        actions = { MessageShareAction(previewMessages().first(), onResult = {}) },
                    ) {
                        CampusMessageDetailScreen(previewMessages().first())
                    }
                    "notifications" -> HydrosPage("通知管理", onBack = {}) {
                        NotificationsScreen(
                            settings = NotificationSettings(),
                            onMasterChange = {},
                            onTopicChange = { _, _ -> },
                            onTestNotification = {},
                        )
                    }
                    "licenses" -> HydrosPage("开源许可", onBack = {}) {
                        LicensesIndexScreen(onProject = {}, onThirdParty = {}, onReferences = {})
                    }
                    "references" -> HydrosPage("参考的开源项目", onBack = {}) {
                        ReferencedProjectsScreen()
                    }
                    "licensesproject" -> HydrosPage("本项目许可证", onBack = {}) {
                        ProjectLicenseScreen()
                    }
                    "licensesthird" -> HydrosPage("第三方依赖许可证", onBack = {}) {
                        OpenSourceLicensesScreen()
                    }
                    "legal" -> HydrosPage("服务协议", onBack = {}) {
                        LegalDocumentScreen(LegalDocuments.TERMS_OF_SERVICE)
                    }
                    "plusterms" -> HydrosPage("Hydros+ 服务协议", onBack = {}) {
                        LegalDocumentScreen(LegalDocuments.HYDROS_PLUS_TERMS)
                    }
                    "privacy" -> HydrosPage("隐私政策", onBack = {}) {
                        LegalDocumentScreen(LegalDocuments.PRIVACY_POLICY)
                    }

                    // ----- 首次启动 / 登录 -----
                    "onboarding" -> OnboardingScreen(onComplete = {})
                    "onboardlegal" -> OnboardingScreen(onComplete = {}, initialStep = 1)
                    "login" -> HydrosPage("登录") {
                        LoginScreen(
                            initialPhone = "13800138000",
                            footer = {
                                if (state.session != null) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        TextButton(onClick = {}) {
                                            Text("返回当前设备，关闭或恢复使用")
                                        }
                                    }
                                }
                            },
                        )
                    }

                    // ----- 扫一扫（裸页，和 HydrosApp 里一致，不套 HydrosPage） -----
                    "scan" -> ScanContent(
                        state = ScanState(),
                        cameraGranted = true,
                        cameraStatus = CameraStatus(ready = true, hasFlash = true, torchOn = true),
                        onPermission = {},
                        onSettings = {},
                        onTorch = {},
                        onPhoto = {},
                        onRetry = {},
                        onChoose = {},
                        queryProgress = null,
                        preview = { modifier -> Box(modifier.background(Color.Black)) },
                    )

                    // 默认与旧版本保持一致：用水报告。
                    else -> HydrosPage("智能用水报告", onBack = {}) {
                        WaterReportScreen(
                            report = state.waterUsage,
                            limitCents = PREVIEW_LIMIT_CENTS,
                            onWaterLimit = {},
                        )
                    }
                }
                }
            }
        }
    }
}

/** 消息页预览数据：三条校园通知，其中一条已读。 */
private fun previewMessages(): List<CampusMessage> = listOf(
    CampusMessage(
        id = "qm-1",
        title = "今晚 22:00-23:30 4 号楼停水检修",
        publishedAt = "2026-11-21 10:20",
        summary = "4 号楼 6 层今晚 22:00 起停水检修，预计 23:30 恢复，请提前储水。",
        body = "<div style=\"background:#FFFFFF;padding:20px 24px;\"><div style=\"margin-bottom:20px;text-align:center\"><h1 style=\"font-size:16px;color:#333333;\">南区宿舍停水通知</h1><div style=\"font-size:13px;color:#999999;\">2026-9-26 09:24:40</div></div><div style=\"font-size:13px;color:#333333;line-height:26px;\"><p>南区宿舍楼洗浴热水 9 月 26 日起停水检修水泵，10 月 6 日正常使用，给同学们造成不便敬请谅解。</p><p>检修期间请到北区公共浴室用水。</p></div></div>",
    ),
    CampusMessage(
        id = "qm-2",
        title = "本月水价调整说明",
        publishedAt = "2026-11-20 16:05",
        summary = "自 12 月 1 日起，宿舍热水单价调整为 0.028 元/L。",
        body = "自 12 月 1 日起，宿舍热水单价由 0.027 元/L 调整为 0.028 元/L。已产生的账单仍按原单价结算，用水量估算也会随新单价生效。",
    ),
    CampusMessage(
        id = "qm-3",
        title = "欢迎使用 Hydros+",
        publishedAt = "2026-11-19 09:00",
        summary = "扫码即可取水，账单、用水报告与限额都可以在应用里查看。",
        body = "欢迎使用 Hydros+。你可以在首页扫码连接设备，在钱包里查看每一笔账单，在“我的”里查看智能用水报告并设置用水限额。",
    ),
)

