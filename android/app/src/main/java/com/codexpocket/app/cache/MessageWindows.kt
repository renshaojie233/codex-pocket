package com.codexpocket.app.cache

import com.codexpocket.app.model.ChatMessage
import com.codexpocket.app.model.MediaAttachment

/**
 * Combines oldest-to-newest message windows. Later windows are authoritative
 * and move matching messages to their newest known position.
 */
internal fun mergeMessageWindows(
    limit: Int,
    vararg windows: List<ChatMessage>,
): List<ChatMessage> {
    if (limit <= 0) return emptyList()
    val merged = LinkedHashMap<String, ChatMessage>()
    windows.forEach { window ->
        window.forEach { message ->
            val key = message.cacheIdentity()
            val existing = merged.remove(key)
            merged[key] = if (existing == null) message else mergeChatMessages(existing, message)
        }
    }
    return merged.values.toList().takeLast(limit)
}

/**
 * Live item/completed events and thread snapshots are sometimes intentionally
 * sparse. Never let a later sparse copy erase media, commands, phases, or text
 * that the phone already received for the same Codex item.
 */
internal fun mergeChatMessages(older: ChatMessage, newer: ChatMessage): ChatMessage = newer.copy(
    turnId = newer.turnId.ifBlank { older.turnId },
    role = newer.role.ifBlank { older.role },
    text = newer.text.ifBlank { older.text },
    kind = newer.kind.ifBlank { older.kind },
    phase = newer.phase ?: older.phase,
    command = newer.command ?: older.command,
    status = newer.status ?: older.status,
    attachments = mergeAttachments(older.attachments, newer.attachments),
    isStreaming = newer.isStreaming,
    // A server-backed user item has a real turn id and is authoritative proof
    // that an optimistic mobile submission arrived. Never keep a stale local
    // "sending" or "failed" badge after that item has synced from the Mac.
    deliveryState = if (
        newer.role == "user" && newer.turnId.isNotBlank() && newer.turnId != "pending"
    ) {
        newer.deliveryState
    } else {
        newer.deliveryState ?: older.deliveryState
    },
)

private fun mergeAttachments(
    older: List<MediaAttachment>,
    newer: List<MediaAttachment>,
): List<MediaAttachment> {
    val merged = LinkedHashMap<String, MediaAttachment>()
    (older + newer).forEach { attachment ->
        val key = attachment.source.ifBlank { attachment.id }
        merged.remove(key)
        merged[key] = attachment
    }
    return merged.values.toList()
}

internal fun excludeDiscardedLocalMessages(
    messages: List<ChatMessage>,
    discardedMessageIds: Set<String>,
): List<ChatMessage> = messages.filterNot { message ->
    message.id in discardedMessageIds &&
        message.role == "user" &&
        (message.deliveryState != null || message.turnId == "pending")
}

private fun ChatMessage.cacheIdentity(): String = id.ifBlank {
    "$turnId\u0000$role\u0000$kind\u0000${text.hashCode()}"
}
