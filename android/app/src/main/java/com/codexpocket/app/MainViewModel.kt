package com.codexpocket.app

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.codexpocket.app.cache.MessageCacheStore
import com.codexpocket.app.cache.excludeDiscardedLocalMessages
import com.codexpocket.app.cache.mergeChatMessages
import com.codexpocket.app.cache.mergeMessageWindows
import com.codexpocket.app.cache.MessageCacheStats
import com.codexpocket.app.media.PocketMediaLoader
import com.codexpocket.app.model.ActivityEntry
import com.codexpocket.app.model.AccountStatus
import com.codexpocket.app.model.AutomationSummary
import com.codexpocket.app.model.ChatMessage
import com.codexpocket.app.model.CodexModeOption
import com.codexpocket.app.model.ConnectionState
import com.codexpocket.app.model.DirectoryEntry
import com.codexpocket.app.model.MediaAttachment
import com.codexpocket.app.model.ModelOption
import com.codexpocket.app.model.PendingApproval
import com.codexpocket.app.model.PendingImage
import com.codexpocket.app.model.PermissionProfileOption
import com.codexpocket.app.model.ReasoningEffortOption
import com.codexpocket.app.model.ServiceTierOption
import com.codexpocket.app.model.ThreadGoal
import com.codexpocket.app.model.ThreadSummary
import com.codexpocket.app.model.UiState
import com.codexpocket.app.model.UsageLimit
import com.codexpocket.app.model.parseChatMessages
import com.codexpocket.app.model.parseChatMessage
import com.codexpocket.app.network.BridgeClient
import com.codexpocket.app.network.ImageUploader
import com.codexpocket.app.notification.TaskNotificationService
import com.codexpocket.app.notification.TaskNotifications
import com.codexpocket.app.ui.smoothStreamChunkCodePoints
import com.codexpocket.app.ui.takeCodePointPrefix
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainViewModel(application: Application) : AndroidViewModel(application), BridgeClient.Listener {
    private val preferences = application.getSharedPreferences("codex-pocket", 0)
    private val client = BridgeClient(application, this)
    private val imageUploader = ImageUploader(application.contentResolver)
    private val messageCache = MessageCacheStore(application.cacheDir)
    private val initialCacheStats = messageCache.stats().let { stats ->
        stats.copy(bytes = stats.bytes + PocketMediaLoader.sizeBytes(application))
    }
    private val cacheWriteJobs = ConcurrentHashMap<String, Job>()
    private val processItemScrollOffsets = ConcurrentHashMap<String, Int>()
    private val chatScrollPositions = ConcurrentHashMap<String, Pair<String, Int>>()
    private val discardedLocalMessageIds = preferences
        .getStringSet(DISCARDED_LOCAL_MESSAGES_PREFERENCE, emptySet())
        .orEmpty()
        .toMutableSet()
    private var threadLoadGeneration = 0L
    private var activeThreadSyncJob: Job? = null
    private var activeThreadSyncThreadId: String? = null
    private var activeThreadSyncInFlight = false
    private val agentStreamBuffers = mutableMapOf<String, StringBuilder>()
    private val agentStreamJobs = mutableMapOf<String, Job>()
    private val pendingAgentCompletions = mutableMapOf<String, JSONObject>()
    private var appInForeground = false
    private var pausedForBackground = false
    private val _state = MutableStateFlow(
        UiState(
            endpoint = preferences.getString("endpoint", BuildConfig.BRIDGE_ENDPOINT)
                ?: BuildConfig.BRIDGE_ENDPOINT,
            token = preferences.getString("token", BuildConfig.BRIDGE_TOKEN)
                ?: BuildConfig.BRIDGE_TOKEN,
            completionNotificationsEnabled = preferences.getBoolean(
                TaskNotificationService.ENABLED_PREFERENCE,
                false,
            ),
            messageFontSizeSp = preferences.getFloat("message-font-size-sp", 15f).coerceIn(12f, 20f),
            compactChatEnabled = preferences.getBoolean("compact-chat", false),
            selectedMode = preferences.getString("mode", "default") ?: "default",
            fastModeEnabled = preferences.getBoolean("fast-mode", false),
            defaultPermissionProfile = preferences.getString(
                "default-permission-profile",
                ":danger-full-access",
            ) ?: ":danger-full-access",
            messageCacheThreadCount = initialCacheStats.threadCount,
            messageCacheBytes = initialCacheStats.bytes,
            expandedProcessGroups = preferences
                .getStringSet(EXPANDED_PROCESS_GROUPS_PREFERENCE, emptySet())
                .orEmpty()
                .toSet(),
            expandedProcessItems = preferences
                .getStringSet(EXPANDED_PROCESS_ITEMS_PREFERENCE, emptySet())
                .orEmpty()
                .toSet(),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        if (_state.value.token.isNotBlank()) {
            connect()
        }
    }

    fun setEndpoint(value: String) = _state.update { it.copy(endpoint = value) }
    fun setToken(value: String) = _state.update { it.copy(token = value) }
    fun setInput(value: String) = _state.update { it.copy(input = value) }

    fun setProcessGroupExpanded(key: String, expanded: Boolean) {
        if (key.isBlank()) return
        _state.update { state ->
            val updated = state.expandedProcessGroups.withExpansion(key, expanded)
            preferences.edit { putStringSet(EXPANDED_PROCESS_GROUPS_PREFERENCE, updated) }
            state.copy(expandedProcessGroups = updated)
        }
    }

    fun setProcessItemExpanded(key: String, expanded: Boolean) {
        if (key.isBlank()) return
        _state.update { state ->
            val updated = state.expandedProcessItems.withExpansion(key, expanded)
            preferences.edit { putStringSet(EXPANDED_PROCESS_ITEMS_PREFERENCE, updated) }
            state.copy(expandedProcessItems = updated)
        }
    }

    fun processItemScrollOffset(key: String): Int = processItemScrollOffsets[key]
        ?: preferences.getInt("$PROCESS_ITEM_SCROLL_PREFIX${key.preferenceSuffix()}", 0).also {
            processItemScrollOffsets[key] = it
        }

    fun saveProcessItemScrollOffset(key: String, offset: Int) {
        if (key.isBlank()) return
        val safeOffset = offset.coerceAtLeast(0)
        processItemScrollOffsets[key] = safeOffset
        preferences.edit {
            putInt("$PROCESS_ITEM_SCROLL_PREFIX${key.preferenceSuffix()}", safeOffset)
        }
    }

    fun chatScrollPosition(threadId: String): Pair<String, Int>? = chatScrollPositions[threadId]
        ?: preferences.getString(
            "$CHAT_SCROLL_ITEM_PREFIX${threadId.preferenceSuffix()}",
            null,
        )?.let { itemKey ->
            val restored = itemKey to preferences.getInt(
                "$CHAT_SCROLL_OFFSET_PREFIX${threadId.preferenceSuffix()}",
                0,
            )
            chatScrollPositions[threadId] = restored
            restored
        }

    fun saveChatScrollPosition(threadId: String, itemKey: String, offset: Int) {
        if (threadId.isNotBlank() && itemKey.isNotBlank()) {
            val safeOffset = offset.coerceAtLeast(0)
            chatScrollPositions[threadId] = itemKey to safeOffset
            preferences.edit {
                putString("$CHAT_SCROLL_ITEM_PREFIX${threadId.preferenceSuffix()}", itemKey)
                putInt("$CHAT_SCROLL_OFFSET_PREFIX${threadId.preferenceSuffix()}", safeOffset)
            }
        }
    }

    private fun String.preferenceSuffix(): String = hashCode().toUInt().toString(16)

    private fun Set<String>.withExpansion(key: String, expanded: Boolean): Set<String> {
        val updated = toMutableSet()
        if (expanded) updated += key else updated -= key
        while (updated.size > MAX_REMEMBERED_DISCLOSURES) updated.remove(updated.first())
        return updated
    }

    fun addPendingImages(uriStrings: List<String>) {
        val existing = _state.value.pendingImages
        val remaining = MAX_IMAGES_PER_MESSAGE - existing.size
        if (remaining <= 0) {
            fail("每次最多发送 4 张图片")
            return
        }
        val resolver = getApplication<Application>().contentResolver
        var oversizedName: String? = null
        val additions = uriStrings.distinct().take(remaining).mapNotNull { source ->
            val uri = runCatching { Uri.parse(source) }.getOrNull() ?: return@mapNotNull null
            val mimeType = resolver.getType(uri).orEmpty().ifBlank { "image/jpeg" }
            if (!mimeType.startsWith("image/")) return@mapNotNull null
            var displayName = "图片"
            var sizeBytes: Long? = null
            runCatching {
                resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
            if (sizeBytes != null && sizeBytes!! > MAX_IMAGE_BYTES) {
                oversizedName = displayName
                return@mapNotNull null
            }
            PendingImage(
                id = UUID.randomUUID().toString(),
                uri = source,
                name = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
            )
        }
        _state.update { state ->
            state.copy(
                pendingImages = (state.pendingImages + additions)
                    .distinctBy { it.uri }
                    .take(MAX_IMAGES_PER_MESSAGE),
                error = null,
            )
        }
        oversizedName?.let { fail("$it 超过 15 MB，请压缩后再发送") }
    }

    fun removePendingImage(id: String) {
        if (_state.value.isUploadingImages) return
        _state.update { state -> state.copy(pendingImages = state.pendingImages.filterNot { it.id == id }) }
    }

    fun setMessageFontSize(value: Float) {
        val resolved = value.coerceIn(12f, 20f)
        preferences.edit { putFloat("message-font-size-sp", resolved) }
        _state.update { it.copy(messageFontSizeSp = resolved) }
    }

    fun setCompactChatEnabled(enabled: Boolean) {
        preferences.edit { putBoolean("compact-chat", enabled) }
        _state.update { it.copy(compactChatEnabled = enabled) }
    }

    fun selectDefaultPermissionProfile(profileId: String) {
        val profile = _state.value.permissionProfiles.firstOrNull { it.id == profileId }
        if (profile != null && !profile.allowed) {
            fail("这台 Mac 的管理策略不允许使用该权限")
            return
        }
        preferences.edit { putString("default-permission-profile", profileId) }
        _state.update { it.copy(defaultPermissionProfile = profileId, error = null) }
    }

    fun selectModel(modelId: String) {
        _state.update { state ->
            val model = state.models.firstOrNull { it.id == modelId } ?: return@update state
            val effort = state.selectedEffort.takeIf { current ->
                model.efforts.any { it.id == current }
            } ?: model.defaultEffort
            preferences.edit {
                putString("model", model.id)
                putString("effort", effort)
            }
            state.copy(
                selectedModel = model.id,
                selectedEffort = effort,
                fastModeEnabled = state.fastModeEnabled && model.serviceTiers.any { it.id == "priority" },
            )
        }
    }

    fun selectEffort(effort: String) {
        preferences.edit { putString("effort", effort) }
        _state.update { it.copy(selectedEffort = effort) }
    }

    fun selectMode(mode: String) {
        if (mode != "default" && mode != "plan") return
        val current = _state.value
        val effort = if (mode == "plan") "medium" else current.selectedEffort
        preferences.edit { putString("mode", mode) }
        val thread = current.selectedThread
        if (thread == null) {
            _state.update { it.copy(selectedMode = mode, selectedEffort = effort) }
            return
        }
        _state.update { it.copy(isModeUpdating = true, error = null) }
        client.request(
            "thread.mode.set",
            JSONObject()
                .put("threadId", thread.id)
                .put("mode", mode)
                .put("model", current.selectedModel)
                .put("effort", effort),
        ) { result ->
            onMain {
                result.fold(
                    onSuccess = {
                        _state.update { state ->
                            state.copy(
                                selectedMode = mode,
                                selectedEffort = effort,
                                isModeUpdating = false,
                            )
                        }
                    },
                    onFailure = {
                        _state.update { state -> state.copy(isModeUpdating = false) }
                        fail(it.message ?: "无法切换模式")
                    },
                )
            }
        }
    }

    fun setFastModeEnabled(enabled: Boolean) {
        val current = _state.value
        val supportsFast = current.models.firstOrNull { it.id == current.selectedModel }
            ?.serviceTiers?.any { it.id == "priority" } == true
        if (enabled && !supportsFast) {
            fail("当前模型不支持 Fast 加速")
            return
        }
        preferences.edit { putBoolean("fast-mode", enabled) }
        val thread = current.selectedThread
        if (thread == null) {
            _state.update { it.copy(fastModeEnabled = enabled) }
            return
        }
        _state.update { it.copy(isModeUpdating = true, error = null) }
        client.request(
            "thread.fast.set",
            JSONObject().put("threadId", thread.id).put("enabled", enabled),
        ) { result ->
            onMain {
                result.fold(
                    onSuccess = {
                        _state.update { state -> state.copy(fastModeEnabled = enabled, isModeUpdating = false) }
                    },
                    onFailure = {
                        _state.update { state -> state.copy(isModeUpdating = false) }
                        fail(it.message ?: "无法切换 Fast 模式")
                    },
                )
            }
        }
    }

    fun setGoal(objective: String, tokenBudget: Long?) {
        val thread = _state.value.selectedThread ?: return
        val params = JSONObject()
            .put("threadId", thread.id)
            .put("objective", objective.trim())
        tokenBudget?.takeIf { it > 0 }?.let { params.put("tokenBudget", it) }
        _state.update { it.copy(isModeUpdating = true, error = null) }
        client.request("thread.goal.set", params) { result ->
            onMain {
                result.fold(
                    onSuccess = { payload ->
                        _state.update {
                            it.copy(
                                goal = parseGoal(payload.optJSONObject("goal")),
                                isModeUpdating = false,
                            )
                        }
                        refreshThreads()
                    },
                    onFailure = {
                        _state.update { state -> state.copy(isModeUpdating = false) }
                        fail(it.message ?: "无法启动目标模式")
                    },
                )
            }
        }
    }

    fun setGoalPaused(paused: Boolean) {
        val thread = _state.value.selectedThread ?: return
        _state.update { it.copy(isModeUpdating = true, error = null) }
        client.request(
            "thread.goal.status",
            JSONObject()
                .put("threadId", thread.id)
                .put("status", if (paused) "paused" else "active"),
        ) { result ->
            onMain {
                result.fold(
                    onSuccess = { payload ->
                        _state.update {
                            it.copy(goal = parseGoal(payload.optJSONObject("goal")), isModeUpdating = false)
                        }
                    },
                    onFailure = {
                        _state.update { state -> state.copy(isModeUpdating = false) }
                        fail(it.message ?: "无法更新目标状态")
                    },
                )
            }
        }
    }

    fun clearGoal() {
        val thread = _state.value.selectedThread ?: return
        _state.update { it.copy(isModeUpdating = true, error = null) }
        client.request("thread.goal.clear", JSONObject().put("threadId", thread.id)) { result ->
            onMain {
                result.fold(
                    onSuccess = {
                        _state.update { it.copy(goal = null, isModeUpdating = false) }
                        refreshThreads()
                    },
                    onFailure = {
                        _state.update { state -> state.copy(isModeUpdating = false) }
                        fail(it.message ?: "无法清除目标")
                    },
                )
            }
        }
    }

    fun connect() {
        val endpoint = _state.value.endpoint.trim()
        val token = _state.value.token.trim()
        if (!endpoint.startsWith("ws://") && !endpoint.startsWith("wss://")) {
            _state.update { it.copy(error = "地址必须以 ws:// 或 wss:// 开头") }
            return
        }
        if (token.isBlank()) {
            _state.update { it.copy(error = "请输入配对令牌") }
            return
        }
        preferences.edit {
            putString("endpoint", endpoint)
            putString("token", token)
        }
        _state.update { it.copy(connection = ConnectionState.Connecting, isReconnecting = false, error = null) }
        client.connect(endpoint, token)
    }

    fun setCompletionNotificationsEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(TaskNotificationService.ENABLED_PREFERENCE, enabled) }
        _state.update { it.copy(completionNotificationsEnabled = enabled, error = null) }
        if (!appInForeground) TaskNotificationService.ensureRunning(getApplication())
    }

    fun onAppForeground() {
        if (appInForeground) return
        appInForeground = true
        TaskNotificationService.stop(getApplication())
        if (pausedForBackground && _state.value.token.isNotBlank()) {
            pausedForBackground = false
            connect()
        }
    }

    fun onAppBackground() {
        if (!appInForeground) return
        appInForeground = false
        if (_state.value.token.isBlank() || _state.value.endpoint.isBlank()) return
        pausedForBackground = true
        stopActiveThreadSync()
        clearAgentStreams()
        client.disconnect()
        TaskNotificationService.ensureRunning(getApplication())
    }

    fun sendTestNotification() {
        if (!_state.value.completionNotificationsEnabled) return
        if (!TaskNotificationService.sendTest(getApplication())) {
            fail("系统通知目前被关闭，请在小米通知设置中允许 Codex Pocket 显示通知。")
        }
    }

    fun reportNotificationPermissionDenied() {
        fail("没有通知权限，无法显示任务完成提醒。可以在小米系统设置中重新允许。")
    }

    fun openNotificationSettings() {
        TaskNotifications.openSystemSettings(getApplication())
    }

    fun disconnect() {
        pausedForBackground = false
        TaskNotificationService.stop(getApplication())
        cacheCurrentMessages(delayMillis = 0)
        clearAgentStreams()
        client.disconnect()
        _state.update {
            it.copy(
                connection = ConnectionState.Disconnected,
                isReconnecting = false,
                isSyncing = false,
                selectedThread = null,
                messages = emptyList(),
                activeTurnId = null,
                currentStatus = "",
                statusDetail = "",
                activities = emptyList(),
                pendingApproval = null,
                goal = null,
                pendingImages = emptyList(),
                isUploadingImages = false,
                hasOlderMessages = false,
                isLoadingOlderMessages = false,
            )
        }
    }

    fun refreshThreads() {
        _state.update { it.copy(isLoading = true, error = null) }
        client.request("threads.list", JSONObject().put("limit", 50)) { result ->
            onMain {
                result.fold(
                    onSuccess = { payload ->
                        val threads = parseThreads(payload.optJSONArray("threads") ?: JSONArray())
                        _state.update { it.copy(threads = threads, isLoading = false) }
                    },
                    onFailure = { fail(it.message ?: "无法读取任务") },
                )
            }
        }
    }

    fun createThread(cwd: String) {
        val path = cwd.trim()
        if (!path.startsWith("/")) {
            fail("请选择 Mac 上以 / 开头的项目目录")
            return
        }
        val current = _state.value
        _state.update { it.copy(isCreatingThread = true, error = null) }
        client.request(
            "thread.create",
            JSONObject()
                .put("cwd", path)
                .put("model", current.selectedModel)
                .put("effort", current.selectedEffort)
                .put("mode", current.selectedMode)
                .put("fastMode", current.fastModeEnabled)
                .put("permissionProfile", current.defaultPermissionProfile),
        ) { result ->
            onMain {
                result.fold(
                    onSuccess = { payload ->
                        val threadPayload = payload.optJSONObject("thread")
                        val thread = parseThread(threadPayload)
                        if (thread == null) {
                            _state.update { it.copy(isCreatingThread = false) }
                            fail("Codex 创建了任务，但没有返回任务信息")
                        } else {
                            _state.update {
                                it.copy(
                                    isCreatingThread = false,
                                    isDirectoryBrowserOpen = false,
                                    directoryEntries = emptyList(),
                                    threads = listOf(thread) + it.threads.filterNot { old -> old.id == thread.id },
                                )
                            }
                            openThread(thread)
                        }
                    },
                    onFailure = {
                        _state.update { state -> state.copy(isCreatingThread = false) }
                        fail(it.message ?: "无法创建任务")
                    },
                )
            }
        }
    }

    fun openDirectoryBrowser(startPath: String) {
        _state.update { it.copy(isDirectoryBrowserOpen = true) }
        browseDirectory(startPath.ifBlank { "/Users" })
    }

    fun browseDirectory(path: String) {
        _state.update { it.copy(isDirectoryLoading = true, error = null) }
        client.request("directories.list", JSONObject().put("path", path)) { result ->
            onMain {
                result.fold(
                    onSuccess = { payload ->
                        val entries = buildList {
                            val source = payload.optJSONArray("directories") ?: JSONArray()
                            for (index in 0 until source.length()) {
                                val item = source.optJSONObject(index) ?: continue
                                add(
                                    DirectoryEntry(
                                        name = item.optString("name"),
                                        path = item.optString("path"),
                                    ),
                                )
                            }
                        }
                        _state.update {
                            it.copy(
                                directoryBrowserPath = payload.optString("path", path),
                                directoryEntries = entries,
                                isDirectoryLoading = false,
                            )
                        }
                    },
                    onFailure = {
                        _state.update { state -> state.copy(isDirectoryLoading = false) }
                        fail(it.message ?: "无法读取这个文件夹")
                    },
                )
            }
        }
    }

    fun closeDirectoryBrowser() = _state.update {
        it.copy(isDirectoryBrowserOpen = false, isDirectoryLoading = false)
    }

    private fun loadModels() {
        client.request("models.list", JSONObject().put("limit", 100)) { result ->
            onMain {
                result.fold(
                    onSuccess = { payload ->
                        val models = parseModels(payload.optJSONArray("models") ?: JSONArray())
                        _state.update { state ->
                            val preferredModel = preferences.getString("model", null)
                            val selected = models.firstOrNull { it.id == state.selectedModel }
                                ?: models.firstOrNull { it.id == preferredModel }
                                ?: models.firstOrNull { it.isDefault }
                                ?: models.firstOrNull()
                            val preferredEffort = preferences.getString("effort", null)
                            val effort = state.selectedEffort.takeIf { current ->
                                selected?.efforts?.any { it.id == current } == true
                            } ?: preferredEffort?.takeIf { saved ->
                                selected?.efforts?.any { it.id == saved } == true
                            } ?: selected?.defaultEffort.orEmpty()
                            state.copy(
                                models = models,
                                selectedModel = selected?.id.orEmpty(),
                                selectedEffort = effort,
                            )
                        }
                    },
                    onFailure = { fail(it.message ?: "无法读取模型列表") },
                )
            }
        }
    }

    private fun loadModes() {
        client.request("modes.list") { result ->
            onMain {
                result.fold(
                    onSuccess = { payload ->
                        val modes = buildList {
                            val source = payload.optJSONArray("modes") ?: JSONArray()
                            for (index in 0 until source.length()) {
                                val item = source.optJSONObject(index) ?: continue
                                add(
                                    CodexModeOption(
                                        id = item.optString("id"),
                                        name = item.optString("name"),
                                        effort = item.optString("effort").ifBlank { null },
                                    ),
                                )
                            }
                        }
                        _state.update { it.copy(modes = modes) }
                    },
                    onFailure = { fail(it.message ?: "无法读取模式列表") },
                )
            }
        }
    }

    fun loadPermissionProfiles() {
        _state.update { it.copy(isPermissionsLoading = true) }
        client.request("permissions.list", JSONObject().put("limit", 100)) { result ->
            onMain {
                result.fold(
                    onSuccess = { payload ->
                        val profiles = buildList {
                            val source = payload.optJSONArray("profiles") ?: JSONArray()
                            for (index in 0 until source.length()) {
                                val item = source.optJSONObject(index) ?: continue
                                val id = item.optString("id")
                                if (id.isBlank()) continue
                                add(
                                    PermissionProfileOption(
                                        id = id,
                                        description = item.optString("description").ifBlank { null },
                                        allowed = item.optBoolean("allowed", true),
                                    ),
                                )
                            }
                        }
                        _state.update { state ->
                            val selected = profiles.firstOrNull {
                                it.id == state.defaultPermissionProfile && it.allowed
                            }?.id ?: profiles.firstOrNull {
                                it.id == ":danger-full-access" && it.allowed
                            }?.id ?: profiles.firstOrNull { it.allowed }?.id
                                ?: state.defaultPermissionProfile
                            if (selected != state.defaultPermissionProfile) {
                                preferences.edit { putString("default-permission-profile", selected) }
                            }
                            state.copy(
                                permissionProfiles = profiles,
                                defaultPermissionProfile = selected,
                                isPermissionsLoading = false,
                            )
                        }
                    },
                    onFailure = {
                        _state.update { state -> state.copy(isPermissionsLoading = false) }
                        fail(it.message ?: "无法读取运行权限")
                    },
                )
            }
        }
    }

    fun openThread(thread: ThreadSummary) = loadThread(thread, preserveVisibleContent = false)

    private fun refreshOpenThread(thread: ThreadSummary) =
        loadThread(thread, preserveVisibleContent = true)

    private fun loadThread(thread: ThreadSummary, preserveVisibleContent: Boolean) {
        if (!preserveVisibleContent) {
            cacheCurrentMessages(delayMillis = 0)
            clearAgentStreams()
        }
        val generation = ++threadLoadGeneration
        val visibleMessages = _state.value.messages.takeIf { preserveVisibleContent }.orEmpty()
        _state.update {
            if (preserveVisibleContent) {
                it.copy(
                    selectedThread = thread,
                    isLoading = false,
                    isSyncing = true,
                    error = null,
                )
            } else {
                it.copy(
                    selectedThread = thread,
                    messages = emptyList(),
                    isLoading = true,
                    isSyncing = true,
                    error = null,
                    isSending = false,
                    activeTurnId = null,
                    currentStatus = "",
                    statusDetail = "",
                    activities = emptyList(),
                    pendingApproval = null,
                    goal = null,
                    pendingImages = emptyList(),
                    isUploadingImages = false,
                    hasOlderMessages = false,
                    isLoadingOlderMessages = false,
                )
            }
        }
        viewModelScope.launch {
            val cached = (if (preserveVisibleContent) {
                visibleMessages
            } else {
                withContext(Dispatchers.IO) { messageCache.read(thread.id) }
            }).let { messages ->
                excludeDiscardedLocalMessages(messages, discardedLocalMessageIds)
            }
            if (generation != threadLoadGeneration || _state.value.selectedThread?.id != thread.id) {
                return@launch
            }
            if (cached.isNotEmpty()) {
                _state.update { it.copy(messages = cached, isLoading = false) }
            }
            prefetchMessageImages(cached)

            val pendingClientMessageIds = (cached + _state.value.messages)
                .asSequence()
                .filter { it.isLocalSubmission() }
                .map { it.id }
                .filter(String::isNotBlank)
                .distinct()
                .take(20)
                .toList()
            val params = JSONObject()
                .put("threadId", thread.id)
                .put("messageLimit", MessageCacheStore.LATEST_SYNC_MESSAGE_COUNT)
                .put("clientMessageIds", JSONArray(pendingClientMessageIds))
            client.request("thread.read", params) { result ->
                onMain {
                    if (generation != threadLoadGeneration || _state.value.selectedThread?.id != thread.id) {
                        return@onMain
                    }
                    result.fold(
                        onSuccess = { payload ->
                            val fresh = parseChatMessages(payload.optJSONArray("messages") ?: JSONArray())
                            val settings = payload.optJSONObject("settings")
                            val threadPayload = payload.optJSONObject("thread")
                            val goal = parseGoal(payload.optJSONObject("goal"))
                            val freshIds = fresh.mapTo(HashSet()) { it.id }
                            val confirmedClientMessageIds = payload
                                .optJSONArray("confirmedClientMessageIds")
                                .toStringList()
                                .toHashSet()
                            val currentMessages = _state.value.messages
                            val localSubmissions = (cached + currentMessages)
                                .filter { it.isLocalSubmission() }
                                .distinctBy { it.id }
                            val newlyFailed = localSubmissions.any { message ->
                                message.deliveryState == "sending" &&
                                    message.id !in freshIds && message.id !in confirmedClientMessageIds
                            }
                            val unresolvedLocalSubmissions = localSubmissions
                                .filter { it.id !in freshIds && it.id !in confirmedClientMessageIds }
                                .map { message ->
                                    if (message.deliveryState == "failed") message
                                    else message.copy(deliveryState = "failed")
                                }
                            val cachedWithoutLocalSubmissions = cached.filterNot { it.isLocalSubmission() }
                            val cachedIds = cachedWithoutLocalSubmissions.mapTo(HashSet()) { it.id }
                            val liveMessages = _state.value.messages.filter { message ->
                                !message.isLocalSubmission() && message.id !in freshIds &&
                                    (message.isStreaming || message.id !in cachedIds)
                            }
                            val mergedMessages = preserveSmoothedAgentText(
                                currentMessages,
                                mergeMessageWindows(
                                    MessageCacheStore.MAX_MESSAGES_PER_THREAD,
                                    cachedWithoutLocalSubmissions,
                                    fresh,
                                    liveMessages,
                                    unresolvedLocalSubmissions,
                                ),
                            )
                            _state.update { state ->
                                val model = settings?.optString("model").orEmpty()
                                    .takeIf(String::isNotBlank) ?: state.selectedModel
                                val modelOption = state.models.firstOrNull { it.id == model }
                                val effort = settings?.optString("effort").orEmpty()
                                    .takeIf(String::isNotBlank)
                                    ?: state.selectedEffort.takeIf { current ->
                                        modelOption?.efforts?.any { it.id == current } == true
                                    }
                                    ?: modelOption?.defaultEffort.orEmpty()
                                state.copy(
                                    messages = mergedMessages,
                                    isLoading = false,
                                    isSyncing = false,
                                    isSending = threadPayload?.optString("status") == "active",
                                    activeTurnId = payload.optString("activeTurnId").takeIf { it.isNotBlank() },
                                    currentStatus = if (threadPayload?.optString("status") == "active") {
                                        "Codex 正在处理…"
                                    } else {
                                        ""
                                    },
                                    selectedModel = model,
                                    selectedEffort = effort,
                                    selectedMode = settings?.optString("mode", "default") ?: "default",
                                    fastModeEnabled = settings?.optString("serviceTier") == "priority",
                                    currentPermissionProfile = settings?.optString("permissionProfile")
                                        ?.ifBlank { null },
                                    goal = goal,
                                    hasOlderMessages = payload.optBoolean("hasOlderMessages"),
                                    isLoadingOlderMessages = false,
                                    error = if (newlyFailed) {
                                        "有一条消息没有送达 Mac，已标记为发送失败，可以点击重试。"
                                    } else {
                                        state.error
                                    },
                                )
                            }
                            scheduleMessageCache(thread.id, mergedMessages)
                            prefetchMessageImages(mergedMessages)
                            if (threadPayload?.optString("status") == "active") {
                                startActiveThreadSync(thread.id)
                            } else {
                                stopActiveThreadSync(thread.id)
                            }
                        },
                        onFailure = {
                            _state.update { state -> state.copy(isSyncing = false, isLoading = false) }
                            fail(it.message ?: "无法读取任务内容")
                        },
                    )
                }
            }
        }
    }

    fun closeThread() = closeThread(preserveCache = true)

    private fun closeThread(preserveCache: Boolean) {
        if (preserveCache) cacheCurrentMessages(delayMillis = 0)
        stopActiveThreadSync()
        clearAgentStreams()
        threadLoadGeneration += 1
        _state.update {
            it.copy(
                selectedThread = null,
                messages = emptyList(),
                isSyncing = false,
                activeTurnId = null,
                error = null,
                isSending = false,
                currentStatus = "",
                statusDetail = "",
                activities = emptyList(),
                pendingApproval = null,
                goal = null,
                pendingImages = emptyList(),
                isUploadingImages = false,
                hasOlderMessages = false,
                isLoadingOlderMessages = false,
            )
        }
        refreshThreads()
    }

    fun loadOlderMessages() {
        val current = _state.value
        val thread = current.selectedThread ?: return
        val beforeMessageId = current.messages.firstOrNull()?.id?.takeIf(String::isNotBlank) ?: return
        if (!current.hasOlderMessages || current.isLoadingOlderMessages) return
        _state.update { it.copy(isLoadingOlderMessages = true) }
        viewModelScope.launch {
            val cachedPage = withContext(Dispatchers.IO) {
                messageCache.readBefore(
                    thread.id,
                    beforeMessageId,
                    MessageCacheStore.LATEST_SYNC_MESSAGE_COUNT,
                )
            }
            if (_state.value.selectedThread?.id != thread.id) return@launch
            if (cachedPage.messages.isNotEmpty()) {
                val merged = mergeMessageWindows(
                    MessageCacheStore.MAX_MESSAGES_PER_THREAD,
                    cachedPage.messages,
                    _state.value.messages,
                )
                _state.update { it.copy(messages = merged, isLoadingOlderMessages = false) }
                prefetchMessageImages(cachedPage.messages)
                return@launch
            }

            client.request(
                "thread.read",
                JSONObject()
                    .put("threadId", thread.id)
                    .put("messageLimit", MessageCacheStore.LATEST_SYNC_MESSAGE_COUNT)
                    .put("beforeMessageId", beforeMessageId),
            ) { result ->
                onMain {
                    if (_state.value.selectedThread?.id != thread.id) return@onMain
                    result.fold(
                        onSuccess = { payload ->
                            val older = parseChatMessages(payload.optJSONArray("messages") ?: JSONArray())
                            val merged = mergeMessageWindows(
                                MessageCacheStore.MAX_MESSAGES_PER_THREAD,
                                older,
                                _state.value.messages,
                            )
                            _state.update {
                                it.copy(
                                    messages = merged,
                                    hasOlderMessages = payload.optBoolean("hasOlderMessages") &&
                                        payload.optBoolean("cursorFound", true),
                                    isLoadingOlderMessages = false,
                                )
                            }
                            scheduleMessageCache(thread.id, merged)
                            prefetchMessageImages(older)
                        },
                        onFailure = { error ->
                            _state.update { it.copy(isLoadingOlderMessages = false) }
                            fail(error.message ?: "无法读取更早消息")
                        },
                    )
                }
            }
        }
    }

    fun sendMessage() {
        val current = _state.value
        val text = current.input.trim()
        val images = current.pendingImages
        if (current.isUploadingImages || (text.isBlank() && images.isEmpty())) return
        submitUserMessage(current, text, images, steering = current.isSending)
    }

    private fun submitUserMessage(
        current: UiState,
        text: String,
        images: List<PendingImage>,
        steering: Boolean,
        clearComposer: Boolean = true,
    ) {
        val thread = current.selectedThread ?: return
        val expectedTurnId = current.activeTurnId
        if (steering && expectedTurnId.isNullOrBlank()) {
            fail("Codex 正在运行，但当前回合尚未同步完成，请稍后再发送引导")
            return
        }
        val clientMessageId = UUID.randomUUID().toString()
        val localMessage = ChatMessage(
            id = clientMessageId,
            turnId = expectedTurnId ?: "pending",
            role = "user",
            text = text,
            kind = "userMessage",
            deliveryState = "sending",
            attachments = images.map { image ->
                MediaAttachment(
                    id = image.id,
                    kind = "image",
                    source = image.uri,
                    name = image.name,
                    mimeType = image.mimeType,
                    isLocal = false,
                )
            },
        )
        _state.update {
            it.copy(
                input = if (clearComposer) "" else it.input,
                pendingImages = if (clearComposer) emptyList() else it.pendingImages,
                messages = it.messages + localMessage,
                isSending = true,
                isUploadingImages = images.isNotEmpty(),
                currentStatus = when {
                    images.isNotEmpty() -> "正在上传图片…"
                    steering -> "已发送引导…"
                    else -> "正在提交任务…"
                },
                statusDetail = if (images.isNotEmpty()) "${images.size} 张图片将通过 Tailscale 发送"
                else if (steering) "Codex 将在当前任务中调整方向" else "等待 Codex 接收",
                activities = if (steering) it.activities
                else listOf(ActivityEntry("turn-pending", "正在提交任务")),
                pendingApproval = null,
                error = null,
            )
        }
        cacheCurrentMessages()
        viewModelScope.launch {
            val uploaded = runCatching {
                images.map { image ->
                    imageUploader.upload(
                        endpoint = current.endpoint,
                        token = current.token,
                        uri = Uri.parse(image.uri),
                        displayName = image.name,
                        mimeType = image.mimeType,
                        sizeBytes = image.sizeBytes,
                    )
                }
            }.getOrElse { error ->
                restoreFailedSubmission(thread.id, clientMessageId, steering)
                fail(error.message ?: "图片上传失败")
                return@launch
            }

            if (_state.value.selectedThread?.id == thread.id) {
                _state.update {
                    it.copy(
                        isUploadingImages = false,
                        currentStatus = if (steering) "正在发送引导…" else "正在提交任务…",
                        statusDetail = "等待 Codex 接收",
                    )
                }
            }
            val params = JSONObject()
                .put("threadId", thread.id)
                .put("text", text)
                .put("clientMessageId", clientMessageId)
                .put("images", JSONArray().also { array ->
                    uploaded.forEach { image ->
                        array.put(
                            JSONObject()
                                .put("path", image.path)
                                .put("name", image.name)
                                .put("mimeType", image.mimeType),
                        )
                    }
                })
            val method = if (steering) {
                params.put("turnId", expectedTurnId)
                "turn.steer"
            } else {
                params
                    .put("model", current.selectedModel)
                    .put("effort", current.selectedEffort)
                    .put("mode", current.selectedMode)
                    .put("fastMode", current.fastModeEnabled)
                    .put("permissionProfile", current.defaultPermissionProfile)
                "turn.start"
            }
            client.request(method, params) { result ->
                onMain {
                    result.fold(
                        onSuccess = { payload ->
                            if (_state.value.selectedThread?.id == thread.id) {
                                _state.update {
                                    val turnId = payload.optString("turnId").ifBlank { it.activeTurnId }
                                    it.copy(
                                        messages = it.messages.map { message ->
                                            if (message.id == clientMessageId) {
                                                message.copy(
                                                    turnId = turnId ?: message.turnId,
                                                    deliveryState = null,
                                                )
                                            } else {
                                                message
                                            }
                                        },
                                        isUploadingImages = false,
                                        activeTurnId = turnId,
                                        currentStatus = if (steering) "正在按新指令调整…" else "正在思考…",
                                        statusDetail = if (steering) "引导消息已同步到当前回合"
                                        else "Codex 已接收任务",
                                    )
                                }
                            }
                        },
                        onFailure = { error ->
                            reconcileSubmissionAfterUncertainFailure(
                                threadId = thread.id,
                                clientMessageId = clientMessageId,
                                steering = steering,
                                failureMessage = error.message
                                    ?: if (steering) "无法引导当前任务" else "发送失败",
                            )
                        },
                    )
                }
            }
        }
    }

    private fun reconcileSubmissionAfterUncertainFailure(
        threadId: String,
        clientMessageId: String,
        steering: Boolean,
        failureMessage: String,
        attempt: Int = 0,
    ) {
        if (_state.value.selectedThread?.id != threadId) return
        _state.update { state ->
            state.copy(
                currentStatus = "正在确认消息是否送达…",
                statusDetail = "网络回执丢失，正在向 Mac 校验",
                error = null,
            )
        }
        viewModelScope.launch {
            delay(SUBMISSION_RECONCILE_DELAY_MILLIS)
            if (_state.value.selectedThread?.id != threadId) return@launch
            client.request(
                "thread.read",
                JSONObject()
                    .put("threadId", threadId)
                    .put("messageLimit", 1)
                    .put("clientMessageIds", JSONArray().put(clientMessageId)),
            ) { verification ->
                onMain {
                    if (_state.value.selectedThread?.id != threadId) return@onMain
                    val payload = verification.getOrNull()
                    val confirmed = payload
                        ?.optJSONArray("confirmedClientMessageIds")
                        .toStringList()
                        .contains(clientMessageId)
                    if (confirmed) {
                        _state.update { state ->
                            state.copy(
                                messages = state.messages.map { message ->
                                    if (message.id == clientMessageId) {
                                        message.copy(
                                            turnId = payload?.optString("activeTurnId")
                                                ?.takeIf(String::isNotBlank) ?: message.turnId,
                                            deliveryState = null,
                                        )
                                    } else {
                                        message
                                    }
                                },
                                isUploadingImages = false,
                                currentStatus = if (steering) "正在按新指令调整…" else "正在思考…",
                                statusDetail = "已从 Mac 确认消息送达",
                                error = null,
                            )
                        }
                        cacheCurrentMessages()
                    } else if (verification.isSuccess && attempt + 1 < SUBMISSION_RECONCILE_ATTEMPTS) {
                        reconcileSubmissionAfterUncertainFailure(
                            threadId,
                            clientMessageId,
                            steering,
                            failureMessage,
                            attempt + 1,
                        )
                    } else {
                        restoreFailedSubmission(threadId, clientMessageId, steering)
                        fail(failureMessage)
                    }
                }
            }
        }
    }

    private fun restoreFailedSubmission(
        threadId: String,
        clientMessageId: String,
        steering: Boolean,
    ) {
        if (_state.value.selectedThread?.id != threadId) return
        _state.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id == clientMessageId) message.copy(deliveryState = "failed") else message
                },
                isUploadingImages = false,
                isSending = if (steering) state.isSending else false,
            )
        }
        cacheCurrentMessages()
    }

    fun retryFailedMessage(messageId: String) {
        val current = _state.value
        val message = current.messages.firstOrNull {
            it.id == messageId && it.deliveryState == "failed"
        } ?: return
        val images = message.attachments.filter { it.kind == "image" }.map { attachment ->
            PendingImage(
                id = attachment.id,
                uri = attachment.source,
                name = attachment.name,
                mimeType = attachment.mimeType.ifBlank { "image/jpeg" },
                sizeBytes = null,
            )
        }
        rememberDiscardedLocalMessage(messageId)
        _state.update { state ->
            state.copy(messages = state.messages.filterNot { it.id == messageId }, error = null)
        }
        submitUserMessage(
            _state.value,
            message.text,
            images,
            steering = _state.value.isSending,
            clearComposer = false,
        )
    }

    fun discardFailedMessage(messageId: String) {
        val message = _state.value.messages.firstOrNull {
            it.id == messageId && it.deliveryState == "failed"
        } ?: return
        rememberDiscardedLocalMessage(message.id)
        _state.update { state ->
            state.copy(messages = state.messages.filterNot {
                it.id == messageId && it.deliveryState == "failed"
            })
        }
        cacheCurrentMessages()
    }

    private fun rememberDiscardedLocalMessage(messageId: String) {
        discardedLocalMessageIds += messageId
        while (discardedLocalMessageIds.size > MAX_DISCARDED_LOCAL_MESSAGES) {
            discardedLocalMessageIds.remove(discardedLocalMessageIds.first())
        }
        preferences.edit {
            putStringSet(
                DISCARDED_LOCAL_MESSAGES_PREFERENCE,
                discardedLocalMessageIds.toSet(),
            )
        }
    }

    fun loadAccountStatus() {
        _state.update { it.copy(isAccountLoading = true, error = null) }
        client.request("account.status") { result ->
            onMain {
                result.fold(
                    onSuccess = { payload ->
                        val account = payload.optJSONObject("account")
                        val credits = payload.optJSONObject("credits")
                        val usage = payload.optJSONObject("usage")
                        val limits = buildList {
                            val source = payload.optJSONArray("limits") ?: JSONArray()
                            for (index in 0 until source.length()) {
                                val item = source.optJSONObject(index) ?: continue
                                add(
                                    UsageLimit(
                                        name = item.optString("name", "Codex"),
                                        period = item.optString("period"),
                                        usedPercent = item.optDouble("usedPercent").coerceIn(0.0, 100.0),
                                        remainingPercent = item.optDouble("remainingPercent").coerceIn(0.0, 100.0),
                                        windowDurationMins = item.optNullableLong("windowDurationMins"),
                                        resetsAt = item.optNullableLong("resetsAt"),
                                        limit = item.optString("limit").ifBlank { null },
                                        used = item.optString("used").ifBlank { null },
                                    ),
                                )
                            }
                        }
                        _state.update {
                            it.copy(
                                isAccountLoading = false,
                                accountStatus = AccountStatus(
                                    email = account?.optString("email")?.ifBlank { null },
                                    planType = account?.optString("planType")?.ifBlank { null },
                                    limits = limits,
                                    hasCredits = credits?.optBoolean("hasCredits") == true,
                                    unlimitedCredits = credits?.optBoolean("unlimited") == true,
                                    creditBalance = credits?.optString("balance")?.ifBlank { null },
                                    resetCredits = payload.optNullableLong("resetCredits"),
                                    lifetimeTokens = usage?.optNullableLong("lifetimeTokens"),
                                    peakDailyTokens = usage?.optNullableLong("peakDailyTokens"),
                                    currentStreakDays = usage?.optNullableLong("currentStreakDays"),
                                    unavailable = payload.optJSONArray("unavailable").toStringList(),
                                ),
                            )
                        }
                    },
                    onFailure = {
                        _state.update { state -> state.copy(isAccountLoading = false) }
                        fail(it.message ?: "无法读取账户用量")
                    },
                )
            }
        }
    }

    fun loadAutomations() {
        _state.update { it.copy(isAutomationsLoading = true) }
        client.request("automations.list") { result ->
            onMain {
                result.fold(
                    onSuccess = { payload ->
                        val automations = buildList {
                            val source = payload.optJSONArray("automations") ?: JSONArray()
                            for (index in 0 until source.length()) {
                                parseAutomation(source.optJSONObject(index))?.let(::add)
                            }
                        }
                        _state.update { it.copy(automations = automations, isAutomationsLoading = false) }
                    },
                    onFailure = {
                        _state.update { state -> state.copy(isAutomationsLoading = false) }
                        fail(it.message ?: "无法读取自动化任务")
                    },
                )
            }
        }
    }

    fun setAutomationActive(id: String, active: Boolean) {
        _state.update { it.copy(updatingAutomationId = id, error = null) }
        client.request(
            "automation.status.set",
            JSONObject().put("id", id).put("active", active),
        ) { result ->
            onMain {
                result.fold(
                    onSuccess = { payload ->
                        val updated = parseAutomation(payload.optJSONObject("automation"))
                        _state.update { state ->
                            state.copy(
                                automations = if (updated == null) state.automations else {
                                    state.automations.map { if (it.id == updated.id) updated else it }
                                },
                                updatingAutomationId = null,
                            )
                        }
                    },
                    onFailure = {
                        _state.update { state -> state.copy(updatingAutomationId = null) }
                        fail(it.message ?: "无法更新自动化状态")
                    },
                )
            }
        }
    }

    fun archiveCurrentThread() {
        val thread = _state.value.selectedThread ?: return
        _state.update { it.copy(isArchivingThread = true, error = null) }
        client.request("thread.archive", JSONObject().put("threadId", thread.id)) { result ->
            onMain {
                result.fold(
                    onSuccess = {
                        removeCachedThread(thread.id)
                        _state.update {
                            it.copy(
                                isArchivingThread = false,
                                selectedThread = null,
                                messages = emptyList(),
                                isSyncing = false,
                                activeTurnId = null,
                                isSending = false,
                                currentStatus = "",
                                statusDetail = "",
                                activities = emptyList(),
                            )
                        }
                        refreshThreads()
                    },
                    onFailure = {
                        _state.update { state -> state.copy(isArchivingThread = false) }
                        fail(it.message ?: "无法归档任务")
                    },
                )
            }
        }
    }

    fun interrupt() {
        val current = _state.value
        val threadId = current.selectedThread?.id ?: return
        val turnId = current.activeTurnId ?: return
        client.request(
            "turn.interrupt",
            JSONObject().put("threadId", threadId).put("turnId", turnId),
        ) { result ->
            onMain {
                result.onFailure { fail(it.message ?: "无法停止任务") }
            }
        }
    }

    fun respondToApproval(allow: Boolean) {
        val approval = _state.value.pendingApproval ?: return
        if (!approval.canApprove) return
        val decision = if (allow) "accept" else "decline"
        val params = JSONObject()
            .put("requestId", approval.requestId)
            .put("result", JSONObject().put("decision", decision))
        _state.update {
            it.copy(
                pendingApproval = null,
                currentStatus = if (allow) "已允许，继续运行…" else "已拒绝，等待 Codex 处理…",
                statusDetail = approval.detail,
            )
        }
        client.request("codex.respond", params) { result ->
            onMain { result.onFailure { fail(it.message ?: "无法提交授权结果") } }
        }
    }

    fun clearMessageCache() {
        cacheWriteJobs.values.forEach { it.cancel() }
        cacheWriteJobs.clear()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { PocketMediaLoader.clear(getApplication()) }
            val stats = combinedCacheStats(
                runCatching { messageCache.clear() }.getOrElse { messageCache.stats() },
            )
            _state.update {
                it.copy(
                    messageCacheThreadCount = stats.threadCount,
                    messageCacheBytes = stats.bytes,
                )
            }
        }
    }

    fun refreshCacheStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = combinedCacheStats(messageCache.stats())
            _state.update {
                it.copy(
                    messageCacheThreadCount = stats.threadCount,
                    messageCacheBytes = stats.bytes,
                )
            }
        }
    }

    private fun cacheCurrentMessages(delayMillis: Long = CACHE_WRITE_DELAY_MILLIS) {
        val current = _state.value
        val threadId = current.selectedThread?.id ?: return
        if (current.messages.isEmpty()) return
        scheduleMessageCache(threadId, current.messages, delayMillis)
    }

    private fun scheduleMessageCache(
        threadId: String,
        messages: List<ChatMessage>,
        delayMillis: Long = CACHE_WRITE_DELAY_MILLIS,
    ) {
        val snapshot = messages.toList()
        val discardedSnapshot = discardedLocalMessageIds.toSet()
        cacheWriteJobs.remove(threadId)?.cancel()
        cacheWriteJobs[threadId] = viewModelScope.launch(Dispatchers.IO) {
            if (delayMillis > 0) delay(delayMillis)
            val stats = combinedCacheStats(
                runCatching {
                    messageCache.write(threadId, snapshot, discardedSnapshot)
                }
                    .getOrElse { messageCache.stats() },
            )
            _state.update {
                it.copy(
                    messageCacheThreadCount = stats.threadCount,
                    messageCacheBytes = stats.bytes,
                )
            }
        }
    }

    private fun removeCachedThread(threadId: String) {
        cacheWriteJobs.remove(threadId)?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            val stats = combinedCacheStats(
                runCatching { messageCache.remove(threadId) }
                    .getOrElse { messageCache.stats() },
            )
            _state.update {
                it.copy(
                    messageCacheThreadCount = stats.threadCount,
                    messageCacheBytes = stats.bytes,
                )
            }
        }
    }

    private fun combinedCacheStats(messageStats: MessageCacheStats): MessageCacheStats =
        messageStats.copy(bytes = messageStats.bytes + PocketMediaLoader.sizeBytes(getApplication()))

    private fun prefetchMessageImages(messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        val current = _state.value
        val loader = PocketMediaLoader.get(getApplication())
        messages.asSequence()
            .flatMap { it.attachments.asSequence() }
            .filter { it.kind == "image" && it.source.isNotBlank() }
            .distinctBy { it.source }
            .forEach { attachment ->
                val source = if (!attachment.isLocal) {
                    attachment.source
                } else {
                    val bridge = Uri.parse(current.endpoint)
                    Uri.Builder()
                        .scheme(if (bridge.scheme == "wss") "https" else "http")
                        .encodedAuthority(bridge.encodedAuthority)
                        .path("/media")
                        .appendQueryParameter("path", attachment.source)
                        .appendQueryParameter("token", current.token)
                        .build()
                        .toString()
                }
                loader.enqueue(
                    ImageRequest.Builder(getApplication<Application>())
                        .data(source)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build(),
                )
            }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    override fun onConnected() = onMain {
        val previous = _state.value
        val selected = previous.selectedThread
        _state.update {
            it.copy(connection = ConnectionState.Connected, isReconnecting = false, error = null)
        }
        if (selected == null) refreshThreads() else refreshOpenThread(selected)
        if (previous.models.isEmpty()) loadModels()
        if (previous.modes.isEmpty()) loadModes()
        if (previous.permissionProfiles.isEmpty()) loadPermissionProfiles()
    }

    override fun onDisconnected(reason: String?) = onMain {
        _state.update { state ->
            state.copy(
                // Keep the current task visible during transient Tailscale or
                // mobile-network handovers instead of jumping to login.
                connection = if (state.connection == ConnectionState.Connected) {
                    ConnectionState.Connected
                } else {
                    state.connection
                },
                isReconnecting = true,
                isLoading = false,
                isSyncing = false,
                error = null,
            )
        }
    }

    override fun onReconnecting(delaySeconds: Int) = onMain {
        _state.update { state ->
            state.copy(
                connection = if (state.connection == ConnectionState.Connected) {
                    ConnectionState.Connected
                } else {
                    ConnectionState.Connecting
                },
                isReconnecting = true,
                error = null,
                currentStatus = "网络波动，正在自动恢复…",
                statusDetail = "$delaySeconds 秒后重试",
            )
        }
    }

    override fun onEvent(event: String, data: JSONObject) = onMain {
        when (event) {
            "agent.delta" -> applyAgentDelta(data)
            "reasoning.delta" -> applyStreamingActivity(data, "正在思考", "reasoning")
            "plan.delta" -> applyStreamingActivity(data, "正在制定计划", "plan")
            "tool.progress" -> applyStreamingActivity(data, "工具正在运行", "tool")
            "tool.output" -> applyStreamingActivity(data, "正在执行命令", "tool")
            "item.started", "item.completed" -> {
                if (!belongsToSelectedThread(data)) return@onMain
                val item = data.optJSONObject("item")
                if (event == "item.completed") applyCompletedItem(item) else applyItem(item)
                applyActivity(data.optJSONObject("activity"))
            }
            "turn.started" -> {
                if (!belongsToSelectedThread(data)) {
                    refreshThreads()
                    return@onMain
                }
                _state.update {
                    it.copy(
                        activeTurnId = data.optJSONObject("turn")?.optString("id"),
                        isSending = true,
                        currentStatus = "正在思考…",
                        statusDetail = "Codex 正在分析你的请求",
                        activities = it.activities.filterNot { entry -> entry.id == "turn-pending" } +
                            ActivityEntry("turn-started", "Codex 已开始处理", phase = "completed"),
                    )
                }
                _state.value.selectedThread?.id?.let(::startActiveThreadSync)
            }
            "turn.completed" -> {
                if (belongsToSelectedThread(data)) {
                    val completedThread = _state.value.selectedThread
                    stopActiveThreadSync(data.optString("threadId"))
                    _state.update {
                        it.copy(
                            isSending = false,
                            activeTurnId = null,
                            currentStatus = "已完成",
                            statusDetail = "",
                            pendingApproval = null,
                        )
                    }
                    cacheCurrentMessages()
                    if (appInForeground && _state.value.completionNotificationsEnabled) {
                        TaskNotifications.showCompletion(
                            getApplication(),
                            data.optString("threadId"),
                            completedThread?.title.orEmpty(),
                        )
                    }
                    if (completedThread != null) {
                        viewModelScope.launch {
                            delay(COMPLETED_THREAD_SYNC_DELAY_MILLIS)
                            if (_state.value.selectedThread?.id == completedThread.id) {
                                refreshOpenThread(completedThread)
                            }
                        }
                    }
                }
                refreshThreads()
            }
            "thread.catalog" -> {
                val removed = data.optString("action") == "archived" ||
                    data.optString("action") == "deleted"
                if (removed) data.optString("threadId").takeIf(String::isNotBlank)?.let(::removeCachedThread)
                val removedCurrent = removed && belongsToSelectedThread(data)
                if (removedCurrent) closeThread(preserveCache = false) else refreshThreads()
            }
            "thread.settings" -> {
                if (belongsToSelectedThread(data)) {
                    _state.update { state ->
                        state.copy(
                            selectedModel = data.optString("model").ifBlank { state.selectedModel },
                            selectedEffort = data.optString("effort").ifBlank { state.selectedEffort },
                            selectedMode = data.optString("mode").ifBlank { state.selectedMode },
                            fastModeEnabled = data.optString("serviceTier") == "priority",
                            currentPermissionProfile = data.optString("permissionProfile")
                                .takeIf(String::isNotBlank) ?: state.currentPermissionProfile,
                        )
                    }
                }
            }
            "thread.goal" -> {
                if (belongsToSelectedThread(data)) {
                    _state.update { it.copy(goal = parseGoal(data.optJSONObject("goal"))) }
                }
                refreshThreads()
            }
            "thread.goal.cleared" -> {
                if (belongsToSelectedThread(data)) _state.update { it.copy(goal = null) }
                refreshThreads()
            }
            "automation.updated" -> {
                val updated = parseAutomation(data.optJSONObject("automation")) ?: return@onMain
                _state.update { state ->
                    val exists = state.automations.any { it.id == updated.id }
                    state.copy(
                        automations = if (exists) {
                            state.automations.map { if (it.id == updated.id) updated else it }
                        } else {
                            listOf(updated) + state.automations
                        },
                        updatingAutomationId = null,
                    )
                }
            }
            "thread.status" -> {
                val selected = belongsToSelectedThread(data)
                val type = data.optJSONObject("status")?.optString("type")
                if (selected) {
                    _state.update {
                        it.copy(
                            isSending = type == "active",
                            currentStatus = if (type == "active") "Codex 正在处理…" else it.currentStatus,
                        )
                    }
                    val selectedThreadId = _state.value.selectedThread?.id
                    if (type == "active" && selectedThreadId != null) {
                        startActiveThreadSync(selectedThreadId)
                    } else {
                        stopActiveThreadSync(selectedThreadId)
                    }
                } else {
                    refreshThreads()
                }
            }
            "codex.request" -> applyServerRequest(data)
            "codex.warning" -> {
                if (data.optString("threadId").isBlank() || belongsToSelectedThread(data)) {
                    _state.update {
                        it.copy(
                            currentStatus = "Codex 提示",
                            statusDetail = data.optString("message"),
                        )
                    }
                }
            }
            "model.rerouted" -> {
                if (belongsToSelectedThread(data)) {
                    _state.update {
                        it.copy(
                            currentStatus = "模型已自动切换",
                            statusDetail = data.optString("reason"),
                        )
                    }
                }
            }
            "codex.error", "bridge.error" -> {
                val error = data.optJSONObject("error")
                fail(data.optString("message").ifBlank { error?.optString("message") ?: "Codex 发生错误" })
                _state.update { it.copy(isSending = false, currentStatus = "运行出错") }
            }
        }
    }

    private fun startActiveThreadSync(threadId: String) {
        if (activeThreadSyncThreadId == threadId && activeThreadSyncJob?.isActive == true) return
        stopActiveThreadSync()
        activeThreadSyncThreadId = threadId
        activeThreadSyncJob = viewModelScope.launch {
            while (
                _state.value.selectedThread?.id == threadId &&
                _state.value.isSending
            ) {
                delay(ACTIVE_THREAD_SYNC_INTERVAL_MILLIS)
                val current = _state.value
                if (
                    current.selectedThread?.id != threadId ||
                    !current.isSending ||
                    current.connection != ConnectionState.Connected ||
                    activeThreadSyncInFlight
                ) continue
                activeThreadSyncInFlight = true
                _state.update { state ->
                    if (state.selectedThread?.id == threadId) state.copy(isSyncing = true) else state
                }
                client.request(
                    "thread.read",
                    JSONObject()
                        .put("threadId", threadId)
                        .put("messageLimit", MessageCacheStore.LATEST_SYNC_MESSAGE_COUNT),
                ) { result ->
                    onMain {
                        activeThreadSyncInFlight = false
                        if (_state.value.selectedThread?.id != threadId) return@onMain
                        _state.update { it.copy(isSyncing = false) }
                        result.onSuccess { payload -> applyActiveThreadSnapshot(threadId, payload) }
                    }
                }
            }
            if (activeThreadSyncThreadId == threadId) activeThreadSyncThreadId = null
        }
    }

    private fun stopActiveThreadSync(threadId: String? = null) {
        if (threadId != null && activeThreadSyncThreadId != threadId) return
        activeThreadSyncJob?.cancel()
        activeThreadSyncJob = null
        activeThreadSyncThreadId = null
        activeThreadSyncInFlight = false
    }

    private fun applyActiveThreadSnapshot(threadId: String, payload: JSONObject) {
        val fresh = parseChatMessages(payload.optJSONArray("messages") ?: JSONArray())
        val threadIsActive = payload.optJSONObject("thread")?.optString("status") == "active"
        val activeTurnId = payload.optString("activeTurnId").takeIf(String::isNotBlank)
        val previousMessages = _state.value.messages
        val merged = preserveSmoothedAgentText(
            previousMessages,
            mergeMessageWindows(
                MessageCacheStore.MAX_MESSAGES_PER_THREAD,
                previousMessages,
                fresh,
            ),
        )
        _state.update { state ->
            if (state.selectedThread?.id != threadId) state else state.copy(
                messages = merged,
                isSending = threadIsActive,
                activeTurnId = activeTurnId,
                currentStatus = if (threadIsActive) {
                    state.currentStatus.ifBlank { "Codex 正在处理…" }
                } else {
                    state.currentStatus
                },
            )
        }
        if (merged != previousMessages) {
            scheduleMessageCache(threadId, merged)
            prefetchMessageImages(fresh)
        }
        if (!threadIsActive) stopActiveThreadSync(threadId)
    }

    override fun onError(message: String) = onMain { fail(message) }

    private fun applyAgentDelta(data: JSONObject) {
        val selectedId = _state.value.selectedThread?.id ?: return
        if (data.optString("threadId") != selectedId) return
        val itemId = data.optString("itemId")
        val delta = data.optString("delta")
        if (itemId.isBlank() || delta.isEmpty()) return
        enqueueAgentText(
            threadId = data.optString("threadId"),
            turnId = data.optString("turnId"),
            itemId = itemId,
            text = delta,
        )
    }

    private fun enqueueAgentText(threadId: String, turnId: String, itemId: String, text: String) {
        if (text.isEmpty()) return
        agentStreamBuffers.getOrPut(itemId, ::StringBuilder).append(text)
        if (agentStreamJobs[itemId]?.isActive == true) return
        agentStreamJobs[itemId] = viewModelScope.launch {
            var idleChecks = 0
            while (true) {
                val pending = agentStreamBuffers[itemId] ?: break
                if (pending.isEmpty()) {
                    if (pendingAgentCompletions.containsKey(itemId)) break
                    if (idleChecks >= SMOOTH_STREAM_MAX_IDLE_CHECKS) break
                    idleChecks += 1
                    delay(SMOOTH_STREAM_IDLE_TICK_MILLIS)
                    continue
                }
                idleChecks = 0
                val pendingText = pending.toString()
                val codePoints = pendingText.codePointCount(0, pendingText.length)
                val (chunk, remainder) = takeCodePointPrefix(
                    pendingText,
                    smoothStreamChunkCodePoints(codePoints),
                )
                pending.clear()
                pending.append(remainder)
                appendAgentChunk(threadId, turnId, itemId, chunk)
                delay(SMOOTH_STREAM_TICK_MILLIS)
            }
            agentStreamBuffers.remove(itemId)
            agentStreamJobs.remove(itemId)
            pendingAgentCompletions.remove(itemId)?.let {
                applyItem(it)
                _state.update { state ->
                    if (state.activeTurnId == null) {
                        state.copy(isSending = false, currentStatus = "已完成", statusDetail = "")
                    } else {
                        state
                    }
                }
            }
            cacheCurrentMessages()
        }
    }

    /**
     * A periodic thread snapshot may already contain the complete answer while
     * the phone is still revealing queued deltas. Keep the visible prefix and
     * add only a genuinely missing suffix to the local animation queue.
     */
    private fun preserveSmoothedAgentText(
        previous: List<ChatMessage>,
        merged: List<ChatMessage>,
    ): List<ChatMessage> {
        if (agentStreamJobs.isEmpty() && agentStreamBuffers.isEmpty()) return merged
        val previousById = previous.associateBy { it.id }
        return merged.map { fresh ->
            val existing = previousById[fresh.id] ?: return@map fresh
            val buffer = agentStreamBuffers[fresh.id]
            val isSmoothing = agentStreamJobs[fresh.id]?.isActive == true ||
                buffer?.isNotEmpty() == true
            if (!isSmoothing || fresh.kind != "agentMessage" || !fresh.text.startsWith(existing.text)) {
                return@map fresh
            }

            val projectedText = existing.text + buffer?.toString().orEmpty()
            if (fresh.text.startsWith(projectedText) && fresh.text.length > projectedText.length) {
                buffer?.append(fresh.text.substring(projectedText.length))
            }
            fresh.copy(text = existing.text, isStreaming = true)
        }
    }

    private fun clearAgentStreams() {
        agentStreamJobs.values.forEach { it.cancel() }
        agentStreamJobs.clear()
        agentStreamBuffers.clear()
        pendingAgentCompletions.clear()
    }

    private fun appendAgentChunk(threadId: String, turnId: String, itemId: String, chunk: String) {
        if (_state.value.selectedThread?.id != threadId || chunk.isEmpty()) return
        _state.update { state ->
            val index = state.messages.indexOfFirst { it.id == itemId }
            if (index >= 0) {
                val messages = state.messages.toMutableList()
                messages[index] = messages[index].copy(
                    text = messages[index].text + chunk,
                    isStreaming = true,
                )
                state.copy(
                    messages = messages,
                    isSending = true,
                    currentStatus = "正在生成回复…",
                    statusDetail = "回复内容正在实时传输",
                )
            } else {
                state.copy(
                    messages = state.messages + ChatMessage(
                        id = itemId,
                        turnId = turnId,
                        role = "assistant",
                        text = chunk,
                        kind = "agentMessage",
                        isStreaming = true,
                    ),
                    isSending = true,
                    currentStatus = "正在生成回复…",
                    statusDetail = "回复内容正在实时传输",
                )
            }
        }
    }

    private fun applyCompletedItem(item: JSONObject?) {
        if (item == null || item == JSONObject.NULL) return
        val parsed = parseChatMessage(item)
        val itemId = parsed?.id.orEmpty()
        if (parsed?.kind == "agentMessage" && itemId.isNotBlank()) {
            val visibleText = _state.value.messages.firstOrNull { it.id == itemId }?.text.orEmpty()
            val projectedText = visibleText + agentStreamBuffers[itemId]?.toString().orEmpty()
            if (parsed.text.startsWith(projectedText) && parsed.text.length > projectedText.length) {
                enqueueAgentText(
                    threadId = _state.value.selectedThread?.id.orEmpty(),
                    turnId = parsed.turnId,
                    itemId = itemId,
                    text = parsed.text.substring(projectedText.length),
                )
            }
        }
        val smoothing = parsed?.kind == "agentMessage" &&
            (agentStreamJobs[itemId]?.isActive == true || agentStreamBuffers[itemId]?.isNotEmpty() == true)
        if (smoothing) {
            pendingAgentCompletions[itemId] = JSONObject(item.toString())
        } else {
            applyItem(item)
        }
    }

    private fun applyStreamingActivity(data: JSONObject, title: String, kind: String) {
        if (!belongsToSelectedThread(data)) return
        val id = data.optString("itemId").ifBlank { "$kind-${data.optString("turnId")}" }
        val delta = data.optString("delta").ifBlank { data.optString("message") }
        _state.update { state ->
            val index = state.activities.indexOfFirst { it.id == id }
            val activities = state.activities.toMutableList()
            val oldDetail = activities.getOrNull(index)?.detail.orEmpty()
            val detail = (oldDetail + delta).takeLast(1600)
            val entry = ActivityEntry(id = id, title = title, detail = detail, phase = "started")
            if (index >= 0) activities[index] = entry else activities.add(entry)
            state.copy(
                activities = activities.takeLast(24),
                isSending = true,
                currentStatus = title,
                statusDetail = detail.takeLast(320),
            )
        }
    }

    private fun applyActivity(activity: JSONObject?) {
        activity ?: return
        val entry = ActivityEntry(
            id = activity.optString("id"),
            title = activity.optString("title", "正在处理"),
            detail = activity.optString("detail"),
            phase = activity.optString("phase", "started"),
        )
        _state.update { state ->
            val index = state.activities.indexOfFirst { it.id == entry.id }
            val activities = state.activities.toMutableList()
            val existing = activities.getOrNull(index)
            val merged = entry.copy(detail = entry.detail.ifBlank { existing?.detail.orEmpty() })
            if (index >= 0) activities[index] = merged else activities.add(merged)
            state.copy(
                activities = activities.takeLast(24),
                currentStatus = if (entry.phase == "started") entry.title else state.currentStatus,
                statusDetail = if (entry.phase == "started") merged.detail else state.statusDetail,
            )
        }
    }

    private fun applyServerRequest(data: JSONObject) {
        val params = data.optJSONObject("params") ?: JSONObject()
        if (params.optString("threadId") != _state.value.selectedThread?.id) return
        val method = data.optString("method")
        val command = params.optString("command")
        val reason = params.optString("reason")
        val question = params.optJSONArray("questions")?.optJSONObject(0)?.optString("question").orEmpty()
        val detail = command.ifBlank { reason.ifBlank { question.ifBlank { "Codex 正在等待你的操作" } } }
        val canApprove = method == "item/commandExecution/requestApproval" ||
            method == "item/fileChange/requestApproval"
        val title = when (method) {
            "item/commandExecution/requestApproval" -> "需要允许执行命令"
            "item/fileChange/requestApproval" -> "需要允许修改文件"
            "item/tool/requestUserInput" -> "Codex 正在等待你的回答"
            "item/permissions/requestApproval" -> "Codex 正在请求额外权限"
            else -> "Codex 正在等待确认"
        }
        _state.update {
            it.copy(
                currentStatus = title,
                statusDetail = detail,
                pendingApproval = PendingApproval(
                    requestId = data.optString("requestId"),
                    method = method,
                    title = title,
                    detail = detail,
                    canApprove = canApprove,
                ),
            )
        }
    }

    private fun belongsToSelectedThread(data: JSONObject): Boolean =
        data.optString("threadId") == _state.value.selectedThread?.id

    private fun applyItem(item: JSONObject?) {
        if (item == null || item == JSONObject.NULL) return
        val parsed = parseChatMessage(item) ?: return
        _state.update { state ->
            val index = state.messages.indexOfFirst { it.id == parsed.id }
            if (index < 0) state.copy(messages = state.messages + parsed)
            else {
                val messages = state.messages.toMutableList()
                val existing = messages[index]
                messages[index] = mergeChatMessages(existing, parsed).copy(isStreaming = false)
                state.copy(messages = messages)
            }
        }
        prefetchMessageImages(listOf(parsed))
        cacheCurrentMessages()
    }

    private fun parseThreads(array: JSONArray): List<ThreadSummary> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            parseThread(item)?.let(::add)
        }
    }

    private fun parseThread(item: JSONObject?): ThreadSummary? {
        item ?: return null
        val id = item.optString("id")
        if (id.isBlank()) return null
        return ThreadSummary(
            id = id,
            title = item.optString("title", "未命名任务"),
            preview = item.optString("preview"),
            cwd = item.optString("cwd"),
            status = item.optString("status", "notLoaded"),
            activeFlags = item.optJSONArray("activeFlags").toStringList(),
            createdAt = item.optLong("createdAt"),
            updatedAt = item.optLong("updatedAt"),
            isPinned = item.optBoolean("isPinned"),
            goalStatus = item.optJSONObject("goal")?.optString("status")?.ifBlank { null },
            goalObjective = item.optJSONObject("goal")?.optString("objective")?.ifBlank { null },
        )
    }

    private fun parseModels(array: JSONArray): List<ModelOption> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val efforts = buildList {
                val source = item.optJSONArray("efforts") ?: JSONArray()
                for (effortIndex in 0 until source.length()) {
                    val effort = source.optJSONObject(effortIndex) ?: continue
                    add(
                        ReasoningEffortOption(
                            id = effort.optString("id"),
                            description = effort.optString("description"),
                        ),
                    )
                }
            }
            val serviceTiers = buildList {
                val source = item.optJSONArray("serviceTiers") ?: JSONArray()
                for (tierIndex in 0 until source.length()) {
                    val tier = source.optJSONObject(tierIndex) ?: continue
                    add(
                        ServiceTierOption(
                            id = tier.optString("id"),
                            name = tier.optString("name"),
                            description = tier.optString("description"),
                        ),
                    )
                }
            }
            add(
                ModelOption(
                    id = item.optString("id"),
                    displayName = item.optString("displayName", item.optString("id")),
                    description = item.optString("description"),
                    efforts = efforts,
                    defaultEffort = item.optString("defaultEffort", efforts.firstOrNull()?.id.orEmpty()),
                    isDefault = item.optBoolean("isDefault"),
                    serviceTiers = serviceTiers,
                ),
            )
        }
    }

    private fun parseGoal(item: JSONObject?): ThreadGoal? {
        if (item == null) return null
        val objective = item.optString("objective")
        if (objective.isBlank()) return null
        return ThreadGoal(
            objective = objective,
            status = item.optString("status", "active"),
            tokenBudget = item.optNullableLong("tokenBudget"),
            tokensUsed = item.optLong("tokensUsed"),
            timeUsedSeconds = item.optLong("timeUsedSeconds"),
        )
    }

    private fun parseAutomation(item: JSONObject?): AutomationSummary? {
        if (item == null) return null
        val id = item.optString("id")
        if (id.isBlank()) return null
        return AutomationSummary(
            id = id,
            name = item.optString("name", id),
            status = item.optString("status", "PAUSED"),
            rrule = item.optString("rrule"),
            targetThreadId = item.optString("targetThreadId").ifBlank { null },
            promptPreview = item.optString("promptPreview"),
            updatedAt = item.optLong("updatedAt"),
        )
    }

    private fun JSONArray?.toStringList(): List<String> = buildList {
        val array = this@toStringList ?: return@buildList
        for (index in 0 until array.length()) add(array.optString(index))
    }

    private fun ChatMessage.isLocalSubmission(): Boolean =
        role == "user" && (deliveryState != null || turnId == "pending")

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (has(name) && !isNull(name)) optLong(name) else null

    private fun fail(message: String) {
        _state.update { it.copy(error = message, isLoading = false) }
    }

    private fun onMain(block: () -> Unit) {
        viewModelScope.launch { block() }
    }

    override fun onCleared() {
        stopActiveThreadSync()
        clearAgentStreams()
        cacheWriteJobs.values.forEach { it.cancel() }
        val current = _state.value
        current.selectedThread?.id?.let { threadId ->
            if (current.messages.isNotEmpty()) {
                runCatching {
                    messageCache.write(threadId, current.messages, discardedLocalMessageIds)
                }
            }
        }
        client.close()
        super.onCleared()
    }

    companion object {
        private const val CACHE_WRITE_DELAY_MILLIS = 650L
        private const val ACTIVE_THREAD_SYNC_INTERVAL_MILLIS = 8_000L
        private const val SMOOTH_STREAM_TICK_MILLIS = 28L
        private const val SMOOTH_STREAM_IDLE_TICK_MILLIS = 80L
        private const val SMOOTH_STREAM_MAX_IDLE_CHECKS = 25
        private const val COMPLETED_THREAD_SYNC_DELAY_MILLIS = 350L
        private const val SUBMISSION_RECONCILE_DELAY_MILLIS = 1_000L
        private const val SUBMISSION_RECONCILE_ATTEMPTS = 2
        private const val DISCARDED_LOCAL_MESSAGES_PREFERENCE = "discarded-local-message-ids"
        private const val MAX_DISCARDED_LOCAL_MESSAGES = 500
        private const val EXPANDED_PROCESS_GROUPS_PREFERENCE = "expanded-process-groups"
        private const val EXPANDED_PROCESS_ITEMS_PREFERENCE = "expanded-process-items"
        private const val MAX_REMEMBERED_DISCLOSURES = 500
        private const val PROCESS_ITEM_SCROLL_PREFIX = "process-item-scroll-"
        private const val CHAT_SCROLL_ITEM_PREFIX = "chat-scroll-item-"
        private const val CHAT_SCROLL_OFFSET_PREFIX = "chat-scroll-offset-"
        private const val MAX_IMAGES_PER_MESSAGE = 4
        private const val MAX_IMAGE_BYTES = 15L * 1024L * 1024L
    }
}
