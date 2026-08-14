package com.codexpocket.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.graphics.drawable.GradientDrawable
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * Keeps noVNC as the protocol decoder, but moves the final frame composition
 * out of WebView. Xiaomi/HyperOS can read correct Canvas pixels while drawing
 * the same Canvas (and even a Blob-backed image) as black. A native ImageView
 * is not affected by that compositor bug.
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
internal class NativeRemoteDesktopView(
    context: Context,
    private val expectedOrigin: String,
    private val onFullscreenRequest: (Boolean) -> Unit,
) : FrameLayout(context) {
    private val expectedHost = Uri.parse(expectedOrigin).host.orEmpty()
    private val webView = WebView(context)
    private val frameView = ImageView(context)
    private val statusView = TextView(context)
    private val inputPanel = LinearLayout(context)
    private val input = EditText(context)
    private val preferences = context.getSharedPreferences(QUALITY_PREFERENCES, Context.MODE_PRIVATE)
    private lateinit var displayButton: TextView
    private lateinit var floatingControls: LinearLayout
    private lateinit var floatingToggle: TextView
    private val floatingActionButtons = mutableListOf<View>()
    private val nativeBridge = NativeFrameBridge()
    private var currentUrl = ""
    private var currentBitmap: Bitmap? = null
    private var decodedFrameReported = false
    private var qualityMode = preferences.getString(QUALITY_MODE_KEY, QUALITY_AUTO)
        ?.takeIf(QUALITY_MODES::contains) ?: QUALITY_AUTO
    private var frameMode = preferences.getString(FRAME_MODE_KEY, FRAME_SMART)
        ?.takeIf(FRAME_MODES::contains) ?: FRAME_SMART
    private var floatingControlsExpanded = false
    private var fullscreen = false
    private var active = true

    init {
        setBackgroundColor(Color.rgb(17, 18, 23))
        keepScreenOn = true
        configureWebView()
        addView(
            webView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        frameView.apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "远程桌面画面"
            isClickable = false
            isFocusable = false
        }
        addView(
            frameView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        configureStatus()
        configureInputPanel()
        configureFloatingControls()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.apply {
            setBackgroundColor(Color.rgb(17, 18, 23))
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isFocusable = true
            isFocusableInTouchMode = true
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = request.url.host != expectedHost

                override fun onPageFinished(view: WebView, url: String) {
                    applyDisplaySettings()
                }
            }
            addJavascriptInterface(nativeBridge, NATIVE_FRAME_INTERFACE)
            configureBinaryFrameChannel()
        }
    }

    private fun configureBinaryFrameChannel() {
        if (
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)
        ) {
            return
        }
        WebViewCompat.addWebMessageListener(
            webView,
            BINARY_FRAME_INTERFACE,
            setOf(expectedOrigin),
        ) { _, message, _, isMainFrame, _ ->
            if (
                active &&
                isMainFrame &&
                message.type == WebMessageCompat.TYPE_ARRAY_BUFFER
            ) {
                nativeBridge.onBinaryFrame(message.arrayBuffer)
            }
        }
    }

    private fun configureStatus() {
        statusView.apply {
            text = "正在建立安全通道…"
            setTextColor(Color.rgb(216, 216, 229))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(13), dp(8), dp(13), dp(8))
            background = roundedBackground(Color.argb(230, 25, 26, 34), dp(18).toFloat())
        }
        addView(
            statusView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                .apply { topMargin = dp(10) },
        )
    }

    private fun configureInputPanel() {
        inputPanel.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(8), dp(9), dp(8))
            background = roundedBackground(Color.argb(245, 35, 36, 45), dp(17).toFloat())
            visibility = View.GONE
        }
        input.apply {
            hint = "输入到远程电脑…"
            setHintTextColor(Color.rgb(156, 157, 170))
            setTextColor(Color.WHITE)
            textSize = 15f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(12), 0, dp(12), 0)
            background = roundedBackground(Color.rgb(48, 49, 59), dp(12).toFloat())
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendInputText()
                    true
                } else {
                    false
                }
            }
        }
        inputPanel.addView(
            input,
            LinearLayout.LayoutParams(0, dp(46), 1f),
        )
        inputPanel.addView(nativeButton("发送", dp(58)) { sendInputText() })
        addView(
            inputPanel,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
                .apply {
                    marginStart = dp(10)
                    marginEnd = dp(10)
                    bottomMargin = dp(68)
                },
        )
    }

    private fun configureFloatingControls() {
        floatingControls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = roundedBackground(Color.argb(238, 24, 25, 32), dp(18).toFloat())
            elevation = dp(12).toFloat()
        }
        floatingToggle = nativeButton("•••", dp(46)) { toggleFloatingControls() }.apply {
            contentDescription = "展开远程控制工具"
        }
        floatingControls.addView(floatingToggle)

        val keyboardButton = nativeButton("键盘", dp(64)) {
            toggleKeyboard()
            setFloatingControlsExpanded(false)
        }
        val cadButton = nativeButton("CAD", dp(57)) {
                evaluate("window.codexPocketNativeCtrlAltDelete?.()")
                setFloatingControlsExpanded(false)
            }
        displayButton = nativeButton("画面", dp(64)) {
            showDisplayMenu(displayButton)
        }
        val reconnectButton = nativeButton("重连", dp(64)) {
            reconnect()
            setFloatingControlsExpanded(false)
        }
        floatingActionButtons += listOf(keyboardButton, cadButton, displayButton, reconnectButton)
        floatingActionButtons.forEach(floatingControls::addView)
        attachFloatingDragGesture()
        setFloatingControlsExpanded(false)
        addView(
            floatingControls,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
                .apply {
                    marginEnd = dp(14)
                    bottomMargin = dp(14)
                },
        )
    }

    private fun toggleFloatingControls() {
        setFloatingControlsExpanded(!floatingControlsExpanded)
    }

    private fun setFloatingControlsExpanded(expanded: Boolean) {
        floatingControlsExpanded = expanded
        floatingActionButtons.forEach { it.visibility = if (expanded) View.VISIBLE else View.GONE }
        if (::floatingToggle.isInitialized) {
            floatingToggle.text = if (expanded) "收起" else "•••"
            floatingToggle.contentDescription = if (expanded) "收起远程控制工具" else "展开远程控制工具"
        }
        if (::floatingControls.isInitialized) floatingControls.post(::clampFloatingControls)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachFloatingDragGesture() {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false
        floatingToggle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = floatingControls.x
                    startY = floatingControls.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!dragging && (kotlin.math.abs(deltaX) > touchSlop || kotlin.math.abs(deltaY) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        floatingControls.x = startX + deltaX
                        floatingControls.y = startY + deltaY
                        clampFloatingControls()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun clampFloatingControls() {
        if (width <= 0 || height <= 0 || floatingControls.width <= 0 || floatingControls.height <= 0) return
        floatingControls.x = floatingControls.x.coerceIn(0f, (width - floatingControls.width).coerceAtLeast(0).toFloat())
        floatingControls.y = floatingControls.y.coerceIn(0f, (height - floatingControls.height).coerceAtLeast(0).toFloat())
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (::floatingControls.isInitialized) floatingControls.post(::clampFloatingControls)
    }

    private fun nativeButton(label: String, width: Int, action: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextColor(Color.rgb(242, 242, 247))
            textSize = 14f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            background = roundedBackground(Color.argb(28, 255, 255, 255), dp(12).toFloat())
            layoutParams = LinearLayout.LayoutParams(width, dp(41)).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
        }

    private fun toggleKeyboard() {
        val opening = inputPanel.visibility != View.VISIBLE
        inputPanel.visibility = if (opening) View.VISIBLE else View.GONE
        val keyboard = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (opening) {
            input.requestFocus()
            keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        } else {
            keyboard.hideSoftInputFromWindow(input.windowToken, 0)
            webView.requestFocus()
        }
    }

    private fun sendInputText() {
        val text = input.text?.toString().orEmpty()
        if (text.isBlank()) return
        evaluate("window.codexPocketNativeSendText?.(${JSONObject.quote(text)})")
        input.text?.clear()
    }

    private fun showDisplayMenu(anchor: View) {
        PopupMenu(context, anchor).apply {
            QUALITY_OPTIONS.forEachIndexed { index, (mode, label) ->
                menu.add(QUALITY_MENU_GROUP, QUALITY_MENU_ID_BASE + index, index, "清晰度 · $label").apply {
                    isCheckable = true
                    isChecked = qualityMode == mode
                }
            }
            FRAME_OPTIONS.forEachIndexed { index, (mode, label) ->
                menu.add(
                    FRAME_MENU_GROUP,
                    FRAME_MENU_ID_BASE + index,
                    QUALITY_OPTIONS.size + index,
                    "刷新 · $label",
                ).apply {
                    isCheckable = true
                    isChecked = frameMode == mode
                }
            }
            menu.add(
                DISPLAY_MENU_GROUP,
                FULLSCREEN_MENU_ID,
                QUALITY_OPTIONS.size + FRAME_OPTIONS.size,
                if (fullscreen) "退出全屏" else "全屏显示",
            ).apply {
                isCheckable = true
                isChecked = fullscreen
            }
            menu.setGroupCheckable(QUALITY_MENU_GROUP, true, true)
            menu.setGroupCheckable(FRAME_MENU_GROUP, true, true)
            setOnMenuItemClickListener { item ->
                when (item.groupId) {
                    QUALITY_MENU_GROUP -> QUALITY_OPTIONS.getOrNull(item.itemId - QUALITY_MENU_ID_BASE)
                        ?.let { setQualityMode(it.first) }
                    FRAME_MENU_GROUP -> FRAME_OPTIONS.getOrNull(item.itemId - FRAME_MENU_ID_BASE)
                        ?.let { setFrameMode(it.first) }
                    DISPLAY_MENU_GROUP -> if (item.itemId == FULLSCREEN_MENU_ID) {
                        fullscreen = !fullscreen
                        onFullscreenRequest(fullscreen)
                    }
                    else -> return@setOnMenuItemClickListener false
                }
                setFloatingControlsExpanded(false)
                true
            }
            show()
        }
    }

    private fun setQualityMode(mode: String) {
        if (mode !in QUALITY_MODES) return
        qualityMode = mode
        preferences.edit().putString(QUALITY_MODE_KEY, mode).apply()
        applyDisplaySettings()
    }

    private fun setFrameMode(mode: String) {
        if (mode !in FRAME_MODES) return
        frameMode = mode
        preferences.edit().putString(FRAME_MODE_KEY, mode).apply()
        applyDisplaySettings()
    }

    private fun applyDisplaySettings() {
        evaluate(
            "window.codexPocketNativeSetQuality?.(${JSONObject.quote(qualityMode)});" +
                "window.codexPocketNativeSetFrameMode?.(${JSONObject.quote(frameMode)})",
        )
    }

    fun load(url: String) {
        if (!active) return
        currentUrl = url
        statusView.text = "正在建立安全通道…"
        webView.loadUrl(url)
    }

    fun reconnect() {
        if (!active) return
        statusView.text = "正在重新连接…"
        if (currentUrl.isBlank()) return
        webView.loadUrl(currentUrl)
    }

    fun syncFullscreen(value: Boolean) {
        fullscreen = value
    }

    fun onResumeRemote() {
        if (!active) return
        webView.onResume()
        evaluate("window.codexPocketRemoteReconnect?.()")
    }

    fun onPauseRemote() {
        if (!active) return
        evaluate("window.codexPocketRemoteDisconnect?.()")
        webView.onPause()
    }

    fun destroyRemote() {
        if (!active) return
        active = false
        nativeBridge.close()
        if (
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)
        ) {
            WebViewCompat.removeWebMessageListener(webView, BINARY_FRAME_INTERFACE)
        }
        webView.removeJavascriptInterface(NATIVE_FRAME_INTERFACE)
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.destroy()
        frameView.setImageDrawable(null)
        currentBitmap = null
    }

    private fun evaluate(script: String) {
        webView.evaluateJavascript(script, null)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private inner class NativeFrameBridge {
        private val decoding = AtomicBoolean(false)
        private val decoderExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "codex-pocket-frame-decoder").apply { isDaemon = true }
        }
        @Volatile private var enabled = true

        @JavascriptInterface
        fun canAcceptFrame(): Boolean = enabled && !decoding.get()

        @JavascriptInterface
        fun onFrame(
            encoded: String,
            frameWidth: Int,
            frameHeight: Int,
            @Suppress("UNUSED_PARAMETER") desktopWidth: Int,
            @Suppress("UNUSED_PARAMETER") desktopHeight: Int,
        ): Boolean {
            if (!enabled || frameWidth <= 0 || frameHeight <= 0 || !decoding.compareAndSet(false, true)) {
                return false
            }
            try {
                decoderExecutor.execute {
                    try {
                        val comma = encoded.indexOf(',')
                        val payload = if (comma >= 0) encoded.substring(comma + 1) else encoded
                        val bytes = Base64.decode(payload, Base64.DEFAULT)
                        decodeAndDisplay(
                            bytes,
                            FramePatch(frameWidth, frameHeight, 0, 0, frameWidth, frameHeight),
                        )
                    } catch (error: RuntimeException) {
                        frameFailure()
                    }
                }
                return true
            } catch (error: RuntimeException) {
                frameFailure()
                return false
            }
        }

        fun onBinaryFrame(packet: ByteArray) {
            if (!enabled || packet.size <= BINARY_FRAME_HEADER_SIZE || packet.size > MAX_FRAME_PACKET_SIZE) return
            if (!decoding.compareAndSet(false, true)) return
            try {
                val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
                if (buffer.int != BINARY_FRAME_MAGIC || buffer.get().toInt() != BINARY_FRAME_VERSION) {
                    decoding.set(false)
                    return
                }
                buffer.get() // flags, reserved for future codecs
                buffer.short
                val frameWidth = buffer.int
                val frameHeight = buffer.int
                val patchX = buffer.int
                val patchY = buffer.int
                val patchWidth = buffer.int
                val patchHeight = buffer.int
                buffer.int // source desktop width
                buffer.int // source desktop height
                buffer.int // sequence number
                val patch = FramePatch(frameWidth, frameHeight, patchX, patchY, patchWidth, patchHeight)
                if (!patch.isValid() || buffer.remaining() <= 0) {
                    decoding.set(false)
                    return
                }
                val encoded = ByteArray(buffer.remaining())
                buffer.get(encoded)
                decoderExecutor.execute {
                    try {
                        decodeAndDisplay(encoded, patch)
                    } catch (error: RuntimeException) {
                        frameFailure()
                    }
                }
            } catch (error: RuntimeException) {
                frameFailure()
            }
        }

        private fun decodeAndDisplay(encoded: ByteArray, patch: FramePatch) {
            val bitmap = BitmapFactory.decodeByteArray(
                encoded,
                0,
                encoded.size,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = true
                },
            )
            if (bitmap == null || bitmap.width != patch.patchWidth || bitmap.height != patch.patchHeight) {
                bitmap?.recycle()
                frameFailure()
                return
            }
            val posted = frameView.post { displayDecodedPatch(bitmap, patch) }
            if (!posted) {
                bitmap.recycle()
                decoding.set(false)
            }
        }

        private fun displayDecodedPatch(bitmap: Bitmap, patch: FramePatch) {
            try {
                if (!active || !enabled) {
                    bitmap.recycle()
                    return
                }
                val previous = currentBitmap
                val fullFrame = patch.isFullFrame()
                val displayed = if (fullFrame) {
                    currentBitmap = bitmap
                    frameView.setImageBitmap(bitmap)
                    bitmap
                } else if (
                    previous != null &&
                    !previous.isRecycled &&
                    previous.isMutable &&
                    previous.width == patch.frameWidth &&
                    previous.height == patch.frameHeight
                ) {
                    Canvas(previous).drawBitmap(bitmap, patch.patchX.toFloat(), patch.patchY.toFloat(), null)
                    bitmap.recycle()
                    frameView.invalidate()
                    previous
                } else {
                    bitmap.recycle()
                    evaluate("window.codexPocketRequestFullFrame?.()")
                    return
                }
                statusView.visibility = View.GONE
                if (!decodedFrameReported) {
                    decodedFrameReported = true
                    evaluate(
                        "window.codexPocketNativeFrameDisplayed?.(${displayed.width},${displayed.height})",
                    )
                }
                if (fullFrame && previous != null && previous !== displayed) {
                    frameView.postDelayed({
                        if (previous !== currentBitmap && !previous.isRecycled) previous.recycle()
                    }, OLD_FRAME_RECYCLE_DELAY_MILLIS)
                }
            } finally {
                // Do not create a queue: the next frame is accepted only after this
                // patch has reached the ImageView on the UI thread.
                decoding.set(false)
            }
        }

        private fun frameFailure() {
            decoding.set(false)
            statusView.post {
                if (!active || !enabled) return@post
                statusView.text = "原生画面解码失败，正在重试…"
                statusView.visibility = View.VISIBLE
                evaluate("window.codexPocketRequestFullFrame?.()")
            }
        }

        @JavascriptInterface
        fun onStatus(message: String, connected: Boolean) {
            statusView.post {
                if (!active || !enabled) return@post
                statusView.text = message.take(80)
                statusView.setTextColor(
                    if (connected) Color.rgb(169, 242, 204) else Color.rgb(216, 216, 229),
                )
                if (!connected) statusView.visibility = View.VISIBLE
                if (connected) applyDisplaySettings()
            }
        }

        fun close() {
            enabled = false
            decoderExecutor.shutdownNow()
        }
    }

    private data class FramePatch(
        val frameWidth: Int,
        val frameHeight: Int,
        val patchX: Int,
        val patchY: Int,
        val patchWidth: Int,
        val patchHeight: Int,
    ) {
        fun isValid(): Boolean =
            frameWidth in 1..MAX_FRAME_DIMENSION &&
                frameHeight in 1..MAX_FRAME_DIMENSION &&
                patchX >= 0 && patchY >= 0 && patchWidth > 0 && patchHeight > 0 &&
                patchX.toLong() + patchWidth <= frameWidth.toLong() &&
                patchY.toLong() + patchHeight <= frameHeight.toLong()

        fun isFullFrame(): Boolean =
            patchX == 0 && patchY == 0 && patchWidth == frameWidth && patchHeight == frameHeight
    }

    private companion object {
        const val NATIVE_FRAME_INTERFACE = "CodexPocketNativeFrame"
        const val BINARY_FRAME_INTERFACE = "CodexPocketBinaryFrame"
        const val BINARY_FRAME_HEADER_SIZE = 44
        const val BINARY_FRAME_MAGIC = 0x43505246
        const val BINARY_FRAME_VERSION = 1
        const val MAX_FRAME_PACKET_SIZE = 24 * 1024 * 1024
        const val MAX_FRAME_DIMENSION = 8192
        const val OLD_FRAME_RECYCLE_DELAY_MILLIS = 200L
        const val QUALITY_PREFERENCES = "remote-desktop"
        const val QUALITY_MODE_KEY = "quality-mode"
        const val FRAME_MODE_KEY = "frame-mode"
        const val QUALITY_AUTO = "auto"
        const val QUALITY_SMOOTH = "smooth"
        const val QUALITY_HIGH = "high"
        const val QUALITY_ORIGINAL = "original"
        const val FRAME_SMART = "smart"
        const val FRAME_LOW_LATENCY = "low-latency"
        const val FRAME_BALANCED = "balanced"
        const val FRAME_POWER_SAVE = "power-save"
        const val QUALITY_MENU_GROUP = 1
        const val FRAME_MENU_GROUP = 2
        const val DISPLAY_MENU_GROUP = 3
        const val QUALITY_MENU_ID_BASE = 100
        const val FRAME_MENU_ID_BASE = 200
        const val FULLSCREEN_MENU_ID = 300
        val QUALITY_MODES = setOf(QUALITY_AUTO, QUALITY_SMOOTH, QUALITY_HIGH, QUALITY_ORIGINAL)
        val FRAME_MODES = setOf(FRAME_SMART, FRAME_LOW_LATENCY, FRAME_BALANCED, FRAME_POWER_SAVE)
        val QUALITY_OPTIONS = listOf(
            QUALITY_AUTO to "自动（按屏幕）",
            QUALITY_SMOOTH to "流畅 960 × 540",
            QUALITY_HIGH to "高清 1280 × 720",
            QUALITY_ORIGINAL to "原始 1920 × 1080",
        )
        val FRAME_OPTIONS = listOf(
            FRAME_SMART to "智能（操作 30 帧）",
            FRAME_LOW_LATENCY to "极速 30 帧",
            FRAME_BALANCED to "均衡 10 帧",
            FRAME_POWER_SAVE to "省电 4 帧",
        )
    }
}
