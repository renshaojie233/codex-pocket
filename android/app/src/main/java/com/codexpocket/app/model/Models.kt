package com.codexpocket.app.model

enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Error,
}

data class ThreadSummary(
    val id: String,
    val title: String,
    val preview: String,
    val cwd: String,
    val status: String,
    val activeFlags: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean,
    val goalStatus: String? = null,
    val goalObjective: String? = null,
)

data class DirectoryEntry(
    val name: String,
    val path: String,
)

data class UsageLimit(
    val name: String,
    val period: String,
    val usedPercent: Double,
    val remainingPercent: Double,
    val windowDurationMins: Long? = null,
    val resetsAt: Long? = null,
    val limit: String? = null,
    val used: String? = null,
)

data class AccountStatus(
    val email: String? = null,
    val planType: String? = null,
    val limits: List<UsageLimit> = emptyList(),
    val hasCredits: Boolean = false,
    val unlimitedCredits: Boolean = false,
    val creditBalance: String? = null,
    val resetCredits: Long? = null,
    val lifetimeTokens: Long? = null,
    val peakDailyTokens: Long? = null,
    val currentStreakDays: Long? = null,
    val unavailable: List<String> = emptyList(),
)

data class ChatMessage(
    val id: String,
    val turnId: String,
    val role: String,
    val text: String,
    val kind: String,
    val phase: String? = null,
    val command: String? = null,
    val status: String? = null,
    val attachments: List<MediaAttachment> = emptyList(),
    val isStreaming: Boolean = false,
    val deliveryState: String? = null,
)

data class MediaAttachment(
    val id: String,
    val kind: String,
    val source: String,
    val name: String,
    val mimeType: String = "",
    val isLocal: Boolean = false,
)

data class PendingImage(
    val id: String,
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long? = null,
)

data class ReasoningEffortOption(
    val id: String,
    val description: String,
)

data class ModelOption(
    val id: String,
    val displayName: String,
    val description: String,
    val efforts: List<ReasoningEffortOption>,
    val defaultEffort: String,
    val isDefault: Boolean,
    val serviceTiers: List<ServiceTierOption> = emptyList(),
)

data class ServiceTierOption(
    val id: String,
    val name: String,
    val description: String,
)

data class CodexModeOption(
    val id: String,
    val name: String,
    val effort: String? = null,
)

data class PermissionProfileOption(
    val id: String,
    val description: String? = null,
    val allowed: Boolean = true,
)

data class ThreadGoal(
    val objective: String,
    val status: String,
    val tokenBudget: Long? = null,
    val tokensUsed: Long = 0,
    val timeUsedSeconds: Long = 0,
)

data class AutomationSummary(
    val id: String,
    val name: String,
    val status: String,
    val rrule: String,
    val targetThreadId: String? = null,
    val promptPreview: String = "",
    val updatedAt: Long = 0,
)

data class ActivityEntry(
    val id: String,
    val title: String,
    val detail: String = "",
    val phase: String = "started",
)

data class PendingApproval(
    val requestId: String,
    val method: String,
    val title: String,
    val detail: String,
    val canApprove: Boolean,
)

data class UiState(
    val endpoint: String = "ws://127.0.0.1:8787/ws",
    val token: String = "",
    val connection: ConnectionState = ConnectionState.Disconnected,
    val isReconnecting: Boolean = false,
    val error: String? = null,
    val threads: List<ThreadSummary> = emptyList(),
    val selectedThread: ThreadSummary? = null,
    val messages: List<ChatMessage> = emptyList(),
    val models: List<ModelOption> = emptyList(),
    val selectedModel: String = "",
    val selectedEffort: String = "",
    val modes: List<CodexModeOption> = emptyList(),
    val selectedMode: String = "default",
    val fastModeEnabled: Boolean = false,
    val permissionProfiles: List<PermissionProfileOption> = emptyList(),
    val defaultPermissionProfile: String = ":danger-full-access",
    val currentPermissionProfile: String? = null,
    val isPermissionsLoading: Boolean = false,
    val goal: ThreadGoal? = null,
    val isModeUpdating: Boolean = false,
    val automations: List<AutomationSummary> = emptyList(),
    val isAutomationsLoading: Boolean = false,
    val updatingAutomationId: String? = null,
    val input: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val activeTurnId: String? = null,
    val currentStatus: String = "",
    val statusDetail: String = "",
    val activities: List<ActivityEntry> = emptyList(),
    val pendingApproval: PendingApproval? = null,
    val isCreatingThread: Boolean = false,
    val isDirectoryBrowserOpen: Boolean = false,
    val directoryBrowserPath: String = "",
    val directoryEntries: List<DirectoryEntry> = emptyList(),
    val isDirectoryLoading: Boolean = false,
    val accountStatus: AccountStatus? = null,
    val isAccountLoading: Boolean = false,
    val isArchivingThread: Boolean = false,
    val completionNotificationsEnabled: Boolean = false,
    val messageFontSizeSp: Float = 15f,
    val compactChatEnabled: Boolean = false,
    val messageCacheThreadCount: Int = 0,
    val messageCacheBytes: Long = 0,
    val pendingImages: List<PendingImage> = emptyList(),
    val isUploadingImages: Boolean = false,
    val hasOlderMessages: Boolean = false,
    val isLoadingOlderMessages: Boolean = false,
)
