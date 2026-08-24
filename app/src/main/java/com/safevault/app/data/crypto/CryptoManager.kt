package com.safevault.app.data.crypto

/** Ciphertext together with the GCM nonce (IV) required to decrypt it. */
data class EncryptedPayload(
    val iv: ByteArray,
    val ciphertext: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedPayload) return false
        return iv.contentEquals(other.iv) && ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int = 31 * iv.contentHashCode() + ciphertext.contentHashCode()
}

/**
 * Symmetric authenticated-encryption abstraction.
 *
 * The concrete implementation uses AES-256-GCM with a key held in the
 * Android Keystore (hardware-backed / StrongBox where available), so the raw
 * key material never leaves the secure element and cannot be extracted by the
 * app process.
 */
interface CryptoManager {
    /** Encrypt UTF-8 [plaintext], producing a fresh random IV per call. */
    fun encrypt(plaintext: String): EncryptedPayload

    /** Decrypt [payload] back to the original UTF-8 string. */
    fun decrypt(payload: EncryptedPayload): String
}

/** Why a crypto operation failed — decides whether the session can continue. */
enum class CryptoFailure {
    /** The authenticated window elapsed; the user must authenticate again. */
    AUTH_REQUIRED,

    /** The OS destroyed the key (secure lock screen removed or reset). */
    KEY_INVALIDATED,

    /** Corrupt or foreign ciphertext — only this record is affected. */
    UNREADABLE,
}

/** Thrown when encryption or decryption fails. */
class CryptoException(
    val failure: CryptoFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
