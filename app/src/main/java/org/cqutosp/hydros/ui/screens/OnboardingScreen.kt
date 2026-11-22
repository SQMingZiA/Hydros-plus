package org.cqutosp.hydros.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import org.cqutosp.hydros.ui.theme.LocalFeedbackSettings
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.cqutosp.hydros.R
import org.cqutosp.hydros.ui.components.*

private const val STEP_COUNT = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onTerms: () -> Unit = {},
    onPrivacy: () -> Unit = {},
    onPlusTerms: () -> Unit = {},
    initialStep: Int = 0,
    modifier: Modifier = Modifier
) {
    var currentStep by rememberSaveable { mutableIntStateOf(initialStep.coerceIn(0, STEP_COUNT - 1)) }
    var agreedToLegal by rememberSaveable { mutableStateOf(false) }

    val reduceMotion = LocalFeedbackSettings.current.reduceMotionEnabled
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isPreview = LocalInspectionMode.current

    // Permissions state
    var cameraGranted by remember { mutableStateOf(isPreview || checkCameraPermission(context)) }
    var bluetoothGranted by remember { mutableStateOf(isPreview || checkBluetoothPermission(context)) }
    var notificationGranted by remember { mutableStateOf(isPreview || checkNotificationPermission(context)) }

    fun refreshPermissions() {
        if (!isPreview) {
            cameraGranted = checkCameraPermission(context)
            bluetoothGranted = checkBluetoothPermission(context)
            notificationGranted = checkNotificationPermission(context)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
        refreshPermissions()
    }

    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        bluetoothGranted = checkBluetoothPermission(context)
        refreshPermissions()
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationGranted = it
        refreshPermissions()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // 底部操作条：白底、顶部 0.5dp 描边、轻阴影（非玻璃）。
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StepIndicator(STEP_COUNT, currentStep)
                    ActionRow {
                        if (currentStep > 0) {
                            TextAction(
                                label = "上一步",
                                onClick = { if (currentStep > 0) currentStep-- },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        when (currentStep) {
                            0 -> {
                                PrimaryAction(
                                    label = "下一步",
                                    onClick = { currentStep = 1 },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            1 -> {
                                PrimaryAction(
                                    label = "我已阅读并继续",
                                    enabled = agreedToLegal,
                                    onClick = {
                                        if (agreedToLegal) currentStep = 2
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            2 -> {
                                val allGranted = cameraGranted && bluetoothGranted && notificationGranted
                                PrimaryAction(
                                    label = "下一步",
                                    enabled = allGranted,
                                    onClick = {
                                        if (allGranted) currentStep = 3
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            3 -> {
                                PrimaryAction(
                                    label = "开始使用",
                                    onClick = { onComplete() },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (reduceMotion) {
                    EnterTransition.None.togetherWith(ExitTransition.None)
                } else {
                    fadeIn(tween(250)).togetherWith(fadeOut(tween(250)))
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "OnboardingStepTransition"
        ) { step ->
            when (step) {
                0 -> WelcomeStep()
                1 -> LegalStep(
                    agreed = agreedToLegal,
                    onToggleAgreed = { agreedToLegal = it },
                    onTerms = onTerms,
                    onPrivacy = onPrivacy,
                    onPlusTerms = onPlusTerms
                )
                2 -> PermissionsStep(
                    cameraGranted = cameraGranted,
                    onRequestCamera = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                    bluetoothGranted = bluetoothGranted,
                    onRequestBluetooth = {
                        bluetoothLauncher.launch(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                            )
                        )
                    },
                    notificationGranted = notificationGranted,
                    onRequestNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
                3 -> GetStartedStep()
            }
        }
    }
}

/** 页面级标题：22sp SemiBold，左对齐。 */
@Composable
private fun OnboardingTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** 平铺分组：白底、无卡片容器、无额外左右 padding，行间由 0.5dp 发丝线分隔。 */
@Composable
private fun OnboardingCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth(), content = content)
}

@Composable
private fun WelcomeStep() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "欢迎使用 Hydros+",
                fontSize = 30.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "趣智校园轻量三方客户端",
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LegalStep(
    agreed: Boolean,
    onToggleAgreed: (Boolean) -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    onPlusTerms: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            OnboardingTitle("阅读相关条款")

            Text(
                text = "Hydros+ 是基于 Hydros 及 muguang、quzhi-campus-third-party 等开源项目改进的第三方客户端，仅用于开发学习与技术研究，与官方及其运营方无关。请先阅读以下条款。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OnboardingCard {
                MineRow(
                    title = "Hydros 服务协议",
                    icon = R.drawable.ic_gavel,
                    tone = IconTone.PRIMARY,
                    onClick = onTerms
                )
                SubtleDivider(horizontalPadding = 0.dp)
                MineRow(
                    title = "Hydros 隐私政策",
                    icon = R.drawable.ic_shield,
                    tone = IconTone.SECONDARY,
                    onClick = onPrivacy
                )
                SubtleDivider(horizontalPadding = 0.dp)
                MineRow(
                    title = "Hydros+ 服务协议",
                    supporting = "补充声明与免责条款，建议优先阅读",
                    icon = R.drawable.ic_info,
                    tone = IconTone.TERTIARY,
                    onClick = onPlusTerms
                )
            }

            CheckboxRow(
                text = "我已仔细阅读并同意《Hydros 服务协议》《Hydros 隐私政策》与《Hydros+ 服务协议》",
                checked = agreed,
                onCheckedChange = onToggleAgreed
            )
        }
    }
}

@Composable
private fun PermissionsStep(
    cameraGranted: Boolean,
    onRequestCamera: () -> Unit,
    bluetoothGranted: Boolean,
    onRequestBluetooth: () -> Unit,
    notificationGranted: Boolean,
    onRequestNotification: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            OnboardingTitle("应用权限清单")

            OnboardingCard {
                PermissionRow(
                    title = "相机",
                    supporting = "扫描设备二维码",
                    icon = R.drawable.ic_scan,
                    tone = IconTone.PRIMARY,
                    isGranted = cameraGranted,
                    onRequest = onRequestCamera
                )
                SubtleDivider(horizontalPadding = 0.dp)
                PermissionRow(
                    title = "附近设备（蓝牙）",
                    supporting = "搜索连接水控设备",
                    icon = R.drawable.ic_bluetooth,
                    tone = IconTone.SECONDARY,
                    isGranted = bluetoothGranted,
                    onRequest = onRequestBluetooth
                )
                SubtleDivider(horizontalPadding = 0.dp)
                PermissionRow(
                    title = "通知",
                    supporting = "用水状态与账单通知",
                    icon = R.drawable.ic_notifications,
                    tone = IconTone.TERTIARY,
                    isGranted = notificationGranted,
                    onRequest = onRequestNotification
                )
            }
        }
    }
}

@Composable
private fun GetStartedStep() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            ) {
                Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                    Glyph(
                        res = R.drawable.ic_check,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = "开始使用 Hydros+",
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun checkCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun checkBluetoothPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

private fun checkNotificationPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
