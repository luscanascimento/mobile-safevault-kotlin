package com.safevault.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.safevault.app.domain.model.AppSettings
import com.safevault.app.domain.repository.SettingsRepository
import com.safevault.app.ui.navigation.SafeVaultNavGraph
import com.safevault.app.ui.screens.lock.LockScreen
import com.safevault.app.ui.theme.SafeVaultTheme
import com.safevault.app.util.BiometricAuthenticator
import com.safevault.app.util.BiometricAvailability
import com.safevault.app.util.VaultLockManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/**
 * Single-activity host. Extends [FragmentActivity] because [BiometricPrompt]
 * requires it. Owns the lock gate: the encrypted vault UI is only composed
 * once [VaultLockManager] reports an unlocked session.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var lockManager: VaultLockManager

    @Inject lateinit var settingsRepository: SettingsRepository

    private val lockErrorState = MutableStateFlow<String?>(null)

    /** True only while the device has no biometric/credential enrolled. */
    private val needsEnrollmentState = MutableStateFlow(false)

    private lateinit var biometricAuthenticator: BiometricAuthenticator

    /** Re-checks availability when the user comes back from system settings. */
    private val enrollmentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            promptUnlock()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FLAG_SECURE for the whole window: every screen (lock, vault, editor,
        // settings) and every transition between them is excluded from
        // screenshots and from the recent-apps preview.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        enableEdgeToEdge()

        biometricAuthenticator = BiometricAuthenticator(this)

        // Vault always starts locked; observe lifecycle for auto-lock policy.
        observeLifecycleForAutoLock()

        setContent {
            val settings by settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            SafeVaultTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        lockManager = lockManager,
                        lockError = lockErrorState,
                        needsEnrollment = needsEnrollmentState,
                        onRequestUnlock = ::promptUnlock,
                        onRequestEnrollment = {
                            BiometricAuthenticator.launchEnrollment(enrollmentLauncher)
                        },
                    )
                }
            }
        }
    }

    /** Trigger the biometric / device-credential prompt. */
    private fun promptUnlock() {
        val availability = biometricAuthenticator.availability()
        needsEnrollmentState.value = availability == BiometricAvailability.NONE_ENROLLED

        when (availability) {
            BiometricAvailability.AVAILABLE -> {
                lockErrorState.value = null
                biometricAuthenticator.authenticate(
                    title = getString(R.string.biometric_prompt_title),
                    subtitle = getString(R.string.biometric_prompt_subtitle),
                    onSuccess = { lockManager.unlock() },
                    onError = { message -> lockErrorState.value = message },
                )
            }
            BiometricAvailability.NONE_ENROLLED ->
                lockErrorState.value = getString(R.string.biometric_none_enrolled)
            BiometricAvailability.UNAVAILABLE ->
                lockErrorState.value = getString(R.string.biometric_not_available)
        }
    }

    /**
     * Records background timestamps and re-evaluates the auto-lock timeout each
     * time the activity returns to the foreground.
     */
    private fun observeLifecycleForAutoLock() {
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> lockManager.onAppBackgrounded()
                    Lifecycle.Event.ON_START -> lockManager.onAppForegrounded(lifecycleScope)
                    else -> Unit
                }
            },
        )
    }
}

@Composable
private fun AppRoot(
    lockManager: VaultLockManager,
    lockError: MutableStateFlow<String?>,
    needsEnrollment: MutableStateFlow<Boolean>,
    onRequestUnlock: () -> Unit,
    onRequestEnrollment: () -> Unit,
) {
    val isUnlocked by lockManager.isUnlocked.collectAsStateWithLifecycle()
    val keyInvalidated by lockManager.keyInvalidated.collectAsStateWithLifecycle()
    val error by lockError.collectAsStateWithLifecycle()
    val enrollmentNeeded by needsEnrollment.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    // Auto-launch the prompt the first time we present the lock screen. After a
    // key invalidation we wait for an explicit tap instead, so the explanation
    // is not hidden behind the system prompt.
    LaunchedEffect(isUnlocked, keyInvalidated) {
        if (!isUnlocked && !keyInvalidated) onRequestUnlock()
    }

    AnimatedContent(
        targetState = isUnlocked,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "lock-gate",
    ) { unlocked ->
        if (unlocked) {
            SafeVaultNavGraph(navController = navController)
        } else {
            LockScreen(
                errorMessage = if (keyInvalidated) {
                    stringResource(R.string.lock_key_invalidated)
                } else {
                    error
                },
                onUnlockClick = onRequestUnlock,
                onEnrollClick = onRequestEnrollment.takeIf { enrollmentNeeded },
            )
        }
    }
}
