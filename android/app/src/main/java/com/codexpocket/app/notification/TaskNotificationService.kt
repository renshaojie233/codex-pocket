package com.codexpocket.app.notification

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.codexpocket.app.BuildConfig
import com.codexpocket.app.cache.MessageCacheStore
import com.codexpocket.app.model.parseChatMessages
import com.codexpocket.app.network.BridgeClient
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

class TaskNotificationService : Service(), BridgeClient.Listener {
    private val client by lazy { BridgeClient(this, this) }
    private val messageCache by lazy { MessageCacheStore(cacheDir) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val threadTitles = ConcurrentHashMap<String, String>()
    private val seenCompletions = LinkedHashSet<String>()
    private val scheduledSyncs = mutableMapOf<String, Runnable>()
    private val syncingThreads = mutableSetOf<String>()
    private val resyncAfterFlight = mutableSetOf<String>()
    private var destroyed = false

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
        connectFromPreferences()
        return START_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        scheduledSyncs.values.forEach(mainHandler::removeCallbacks)
        scheduledSyncs.clear()
        client.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConnected() {
        updateListenerStatus("正在同步最近消息…")
        refreshThreadCatalog(prefetchRecent = true)
    }

    override fun onDisconnected(reason: String?) {
        updateListenerStatus("网络已切换，正在恢复同步…")
    }

    override fun onReconnecting(delaySeconds: Int) {
        updateListenerStatus(if (delaySeconds <= 0) "正在适应新网络…" else "连接中断，$delaySeconds 秒后重试")
    }

    override fun onEvent(event: String, data: JSONObject) {
        val threadId = data.optString("threadId")
        if (threadId.isNotBlank() && event in THREAD_SYNC_EVENTS) {
            scheduleThreadSync(
                threadId,
                if (event == "turn.completed" || event == "item.completed") {
                    FINAL_EVENT_SYNC_DELAY_MILLIS
                } else {
                    STREAM_EVENT_SYNC_DELAY_MILLIS
                },
            )
        }
        when (event) {
            "turn.completed" -> handleTurnCompleted(data)
            "thread.catalog" -> {
                if (data.optString("action") in setOf("archived", "deleted")) {
                    threadId.takeIf(String::isNotBlank)?.let(messageCache::remove)
                }
                refreshThreadCatalog(prefetchRecent = false)
            }
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
        val notificationsEnabled = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getBoolean(ENABLED_PREFERENCE, false)
        if (!notificationsEnabled) return
        val turnId = data.optJSONObject("turn")?.optString("id").orEmpty()
        val completionKey = turnId.ifBlank { "$threadId:${System.currentTimeMillis() / 1000}" }
        synchronized(seenCompletions) {
            if (!seenCompletions.add(completionKey)) return
            while (seenCompletions.size > 128) {
                seenCompletions.remove(seenCompletions.first())
            }
        }
        refreshThreadCatalog(prefetchRecent = false) {
            TaskNotifications.showCompletion(
                this,
                threadId,
                threadTitles[threadId].orEmpty().ifBlank { "Codex 任务" },
            )
        }
    }

    private fun refreshThreadCatalog(
        prefetchRecent: Boolean,
        onReady: (() -> Unit)? = null,
    ) {
        client.request("threads.list", JSONObject().put("limit", 100)) { result ->
            result.onSuccess { payload ->
                val threads = payload.optJSONArray("threads") ?: JSONArray()
                val prefetchIds = LinkedHashSet<String>()
                for (index in 0 until threads.length()) {
                    val thread = threads.optJSONObject(index) ?: continue
                    val id = thread.optString("id")
                    if (id.isBlank()) continue
                    threadTitles[id] = thread.optString("title")
                    if (
                        prefetchRecent &&
                        (index < RECENT_THREAD_PREFETCH_COUNT || thread.optString("status") == "active")
                    ) {
                        prefetchIds += id
                    }
                }
                prefetchIds.forEachIndexed { index, id ->
                    scheduleThreadSync(id, INITIAL_PREFETCH_DELAY_MILLIS + index * PREFETCH_STAGGER_MILLIS)
                }
            }
            onReady?.invoke()
        }
    }

    private fun scheduleThreadSync(threadId: String, delayMillis: Long) {
        if (threadId.isBlank() || destroyed) return
        mainHandler.post {
            if (destroyed) return@post
            if (threadId in syncingThreads) {
                resyncAfterFlight += threadId
                return@post
            }
            scheduledSyncs.remove(threadId)?.let(mainHandler::removeCallbacks)
            val runnable = Runnable {
                scheduledSyncs.remove(threadId)
                syncThread(threadId)
            }
            scheduledSyncs[threadId] = runnable
            mainHandler.postDelayed(runnable, delayMillis.coerceAtLeast(0L))
        }
    }

    private fun syncThread(threadId: String) {
        if (destroyed || threadId in syncingThreads) return
        if (syncingThreads.size >= MAX_CONCURRENT_SYNCS) {
            scheduleThreadSync(threadId, SYNC_QUEUE_RETRY_MILLIS)
            return
        }
        syncingThreads += threadId
        updateSyncStatus()
        client.request(
            "thread.read",
            JSONObject()
                .put("threadId", threadId)
                .put("messageLimit", MessageCacheStore.LATEST_SYNC_MESSAGE_COUNT),
        ) { result ->
            result.onSuccess { payload ->
                val messages = parseChatMessages(payload.optJSONArray("messages") ?: JSONArray())
                if (messages.isNotEmpty()) {
                    val discardedIds = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
                        .getStringSet(DISCARDED_LOCAL_MESSAGES_PREFERENCE, emptySet())
                        .orEmpty()
                    messageCache.write(threadId, messages, discardedIds)
                }
            }
            mainHandler.post {
                syncingThreads -= threadId
                val shouldRunAgain = resyncAfterFlight.remove(threadId)
                updateSyncStatus()
                if (shouldRunAgain) scheduleThreadSync(threadId, RESYNC_AFTER_FLIGHT_DELAY_MILLIS)
            }
        }
    }

    private fun updateSyncStatus() {
        updateListenerStatus(
            if (syncingThreads.isEmpty()) "消息已在后台自动同步" else "正在同步 ${syncingThreads.size} 个任务…",
        )
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
        const val PREFERENCES_NAME = "codex-pocket"
        const val ENABLED_PREFERENCE = "completion-notifications"
        private const val DISCARDED_LOCAL_MESSAGES_PREFERENCE = "discarded-local-message-ids"
        private const val RECENT_THREAD_PREFETCH_COUNT = 12
        private const val MAX_CONCURRENT_SYNCS = 2
        private const val INITIAL_PREFETCH_DELAY_MILLIS = 100L
        private const val PREFETCH_STAGGER_MILLIS = 180L
        private const val SYNC_QUEUE_RETRY_MILLIS = 300L
        private const val STREAM_EVENT_SYNC_DELAY_MILLIS = 1_200L
        private const val FINAL_EVENT_SYNC_DELAY_MILLIS = 120L
        private const val RESYNC_AFTER_FLIGHT_DELAY_MILLIS = 250L
        private val THREAD_SYNC_EVENTS = setOf(
            "agent.delta",
            "reasoning.delta",
            "plan.delta",
            "tool.progress",
            "tool.output",
            "item.started",
            "item.completed",
            "turn.started",
            "turn.completed",
            "thread.status",
            "thread.settings",
            "thread.goal",
            "thread.goal.cleared",
        )

        fun ensureRunning(context: Context) {
            val appContext = context.applicationContext
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, TaskNotificationService::class.java).setAction(ACTION_START),
            )
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
