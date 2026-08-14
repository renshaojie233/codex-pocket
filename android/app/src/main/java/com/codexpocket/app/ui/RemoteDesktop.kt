package com.codexpocket.app.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.net.URI

internal data class RemoteDesktopDevice(
    val id: String,
    val name: String,
    val description: String,
    val routeDescription: String,
    val directEndpoint: String,
)

internal val remoteDesktopDevices = listOf(
    RemoteDesktopDevice(
        id = "workstation",
        name = "Workstation",
        description = "Ubuntu 工作站 · 物理桌面",
        routeDescription = "Tailscale 直连 · 不经过 Mac",
        directEndpoint = "http://100.115.211.82:8790",
    ),
    RemoteDesktopDevice(
        id = "agilex",
        name = "Agilex",
        description = "Agilex 电脑 · GNOME 桌面",
        routeDescription = "Tailscale 直连 · 不经过 Mac",
        directEndpoint = "http://100.64.202.98:8790",
    ),
    RemoteDesktopDevice(
        id = "rsj-pc",
        name = "RSJ PC",
        description = "rsj-pc · Linux 桌面",
        routeDescription = "Tailscale 直连 · 不经过 Mac",
        directEndpoint = "http://100.77.122.104:8790",
    ),
)

@Composable
internal fun RemoteDesktopLauncherCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Computer,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text("远程桌面", fontWeight = FontWeight.Bold)
                Text(
                    "直接控制三台 Tailscale 电脑",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                "打开远程桌面",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemoteDesktopListScreen(
    endpoint: String,
    token: String,
    onBack: () -> Unit,
    onConnect: (RemoteDesktopDevice) -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回任务列表")
                    }
                },
                title = {
                    Column {
                        Text("远程桌面", fontWeight = FontWeight.Bold)
                        Text(
                            "选择要控制的电脑",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Security,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("NVENC 极致模式", fontWeight = FontWeight.SemiBold)
                            Text(
                                "默认 1080p / 60fps 硬件串流，手机原生硬解；仍可随时切回兼容模式。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(remoteDesktopDevices, key = { it.id }) { device ->
                RemoteDesktopDeviceCard(
                    device = device,
                    onExtremeClick = {
                        launchCodexStream(
                            context = context,
                            endpoint = endpoint,
                            token = token,
                            device = device,
                        )
                    },
                    onCompatibilityClick = { onConnect(device) },
                )
            }
            item {
                Text(
                    "首次使用会提示安装一次 Codex Stream。之后点击电脑即可自动登记和配对；分辨率、码率、帧率与解码器可在串流设置中调整。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun RemoteDesktopDeviceCard(
    device: RemoteDesktopDevice,
    onExtremeClick: () -> Unit,
    onCompatibilityClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().height(146.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Computer,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(25.dp),
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(device.name, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(8.dp))
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF36B37E)))
                    }
                    Text(
                        device.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "硬件串流 · Tailscale 直连",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                TextButton(onClick = onExtremeClick) {
                    Text("极致模式")
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        "连接 ${device.name}",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "NVENC → 原生硬解",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCompatibilityClick) { Text("兼容 VNC") }
            }
        }
    }
}

private const val CODEX_STREAM_PACKAGE = "com.codexpocket.stream"
private const val CODEX_STREAM_ACTIVITY = "com.limelight.PcView"
private const val CODEX_STREAM_HOST = "com.codexpocket.stream.extra.HOST"
private const val CODEX_STREAM_TOKEN = "com.codexpocket.stream.extra.TOKEN"
private const val CODEX_STREAM_GATEWAY_PORT = "com.codexpocket.stream.extra.GATEWAY_PORT"

private fun launchCodexStream(
    context: Context,
    endpoint: String,
    token: String,
    device: RemoteDesktopDevice,
) {
    val installed = runCatching {
        context.packageManager.getPackageInfo(CODEX_STREAM_PACKAGE, 0)
    }.isSuccess
    if (!installed) {
        val download = Intent(Intent.ACTION_VIEW, Uri.parse(codexStreamDownloadUrl(endpoint))).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(download) }
            .onSuccess {
                Toast.makeText(context, "请先安装 Codex Stream；安装后再点一次极致模式", Toast.LENGTH_LONG).show()
            }
            .onFailure {
                Toast.makeText(context, "无法打开 Codex Stream 下载地址", Toast.LENGTH_LONG).show()
            }
        return
    }

    val host = Uri.parse(device.directEndpoint).host.orEmpty()
    val intent = Intent().apply {
        component = ComponentName(CODEX_STREAM_PACKAGE, CODEX_STREAM_ACTIVITY)
        putExtra(CODEX_STREAM_HOST, host)
        putExtra(CODEX_STREAM_TOKEN, token)
        putExtra(CODEX_STREAM_GATEWAY_PORT, 8790)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "Codex Stream 启动失败，请重新安装", Toast.LENGTH_LONG).show()
        }
}

internal fun codexStreamDownloadUrl(endpoint: String): String {
    val bridge = URI(endpoint)
    val scheme = if (bridge.scheme == "wss") "https" else "http"
    return URI(
        scheme,
        null,
        bridge.host,
        bridge.port,
        "/download/codex-stream.apk",
        null,
        null,
    ).toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemoteDesktopSessionScreen(
    device: RemoteDesktopDevice,
    token: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val url = remember(token, device.id, device.directEndpoint) {
        remoteDesktopClientUrl(device.directEndpoint, token, device.id)
    }
    var nativeRemoteView by remember { mutableStateOf<NativeRemoteDesktopView?>(null) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        if (fullscreen) fullscreen = false else onBack()
    }

    DisposableEffect(activity, fullscreen) {
        val controller = activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView)
        }
        if (fullscreen) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (fullscreen) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            nativeRemoteView?.destroyRemote()
            nativeRemoteView = null
        }
    }

    DisposableEffect(lifecycleOwner, nativeRemoteView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> nativeRemoteView?.onResumeRemote()
                Lifecycle.Event.ON_STOP -> nativeRemoteView?.onPauseRemote()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = Color(0xFF111217),
        topBar = {
            if (!fullscreen) TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回设备列表")
                    }
                },
                title = {
                    Column {
                        Text(device.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "远程控制中",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF36B37E),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { nativeRemoteView?.reconnect() }) {
                        Icon(Icons.Rounded.Refresh, "重新加载远程桌面")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF17181E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        AndroidView(
            factory = {
                val uri = Uri.parse(url)
                val origin = "${uri.scheme}://${uri.encodedAuthority}"
                NativeRemoteDesktopView(context, origin) { fullscreen = it }.apply {
                    nativeRemoteView = this
                    load(url)
                }
            },
            update = { view ->
                if (view !== nativeRemoteView) nativeRemoteView = view
                view.syncFullscreen(fullscreen)
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun remoteDesktopClientUrl(endpoint: String, token: String, deviceId: String): String {
    val bridge = Uri.parse(endpoint)
    val scheme = if (bridge.scheme == "wss") "https" else "http"
    return Uri.Builder()
        .scheme(scheme)
        .encodedAuthority(bridge.encodedAuthority)
        .path("/remote/client")
        .appendQueryParameter("device", deviceId)
        .appendQueryParameter("token", token)
        .appendQueryParameter("render", "hardware")
        .build()
        .toString()
}
