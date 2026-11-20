package org.cqutosp.hydros.ui.scanner

import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import android.util.SizeF
import android.view.MotionEvent
import androidx.camera.core.*
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class CameraStatus(
    val ready: Boolean = false,
    val hasFlash: Boolean = false,
    val torchOn: Boolean = false,
    val error: String? = null,
    val torchError: String? = null,
    val zoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val focalLength35mm: Int = 0,
)

/** Pre-initializes CameraX hardware provider and ML Kit models in background to eliminate first-launch latency. */
fun preloadScanner(context: Context) {
    try {
        ProcessCameraProvider.getInstance(context)
        val dummy = InputImage.fromBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), 0)
        BarcodeScanning.getClient(qrOptions().build()).process(dummy)
    } catch (_: Throwable) {
        // Safe to ignore in test or headless environments.
    }
}

@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
@Composable
internal fun CameraPreview(
    enabled: Boolean,
    torchRequested: Boolean,
    modifier: Modifier = Modifier,
    scanning: Boolean = true,
    onStatus: (CameraStatus) -> Unit,
    onCodes: (List<String>) -> Unit,
    onFailure: () -> Unit,
    zoomRatio: Float = 1f,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latestCodes by rememberUpdatedState(onCodes)
    val latestStatus by rememberUpdatedState(onStatus)
    val latestFailure by rememberUpdatedState(onFailure)
    val latestScanning by rememberUpdatedState(scanning)
    val view = remember(context) { PreviewView(context).apply {
        // COMPATIBLE 走 TextureView：PERFORMANCE(SurfaceView) 会盖住其上层的 Compose 遮罩/取景框/扫描线。
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
        contentDescription = "相机取景，点击对焦"
    } }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var status by remember { mutableStateOf(CameraStatus()) }
    LaunchedEffect(torchRequested, camera, enabled) {
        val bound = camera
        if (bound != null && enabled && bound.cameraInfo.hasFlashUnit()) {
            val task = bound.cameraControl.enableTorch(torchRequested)
            task.addListener({
                if (camera === bound) {
                    val error = try { task.get(); null } catch (_: Exception) { "手电筒切换失败，请重试" }
                    status = status.copy(torchError = error)
                    latestStatus(status)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }
    // 只同步变焦，不重建相机：滑杆变化时把倍率真正作用到 CameraControl。
    LaunchedEffect(zoomRatio, camera, enabled) {
        val bound = camera
        if (bound != null && enabled) {
            val zoomState = bound.cameraInfo.zoomState.value
            if (zoomState != null) {
                val target = zoomRatio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                if (kotlin.math.abs(zoomState.zoomRatio - target) > 0.02f) {
                    try { bound.cameraControl.setZoomRatio(target) } catch (_: Exception) { }
                }
            }
        }
    }
    DisposableEffect(owner, enabled, view) {
        if (!enabled) {
            status = CameraStatus()
            latestStatus(status)
            onDispose { }
        } else {
            val executor = Executors.newSingleThreadExecutor()
            val main = ContextCompat.getMainExecutor(context)
            val future = ProcessCameraProvider.getInstance(context)
            val resolutionSelector = ResolutionSelector.Builder().setResolutionStrategy(
                ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            ).build()
            val preview = Preview.Builder().setResolutionSelector(resolutionSelector).build().also { it.setSurfaceProvider(view.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            val lock = Any()
            var disposed = false
            var processing = false
            var provider: ProcessCameraProvider? = null
            var decoder: com.google.mlkit.vision.barcode.BarcodeScanner? = null
            var bound: Camera? = null
            var torchObserver: Observer<Int>? = null
            var cameraObserver: Observer<CameraState>? = null
            var zoomObserver: Observer<ZoomState>? = null
            fun publish(value: CameraStatus) { status = value; latestStatus(value) }
            fun focus(x: Float, y: Float) {
                val target = bound ?: return
                val point = view.meteringPointFactory.createPoint(x, y)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                if (target.cameraInfo.isFocusMeteringSupported(action)) target.cameraControl.startFocusAndMetering(action)
            }
            view.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) { focus(event.x, event.y); view.performClick() }
                true
            }
            publish(CameraStatus())
            future.addListener({
                if (!disposed) try {
                    provider = future.get()
                    val target = provider!!.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    bound = target
                    camera = target
                    publish(status.copy(
                        focalLength35mm = focalLengthIn35mm(target.cameraInfo),
                        zoomRatio = target.cameraInfo.zoomState.value?.zoomRatio ?: 1f,
                        minZoomRatio = target.cameraInfo.zoomState.value?.minZoomRatio ?: 1f,
                        maxZoomRatio = target.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f,
                    ))
                    val zoomMax = target.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                    decoder = BarcodeScanning.getClient(qrOptions().setZoomSuggestionOptions(
                        ZoomSuggestionOptions.Builder { ratio ->
                            if (disposed || !status.ready || !latestScanning) false else {
                                target.cameraControl.setZoomRatio(ratio.coerceIn(1f, zoomMax)); true
                            }
                        }.setMaxSupportedZoomRatio(zoomMax).build()).build())
                    torchObserver = Observer<Int> { value ->
                        if (!disposed) publish(status.copy(torchOn = value == TorchState.ON))
                    }.also { target.cameraInfo.torchState.observe(owner, it) }
                    cameraObserver = Observer<CameraState> { value ->
                        if (!disposed) publish(status.copy(ready = value.type == CameraState.Type.OPEN,
                            hasFlash = target.cameraInfo.hasFlashUnit(),
                            error = if (value.error != null) "相机无法启动，请检查权限或设备相机" else null))
                    }.also { target.cameraInfo.cameraState.observe(owner, it) }
                    zoomObserver = Observer<ZoomState> { zoom ->
                        if (!disposed) publish(status.copy(
                            zoomRatio = zoom.zoomRatio,
                            minZoomRatio = zoom.minZoomRatio,
                            maxZoomRatio = zoom.maxZoomRatio,
                        ))
                    }.also { target.cameraInfo.zoomState.observe(owner, it) }
                    analysis.setAnalyzer(executor) { image ->
                        synchronized(lock) {
                            val media = image.image
                            val scanner = decoder
                            if (disposed || !latestScanning || processing || media == null || scanner == null) image.close()
                            else {
                                processing = true
                                try {
                                    scanner.process(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees))
                                        .addOnSuccessListener { codes ->
                                            if (!disposed && latestScanning) latestCodes(codes.mapNotNull { it.rawValue })
                                        }
                                        .addOnFailureListener { if (!disposed && latestScanning) latestFailure() }
                                        .addOnCompleteListener {
                                            synchronized(lock) {
                                                image.close()
                                                processing = false
                                                if (disposed) scanner.close()
                                            }
                                        }
                                } catch (_: Exception) {
                                    image.close(); processing = false
                                    main.execute { if (!disposed && latestScanning) latestFailure() }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    publish(CameraStatus(error = "相机无法启动，请检查权限或设备相机"))
                }
            }, main)
            onDispose {
                synchronized(lock) {
                    disposed = true
                    analysis.clearAnalyzer()
                    if (!processing) decoder?.close()
                }
                view.setOnTouchListener(null)
                torchObserver?.let { bound?.cameraInfo?.torchState?.removeObserver(it) }
                cameraObserver?.let { bound?.cameraInfo?.cameraState?.removeObserver(it) }
                zoomObserver?.let { bound?.cameraInfo?.zoomState?.removeObserver(it) }
                bound?.cameraControl?.enableTorch(false)
                provider?.unbind(preview, analysis)
                camera = null
                executor.shutdown()
            }
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
}

/**
 * 相机 35mm 等效焦距（mm）：取不到物理焦距或传感器尺寸时返回 0，界面退回只显示变焦倍率。
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
private fun focalLengthIn35mm(cameraInfo: CameraInfo): Int {
    return try {
        val c2 = Camera2CameraInfo.from(cameraInfo)
        val focal = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: 0f
        val physical: SizeF? = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        if (focal <= 0f || physical == null || physical.width <= 0f || physical.height <= 0f) 0
        else {
            val w: Float = physical.width
            val h: Float = physical.height
            (focal * 43.266f / sqrt(w * w + h * h)).roundToInt()
        }
    } catch (_: Throwable) {
        0
    }
}

