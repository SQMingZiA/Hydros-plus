package org.cqutosp.hydros.data.update

import android.content.Context
import android.content.SharedPreferences

/**
 * 启动自动版本检测开关。放在 SharedPreferences 里是为了让应用启动时
 * （DataStore 还没读完）就能决定要不要访问更新源，避免网关不可达时每次都弹提示。
 */
object UpdateCheckSetting {
    private const val FILE = "hydros.update"
    private const val KEY = "checkEnabled"
    private const val DEFAULT_ON_MIGRATION = "defaultOnV147"

    @Volatile private var cached = true
    private var prefs: SharedPreferences? = null

    fun install(context: Context) {
        val store = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = store
        // 1.4.7 起自动检测默认开启：老版本可能留下了关闭状态，这里只迁移一次，
        // 之后用户仍可在开发者页手动关掉。
        if (!store.getBoolean(DEFAULT_ON_MIGRATION, false)) {
            store.edit().putBoolean(KEY, true).putBoolean(DEFAULT_ON_MIGRATION, true).apply()
            cached = true
        } else {
            cached = store.getBoolean(KEY, true)
        }
    }

    fun enabled(): Boolean = cached

    fun set(enabled: Boolean) {
        cached = enabled
        prefs?.edit()?.putBoolean(KEY, enabled)?.apply()
    }
}
