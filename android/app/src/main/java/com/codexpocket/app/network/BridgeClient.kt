package com.codexpocket.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class BridgeClient(context: Context, private val listener: Listener) {
    interface Listener {
        fun onConnected()
        fun onDisconnected(reason: String?)
        fun onReconnecting(delaySeconds: Int)
        fun onEvent(event: String, data: JSONObject)
        fun onError(message: String)
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        // Detect a half-open socket quickly after Wi-Fi/cellular handovers.
        // Requests are idempotent where retried, so reconnecting is safer than
        // leaving the phone attached to a dead downlink for tens of seconds.
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private data class PendingRequest(
        val envelope: String,
        val retryable: Boolean,
        val timeoutMillis: Long,
        val callback: (Result<JSONObject>) -> Unit,
        @Volatile var needsResend: Boolean = false,
    )

    private val callbacks = ConcurrentHashMap<String, PendingRequest>()
    private val requestTimeouts = ConcurrentHashMap<String, Runnable>()
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val connectivityManager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val underlyingNetworks = linkedMapOf<Network, String>()
    private var networkMonitorRegistered = false
    private var networkSnapshot = ""
    private var networkSnapshotReady = false
    private var lastNetworkRestartAt = 0L
    private var networkReconnectRunnable: Runnable? = null
    private var handoverRecoveryRunnable: Runnable? = null
    private var handoverRecoveryAttempt = 0
    private var reconnectRunnable: Runnable? = null
    private var helloTimeoutRunnable: Runnable? = null
    @Volatile
    private var socket: WebSocket? = null
    private var endpoint: String = ""
    private var token: String = ""
    private var reconnectAttempt = 0
    private var serverInstanceId = ""
    private var lastEventSequence = 0L
    @Volatile
    private var generation = 0
    @Volatile
    private var connectedGeneration = -1
    @Volatile
    private var shouldReconnect = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateUnderlyingNetwork(network, connectivityManager.getNetworkCapabilities(network))
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            updateUnderlyingNetwork(network, capabilities)
        }

        override fun onLost(network: Network) {
            reconnectHandler.post {
                underlyingNetworks.remove(network)
                handleNetworkSnapshotChanged()
            }
        }
    }

    fun connect(endpoint: String, token: String) {
        ensureNetworkMonitor()
        shouldReconnect = true
        this.endpoint = endpoint
        this.token = token
        reconnectAttempt = 0
        generation += 1
        cancelReconnect()
        cancelHelloTimeout()
        cancelHandoverRecovery()
        socket?.cancel()
        openSocket(generation)
    }

    fun disconnect() {
        shouldReconnect = false
        generation += 1
        cancelReconnect()
        cancelHelloTimeout()
        cancelHandoverRecovery()
        socket?.close(1000, "Client disconnect")
        socket = null
        failPending("连接已断开")
    }

    fun close() {
        disconnect()
        cancelNetworkReconnect()
        if (networkMonitorRegistered) {
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
            networkMonitorRegistered = false
        }
        httpClient.connectionPool.evictAll()
    }

    fun request(
        method: String,
        params: JSONObject = JSONObject(),
        callback: (Result<JSONObject>) -> Unit,
    ) {
        val id = UUID.randomUUID().toString()
        val envelope = JSONObject()
            .put("type", "request")
            .put("id", id)
            .put("method", method)
            .put("params", params)
            .toString()
        val pending = PendingRequest(
            envelope = envelope,
            retryable = isRetryableBridgeMethod(method),
            timeoutMillis = requestTimeoutMillis(method),
            callback = callback,
        )
        callbacks[id] = pending
        scheduleRequestTimeout(id, pending)
        if (socket?.send(envelope) != true) {
            if (shouldReconnect && pending.retryable) {
                pending.needsResend = true
            } else if (callbacks.remove(id, pending)) {
                cancelRequestTimeout(id)
                callback(Result.failure(IllegalStateException("连接尚未就绪")))
            }
        }
    }

    private fun openSocket(connectionGeneration: Int) {
        if (!shouldReconnect || endpoint.isBlank()) return
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $token")
            .build()
        cancelHelloTimeout()
        helloTimeoutRunnable = Runnable {
            if (!shouldReconnect || connectionGeneration != generation) return@Runnable
            generation += 1
            val staleSocket = socket
            socket = null
            staleSocket?.cancel()
            preserveRetryablePending("连接握手超时")
            scheduleReconnect("连接握手超时")
        }.also { reconnectHandler.postDelayed(it, HELLO_TIMEOUT_MILLIS) }
        socket = httpClient.newWebSocket(request, SocketListener(connectionGeneration))
    }

    private fun scheduleReconnect(reason: String?) {
        if (!shouldReconnect) return
        val delaySeconds = reconnectDelaySeconds(reconnectAttempt)
        reconnectAttempt += 1
        listener.onDisconnected(reason)
        listener.onReconnecting(delaySeconds)
        val expectedGeneration = generation
        reconnectRunnable = Runnable {
            if (shouldReconnect && expectedGeneration == generation) openSocket(expectedGeneration)
        }.also { reconnectHandler.postDelayed(it, delaySeconds * 1000L) }
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let(reconnectHandler::removeCallbacks)
        reconnectRunnable = null
    }

    private fun cancelHelloTimeout() {
        helloTimeoutRunnable?.let(reconnectHandler::removeCallbacks)
        helloTimeoutRunnable = null
    }

    private fun ensureNetworkMonitor() {
        if (networkMonitorRegistered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Unlike registerNetworkCallback, this follows the best
                // physical (non-VPN) network. That matters on Xiaomi devices
                // which keep Wi-Fi and cellular simultaneously available.
                connectivityManager.registerBestMatchingNetworkCallback(
                    request,
                    networkCallback,
                    reconnectHandler,
                )
            } else {
                connectivityManager.registerNetworkCallback(request, networkCallback)
            }
        }
            .onSuccess { networkMonitorRegistered = true }
    }

    private fun updateUnderlyingNetwork(network: Network, capabilities: NetworkCapabilities?) {
        reconnectHandler.post {
            if (capabilities == null) {
                underlyingNetworks.remove(network)
            } else {
                underlyingNetworks[network] = networkCapabilitySignature(capabilities)
            }
            handleNetworkSnapshotChanged()
        }
    }

    private fun handleNetworkSnapshotChanged() {
        val nextSnapshot = underlyingNetworks.entries
            .sortedBy { it.key.toString() }
            .joinToString("|") { (network, capabilities) -> "$network:$capabilities" }
        if (!networkSnapshotReady) {
            networkSnapshot = nextSnapshot
            networkSnapshotReady = true
            return
        }
        if (!shouldRestartForNetworkChange(networkSnapshot, nextSnapshot, shouldReconnect)) return
        networkSnapshot = nextSnapshot
        cancelNetworkReconnect()
        cancelHandoverRecovery()
        handoverRecoveryAttempt = 0
        networkReconnectRunnable = Runnable { restartAfterNetworkHandover() }
            .also { reconnectHandler.postDelayed(it, NETWORK_HANDOVER_DEBOUNCE_MILLIS) }
    }

    private fun restartAfterNetworkHandover() {
        networkReconnectRunnable = null
        if (!shouldReconnect || endpoint.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        val remaining = NETWORK_RESTART_MIN_INTERVAL_MILLIS - (now - lastNetworkRestartAt)
        if (remaining > 0) {
            networkReconnectRunnable = Runnable { restartAfterNetworkHandover() }
                .also { reconnectHandler.postDelayed(it, remaining) }
            return
        }
        lastNetworkRestartAt = now
        generation += 1
        cancelReconnect()
        cancelHelloTimeout()
        socket?.cancel()
        socket = null
        httpClient.connectionPool.evictAll()
        preserveRetryablePending("网络已切换")
        reconnectAttempt = 0
        listener.onDisconnected("网络已切换")
        listener.onReconnecting(0)
        handoverRecoveryAttempt += 1
        val openedGeneration = generation
        openSocket(openedGeneration)
        if (handoverRecoveryAttempt < MAX_HANDOVER_RECOVERY_ATTEMPTS) {
            scheduleHandoverRecoveryCheck(openedGeneration)
        }
    }

    private fun cancelNetworkReconnect() {
        networkReconnectRunnable?.let(reconnectHandler::removeCallbacks)
        networkReconnectRunnable = null
    }

    private fun scheduleHandoverRecoveryCheck(connectionGeneration: Int) {
        cancelHandoverRecovery()
        handoverRecoveryRunnable = Runnable {
            handoverRecoveryRunnable = null
            if (
                shouldReconnect &&
                connectionGeneration == generation &&
                connectedGeneration != connectionGeneration
            ) {
                // Tailscale often needs a moment to rebuild its userspace VPN
                // route after Wi-Fi/cellular changes. A socket opened during
                // that small gap can remain black-holed without failing fast,
                // so replace it rather than requiring an app restart.
                restartAfterNetworkHandover()
            }
        }.also {
            reconnectHandler.postDelayed(it, HANDOVER_RECOVERY_CHECK_MILLIS)
        }
    }

    private fun cancelHandoverRecovery() {
        handoverRecoveryRunnable?.let(reconnectHandler::removeCallbacks)
        handoverRecoveryRunnable = null
    }

    private fun scheduleRequestTimeout(id: String, pending: PendingRequest) {
        cancelRequestTimeout(id)
        val runnable = Runnable {
            if (callbacks[id] !== pending) return@Runnable
            requestTimeouts.remove(id)
            if (pending.retryable && shouldReconnect) {
                pending.needsResend = true
                socket?.cancel()
            } else if (callbacks.remove(id, pending)) {
                pending.callback(Result.failure(IllegalStateException("发送确认超时，消息可能没有送达")))
            }
        }
        requestTimeouts[id] = runnable
        reconnectHandler.postDelayed(runnable, pending.timeoutMillis)
    }

    private fun cancelRequestTimeout(id: String) {
        requestTimeouts.remove(id)?.let(reconnectHandler::removeCallbacks)
    }

    private inner class SocketListener(private val connectionGeneration: Int) : WebSocketListener() {
        private var terminalHandled = false

        override fun onOpen(webSocket: WebSocket, response: Response) = Unit

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (connectionGeneration != generation) return
            try {
                val message = JSONObject(text)
                when (message.optString("type")) {
                    "hello" -> handleHello(webSocket, message)
                    "response" -> handleResponse(message)
                    "event" -> dispatchEvent(message)
                }
            } catch (error: Exception) {
                listener.onError("消息解析失败：${error.message}")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            handleTerminal(webSocket, reason.ifBlank { null })
        }

        override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
            handleTerminal(webSocket, error.message)
        }

        private fun handleTerminal(webSocket: WebSocket, reason: String?) {
            if (terminalHandled || connectionGeneration != generation) return
            terminalHandled = true
            cancelHelloTimeout()
            if (socket === webSocket) socket = null
            preserveRetryablePending(reason ?: "连接中断")
            scheduleReconnect(reason)
        }
    }

    private fun resendPending(webSocket: WebSocket) {
        callbacks.forEach { (id, pending) ->
            if (pending.retryable && pending.needsResend && webSocket.send(pending.envelope)) {
                pending.needsResend = false
                scheduleRequestTimeout(id, pending)
            }
        }
    }

    private fun handleHello(webSocket: WebSocket, message: JSONObject) {
        cancelHelloTimeout()
        connectedGeneration = generation
        cancelHandoverRecovery()
        reconnectAttempt = 0
        cancelReconnect()
        resendPending(webSocket)
        val nextServerInstanceId = message.optString("serverInstanceId")
        val serverEventSequence = message.optLong("eventSequence")
        val canReplay = serverInstanceId.isNotBlank() && serverInstanceId == nextServerInstanceId &&
            lastEventSequence > 0L && serverEventSequence > lastEventSequence
        if (serverInstanceId != nextServerInstanceId) {
            serverInstanceId = nextServerInstanceId
            lastEventSequence = 0L
        }
        if (!canReplay) {
            listener.onConnected()
            return
        }
        // The socket is usable as soon as the Bridge hello arrives. Do not
        // keep the UI disabled while missed streaming events are replayed over
        // a slow cellular DERP path; MainViewModel immediately refreshes the
        // current thread snapshot and replay continues in the background.
        listener.onConnected()
        request(
            "events.replay",
            JSONObject()
                .put("serverInstanceId", serverInstanceId)
                .put("afterSequence", lastEventSequence)
                .put("maxEvents", MAX_REPLAY_EVENTS),
        ) { result ->
            result.onSuccess { payload ->
                val events = payload.optJSONArray("events")
                if (events != null) {
                    for (index in 0 until events.length()) {
                        events.optJSONObject(index)?.let(::dispatchEvent)
                    }
                }
                lastEventSequence = maxOf(lastEventSequence, payload.optLong("latestSequence"))
            }
        }
    }

    private fun dispatchEvent(message: JSONObject) {
        val sequence = message.optLong("sequence")
        if (sequence > 0L && sequence <= lastEventSequence) return
        if (sequence > 0L) lastEventSequence = sequence
        listener.onEvent(
            message.optString("event"),
            message.optJSONObject("data") ?: JSONObject(),
        )
    }

    private fun networkCapabilitySignature(capabilities: NetworkCapabilities): String = buildString {
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) append("wifi")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) append("cellular")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) append("ethernet")
        append(':')
        append(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
    }

    private fun handleResponse(message: JSONObject) {
        val id = message.optString("id")
        val callback = callbacks.remove(id)?.callback ?: return
        cancelRequestTimeout(id)
        if (message.optBoolean("ok")) {
            callback(Result.success(message.optJSONObject("result") ?: JSONObject()))
        } else {
            callback(Result.failure(IllegalStateException(message.optString("error", "请求失败"))))
        }
    }

    private fun failPending(message: String) {
        val pending = callbacks.entries.map { (id, pending) ->
            cancelRequestTimeout(id)
            pending.callback
        }
        callbacks.clear()
        pending.forEach { callback ->
            callback(Result.failure(IllegalStateException(message)))
        }
    }

    private fun preserveRetryablePending(message: String) {
        callbacks.entries.forEach { (id, pending) ->
            cancelRequestTimeout(id)
            if (pending.retryable) {
                pending.needsResend = true
            } else if (callbacks.remove(id, pending)) {
                pending.callback(Result.failure(IllegalStateException(message)))
            }
        }
    }
}

