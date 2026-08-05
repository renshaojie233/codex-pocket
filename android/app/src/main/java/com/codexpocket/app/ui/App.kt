package com.codexpocket.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.codexpocket.app.MainViewModel
import com.codexpocket.app.media.MediaSaver
import com.codexpocket.app.media.PocketMediaLoader
import com.codexpocket.app.model.ActivityEntry
import com.codexpocket.app.model.ChatMessage
import com.codexpocket.app.model.ConnectionState
import com.codexpocket.app.model.MediaAttachment
import com.codexpocket.app.model.PendingImage
import com.codexpocket.app.model.ThreadSummary
import com.codexpocket.app.model.UiState
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

@Composable
fun CodexPocketApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = when {
                state.connection != ConnectionState.Connected -> "connect"
                state.selectedThread != null -> "chat"
                else -> "threads"
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                "connect" -> ConnectionScreen(state, viewModel)
                "chat" -> ChatScreen(state, viewModel, snackbar)
                else -> ThreadsScreen(state, viewModel, snackbar)
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
        )
    }
}

@Composable
private fun ConnectionScreen(state: UiState, viewModel: MainViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(68.dp).clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Code, null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Codex Pocket", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "把这台手机变成你的 Codex 遥控器",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 30.dp),
        )
        OutlinedTextField(
            value = state.endpoint,
            onValueChange = viewModel::setEndpoint,
            label = { Text("Mac Bridge 地址") },
            placeholder = { Text("ws://100.x.x.x:8787/ws") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = state.token,
            onValueChange = viewModel::setToken,
            label = { Text("配对令牌") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.connect() }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = viewModel::connect,
            enabled = state.connection != ConnectionState.Connecting,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (state.connection == ConnectionState.Connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(10.dp))
                Text("正在连接…")
            } else {
                Icon(Icons.Rounded.Wifi, null)
                Spacer(Modifier.width(8.dp))
                Text("连接 Mac")
            }
        }
        Text(
            "请先确认手机上的 Tailscale 已连接。普通网络流量不会经过 Mac。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadsScreen(state: UiState, viewModel: MainViewModel, snackbar: SnackbarHostState) {
    var showNewThread by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Codex Pocket", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val connectionColor = if (state.isReconnecting) {
                                Color(0xFFE0A126)
                            } else {
                                Color(0xFF36B37E)
                            }
                            Box(Modifier.size(7.dp).clip(CircleShape).background(connectionColor))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (state.isReconnecting) "网络波动，正在恢复" else "Mac 已连接",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.isReconnecting) Color(0xFFA56D08) else Color(0xFF258B62),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showSettings = true
                        viewModel.loadAccountStatus()
                        viewModel.loadAutomations()
                        viewModel.loadPermissionProfiles()
                        viewModel.refreshCacheStats()
                    }) {
                        Icon(Icons.Rounded.Settings, "设置与用量")
                    }
                    IconButton(onClick = viewModel::refreshThreads) {
                        Icon(Icons.Rounded.Refresh, "刷新")
                    }
                    IconButton(onClick = viewModel::disconnect) {
                        Icon(Icons.Rounded.WifiOff, "断开")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.statusBarsPadding(),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewThread = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("新任务") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading && state.threads.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "最近任务",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
                items(state.threads, key = { it.id }) { thread ->
                    ThreadCard(thread, onClick = { viewModel.openThread(thread) })
                }
                if (state.threads.isEmpty()) {
                    item {
                        Text(
                            "还没有找到 Codex 任务",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }
            }
        }
    }
    if (showNewThread) {
        NewThreadSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = {
                showNewThread = false
                viewModel.closeDirectoryBrowser()
            },
        )
    }
    if (showSettings) {
        SettingsSheet(state = state, viewModel = viewModel, onDismiss = { showSettings = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewThreadSheet(state: UiState, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val recentDirectories = remember(state.threads) {
        state.threads.map { it.cwd }.filter { it.isNotBlank() }.distinct().take(8)
    }
    var cwd by remember(recentDirectories) {
        mutableStateOf(recentDirectories.firstOrNull() ?: "/Users")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text("新建 Codex 任务", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "选择 Mac 上的项目目录。任务会同时出现在手机和桌面端。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp, bottom = 18.dp),
            )

            OutlinedTextField(
                value = cwd,
                onValueChange = { cwd = it },
                label = { Text("项目目录") },
                placeholder = { Text("/Users/你的名字/项目") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { viewModel.openDirectoryBrowser(cwd) }) {
                        Icon(Icons.Rounded.Folder, "浏览 Mac 文件夹")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )

            if (recentDirectories.isNotEmpty()) {
                Text(
                    "最近项目",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 15.dp, bottom = 6.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentDirectories, key = { it }) { directory ->
                        AssistChip(
                            onClick = { cwd = directory },
                            label = {
                                Text(
                                    directory.substringAfterLast('/').ifBlank { directory },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = { Icon(Icons.Rounded.Folder, null, modifier = Modifier.size(17.dp)) },
                        )
                    }
                }
            }

            Text(
                "模型与思考强度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
            ModelControls(state, viewModel)

            Button(
                onClick = { viewModel.createThread(cwd) },
                enabled = cwd.startsWith("/") && !state.isCreatingThread,
                modifier = Modifier.fillMaxWidth().height(54.dp).padding(top = 6.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (state.isCreatingThread) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(9.dp))
                    Text("正在创建…")
                } else {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("创建并打开")
                }
            }
        }
    }

    if (state.isDirectoryBrowserOpen) {
        DirectoryBrowserDialog(
            state = state,
            onOpen = viewModel::browseDirectory,
            onChoose = {
                cwd = it
                viewModel.closeDirectoryBrowser()
            },
            onDismiss = viewModel::closeDirectoryBrowser,
        )
    }
}

@Composable
private fun DirectoryBrowserDialog(
    state: UiState,
    onOpen: (String) -> Unit,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("选择 Mac 文件夹", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    state.directoryBrowserPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp, bottom = 10.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            val current = state.directoryBrowserPath
                            val parent = current.substringBeforeLast('/', "").ifBlank { "/" }
                            onOpen(parent)
                        },
                        enabled = state.directoryBrowserPath != "/" && !state.isDirectoryLoading,
                    ) {
                        Icon(Icons.Rounded.ArrowUpward, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("上一级")
                    }
                    Spacer(Modifier.weight(1f))
                    if (state.isDirectoryLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 430.dp)) {
                    items(state.directoryEntries, key = { it.path }) { directory ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(directory.path) }
                                .padding(horizontal = 6.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(11.dp))
                            Text(
                                directory.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                null,
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    if (!state.isDirectoryLoading && state.directoryEntries.isEmpty()) {
                        item {
                            Text(
                                "这个文件夹里没有子文件夹",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = { onChoose(state.directoryBrowserPath) },
                        enabled = state.directoryBrowserPath.isNotBlank() && !state.isDirectoryLoading,
                    ) { Text("选择当前文件夹") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(state: UiState, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val account = state.accountStatus
    val context = LocalContext.current
    val permissionProfiles = remember(state.permissionProfiles) {
        val order = listOf(":danger-full-access", ":workspace", ":read-only")
        state.permissionProfiles.sortedBy { profile ->
            order.indexOf(profile.id).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setCompletionNotificationsEnabled(true)
        } else {
            viewModel.setCompletionNotificationsEnabled(false)
            viewModel.reportNotificationPermissionDenied()
        }
    }
    val setNotificationEnabled: (Boolean) -> Unit = { enabled ->
        if (!enabled) {
            viewModel.setCompletionNotificationsEnabled(false)
        } else if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.setCompletionNotificationsEnabled(true)
        } else {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 720.dp).navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("设置与用量", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "数据来自这台 Mac 当前登录的 Codex 账户",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.loadAccountStatus()
                            viewModel.loadAutomations()
                            viewModel.loadPermissionProfiles()
                        },
                        enabled = !state.isAccountLoading && !state.isAutomationsLoading &&
                            !state.isPermissionsLoading,
                    ) {
                        Icon(Icons.Rounded.Refresh, "刷新用量")
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Security,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text("默认运行权限", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "新任务和手机接下来发送的回合都会使用此设置",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.isPermissionsLoading) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }
                        if (permissionProfiles.isEmpty() && !state.isPermissionsLoading) {
                            Text(
                                "暂时无法读取这台 Mac 的权限档位",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 14.dp),
                            )
                        } else {
                            Column(Modifier.padding(top = 9.dp)) {
                                permissionProfiles.forEachIndexed { index, profile ->
                                    if (index > 0) HorizontalDivider()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(enabled = profile.allowed) {
                                                viewModel.selectDefaultPermissionProfile(profile.id)
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = state.defaultPermissionProfile == profile.id,
                                            enabled = profile.allowed,
                                            onClick = { viewModel.selectDefaultPermissionProfile(profile.id) },
                                        )
                                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    permissionProfileLabel(profile.id),
                                                    fontWeight = FontWeight.Medium,
                                                )
                                                if (!profile.allowed) {
                                                    Text(
                                                        "  已禁用",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.error,
                                                    )
                                                }
                                            }
                                            Text(
                                                profile.description ?: permissionProfileDescription(profile.id),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.NotificationsActive,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text("任务完成提醒", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (state.completionNotificationsEnabled) "后台监听已开启"
                                    else "横幅、提示音和震动均已关闭",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.completionNotificationsEnabled,
                                onCheckedChange = setNotificationEnabled,
                            )
                        }
                        if (state.completionNotificationsEnabled) {
                            Text(
                                "会保留一条低调的后台监听通知。若小米仍收不到，请允许自启动，并把电池策略设为“无限制”。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = viewModel::sendTestNotification,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("测试提醒")
                                }
                                TextButton(
                                    onClick = viewModel::openNotificationSettings,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("系统提醒设置")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("对话显示", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(
                                "${state.messageFontSizeSp.toInt()} sp",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value = state.messageFontSizeSp,
                            onValueChange = viewModel::setMessageFontSize,
                            valueRange = 12f..20f,
                            steps = 7,
                            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text("小", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.weight(1f))
                            Text("标准", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.weight(1f))
                            Text("大", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Text(
                            "这是消息正文的显示效果，调整后立即生效。",
                            fontSize = state.messageFontSizeSp.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("紧凑对话", fontWeight = FontWeight.Medium)
                                Text(
                                    "减少消息间距与空白，一屏显示更多内容",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.compactChatEnabled,
                                onCheckedChange = viewModel::setCompactChatEnabled,
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("手机本地缓存", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${state.messageCacheThreadCount} 个任务 · ${formatCacheSize(state.messageCacheBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(
                                onClick = viewModel::clearMessageCache,
                                enabled = state.messageCacheBytes > 0,
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("清空")
                            }
                        }
                        Text(
                            "打开时先显示手机缓存，再同步 Mac 最近 120 条；继续向上翻会自动加载更早内容。消息与图片合计最多 1 GB，超出后自动清理最早缓存，不会删除 Mac 上的任何记录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动化与监控", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${state.automations.count { it.status == "ACTIVE" }} 个运行 · ${state.automations.count { it.status != "ACTIVE" }} 个暂停",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.isAutomationsLoading) {
                        CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = viewModel::loadAutomations) {
                            Icon(Icons.Rounded.Refresh, "刷新自动化")
                        }
                    }
                }
            }

            items(state.automations, key = { "automation-${it.id}" }) { automation ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Flag,
                                null,
                                tint = if (automation.status == "ACTIVE") MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(21.dp),
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(
                                    automation.name,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    automationScheduleLabel(automation.rrule),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = automation.status == "ACTIVE",
                                enabled = state.updatingAutomationId == null,
                                onCheckedChange = { viewModel.setAutomationActive(automation.id, it) },
                            )
                        }
                        if (automation.promptPreview.isNotBlank()) {
                            Text(
                                automation.promptPreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 9.dp),
                            )
                        }
                        automation.targetThreadId?.let { targetThreadId ->
                            val target = state.threads.firstOrNull { it.id == targetThreadId }
                            if (target != null) {
                                TextButton(
                                    onClick = {
                                        onDismiss()
                                        viewModel.openThread(target)
                                    },
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                                    modifier = Modifier.padding(top = 4.dp),
                                ) { Text("打开关联对话") }
                            }
                        }
                    }
                }
            }

            if (!state.isAutomationsLoading && state.automations.isEmpty()) {
                item {
                    Text(
                        "这台 Mac 暂时没有自动化任务。创建和修改完整监控指令仍需在桌面端完成。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.isAccountLoading && account == null) {
                item {
                    Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (account != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                account.email ?: "当前 Codex 账户",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                planLabel(account.planType),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                }

                if (account.limits.isNotEmpty()) {
                    item {
                        Text("剩余额度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    items(account.limits, key = { "${it.name}-${it.period}" }) { limit ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(15.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(limit.name, fontWeight = FontWeight.Medium)
                                        Text(
                                            usagePeriodLabel(limit.period, limit.windowDurationMins),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        "剩余 ${limit.remainingPercent.toInt()}%",
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (limit.remainingPercent < 15) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (limit.usedPercent / 100.0).toFloat() },
                                    modifier = Modifier.fillMaxWidth().padding(top = 11.dp).height(7.dp)
                                        .clip(RoundedCornerShape(99.dp)),
                                )
                                Row(Modifier.fillMaxWidth().padding(top = 7.dp)) {
                                    Text(
                                        "已用 ${limit.usedPercent.toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    limit.resetsAt?.let {
                                        Text(
                                            "${formatResetTime(it)} 重置",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (account.unlimitedCredits || account.creditBalance != null || account.resetCredits != null) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(15.dp)) {
                                Text("额外额度", fontWeight = FontWeight.Medium)
                                Text(
                                    when {
                                        account.unlimitedCredits -> "无限制"
                                        account.creditBalance != null -> "余额 ${account.creditBalance}"
                                        else -> "暂无余额信息"
                                    },
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                account.resetCredits?.let {
                                    Text(
                                        "可用额度重置次数：$it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (account.lifetimeTokens != null || account.peakDailyTokens != null) {
                    item {
                        Text("使用统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            UsageStat(
                                "累计 Token",
                                formatLargeNumber(account.lifetimeTokens),
                                Modifier.weight(1f),
                            )
                            UsageStat(
                                "单日峰值",
                                formatLargeNumber(account.peakDailyTokens),
                                Modifier.weight(1f),
                            )
                        }
                    }
                    account.currentStreakDays?.let { streak ->
                        item {
                            Text(
                                "连续使用 $streak 天",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (account.unavailable.isNotEmpty()) {
                    item {
                        Text(
                            "部分账户统计暂时不可用，可稍后刷新。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    Text(
                        "暂时无法读取账户用量。请确认 Mac 端 Codex 已登录后重试。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    )
                }
            }

            item {
                HorizontalDivider(Modifier.padding(top = 4.dp))
                Text(
                    "Bridge：${state.endpoint}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun UsageStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ThreadCard(thread: ThreadSummary, onClick: () -> Unit) {
    val isActive = thread.status == "active" || thread.activeFlags.isNotEmpty()
    val needsAttention = thread.activeFlags.any { it == "waitingOnApproval" || it == "waitingOnUserInput" }
    val activeColor = if (needsAttention) Color(0xFFE88919) else MaterialTheme.colorScheme.primary
    val activeContainer = if (needsAttention) Color(0xFFFFE8C7) else MaterialTheme.colorScheme.primaryContainer
    val statusLabel = if (needsAttention) "等待你" else "进行中"
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().height(126.dp),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                    .background(
                        if (isActive) activeColor
                        else MaterialTheme.colorScheme.primaryContainer,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isActive) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f),
                    )
                }
                Icon(
                    Icons.Rounded.Code,
                    null,
                    tint = if (isActive) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        thread.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isActive) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = activeContainer,
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(6.dp).clip(CircleShape)
                                        .background(activeColor),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = activeColor,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                Box(Modifier.height(38.dp).padding(top = 4.dp)) {
                    Text(
                        thread.preview.replace('\n', ' ').ifBlank { "暂无内容预览" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        thread.cwd.substringAfterLast('/').ifBlank { "项目" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "  ·  ${formatTime(thread.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (thread.goalStatus != null) {
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (thread.goalStatus == "paused") "Goal 已暂停" else "Goal",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (thread.goalStatus == "paused") {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

internal sealed interface ChatTimelineItem {
    val key: String
}

internal data class TimelineMessage(val message: ChatMessage) : ChatTimelineItem {
    override val key: String = "message-${message.id}"
}

internal data class TimelineProcess(val messages: List<ChatMessage>) : ChatTimelineItem {
    val turnId: String = messages.firstNotNullOfOrNull { it.turnId.takeIf(String::isNotBlank) }.orEmpty()
    override val key: String = "process-${turnId.ifBlank { "unknown" }}-${messages.first().id}"
}

private fun isProcessMessage(
    message: ChatMessage,
    index: Int,
    lastAssistantMessageByTurn: Map<String, Int>,
): Boolean {
    if (message.role == "tool" || message.role == "status" || message.kind == "plan") return true
    if (message.role != "assistant" || message.kind != "agentMessage") return false
    if (message.phase == "commentary") return true
    if (message.turnId.isBlank()) return false
    // Some providers and old phone caches do not preserve MessagePhase. In
    // that case the newest assistant message in the turn is the visible one;
    // earlier updates are still available inside the process disclosure.
    return lastAssistantMessageByTurn[message.turnId] != index
}

internal fun buildChatTimeline(messages: List<ChatMessage>): List<ChatTimelineItem> {
    val lastAssistantMessageByTurn = buildMap {
        messages.forEachIndexed { index, message ->
            if (message.role == "assistant" && message.kind == "agentMessage" && message.turnId.isNotBlank()) {
                put(message.turnId, index)
            }
        }
    }
    return buildList {
        val pendingProcess = mutableListOf<ChatMessage>()
        fun flushProcess() {
            if (pendingProcess.isNotEmpty()) {
                add(TimelineProcess(pendingProcess.toList()))
                pendingProcess.clear()
            }
        }
        messages.forEachIndexed { index, message ->
            if (isProcessMessage(message, index, lastAssistantMessageByTurn)) {
                val pendingTurnId = pendingProcess.lastOrNull()?.turnId.orEmpty()
                if (pendingProcess.isNotEmpty() && pendingTurnId != message.turnId) flushProcess()
                pendingProcess += message
            } else {
                flushProcess()
                add(TimelineMessage(message))
            }
        }
        flushProcess()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(state: UiState, viewModel: MainViewModel, snackbar: SnackbarHostState) {
    val thread = state.selectedThread ?: return
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val showsStatus = state.isSending || state.pendingApproval != null || state.isReconnecting
    val timeline = remember(state.messages) { buildChatTimeline(state.messages) }
    val liveProcessKey = remember(timeline, state.activeTurnId) {
        state.activeTurnId?.let { activeTurnId ->
            timeline.filterIsInstance<TimelineProcess>().lastOrNull { it.turnId == activeTurnId }?.key
        }
    }
    val needsStandaloneLiveProcess = showsStatus &&
        (liveProcessKey == null || timeline.lastOrNull()?.key != liveProcessKey)
    val contentCount = timeline.size + if (needsStandaloneLiveProcess) 1 else 0
    val savedScrollPosition = remember(thread.id) { viewModel.chatScrollPosition(thread.id) }
    val latestTimeline by rememberUpdatedState(timeline)
    val latestNeedsStandaloneProcess by rememberUpdatedState(needsStandaloneLiveProcess)
    var threadMenuExpanded by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    var hasPositionedInitially by remember(thread.id) { mutableStateOf(false) }
    var followsLatest by remember(thread.id) { mutableStateOf(true) }
    val bottomThresholdPx = with(LocalDensity.current) { 44.dp.roundToPx() }
    val showJumpToLatest by remember(thread.id) {
        derivedStateOf {
            hasPositionedInitially && !listState.isNearLatest(bottomThresholdPx)
        }
    }

    DisposableEffect(thread.id) {
        onDispose {
            val firstIndex = listState.firstVisibleItemIndex
            val itemKey = latestTimeline.getOrNull(firstIndex)?.key
                ?: "live-process".takeIf {
                    latestNeedsStandaloneProcess && firstIndex == latestTimeline.size
                }
            if (itemKey != null) {
                viewModel.saveChatScrollPosition(
                    thread.id,
                    itemKey,
                    listState.firstVisibleItemScrollOffset,
                )
            }
        }
    }

    LaunchedEffect(
        state.isLoading,
        state.messages.hashCode(),
        state.activities.hashCode(),
        state.statusDetail,
        showsStatus,
    ) {
        if (contentCount <= 0) return@LaunchedEffect
        val latestIndex = contentCount - 1
        if (!hasPositionedInitially) {
            val restoredIndex = savedScrollPosition?.first?.let { savedKey ->
                timeline.indexOfFirst { it.key == savedKey }.takeIf { it >= 0 }
                    ?: timeline.size.takeIf {
                        savedKey == "live-process" && needsStandaloneLiveProcess
                    }
            }
            if (savedScrollPosition != null && restoredIndex == null && state.isLoading) {
                return@LaunchedEffect
            }
            if (restoredIndex != null) {
                listState.scrollToItem(restoredIndex, savedScrollPosition?.second ?: 0)
                followsLatest = listState.isNearLatest(bottomThresholdPx)
            } else {
                listState.scrollToLatest(latestIndex)
                followsLatest = true
            }
            hasPositionedInitially = true
        } else if (followsLatest) {
            listState.scrollToLatest(latestIndex)
        }
    }

    LaunchedEffect(thread.id, listState, hasPositionedInitially) {
        if (!hasPositionedInitially) return@LaunchedEffect
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> followsLatest = false
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    snapshotFlow { listState.isScrollInProgress }.first { !it }
                    followsLatest = listState.isNearLatest(bottomThresholdPx)
                }
            }
        }
    }

    LaunchedEffect(
        thread.id,
        state.hasOlderMessages,
        state.isLoadingOlderMessages,
        hasPositionedInitially,
    ) {
        if (!hasPositionedInitially || !state.hasOlderMessages || state.isLoadingOlderMessages) {
            return@LaunchedEffect
        }
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstVisible ->
                if (firstVisible <= 2) viewModel.loadOlderMessages()
            }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = viewModel::closeThread) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                    }
                },
                title = {
                    Column {
                        Text(
                            thread.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            thread.cwd.substringAfterLast('/'),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.openThread(thread) },
                        enabled = !state.isSending && !state.isReconnecting,
                    ) {
                        Icon(Icons.Rounded.Refresh, "重新同步")
                    }
                    Box {
                        IconButton(onClick = { threadMenuExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, "任务菜单")
                        }
                        DropdownMenu(
                            expanded = threadMenuExpanded,
                            onDismissRequest = { threadMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("归档任务") },
                                leadingIcon = { Icon(Icons.Rounded.Archive, null) },
                                enabled = !state.isSending && !state.isArchivingThread,
                                onClick = {
                                    threadMenuExpanded = false
                                    confirmArchive = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.statusBarsPadding(),
            )
        },
        bottomBar = { Composer(state, viewModel) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading && state.messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = if (state.compactChatEnabled) 11.dp else 14.dp,
                        vertical = if (state.compactChatEnabled) 7.dp else 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (state.compactChatEnabled) 7.dp else 12.dp),
                ) {
                    items(timeline, key = { it.key }) { item ->
                        when (item) {
                            is TimelineMessage -> MessageBubble(
                                message = item.message,
                                endpoint = state.endpoint,
                                token = state.token,
                                fontSizeSp = state.messageFontSizeSp,
                                compact = state.compactChatEnabled,
                                onRetry = { viewModel.retryFailedMessage(item.message.id) },
                                onDiscard = { viewModel.discardFailedMessage(item.message.id) },
                            )
                            is TimelineProcess -> ProcessGroupCard(
                                group = item,
                                endpoint = state.endpoint,
                                token = state.token,
                                state = state,
                                viewModel = viewModel,
                                isLive = item.turnId == state.activeTurnId,
                                showLiveStatus = item.key == liveProcessKey && !needsStandaloneLiveProcess,
                                fontSizeSp = state.messageFontSizeSp,
                                compact = state.compactChatEnabled,
                            )
                        }
                    }
                    if (needsStandaloneLiveProcess) {
                        item("live-process") {
                            ProcessGroupCard(
                                group = null,
                                endpoint = state.endpoint,
                                token = state.token,
                                state = state,
                                viewModel = viewModel,
                                isLive = true,
                                showLiveStatus = true,
                                fontSizeSp = state.messageFontSizeSp,
                                compact = state.compactChatEnabled,
                            )
                        }
                    }
                }
                if (state.isLoadingOlderMessages) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    )
                }
                if (showJumpToLatest) {
                    FilledIconButton(
                        onClick = {
                            followsLatest = true
                            scrollScope.launch {
                                val latest = listState.layoutInfo.totalItemsCount - 1
                                if (latest >= 0) listState.scrollToLatest(latest)
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(Icons.Rounded.ArrowDownward, "回到最新消息")
                    }
                }
                ChatScrollbar(
                    listState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onUserScrollStart = { followsLatest = false },
                    onUserScrollEnd = {
                        followsLatest = listState.isNearLatest(bottomThresholdPx)
                    },
                )
            }
        }
    }
    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            icon = { Icon(Icons.Rounded.Archive, null) },
            title = { Text("归档这个任务？") },
            text = { Text("任务会从最近任务中移除，但不会删除历史记录。桌面端也会同步更新。") },
            dismissButton = {
                TextButton(onClick = { confirmArchive = false }) { Text("取消") }
            },
            confirmButton = {
                Button(onClick = {
                    confirmArchive = false
                    viewModel.archiveCurrentThread()
                }) { Text("归档") }
            },
        )
    }
}

@Composable
private fun ChatScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onUserScrollStart: () -> Unit = {},
    onUserScrollEnd: () -> Unit = {},
) {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val totalItems = layoutInfo.totalItemsCount
    val canScroll = listState.canScrollBackward || listState.canScrollForward
    var trackHeightPx by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    if (!canScroll || totalItems <= 0 || visibleItems.isEmpty()) return

    val density = LocalDensity.current
    val viewportHeightPx = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
        .coerceAtLeast(1)
    val estimatedItemSizePx = visibleItems.map { it.size }.average().toFloat().coerceAtLeast(1f)
    val visibleItemSpan = (viewportHeightPx / estimatedItemSizePx).coerceAtMost(totalItems.toFloat())
    val maxPosition = (totalItems - visibleItemSpan).coerceAtLeast(0.001f)
    val firstVisibleSize = visibleItems.firstOrNull {
        it.index == listState.firstVisibleItemIndex
    }?.size?.coerceAtLeast(1) ?: estimatedItemSizePx.roundToInt()
    val currentPosition = listState.firstVisibleItemIndex +
        listState.firstVisibleItemScrollOffset.toFloat() / firstVisibleSize
    val scrollFraction = (currentPosition / maxPosition).coerceIn(0f, 1f)
    val minimumThumbPx = with(density) { 44.dp.toPx() }
    val thumbHeightPx = if (trackHeightPx > 0) {
        (trackHeightPx * (visibleItemSpan / totalItems))
            .coerceIn(minimumThumbPx.coerceAtMost(trackHeightPx.toFloat()), trackHeightPx.toFloat())
    } else {
        minimumThumbPx
    }
    val thumbTravelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
    val thumbTopPx = scrollFraction * thumbTravelPx
    val estimatedScrollableHeightPx =
        (estimatedItemSizePx * totalItems - viewportHeightPx).coerceAtLeast(1f)
    val contentPixelsPerTrackPixel = (estimatedScrollableHeightPx / thumbTravelPx)
        .coerceIn(1f, 48f)

    Box(
        modifier
            .fillMaxHeight()
            .width(24.dp)
            .onSizeChanged { trackHeightPx = it.height }
            .pointerInput(totalItems, trackHeightPx) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isDragging = true
                        onUserScrollStart()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        listState.dispatchRawDelta(dragAmount * contentPixelsPerTrackPixel)
                    },
                    onDragEnd = {
                        isDragging = false
                        onUserScrollEnd()
                    },
                    onDragCancel = {
                        isDragging = false
                        onUserScrollEnd()
                    },
                )
            },
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, thumbTopPx.roundToInt()) }
                .width(if (isDragging) 7.dp else 5.dp)
                .height(with(density) { thumbHeightPx.toDp() })
                .clip(RoundedCornerShape(999.dp))
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (isDragging) 0.78f else 0.38f,
                    ),
                ),
        )
    }
}

private fun LazyListState.isNearLatest(thresholdPx: Int): Boolean {
    val info = layoutInfo
    val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return false
    return isNearLatestPosition(
        totalItems = info.totalItemsCount,
        lastVisibleIndex = lastVisible.index,
        lastVisibleBottom = lastVisible.offset + lastVisible.size,
        viewportEnd = info.viewportEndOffset,
        thresholdPx = thresholdPx,
    )
}

internal fun isNearLatestPosition(
    totalItems: Int,
    lastVisibleIndex: Int,
    lastVisibleBottom: Int,
    viewportEnd: Int,
    thresholdPx: Int,
): Boolean = totalItems > 0 &&
    lastVisibleIndex == totalItems - 1 &&
    lastVisibleBottom <= viewportEnd + thresholdPx

private suspend fun LazyListState.scrollToLatest(latestIndex: Int) {
    if (latestIndex < 0) return
    scrollToItem(latestIndex)
    val info = layoutInfo
    val latest = info.visibleItemsInfo.lastOrNull { it.index == latestIndex } ?: return
    val viewportHeight = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
    val offsetInsideLatest = (latest.size - viewportHeight + info.afterContentPadding).coerceAtLeast(0)
    if (offsetInsideLatest > 0) scrollToItem(latestIndex, offsetInsideLatest)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProcessGroupCard(
    group: TimelineProcess?,
    endpoint: String,
    token: String,
    state: UiState,
    viewModel: MainViewModel,
    isLive: Boolean,
    showLiveStatus: Boolean,
    fontSizeSp: Float,
    compact: Boolean,
) {
    val messages = group?.messages.orEmpty()
    val displayMessages = remember(messages) { collapseViewedImageMessages(messages) }
    val disclosureKey = "${state.selectedThread?.id}:${group?.key ?: "live-process"}"
    val expanded = disclosureKey in state.expandedProcessGroups
    val approval = state.pendingApproval.takeIf { showLiveStatus }
    val recentActivities = state.activities.takeLast(8).takeIf { isLive }.orEmpty()
    val progressPreview = if (isLive) {
        processProgressPreview(
            messages = messages,
            activities = recentActivities,
            statusDetail = state.statusDetail,
            isLive = true,
        )
    } else {
        processFoldPreview(messages)
    }
    val liveTextAlpha = if (isLive) {
        val transition = rememberInfiniteTransition(label = "live-progress-pulse")
        transition.animateFloat(
            initialValue = 0.46f,
            targetValue = 0.82f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 950),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "live-progress-alpha",
        ).value
    } else {
        0.58f
    }
    var wasLive by remember(disclosureKey) { mutableStateOf(isLive) }
    LaunchedEffect(approval?.requestId) {
        if (approval != null) viewModel.setProcessGroupExpanded(disclosureKey, true)
    }
    LaunchedEffect(isLive) {
        if (isLive) {
            viewModel.setProcessGroupExpanded(disclosureKey, true)
        } else if (wasLive) {
            viewModel.setProcessGroupExpanded(disclosureKey, false)
        }
        wasLive = isLive
    }
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLive) {
                MaterialTheme.colorScheme.background
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (!isLive) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = if (compact) 36.dp else 40.dp)
                        .combinedClickable(
                            onClick = {
                                viewModel.setProcessGroupExpanded(disclosureKey, !expanded)
                            },
                            onDoubleClick = {
                                viewModel.setProcessGroupExpanded(disclosureKey, !expanded)
                            },
                        )
                        .padding(horizontal = 11.dp, vertical = if (compact) 5.dp else 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Code,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    val completedStepCount = messages.count { !isProcessCommentary(it) }
                    Text(
                        "已完成 · ${completedStepCount.coerceAtLeast(messages.size.coerceAtMost(1))} 步",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (progressPreview.isNotBlank()) {
                        Text(
                            progressPreview,
                            modifier = Modifier.weight(1f),
                            fontSize = (fontSizeSp - 4f).coerceAtLeast(10f).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        if (expanded) "双击收起过程" else "双击展开过程",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (isLive || expanded) {
                if (!isLive) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(horizontal = 12.dp, vertical = if (compact) 7.dp else 10.dp)) {
                    displayMessages.forEachIndexed { index, message ->
                        if (index > 0) {
                            Spacer(Modifier.height(if (compact) 6.dp else 9.dp))
                        }
                        if (isProcessCommentary(message)) {
                            ProcessCommentary(
                                message = message,
                                endpoint = endpoint,
                                token = token,
                                fontSizeSp = fontSizeSp,
                                compact = compact,
                            )
                        } else {
                            ProcessActivityDisclosure(
                                message = message,
                                groupKey = disclosureKey,
                                endpoint = endpoint,
                                token = token,
                                state = state,
                                viewModel = viewModel,
                                activeOverride = recentActivities.lastOrNull { it.id == message.id }
                                    ?.let { it.phase != "completed" },
                                fontSizeSp = fontSizeSp,
                                compact = compact,
                            )
                        }
                    }
                    approval?.let {
                        if (messages.isNotEmpty()) {
                            HorizontalDivider(Modifier.padding(vertical = 9.dp))
                        }
                        if (it.canApprove) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { viewModel.respondToApproval(false) }) { Text("拒绝") }
                                Button(onClick = { viewModel.respondToApproval(true) }) { Text("允许一次") }
                            }
                        } else {
                            Text(
                                "这种交互暂时不能在手机上直接回答；可以停止任务，再把答案作为新消息发送。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (showLiveStatus) {
                        val liveStatus = processLiveStatus(
                            activities = recentActivities,
                            currentStatus = state.currentStatus,
                            statusDetail = state.statusDetail,
                        )
                        if (messages.isNotEmpty() || approval != null) {
                            Spacer(Modifier.height(if (compact) 9.dp else 13.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(6.dp).clip(CircleShape).background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = liveTextAlpha),
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                liveStatus,
                                fontSize = (fontSizeSp - 3f).coerceAtLeast(10f).sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = liveTextAlpha),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (
                        (!isLive && messages.isEmpty()) ||
                        (showLiveStatus && state.statusDetail.isBlank() && recentActivities.isEmpty() &&
                            messages.isEmpty() && approval == null)
                    ) {
                        Text(
                            "暂无过程详情",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProcessActivityDisclosure(
    message: ChatMessage,
    groupKey: String,
    endpoint: String,
    token: String,
    state: UiState,
    viewModel: MainViewModel,
    activeOverride: Boolean?,
    fontSizeSp: Float,
    compact: Boolean,
) {
    val itemKey = "$groupKey:${message.id}"
    val expanded = itemKey in state.expandedProcessItems
    val scrollState = remember(itemKey) {
        androidx.compose.foundation.ScrollState(viewModel.processItemScrollOffset(itemKey))
    }
    val hasDetails = message.text.isNotBlank() ||
        !message.command.isNullOrBlank() || message.attachments.isNotEmpty()
    val viewedImageCount = viewedImageCount(message)
    DisposableEffect(itemKey) {
        onDispose { viewModel.saveProcessItemScrollOffset(itemKey, scrollState.value) }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (hasDetails) {
                            viewModel.saveProcessItemScrollOffset(itemKey, scrollState.value)
                            viewModel.setProcessItemExpanded(itemKey, !expanded)
                        }
                    },
                    onDoubleClick = {
                        viewModel.saveProcessItemScrollOffset(itemKey, scrollState.value)
                        viewModel.setProcessItemExpanded(itemKey, !expanded)
                    },
                )
                .padding(vertical = if (compact) 3.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (viewedImageCount > 0 || message.kind == "imageGeneration") {
                    Icons.Rounded.AddPhotoAlternate
                } else {
                    Icons.Rounded.Code
                },
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                processActivitySummary(message, activeOverride),
                modifier = Modifier.weight(1f),
                fontSize = (fontSizeSp - 2f).coerceAtLeast(11f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasDetails) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    if (expanded) "双击收起详情" else "双击查看详情",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (expanded) {
            Box(
                Modifier.fillMaxWidth()
                    .padding(start = 24.dp, top = 3.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                    .heightIn(max = if (compact) 340.dp else 430.dp)
                    .verticalScroll(scrollState)
                    .padding(10.dp),
            ) {
                ProcessMessageContent(message, endpoint, token, fontSizeSp, compact)
            }
        }
    }
}

@Composable
private fun ProcessCommentary(
    message: ChatMessage,
    endpoint: String,
    token: String,
    fontSizeSp: Float,
    compact: Boolean,
) {
    if (message.text.isNotBlank()) {
        MarkdownText(
            markdown = message.text,
            color = MaterialTheme.colorScheme.onSurface,
            linkColor = MaterialTheme.colorScheme.primary,
            fontSizeSp = fontSizeSp,
            compact = compact,
        )
    }
    if (message.attachments.isNotEmpty()) {
        if (message.text.isNotBlank()) Spacer(Modifier.height(8.dp))
        MediaGallery(message.attachments, endpoint, token)
    }
}

private fun isProcessCommentary(message: ChatMessage): Boolean =
    message.role == "assistant" && message.kind == "agentMessage"

internal fun processActivitySummary(message: ChatMessage, activeOverride: Boolean? = null): String {
    val active = activeOverride ?: (
        message.status.orEmpty().lowercase() in setOf(
            "inprogress",
            "started",
            "running",
        )
    )
    val completed = !active
    val prefix = if (active) "正在" else "已"
    val detail = message.command.orEmpty().ifBlank { message.text }
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(150)
    val viewedImages = viewedImageCount(message)
    if (viewedImages > 0) return "${prefix}查看 $viewedImages 张图像"
    return when (message.kind) {
        "commandExecution" -> if (detail.isBlank()) "${prefix}运行命令" else "${prefix}运行 $detail"
        "imageView" -> "${prefix}查看 ${message.attachments.size.coerceAtLeast(1)} 张图像"
        "imageGeneration" -> if (completed) "已生成图像" else "正在生成图像"
        "reasoning" -> if (completed) "已完成思考" else "正在思考"
        "plan" -> if (completed) "已更新计划" else "正在更新计划"
        "fileChange" -> if (detail.isBlank()) "${prefix}修改文件" else "${prefix}修改 $detail"
        "mcpToolCall", "dynamicToolCall" -> if (detail.isBlank()) "${prefix}调用工具" else "${prefix}调用 $detail"
        "collabAgentToolCall" -> if (detail.isBlank()) "${prefix}运行协作任务" else "${prefix}运行 $detail"
        "webSearch" -> if (completed) "已搜索网络" else "正在搜索网络"
        "contextCompaction" -> if (completed) "已整理上下文" else "正在整理上下文"
        else -> internalMessageTitle(message) + detail.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()
    }
}

private fun viewedImageCount(message: ChatMessage): Int {
    val command = message.command.orEmpty().lowercase()
    val isImageView = message.kind == "imageView" ||
        command.contains("view_image") || command.contains("查看图片") ||
        command.contains("view image")
    return if (isImageView) message.attachments.count { it.kind == "image" } else 0
}

internal fun processFoldPreview(messages: List<ChatMessage>): String {
    val viewedSources = messages
        .filter { viewedImageCount(it) > 0 }
        .flatMap { it.attachments }
        .filter { it.kind == "image" }
        .distinctBy { it.source }
    val latestCommand = messages.lastOrNull { it.kind == "commandExecution" }
    val actionSummary = buildList {
        if (viewedSources.isNotEmpty()) add("已查看 ${viewedSources.size} 张图像")
        latestCommand?.let { add(processActivitySummary(it, activeOverride = false)) }
    }.joinToString(" · ")
    if (actionSummary.isNotBlank()) return actionSummary
    return processProgressPreview(messages, emptyList(), "", isLive = false)
}

internal fun collapseViewedImageMessages(messages: List<ChatMessage>): List<ChatMessage> = buildList {
    var index = 0
    while (index < messages.size) {
        val first = messages[index]
        if (viewedImageCount(first) <= 0) {
            add(first)
            index += 1
            continue
        }
        val grouped = mutableListOf(first)
        var cursor = index + 1
        while (cursor < messages.size && viewedImageCount(messages[cursor]) > 0) {
            grouped += messages[cursor]
            cursor += 1
        }
        val attachments = grouped.flatMap { it.attachments }.distinctBy { it.source }
        val isActive = grouped.any {
            it.status.orEmpty().lowercase() in setOf("inprogress", "started", "running")
        }
        add(
            first.copy(
                text = grouped.map { it.text }.filter(String::isNotBlank).joinToString("\n\n"),
                kind = "imageView",
                command = "查看图片",
                status = if (isActive) "inProgress" else grouped.last().status,
                attachments = attachments,
            ),
        )
        index = cursor
    }
}

internal fun processLiveStatus(
    activities: List<ActivityEntry>,
    currentStatus: String,
    statusDetail: String,
): String {
    val active = activities.lastOrNull { it.phase != "completed" }
    return listOfNotNull(
        active?.let { listOf(it.title, it.detail).filter(String::isNotBlank).joinToString(" · ") },
        listOf(currentStatus, statusDetail).filter(String::isNotBlank).joinToString(" · "),
    ).firstOrNull(String::isNotBlank) ?: "Codex 正在处理…"
}

internal fun processProgressPreview(
    messages: List<ChatMessage>,
    activities: List<ActivityEntry>,
    statusDetail: String,
    isLive: Boolean,
): String {
    val latestActivity = activities.lastOrNull()
    val latestCommentary = messages.lastOrNull {
        it.kind == "agentMessage" && it.text.isNotBlank()
    }?.text
    val latestProcess = messages.lastOrNull()?.let { it.command.orEmpty().ifBlank { it.text } }
    val candidates = if (isLive) {
        listOf(latestCommentary, statusDetail, latestActivity?.detail, latestActivity?.title, latestProcess)
    } else {
        listOf(latestCommentary, latestProcess)
    }
    return candidates.firstOrNull { !it.isNullOrBlank() }
        .orEmpty()
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(180)
}

@Composable
private fun ProcessMessageContent(
    message: ChatMessage,
    endpoint: String,
    token: String,
    fontSizeSp: Float,
    compact: Boolean,
) {
    val isTool = message.role == "tool"
    if (!message.command.isNullOrBlank()) {
        Text(
            message.command,
            fontFamily = FontFamily.Monospace,
            fontSize = (fontSizeSp - 2f).coerceAtLeast(11f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
    if (message.text.isNotBlank()) {
        if (isTool) {
            Text(
                message.text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = (fontSizeSp - 1f).coerceAtLeast(11f).sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 5.dp),
            )
        } else {
            Box(Modifier.padding(top = 5.dp)) {
                MarkdownText(
                    markdown = message.text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    linkColor = MaterialTheme.colorScheme.primary,
                    fontSizeSp = fontSizeSp,
                    compact = compact,
                )
            }
        }
    }
    if (message.attachments.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        MediaGallery(message.attachments, endpoint, token)
    }
}

@Composable
private fun ActivityRow(activity: ActivityEntry, fontSizeSp: Float) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 6.dp).size(6.dp).clip(CircleShape).background(
                if (activity.phase == "completed") Color(0xFF36B37E) else MaterialTheme.colorScheme.primary,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                activity.title,
                fontSize = (fontSizeSp - 2f).coerceAtLeast(11f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (activity.detail.isNotBlank()) {
                Text(
                    activity.detail.replace('\n', ' '),
                    fontSize = (fontSizeSp - 3f).coerceAtLeast(10f).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    endpoint: String,
    token: String,
    fontSizeSp: Float,
    compact: Boolean,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    val isUser = message.role == "user"
    val isFailed = isUser && message.deliveryState == "failed"
    val isSending = isUser && message.deliveryState == "sending"
    val bubbleColor = when {
        isFailed -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val bubbleContentColor = when {
        isFailed -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 5.dp,
                bottomEnd = if (isUser) 5.dp else 18.dp,
            ),
            colors = CardDefaults.cardColors(
                containerColor = bubbleColor,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 0.dp else 1.dp),
            modifier = Modifier.fillMaxWidth(if (compact) 0.97f else 0.92f),
        ) {
            Column(
                Modifier.padding(
                    horizontal = if (compact) 12.dp else 15.dp,
                    vertical = if (compact) 8.dp else 12.dp,
                ),
            ) {
                if (message.text.isNotBlank()) {
                    MarkdownText(
                        markdown = message.text,
                        color = bubbleContentColor,
                        linkColor = if (isUser && !isFailed) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        fontSizeSp = fontSizeSp,
                        compact = compact,
                    )
                }
                if (message.attachments.isNotEmpty()) {
                    if (message.text.isNotBlank()) Spacer(Modifier.height(10.dp))
                    MediaGallery(message.attachments, endpoint, token)
                }
                if (isSending) {
                    Text(
                        "正在发送…",
                        style = MaterialTheme.typography.labelSmall,
                        color = bubbleContentColor.copy(alpha = 0.72f),
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
                if (isFailed) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 5.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "未送达",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDiscard) { Text("删除") }
                        TextButton(onClick = onRetry) { Text("重试") }
                    }
                }
            }
        }
    }
}

private fun internalMessageTitle(message: ChatMessage): String = when (message.kind) {
    "agentMessage" -> "进度更新"
    "reasoning" -> "思考过程"
    "plan" -> "执行计划"
    "commandExecution" -> "命令执行"
    "fileChange" -> "文件修改"
    "mcpToolCall", "dynamicToolCall" -> "工具调用"
    "collabAgentToolCall" -> "协作任务"
    "webSearch" -> "网络搜索"
    "imageGeneration" -> "图片生成"
    "imageView" -> "图片查看"
    "contextCompaction" -> "上下文整理"
    else -> "过程信息"
}

@Composable
private fun MediaGallery(attachments: List<MediaAttachment>, endpoint: String, token: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var opened by remember { mutableStateOf<MediaAttachment?>(null) }
    var pendingSave by remember { mutableStateOf<Triple<MediaAttachment, String, Boolean>?>(null) }
    val saveMedia: (MediaAttachment, String, Boolean) -> Unit = { attachment, source, openAfterSave ->
        scope.launch {
            Toast.makeText(
                context,
                if (openAfterSave) "正在下载文件…" else "正在保存…",
                Toast.LENGTH_SHORT,
            ).show()
            runCatching { MediaSaver.saveDetailed(context, attachment, source) }
                .onSuccess { saved ->
                    if (openAfterSave && MediaSaver.open(context, saved)) {
                        Toast.makeText(context, "已下载：${saved.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        val destination = if (attachment.kind == "file") "下载/Codex Pocket" else "相册"
                        Toast.makeText(
                            context,
                            "已保存到$destination：${saved.name}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                .onFailure { error ->
                    Toast.makeText(context, "保存失败：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
        }
    }
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val media = pendingSave
        pendingSave = null
        if (granted && media != null) {
            saveMedia(media.first, media.second, media.third)
        } else if (!granted) {
            Toast.makeText(context, "没有存储权限，无法保存媒体", Toast.LENGTH_LONG).show()
        }
    }
    val requestSave: (MediaAttachment, String) -> Unit = { attachment, source ->
        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = Triple(attachment, source, false)
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveMedia(attachment, source, false)
        }
    }
    val requestOpen: (MediaAttachment, String) -> Unit = { attachment, source ->
        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = Triple(attachment, source, true)
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveMedia(attachment, source, true)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        attachments.forEach { attachment ->
            val source = remember(attachment.source, endpoint, token) {
                resolveMediaSource(attachment, endpoint, token)
            }
            when (attachment.kind) {
                "image" -> MediaImage(
                    attachment = attachment,
                    source = source,
                    onClick = { opened = attachment },
                    onLongClick = { requestSave(attachment, source) },
                )
                "video" -> MediaVideoPreview(
                    attachment = attachment,
                    source = source,
                    onClick = { opened = attachment },
                    onLongClick = { requestSave(attachment, source) },
                )
                "audio" -> MediaFileCard(
                    attachment = attachment,
                    label = "播放音频",
                    icon = { Icon(Icons.Rounded.Audiotrack, null) },
                    onClick = { opened = attachment },
                )
                "file" -> MediaFileCard(
                    attachment = attachment,
                    label = "下载并打开",
                    icon = { Icon(Icons.Rounded.Description, null) },
                    onClick = { requestOpen(attachment, source) },
                )
            }
        }
    }

    opened?.let { attachment ->
        val source = resolveMediaSource(attachment, endpoint, token)
        when (attachment.kind) {
            "image" -> ImagePreviewDialog(
                attachment = attachment,
                source = source,
                onSave = { requestSave(attachment, source) },
                onDismiss = { opened = null },
            )
            "video", "audio" -> MediaPlayerDialog(
                attachment = attachment,
                source = source,
                onSave = { requestSave(attachment, source) },
                onDismiss = { opened = null },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaVideoPreview(
    attachment: MediaAttachment,
    source: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val mediaImageLoader = remember(context) { PocketMediaLoader.get(context) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = remember(source) {
                ImageRequest.Builder(context)
                    .data(source)
                    .videoFrameMillis(700)
                    .crossfade(true)
                    .build()
            },
            imageLoader = mediaImageLoader,
            contentDescription = attachment.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.62f),
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.Center).size(58.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, "播放视频", modifier = Modifier.size(34.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaImage(
    attachment: MediaAttachment,
    source: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val mediaImageLoader = remember(context) { PocketMediaLoader.get(context) }
    AsyncImage(
        model = remember(source) {
            ImageRequest.Builder(context)
                .data(source)
                .crossfade(true)
                .build()
        },
        imageLoader = mediaImageLoader,
        contentDescription = attachment.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 360.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

@Composable
private fun MediaFileCard(
    attachment: MediaAttachment,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp),
            ) { Box(contentAlignment = Alignment.Center) { icon() } }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    attachment.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(
                if (attachment.kind == "file") Icons.Rounded.Download else Icons.Rounded.PlayArrow,
                null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ImagePreviewDialog(
    attachment: MediaAttachment,
    source: String,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val mediaImageLoader = remember(context) { PocketMediaLoader.get(context) }
    var scale by remember(source) { mutableStateOf(1f) }
    var offset by remember(source) { mutableStateOf(Offset.Zero) }
    var canvasSize by remember(source) { mutableStateOf(IntSize.Zero) }
    var controlsVisible by remember(source) { mutableStateOf(true) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 6f)
        if (newScale <= 1.01f) {
            offset = Offset.Zero
        } else {
            val maxX = canvasSize.width * (newScale - 1f) / 2f
            val maxY = canvasSize.height * (newScale - 1f) / 2f
            offset = Offset(
                x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                y = (offset.y + panChange.y).coerceIn(-maxY, maxY),
            )
        }
        scale = newScale
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black),
        ) {
            AsyncImage(
                model = remember(source) {
                    ImageRequest.Builder(context).data(source).crossfade(true).build()
                },
                imageLoader = mediaImageLoader,
                contentDescription = attachment.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(top = 52.dp, bottom = 46.dp)
                    .onSizeChanged { canvasSize = it }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .transformable(transformState)
                    .pointerInput(source) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            },
                            onLongPress = { onSave() },
                        )
                    },
            )
            if (controlsVisible) {
                Surface(
                    color = Color.Black.copy(alpha = 0.58f),
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                ) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, "关闭图片")
                        }
                        Text(
                            attachment.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        IconButton(onClick = onSave) {
                            Icon(Icons.Rounded.Download, "保存图片")
                        }
                    }
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.58f),
                    contentColor = Color.White.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Text(
                        "双指缩放 · 拖动查看 · 长按保存",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaPlayerDialog(
    attachment: MediaAttachment,
    source: String,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var prepared by remember(source) { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        val controller = MediaController(context)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoURI(Uri.parse(source))
                        setOnPreparedListener { player ->
                            player.isLooping = false
                            prepared = true
                            start()
                            controller.show(2500)
                        }
                    }
                },
                modifier = if (attachment.kind == "audio") {
                    Modifier.fillMaxWidth().height(180.dp).align(Alignment.Center)
                } else {
                    Modifier.fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = 52.dp, bottom = 10.dp)
                },
            )
            if (!prepared) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(34.dp),
                )
            }
            if (attachment.kind == "audio") {
                Icon(
                    Icons.Rounded.Audiotrack,
                    null,
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.align(Alignment.Center).size(52.dp),
                )
            }
            Surface(
                color = Color.Black.copy(alpha = 0.58f),
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            ) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "关闭播放器")
                    }
                    Text(
                        attachment.name,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    IconButton(onClick = onSave) {
                        Icon(Icons.Rounded.Download, "保存媒体")
                    }
                }
            }
        }
    }
}

private fun resolveMediaSource(attachment: MediaAttachment, endpoint: String, token: String): String {
    if (!attachment.isLocal) return attachment.source
    val bridge = Uri.parse(endpoint)
    val httpScheme = if (bridge.scheme == "wss") "https" else "http"
    return Uri.Builder()
        .scheme(httpScheme)
        .encodedAuthority(bridge.encodedAuthority)
        .path("/media")
        .appendQueryParameter("path", attachment.source)
        .appendQueryParameter("token", token)
        .build()
        .toString()
}

@Composable
private fun MarkdownText(
    markdown: String,
    color: Color,
    linkColor: Color,
    fontSizeSp: Float,
    compact: Boolean,
) {
    val context = LocalContext.current
    val textColor = color.toArgb()
    val latexTextSizePx = with(LocalDensity.current) { fontSizeSp.sp.toPx() }
    val markwon = remember(context, latexTextSizePx, textColor) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(
                JLatexMathPlugin.create(latexTextSizePx) { builder ->
                    builder.inlinesEnabled(true)
                    builder.theme().textColor(textColor)
                },
            )
            .build()
    }
    val resolvedLinkColor = linkColor.toArgb()
    val normalizedMarkdown = remember(markdown) { normalizeLatexForMarkwon(markdown) }
    AndroidView(
        factory = { viewContext ->
            TextView(viewContext).apply {
                setTextIsSelectable(true)
                linksClickable = true
                movementMethod = LinkMovementMethod.getInstance()
                includeFontPadding = false
                setLineSpacing(0f, 1.08f)
                setPadding(0, 0, 0, 0)
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.setLinkTextColor(resolvedLinkColor)
            textView.textSize = fontSizeSp
            textView.setLineSpacing(0f, if (compact) 1.02f else 1.08f)
            markwon.setMarkdown(textView, normalizedMarkdown)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Composer(state: UiState, viewModel: MainViewModel) {
    var showModeSheet by remember { mutableStateOf(false) }
    var showGoalEditor by remember { mutableStateOf(false) }
    var confirmClearGoal by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        viewModel.addPendingImages(uris.map(Uri::toString))
    }
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        state.goal?.let { goal ->
            GoalStatusBar(
                state = state,
                onEdit = { showGoalEditor = true },
                onTogglePaused = { viewModel.setGoalPaused(goal.status == "active") },
            )
        }
        ModelControls(
            state = state,
            viewModel = viewModel,
            showModeButton = true,
            onModeClick = { showModeSheet = true },
        )
        if (state.pendingImages.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.pendingImages, key = { it.id }) { image ->
                    PendingImagePreview(image, onRemove = { viewModel.removePendingImage(image.id) })
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(
                onClick = { imagePicker.launch("image/*") },
                enabled = !state.isReconnecting && !state.isUploadingImages && state.pendingImages.size < 4,
                modifier = Modifier.size(50.dp),
            ) {
                if (state.isUploadingImages) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.AddPhotoAlternate, "选择图片")
                }
            }
            Spacer(Modifier.width(5.dp))
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::setInput,
                placeholder = {
                    Text(
                        when {
                            state.isReconnecting -> "连接恢复后即可继续发送…"
                            state.isUploadingImages -> "正在上传图片…"
                            state.isSending -> "输入引导，调整当前任务…"
                            else -> "给 Codex 发送指令…"
                        },
                    )
                },
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 5,
                shape = RoundedCornerShape(20.dp),
            )
            Spacer(Modifier.width(9.dp))
            if (state.isSending && !state.isUploadingImages) {
                FilledIconButton(
                    onClick = viewModel::interrupt,
                    enabled = !state.isReconnecting,
                    modifier = Modifier.size(50.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Rounded.Stop, "停止")
                }
                Spacer(Modifier.width(7.dp))
            }
            FilledIconButton(
                onClick = viewModel::sendMessage,
                enabled = !state.isReconnecting && !state.isUploadingImages &&
                    (state.input.isNotBlank() || state.pendingImages.isNotEmpty()),
                modifier = Modifier.size(50.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, if (state.isSending) "发送引导" else "发送")
            }
        }
    }

    if (showModeSheet) {
        ModeSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { showModeSheet = false },
            onGoal = {
                showModeSheet = false
                showGoalEditor = true
            },
        )
    }
    if (showGoalEditor) {
        GoalEditorDialog(
            state = state,
            onDismiss = { showGoalEditor = false },
            onSave = { objective, budget ->
                showGoalEditor = false
                viewModel.setGoal(objective, budget)
            },
            onClear = if (state.goal != null) {
                {
                    showGoalEditor = false
                    confirmClearGoal = true
                }
            } else {
                null
            },
        )
    }
    if (confirmClearGoal) {
        AlertDialog(
            onDismissRequest = { confirmClearGoal = false },
            icon = { Icon(Icons.Rounded.Flag, null) },
            title = { Text("清除这个 Goal？") },
            text = { Text("目标记录和进度会从这个任务中移除，对话历史不会删除。") },
            dismissButton = { TextButton(onClick = { confirmClearGoal = false }) { Text("取消") } },
            confirmButton = {
                Button(onClick = {
                    confirmClearGoal = false
                    viewModel.clearGoal()
                }) { Text("清除") }
            },
        )
    }
}

@Composable
private fun PendingImagePreview(image: PendingImage, onRemove: () -> Unit) {
    val context = LocalContext.current
    val mediaImageLoader = remember(context) { PocketMediaLoader.get(context) }
    Box(
        Modifier.size(72.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = Uri.parse(image.uri),
            imageLoader = mediaImageLoader,
            contentDescription = image.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f),
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)
                .clickable(onClick = onRemove),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Close,
                    "移除图片",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun ModelControls(
    state: UiState,
    viewModel: MainViewModel,
    showModeButton: Boolean = false,
    onModeClick: () -> Unit = {},
) {
    val selectedModel = state.models.firstOrNull { it.id == state.selectedModel }
    val modelItems = state.models.map { it.id to it.displayName }
    val effortItems = selectedModel?.efforts.orEmpty().map { it.id to effortLabel(it.id) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showModeButton) {
            val modeLabel = when {
                state.goal != null -> "Goal"
                state.selectedMode == "plan" -> "Plan"
                else -> "普通"
            } + if (state.fastModeEnabled) " · Fast" else ""
            TextButton(
                onClick = onModeClick,
                enabled = !state.isSending && !state.isModeUpdating,
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
            ) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(2.dp))
                Text(modeLabel, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            Spacer(Modifier.width(2.dp))
        }
        CompactSelector(
            label = selectedModel?.displayName ?: "模型加载中",
            items = modelItems,
            enabled = modelItems.isNotEmpty() && !state.isSending,
            onSelect = viewModel::selectModel,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(4.dp))
        CompactSelector(
            label = effortLabel(state.selectedEffort),
            items = effortItems,
            enabled = effortItems.isNotEmpty() && !state.isSending && state.selectedMode != "plan",
            onSelect = viewModel::selectEffort,
        )
    }
}

@Composable
private fun GoalStatusBar(
    state: UiState,
    onEdit: () -> Unit,
    onTogglePaused: () -> Unit,
) {
    val goal = state.goal ?: return
    val status = when (goal.status) {
        "paused" -> "已暂停"
        "blocked" -> "受阻"
        "usageLimited" -> "用量受限"
        "budgetLimited" -> "预算已用完"
        "complete" -> "已完成"
        else -> "运行中"
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Flag,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Goal · $status",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    goal.objective,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onTogglePaused,
                enabled = !state.isModeUpdating && goal.status in listOf("active", "paused", "blocked"),
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    if (goal.status == "active") Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (goal.status == "blocked") "恢复目标"
                    else if (goal.status == "paused") "继续 Goal" else "暂停 Goal",
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onEdit, enabled = !state.isModeUpdating, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.Edit, "编辑 Goal", modifier = Modifier.size(19.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSheet(
    state: UiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onGoal: () -> Unit,
) {
    val supportsFast = state.models.firstOrNull { it.id == state.selectedModel }
        ?.serviceTiers?.any { it.id == "priority" } == true
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
        ) {
            Text("运行模式", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "模式会同步到 Mac 桌面端，并从下一轮开始生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 13.dp),
            )
            ModeChoice(
                title = "普通模式",
                description = "直接执行你的指令",
                selected = state.selectedMode == "default",
                enabled = !state.isModeUpdating,
                onClick = {
                    viewModel.selectMode("default")
                    onDismiss()
                },
            )
            Spacer(Modifier.height(8.dp))
            ModeChoice(
                title = "Plan 规划模式",
                description = "先梳理上下文和方案，再决定如何执行",
                selected = state.selectedMode == "plan",
                enabled = !state.isModeUpdating,
                onClick = {
                    viewModel.selectMode("plan")
                    onDismiss()
                },
            )
            Spacer(Modifier.height(8.dp))
            ModeChoice(
                title = "Goal 目标模式",
                description = "持续推进一个可运行数小时或数天的目标",
                selected = state.goal != null,
                enabled = !state.isModeUpdating,
                onClick = onGoal,
            )
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fast 加速", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (supportsFast) "约 1.5 倍速度，会增加用量" else "当前模型不支持 Fast",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.fastModeEnabled,
                    enabled = supportsFast && !state.isModeUpdating,
                    onCheckedChange = viewModel::setFastModeEnabled,
                )
            }
        }
    }
}

@Composable
private fun ModeChoice(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Text("当前", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun GoalEditorDialog(
    state: UiState,
    onDismiss: () -> Unit,
    onSave: (String, Long?) -> Unit,
    onClear: (() -> Unit)?,
) {
    var objective by remember(state.goal?.objective) { mutableStateOf(state.goal?.objective.orEmpty()) }
    var tokenBudget by remember(state.goal?.tokenBudget) {
        mutableStateOf(state.goal?.tokenBudget?.toString().orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Flag, null) },
        title = { Text(if (state.goal == null) "启动 Goal" else "编辑 Goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = objective,
                    onValueChange = { objective = it },
                    label = { Text("目标") },
                    placeholder = { Text("例如：完成并验证移动端的全部功能") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tokenBudget,
                    onValueChange = { tokenBudget = it.filter(Char::isDigit) },
                    label = { Text("Token 预算（可选）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "留空表示不设置预算。Goal 仍可在当前对话里继续引导。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            Row {
                onClear?.let { clear ->
                    TextButton(onClick = clear) {
                        Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("清除")
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(objective.trim(), tokenBudget.toLongOrNull()) },
                enabled = objective.isNotBlank() && !state.isModeUpdating,
            ) { Text(if (state.goal == null) "启动" else "保存") }
        },
    )
}

@Composable
private fun CompactSelector(
    label: String,
    items: List<Pair<String, String>>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
            Icon(Icons.Rounded.ExpandMore, null, modifier = Modifier.size(17.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (id, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    },
                )
            }
        }
    }
}

private fun effortLabel(effort: String): String = when (effort.lowercase()) {
    "none" -> "不思考"
    "minimal" -> "极简"
    "low" -> "低"
    "medium" -> "中"
    "high" -> "高"
    "xhigh" -> "很高"
    "max" -> "最高"
    "ultra" -> "Ultra"
    else -> effort.ifBlank { "思考强度" }
}

private fun permissionProfileLabel(profile: String): String = when (profile) {
    ":danger-full-access" -> "完全访问"
    ":workspace" -> "工作区"
    ":read-only" -> "只读"
    else -> profile.removePrefix(":").ifBlank { "自定义权限" }
}

private fun permissionProfileDescription(profile: String): String = when (profile) {
    ":danger-full-access" -> "可访问整台 Mac、直接联网并运行命令，不逐次询问"
    ":workspace" -> "可在当前项目内读写；越界或联网时会请求确认"
    ":read-only" -> "只允许读取；修改、运行或联网时会请求确认"
    else -> "由这台 Mac 上的 Codex 权限配置定义"
}

private fun automationScheduleLabel(rrule: String): String {
    if (rrule.isBlank()) return "按事件运行"
    val parts = rrule.split(';').mapNotNull { part ->
        val pieces = part.split('=', limit = 2)
        if (pieces.size == 2) pieces[0] to pieces[1] else null
    }.toMap()
    val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    return when (parts["FREQ"]?.uppercase()) {
        "MINUTELY" -> if (interval == 1) "每分钟" else "每 $interval 分钟"
        "HOURLY" -> if (interval == 1) "每小时" else "每 $interval 小时"
        "DAILY" -> if (interval == 1) "每天" else "每 $interval 天"
        "WEEKLY" -> if (interval == 1) "每周" else "每 $interval 周"
        else -> "定时运行"
    }
}

private fun planLabel(plan: String?): String = when (plan?.lowercase()) {
    "free" -> "Free 方案"
    "go" -> "Go 方案"
    "plus" -> "Plus 方案"
    "pro", "prolite" -> "Pro 方案"
    "team", "business", "self_serve_business_usage_based" -> "Business 方案"
    "enterprise", "enterprise_cbp_usage_based", "ent26" -> "Enterprise 方案"
    "edu" -> "Education 方案"
    else -> "Codex 账户"
}

private fun usagePeriodLabel(period: String, durationMins: Long?): String {
    if (period == "spend") return "费用周期"
    return when {
        durationMins == null -> if (period == "primary") "短周期额度" else "长周期额度"
        durationMins < 60 -> "$durationMins 分钟额度"
        durationMins < 24 * 60 -> "${durationMins / 60} 小时额度"
        durationMins < 7 * 24 * 60 -> "${durationMins / (24 * 60)} 天额度"
        else -> "${durationMins / (7 * 24 * 60)} 周额度"
    }
}

private fun formatResetTime(timestampSeconds: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestampSeconds * 1000))

private fun formatLargeNumber(value: Long?): String = when {
    value == null -> "—"
    value >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.1fB", value / 1_000_000_000.0)
    value >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", value / 1_000.0)
    else -> value.toString()
}

private fun formatCacheSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatTime(timestampSeconds: Long): String {
    if (timestampSeconds <= 0) return ""
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestampSeconds * 1000))
}
