package com.codexpocket.app.ui

import com.codexpocket.app.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTimelineTest {
    @Test
    fun `shows final answer and folds commentary with internal work`() {
        val timeline = buildChatTimeline(
            listOf(
                message("user", role = "user", kind = "userMessage"),
                message("update", phase = "commentary"),
                message("reasoning", role = "status", kind = "reasoning"),
                message("tool", role = "tool", kind = "commandExecution"),
                message("final", phase = "final_answer"),
            ),
        )

        assertEquals(3, timeline.size)
        assertEquals("user", (timeline[0] as TimelineMessage).message.id)
        assertEquals(
            listOf("update", "reasoning", "tool"),
            (timeline[1] as TimelineProcess).messages.map { it.id },
        )
        assertEquals("final", (timeline[2] as TimelineMessage).message.id)
    }

    @Test
    fun `old cache without phase keeps only latest assistant message visible`() {
        val timeline = buildChatTimeline(
            listOf(
                message("first-update"),
                message("second-update"),
                message("latest-result"),
            ),
        )

        assertEquals(2, timeline.size)
        assertEquals(
            listOf("first-update", "second-update"),
            (timeline[0] as TimelineProcess).messages.map { it.id },
        )
        assertEquals("latest-result", (timeline[1] as TimelineMessage).message.id)
    }

    @Test
    fun `live commentary stays inside one compact process row`() {
        val timeline = buildChatTimeline(
            listOf(
                message("update-1", phase = "commentary"),
                message("update-2", phase = "commentary"),
            ),
        )

        assertEquals(1, timeline.size)
        assertTrue(timeline.single() is TimelineProcess)
        assertEquals("process-turn-1", timeline.single().key)
    }

    private fun message(
        id: String,
        role: String = "assistant",
        kind: String = "agentMessage",
        phase: String? = null,
    ) = ChatMessage(
        id = id,
        turnId = "turn-1",
        role = role,
        text = id,
        kind = kind,
        phase = phase,
    )
}
