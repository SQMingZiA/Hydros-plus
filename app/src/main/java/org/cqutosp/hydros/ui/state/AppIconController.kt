package org.cqutosp.hydros.ui.state

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cqutosp.hydros.data.storage.AppIconKeys
import org.cqutosp.hydros.data.storage.AppIconStore
import java.io.File

/** 三个桌面入口别名的全名，必须与 AndroidManifest.xml 中的 activity-alias 一一对应。 */
object AppIconAliases {
    const val DEFAULT = "org.cqutosp.hydros.DefaultIcon"
    const val QZXY = "org.cqutosp.hydros.QzxyIcon"
    const val CUSTOM = "org.cqutosp.hydros.CustomIcon"

    val all: List<String> = listOf(DEFAULT, QZXY, CUSTOM)

    /**
     * 某个图标键对应的桌面上真正启用的别名。
     *
     * 自定义图标不能替换已安装的 launcher 资源，改为用保存的图片创建一个可固定到桌面的快捷方式，
     * 因此这里仍然回到默认别名。
     */
    fun forKey(key: String): String = when (key) {
        AppIconKeys.QZXY -> QZXY
        else -> DEFAULT
    }
}

/**
 * 桌面图标切换：通过 PackageManager.setComponentEnabledSetting 启用目标 activity-alias 并禁用其余别名。
 *
 * 这里只碰组件状态与本地偏好，不重启进程（DONT_KILL_APP），避免正在进行的业务被中断。
 */
object AppIconController {
    const val CUSTOM_FILE_NAME = "app_icon_custom.png"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 立即应用图标键：切换别名 enabled 状态，并异步落盘。 */
    fun apply(context: Context, key: String) {
        val app = context.applicationContext
        val normalized = AppIconKeys.normalize(key)
        val target = AppIconAliases.forKey(normalized)
        // 先启用目标别名，再禁用其余别名：中途异常也不会出现“没有任何桌面入口”的状态。
        setEnabled(app, target, true)
        AppIconAliases.all.forEach { alias ->
            if (alias != target) {
                setEnabled(app, alias, false)
            }
        }
        scope.launch {
            runCatching { AppIconStore(app).icon(normalized) }
        }
    }

    /** 读取当前生效的图标键：先看别名状态，再回落到本地偏好。 */
    fun currentAppIcon(context: Context): String {
        val app = context.applicationContext
        val packageManager = app.packageManager
        return when {
            isEnabled(packageManager, app, AppIconAliases.QZXY) -> AppIconKeys.QZXY
            isEnabled(packageManager, app, AppIconAliases.CUSTOM) -> AppIconKeys.CUSTOM
            else -> AppIconStore(app).cachedIcon()
        }
    }

    /** 把相册里的图片裁成 512x512 的 PNG 存到 filesDir，返回文件绝对路径；失败返回 null。 */
    suspend fun saveCustomIcon(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val bitmap = decodeSquare(app, uri, CUSTOM_ICON_SIZE)
        if (bitmap == null) {
            null
        } else {
            try {
                val target = File(app.filesDir, CUSTOM_FILE_NAME)
                target.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                val path = target.absolutePath
                runCatching { AppIconStore(app).customPath(path) }
                path
            } catch (error: Exception) {
                null
            } finally {
                bitmap.recycle()
            }
        }
    }

    /** 删除已保存的自定义图标文件与记录。 */
    suspend fun clearCustomIcon(context: Context) {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            runCatching { File(app.filesDir, CUSTOM_FILE_NAME).delete() }
            runCatching { AppIconStore(app).customPath(null) }
        }
    }

    /** 是否已经保存过自定义图标文件（只判断文件是否存在，不读偏好）。 */
    fun hasCustomIcon(context: Context): Boolean =
        File(context.applicationContext.filesDir, CUSTOM_FILE_NAME).exists()

    /** 读取已保存的自定义图标位图，供界面预览和使用在桌面快捷方式上。 */
    fun customIconBitmap(context: Context): Bitmap? = runCatching {
        val file = File(context.applicationContext.filesDir, CUSTOM_FILE_NAME)
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }.getOrNull()

    /**
     * 用自定义图片请求在桌面固定一个快捷方式。
     *
     * 系统不允许第三方应用在安装后改写自己的启动图标资源，因此用带自定义图片的快捷方式实现
     * 同一效果；返回 false 表示当前桌面不支持固定快捷方式。
     */
    fun requestPinnedCustomIcon(context: Context, label: String): Boolean {
        val bitmap = customIconBitmap(context) ?: return false
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return false
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(context.applicationContext, "hydros-custom-icon")
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(androidx.core.graphics.drawable.IconCompat.createWithAdaptiveBitmap(bitmap))
            .setIntent(launch)
            .build()
        return runCatching {
            androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(context.applicationContext, shortcut, null)
        }.getOrDefault(false)
    }

    private fun isEnabled(packageManager: PackageManager, context: Context, alias: String): Boolean =
        packageManager.getComponentEnabledSetting(ComponentName(context.packageName, alias)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    private fun setEnabled(context: Context, alias: String, enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, alias),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun decodeSquare(context: Context, uri: Uri, side: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { resolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input, null, bounds) } }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= side) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = runCatching {
            resolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input, null, options) }
        }.getOrNull() ?: return null
        val square = centerSquare(decoded)
        return if (square.width == side && square.height == side) {
            square
        } else {
            Bitmap.createScaledBitmap(square, side, side, true)
        }
    }

    private fun centerSquare(source: Bitmap): Bitmap {
        val edge = minOf(source.width, source.height)
        val left = (source.width - edge) / 2
        val top = (source.height - edge) / 2
        return Bitmap.createBitmap(source, left, top, edge, edge)
    }

    private const val CUSTOM_ICON_SIZE = 512
}
