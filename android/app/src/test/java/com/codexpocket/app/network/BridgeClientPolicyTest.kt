package com.codexpocket.app.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeClientPolicyTest {
    @Test
    fun `retries read requests after a transient reconnect`() {
        assertTrue(isRetryableBridgeMethod("threads.list"))
        assertTrue(isRetryableBridgeMethod("thread.read"))
        assertTrue(isRetryableBridgeMethod("account.status"))
    }

    @Test
    fun `never automatically repeats mutating requests`() {
        assertFalse(isRetryableBridgeMethod("turn.start"))
        assertFalse(isRetryableBridgeMethod("turn.steer"))
        assertFalse(isRetryableBridgeMethod("thread.archive"))
    }

    @Test
    fun `restarts an active connection when the underlying network changes`() {
        assertTrue(shouldRestartForNetworkChange("wifi:true", "cellular:true", true))
        assertFalse(shouldRestartForNetworkChange("wifi:true", "wifi:true", true))
        assertFalse(shouldRestartForNetworkChange("wifi:true", "cellular:true", false))
    }
}
