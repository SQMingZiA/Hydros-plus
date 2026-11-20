package org.cqutosp.hydros.data.storage

import org.cqutosp.hydros.data.feedback.*
import android.content.Context
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
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable data class SavedAccount(val devices: List<DeviceUi> = emptyList(), val favorites: Set<String> = emptySet(),
    val recent: List<String> = emptyList(), val session: SessionUi? = null,
    val deviceRemarks: Map<String, String> = emptyMap())
@Serializable data class FeedbackSettings(val hapticFeedbackEnabled: Boolean = true, val reduceMotionEnabled: Boolean = false)
@Serializable private data class SavedSettings(val token: String? = null, val lastAccount: String? = null, val theme: String = "SYSTEM", val accounts: Map<String, SavedAccount> = emptyMap(), val onboardingCompleted: Boolean = false,
    val feedback: FeedbackSettings = FeedbackSettings(), val fontSizeScale: Float = 1f,
    val postSessionDestination: String = PostSessionDestination.DEVICE.name,
    val homeConnectCardVisible: Boolean = true, val waterLimitCents: Long? = null,
    val waterLimitNotifiedMonth: String? = null, val avatarPath: String? = null, val demoMode: Boolean = false,
    val fontFamily: String = "PINGFANG", val gradientPreset: String = "DEFAULT", val gradientEnabled: Boolean = true,
    val accentColor: Long? = null, val wallpaperPath: String? = null, val wallpaperDim: Float = 0.0f, val wallpaperBlur: Float = 0.0f,
    val voiceAssistantEnabled: Boolean = false)
interface HydrosStore {
    suspend fun credentials(): Credentials?
    suspend fun credentials(value: Credentials?)
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
    suspend fun postSessionDestination(): PostSessionDestination = PostSessionDestination.DEVICE
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
    /** 开发者演示数据模式：用本地模拟数据替代校园网关数据，便于无设备调试。 */
    suspend fun demoMode(): Boolean = false
    suspend fun demoMode(value: Boolean) {}
    /** 智能助手：允许语音助手通过快捷指令/深链打开设备、查询余额与消费。 */
    suspend fun voiceAssistantEnabled(): Boolean = false
    suspend fun voiceAssistantEnabled(value: Boolean) {}
    /** 自定义头像在应用私有目录里的绝对路径，null 表示使用默认头像。 */
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
    private val preferences = context.applicationContext.hydrosPreferences
    private val key = stringPreferencesKey("settings")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val secret = CredentialCipher()
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
    override suspend fun credentials(): Credentials? {
        val encoded = read().token ?: return null
        return try { json.decodeFromString<Credentials>(secret.decrypt(encoded)) }
        catch (_: Exception) {
            // 解密失败通常意味着 Android Keystore 里的会话密钥失效（换签名安装、系统重置密钥等）。
            // 这里不要直接 credentials(null) 把 token 抹掉：保留 lastAccount 以便登录页回填手机号，
            // 也避免一次读取失败就把本地凭证永久删除。
            null
        }
    }
    override suspend fun credentials(value: Credentials?) { edit { it.copy(token = value?.let { auth -> secret.encrypt(json.encodeToString(auth)) }, lastAccount=value?.key ?: it.lastAccount) } }
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
        return PostSessionDestination.entries.firstOrNull { it.name == saved } ?: PostSessionDestination.DEVICE
    }
    override suspend fun postSessionDestination(value: PostSessionDestination) { edit { it.copy(postSessionDestination = value.name) } }
    override suspend fun homeConnectCardVisible() = read().homeConnectCardVisible
    override suspend fun homeConnectCardVisible(value: Boolean) { edit { it.copy(homeConnectCardVisible = value) } }
    override suspend fun waterLimitCents() = read().waterLimitCents
    override suspend fun waterLimitCents(value: Long?) { edit { it.copy(waterLimitCents = value) } }
    override suspend fun waterLimitNotifiedMonth() = read().waterLimitNotifiedMonth
    override suspend fun waterLimitNotifiedMonth(value: String?) { edit { it.copy(waterLimitNotifiedMonth = value) } }
    override suspend fun demoMode() = read().demoMode
    override suspend fun demoMode(value: Boolean) { edit { it.copy(demoMode = value) } }
    override suspend fun voiceAssistantEnabled() = read().voiceAssistantEnabled
    override suspend fun voiceAssistantEnabled(value: Boolean) { edit { it.copy(voiceAssistantEnabled = value) } }
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
