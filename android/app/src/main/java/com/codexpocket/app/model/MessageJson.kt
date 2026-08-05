package com.codexpocket.app.model

import org.json.JSONArray
import org.json.JSONObject

/** Shared decoder used by both the visible chat and the background sync service. */
fun parseChatMessages(array: JSONArray): List<ChatMessage> = buildList {
    for (index in 0 until array.length()) {
        parseChatMessage(array.optJSONObject(index))?.let(::add)
    }
}

fun parseChatMessage(item: JSONObject?): ChatMessage? {
    item ?: return null
    val attachments = buildList {
        val source = item.optJSONArray("attachments") ?: JSONArray()
        for (index in 0 until source.length()) {
            val attachment = source.optJSONObject(index) ?: continue
            val mediaSource = attachment.optString("source")
            if (mediaSource.isBlank()) continue
            add(
                MediaAttachment(
                    id = attachment.optString("id", "media-$index"),
                    kind = attachment.optString("kind", "file"),
                    source = mediaSource,
                    name = attachment.optString("name", "媒体文件"),
                    mimeType = attachment.optString("mimeType"),
                    isLocal = attachment.optBoolean("isLocal"),
                ),
            )
        }
    }
    return ChatMessage(
        id = item.optString("id"),
        turnId = item.optString("turnId"),
        role = item.optString("role"),
        text = item.optString("text"),
        kind = item.optString("kind"),
        phase = item.optString("phase").ifBlank { null },
        command = item.optString("command").ifBlank { null },
        status = item.optString("status").ifBlank { null },
        attachments = attachments,
        deliveryState = item.optString("deliveryState").ifBlank { null },
    )
}
