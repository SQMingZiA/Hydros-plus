package org.cqutosp.hydros.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.cqutosp.hydros.R
import org.cqutosp.hydros.data.feedback.*
import org.cqutosp.hydros.ui.components.ChoiceDialog
import org.cqutosp.hydros.ui.components.Glyph
import org.cqutosp.hydros.ui.components.hydrosPressable
import org.cqutosp.hydros.ui.components.LocalHydrosSnackbar
import org.cqutosp.hydros.ui.components.SnackbarAction
import org.cqutosp.hydros.ui.components.SnackbarMessage
import org.cqutosp.hydros.ui.components.rememberHydrosHaptics
import org.cqutosp.hydros.ui.scanner.*
import kotlinx.coroutines.CancellationException
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ScanScreen(onResolve: suspend (String, Int?) -> String?, enabled: Boolean = true,
    queryProgress: String? = null, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val session = remember { ScanSession() }
    val snackbar = LocalHydrosSnackbar.current
    val state by session.state.collectAsState()
    val resolve by rememberUpdatedState(onResolve)
    val select by rememberUpdatedState(onSelect)
    fun cameraGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var permission by remember { mutableStateOf(cameraGranted()) }
    var permissionDenied by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var torch by remember { mutableStateOf(false) }
    var cameraStatus by remember { mutableStateOf(CameraStatus()) }
    var cameraRetryKey by remember { mutableIntStateOf(0) }
    var photo by remember { mutableStateOf<Pair<Long, Uri>?>(null) }
    val zoom = remember { ScanZoomController() }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = it
        permissionDenied = !it
        if (!it) snackbar.show("未获得相机权限，可从相册选择二维码", error = true)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val token = session.picked(uri != null)
        photo = if (token != null && uri != null) token to uri else null
    }
    LaunchedEffect(enabled) { if (enabled && !permission) permissionRequest.launch(Manifest.permission.CAMERA) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { active = true; permission = cameraGranted() }
            if (event == Lifecycle.Event.ON_PAUSE) { active = false; torch = false; session.pauseCamera() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(enabled) {
        if (!enabled) { torch = false; photo = null; session.pauseCamera() }
    }
    LaunchedEffect(state.phase) { if (state.phase != ScanPhase.SCANNING) torch = false }
    LaunchedEffect(photo, enabled) {
        if (!enabled) return@LaunchedEffect
        val (token, uri) = photo ?: return@LaunchedEffect
        try { session.codes(token, decodeQrPhoto(context, uri), photo = true) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { session.fail(token, "图片无法读取或识别，请选择其他图片") }
        finally { photo = null }
    }
    LaunchedEffect(state.phase, state.generation, enabled) {
        if (enabled && state.phase == ScanPhase.RESOLVING) {
            val token = state.generation
            try { session.resolved(token, resolve(state.mac!!, null)) }
            catch (e: CancellationException) { throw e }
            catch (error: Exception) { session.fail(token, failureFeedback(error, FailureOperation.SCAN, FailureStage.DEVICE_INFO).text) }
        }
    }
    var navigatedGeneration by remember { mutableStateOf<Long?>(null) }
    val haptics = rememberHydrosHaptics()
    LaunchedEffect(state.deviceId, state.generation, active, enabled) {
        if (enabled && active && state.phase == ScanPhase.DONE && navigatedGeneration != state.generation) {
            navigatedGeneration = state.generation
            haptics.success()
            select(state.deviceId!!)
        }
    }
    // 只同步相机变焦范围；不再把相机回报的倍率回写到 zoom（会和 setZoomRatio 互相追值，导致倍率抖动/闪烁）。
    LaunchedEffect(cameraStatus.ready, cameraStatus.minZoomRatio, cameraStatus.maxZoomRatio) {
        zoom.minZoom = maxOf(1f, cameraStatus.minZoomRatio)
        if (cameraStatus.maxZoomRatio > zoom.minZoom) zoom.maxZoom = cameraStatus.maxZoomRatio
    }
    CompositionLocalProvider(LocalScanZoom provides zoom) {
        ScanContent(
            state = state,
            cameraGranted = permission,
            cameraStatus = cameraStatus,
            onPermission = { permissionRequest.launch(Manifest.permission.CAMERA) },
            onSettings = if (permissionDenied) ({
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
            }) else null,
            onTorch = { torch = !cameraStatus.torchOn },
            onPhoto = {
                torch = false
                session.pick()
                try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                catch (_: ActivityNotFoundException) { session.picked(false); snackbar.show("无法打开相册，请稍后重试", error = true) }
            },
            onRetry = {
                photo = null
                torch = false
                if (cameraStatus.error != null) {
                    cameraStatus = CameraStatus()
                    cameraRetryKey++
                }
                session.retryQuery()
            },
            onRescan = { session.retry() },
            queryProgress = queryProgress,
            onChoose = session::choose,
        ) { modifier ->
            key(cameraRetryKey) {
                val currentGeneration by rememberUpdatedState(state.generation)
                CameraPreview(
                    enabled = enabled && permission,
                    scanning = active && state.phase == ScanPhase.SCANNING,
                    torchRequested = torch,
                    modifier = modifier,
                    onStatus = { cameraStatus = it; if (it.torchError != null) torch = it.torchOn },
                    onCodes = { session.codes(currentGeneration, it) },
                    onFailure = { session.fail(currentGeneration, "相机识别失败，请重新扫描") },
                    zoomRatio = zoom.zoom,
                )
            }
        }
    }
}

/** 沉浸式整屏扫码：相机铺满，直角取景框浮在画面上，双指可捏合缩放，底部相册 / 手电筒圆钮。 */
@Composable
internal fun ScanContent(
    state: ScanState,
    cameraGranted: Boolean,
    cameraStatus: CameraStatus,
    onPermission: () -> Unit,
    onSettings: (() -> Unit)? = null,
    onTorch: () -> Unit,
    onPhoto: () -> Unit,
    onRetry: () -> Unit,
    onChoose: (String) -> Unit,
    onRescan: (() -> Unit)? = null,
    queryProgress: String? = null,
    preview: @Composable (Modifier) -> Unit,
) {
    val scanning = state.phase == ScanPhase.SCANNING
    val busy = state.phase in setOf(ScanPhase.PICKING, ScanPhase.DECODING, ScanPhase.RESOLVING, ScanPhase.DONE)
    val zoom = LocalScanZoom.current
    SnackbarMessage(state.message.takeIf { state.phase != ScanPhase.ERROR }, eventKey = state.generation to state.message,
        action = if (state.phase == ScanPhase.ERROR) SnackbarAction("重新扫描", onRetry) else null)
    SnackbarMessage(cameraStatus.error, eventKey = state.generation to cameraStatus.error, action = SnackbarAction("重试", onRetry))
    SnackbarMessage(cameraStatus.torchError)
    val torchReason = when {
        !cameraGranted -> "允许相机权限后可开启手电筒"
        cameraStatus.error != null -> "相机不可用，仍可从相册选择"
        !scanning -> "请先返回扫码状态，再开启手电筒"
        !cameraStatus.ready -> "相机正在启动，请稍候"
        !cameraStatus.hasFlash -> "当前相机不支持手电筒"
        else -> null
    }
    val cameraLive = cameraGranted && cameraStatus.error == null
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    // 焦距胶囊：先把倍率量化到 0.1x 再算文字（原来按相机高频回报的原始倍率换算 mm，会逐帧跳变、看起来闪烁）。
    val focalMm = cameraStatus.focalLength35mm
    val zoomTenths = (cameraStatus.zoomRatio.coerceAtLeast(1f) * 10f).roundToInt().coerceAtLeast(10)
    val focusText = remember(zoomTenths, focalMm) {
        val ratioLabel = (zoomTenths / 10).toString() + "." + (zoomTenths % 10) + "x"
        if (focalMm > 0) (focalMm.toFloat() * zoomTenths / 10f).roundToInt().toString() + "mm · " + ratioLabel
        else ratioLabel
    }
    val latestRatio = rememberUpdatedState(cameraStatus.zoomRatio.coerceAtLeast(1f))
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (cameraGranted) preview(Modifier.fillMaxSize())
        if (cameraLive) {
            if (busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            ScanOverlay(Modifier.fillMaxSize())
            // 双指捏合缩放：只消费两指手势，单指事件不消费，继续交给相机预览做点击对焦。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(cameraLive, scanning) {
                        if (!cameraLive || !scanning) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var baseDistance = 0f
                            var baseZoom = 1f
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    val distance = (pressed[0].position - pressed[1].position).getDistance()
                                    if (distance > 0f) {
                                        if (baseDistance <= 0f) {
                                            baseDistance = distance
                                            baseZoom = latestRatio.value
                                        } else {
                                            zoom.request(baseZoom * (distance / baseDistance))
                                            pressed.forEach { it.consume() }
                                        }
                                    }
                                } else baseDistance = 0f
                            }
                        }
                    }
            )
        }
        if (!cameraLive) {
            ScanCameraNotice(
                granted = cameraGranted,
                onPermission = onPermission,
                onSettings = onSettings,
                onRetry = onRetry,
            )
        }
        // 顶部：返回 / 标题 / 等效焦距
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScanGlyphButton(
                iconRes = R.drawable.ic_back,
                description = "返回",
                modifier = Modifier.testTag("scan-back"),
                onClick = { backDispatcher?.onBackPressed() },
            )
            Text(
                text = "扫一扫",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ScanFocalPill(text = focusText)
        }
        // 底部：状态 / 提示 / 相册 · 手电筒
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScanStatusPanel(
                state = state,
                queryProgress = queryProgress,
                cameraLive = cameraLive,
                scanning = scanning,
                ready = cameraStatus.ready,
                onRetry = onRetry,
                onRescan = onRescan,
            )
            Text(
                text = "将设备上的二维码放入框内，即可自动连接",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ScanRoundEntry(
                    label = "从相册选择",
                    iconRes = R.drawable.ic_photo,
                    enabled = !busy,
                    modifier = Modifier.width(76.dp),
                    onClick = onPhoto,
                )

                ScanTorchEntry(
                    on = cameraStatus.torchOn,
                    disabledReason = torchReason,
                    onToggle = onTorch,
                    modifier = Modifier.width(76.dp),
                )
            }
        }
    }
    if (state.phase == ScanPhase.CHOOSING) ChoiceDialog(
        title = "选择用水设备",
        options = state.candidates,
        optionLabel = { "设备 $it" },
        onSelect = onChoose,
        onDismiss = onRetry,
        dismissLabel = "重新扫描"
    )
}

