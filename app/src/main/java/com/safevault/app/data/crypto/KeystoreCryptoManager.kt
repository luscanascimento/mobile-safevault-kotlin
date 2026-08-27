package com.safevault.app.data.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.security.keystore.UserNotAuthenticatedException
import com.safevault.app.domain.model.AutoLockTimeout
import com.safevault.app.util.VaultLockManager
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM encryption backed by a non-exportable, auth-bound key in the
 * Android Keystore.
 *
 * Two decisions here are not obvious from the code:
 *
 * 1. The IV is never supplied on encrypt — the provider generates a fresh one
 *    per operation and we store it with the ciphertext. Reusing an IV under GCM
 *    leaks the keystream, so letting the caller pick one is not an option.
 * 2. The key uses a validity *window* rather than per-operation authentication
 *    (`setUserAuthenticationParameters(0, ...)`). Per-operation binding needs a
 *    `BiometricPrompt` + `CryptoObject` round trip for every `Cipher`, i.e. one
 *    prompt per note on every list render. The price of the window: the key is
 *    bound to the secure lock screen (removing/resetting it invalidates the
 *    key) but survives enrolling an extra fingerprint.
 */
@Singleton
class KeystoreCryptoManager @Inject constructor(
    private val lockManager: VaultLockManager,
) : CryptoManager {

    override fun encrypt(plaintext: String): EncryptedPayload = runCrypto("Encryption") {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        EncryptedPayload(
            iv = cipher.iv,
            ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)),
        )
    }

    override fun decrypt(payload: EncryptedPayload): String = runCrypto("Decryption") {
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.iv)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        }
        String(cipher.doFinal(payload.ciphertext), Charsets.UTF_8)
    }

    /**
     * Maps Keystore failures onto [CryptoFailure] so callers can tell a single
     * unreadable record apart from a session-wide authentication problem.
     */
    private inline fun <T> runCrypto(operation: String, block: () -> T): T = try {
        block()
    } catch (e: UserNotAuthenticatedException) {
        // The authenticated window elapsed: drop back to the lock screen, which
        // re-presents BiometricPrompt and refreshes the Keystore auth token.
        lockManager.lock()
        throw CryptoException(
            CryptoFailure.AUTH_REQUIRED,
            "$operation requires a fresh authentication",
            e,
        )
    } catch (e: KeyPermanentlyInvalidatedException) {
        // The secure lock screen was removed/reset. Everything written with the
        // old key is unrecoverable; drop the dead alias so a fresh key can be
        // created and the vault stays usable, then warn on the lock screen.
        deleteKey()
        lockManager.onKeyInvalidated()
        throw CryptoException(
            CryptoFailure.KEY_INVALIDATED,
            "$operation failed: the vault key was invalidated by the OS",
            e,
        )
    } catch (e: Exception) {
        throw CryptoException(CryptoFailure.UNREADABLE, "$operation failed", e)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        return generateKey()
    }

    private fun deleteKey() {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )

        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                AUTH_VALIDITY_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        } else {
            // minSdk 26: pre-API-30 equivalent of the call above.
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
        }

        // StrongBox only exists from API 28, and only StrongBoxUnavailableException
        // is worth retrying: any other failure means the spec itself is bad, and
        // re-running the identical call would just lose the original cause.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            generator.init(builder.build())
            return generator.generateKey()
        }

        builder.setIsStrongBoxBacked(true)
        return try {
            generator.init(builder.build())
            generator.generateKey()
        } catch (e: StrongBoxUnavailableException) {
            generator.init(builder.setIsStrongBoxBacked(false).build())
            generator.generateKey()
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "safevault_master_key"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val TRANSFORMATION =
            "${KeyProperties.KEY_ALGORITHM_AES}/" +
                "${KeyProperties.BLOCK_MODE_GCM}/" +
                KeyProperties.ENCRYPTION_PADDING_NONE

        /**
         * How long the Keystore keeps accepting a previous authentication. Kept
         * equal to the longest auto-lock the user can pick, so the OS window is
         * never shorter than a legitimately unlocked session.
         */
        private val AUTH_VALIDITY_SECONDS =
            (AutoLockTimeout.entries.maxOf { it.millis } / 1_000L).toInt()
    }
}
