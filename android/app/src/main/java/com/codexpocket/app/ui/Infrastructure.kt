package com.codexpocket.app.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.codexpocket.app.MainViewModel
import com.codexpocket.app.model.CameraDevice
import com.codexpocket.app.model.CameraSource
import com.codexpocket.app.model.DeviceGpuStatus
import com.codexpocket.app.model.SshDeviceStatus
import com.codexpocket.app.model.UiState
import kotlinx.coroutines.delay
import java.util.Locale
import java.net.URI
import java.net.URLEncoder

internal data class SelectedCamera(
    val device: CameraDevice,
    val camera: CameraSource,
)

@Composable
internal fun InfrastructureLauncherCard(state: UiState, onClick: () -> Unit) {
    val online = state.sshDevices.count { it.online }
    val total = state.sshDevices.size
    val cameraCount = state.cameraDevices.sumOf { it.cameras.count { camera -> camera.available } }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.onSecondary)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("设备与摄像头", fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        total == 0 && state.isDeviceStatusLoading -> "正在读取 SSH 设备…"
                        total == 0 -> "查看 SSH 状态与远程摄像头"
                        else -> "$online/$total 台在线 · $cameraCount 个摄像头"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
                )
            }
            if (state.isDeviceStatusLoading || state.isCameraListLoading) {
                CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InfrastructureScreen(
    state: UiState,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenCamera: (CameraDevice, CameraSource) -> Unit,
) {
    var tab by remember { mutableStateOf("devices") }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        viewModel.loadDeviceStatus()
        viewModel.loadCameras()
        var tick = 0
        while (true) {
            delay(10_000)
            viewModel.loadDeviceStatus(force = true)
            tick += 1
            if (tick % 3 == 0) viewModel.loadCameras(force = true)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("设备中心", fontWeight = FontWeight.Bold)
                        Text(
                            "与 Mac 的 SSH 清单保持一致",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.loadDeviceStatus(force = true)
                        viewModel.loadCameras(force = true)
                    }) {
                        Icon(Icons.Rounded.Refresh, "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(15.dp))
                    .padding(4.dp),
            ) {
                InfrastructureTab("设备", tab == "devices", Modifier.weight(1f)) { tab = "devices" }
                InfrastructureTab("摄像头", tab == "cameras", Modifier.weight(1f)) { tab = "cameras" }
            }
            if (tab == "devices") {
                DeviceStatusList(state)
            } else {
                CameraList(state, onOpenCamera)
            }
        }
    }
}

@Composable
private fun InfrastructureTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 9.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun DeviceStatusList(state: UiState) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.sshDevices.isEmpty() && state.isDeviceStatusLoading) {
            item { LoadingLine("正在并行检查 SSH 设备…") }
        }
        items(state.sshDevices, key = { it.id }) { DeviceStatusCard(it) }
    }
}

@Composable
private fun DeviceStatusCard(device: SshDeviceStatus) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(9.dp).background(
                        if (device.online) Color(0xFF2FA373) else Color(0xFF9C9BA5),
                        CircleShape,
                    ),
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOf(device.category, device.hostname.ifBlank { device.address })
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (device.online) "${device.latencyMs} ms" else "离线",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (device.online) Color(0xFF2F8E68) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!device.online) {
                if (device.error.isNotBlank()) Text(
                    device.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
                return@Column
            }
            Spacer(Modifier.height(13.dp))
            Metric("CPU", device.cpuPercent, "${device.cpuPercent.oneDecimal()}% · ${device.cpuCount} 核")
            Metric(
                "内存",
                percent(device.memoryUsed, device.memoryTotal),
                "${formatBytes(device.memoryUsed)} / ${formatBytes(device.memoryTotal)}",
            )
            Metric(
                "磁盘",
                percent(device.diskUsed, device.diskTotal),
                "${formatBytes(device.diskUsed)} / ${formatBytes(device.diskTotal)}",
            )
            device.gpus.forEach { GpuLine(it) }
        }
    }
}

