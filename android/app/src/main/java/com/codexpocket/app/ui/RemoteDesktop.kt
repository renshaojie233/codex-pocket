package com.codexpocket.app.ui

import android.net.Uri
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    onBack: () -> Unit,
    onConnect: (RemoteDesktopDevice) -> Unit,
) {
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
                            Text("手机 / 平板直接连接目标电脑", fontWeight = FontWeight.SemiBold)
                            Text(
                                "屏幕数据走 Tailscale 点对点链路，不经过 Mac，也不开放公网端口。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(remoteDesktopDevices, key = { it.id }) { device ->
                RemoteDesktopDeviceCard(device, onClick = { onConnect(device) })
            }
            item {
                Text(
                    "触控：单击为左键，双击后拖动可拖拽，两指滑动与缩放。进入桌面后可打开软键盘。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun RemoteDesktopDeviceCard(device: RemoteDesktopDevice, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().height(116.dp),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    device.routeDescription,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                "连接 ${device.name}",
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemoteDesktopSessionScreen(
    device: RemoteDesktopDevice,
    token: String,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val url = remember(token, device.id, device.directEndpoint) {
        remoteDesktopClientUrl(device.directEndpoint, token, device.id)
    }
    var nativeRemoteView by remember { mutableStateOf<NativeRemoteDesktopView?>(null) }

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
            TopAppBar(
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
                NativeRemoteDesktopView(context, Uri.parse(url).host.orEmpty()).apply {
                    nativeRemoteView = this
                    load(url)
                }
            },
            update = { view ->
                if (view !== nativeRemoteView) nativeRemoteView = view
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
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
