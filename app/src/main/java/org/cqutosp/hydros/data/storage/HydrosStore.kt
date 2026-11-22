package org.cqutosp.hydros.data.storage

import org.cqutosp.hydros.data.feedback.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import org.cqutosp.hydros.data.network.Credentials
import org.cqutosp.hydros.ui.state.DeviceUi
import org.cqutosp.hydros.ui.state.SessionUi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable data class SavedAccount(val devices: List<DeviceUi> = emptyList(), val favorites: Set<String> = emptySet(),
    val recent: List<String> = emptyList(), val session: SessionUi? = null,
    val deviceRemarks: Map<String, String> = emptyMap())
@Serializable data class FeedbackSettings(
    val hapticFeedbackEnabled: Boolean = true,
    val reduceMotionEnabled: Boolean = false,
    /** 关闭长按取水的动画：开启后设备改为点击即开始使用。 */
    val waterAnimationDisabled: Boolean = false
)
/**
 * 通知偏好：一个总开关 + 按主题细分的开关。
 *
 * 总开关关闭时不发任何系统通知；单个主题关闭只影响该主题，界面内的提示不受影响。
 */
@Serializable data class NotificationSettings(
    val enabled: Boolean = true,
    /** 用水与限额：达到月度限额、开始/结束用水。 */
    val waterAlerts: Boolean = true,
    /** 账单与充值：账单结算、充值到账、余额不足。 */
    val billing: Boolean = true,
    /** 校园通知：学校推送的停水、检修与公告。 */
    val campus: Boolean = true,
    /** 设备与使用：设备连接、掉线、会话异常。 */
    val device: Boolean = true,
    /** 版本更新：发现新版本。 */
    val updates: Boolean = true,
    /** 由系统通知权限被拒绝导致的静默状态，记录上次检查时间。 */
    val lastPromptedAt: Long = 0L,
)
@Serializable private data class SavedSettings(val token: String? = null, /** 不依赖 Keystore 的后备密文，默认 null 以兼容旧版数据。 */ val tokenBackup: String? = null, val lastAccount: String? = null, val theme: String = "SYSTEM", val accounts: Map<String, SavedAccount> = emptyMap(), val onboardingCompleted: Boolean = false,
    val feedback: FeedbackSettings = FeedbackSettings(), val fontSizeScale: Float = 1f,
    val postSessionDestination: String = PostSessionDestination.BILL.name,
    val homeConnectCardVisible: Boolean = true, val waterLimitCents: Long? = null,
    val waterLimitNotifiedMonth: String? = null, /** 达到限额后是否禁止开始新的用水。 */ val waterLimitBlock: Boolean = false, val avatarPath: String? = null, val demoMode: Boolean = false,
    val fontFamily: String = "PINGFANG", val fontWeightStep: Int = 0, val gradientPreset: String = "DEFAULT", val gradientEnabled: Boolean = true,
    val accentColor: Long? = null, val wallpaperPath: String? = null, val wallpaperDim: Float = 0.0f, val wallpaperBlur: Float = 0.0f,
    /** 底部导航是否启用液态玻璃（折射+模糊），关闭后改为普通磨砂。 */ val liquidGlass: Boolean = true,
    /** 启动时是否自动检查新版本；关闭后不再自动请求更新源。 */ val updateCheckEnabled: Boolean = true,
    /** 系统通知偏好（总开关 + 主题开关）。 */
    val notifications: NotificationSettings = NotificationSettings(),
    /** 校园消息回执：是否已建立基线、出现过的版本号、已读版本号。 */
    val messageBaseline: Boolean = false, val messageKnown: List<String> = emptyList(),
    val messageRead: List<String> = emptyList())
