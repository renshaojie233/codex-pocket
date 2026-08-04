package com.codexpocket.app.notification

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.codexpocket.app.BuildConfig
import com.codexpocket.app.network.BridgeClient
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

class TaskNotificationService : Service(), BridgeClient.Listener {
    private val client by lazy { BridgeClient(this, this) }
    private val threadTitles = ConcurrentHashMap<String, String>()
    private val seenCompletions = LinkedHashSet<String>()

    override fun onCreate() {
        super.onCreate()
        TaskNotifications.createChannels(this)
        ServiceCompat.startForeground(
            this,
            TaskNotifications.LISTENER_NOTIFICATION_ID,
            TaskNotifications.listenerNotification(this, "正在连接 Mac…"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            } else {
                0
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        connectFromPreferences()
        return START_STICKY
    }

    override fun onDestroy() {
        client.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConnected() {
        updateListenerStatus("正在监听 Mac 上的任务")
        refreshThreadTitles()
    }

    override fun onDisconnected(reason: String?) {
        updateListenerStatus("与 Mac 的连接已中断")
    }

    override fun onReconnecting(delaySeconds: Int) {
        updateListenerStatus("连接中断，$delaySeconds 秒后重试")
    }

    override fun onEvent(event: String, data: JSONObject) {
        when (event) {
            "turn.completed" -> handleTurnCompleted(data)
            "thread.catalog" -> refreshThreadTitles()
        }
    }

    override fun onError(message: String) {
        updateListenerStatus("监听异常，正在自动恢复")
    }

    private fun connectFromPreferences() {
        val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        val endpoint = preferences.getString("endpoint", BuildConfig.BRIDGE_ENDPOINT)
            ?: BuildConfig.BRIDGE_ENDPOINT
        val token = preferences.getString("token", BuildConfig.BRIDGE_TOKEN)
            ?: BuildConfig.BRIDGE_TOKEN
        if (endpoint.isBlank() || token.isBlank()) {
            updateListenerStatus("等待完成 Mac 连接设置")
            return
        }
        updateListenerStatus("正在连接 Mac…")
        client.connect(endpoint, token)
    }

    private fun handleTurnCompleted(data: JSONObject) {
        val threadId = data.optString("threadId")
        if (threadId.isBlank()) return
        val turnId = data.optJSONObject("turn")?.optString("id").orEmpty()
        val completionKey = turnId.ifBlank { "$threadId:${System.currentTimeMillis() / 1000}" }
        synchronized(seenCompletions) {
            if (!seenCompletions.add(completionKey)) return
            while (seenCompletions.size > 128) {
                seenCompletions.remove(seenCompletions.first())
            }
        }
        refreshThreadTitles {
            TaskNotifications.showCompletion(
                this,
                threadId,
                threadTitles[threadId].orEmpty().ifBlank { "Codex 任务" },
            )
        }
    }

    private fun refreshThreadTitles(onReady: (() -> Unit)? = null) {
        client.request("threads.list", JSONObject().put("limit", 100)) { result ->
            result.onSuccess { payload ->
                val threads = payload.optJSONArray("threads") ?: JSONArray()
                for (index in 0 until threads.length()) {
                    val thread = threads.optJSONObject(index) ?: continue
                    val id = thread.optString("id")
                    if (id.isNotBlank()) threadTitles[id] = thread.optString("title")
                }
            }
            onReady?.invoke()
        }
    }

    private fun updateListenerStatus(status: String) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(this).notify(
            TaskNotifications.LISTENER_NOTIFICATION_ID,
            TaskNotifications.listenerNotification(this, status),
        )
    }

    companion object {
        private const val ACTION_START = "com.codexpocket.app.notification.START"
        private const val ACTION_STOP = "com.codexpocket.app.notification.STOP"
        const val PREFERENCES_NAME = "codex-pocket"
        const val ENABLED_PREFERENCE = "completion-notifications"

        fun setEnabled(context: Context, enabled: Boolean) {
            val appContext = context.applicationContext
            if (enabled) {
                ContextCompat.startForegroundService(
                    appContext,
                    Intent(appContext, TaskNotificationService::class.java).setAction(ACTION_START),
                )
            } else {
                val serviceIntent = Intent(appContext, TaskNotificationService::class.java)
                appContext.stopService(serviceIntent)
            }
        }

        fun sendTest(context: Context): Boolean {
            return TaskNotifications.showCompletion(
                context.applicationContext,
                "notification-test-${System.currentTimeMillis()}",
                "测试成功：提示音和震动工作正常",
            )
        }
    }
}
