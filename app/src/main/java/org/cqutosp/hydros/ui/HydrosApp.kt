package org.cqutosp.hydros.ui

import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.BackHandler
import org.cqutosp.hydros.ui.components.SnackbarEnd
import androidx.core.view.WindowCompat

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import org.cqutosp.hydros.ui.components.isDarkScheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import org.cqutosp.hydros.ui.theme.LocalHydrosGradient
import org.cqutosp.hydros.ui.theme.LocalHydrosGradientEnabled
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.compose.ui.platform.LocalContext
import org.cqutosp.hydros.data.HydrosApplication
import org.cqutosp.hydros.data.storage.PostSessionDestination
import android.os.Build
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.navigation.compose.*
import org.cqutosp.hydros.BuildConfig
import org.cqutosp.hydros.R
import org.cqutosp.hydros.thirdparty.AlipayResult
import org.cqutosp.hydros.thirdparty.RechargeOrder
import org.cqutosp.hydros.ui.thirdparty.launchAlipayOrder
import org.cqutosp.hydros.ui.components.HydrosPage
import org.cqutosp.hydros.ui.components.PageColumn
import org.cqutosp.hydros.ui.components.InlineState
import org.cqutosp.hydros.ui.components.PrimaryAction
import org.cqutosp.hydros.ui.components.TextAction
import org.cqutosp.hydros.ui.thirdparty.ThirdPartyViewModel
import org.cqutosp.hydros.ui.thirdparty.thirdPartyViewModel
import org.cqutosp.hydros.ui.components.HydrosSnackbarProvider
import org.cqutosp.hydros.ui.components.HydrosSnackbarState
import org.cqutosp.hydros.ui.components.Glyph
import org.cqutosp.hydros.ui.components.GlassIconButton
import org.cqutosp.hydros.ui.components.Section
import org.cqutosp.hydros.ui.components.SubtleDivider
import org.cqutosp.hydros.ui.components.InfoRow
import org.cqutosp.hydros.ui.components.hydrosPressable
import org.cqutosp.hydros.ui.components.HoldActionHost
import org.cqutosp.hydros.ui.components.rememberHydrosHaptics
import org.cqutosp.hydros.ui.legal.LegalDocuments
import com.mikepenz.markdown.model.rememberMarkdownState
import org.cqutosp.hydros.ui.state.*
import org.cqutosp.hydros.ui.screens.*
import org.cqutosp.hydros.ui.share.MessageShareAction
import org.cqutosp.hydros.ui.theme.HydrosTheme
import org.cqutosp.hydros.ui.theme.FontChoice
import org.cqutosp.hydros.ui.theme.fontSet
import org.cqutosp.hydros.ui.theme.GradientPreset
import androidx.activity.ComponentActivity
import org.cqutosp.hydros.ui.components.LocalLiquidBackdrop
import org.cqutosp.hydros.ui.components.LocalLiquidGlassEnabled
import org.cqutosp.hydros.ui.components.drawLiquidDroplet
import org.cqutosp.hydros.ui.components.drawLiquidFusion
import org.cqutosp.hydros.ui.components.drawLiquidRipple
import org.cqutosp.hydros.ui.components.capsulePath
import org.cqutosp.hydros.ui.components.circlePath
import org.cqutosp.hydros.ui.components.drawLiquidGlassPanel
import org.cqutosp.hydros.ui.components.drawLiquidGlassBallTint
import org.cqutosp.hydros.ui.components.LocalHydrosWallpaper
import org.cqutosp.hydros.ui.components.WallpaperConfig
import androidx.activity.result.PickVisualMediaRequest
import org.cqutosp.hydros.ui.theme.IosTokens
import org.cqutosp.hydros.ui.theme.LocalFeedbackSettings

private data class AppDestination(val route: String, val label: String, val icon: Int, val iconOutline: Int)
private val destinations = listOf(
    AppDestination("home", "首页", R.drawable.ic_home, R.drawable.ic_home),
    AppDestination("messages", "消息", R.drawable.ic_message_circle, R.drawable.ic_message_circle),
    AppDestination("recharge", "充值", R.drawable.ic_wallet, R.drawable.ic_wallet_outline),
    AppDestination("mine", "我的", R.drawable.ic_person, R.drawable.ic_person_outline),
)

private fun isTabSwitch(from: String?, to: String?): Boolean =
    destinations.any { it.route == from } && destinations.any { it.route == to }

@Composable
private fun hydrosViewModel(): HydrosViewModel {
    val dependencies=(LocalContext.current.applicationContext as HydrosApplication).dependencies
    return viewModel(factory=remember(dependencies) { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HydrosViewModel(dependencies) as T
    } })
}

@Composable
private fun appInfoViewModel(): AppInfoViewModel {
    val context = LocalContext.current.applicationContext
    val app = context as HydrosApplication
    val source = app.appUpdates
    val installer = app.appInstaller
    return viewModel(factory = remember(source, installer) { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppInfoViewModel(source, installer) as T
    } })
}

