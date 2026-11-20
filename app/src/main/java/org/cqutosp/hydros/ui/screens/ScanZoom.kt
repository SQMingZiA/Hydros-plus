package org.cqutosp.hydros.ui.screens

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/**
 * 扫码页的变焦状态容器。
 *
 * 只保存 UI 侧请求值（1.0x ~ 相机最大变焦），真正的相机变焦由 CameraPreview 通过
 * CameraControl.setZoomRatio 应用。扫码页用双指捏合手势调用 [request] 改变倍率，
 * 相机回报的真实变焦只在 CameraStatus 里用于显示焦距文字。
 */
internal class ScanZoomController {
    var zoom by mutableFloatStateOf(1f)
    var minZoom by mutableFloatStateOf(1f)
    var maxZoom by mutableFloatStateOf(8f)

    /** 用户双指捏合：夹在相机支持范围内并立即反映到 UI。 */
    fun request(value: Float) {
        val upper = maxZoom.coerceAtLeast(minZoom)
        zoom = value.coerceIn(minZoom, upper)
    }

    /** 相机回报的真实变焦（可能来自自动变焦），同步回控件避免“画面变了、滑块没变”。 */
    fun adopt(value: Float) {
        val upper = maxZoom.coerceAtLeast(minZoom)
        val target = value.coerceIn(minZoom, upper)
        if (abs(zoom - target) > 0.02f) zoom = target
    }
}

/** 默认实例让 ScanContent 在没有相机的测试/预览环境里也能渲染完整控制条。 */
internal val LocalScanZoom = compositionLocalOf { ScanZoomController() }
