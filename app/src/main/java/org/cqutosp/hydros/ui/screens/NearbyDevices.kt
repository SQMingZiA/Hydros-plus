package org.cqutosp.hydros.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import org.cqutosp.hydros.data.feedback.*
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.cqutosp.hydros.ui.components.*
import org.cqutosp.hydros.ui.state.DeviceUi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.cqutosp.hydros.data.repository.NearbyDiscoveryState

@Composable
fun NearbyDevices(
    state: NearbyDiscoveryState,
    devices: List<DeviceUi>,
    onScan: () -> Unit,
    onStop: () -> Unit,
    onSelect: (String) -> Unit,
    remarks: Map<String, String> = emptyMap(),
    demoMode: Boolean = false,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val stop by rememberUpdatedState(onStop)
    val scan by rememberUpdatedState(onScan)
    var pendingScan by remember { mutableStateOf(false) }
    val wanted = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    var permissions by remember { mutableStateOf(wanted.associateWith(::granted)) }
    val snackbar = LocalHydrosSnackbar.current
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissions = wanted.associateWith(::granted)
        if (permissions.values.all { it } && pendingScan) {
            pendingScan = false
            scan()
        } else if (permissions.values.any { !it }) snackbar.show("请允许附近设备权限，授权后将继续扫描", error = true)
    }
    LaunchedEffect(demoMode) { if (demoMode) scan() }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = wanted.associateWith(::granted)
                if (pendingScan && permissions.values.all { it }) {
                    pendingScan = false
                    scan()
                }
            }
            if (event == Lifecycle.Event.ON_STOP) stop()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            stop()
        }
    }
    fun startScan() {
        if (demoMode) {
            // 演示模式不需要蓝牙权限，直接返回模拟设备。
            scan()
            return
        }
        permissions = wanted.associateWith(::granted)
        if (permissions.values.any { !it }) {
            pendingScan = true
            request.launch(wanted.toTypedArray())
        } else { pendingScan = false; scan() }
    }

    SectionHeader("附近设备")
    Column {
        // 分组标题下方的平铺内容，与首页其它分组保持同样的发丝线密度。
        if (!demoMode && permissions.values.any { !it }) {
            InlineState("需要附近设备权限")
            SecondaryAction("允许蓝牙", onClick = { startScan() }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            TextAction("前往设置", onClick = { pendingScan = true; context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(("package:" + context.packageName).toUri())
                    ) })
        } else {
            if (!state.searching && state.error != null) {
                val text = when {
                    state.scanFailed -> (state.error as? org.cqutosp.hydros.data.ble.NearbyScanFailure)?.message
                        ?: failureFeedback(state.error, FailureOperation.SCAN, FailureStage.BLE_CONNECT).text
                    state.devices.isNotEmpty() -> "部分设备信息暂未加载，已找到的设备仍可选择"
                    else -> "已发现设备，但设备信息查询失败。" + failureFeedback(state.error, FailureOperation.SCAN).text
                }
                InlineState("扫描未全部完成", text)
            }
            if (!state.searching && (state.error as? AppFailure)?.reason == FailureReason.BLUETOOTH_OFF) {
                TextAction("开启蓝牙并继续扫描", onClick = {
                    pendingScan = true
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                })
            }
            val found = state.devices.map { it.id }.toSet()
            val nearby = devices.filter { it.id in found }
            nearby.forEachIndexed { index, device ->
                if (index > 0) SubtleDivider(horizontalPadding = 0.dp)
                DeviceRow(device, remarks) { onSelect(device.id) }
            }
            if (state.searching) {
                InlineState(
                    title = "正在查找附近设备…",
                    loading = true,
                    modifier = Modifier.padding(vertical = if (nearby.isEmpty()) 12.dp else 0.dp)
                )
            } else if (state.hasSearched && nearby.isEmpty() && state.error == null) {
                InlineState(
                    title = "未发现附近设备",
                    icon = null,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            if (!state.searching) {
                TextAction(
                    label = if (state.hasSearched) "重新扫描" else "扫描附近设备",
                    onClick = { startScan() },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)
                )
            }
        }
    }
}
