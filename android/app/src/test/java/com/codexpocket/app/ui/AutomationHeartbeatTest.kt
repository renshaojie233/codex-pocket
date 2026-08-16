package com.codexpocket.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutomationHeartbeatTest {
    @Test
    fun parsesHeartbeatEnvelope() {
        val parsed = parseAutomationHeartbeat(
            """
            <heartbeat>
              <automation_id>take-data-pi05-30hz-async</automation_id>
              <decision>NOTIFY</decision>
              <message>转换正常，完成3705/4191 episodes &amp; 错误为0。</message>
            </heartbeat>
            """.trimIndent(),
        )

        assertEquals("take-data-pi05-30hz-async", parsed?.automationId)
        assertEquals("NOTIFY", parsed?.decision)
        assertEquals("转换正常，完成3705/4191 episodes & 错误为0。", parsed?.message)
    }

    @Test
    fun ignoresOrdinaryMarkdown() {
        assertNull(parseAutomationHeartbeat("## 正常回复"))
    }
}
