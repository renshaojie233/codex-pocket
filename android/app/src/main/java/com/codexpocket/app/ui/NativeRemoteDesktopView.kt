package com.codexpocket.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
    private val expectedHost: String,
) : FrameLayout(context) {
    private val webView = WebView(context)
    private val frameView = ImageView(context)
    private val statusView = TextView(context)
    private val inputPanel = LinearLayout(context)
    private val input = EditText(context)
    private val preferences = context.getSharedPreferences(QUALITY_PREFERENCES, Context.MODE_PRIVATE)
    private lateinit var qualityButton: TextView
    private lateinit var floatingControls: LinearLayout
    private lateinit var floatingToggle: TextView
    private val floatingActionButtons = mutableListOf<View>()
    private val nativeBridge = NativeFrameBridge()
    private var currentUrl = ""
    private var currentBitmap: Bitmap? = null
    private var decodedFrameReported = false
    private var qualityMode = preferences.getString(QUALITY_MODE_KEY, QUALITY_AUTO)
        ?.takeIf(QUALITY_MODES::contains) ?: QUALITY_AUTO
    private var floatingControlsExpanded = false
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
                    applyQualityMode()
                }
            }
            addJavascriptInterface(nativeBridge, NATIVE_FRAME_INTERFACE)
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
        qualityButton = nativeButton(qualityButtonLabel(), dp(64)) {
            showQualityMenu(qualityButton)
        }
        val reconnectButton = nativeButton("重连", dp(64)) {
            reconnect()
            setFloatingControlsExpanded(false)
        }
        floatingActionButtons += listOf(keyboardButton, cadButton, qualityButton, reconnectButton)
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

    private fun showQualityMenu(anchor: View) {
        PopupMenu(context, anchor).apply {
            QUALITY_OPTIONS.forEach { (mode, label) ->
                menu.add(label).apply {
                    isCheckable = true
                    isChecked = qualityMode == mode
                }
            }
            setOnMenuItemClickListener { item ->
                val selected = QUALITY_OPTIONS.firstOrNull { it.second == item.title.toString() }
                    ?: return@setOnMenuItemClickListener false
                setQualityMode(selected.first)
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
        if (::qualityButton.isInitialized) qualityButton.text = qualityButtonLabel()
        applyQualityMode()
    }

    private fun qualityButtonLabel(): String = when (qualityMode) {
        QUALITY_SMOOTH -> "流畅"
        QUALITY_HIGH -> "高清"
        QUALITY_ORIGINAL -> "原始"
        else -> "自动"
    }

    private fun applyQualityMode() {
        evaluate("window.codexPocketNativeSetQuality?.(${JSONObject.quote(qualityMode)})")
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
        @Volatile private var enabled = true

        @JavascriptInterface
        fun onFrame(
            encoded: String,
            frameWidth: Int,
            frameHeight: Int,
            @Suppress("UNUSED_PARAMETER") desktopWidth: Int,
            @Suppress("UNUSED_PARAMETER") desktopHeight: Int,
        ) {
            if (!enabled || frameWidth <= 0 || frameHeight <= 0 || !decoding.compareAndSet(false, true)) return
            try {
                val comma = encoded.indexOf(',')
                val payload = if (comma >= 0) encoded.substring(comma + 1) else encoded
                val bytes = Base64.decode(payload, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
                frameView.post {
                    if (!active || !enabled) {
                        bitmap.recycle()
                        return@post
                    }
                    val previous = currentBitmap
                    currentBitmap = bitmap
                    frameView.setImageBitmap(bitmap)
                    statusView.visibility = View.GONE
                    if (!decodedFrameReported) {
                        decodedFrameReported = true
                        evaluate(
                            "window.codexPocketNativeFrameDisplayed?.(${bitmap.width},${bitmap.height})",
                        )
                    }
                    if (previous != null && previous !== bitmap) {
                        frameView.postDelayed({
                            if (previous !== currentBitmap && !previous.isRecycled) previous.recycle()
                        }, OLD_FRAME_RECYCLE_DELAY_MILLIS)
                    }
                }
            } catch (error: RuntimeException) {
                statusView.post {
                    if (!active || !enabled) return@post
                    statusView.text = "原生画面解码失败，正在重试…"
                    statusView.visibility = View.VISIBLE
                }
            } finally {
                decoding.set(false)
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
                if (connected) applyQualityMode()
            }
        }

        fun close() {
            enabled = false
        }
    }

    private companion object {
        const val NATIVE_FRAME_INTERFACE = "CodexPocketNativeFrame"
        const val OLD_FRAME_RECYCLE_DELAY_MILLIS = 750L
        const val QUALITY_PREFERENCES = "remote-desktop"
        const val QUALITY_MODE_KEY = "quality-mode"
        const val QUALITY_AUTO = "auto"
        const val QUALITY_SMOOTH = "smooth"
        const val QUALITY_HIGH = "high"
        const val QUALITY_ORIGINAL = "original"
        val QUALITY_MODES = setOf(QUALITY_AUTO, QUALITY_SMOOTH, QUALITY_HIGH, QUALITY_ORIGINAL)
        val QUALITY_OPTIONS = listOf(
            QUALITY_AUTO to "自动（按屏幕）",
            QUALITY_SMOOTH to "流畅 960 × 540",
            QUALITY_HIGH to "高清 1280 × 720",
            QUALITY_ORIGINAL to "原始 1920 × 1080",
        )
    }
}
