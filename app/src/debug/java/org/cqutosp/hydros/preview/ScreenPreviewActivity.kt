package org.cqutosp.hydros.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.cqutosp.hydros.data.HydrosApplication
import org.cqutosp.hydros.data.storage.AndroidAppDataControl
import org.cqutosp.hydros.ui.state.AppIconController
import org.cqutosp.hydros.ui.state.HydrosViewModel
import org.cqutosp.hydros.ui.HydrosBottomBar
import org.cqutosp.hydros.ui.HomeWalletTabs
import org.cqutosp.hydros.ui.components.HoldActionHost
import org.cqutosp.hydros.ui.components.HomeBackdrop
import org.cqutosp.hydros.ui.components.HydrosPage
import org.cqutosp.hydros.ui.legal.LegalDocuments
import org.cqutosp.hydros.ui.scanner.CameraStatus
import org.cqutosp.hydros.ui.scanner.ScanState
import org.cqutosp.hydros.ui.screens.AccountForm
import org.cqutosp.hydros.ui.screens.AccountFormScreen
import org.cqutosp.hydros.ui.screens.AboutScreen
import org.cqutosp.hydros.data.storage.AppIconKeys
import org.cqutosp.hydros.ui.screens.AppIconScreen
import org.cqutosp.hydros.ui.screens.AppLogsScreen
import org.cqutosp.hydros.ui.screens.AppearanceScreen
import org.cqutosp.hydros.ui.screens.BillDetailScreen
import org.cqutosp.hydros.ui.screens.DeviceDetailScreen
import org.cqutosp.hydros.ui.screens.DeveloperScreen
import org.cqutosp.hydros.ui.screens.DisplaySettingsScreen
import org.cqutosp.hydros.ui.screens.DormBindingScreen
import org.cqutosp.hydros.ui.screens.DormNearbyDevice
import org.cqutosp.hydros.ui.screens.FeedbackSettingsScreen
import org.cqutosp.hydros.ui.screens.HomeScreen
import org.cqutosp.hydros.ui.screens.LegalDocumentScreen
import org.cqutosp.hydros.ui.screens.LicensesIndexScreen
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
import org.cqutosp.hydros.ui.screens.VoiceAssistantScreen
import org.cqutosp.hydros.ui.screens.WaterReportScreen
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
        // 调试用：--es applyIcon QZXY|DEFAULT 可在启动预览页前切换桌面图标别名。
        intent.getStringExtra("applyIcon")?.let { AppIconController.apply(this, it) }
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
                        titleContent = { HomeWalletTabs(progress = 0f, onSelect = {}) },
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
                    "wallet" -> HydrosPage(
                        "钱包",
                        titleContent = { HomeWalletTabs(progress = 1f, onSelect = {}) },
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
                            state = state,
                            onManageAccount = {},
                            onAppearance = {},
                            onAbout = {},
                            hasUpdate = true,
                            onWaterReport = {},
                            onWaterLimit = {},
                            onAvatarPath = {},
                        )
                    }
                    "water" -> HydrosPage("智能用水报告", onBack = {}) {
                        WaterReportScreen(
                            report = state.waterUsage,
                            limitCents = PREVIEW_LIMIT_CENTS,
                            onWaterLimit = {},
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
                        bottomBar = {
                            HydrosBottomBar(route = "recharge", onNavigate = {}, onScan = {}, onHeight = {})
                        },
                    ) {
                        RechargeScreen(
                            ui = previewRechargeUi(),
                            merchant = "南阳科技职业学院",
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
                                voiceAssistantEnabled = live.voiceAssistantEnabled,
                                onOpenVoiceAssistant = {},
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
                    "appicon" -> HydrosPage("桌面图标", onBack = {}) {
                        AppIconScreen(
                            current = AppIconKeys.DEFAULT,
                            onSelect = {},
                        )
                    }
                    "voice" -> HydrosPage("智能助手", onBack = {}) {
                        VoiceAssistantScreen(enabled = true, onEnabled = {}, onTest = {})
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
                        )
                    }
                    "releasedetail" -> HydrosPage(release.name, onBack = {}) {
                        ReleaseDetailScreen(release)
                    }
                    "developer" -> HydrosPage("开发者页面", onBack = {}) {
                        DeveloperScreen(
                            demoMode = true,
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
                            voiceAssistantEnabled = true,
                            onOpenVoiceAssistant = {},
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
                    "licenses" -> HydrosPage("开源许可", onBack = {}) {
                        LicensesIndexScreen(onProject = {}, onThirdParty = {})
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
                    "privacy" -> HydrosPage("隐私政策", onBack = {}) {
                        LegalDocumentScreen(LegalDocuments.PRIVACY_POLICY)
                    }

                    // ----- 首次启动 / 登录 -----
                    "onboarding" -> OnboardingScreen(onComplete = {})
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
