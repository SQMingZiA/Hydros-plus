package org.cqutosp.hydros.ui.state

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
 * 这里只碰组件状态与本地偏好，不重启进程（DONT_KILL_APP），避免正在进行的业务被中断；
 * 切换后额外做一次缓存唤醒，让只读启动器缓存的桌面尽快重新拉取图标信息。
 */
object AppIconController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 立即应用图标键：切换别名 enabled 状态，唤醒桌面缓存，并异步落盘。
     *
     * 顺序很重要：先把目标别名打开并确认 PackageManager 已经接受，再去关旧别名；
     * 否则在部分 ROM（HyperOS / Android 16+）上一次失败的 setComponentEnabledSetting
     * 会留下“两个都关掉”的状态，桌面上直接没有图标。
     *
     * @return 目标别名是否确实处于启用状态
     */
    fun apply(context: Context, key: String): Boolean {
        val app = context.applicationContext
        val normalized = AppIconKeys.normalize(key)
        val target = AppIconAliases.forKey(normalized)
        setEnabled(app, target, true)
        if (!isAliasEnabled(app, target)) {
            HydrosLog.log("icon", "启用桌面别名失败：" + target)
            return false
        }
        AppIconAliases.all.forEach { alias ->
            if (alias != target) {
                setEnabled(app, alias, false)
            }
        }
        // 落盘要早于刷新：某些桌面会重新查询 PackageManager 时顺带读到我们的偏好。
        scope.launch { runCatching { AppIconStore(app).icon(normalized) } }
        refreshLauncher(app, target)
        // 厂商桌面大多是异步刷新：立刻刷新一次后，再补两轮延迟刷新。
        scope.launch {
            // 再设置一次同样的状态：会产生新的组件变更回调，专门对付只听回调不看状态的桌面。
            delay(600)
            setEnabled(app, target, true)
            refreshLauncher(app, target)
            delay(1400)
            refreshLauncher(app, target)
        }
        HydrosLog.log("icon", "桌面图标切换为 " + normalized)
        return true
    }

    private fun isAliasEnabled(context: Context, alias: String): Boolean = runCatching {
        val info = context.packageManager.getActivityInfo(
            ComponentName(context.packageName, alias),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        info.enabled
    }.getOrDefault(false)

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

    /**
     * 别名的 enabled 状态变化后，通知系统与启动器重新读取组件信息。
     *
     * 部分厂商桌面（尤其 HyperOS / Android 16+）只在收到整包级回调时才刷新图标缓存，
     * 单纯切别名会继续显示旧图标。这里依次做几件“读一次即触发刷新”的廉价操作，
     * 全部包在 runCatching 里，失败也不影响切换结果：
     * 1. 以 ENABLED 状态重设一次应用本身（不杀进程）：会发出整包 CHANGED 回调，
     *    这是各家桌面最常监听的刷新信号，也是别名切换后最常见的一步；
     * 2. 读一次目标别名的 ActivityInfo，确保 PackageManager 缓存中的组件信息是新的；
     * 3. 查询一次桌面 Intent，让系统桌面列表缓存更新；
     * 4. 通过 LauncherApps 拉一次本应用的桌面入口列表（部分桌面监听这次调用刷新图标）；
     * 5. 刷新已固定的快捷方式，避免快捷方式仍显示旧图标。
     */
    private fun refreshLauncher(context: Context, alias: String) {
        val packageName = context.packageName
        runCatching {
            context.packageManager.setApplicationEnabledSetting(
                packageName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        runCatching {
            context.packageManager.getActivityInfo(
                ComponentName(packageName, alias),
                PackageManager.MATCH_DISABLED_COMPONENTS
            )
        }
        runCatching {
            context.packageManager.queryIntentActivities(launcherIntent, 0)
        }
        runCatching {
            context.getSystemService(LauncherApps::class.java)?.getActivityList(packageName, Process.myUserHandle())
        }
        runCatching {
            context.getSystemService(ShortcutManager::class.java)?.let { manager ->
                manager.updateShortcuts(manager.dynamicShortcuts)
            }
        }
    }
}
