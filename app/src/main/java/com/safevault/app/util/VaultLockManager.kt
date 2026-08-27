package com.safevault.app.util

import android.os.SystemClock
import com.safevault.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for whether the vault is currently unlocked.
 *
 * Locking policy:
 *  - The vault starts locked on every cold start.
 *  - When the app goes to the background we record a timestamp.
 *  - On returning to the foreground we re-lock if the elapsed idle time meets
 *    or exceeds the user's configured auto-lock timeout (0 == lock immediately).
 *
 * [SystemClock.elapsedRealtime] is used (not wall-clock) so changing the system
 * time cannot be abused to bypass the timeout.
 */
@Singleton
class VaultLockManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _keyInvalidated = MutableStateFlow(false)

    /**
     * Latched once the OS destroys the vault key. The lock screen uses it to
     * explain why older notes can no longer be read.
     *
     * Process-scoped only. Persist it if the warning must survive a cold start.
     */
    val keyInvalidated: StateFlow<Boolean> = _keyInvalidated.asStateFlow()

    private var backgroundedAtElapsed: Long? = null

    /** Called after a successful biometric / device-credential authentication. */
    fun unlock() {
        _isUnlocked.value = true
        backgroundedAtElapsed = null
    }

    /** Force an immediate lock (manual "Lock now" action or expired auth window). */
    fun lock() {
        _isUnlocked.value = false
        backgroundedAtElapsed = null
    }

    /**
     * The OS permanently invalidated the Keystore key (the secure lock screen
     * was removed or reset). Notes written with the old key are unrecoverable,
     * so we lock and surface the reason instead of looping on failed unlocks.
     */
    fun onKeyInvalidated() {
        _keyInvalidated.value = true
        lock()
    }

    /** Record the moment the app was last sent to the background. */
    fun onAppBackgrounded() {
        if (_isUnlocked.value) {
            backgroundedAtElapsed = SystemClock.elapsedRealtime()
        }
    }

    /**
     * Evaluate the auto-lock policy when the app returns to the foreground.
     * Runs on [scope] because it needs to read the current timeout preference.
     */
    fun onAppForegrounded(scope: CoroutineScope) {
        val backgroundedAt = backgroundedAtElapsed ?: return
        if (!_isUnlocked.value) return

        scope.launch {
            val timeout = settingsRepository.settings.first().autoLockTimeout
            val idleMillis = SystemClock.elapsedRealtime() - backgroundedAt
            if (idleMillis >= timeout.millis) {
                lock()
            } else {
                backgroundedAtElapsed = null
            }
        }
    }
}
