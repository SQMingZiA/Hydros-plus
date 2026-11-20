package org.cqutosp.hydros.ui.state

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.cqutosp.hydros.data.storage.AppIconKeys
import org.cqutosp.hydros.data.storage.AppIconStore

/** 桌面入口别名的全名，必须与 AndroidManifest.xml 中的 activity-alias 一一对应。 */
object AppIconAliases {
    const val DEFAULT = "org.cqutosp.hydros.DefaultIcon"
    const val QZXY = "org.cqutosp.hydros.QzxyIcon"

    val all: List<String> = listOf(DEFAULT, QZXY)

    /** 某个图标键对应的桌面上真正启用的别名。 */
    fun forKey(key: String): String = when (key) {
        AppIconKeys.QZXY -> QZXY
        else -> DEFAULT
    }
}

/**
 * 桌面图标切换：通过 PackageManager.setComponentEnabledSetting 启用目标 activity-alias 并禁用另一个。
 *
 * 这里只碰组件状态与本地偏好，不重启进程（DONT_KILL_APP），避免正在进行的业务被中断。
 */
object AppIconController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 立即应用图标键：切换别名 enabled 状态，并异步落盘。 */
    fun apply(context: Context, key: String) {
        val app = context.applicationContext
        val normalized = AppIconKeys.normalize(key)
        val target = AppIconAliases.forKey(normalized)
        // 先启用目标别名，再禁用另一个：中途异常也不会出现“没有任何桌面入口”的状态。
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
            else -> AppIconStore(app).cachedIcon()
        }
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
}