interface HydrosStore {
    suspend fun credentials(): Credentials?
    suspend fun credentials(value: Credentials?)
    /** 本地是否仍存有凭证密文：用于区分"确实未登录"与"密文暂时解不出"。 */
    suspend fun hasStoredCredentials(): Boolean = false
    suspend fun lastAccount(): String? = null
    suspend fun account(key: String): SavedAccount
    suspend fun updateAccount(key: String, transform: (SavedAccount) -> SavedAccount): SavedAccount
    suspend fun theme(): String
    suspend fun theme(value: String)
    suspend fun feedback(): FeedbackSettings
    suspend fun updateFeedback(transform: (FeedbackSettings) -> FeedbackSettings): FeedbackSettings
    suspend fun fontSizeScale(): Float
    suspend fun fontSizeScale(value: Float)
    suspend fun onboardingCompleted(): Boolean = false
    suspend fun onboardingCompleted(value: Boolean) {}
    suspend fun postSessionDestination(): PostSessionDestination = PostSessionDestination.BILL
    suspend fun postSessionDestination(value: PostSessionDestination) {}
    /** 首页"连接身边的设备"卡片是否展示，关闭后不再显示。 */
    suspend fun homeConnectCardVisible(): Boolean = true
    suspend fun homeConnectCardVisible(value: Boolean) {}
    /** 每月用水支出限额（分），null 表示未设置。 */
    suspend fun waterLimitCents(): Long? = null
    suspend fun waterLimitCents(value: Long?) {}
    /** 已提醒过限额的月份，避免同一月份重复打扰。 */
    suspend fun waterLimitNotifiedMonth(): String? = null
    suspend fun waterLimitNotifiedMonth(value: String?) {}
    /** 达到限额后是否禁止开始新的用水。 */
    suspend fun waterLimitBlock(): Boolean = false
    suspend fun waterLimitBlock(value: Boolean) {}
    /** 开发者演示数据模式：用本地模拟数据替代校园网关数据，便于无设备调试。 */
    suspend fun demoMode(): Boolean = false
    suspend fun demoMode(value: Boolean) {}
    /** 校园消息回执（见 data/messages/MessageReceipts）。 */
    suspend fun messageReceipts(): org.cqutosp.hydros.data.messages.MessageReceipts =
        org.cqutosp.hydros.data.messages.MessageReceipts()
    suspend fun messageReceipts(value: org.cqutosp.hydros.data.messages.MessageReceipts) {}
    /** 底部导航的液态玻璃效果开关，关闭后使用普通磨砂质感。 */
    suspend fun liquidGlass(): Boolean = true
    suspend fun liquidGlass(value: Boolean) {}
    /** 启动自动版本检测开关：关闭后不再自动访问更新源。 */
    suspend fun updateCheckEnabled(): Boolean = true
    suspend fun updateCheckEnabled(value: Boolean) {}
    /** 系统通知偏好（总开关 + 主题开关）。 */
    suspend fun notifications(): NotificationSettings = NotificationSettings()
    suspend fun notifications(value: NotificationSettings) {}
    /** 自定义头像在应用私有目录里的绝对路径，null 表示使用默认头像。 */
    suspend fun fontWeightStep(): Int = 0
    suspend fun fontWeightStep(value: Int) {}

