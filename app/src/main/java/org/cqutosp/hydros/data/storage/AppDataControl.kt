package org.cqutosp.hydros.data.storage

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface AppDataControl {
    suspend fun sizeBytes(): Long
    fun clearAllData(): Boolean
    /** 只清理缓存与临时文件，账号、设备、设置与头像都保留。 */
    fun clearCache(): Boolean
    /** 缓存目录当前占用，用于在「存储占用情况」里提示可清理的空间。 */
    suspend fun cacheSizeBytes(): Long = 0L
}

class AndroidAppDataControl(private val context: Context) : AppDataControl {
    override suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        val internal = File(context.applicationInfo.dataDir)
        // Count user data, excluding installed code and compiled code caches.
        val roots = listOf(context.filesDir, context.cacheDir, context.noBackupFilesDir,
            File(internal, "shared_prefs"), File(internal, "databases")) +
            context.getExternalFilesDirs(null).filterNotNull() + context.externalCacheDirs.filterNotNull()
        roots.distinctBy { it.absolutePath }.sumOf { root ->
            root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }
    override fun clearAllData(): Boolean = context.getSystemService(ActivityManager::class.java).clearApplicationUserData()

    override fun clearCache(): Boolean = runCatching {
        val roots = (listOf(context.cacheDir) + context.externalCacheDirs.filterNotNull()).distinctBy { it.absolutePath }
        roots.forEach { root ->
            root.listFiles()?.forEach { child -> runCatching { child.deleteRecursively() } }
        }
        true
    }.getOrDefault(false)

    override suspend fun cacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        val roots = (listOf(context.cacheDir) + context.externalCacheDirs.filterNotNull()).distinctBy { it.absolutePath }
        roots.sumOf { root -> root.walkTopDown().filter { it.isFile }.sumOf { file -> file.length() } }
    }
}
