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

    @Volatile private var cached = true
    private var prefs: SharedPreferences? = null

    fun install(context: Context) {
        val store = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = store
        cached = store.getBoolean(KEY, true)
    }

    fun enabled(): Boolean = cached

    fun set(enabled: Boolean) {
        cached = enabled
        prefs?.edit()?.putBoolean(KEY, enabled)?.apply()
    }
}