internal fun shouldRestartForNetworkChange(
    previousSnapshot: String,
    nextSnapshot: String,
    shouldReconnect: Boolean,
): Boolean = shouldReconnect && previousSnapshot != nextSnapshot

internal fun reconnectDelaySeconds(attempt: Int): Int {
    val delays = intArrayOf(1, 1, 2, 2, 3, 5, 8, 10)
    return delays[attempt.coerceIn(0, delays.lastIndex)]
}

internal fun backgroundBridgeEndpoint(endpoint: String): String {
    if (endpoint.isBlank() || endpoint.contains("client=background")) return endpoint
    return endpoint + if ('?' in endpoint) "&client=background" else "?client=background"
}

internal fun isRetryableBridgeMethod(method: String): Boolean = method in setOf(
    "threads.list",
    "thread.read",
    "models.list",
    "modes.list",
    "permissions.list",
    "directories.list",
    "account.status",
    "automations.list",
    "devices.status",
    "cameras.list",
    "events.replay",
    // The Bridge de-duplicates these mutations by clientMessageId, making a
    // reconnect resend safe when the original acknowledgement was lost.
    "turn.start",
    "turn.steer",
)

internal fun requestTimeoutMillis(method: String): Long = when (method) {
    "turn.start", "turn.steer" -> MUTATION_ACK_TIMEOUT_MILLIS
    else -> if (isRetryableBridgeMethod(method)) READ_REQUEST_TIMEOUT_MILLIS
    else MUTATION_ACK_TIMEOUT_MILLIS
}

private const val NETWORK_HANDOVER_DEBOUNCE_MILLIS = 600L
private const val NETWORK_RESTART_MIN_INTERVAL_MILLIS = 2_000L
private const val HANDOVER_RECOVERY_CHECK_MILLIS = 3_500L
private const val MAX_HANDOVER_RECOVERY_ATTEMPTS = 3
private const val HELLO_TIMEOUT_MILLIS = 7_500L
private const val MUTATION_ACK_TIMEOUT_MILLIS = 8_000L
private const val READ_REQUEST_TIMEOUT_MILLIS = 30_000L
private const val MAX_REPLAY_EVENTS = 250
