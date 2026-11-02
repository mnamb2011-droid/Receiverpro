package com.smartreceiverpro.core.common

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CoreCommonUnitTest {

    @Test
    fun `StructuredLogger redacts plaintext passwords and parental PINs`() {
        val raw = "Connecting with password=MySecret1234 and {\"Password\":\"0000\",\"pin\":\"9876\"}"
        val sanitized = StructuredLogger.sanitize(raw)
        assertFalse(sanitized.contains("MySecret1234"))
        assertFalse(sanitized.contains("9876"))
        assertTrue(sanitized.contains("[REDACTED]"))
    }

    @Test
    fun `CommandRateLimiter blocks rapid duplicate commands and allows after interval`() = runTest {
        var fakeNow = 1_000L
        val limiter = CommandRateLimiter(minIntervalMs = 150L, timeProvider = { fakeNow })

        assertTrue(limiter.tryAcquire("REMOTE_CH_UP"))
        assertFalse("Immediate duplicate should be rate-limited", limiter.tryAcquire("REMOTE_CH_UP"))

        fakeNow += 200L
        assertTrue("Command after interval should succeed", limiter.tryAcquire("REMOTE_CH_UP"))
    }
}
