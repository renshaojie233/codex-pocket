package com.codexpocket.app.cache

import com.codexpocket.app.model.ChatMessage
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

    private fun message(id: String, text: String) = ChatMessage(
        id = id,
        turnId = "turn-$id",
        role = "assistant",
        text = text,
        kind = "agentMessage",
    )
}