@Composable
private fun Metric(label: String, value: Double, detail: String) {
    Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(38.dp))
        LinearProgressIndicator(
            progress = { (value / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(5.dp),
        )
        Text(
            detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 9.dp),
        )
    }
}

@Composable
private fun GpuLine(gpu: DeviceGpuStatus) {
    Text(
        "GPU ${gpu.index} · ${gpu.name} · ${gpu.utilization.oneDecimal()}% · " +
            "${gpu.memoryUsedMb.toInt()}/${gpu.memoryTotalMb.toInt()} MB · ${gpu.temperature.toInt()}°C",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun CameraList(state: UiState, onOpenCamera: (CameraDevice, CameraSource) -> Unit) {
    val visibleDevices = state.cameraDevices.filter { it.cameras.isNotEmpty() }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (visibleDevices.isEmpty()) {
            item {
                LoadingLine(
                    if (state.isCameraListLoading) "正在发现在线设备的摄像头…"
                    else "当前没有发现可观看的摄像头",
                )
            }
        }
        visibleDevices.forEach { device ->
            item(key = "title-${device.id}") {
                Text(
                    device.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 7.dp),
                )
            }
            items(device.cameras, key = { "${device.id}-${it.id}" }) { camera ->
                Card(
                    onClick = { if (camera.available) onOpenCamera(device, camera) },
                    enabled = camera.available,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(43.dp).background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(13.dp),
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Videocam, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(camera.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                camera.detail.ifBlank { if (camera.available) "可以观看" else "暂不可用" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingLine(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun CameraViewerScreen(
    endpoint: String,
    token: String,
    selected: SelectedCamera,
    onBack: () -> Unit,
) {
    var reload by remember { mutableIntStateOf(0) }
    val streamUrl = remember(endpoint, token, selected, reload) {
        cameraStreamUrl(endpoint, token, selected.device.id, selected.camera.id) + "&reload=$reload"
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    BackHandler(onBack = onBack)
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
            webView?.destroy()
            webView = null
        }
    }
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(selected.camera.name, fontWeight = FontWeight.Bold)
                        Text(
                            selected.device.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.68f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { reload += 1 }) { Icon(Icons.Rounded.Refresh, "重新连接") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    webView = this
                }
            },
            update = { view ->
                if (view.tag != streamUrl) {
                    view.tag = streamUrl
                    val escaped = streamUrl.replace("&", "&amp;").replace("\"", "&quot;")
                    val html = """
                    <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=5">
                    <style>html,body{width:100%;height:100%;margin:0;background:#000;overflow:hidden}body{display:grid;place-items:center}
                    img{width:100%;height:100%;object-fit:contain}#status{position:fixed;color:#aaa;font:14px system-ui;z-index:0}img{z-index:1}</style></head>
                    <body><div id="status">正在连接摄像头…</div><img src="$escaped" onload="document.getElementById('status').style.display='none'"
                    onerror="document.getElementById('status').textContent='连接中断，请点右上角重试'"></body></html>
                    """.trimIndent()
                    view.loadDataWithBaseURL(streamUrl, html, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

internal fun cameraStreamUrl(
    endpoint: String,
    token: String,
    deviceId: String,
    cameraId: String,
): String {
    val bridge = URI(endpoint)
    val scheme = if (bridge.scheme == "wss") "https" else "http"
    @Suppress("DEPRECATION")
    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    return "$scheme://${bridge.rawAuthority}/camera/stream" +
        "?device=${encode(deviceId)}&camera=${encode(cameraId)}&token=${encode(token)}"
}

private fun percent(used: Long, total: Long): Double =
    if (total <= 0L) 0.0 else used.toDouble() * 100.0 / total.toDouble()

private fun Double.oneDecimal(): String = String.format(Locale.US, "%.1f", this)

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1.0) "${gib.oneDecimal()} GB" else "${(bytes / (1024.0 * 1024.0)).toInt()} MB"
}
