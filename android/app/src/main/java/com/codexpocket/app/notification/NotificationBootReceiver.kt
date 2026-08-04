package com.codexpocket.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val enabled = context.getSharedPreferences(TaskNotificationService.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(TaskNotificationService.ENABLED_PREFERENCE, false)
        if (enabled) {
            runCatching { TaskNotificationService.setEnabled(context, true) }
        }
    }
}