    suspend fun avatarPath(): String? = null
    suspend fun avatarPath(value: String?) {}
    /** 应用字体："PINGFANG" 内置苹方，"SYSTEM" 跟随系统。 */
    suspend fun fontFamily(): String = "PINGFANG"
    suspend fun fontFamily(value: String) {}
    /** 首页渐变背景预设，见 GradientPreset。 */
    suspend fun gradientPreset(): String = "DEFAULT"
    suspend fun gradientPreset(value: String) {}
    /** 是否启用首页渐变光斑背景，关闭后使用纯色页面。 */
    suspend fun gradientEnabled(): Boolean = true
    suspend fun gradientEnabled(value: Boolean) {}
    /** 自定义强调色 ARGB 值，null 表示使用默认主题色。 */
    suspend fun accentColor(): Long? = null
    suspend fun accentColor(value: Long?) {}
    /** 自定义壁纸在应用私有目录里的绝对路径，null 表示使用渐变背景。 */
    suspend fun wallpaperPath(): String? = null
    suspend fun wallpaperPath(value: String?) {}
    /** 壁纸压暗程度 0f..0.6f。 */
    suspend fun wallpaperDim(): Float = 0.0f
    suspend fun wallpaperDim(value: Float) {}
    /** 壁纸模糊半径（dp）0f..25f。 */
    suspend fun wallpaperBlur(): Float = 0.0f
    suspend fun wallpaperBlur(value: Float) {}
}
private val Context.hydrosPreferences by preferencesDataStore("hydros")
class PreferencesHydrosStore(context: Context) : HydrosStore {
    private val appContext = context.applicationContext
    private val preferences = appContext.hydrosPreferences
    private val key = stringPreferencesKey("settings")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val secret = CredentialCipher()
    private val backup = BackupCredentialCipher(appContext)
    private suspend fun read(): SavedSettings = failureBoundary(FailureOperation.RESTORE, FailureStage.STORAGE) { preferences.data.first()[key]?.let {
        json.decodeFromString<SavedSettings>(it)
    } ?: SavedSettings() }
    private suspend fun edit(transform: (SavedSettings) -> SavedSettings): SavedSettings = failureBoundary(FailureOperation.SETTINGS, FailureStage.STORAGE) { lock.withLock {
        var next: SavedSettings?=null
        preferences.edit { prefs ->
            val existing=prefs[key]?.let { json.decodeFromString<SavedSettings>(it) } ?: SavedSettings()
            val updated=transform(existing)
            prefs[key]=json.encodeToString(updated)
            next=updated
        }
        requireNotNull(next)
    } }
    /**
     * 读取顺序：主密文（Android Keystore）→ 后备密文（签名派生密钥）。
     * 后备密文解出后回写主密文自愈，下次重新走快路径；两路都失败也不抹掉 token，
     * 保留 lastAccount 供登录页回填手机号。
     */
    override suspend fun credentials(): Credentials? {
        val settings = read()
        decodeToken(settings.token)?.let { return it }
        val recovered = decodeBackup(settings.tokenBackup) ?: return null
        runCatching {
            edit { current ->
                if (current.tokenBackup == null) current
                else current.copy(token = runCatching { secret.encrypt(json.encodeToString(recovered)) }.getOrNull())
            }
        }
        return recovered
    }
    /** 双写：Keystore 主密文失败时后备密文仍在，任何情况下都不会退化成明文 token。 */
    override suspend fun credentials(value: Credentials?) { edit { current ->
        if (value == null) current.copy(token = null, tokenBackup = null)
        else {
            val payload = json.encodeToString(value)
            current.copy(token = runCatching { secret.encrypt(payload) }.getOrNull(),
                tokenBackup = runCatching { backup.encrypt(payload) }.getOrNull(),
                lastAccount = value.key)
        }
    } }
    override suspend fun hasStoredCredentials(): Boolean {
        val settings = read()
        return settings.token != null || settings.tokenBackup != null
    }
    private fun decodeToken(encoded: String?): Credentials? = try {
        encoded?.let { json.decodeFromString<Credentials>(secret.decrypt(it)) }
    } catch (_: Exception) { null }
    private fun decodeBackup(encoded: String?): Credentials? = try {
        encoded?.let { json.decodeFromString<Credentials>(backup.decrypt(it)) }
    } catch (_: Exception) { null }
    override suspend fun lastAccount()=read().lastAccount
    override suspend fun account(key: String) = read().accounts[key] ?: SavedAccount()
    override suspend fun updateAccount(key: String, transform: (SavedAccount) -> SavedAccount): SavedAccount {
        return edit { it.copy(accounts = it.accounts + (key to transform(it.accounts[key] ?: SavedAccount()))) }.accounts.getValue(key)
    }
    override suspend fun theme() = read().theme
    override suspend fun theme(value: String) { edit { it.copy(theme = value) } }
    override suspend fun feedback() = read().feedback
    override suspend fun updateFeedback(transform: (FeedbackSettings) -> FeedbackSettings) =
        edit { it.copy(feedback = transform(it.feedback)) }.feedback
    override suspend fun fontSizeScale() = read().fontSizeScale
    override suspend fun fontSizeScale(value: Float) {
        require(FontSizeOption.entries.any { it.scale == value })
        edit { it.copy(fontSizeScale = value) }
    }
    override suspend fun onboardingCompleted() = read().onboardingCompleted
    override suspend fun onboardingCompleted(value: Boolean) { edit { it.copy(onboardingCompleted = value) } }
    override suspend fun postSessionDestination(): PostSessionDestination {
        val saved = read().postSessionDestination
        return PostSessionDestination.entries.firstOrNull { it.name == saved } ?: PostSessionDestination.BILL
    }
    override suspend fun postSessionDestination(value: PostSessionDestination) { edit { it.copy(postSessionDestination = value.name) } }
    override suspend fun homeConnectCardVisible() = read().homeConnectCardVisible
    override suspend fun homeConnectCardVisible(value: Boolean) { edit { it.copy(homeConnectCardVisible = value) } }
    override suspend fun waterLimitCents() = read().waterLimitCents
    override suspend fun waterLimitCents(value: Long?) { edit { it.copy(waterLimitCents = value) } }
    override suspend fun waterLimitNotifiedMonth() = read().waterLimitNotifiedMonth
    override suspend fun waterLimitNotifiedMonth(value: String?) { edit { it.copy(waterLimitNotifiedMonth = value) } }
    override suspend fun waterLimitBlock() = read().waterLimitBlock
    override suspend fun waterLimitBlock(value: Boolean) { edit { it.copy(waterLimitBlock = value) } }
    override suspend fun demoMode() = read().demoMode
    override suspend fun demoMode(value: Boolean) { edit { it.copy(demoMode = value) } }
    override suspend fun messageReceipts() = read().let {
        org.cqutosp.hydros.data.messages.MessageReceipts(it.messageBaseline, it.messageKnown, it.messageRead)
    }
    override suspend fun messageReceipts(value: org.cqutosp.hydros.data.messages.MessageReceipts) {
        edit { it.copy(messageBaseline = value.initialized, messageKnown = value.known, messageRead = value.read) }
    }
    override suspend fun liquidGlass() = read().liquidGlass
    override suspend fun liquidGlass(value: Boolean) { edit { it.copy(liquidGlass = value) } }
    override suspend fun updateCheckEnabled() = read().updateCheckEnabled
    override suspend fun notifications() = read().notifications
    override suspend fun notifications(value: NotificationSettings) { edit { it.copy(notifications = value) } }
    override suspend fun updateCheckEnabled(value: Boolean) { edit { it.copy(updateCheckEnabled = value) } }
    override suspend fun fontWeightStep() = read().fontWeightStep
    override suspend fun fontWeightStep(value: Int) { edit { it.copy(fontWeightStep = value) } }
    override suspend fun avatarPath() = read().avatarPath
    override suspend fun avatarPath(value: String?) { edit { it.copy(avatarPath = value) } }
    override suspend fun fontFamily() = read().fontFamily
    override suspend fun fontFamily(value: String) { edit { it.copy(fontFamily = value) } }
    override suspend fun gradientPreset() = read().gradientPreset
    override suspend fun gradientPreset(value: String) { edit { it.copy(gradientPreset = value) } }
    override suspend fun gradientEnabled() = read().gradientEnabled
    override suspend fun gradientEnabled(value: Boolean) { edit { it.copy(gradientEnabled = value) } }
    override suspend fun accentColor() = read().accentColor
    override suspend fun accentColor(value: Long?) { edit { it.copy(accentColor = value) } }
    override suspend fun wallpaperPath() = read().wallpaperPath
    override suspend fun wallpaperPath(value: String?) { edit { it.copy(wallpaperPath = value) } }
    override suspend fun wallpaperDim() = read().wallpaperDim
    override suspend fun wallpaperDim(value: Float) { edit { it.copy(wallpaperDim = value) } }
    override suspend fun wallpaperBlur() = read().wallpaperBlur
    override suspend fun wallpaperBlur(value: Float) { edit { it.copy(wallpaperBlur = value) } }
}
/** Only ciphertext, IV and authentication tag leave Android Keystore. */
class CredentialCipher {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("hydros.session", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("hydros.session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    fun encrypt(value: String): String = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, key())
        Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(doFinal(value.toByteArray(Charsets.UTF_8)))
    }
    fun decrypt(value: String): String = Cipher.getInstance("AES/GCM/NoPadding").run {
        val parts = value.split(":", limit = 2)
        init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])))
        String(doFinal(Base64.getDecoder().decode(parts[1])), Charsets.UTF_8)
    }
}
/**
 * 后备密文：完全不依赖 Android Keystore，用于主密钥被系统销毁后的自动恢复。
 * 密钥由应用签名证书摘要 + 包名 + 设备/构建信息派生（同签名重装或系统重置密钥后仍可解出），
 * 磁盘上同样只保存密文与随机 IV，不出现明文 token。
 */
