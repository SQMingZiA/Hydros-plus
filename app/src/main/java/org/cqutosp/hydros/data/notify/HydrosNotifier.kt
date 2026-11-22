package org.cqutosp.hydros.data.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.cqutosp.hydros.MainActivity
import org.cqutosp.hydros.R
import org.cqutosp.hydros.data.storage.NotificationSettings

/**
 * 系统通知的主题。每个主题对应一个独立的通知渠道，用户可以在应用内或系统设置里分别关闭，
 * 这是 Android 8 以后对通知分组的推荐做法：用途不同、打扰程度不同的通知不要挤在一条渠道里。
 */
enum class NotificationTopic(
    val key: String,
    val label: String,
    val description: String,
    val channelId: String,
    val channelName: String,
    val channelDescription: String,
) {
    WATER(
        key = "water", label = "用水与限额",
        description = "达到月度限额、开始或结束用水时提醒",
        channelId = "hydros.water", channelName = "用水提醒",
        channelDescription = "月度用水限额达到、开始与结束用水",
    ),
    BILLING(
        key = "billing", label = "账单与充值",
        description = "账单结算、充值到账与余额不足提醒",
        channelId = "hydros.billing", channelName = "账单与充值",
        channelDescription = "账单结算、充值到账与钱包余额不足",
    ),
    CAMPUS(
        key = "campus", label = "校园通知",
        description = "学校发布的停水、检修与公告",
        channelId = "hydros.campus", channelName = "校园通知",
        channelDescription = "学校通过校园网关推送的停水、检修与公告",
    ),
    DEVICE(
        key = "device", label = "设备与使用",
        description = "设备连接、掉线与使用异常提醒",
        channelId = "hydros.device", channelName = "设备状态",
        channelDescription = "设备连接、掉线与会话异常",
    ),
    UPDATE(
        key = "update", label = "版本更新",
        description = "发现新版本时提醒",
        channelId = "hydros.update", channelName = "版本更新",
        channelDescription = "发现新的 Hydros+ 版本",
    ),
    ;

    companion object {
        fun ofKey(key: String): NotificationTopic? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 系统通知的唯一出口：所有通知都必须经过这里，这样总开关和主题开关才真正生效。
 *
 * 设计要点：
 * - 只使用 [Notification.Builder]（minSdk 31，不需要 compat 库）；
 * - 每个主题一个渠道，用户可以在系统设置里单独静音；
 * - 通知 id 由业务方传入，重复通知会覆盖而不是堆积；
 * - 点击通知回到 [MainActivity]，不接受外部传入的任意 Intent；
 * - 开关或权限不允许时静默丢弃并返回 false，调用方无需自己判断。
 */
object HydrosNotifier {

    private const val GROUP_ID = "hydros"
    private const val GROUP_NAME = "Hydros+ 通知"

    /** 应用启动时调用一次：渠道已存在时系统会忽略重复创建。 */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.createNotificationChannelGroup(NotificationChannelGroup(GROUP_ID, GROUP_NAME))
            NotificationTopic.entries.forEach { topic ->
                val channel = NotificationChannel(topic.channelId, topic.channelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = topic.channelDescription
                    group = GROUP_ID
                    setShowBadge(true)
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /** 系统是否允许本应用发通知（总开关）。 */
    fun systemEnabled(context: Context): Boolean =
        runCatching { context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true }
            .getOrDefault(false)

    /** POST_NOTIFICATIONS 是否已授权；Android 12 及以下默认视为已授权。 */
    fun permissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** 单个渠道是否仍由系统允许；渠道尚未创建时返回 null。 */
    fun channelEnabled(context: Context, topic: NotificationTopic): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        val channel = manager.getNotificationChannel(topic.channelId) ?: return null
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** 用户点击通知时希望直达的页面，例如 "message/12"。 */
    fun deepLinkFor(context: Context, deepLink: String?): Intent? = deepLink?.let {
        Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_DEEP_LINK, it)
        }
    }

    fun canPost(context: Context, settings: NotificationSettings, topic: NotificationTopic): Boolean {
        if (!settings.enabled) return false
        if (!settings.enabledFor(topic)) return false
        if (!systemEnabled(context)) return false
        if (!permissionGranted(context)) return false
        if (channelEnabled(context, topic) == false) return false
        return true
    }

    /** 发送一条通知；返回是否真的发出。 */
    fun notify(
        context: Context,
        settings: NotificationSettings,
        topic: NotificationTopic,
        title: String,
        body: String,
        notificationId: Int,
        deepLink: String? = null,
    ): Boolean {
        if (!canPost(context, settings, topic)) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val open = PendingIntent.getActivity(
                context,
                notificationId,
                deepLinkFor(context, deepLink) ?: Intent(context, MainActivity::class.java),
                flags,
            )
            val builder = Notification.Builder(context, topic.channelId)
                .setSmallIcon(R.drawable.ic_stat_hydros)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(open)
                .setCategory(
                    when (topic) {
                        NotificationTopic.WATER -> Notification.CATEGORY_STATUS
                        NotificationTopic.BILLING -> Notification.CATEGORY_STATUS
                        NotificationTopic.CAMPUS -> Notification.CATEGORY_MESSAGE
                        NotificationTopic.DEVICE -> Notification.CATEGORY_STATUS
                        NotificationTopic.UPDATE -> Notification.CATEGORY_RECOMMENDATION
                    }
                )
                .setVisibility(Notification.VISIBILITY_PRIVATE)
            manager.notify(notificationId, builder.build())
            true
        }.getOrDefault(false)
    }

    const val EXTRA_DEEP_LINK: String = "org.cqutosp.hydros.extra.NOTIFICATION_DEEP_LINK"
}

/** 单个主题是否在偏好里开启；未知主题一律视为关闭，避免新主题默认打扰用户。 */
fun NotificationSettings.enabledFor(topic: NotificationTopic): Boolean = when (topic) {
    NotificationTopic.WATER -> waterAlerts
    NotificationTopic.BILLING -> billing
    NotificationTopic.CAMPUS -> campus
    NotificationTopic.DEVICE -> device
    NotificationTopic.UPDATE -> updates
}
