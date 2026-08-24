package com.safevault.app.util

import android.os.SystemClock
import com.safevault.app.domain.model.AppSettings
import com.safevault.app.domain.model.AutoLockTimeout
import com.safevault.app.domain.model.ThemeMode
import com.safevault.app.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Auto-lock policy.
 *
 * The monotonic [SystemClock.elapsedRealtime] is stubbed so idle time can be
 * driven deterministically. That stub is also what proves the policy ignores
 * the wall clock: these tests move *uptime* while barely any real time passes,
 * so an implementation reading `System.currentTimeMillis()` would never reach
 * the timeout and would fail the assertions below.
 */
class VaultLockManagerTest {

    private val settingsFlow = MutableStateFlow(AppSettings())

    private val settingsRepository = object : SettingsRepository {
        override val settings: Flow<AppSettings> = settingsFlow
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun setDynamicColor(enabled: Boolean) = Unit
        override suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) = Unit
    }

    private var uptime = 1_000L

    @Before
    fun setUp() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { uptime }
    }

    @After
    fun tearDown() {
        unmockkStatic(SystemClock::class)
    }

    private fun manager(timeout: AutoLockTimeout): VaultLockManager {
        settingsFlow.value = AppSettings(autoLockTimeout = timeout)
        return VaultLockManager(settingsRepository)
    }

    @Test
    fun `stays unlocked when idle time is below the timeout`() = runTest {
        val lockManager = manager(AutoLockTimeout.ONE_MINUTE)

        lockManager.unlock()
        lockManager.onAppBackgrounded()
        uptime += 59_000L
        lockManager.onAppForegrounded(this)
        advanceUntilIdle()

        assertTrue(lockManager.isUnlocked.value)
    }

    @Test
    fun `locks once idle time reaches the timeout`() = runTest {
        val lockManager = manager(AutoLockTimeout.ONE_MINUTE)

        lockManager.unlock()
        lockManager.onAppBackgrounded()
        uptime += 60_000L
        lockManager.onAppForegrounded(this)
        advanceUntilIdle()

        assertFalse(lockManager.isUnlocked.value)
    }

    @Test
    fun `IMMEDIATELY locks on any return to the foreground`() = runTest {
        val lockManager = manager(AutoLockTimeout.IMMEDIATELY)

        lockManager.unlock()
        lockManager.onAppBackgrounded()
        lockManager.onAppForegrounded(this)
        advanceUntilIdle()

        assertFalse(lockManager.isUnlocked.value)
    }

    @Test
    fun `starts locked and a foreground without a preceding background is a no-op`() = runTest {
        val lockManager = manager(AutoLockTimeout.TEN_MINUTES)

        assertFalse(lockManager.isUnlocked.value)

        lockManager.unlock()
        uptime += 60 * 60_000L
        lockManager.onAppForegrounded(this)
        advanceUntilIdle()

        assertTrue(lockManager.isUnlocked.value)
    }

    @Test
    fun `backgrounding while locked cannot unlock on the next foreground`() = runTest {
        val lockManager = manager(AutoLockTimeout.IMMEDIATELY)

        lockManager.onAppBackgrounded()
        lockManager.onAppForegrounded(this)
        advanceUntilIdle()

        assertFalse(lockManager.isUnlocked.value)
    }

    @Test
    fun `key invalidation locks the vault and latches the warning`() = runTest {
        val lockManager = manager(AutoLockTimeout.ONE_MINUTE)

        lockManager.unlock()
        assertFalse(lockManager.keyInvalidated.value)

        lockManager.onKeyInvalidated()

        assertFalse(lockManager.isUnlocked.value)
        assertTrue(lockManager.keyInvalidated.value)

        // The warning outlives a later unlock: the old notes stay unreadable,
        // so the user must keep seeing why.
        lockManager.unlock()
        assertTrue(lockManager.keyInvalidated.value)
    }
}
