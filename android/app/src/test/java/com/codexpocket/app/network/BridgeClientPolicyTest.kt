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
        assertTrue(isRetryableBridgeMethod("events.replay"))
    }

    @Test
    fun `retries only server deduplicated message mutations`() {
        assertTrue(isRetryableBridgeMethod("turn.start"))
        assertTrue(isRetryableBridgeMethod("turn.steer"))
        assertTrue(requestTimeoutMillis("turn.start") < requestTimeoutMillis("thread.read"))
    }

    @Test
    fun `never automatically repeats mutating requests`() {
        assertFalse(isRetryableBridgeMethod("thread.archive"))
    }

    @Test
    fun `restarts an active connection when the underlying network changes`() {
        assertTrue(shouldRestartForNetworkChange("wifi:true", "cellular:true", true))
        assertFalse(shouldRestartForNetworkChange("wifi:true", "wifi:true", true))
        assertFalse(shouldRestartForNetworkChange("wifi:true", "cellular:true", false))
    }

    @Test
    fun `keeps network recovery retries quick during a handover`() {
        assertTrue(reconnectDelaySeconds(0) <= 1)
        assertTrue(reconnectDelaySeconds(4) <= 3)
        assertTrue(reconnectDelaySeconds(100) <= 10)
    }
}
