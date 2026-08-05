package com.codexpocket.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        // Continuous cache synchronization is independent of sound/vibration.
        // Restart it after a reboot or app upgrade so new Mac messages are
        // already present when the user opens a task.
        runCatching { TaskNotificationService.ensureRunning(context) }
    }
}
