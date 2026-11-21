package org.cqutosp.hydros.data.update

import android.content.Context
import android.content.SharedPreferences

/** 更新源：Gitee 是作者同步的镜像，GitHub 是正式仓库。 */
enum class UpdateSource(val label: String, val summary: String) {
    GITEE("Gitee", "国内访问更稳，由作者从 GitHub 自动同步"),
    GITHUB("GitHub", "官方仓库，发布最及时"),
}

/**
 * 更新源偏好：用独立的 SharedPreferences 保存，Application 与界面都能同步读取，
 * 不参与 DataStore 的那套异步读取，避免启动时要等一次 IO 才能决定更新源。
 */
object UpdateSourceSetting {
    private const val FILE = "hydros.update"
    private const val KEY = "source"

    @Volatile private var cached: UpdateSource = UpdateSource.GITEE
    @Volatile private var prefs: SharedPreferences? = null

    fun install(context: Context) {
        val store = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = store
        cached = runCatching { UpdateSource.valueOf(store.getString(KEY, null).orEmpty()) }
            .getOrDefault(UpdateSource.GITEE)
    }

    fun current(): UpdateSource = cached

    fun set(source: UpdateSource) {
        cached = source
        prefs?.edit()?.putString(KEY, source.name)?.apply()
    }
}
