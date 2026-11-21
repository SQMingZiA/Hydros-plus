package org.cqutosp.hydros.data.update

import android.content.Context
import android.content.SharedPreferences

/**
 * 更新源：Gitee 与 GitHub 都是完整仓库，各自带有 Releases。
 * 版本信息、更新历史、发布页与「源代码仓库」入口都跟随当前选中的源。
 */
enum class UpdateSource(
    val label: String,
    val summary: String,
    val repository: String,
    val homepage: String,
) {
    GITEE("Gitee", "国内访问更稳，由作者从 GitHub 自动同步", HYDROS_GITEE_REPO, HYDROS_GITEE),
    GITHUB("GitHub", "官方仓库，发布最及时", HYDROS_GITHUB_REPO, HYDROS_REPOSITORY);

    val releasesUrl: String get() = homepage + "/releases"

    fun releaseUrl(tag: String): String =
        releasesUrl + "/tag/" + java.net.URLEncoder.encode(tag, "UTF-8").replace("+", "%20")
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
