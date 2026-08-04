package com.codexpocket.app.ui

import com.codexpocket.app.model.ChatMessage
import com.codexpocket.app.model.ActivityEntry
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

    @Test
    fun `live process preview prefers the newest commentary over an old tool detail`() {
        val preview = processProgressPreview(
            messages = listOf(message("最新进度", phase = "commentary")),
            activities = listOf(ActivityEntry("old-tool", "旧命令", detail = "旧工具输出")),
            statusDetail = "旧状态",
            isLive = true,
        )

        assertEquals("最新进度", preview)
    }

    @Test
    fun `active process follows steering and failed messages at the end of the timeline`() {
        val timeline = buildChatTimeline(
            listOf(
                message("user", role = "user", kind = "userMessage"),
                message("progress", phase = "commentary"),
                message("steer", role = "user", kind = "userMessage"),
            ),
        )

        val ordered = moveActiveProcessToEnd(timeline, "turn-1")

        assertTrue(ordered.last() is TimelineProcess)
        assertEquals(listOf("user", "steer"), ordered.dropLast(1).map {
            (it as TimelineMessage).message.id
        })
    }

    @Test
    fun `completed process stays directly above the final answer after steering`() {
        val timeline = buildChatTimeline(
            listOf(
                message("user", role = "user", kind = "userMessage"),
                message("progress-1", phase = "commentary"),
                message("steer", role = "user", kind = "userMessage"),
                message("progress-2", phase = "commentary"),
                message("final", phase = "final_answer"),
            ),
        )

        assertEquals(4, timeline.size)
        assertEquals("steer", (timeline[1] as TimelineMessage).message.id)
        assertTrue(timeline[2] is TimelineProcess)
        assertEquals("final", (timeline[3] as TimelineMessage).message.id)
    }

    @Test
    fun `desktop style activity summary keeps commands on one useful line`() {
        val command = ChatMessage(
            id = "command",
            turnId = "turn-1",
            role = "tool",
            text = "long command output",
            kind = "commandExecution",
            command = "rg -n processActivitySummary android",
            status = "completed",
        )

        assertEquals(
            "已运行 rg -n processActivitySummary android",
            processActivitySummary(command),
        )
        assertEquals(
            "正在运行 rg -n processActivitySummary android",
            processActivitySummary(command, activeOverride = true),
        )
    }

    @Test
    fun `live status prefers the latest unfinished activity`() {
        val status = processLiveStatus(
            activities = listOf(
                ActivityEntry("old", "旧命令", phase = "completed"),
                ActivityEntry("current", "正在检查", detail = "同步消息", phase = "started"),
            ),
            currentStatus = "正在处理",
            statusDetail = "备用状态",
        )

        assertEquals("正在检查 · 同步消息", status)
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