class BackupCredentialCipher(context: Context) {
    private val appContext = context.applicationContext
    private fun keyBytes(): ByteArray {
        val signing = runCatching {
            @Suppress("DEPRECATION")
            val info = appContext.packageManager.getPackageInfo(appContext.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray() ?: ByteArray(0)
        }.getOrDefault(ByteArray(0))
        val material = appContext.packageName.toByteArray(Charsets.UTF_8) + signing +
            Build.MANUFACTURER.toByteArray(Charsets.UTF_8) + Build.MODEL.toByteArray(Charsets.UTF_8) +
            Build.DEVICE.toByteArray(Charsets.UTF_8) + Build.FINGERPRINT.toByteArray(Charsets.UTF_8) + SALT
        return MessageDigest.getInstance("SHA-256").digest(material)
    }
    fun encrypt(value: String): String = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes(), "AES"))
        Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(doFinal(value.toByteArray(Charsets.UTF_8)))
    }
    fun decrypt(value: String): String = Cipher.getInstance("AES/GCM/NoPadding").run {
        val parts = value.split(":", limit = 2)
        init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes(), "AES"), GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])))
        String(doFinal(Base64.getDecoder().decode(parts[1])), Charsets.UTF_8)
    }
    private companion object {
        val SALT = "hydros.session.backup.v1".toByteArray(Charsets.UTF_8)
    }
}
