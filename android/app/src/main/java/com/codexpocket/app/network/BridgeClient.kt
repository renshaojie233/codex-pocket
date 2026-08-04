package com.codexpocket.app.network

import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class BridgeClient(private val listener: Listener) {
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
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val callbacks = ConcurrentHashMap<String, (Result<JSONObject>) -> Unit>()
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private var socket: WebSocket? = null
    private var endpoint: String = ""
    private var token: String = ""
    private var reconnectAttempt = 0
    private var generation = 0
    private var shouldReconnect = false

    fun connect(endpoint: String, token: String) {
        shouldReconnect = true
        this.endpoint = endpoint
        this.token = token
        reconnectAttempt = 0
        generation += 1
        cancelReconnect()
        socket?.cancel()
        openSocket(generation)
    }

    fun disconnect() {
        shouldReconnect = false
        generation += 1
        cancelReconnect()
        socket?.close(1000, "Client disconnect")
        socket = null
        failPending("连接已断开")
    }

    fun request(
        method: String,
        params: JSONObject = JSONObject(),
        callback: (Result<JSONObject>) -> Unit,
    ) {
        val id = UUID.randomUUID().toString()
        callbacks[id] = callback
        val envelope = JSONObject()
            .put("type", "request")
            .put("id", id)
            .put("method", method)
            .put("params", params)
        if (socket?.send(envelope.toString()) != true) {
            callbacks.remove(id)
            callback(Result.failure(IllegalStateException("连接尚未就绪")))
        }
    }

    private fun openSocket(connectionGeneration: Int) {
        if (!shouldReconnect || endpoint.isBlank()) return
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $token")
            .build()
        socket = httpClient.newWebSocket(request, SocketListener(connectionGeneration))
    }

    private fun scheduleReconnect(reason: String?) {
        if (!shouldReconnect) return
        val delays = intArrayOf(1, 2, 5, 10, 20, 30)
        val delaySeconds = delays[reconnectAttempt.coerceAtMost(delays.lastIndex)]
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

    private inner class SocketListener(private val connectionGeneration: Int) : WebSocketListener() {
        private var terminalHandled = false

        override fun onOpen(webSocket: WebSocket, response: Response) = Unit

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (connectionGeneration != generation) return
            try {
                val message = JSONObject(text)
                when (message.optString("type")) {
                    "hello" -> {
                        reconnectAttempt = 0
                        cancelReconnect()
                        listener.onConnected()
                    }
                    "response" -> handleResponse(message)
                    "event" -> listener.onEvent(
                        message.optString("event"),
                        message.optJSONObject("data") ?: JSONObject(),
                    )
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
            if (socket === webSocket) socket = null
            failPending(reason ?: "连接中断")
            scheduleReconnect(reason)
        }
    }

    private fun handleResponse(message: JSONObject) {
        val id = message.optString("id")
        val callback = callbacks.remove(id) ?: return
        if (message.optBoolean("ok")) {
            callback(Result.success(message.optJSONObject("result") ?: JSONObject()))
        } else {
            callback(Result.failure(IllegalStateException(message.optString("error", "请求失败"))))
        }
    }

    private fun failPending(message: String) {
        val pending = callbacks.values.toList()
        callbacks.clear()
        pending.forEach { callback ->
            callback(Result.failure(IllegalStateException(message)))
        }
    }
}
