package com.safevault.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoLockTimeoutTest {

    @Test
    fun `fromMillis maps known values`() {
        assertEquals(AutoLockTimeout.IMMEDIATELY, AutoLockTimeout.fromMillis(0L))
        assertEquals(AutoLockTimeout.THIRTY_SECONDS, AutoLockTimeout.fromMillis(30_000L))
        assertEquals(AutoLockTimeout.FIVE_MINUTES, AutoLockTimeout.fromMillis(5 * 60_000L))
    }

    @Test
    fun `fromMillis falls back to one minute for unknown values`() {
        assertEquals(AutoLockTimeout.ONE_MINUTE, AutoLockTimeout.fromMillis(123_456L))
    }
}
