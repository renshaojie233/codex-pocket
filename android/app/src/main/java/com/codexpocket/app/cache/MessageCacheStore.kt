package com.codexpocket.app.cache

import com.codexpocket.app.model.ChatMessage
import com.codexpocket.app.model.MediaAttachment
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

data class MessageCacheStats(
    val threadCount: Int = 0,
    val bytes: Long = 0,
)

data class MessageCachePage(
    val messages: List<ChatMessage> = emptyList(),
    val hasOlder: Boolean = false,
)

/**
 * A bounded, app-private cache. It is deliberately stored under cacheDir so
 * Android may reclaim it and it is never treated as the source of truth.
 */
class MessageCacheStore(cacheRoot: File) {
    private val directory = File(cacheRoot, "message-history-v1")

    @Synchronized
    fun read(threadId: String): List<ChatMessage> =
        readMessages(threadId).takeLast(INITIAL_CACHED_MESSAGES)

    @Synchronized
    fun readBefore(threadId: String, beforeMessageId: String, limit: Int): MessageCachePage {
        if (beforeMessageId.isBlank() || limit <= 0) return MessageCachePage()
        val messages = readMessages(threadId)
        val end = messages.indexOfFirst { it.id == beforeMessageId }
        if (end <= 0) return MessageCachePage()
        val start = (end - limit).coerceAtLeast(0)
        return MessageCachePage(
            messages = messages.subList(start, end),
            hasOlder = start > 0,
        )
    }

    private fun readMessages(threadId: String): List<ChatMessage> {
        val file = fileFor(threadId)
        if (!file.isFile) return emptyList()
        return runCatching {
            val root = JSONObject(file.readText())
            if (root.optString("threadId") != threadId) return@runCatching emptyList()
            val array = root.optJSONArray("messages") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    parseMessage(array.optJSONObject(index))?.let(::add)
                }
            }.takeLast(MAX_MESSAGES_PER_THREAD).also {
                file.setLastModified(System.currentTimeMillis())
            }
        }.getOrElse {
            file.delete()
            emptyList()
        }
    }

    @Synchronized
    fun write(threadId: String, messages: List<ChatMessage>): MessageCacheStats {
        if (threadId.isBlank()) return stats()
        if (messages.isEmpty()) {
            remove(threadId)
            return stats()
        }
        directory.mkdirs()
        val retainedMessages = mergeMessageWindows(
            MAX_MESSAGES_PER_THREAD,
            readMessages(threadId),
            messages,
        )
        val encoded = boundedMessages(retainedMessages)
        if (encoded.length() == 0) {
            remove(threadId)
            return stats()
        }
        val root = JSONObject()
            .put("version", CACHE_VERSION)
            .put("threadId", threadId)
            .put("updatedAt", System.currentTimeMillis())
            .put("messages", encoded)
        val destination = fileFor(threadId)
        val temporary = File(directory, "${destination.name}.tmp")
        temporary.writeText(root.toString())
        if (!temporary.renameTo(destination)) {
            destination.delete()
            check(temporary.renameTo(destination)) { "Unable to replace message cache" }
        }
        destination.setLastModified(System.currentTimeMillis())
        trimGlobalBudget()
        return stats()
    }

    @Synchronized
    fun remove(threadId: String): MessageCacheStats {
        fileFor(threadId).delete()
        return stats()
    }

    @Synchronized
    fun clear(): MessageCacheStats {
        directory.listFiles()?.forEach(File::delete)
        directory.delete()
        return MessageCacheStats()
    }

    @Synchronized
    fun stats(): MessageCacheStats {
        val files = cacheFiles()
        return MessageCacheStats(
            threadCount = files.size,
            bytes = files.sumOf(File::length),
        )
    }

    private fun boundedMessages(messages: List<ChatMessage>): JSONArray {
        val selected = ArrayDeque<JSONObject>()
        var bytes = EMPTY_DOCUMENT_BYTES
        for (message in messages.takeLast(MAX_MESSAGES_PER_THREAD).asReversed()) {
            val encoded = encodeMessage(message)
            val itemBytes = encoded.toString().toByteArray(StandardCharsets.UTF_8).size + 1
            if (bytes + itemBytes > MAX_BYTES_PER_THREAD) continue
            selected.addFirst(encoded)
            bytes += itemBytes
        }
        return JSONArray().also { array -> selected.forEach(array::put) }
    }

    private fun encodeMessage(message: ChatMessage): JSONObject {
        val attachments = JSONArray()
        message.attachments
            .filterNot { it.source.startsWith("data:") || it.source.startsWith("blob:") }
            .forEach { attachment ->
                attachments.put(
                    JSONObject()
                        .put("id", attachment.id)
                        .put("kind", attachment.kind)
                        .put("source", attachment.source)
                        .put("name", attachment.name)
                        .put("mimeType", attachment.mimeType)
                        .put("isLocal", attachment.isLocal),
                )
            }
        return JSONObject()
            .put("id", message.id)
            .put("turnId", message.turnId)
            .put("role", message.role)
            .put("text", boundedText(message.text))
            .put("kind", message.kind)
            .put("phase", message.phase)
            .put("command", message.command)
            .put("status", message.status)
            .put("deliveryState", message.deliveryState)
            .put("attachments", attachments)
    }

    private fun boundedText(text: String): String {
        if (text.length <= MAX_CACHED_TEXT_CHARS) return text
        val half = MAX_CACHED_TEXT_CHARS / 2
        return text.take(half) + CACHE_TRUNCATION_MARKER + text.takeLast(half)
    }

    private fun parseMessage(item: JSONObject?): ChatMessage? {
        item ?: return null
        val attachments = buildList {
            val source = item.optJSONArray("attachments") ?: JSONArray()
            for (index in 0 until source.length()) {
                val attachment = source.optJSONObject(index) ?: continue
                val mediaSource = attachment.optString("source")
                if (mediaSource.isBlank()) continue
                add(
                    MediaAttachment(
                        id = attachment.optString("id", "cached-media-$index"),
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
            isStreaming = false,
            deliveryState = item.optString("deliveryState").ifBlank { null },
        )
    }

    private fun trimGlobalBudget() {
        val newestFirst = cacheFiles().sortedByDescending(File::lastModified)
        var retainedBytes = 0L
        newestFirst.forEachIndexed { index, file ->
            retainedBytes += file.length()
            if (index >= MAX_CACHED_THREADS || retainedBytes > MAX_TOTAL_BYTES) file.delete()
        }
    }

    private fun cacheFiles(): List<File> =
        directory.listFiles { file -> file.isFile && file.extension == "json" }?.toList().orEmpty()

    private fun fileFor(threadId: String): File = File(directory, "${threadId.sha256()}.json")

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        const val LATEST_SYNC_MESSAGE_COUNT = 120
        const val INITIAL_CACHED_MESSAGES = 360
        const val MAX_MESSAGES_PER_THREAD = 10_000
        const val MAX_CACHED_THREADS = 200
        const val MAX_TOTAL_BYTES = 128L * 1024L * 1024L
        private const val MAX_BYTES_PER_THREAD = 32 * 1024 * 1024
        private const val MAX_CACHED_TEXT_CHARS = 500_000
        private const val CACHE_VERSION = 1
        private const val EMPTY_DOCUMENT_BYTES = 64
        private const val CACHE_TRUNCATION_MARKER = "\n\n…（手机缓存已截断，联网同步后显示完整内容）…\n\n"
    }
}
