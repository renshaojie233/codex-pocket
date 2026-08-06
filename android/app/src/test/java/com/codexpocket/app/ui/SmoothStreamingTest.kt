package com.codexpocket.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmoothStreamingTest {
    @Test
    fun `reveals short replies one code point at a time`() {
        assertEquals(1, smoothStreamChunkCodePoints(12))
        assertEquals(2, smoothStreamChunkCodePoints(60))
        assertTrue(smoothStreamChunkCodePoints(600) <= 8)
    }

    @Test
    fun `never splits emoji surrogate pairs`() {
        val (prefix, remainder) = takeCodePointPrefix("你🙂好", 2)

        assertEquals("你🙂", prefix)
        assertEquals("好", remainder)
    }
}