/** 底部状态面板：识别 / 查询 / 出错时的说明与重试入口。 */
@Composable
private fun ScanStatusPanel(
    state: ScanState,
    queryProgress: String?,
    cameraLive: Boolean,
    scanning: Boolean,
    ready: Boolean,
    onRetry: () -> Unit,
    onRescan: (() -> Unit)?,
) {
    val phase = state.phase
    val error = phase == ScanPhase.ERROR
    val loading = phase == ScanPhase.PICKING || phase == ScanPhase.DECODING ||
        phase == ScanPhase.RESOLVING || phase == ScanPhase.DONE ||
        (phase == ScanPhase.SCANNING && cameraLive && scanning && !ready)
    val title: String? = when (phase) {
        ScanPhase.PICKING -> "正在选择图片"
        ScanPhase.DECODING -> "正在识别图片"
        ScanPhase.RESOLVING, ScanPhase.DONE -> "正在查询设备"
        ScanPhase.ERROR -> if (state.mac != null) "设备信息查询未完成" else "识别未完成"
        else -> if (cameraLive && scanning && !ready) "正在启动相机" else null
    }
    val description: String? = when (phase) {
        ScanPhase.RESOLVING, ScanPhase.DONE -> queryProgress
        ScanPhase.ERROR -> state.message
        else -> null
    }
    if (title == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = Color.White,
                    strokeWidth = 1.5.dp,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
        }
        if (description != null && description.isNotBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
            )
        }
        if (error) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScanPillButton(
                    label = if (state.mac != null) "重新查询" else "重新扫描",
                    filled = true,
                    onClick = onRetry,
                )
                if (state.mac != null && onRescan != null) {
                    ScanPillButton(label = "重新扫描", onClick = onRescan)
                }
            }
        }
    }
}

