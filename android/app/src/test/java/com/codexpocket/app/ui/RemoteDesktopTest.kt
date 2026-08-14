package com.codexpocket.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteDesktopTest {
    @Test
    fun streamDownloadUsesConfiguredBridgeAuthority() {
        assertEquals(
            "http://100.105.90.3:8787/download/codex-stream.apk",
            codexStreamDownloadUrl("ws://100.105.90.3:8787/ws"),
        )
    }

    @Test
    fun streamDownloadPreservesSecureTransport() {
        assertEquals(
            "https://bridge.example.test/download/codex-stream.apk",
            codexStreamDownloadUrl("wss://bridge.example.test/ws?client=foreground"),
        )
    }
}
