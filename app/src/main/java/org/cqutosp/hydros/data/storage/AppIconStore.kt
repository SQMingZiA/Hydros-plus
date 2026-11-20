package org.cqutosp.hydros.data.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 桌面图标键值：DEFAULT 默认图标、QZXY 趣智校园预设、CUSTOM 用户自选图片。 */
object AppIconKeys {
    const val DEFAULT = "DEFAULT"
    const val QZXY = "QZXY"
    const val CUSTOM = "CUSTOM"

    /** 任何未知或空值都回落到默认图标，避免脏数据把桌面入口落在一个禁用的别名上。 */
    fun normalize(value: String?): String = when (value) {
        QZXY -> QZXY
        CUSTOM -> CUSTOM
        else -> DEFAULT
    }
}

/**
 * 桌面图标偏好的读写封装。
 *
 * 使用独立的 SharedPreferences（hydros.appicon），与 HydrosStore 的 DataStore 分开，
 * 这样清除数据（AndroidAppDataControl 走 clearApplicationUserData）时会被系统一起清掉，无需额外迁移。
 */
class AppIconStore(context: Context) {
    private val appContext = context.applicationContext

    private val preferences: SharedPreferences
        get() = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    suspend fun icon(): String = withContext(Dispatchers.IO) {
        AppIconKeys.normalize(preferences.getString(KEY_ICON, null))
    }

    suspend fun icon(value: String) {
        withContext(Dispatchers.IO) {
            preferences.edit().putString(KEY_ICON, AppIconKeys.normalize(value)).apply()
        }
    }

    suspend fun customPath(): String? = withContext(Dispatchers.IO) {
        preferences.getString(KEY_CUSTOM_PATH, null)?.takeIf { it.isNotEmpty() }
    }

    suspend fun customPath(value: String?) {
        withContext(Dispatchers.IO) {
            val editor = preferences.edit()
            if (value.isNullOrEmpty()) {
                editor.remove(KEY_CUSTOM_PATH)
            } else {
                editor.putString(KEY_CUSTOM_PATH, value)
            }
            editor.apply()
        }
    }

    /** 同步读取当前图标键，仅供启动期判断别名时做兜底，不要在组合里频繁调用。 */
    fun cachedIcon(): String = AppIconKeys.normalize(preferences.getString(KEY_ICON, null))

    /** 同步读取自定义图标文件路径（可能已不存在，调用方需自行判断）。 */
    fun cachedCustomPath(): String? = preferences.getString(KEY_CUSTOM_PATH, null)?.takeIf { it.isNotEmpty() }

    companion object {
        const val FILE_NAME = "hydros.appicon"
        private const val KEY_ICON = "iconKey"
        private const val KEY_CUSTOM_PATH = "customPath"
    }
}
