package com.codexpocket.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class InfrastructureTest {
    @Test
    fun buildsAuthenticatedCameraUrlFromBridgeEndpoint() {
        assertEquals(
            "http://100.64.1.2:8787/camera/stream?device=rsj-pc&camera=v4l-video0&token=a%2Bb+c",
            cameraStreamUrl(
                "ws://100.64.1.2:8787/ws",
                "a+b c",
                "rsj-pc",
                "v4l-video0",
            ),
        )
    }
}
