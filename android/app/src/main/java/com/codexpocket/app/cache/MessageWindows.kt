package com.codexpocket.app.cache

import com.codexpocket.app.model.ChatMessage

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
            merged.remove(key)
            merged[key] = message
        }
    }
    return merged.values.toList().takeLast(limit)
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
