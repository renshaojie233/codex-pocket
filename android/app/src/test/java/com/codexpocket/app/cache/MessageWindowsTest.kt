package com.codexpocket.app.cache

import com.codexpocket.app.model.ChatMessage
import com.codexpocket.app.model.MediaAttachment
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageWindowsTest {
    @Test
    fun `fresh messages replace cache and retain older cached messages`() {
        val cached = listOf(message("1", "old"), message("2", "stale"))
        val fresh = listOf(message("2", "fresh"), message("3", "new"))

        val merged = mergeMessageWindows(3, cached, fresh)

        assertEquals(listOf("1", "2", "3"), merged.map { it.id })
        assertEquals("fresh", merged[1].text)
    }

    @Test
    fun `oldest messages are discarded at the phone cache limit`() {
        val merged = mergeMessageWindows(
            3,
            (1..6).map { message(it.toString(), "message-$it") },
        )

        assertEquals(listOf("4", "5", "6"), merged.map { it.id })
    }

    @Test
    fun `discarded failed local message cannot return from an older cache window`() {
        val failed = ChatMessage(
            id = "failed-local",
            turnId = "pending",
            role = "user",
            text = "not delivered",
            kind = "userMessage",
            deliveryState = "failed",
        )
        val authoritative = ChatMessage(
            id = "failed-local",
            turnId = "server-turn",
            role = "user",
            text = "delivered later",
            kind = "userMessage",
        )

        assertEquals(
            emptyList<ChatMessage>(),
            excludeDiscardedLocalMessages(listOf(failed), setOf(failed.id)),
        )
        assertEquals(
            listOf(authoritative),
            excludeDiscardedLocalMessages(listOf(authoritative), setOf(failed.id)),
        )
    }

    @Test
    fun `sparse completion cannot erase a live command or viewed images`() {
        val live = ChatMessage(
            id = "tool-1",
            turnId = "turn-1",
            role = "tool",
            text = "partial output",
            kind = "dynamicToolCall",
            phase = "commentary",
            command = "functions · view_image",
            status = "inProgress",
            attachments = listOf(
                MediaAttachment("image-1", "image", "/tmp/one.png", "one.png", isLocal = true),
                MediaAttachment("image-2", "image", "/tmp/two.png", "two.png", isLocal = true),
            ),
        )
        val sparseCompletion = live.copy(
            text = "",
            phase = null,
            command = null,
            status = "completed",
            attachments = emptyList(),
        )

        val merged = mergeMessageWindows(10, listOf(live), listOf(sparseCompletion)).single()

        assertEquals("functions · view_image", merged.command)
        assertEquals("partial output", merged.text)
        assertEquals("commentary", merged.phase)
        assertEquals("completed", merged.status)
        assertEquals(listOf("/tmp/one.png", "/tmp/two.png"), merged.attachments.map { it.source })
    }

    @Test
    fun `authoritative user item clears a stale local failure badge`() {
        val failed = ChatMessage(
            id = "client-message",
            turnId = "pending",
            role = "user",
            text = "cellular test",
            kind = "userMessage",
            deliveryState = "failed",
        )
        val delivered = failed.copy(turnId = "server-turn", deliveryState = null)

        val merged = mergeMessageWindows(10, listOf(failed), listOf(delivered)).single()

        assertEquals("server-turn", merged.turnId)
        assertEquals(null, merged.deliveryState)
    }

    private fun message(id: String, text: String) = ChatMessage(
        id = id,
        turnId = "turn-$id",
        role = "assistant",
        text = text,
        kind = "agentMessage",
    )
}