/** 深色底上的小胶囊按钮：实心白 / 半透明白。 */
@Composable
private fun ScanPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Box(
        modifier = modifier
            .hydrosPressable(role = Role.Button, onClick = onClick)
            .clip(RoundedCornerShape(999.dp))
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (filled) Color.Black else Color.White,
        )
    }
}


@Composable
private fun ScanFocalPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .widthIn(min = 84.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag("scan-focal"),
        contentAlignment = Alignment.Center,
    ) {
        // 只渲染这一个 Text：字符串由外面 remember 好，倍率变化时不会出现两个节点交替。
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}

/** 顶部圆形图标按钮：半透明底 + 白色图标，压在相机画面上。 */
@Composable
private fun ScanGlyphButton(
    iconRes: Int,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val haptics = rememberHydrosHaptics()
    Box(
        modifier = modifier
            .size(40.dp)
            .hydrosPressable(
                role = Role.Button,
                onClickLabel = description,
                onClick = { haptics.light(); onClick() },
            )
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(iconRes, modifier = Modifier.size(22.dp), tint = Color.White, description = description)
    }
}

/** 手电筒开关（底部唯一入口，scan-torch）：开启时白色实心，关闭时半透明。 */
@Composable
private fun ScanTorchEntry(
    on: Boolean,
    disabledReason: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = LocalHydrosSnackbar.current
    val haptics = rememberHydrosHaptics()
    Column(
        modifier = modifier
            .hydrosPressable(
                role = Role.Button,
                onClick = {
                    if (disabledReason != null) snackbar.show(disabledReason)
                    else { haptics.light(); onToggle() }
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = if (on) "关闭手电筒" else "开启手电筒"
                stateDescription = if (on) "已开启" else "已关闭"
            }
            .testTag("scan-torch")
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (on) Color.White else Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(
                res = R.drawable.ic_flashlight,
                modifier = Modifier.size(24.dp),
                tint = if (on) Color.Black else Color.White,
            )
        }
        Text(
            text = if (on) "关闭手电筒" else "开启手电筒",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 相册入口：圆形图标 + 下方文字。 */
@Composable
private fun ScanRoundEntry(
    label: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlight: Boolean = false,
    disabledReason: String? = null,
    stateText: String? = null,
    onClick: () -> Unit,
) {
    val snackbar = LocalHydrosSnackbar.current
    val haptics = rememberHydrosHaptics()
    Column(
        modifier = modifier
            .hydrosPressable(
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    if (disabledReason != null) snackbar.show(disabledReason)
                    else { haptics.light(); onClick() }
                },
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                when {
                    disabledReason != null -> stateDescription = disabledReason
                    stateText != null -> stateDescription = stateText
                }
                if (!enabled) disabled()
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (highlight) Color.White else Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(
                res = iconRes,
                modifier = Modifier.size(24.dp),
                tint = when {
                    highlight -> Color.Black
                    enabled -> Color.White
                    else -> Color.White.copy(alpha = 0.35f)
                },
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 相机不可用：权限申请 / 重试 / 前往设置（深色底，与整屏一致）。 */
@Composable
private fun ScanCameraNotice(
    granted: Boolean,
    onPermission: () -> Unit,
    onSettings: (() -> Unit)?,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Glyph(
            res = R.drawable.ic_scan,
            modifier = Modifier.size(30.dp),
            tint = Color.White.copy(alpha = 0.9f),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (granted) "相机暂时不可用" else "需要相机权限",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (granted) "仍可从相册选择二维码" else "授权后即可扫描设备上的二维码",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .hydrosPressable(role = Role.Button, onClick = if (granted) onRetry else onPermission)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text(
                    text = if (granted) "重试相机" else "允许相机",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Black,
                )
            }
            if (!granted && onSettings != null) {
                Box(
                    modifier = Modifier
                        .hydrosPressable(role = Role.Button, onClick = onSettings)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "前往设置",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * 取景覆盖层：四周压暗、直角四角角标与上下扫描光带。
 * 不画整圈边框，取景窗口为屏幕宽度的 76%、垂直居中偏上。
 */
@Composable
private fun ScanOverlay(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "scan-line")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(2200, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "scan-line-progress",
    )
    Canvas(modifier) {
        val side = min(size.width * 0.76f, size.height * 0.54f)
        val left = (size.width - side) / 2f
        val top = size.height * 0.19f
        val right = left + side
        val bottom = top + side
        // 四周压暗
        val shade = Color.Black.copy(alpha = 0.58f)
        drawRect(shade, Offset(0f, 0f), Size(size.width, top))
        drawRect(shade, Offset(0f, bottom), Size(size.width, size.height - bottom))
        drawRect(shade, Offset(0f, top), Size(left, side))
        drawRect(shade, Offset(right, top), Size(size.width - right, side))

        // 四角直角角标（只有角标，没有整圈边框线）
        val arm = side * 0.14f
        val stroke = 3.dp.toPx()
        val cornerColor = Color.White.copy(alpha = 0.9f)
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawPath(
                Path().apply {
                    moveTo(x, y + dy * arm)
                    lineTo(x, y)
                    lineTo(x + dx * arm, y)
                },
                cornerColor,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        corner(left, top, 1f, 1f)
        corner(right, top, -1f, 1f)
        corner(right, bottom, -1f, -1f)
        corner(left, bottom, 1f, -1f)

        // 扫描光带：白色，避免整条蓝线
        val beamY = top + side * progress
        val half = 3.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent),
                startY = beamY - half,
                endY = beamY + half,
            ),
            topLeft = Offset(left + 6.dp.toPx(), beamY - half),
            size = Size(side - 12.dp.toPx(), half * 2),
        )
    }
}