@Composable
fun HydrosApp(model: HydrosViewModel = hydrosViewModel(), appInfo: AppInfoViewModel = appInfoViewModel(),
    thirdParty: ThirdPartyViewModel = thirdPartyViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val updates by appInfo.updates.collectAsStateWithLifecycle()
    // 版本更新也走系统通知，是否提醒由「通知管理」里的版本更新开关决定。
    LaunchedEffect(updates.hasUpdate, updates.release?.version) {
        if (updates.hasUpdate) updates.release?.version?.let(model::notifyUpdateAvailable)
    }
    val history by appInfo.history.collectAsStateWithLifecycle()
    val detail by appInfo.detail.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    // 首页与钱包是同一页面的两个顶部标签，用 pager 承载以保留滑动切换与拖拽指示条。
    val pageState = rememberPagerState(pageCount = { 2 })
    val pageScope = rememberCoroutineScope()
    var fontSizePreview by remember { mutableStateOf<Float?>(null) }
    // Parse static legal documents off the UI thread before either entry is opened.
    // These states outlive their destinations and are reused on subsequent visits.
    val terms = rememberMarkdownState(LegalDocuments.TERMS_OF_SERVICE)
    val privacy = rememberMarkdownState(LegalDocuments.PRIVACY_POLICY)
    val plusTerms = rememberMarkdownState(LegalDocuments.HYDROS_PLUS_TERMS)
    val context=LocalContext.current
    val appData = (context.applicationContext as HydrosApplication).appData
    // The installer exposes at most one pending settings/confirmation intent; launch it as the
    // system would after a silent commit so the user never has to find the right button.
    LaunchedEffect(updates.install.phase, updates.install.error) {
        appInfo.takeUpdateLaunchIntent()?.let { intent ->
            if (runCatching { context.startActivity(intent) }.isFailure) appInfo.restoreUpdateLaunchIntent(intent)
        }
    }
    val snackbar = remember { HydrosSnackbarState() }
    var permissionTarget by remember { mutableStateOf<DeviceUi?>(null) }
    fun bluetoothGranted() = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    val bluetoothPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        permissionTarget?.let { target ->
            if (bluetoothGranted()) {
                permissionTarget = null
                model.connect(target)
            } else snackbar.show("请允许附近设备权限，授权后将继续连接", action = org.cqutosp.hydros.ui.components.SnackbarAction("前往设置") {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
            }, error = true)
        }
    }
    fun requestConnect(device: DeviceUi) {
        if (!state.signedIn || state.reauthentication != null) return
        val permissions=arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        if(permissions.any { ContextCompat.checkSelfPermission(context, it)!=PackageManager.PERMISSION_GRANTED }) {
            permissionTarget=device; bluetoothPermission.launch(permissions)
        } else model.connect(device)
    }
    fun openPage(destination: String) {
        if (state.reauthentication != null) return
        if (!state.signedIn && destination != "session" && !destination.startsWith("legal/")) return
        // 钱包不再是独立路由：把它当作首页 pager 的第二页，返回时也不会退回首页。
        if (destination == "wallet") {
            pageScope.launch {
                if (state.feedback.reduceMotionEnabled) pageState.scrollToPage(1)
                else pageState.animateScrollToPage(1)
            }
            return
        }
        nav.navigate(destination)
    }
    var handledSession by rememberSaveable { mutableStateOf<String?>(null) }
    var billFromSession by rememberSaveable { mutableStateOf(false) }
    fun dismissSessionScreen() {
        if (state.session == null) {
            handledSession = null
            model.dismissPostSessionNavigation()
        }
    }
    fun goHome() {
        dismissSessionScreen()
        // popBackStack 是唯一稳定的回首页方式：navigate("home") 在 session 仍在栈顶时会因为
        // popUpTo + singleTop + saveState 的组合被忽略，表现成“点了返回首页没反应”。
        if (!nav.popBackStack("home", inclusive = false)) {
            nav.navigate("home") {
                popUpTo(nav.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    fun back() {
        if (state.reauthentication == null) {
            if (nav.currentDestination?.route == "session") dismissSessionScreen()
            nav.popBackStack()
        }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (!it) snackbar.show("未开启通知权限，后台设备通知可能无法显示")
    }
    LaunchedEffect(state.initializing, state.signedIn, state.onboardingCompleted, state.reauthentication) {
        if(!state.initializing && state.reauthentication == null) {
            val currentRoute=nav.currentDestination?.route
            val target=when {
                !state.signedIn && !state.onboardingCompleted && currentRoute != "onboarding" -> "onboarding"
                !state.signedIn && state.onboardingCompleted && currentRoute != "login" -> "login"
                state.signedIn && (currentRoute == "login" || currentRoute == "onboarding") -> if (state.session != null) "session" else "home"
                else -> null
            }
            if(target != null) {
                nav.navigate(target) { popUpTo(nav.graph.id) { inclusive=true }; launchSingleTop=true }
            }
        }
    }
    AutoUpdateChecks(appInfo)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(state.reauthentication?.id, lifecycleOwner) {
        val notice = state.reauthentication ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val ticket = snackbar.show(notice.message, error = true, protected = true) ?: return@repeatOnLifecycle
            try {
                if (snackbar.awaitDismissal(ticket) != SnackbarEnd.REPLACED) {
                    nav.navigate("login") { popUpTo(nav.graph.id) { inclusive = true }; launchSingleTop = true }
                    model.acknowledgeReauthentication(notice.id)
                }
            } finally {
                snackbar.dismiss(ticket.id, SnackbarEnd.REPLACED)
            }
        }
    }
    BackHandler(state.reauthentication != null) { }
    LaunchedEffect(state.initializing, state.session?.device?.id, state.session?.startedAt, state.session?.stage,
        state.working, state.reauthentication, state.signedIn,
        state.resolvingPostSessionDestination, state.postSessionBillId) {
        if(!state.initializing && state.signedIn && state.reauthentication == null) {
            // The durable opening intent may still receive an explicit rejection (and prepare BLE).
            // Keep its preparation owner alive until an outcome or recovery state is available.
            if (state.working && state.session?.stage == SessionStage.OPENING) return@LaunchedEffect
            val sessionId=state.session?.let { "${it.device.id}:${it.startedAt}" }
            if(sessionId != null && sessionId != handledSession) {
                handledSession=sessionId; nav.navigate("session") { launchSingleTop=true }
            } else if(sessionId == null && handledSession != null && state.signedIn) {
                // The end-use destination may still be resolving a matching bill.
                if (state.resolvingPostSessionDestination) return@LaunchedEffect
                handledSession=null
                val billId=state.postSessionBillId
                if(billId != null) {
                    model.consumePostSessionBill(); billFromSession=true
                    nav.navigate("bill/$billId") { popUpTo("home"); launchSingleTop=true }
                } else {
                    if (state.postSessionDestination == PostSessionDestination.BILL)
                        snackbar.show("未找到本次账单，已返回设备页")
                    nav.navigate("home") { popUpTo("home"); launchSingleTop=true }
                }
            }
        }
    }
    val primaryStates = rememberSaveableStateHolder()
    val entry by nav.currentBackStackEntryAsState()
    val navigationRoute = entry?.destination?.route ?: "home"
    val route = navigationRoute
    LaunchedEffect(entry?.id, state.signedIn) {
        if (!state.signedIn || entry?.destination?.route != "device/{id}" ||
            permissionTarget?.id != entry?.arguments?.getString("id")) permissionTarget = null
    }
    DisposableEffect(lifecycleOwner, nav, model, appInfo) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val current = nav.currentBackStackEntry
                val deviceId = current?.arguments?.getString("id")
                model.recoverQueries(deviceId.takeIf { current?.destination?.route == "bill/{id}" })
                if (current?.destination?.route == "home") model.refreshFavoriteDevices()
                appInfo.onUpdateResumed()
                if (bluetoothGranted() && current?.destination?.route == "device/{id}" && deviceId != null) {
                    val pending = permissionTarget?.takeIf { it.id == deviceId }
                    if (pending != null) { permissionTarget = null; model.connect(pending) }
                    else model.recoverConnection(deviceId)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(navigationRoute == "home" && pageState.currentPage == 1 && state.reauthentication == null) {
        pageScope.launch {
            if (state.feedback.reduceMotionEnabled) pageState.scrollToPage(0) else pageState.animateScrollToPage(0)
        }
    }
    val targetDeviceId = if (route == "device/{id}") entry?.arguments?.getString("id") else null
    val targetDevice = targetDeviceId?.let { id -> state.devices.find { it.id == id } }
    LaunchedEffect(entry?.id, targetDevice != null) {
        if (targetDevice != null) requestConnect(targetDevice)
        else if (route != "device/{id}") model.cancelConnection()
    }
    fun main(destination: String) {
        if (!state.signedIn || state.reauthentication != null) return
        if (destination == "home") {
            // 底部的“首页”总是回到首页标签，而不是上次停留的钱包标签。
            pageScope.launch { pageState.scrollToPage(0) }
        }
        if (route == destination) return
        nav.navigate(destination) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val density = LocalDensity.current
    var mainBarHeight by remember { mutableStateOf(80.dp) }
    val updateBarHeight: (Int) -> Unit = { mainBarHeight = with(density) { it.toDp() } }
    val accentColor = state.accentColor?.let { Color(it) }
    val hydrosWallpaper = remember(state.wallpaperPath, state.wallpaperDim, state.wallpaperBlur) {
        state.wallpaperPath?.takeIf { it.isNotBlank() }?.let { WallpaperConfig(it, state.wallpaperDim, state.wallpaperBlur) }
    }
    HydrosTheme(
        mode = state.theme,
        feedback = state.feedback,
        fontSizeScale = fontSizePreview ?: state.fontSizeScale,
        fontWeightStep = state.fontWeightStep,
        fontSet = FontChoice.fromKey(state.fontFamily).fontSet(),
        accent = accentColor,
        gradientPreset = GradientPreset.fromKey(state.gradientPreset),
        gradientEnabled = state.gradientEnabled
    ) {
        val pageEasing = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }
        val haptics = rememberHydrosHaptics()
        var previousConnection by remember { mutableStateOf(state.connectionStage) }
        LaunchedEffect(state.connectionStage) {
            if (previousConnection == ConnectionStage.CONNECTING && state.connectionStage == ConnectionStage.READY) haptics.success()
            previousConnection = state.connectionStage
        }
        val window = LocalActivity.current?.window
        val lightSystemBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
        SideEffect {
            window?.let {
                WindowCompat.getInsetsController(it, it.decorView).apply {
                    isAppearanceLightStatusBars = lightSystemBars
                    isAppearanceLightNavigationBars = lightSystemBars
                }
            }
        }
        // Each destination owns its chrome so both pages retain their own bars during a back gesture.
        CompositionLocalProvider(
            LocalHydrosWallpaper provides hydrosWallpaper,
            LocalLiquidGlassEnabled provides state.liquidGlass
        ) {
        HydrosSnackbarProvider(snackbar, bottomPadding = if (destinations.any { it.route == route }) mainBarHeight else 0.dp) {
            HydrosNotificationEffects(state, updates, model::dismissMessage, model::refresh, model::loadMonth, appInfo::checkForUpdates, showUpdateErrors = route != "about/update",
                announceSession = route != "session" || state.session?.let { state.working || it.stage in listOf(SessionStage.OPENING, SessionStage.STOPPING, SessionStage.SETTLING) } != true,
                onSession = { openPage("session") })
            HoldActionHost {
                Box(Modifier.fillMaxSize()) {
                    NavHost(
                        // Fading only the parent over this backplate gives a subtle 10% scrim.
                        navController = nav, startDestination = "home", modifier = Modifier.fillMaxSize().background(Color.Black).pointerInput(state.reauthentication, route) {
                            if (state.reauthentication != null && route != "session") awaitPointerEventScope {
                                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        },
                        // Tab changes and reduced-motion navigation have no page translation.
                        enterTransition = {
                            if (state.feedback.reduceMotionEnabled || isTabSwitch(initialState.destination.route, targetState.destination.route)) EnterTransition.None
                            else slideInHorizontally(tween(300, easing = pageEasing)) { it }
                        },
                        exitTransition = {
                            if (state.feedback.reduceMotionEnabled || isTabSwitch(initialState.destination.route, targetState.destination.route)) ExitTransition.None
                            else slideOutHorizontally(tween(300, easing = pageEasing)) { -it / 5 } +
                                fadeOut(tween(300, easing = pageEasing), targetAlpha = 0.9f)
                        },
                        popEnterTransition = {
                            if (state.feedback.reduceMotionEnabled || isTabSwitch(initialState.destination.route, targetState.destination.route)) EnterTransition.None
                            else slideInHorizontally(tween(300, easing = pageEasing)) { -it / 5 } +
                                fadeIn(tween(300, easing = pageEasing), initialAlpha = 0.9f)
                        },
                        popExitTransition = {
                            if (state.feedback.reduceMotionEnabled || isTabSwitch(initialState.destination.route, targetState.destination.route)) ExitTransition.None
                            else slideOutHorizontally(tween(300, easing = pageEasing)) { it }
                        },
                    ) {
                        composable("home") {
                            val tabProgress = pageState.currentPage + pageState.currentPageOffsetFraction
                            HydrosPage("首页",
                                titleContent = {
                                    // HomeWalletTabs 自身会把宽度撑满标题区，所以「新版本可用」用同一
                                    // Box 的 End 对齐叠在右侧空白处，保证一定落在可见区域内。
                                    Box(Modifier.fillMaxWidth()) {
                                        HomeWalletTabs(progress = tabProgress, onSelect = { target ->
                                            pageScope.launch {
                                                if (state.feedback.reduceMotionEnabled) pageState.scrollToPage(target)
                                                else pageState.animateScrollToPage(target)
                                            }
                                        })
                                        // 顶栏最右侧只放一个入口：未读消息优先，其次才是新版本。
                                        when {
                                            state.unreadMessageCount > 0 -> HeaderChip(
                                                text = "收到新消息",
                                                icon = R.drawable.ic_message_circle,
                                                tag = "message-chip",
                                                modifier = Modifier.align(Alignment.CenterEnd),
                                                onClick = { main("messages") },
                                            )
                                            updates.hasUpdate -> HeaderChip(
                                                text = "新版本可用",
                                                icon = R.drawable.ic_download,
                                                tag = "update-chip",
                                                modifier = Modifier.align(Alignment.CenterEnd),
                                                onClick = { openPage("about/update") },
                                            )
                                        }
                                    }
                                },
                                background = if (hydrosWallpaper == null) {
                                    { org.cqutosp.hydros.ui.components.HomeBackdrop() }
                                } else null,
                                bottomBar = { HydrosBottomBar("home", ::main, onScan = { openPage("scan") }, onHeight = updateBarHeight, messageUnread = state.unreadMessageCount) }) {
                                HorizontalPager(
                                    state = pageState,
                                    modifier = Modifier.fillMaxSize().testTag("primary-pager"),
                                    userScrollEnabled = state.signedIn && state.reauthentication == null,
                                    flingBehavior = rememberPrimaryPagerFlingBehavior(pageState, state.feedback.reduceMotionEnabled),
                                ) { page ->
                                    if (page == 0) HomeScreen(state, onScan = { openPage("scan") },
                                        onDevice = { openPage("device/$it") },
                                        onNearbyDevice = { openPage("device/$it") },
                                        onSession = { openPage("session") },
                                        onWaterReport = { openPage("water/report") },
                                        onWallet = { openPage("wallet") },
                                        onRefreshFavorites = model::refreshFavoriteDevices,
                                        onScanNearby = model::discoverNearby, onStopNearby = model::stopDiscovery)
                                    else WalletScreen(state, onMonth = model::loadMonth,
                                        onBillType = model::selectBillType,
                                        onRefresh = model::refresh,
                                        onBill = { billFromSession = false; openPage("bill/$it") })
                                }
                            }
                        }
                        composable("recharge") {
                            HydrosPage("充值",
                                background = if (hydrosWallpaper == null) {
                                    { org.cqutosp.hydros.ui.components.HomeBackdrop() }
                                } else null,
                                actions = {
                                    GlassIconButton(onClick = { openPage("recharge/info") }, description = "接口信息") {
                                        Glyph(R.drawable.ic_info, modifier = Modifier.size(20.dp))
                                    }
                                },
                                bottomBar = { HydrosBottomBar("recharge", ::main, onScan = { openPage("scan") }, onHeight = updateBarHeight, messageUnread = state.unreadMessageCount) }) {
                                RechargeTab(thirdParty = thirdParty, context = context, snackbar = snackbar,
                                    // 运营商家由网关按学校下发，不是固定值；取不到时整行隐藏，不拿学校名兜底。
                                    merchant = state.account.operator.takeIf { it.isNotBlank() })
                            }
                        }
                        composable("recharge/info") {
                            HydrosPage("接口信息", onBack = { back() }) {
                                PageColumn {
                                Section("项目信息") {
                                    val fields = state.account.projectFields
                                    if (fields.isEmpty()) {
                                        InlineState("暂无接口信息", "本次没有从校园网关取到项目字段。", icon = R.drawable.ic_info)
                                    } else {
                                        fields.forEachIndexed { index, field ->
                                            if (index > 0) SubtleDivider()
                                            InfoRow(field.label, field.value)
                                        }
                                    }
                                }
                                }
                            }
                        }
                        composable("mine") {
                            HydrosPage("我的", background = if (hydrosWallpaper == null) {
                                { org.cqutosp.hydros.ui.components.HomeBackdrop() }
                            } else null,
                                bottomBar = { HydrosBottomBar("mine", ::main, onScan = { openPage("scan") }, onHeight = updateBarHeight, messageUnread = state.unreadMessageCount) }) {
                                primaryStates.SaveableStateProvider("mine") {
                                    MineScreen(state, onRefresh = model::refresh, onManageAccount = { openPage("account") },
                                        onAppearance = { openPage("appearance") },
                                        onFeedback = { openPage("feedback") },
                                        onDisplay = { openPage("display") },
                                        onWaterReport = { openPage("water/report") },
                                        onWaterLimit = model::waterLimit,
                                        onWaterLimitPage = { openPage("water/limit") },
                                        onUseCode = { openPage("thirdparty/usecode") },
                                        onDorm = { openPage("thirdparty/dorm") },
                                        onAvatarPath = model::avatarPath,
                                        onMessages = { main("messages") },
                                        onNotifications = { openPage("notifications") },
                                        onAbout = { openPage("about") }, hasUpdate = updates.hasUpdate)
                                }
                            }
                        }
                        composable("messages") {
                            HydrosPage("消息", background = if (hydrosWallpaper == null) {
                                { org.cqutosp.hydros.ui.components.HomeBackdrop() }
                            } else null,
                                bottomBar = { HydrosBottomBar("messages", ::main, onScan = { openPage("scan") }, onHeight = updateBarHeight, messageUnread = state.unreadMessageCount) }) {
                                primaryStates.SaveableStateProvider("messages") {
                                    CampusMessagesScreen(
                                        messages = state.messages,
                                        readRevisions = state.messageReadRevisions,
                                        loading = state.messagesLoading,
                                        loaded = state.messagesLoaded,
                                        error = state.messagesError,
                                        onRefresh = model::loadMessages,
                                        onMarkAllRead = model::markAllMessagesRead,
                                        onOpen = { openPage("message/" + Uri.encode(it.id)) },
                                    )
                                }
                            }
                        }
                        composable("message/{id}") { entry ->
                            val id = entry.arguments?.getString("id").orEmpty()
                            val message = state.messages.firstOrNull { it.id == id }
                            HydrosPage("消息详情", onBack = { back() },
                                actions = { if (message != null) MessageShareAction(message, onResult = { snackbar.show(it) }) }) {
                                CampusMessageDetailScreen(message, onRead = { model.markMessageRead(it.revision) })
                            }
                        }
                        composable("notifications") {
                            HydrosPage("通知管理", onBack = { back() }) {
                                NotificationsScreen(
                                    settings = state.notificationSettings,
                                    onMasterChange = model::notificationMaster,
                                    onTopicChange = model::notificationTopic,
                                    onTestNotification = model::testNotification,
                                )
                            }
                        }
                        composable("account") {
                            HydrosPage("管理账户", onBack = { back() }) {
                                ManageAccountScreen(state,
                                    onPhone = { openPage("account/PHONE") }, onPassword = { openPage("account/PASSWORD") },
                                    onLogout = model::logout, onSession = { openPage("session") })
                            }
                        }
                        composable("appearance") {
                            HydrosPage("应用外观", onBack = { back() }) {
                                AppearanceScreen(
                                    theme = state.theme,
                                    onTheme = model::theme,
                                    fontLabel = FontChoice.fromKey(state.fontFamily).label,
                                    gradientLabel = if (!state.gradientEnabled) "纯色背景" else GradientPreset.fromKey(state.gradientPreset).label,
                                    accentLabel = state.accentColor?.let { "自定义 #%06X".format(it and 0xFFFFFF) } ?: "跟随主题",
                                    wallpaperOn = !state.wallpaperPath.isNullOrBlank(),
                                    onOpenFont = { openPage("appearance/font") },
                                    onOpenThemeColor = { openPage("appearance/gradient") },
                                    onOpenAccent = { openPage("appearance/theme-color") },
                                    onOpenWallpaper = { openPage("appearance/wallpaper") },
                                )
                            }
                        }
                        composable("appearance/font") {
                            HydrosPage("字体", onBack = { back() }) {
                                FontSettingsScreen(state.fontFamily, onSelect = model::fontFamily)
                            }
                        }
                        composable("appearance/theme-color") {
                            // 主题色 = 全站强调色（按钮、选中态、图标底色、扫码球渐变都跟着走）。
                            HydrosPage("主题色", onBack = { back() }) {
                                AccentColorScreen(state.accentColor, onAccent = model::accentColor)
                            }
                        }
                        composable("appearance/gradient") {
                            HydrosPage("渐变背景", onBack = { back() }) {
                                ThemeColorScreen(state.gradientPreset, state.gradientEnabled, model::gradientPreset, model::gradientEnabled)
                            }
                        }
                        composable("appearance/wallpaper") {
                            val wallpaperScope = rememberCoroutineScope()
                            val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
                                if (picked != null) wallpaperScope.launch {
                                    val saved = runCatching { storeWallpaperFile(context, picked) }.getOrNull()
                                    if (saved != null) {
                                        model.wallpaperPath(saved)
                                        snackbar.show("壁纸已更新")
                                    } else {
                                        snackbar.show("无法读取这张图片，请换一张试试")
                                    }
                                }
                            }
                            HydrosPage("背景壁纸", onBack = { back() }) {
                                WallpaperScreen(
                                    path = state.wallpaperPath,
                                    dim = state.wallpaperDim,
                                    blur = state.wallpaperBlur,
                                    onPick = { wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    onClear = { model.wallpaperPath(null); snackbar.show("已恢复渐变背景") },
                                    onDim = model::wallpaperDim,
                                    onBlur = model::wallpaperBlur,
                                )
                            }
                        }
                        composable("feedback") {
                            HydrosPage("反馈设置", onBack = { back() }) {
                                FeedbackSettingsScreen(state.feedback, onHaptic = model::hapticFeedback,
                                    onReduceMotion = model::reduceMotion,
                                    onWaterAnimation = model::waterAnimationDisabled)
                            }
                        }
                        composable("display") {
                            HydrosPage("显示设置", onBack = { back() }) {
                                DisplaySettingsScreen(state.fontSizeScale, onPreview = { fontSizePreview = it }, onSave = model::fontSizeScale,
                                    postSessionDestination = state.postSessionDestination, onPostSessionDestination = model::postSessionDestination,
                                    homeConnectCardVisible = state.homeConnectCardVisible,
                                    onHomeConnectCardVisible = model::homeConnectCardVisible,
                                    liquidGlass = state.liquidGlass,
                                    onLiquidGlass = model::liquidGlass,
                                    fontWeightStep = state.fontWeightStep,
                                    onFontWeightStep = model::fontWeightStep)
                            }
                        }
                        composable("thirdparty/usecode") {
                            HydrosPage("使用码", onBack = { back() }) {
                                // 移植的 UseCodeScreen 自身不带首次加载，进入页面时补一次拉取。
                                LaunchedEffect(Unit) { thirdParty.refreshUseCode() }
                                DisposableEffect(Unit) { onDispose { thirdParty.clearUseCode() } }
                                val useCodeFlow = thirdParty.useCode
                                if (useCodeFlow == null) {
                                    InlineState("当前无法查看使用码", "未获取到校园网关连接，请稍后重试。", icon = R.drawable.ic_code)
                                } else {
                                    val ui by useCodeFlow.collectAsStateWithLifecycle()
                                    UseCodeScreen(ui, onRefresh = thirdParty::refreshUseCode,
                                        onSetEnabled = thirdParty::setUseCodeEnabled,
                                        onGenerate = thirdParty::generateUseCode,
                                        onClaim = thirdParty::claimUseCode)
                                }
                            }
                        }
                        composable("thirdparty/dorm") {
                            HydrosPage("绑定寝室", onBack = { back() }) {
                                val dorm by thirdParty.dorm.collectAsStateWithLifecycle()
                                // 扫描由页面在“选择设备”阶段自己发起，这里不再重复启动 BLE。
                                DormBindingScreen(dorm.bound, dorm.editing, dorm.error,
                                    onSave = thirdParty::saveDorm, onClear = thirdParty::clearDorm,
                                    onEdit = thirdParty::startEditDorm, onCancelEdit = thirdParty::cancelEditDorm,
                                    nearbyDevices = state.nearby.devices.map { device ->
                                        DormNearbyDevice(
                                            id = device.id,
                                            name = device.name,
                                            mac = device.connectMac.ifBlank { device.ledgerMac },
                                            location = device.location,
                                        )
                                    },
                                    nearbySearching = state.nearby.searching,
                                    onScanNearby = model::discoverNearby)
                            }
                        }
                        composable("thirdparty/recharge") {
                            HydrosPage("充值", onBack = { back() }) {
                                val rechargeFlow = thirdParty.recharge
                                if (rechargeFlow == null) {
                                    InlineState("当前无法充值", "未获取到校园网关连接，请稍后重试。", icon = R.drawable.ic_wallet)
                                } else {
                                    val ui by rechargeFlow.collectAsStateWithLifecycle()
                                    val pendingFlow = thirdParty.pendingRechargeOrder
                                    val pending = if (pendingFlow == null) null else pendingFlow.collectAsStateWithLifecycle().value
                                    RechargeScreen(ui, pending,
                                        onOpen = thirdParty::openRecharge,
                                        onRefresh = thirdParty::refreshRecharge,
                                        onSelectPackage = thirdParty::selectRechargePackage,
                                        onSelectChannel = thirdParty::selectRechargeChannel,
                                        onSubmit = thirdParty::submitRecharge,
                                        onConsumeOrder = { thirdParty.consumeRechargeOrder() },
                                        onPaymentResult = {})
                                    LaunchedEffect(pending) {
                                        val order = pending ?: return@LaunchedEffect
                                        when (order) {
                                            is RechargeOrder.Browser -> {
                                                val opened = runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(order.url)))
                                                }.isSuccess
                                                if (!opened) snackbar.show("无法打开支付页面，请检查是否已安装浏览器")
                                            }
                                            is RechargeOrder.Alipay -> {
                                                // PayTask 会阻塞到用户在支付宝里返回结果，交给 launcher 内部切 IO 线程。
                                                snackbar.show("正在拉起支付宝…")
                                                val result = launchAlipayOrder(context, order.orderInfo)
                                                thirdParty.completeRechargePayment(result)
                                                snackbar.show(result.label)
                                                if (result == AlipayResult.SUCCESS) thirdParty.refreshRechargeBalance()
                                            }
                                        }
                                        thirdParty.consumeRechargeOrder()
                                    }
                                }
                            }
                        }
                        composable("water/report") {
                            HydrosPage(
                                "智能用水报告",
                                onBack = { back() },
                                actions = { WaterReportShareAction(state.waterUsage, state.waterLimitCents) },
                            ) {
                                WaterReportScreen(
                                    report = state.waterUsage,
                                    limitCents = state.waterLimitCents,
                                    onWaterLimit = model::waterLimit,
                                    onMonth = { selected -> model.loadMonth(selected.toString()) },
                                    onOpenLimit = { openPage("water/limit") },
                                )
                            }
                        }
                        composable("water/limit") {
                            HydrosPage("用水限额", onBack = { back() }) {
                                WaterLimitScreen(
                                    limitCents = state.waterLimitCents,
                                    blockEnabled = state.waterLimitBlock,
                                    usage = state.waterUsage,
                                    onSaveLimit = { cents ->
                                        model.waterLimit(cents)
                                        snackbar.show(if (cents == null) "已取消本月用水限额" else "已保存用水限额 ¥" + org.cqutosp.hydros.ui.state.money(cents))
                                    },
                                    onBlockLimit = model::waterLimitBlock,
                                )
                            }
                        }
                        composable("about") {
                            HydrosPage("关于 Hydros+", onBack = { back() }) {
                                AboutScreen(updates, onUpdates = { openPage("about/update") },
                                    onLicenses = { openPage("about/licenses") },
                                    onProjectLicense = { openPage("about/licenses/project") },
                                    onTerms = { openPage("legal/terms") },
                                    onPrivacy = { openPage("legal/privacy") },
                                    onPlusTerms = { openPage("legal/plus-terms") },
                                    onDeveloper = { openPage("about/developer") },
                                    appData = appData,
                                    onClear = { model.clearAllData(appData::clearAllData) },
                                    onClearCache = { model.clearCache(appData::clearCache) })
                            }
                        }
                        composable("about/update") {
                            HydrosPage("软件更新", onBack = { back() }) {
                                LaunchedEffect(Unit) { appInfo.checkForUpdates() }
                                SoftwareUpdateScreen(updates, onHistory = { openPage("about/releases") },
                                    onInstall = appInfo::installUpdate,
                                    onContinue = { appInfo.continueInstall { intent ->
                                        if (runCatching { context.startActivity(intent) }.isFailure) appInfo.restoreUpdateLaunchIntent(intent)
                                    } },
                                    onCancel = appInfo::cancelInstall, onCheck = appInfo::checkForUpdates,
                                    updateSource = state.updateSource,
                                    onOpenUpdateSource = { openPage("about/update-source") })
                            }
                        }
                        composable("about/update-source") {
                            HydrosPage("更新源", onBack = { back() }) {
                                UpdateSourceScreen(state.updateSource, onSelect = { source ->
                                    if (source != state.updateSource) {
                                        model.updateSource(source)
                                        appInfo.onSourceChanged()
                                        snackbar.show("已切换到 " + source.label + "，正在重新获取版本信息")
                                    }
                                })
                            }
                        }
                        composable("about/releases") {
                            LaunchedEffect(Unit) { if (!history.loaded) appInfo.loadHistory() }
                            HydrosPage("更新历史", onBack = { back() }) {
                                ReleaseHistoryScreen(history, onRelease = { openPage("about/releases/${android.net.Uri.encode(it.tag)}") },
                                    onLoad = { appInfo.loadHistory() }, source = state.updateSource)
                            }
                        }
                        composable("about/releases/{tag}") { entry ->
                            val tag = entry.arguments?.getString("tag").orEmpty()
                            LaunchedEffect(tag) { appInfo.loadRelease(tag) }
                            HydrosPage(detail.releases.firstOrNull()?.name ?: tag, onBack = { back() }) {
                                detail.releases.firstOrNull()?.let { ReleaseDetailScreen(it) }
                                    ?: ReleaseHistoryScreen(detail, onRelease = {}, onLoad = { appInfo.loadRelease(tag) },
                                        source = state.updateSource)
                            }
                        }
                        composable("about/developer") {
                            HydrosPage("开发者页面", onBack = { back() }) {
                                var confirmClear by remember { mutableStateOf(false) }
                                DeveloperScreen(
                                    demoMode = state.demoMode,
                                    onDemoMode = model::demoMode,
                                    updateCheckEnabled = state.updateCheckEnabled,
                                    onUpdateCheckEnabled = { enabled ->
                                        model.updateCheckEnabled(enabled)
                                        if (!enabled) appInfo.clearError()
                                    },
                                    onOpenLogs = { openPage("about/developer/logs") },
                                    buildInfo = listOf(
                                        "版本" to BuildConfig.VERSION_NAME,
                                        "构建时间" to BuildConfig.BUILD_TIME,
                                        "Android SDK" to Build.VERSION.SDK_INT.toString(),
                                        "系统版本" to Build.VERSION.RELEASE,
                                        "机型" to (Build.MANUFACTURER + " " + Build.MODEL),
                                        "应用 ID" to BuildConfig.APPLICATION_ID,
                                    ),
                                    onCopyBuildInfo = {
                                        val text = listOf(
                                            "Hydros+ " + BuildConfig.VERSION_NAME,
                                            "构建时间 " + BuildConfig.BUILD_TIME,
                                            "Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")",
                                            "机型 " + Build.MANUFACTURER + " " + Build.MODEL,
                                        ).joinToString("\n")
                                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("Hydros+ 构建信息", text))
                                        snackbar.show("构建信息已复制")
                                    },
                                    onResetOnboarding = model::resetOnboarding,
                                    onClearData = { confirmClear = true },
                                    devices = state.devices,
                                    onAddDemoDevice = model::addDemoDevice,
                                    onUpdateDemoDevice = model::updateDemoDevice,
                                    onRemoveDemoDevice = model::removeDemoDevice,
                                )
                                if (confirmClear) {
                                    AlertDialog(
                                        onDismissRequest = { confirmClear = false },
                                        title = { Text("清除所有数据？") },
                                        text = { Text("会退出登录并清空本地设置、收藏与日志，此操作不可撤销。") },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                confirmClear = false
                                                if (model.clearAllData(appData::clearAllData)) snackbar.show("已清除所有数据")
                                            }) { Text("清除") }
                                        },
                                        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
                                    )
                                }
                            }
                        }
                        composable("about/developer/logs") {
                            HydrosPage("应用日志", onBack = { back() }) {
                                var logLines by remember { mutableStateOf(HydrosLog.snapshot()) }
                                AppLogsScreen(
                                    logs = logLines,
                                    onRefresh = {
                                        logLines = HydrosLog.snapshot()
                                        snackbar.show("已刷新日志（" + logLines.size + " 条）")
                                    },
                                    onClear = {
                                        model.clearLogs()
                                        logLines = emptyList()
                                        snackbar.show("日志已清空")
                                    },
                                )
                            }
                        }
                        composable("about/licenses") {
                            HydrosPage("开源许可", onBack = { back() }) {
                                LicensesIndexScreen(
                                    onProject = { openPage("about/licenses/project") },
                                    onThirdParty = { openPage("about/licenses/third-party") },
                                    onReferences = { openPage("about/licenses/references") },
                                )
                            }
                        }
                        composable("about/licenses/project") {
                            HydrosPage("本项目许可证", onBack = { back() }) { ProjectLicenseScreen() }
                        }
                        composable("about/licenses/third-party") {
                            HydrosPage("第三方依赖许可证", onBack = { back() }) { OpenSourceLicensesScreen() }
                        }
                        composable("about/licenses/references") {
                            HydrosPage("参考的开源项目", onBack = { back() }) { ReferencedProjectsScreen() }
                        }
                        composable("legal/terms") {
                            HydrosPage("服务协议", onBack = { back() }) {
                                LegalDocumentScreen(LegalDocuments.TERMS_OF_SERVICE, terms)
                            }
                        }
                        composable("legal/privacy") {
                            HydrosPage("隐私政策", onBack = { back() }) {
                                LegalDocumentScreen(LegalDocuments.PRIVACY_POLICY, privacy)
                            }
                        }
                        composable("legal/plus-terms") {
                            HydrosPage("Hydros+ 服务协议", onBack = { back() }) {
                                LegalDocumentScreen(LegalDocuments.HYDROS_PLUS_TERMS, plusTerms)
                            }
                        }
                        composable("onboarding") {
                            OnboardingScreen(
                                onComplete = model::completeOnboarding,
                                onTerms = { openPage("legal/terms") },
                                onPrivacy = { openPage("legal/privacy") },
                                onPlusTerms = { openPage("legal/plus-terms") }
                            )
                        }
                        composable("login") {
                            HydrosPage("登录") {
                                LoginScreen(
                                    onSend = { model.sendCode(it, 3) },
                                    onLogin = model::login,
                                    loginRetryAfterMillis = { model.graph.auth.loginRetryAfterMillis },
                                    initialPhone = state.lastAccount.orEmpty(),
                                    footer = {
                                        if (state.session != null) {
                                            Row(
                                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                TextButton(onClick = { openPage("session") }) {
                                                    Text("返回当前设备，关闭或恢复使用")
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        composable("device/{id}") { backStack ->
                            val device = state.devices.find { it.id == backStack.arguments?.getString("id") }
                            HydrosPage("设备详情", onBack = { back() }) {
                                DeviceDetailScreen(device, state, ::requestConnect, onStart = {
                                    if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    model.start(it)
                                }, onSession = { openPage("session") }, onBack = { back() }, onFavorite = model::toggleFavorite, onSaveRemark = model::saveDeviceRemark)
                            }
                        }
                        composable("scan") {
                            // 扫码页自带沉浸式黑色外壳（返回、手电筒、取景框都在页面里），不再套 HydrosPage 的白色顶栏。
                            ScanScreen(onResolve = model::scan, enabled = state.signedIn && state.reauthentication == null,
                                queryProgress = state.networkMessage,
                                onSelect = { openPage("device/$it") })
                        }
                        composable("session") {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary)) {
                                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(40.dp).hydrosPressable { back() }
                                                .clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Glyph(R.drawable.ic_back, modifier = Modifier.size(20.dp), tint = Color.White, description = "返回")
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text("设备使用", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                                    }
                                    Box(Modifier.weight(1f).fillMaxWidth()) {
                                        SessionScreen(state.session, onFinish = model::finish, onHome = { goHome() }, remarks = state.deviceRemarks, working = state.working, observation = state.observation,
                                            resolvingDestination = state.resolvingPostSessionDestination)
                                    }
                                }
                            }
                        }
                        composable("bill/{id}") { backStack ->
                            val billId = backStack.arguments?.getString("id")
                            LaunchedEffect(backStack.id) { billId?.let(model::loadDetail) }
                            HydrosPage("账单详情", onBack = { back() }) {
                                val currentBill = billId?.let { id ->
                                    state.billDetail?.takeIf { it.id == id } ?: state.bills.find { it.id == id }
                                }
                                val detailSync = billId?.let { state.billDetailSync[it] }
                                BillDetailScreen(
                                    bill = currentBill,
                                    onWallet = { if (state.reauthentication == null) { if (billFromSession) goHome() else nav.popBackStack("home", inclusive = false) } },
                                    backLabel = if (billFromSession) "返回首页" else "返回钱包",
                                    durationPending = currentBill != null && !currentBill.recharge &&
                                        currentBill.durationText == null && detailSync?.fetching == true,
                                    error = detailSync?.error,
                                    onRetry = { billId?.let { model.loadDetail(it, force = true) } },
                                )
                            }
                        }
                        composable("account/{form}") { backStack ->
                            val form = AccountForm.valueOf(backStack.arguments!!.getString("form")!!)
                            HydrosPage(form.title, onBack = { back() }) {
                                AccountFormScreen(form, state.account, onSend = model::sendCode) { phone, old, password, code ->
                                    val saved = model.saveAccount(form, phone, old, password, code)
                                    if (saved && form != AccountForm.PHONE) back()
                                    saved
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

/** 顶栏右侧的「新版本可用」胶囊，只在检测到新版本时出现。 */
@Composable
internal fun NewVersionChip(modifier: Modifier = Modifier, onClick: () -> Unit) =
    HeaderChip(text = "新版本可用", icon = R.drawable.ic_download, tag = "update-chip", modifier = modifier, onClick = onClick)

/** 顶栏右侧的胶囊提示：新版本与未读消息共用同一种样式。 */
@Composable
internal fun HeaderChip(
    text: String,
    icon: Int,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val haptics = rememberHydrosHaptics()
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier
            .testTag(tag)
            .clip(shape)
            .background(colors.primary.copy(alpha = 0.12f))
            .clickable { haptics.light(); onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Glyph(icon, modifier = Modifier.size(12.dp), tint = colors.primary)
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** 顶部一级标签：首页 / 钱包。progress 是 pager 的连续位置（0..1），跟随手势平滑过渡。 */
@Composable
internal fun HomeWalletTabs(progress: Float, onSelect: (Int) -> Unit) {
    val haptics = rememberHydrosHaptics()
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    val position = progress.coerceIn(0f, 1f)
    val tabWidth = with(density) { typography.headlineMedium.fontSize.toDp() * 2 + 20.dp }
    val indicatorHeight = with(density) { typography.headlineLarge.lineHeight.toDp() }
    Column(Modifier.testTag("primary-switcher")) {
    Row(
        Modifier.height(indicatorHeight),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf("首页" to 0, "钱包" to 1).forEach { (label, index) ->
            val emphasis = (1f - kotlin.math.abs(position - index)).coerceIn(0f, 1f)
            val selected = (position >= 0.5f) == (index == 1)
            val tabInteraction = remember(index) { MutableInteractionSource() }
            Box(
                Modifier.width(tabWidth).fillMaxHeight()
                    .selectable(
                        selected = selected,
                        interactionSource = tabInteraction,
                        indication = null,
                        role = Role.Tab,
                        onClick = {
                            if (!selected) {
                                haptics.medium()
                                onSelect(index)
                            }
                        }
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    label,
                    modifier = Modifier.testTag("primary-title-" + if (index == 0) "home" else "wallet"),
                    style = typography.headlineLarge,
                    fontSize = androidx.compose.ui.unit.lerp(typography.headlineMedium.fontSize, typography.headlineLarge.fontSize, emphasis),
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.lerp(colors.onSurfaceVariant, colors.onSurface, emphasis),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
    Box(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = tabWidth * position + (tabWidth - 28.dp) / 2)
                .size(width = 28.dp, height = 2.dp)
                .testTag("primary-tab-indicator")
                .clip(CircleShape)
                .background(colors.primary)
        )
    }
    }
}

/** 液态玻璃底部导航：悬浮圆角玻璃基底 + 可拖动液滴药丸 + 相邻融合 + 扫码玻璃球。 */
@Composable
internal fun HydrosBottomBar(
    route: String,
    onNavigate: (String) -> Unit,
    onScan: () -> Unit,
    onHeight: (Int) -> Unit,
    messageUnread: Int = 0,
) {
    val haptics = rememberHydrosHaptics()
    val density = LocalDensity.current
    val dark = isDarkScheme()
    val colors = MaterialTheme.colorScheme
    val backdrop = LocalLiquidBackdrop.current
    val liquid = LocalLiquidGlassEnabled.current
    val reduceMotion = LocalFeedbackSettings.current.reduceMotionEnabled
    val barHeight = 52.dp
    val glassShape = RoundedCornerShape(999.dp)
    var capsuleOrigin by remember { mutableStateOf(Offset.Zero) }
    var fabOrigin by remember { mutableStateOf(Offset.Zero) }
    var capsuleWidth by remember { mutableStateOf(0f) }
    val selectedIndex = destinations.indexOfFirst { it.route == route }.coerceAtLeast(0)
    val scope = rememberCoroutineScope()
    // 激活态是一个可以拖动的液滴：位置是连续的浮点数，松手后弹性吸附到最近的标签。
    val droplet = remember { Animatable(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    // 拖动时的连续位置：手势回调不是挂起函数，这里同步记录，画布直接读它。
    var dragTarget by remember { mutableStateOf(selectedIndex.toFloat()) }
    LaunchedEffect(route) {
        if (!dragging) droplet.animateTo(
            selectedIndex.toFloat(),
            if (reduceMotion) snap() else spring(dampingRatio = 0.62f, stiffness = 380f),
        )
    }
    val lift by animateFloatAsState(
        targetValue = if (dragging && !reduceMotion) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.6f, stiffness = 520f),
        label = "glass-lift"
    )
    // 玻璃的透光率随拖动抬升变化：抬起时更透、投影更深，落下时收紧。
    val position = if (dragging) dragTarget else droplet.value
    val tintAlpha = (if (dark) 0.34f else 0.70f) - 0.06f * lift
    val tintTopAlpha = (if (dark) 0.52f else 0.90f) - 0.04f * lift
    val edgeLight = if (dark) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.86f)
    val edgeShade = if (dark) Color.Black.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.10f)
    val brand = colors.primary
    val fabStart = lerp(brand, Color.White, if (dark) 0.16f else 0.34f)
    val fabEnd = lerp(brand, Color(0xFF6C4CF6), 0.45f)
    Box(
        Modifier.fillMaxWidth()
            .onSizeChanged { onHeight(it.height + with(density) { 12.dp.roundToPx() }) }
            .testTag("main-bottom-bar"),
        // 平板等宽屏上胶囊不再拉满整行，改成靠右；手机宽度不足 560dp 时仍铺满。
        contentAlignment = Alignment.CenterEnd
    ) {
        Row(
            // 给手势条/三键导航留出安全距离，避免玻璃胶囊压住系统的滑动条。
            Modifier.widthIn(max = 560.dp).fillMaxWidth().navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.weight(1f).height(barHeight)
                    .graphicsLayer {
                        val s = 1f + 0.012f * lift
                        scaleX = s
                        scaleY = s
                    }
                    .shadow((4 + 3 * lift).dp, glassShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
                    .clip(glassShape)
                    .onSizeChanged { capsuleWidth = it.width.toFloat() }
                    .onGloballyPositioned { capsuleOrigin = it.positionInWindow() }
                    .pointerInput(destinations) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val width = capsuleWidth.coerceAtLeast(1f)
                                dragTarget = (offset.x / width * destinations.size - 0.5f).coerceIn(0f, destinations.lastIndex.toFloat())
                                dragging = true
                                haptics.medium()
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val width = capsuleWidth.coerceAtLeast(1f)
                                val next = (position + amount.x / width * destinations.size)
                                    .coerceIn(0f, destinations.lastIndex.toFloat())
                                dragTarget = next
                            },
                            onDragEnd = {
                                dragging = false
                                val target = dragTarget.roundToInt().coerceIn(0, destinations.lastIndex)
                                scope.launch {
                                    droplet.snapTo(dragTarget)
                                    droplet.animateTo(
                                        target.toFloat(),
                                        if (reduceMotion) snap() else spring(dampingRatio = 0.55f, stiffness = 420f),
                                    )
                                    if (destinations[target].route != route) {
                                        haptics.medium()
                                        onNavigate(destinations[target].route)
                                    }
                                }
                            },
                            onDragCancel = {
                                dragging = false
                                scope.launch {
                                    droplet.animateTo(
                                        selectedIndex.toFloat(),
                                        if (reduceMotion) snap() else spring(dampingRatio = 0.6f, stiffness = 400f),
                                    )
                                }
                            }
                        )
                    }
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    drawLiquidGlassPanel(
                        state = backdrop,
                        origin = capsuleOrigin,
                        shape = capsulePath(w, h),
                        dark = dark,
                        liquid = liquid,
                        magnify = 1.05f
                    )
                    val tabWidth = w / destinations.size
                    val pillWidth = tabWidth * 0.78f
                    val pillHeight = h * 0.74f
                    val pillTop = (h - pillHeight) / 2f
                    val pillLeft = (position + 0.5f) * tabWidth - pillWidth / 2f
                    drawLiquidDroplet(
                        left = pillLeft,
                        top = pillTop,
                        width = pillWidth,
                        height = pillHeight,
                        dark = dark,
                        glow = lift,
                        liquid = liquid,
                        state = backdrop,
                        origin = capsuleOrigin
                    )
                }
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    destinations.forEachIndexed { index, destination ->
                        val pressSource = remember { MutableInteractionSource() }
                        // 药丸掠过时，图标被“挤”成胶囊形状（横向压扁、纵向拉长），并轻微上浮。
                        val prox = if (dragging) {
                            kotlin.math.exp(-((position - index) * (position - index)) / 0.06f)
                        } else if (index == selectedIndex) 1f else 0f
                        val selected = if (dragging) {
                            kotlin.math.abs(position - index) < 0.5f
                        } else index == selectedIndex
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Box(Modifier.fillMaxWidth(0.82f).height(40.dp), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.graphicsLayer {
                                        // 只做极轻微的上浮，图标本身不缩放，避免被拉伸变形。
                                        translationY = -1.dp.toPx() * prox
                                    }
                                ) {
                                    Box {
                                        Glyph(
                                            if (selected) destination.icon else destination.iconOutline,
                                            modifier = Modifier.size(21.dp),
                                            tint = if (selected) colors.primary else colors.onSurfaceVariant,
                                            description = destination.label
                                        )
                                        // 消息标签上的未读小圆点，位置贴着图标右上角。
                                        if (destination.route == "messages" && messageUnread > 0) {
                                            Box(
                                                Modifier
                                                    .align(Alignment.TopEnd)
                                                    .graphicsLayer { translationX = 4.dp.toPx(); translationY = (-2).dp.toPx() }
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(colors.error)
                                            )
                                        }
                                    }
                                    Text(
                                        destination.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) colors.primary else colors.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            Box(
                                Modifier.matchParentSize().clip(RoundedCornerShape(999.dp)).selectable(
                                    selected = index == selectedIndex,
                                    interactionSource = pressSource,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = {
                                        if (route != destination.route) {
                                            haptics.medium()
                                            onNavigate(destination.route)
                                        }
                                    }
                                )
                            )
                        }
                    }
                }
            }
            val fabSource = remember { MutableInteractionSource() }
            val fabPressed by fabSource.collectIsPressedAsState()
            val fabScale by animateFloatAsState(
                targetValue = if (fabPressed && !reduceMotion) 0.93f else 1f,
                animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.55f, stiffness = 900f),
                label = "fab-press"
            )
            Box(
                Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                    }
                    .clickable(
                        interactionSource = fabSource,
                        indication = null
                    ) { haptics.light(); onScan() }
                    .shadow(4.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
                    .clip(CircleShape)
                    .onGloballyPositioned { fabOrigin = it.positionInWindow() }
                    .testTag("scan-action"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawLiquidGlassBallTint(
                        shape = circlePath(center, size.minDimension / 2f),
                        center = center,
                        radius = size.minDimension / 2f,
                        brandStart = fabStart,
                        brandEnd = fabEnd,
                        dark = dark,
                        state = backdrop,
                        origin = fabOrigin
                    )
                }
                Glyph(R.drawable.ic_scan, modifier = Modifier.size(24.dp), tint = Color.White, description = "扫码")
            }
        }
    }
}

/** 底部“充值”标签页的内容：余额 + 套餐 + 支付方式，并处理支付跳转。 */
@Composable
private fun RechargeTab(
    thirdParty: ThirdPartyViewModel,
    context: android.content.Context,
    snackbar: HydrosSnackbarState,
    merchant: String?
) {
    val rechargeFlow = thirdParty.recharge
    val pendingFlow = thirdParty.pendingRechargeOrder
    if (rechargeFlow == null) {
        InlineState("当前无法充值", "未获取到校园网关连接，请稍后重试。", icon = R.drawable.ic_wallet)
        return
    }
    val ui by rechargeFlow.collectAsStateWithLifecycle()
    val pending = pendingFlow?.collectAsStateWithLifecycle()?.value
    RechargeScreen(
        ui = ui,
        pendingOrder = pending,
        onOpen = thirdParty::openRecharge,
        onRefresh = thirdParty::refreshRecharge,
        onSelectPackage = thirdParty::selectRechargePackage,
        onSelectChannel = thirdParty::selectRechargeChannel,
        onSubmit = thirdParty::submitRecharge,
        onConsumeOrder = thirdParty::consumeRechargeOrder,
        onPaymentResult = {},
        merchant = merchant
    )
    LaunchedEffect(pending) {
        val order = pending ?: return@LaunchedEffect
        when (order) {
            is RechargeOrder.Browser -> {
                val opened = runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(order.url)))
                }.isSuccess
                if (!opened) snackbar.show("无法打开支付页面，请检查是否已安装浏览器")
                thirdParty.consumeRechargeOrder()
            }
            is RechargeOrder.Alipay -> {
                val result = launchAlipayOrder(context, order.orderInfo)
                thirdParty.consumeRechargeOrder()
                thirdParty.refreshRechargeBalance()
                snackbar.show(result.label)
            }
        }
    }
}

/** 把相册选中的图片复制到应用私有目录，返回可直接给壁纸渲染使用的绝对路径。 */
private fun storeWallpaperFile(context: android.content.Context, uri: Uri): String? {
    val target = java.io.File(context.filesDir, "wallpaper_src.img")
    val input = context.contentResolver.openInputStream(uri) ?: return null
    return runCatching {
        input.use { source -> target.outputStream().use { output -> source.copyTo(output) } }
        target.absolutePath
    }.getOrNull()
}


