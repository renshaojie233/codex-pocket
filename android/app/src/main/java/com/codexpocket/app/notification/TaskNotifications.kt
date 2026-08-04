package com.codexpocket.app.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codexpocket.app.MainActivity
import com.codexpocket.app.R

object TaskNotifications {
    const val LISTENER_NOTIFICATION_ID = 4101
    private const val LISTENER_CHANNEL_ID = "codex_background_listener"
    const val COMPLETION_CHANNEL_ID = "codex_task_completion_v2"
    private const val LEGACY_COMPLETION_CHANNEL_ID = "codex_task_completion"
    private val vibrationPattern = longArrayOf(0, 220, 100, 320)

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val listenerChannel = NotificationChannel(
            LISTENER_CHANNEL_ID,
            "后台任务监听",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "保持与 Mac 的连接，以便任务完成时及时提醒"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val completionChannel = NotificationChannel(
            COMPLETION_CHANNEL_ID,
            "任务完成提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Codex 完成任务时显示横幅、播放提示音并震动"
            setSound(sound, audioAttributes)
            enableVibration(true)
            vibrationPattern = TaskNotifications.vibrationPattern
            enableLights(true)
            lightColor = 0xFF625BFF.toInt()
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        manager.createNotificationChannels(listOf(listenerChannel, completionChannel))
        manager.deleteNotificationChannel(LEGACY_COMPLETION_CHANNEL_ID)
    }

    fun listenerNotification(context: Context, status: String): Notification {
        createChannels(context)
        return NotificationCompat.Builder(context, LISTENER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Codex Pocket 提醒已开启")
            .setContentText(status)
            .setContentIntent(openAppIntent(context, "listener"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showCompletion(context: Context, threadId: String, threadTitle: String): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        createChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(COMPLETION_CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
            return false
        }
        val safeTitle = threadTitle.ifBlank { "未命名任务" }
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(context, COMPLETION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Codex 任务已完成")
            .setContentText(safeTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$safeTitle\n点击打开 Codex Pocket 查看结果"))
            .setContentIntent(openAppIntent(context, threadId))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(sound)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setVibrate(vibrationPattern)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(threadId), notification)
        vibrate(context)
        return true
    }

    fun openSystemSettings(context: Context) {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, COMPLETION_CHANNEL_ID)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(vibrationPattern, -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                effect,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION),
            )
        } else {
            vibrateLegacy(vibrator, effect)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateLegacy(vibrator: Vibrator, effect: VibrationEffect) {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        vibrator.vibrate(effect, attributes)
    }

    private fun openAppIntent(context: Context, key: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationId(key: String): Int = 5000 + (key.hashCode() and 0x0fffffff) % 100000
}
