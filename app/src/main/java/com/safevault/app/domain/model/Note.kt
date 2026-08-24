package com.safevault.app.domain.model

/**
 * A decrypted vault note as used by the UI and domain layers.
 *
 * The plaintext [content] only ever exists in memory. It is encrypted with
 * AES-256-GCM (see the crypto layer) before being persisted and is discarded
 * as soon as the vault locks.
 */
data class Note(
    val id: Long = 0L,
    val title: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Case-insensitive search rule over title, tags and the decrypted content.
 * A blank query matches everything.
 *
 * Matching is a pure in-memory pass on notes that were already decrypted for
 * the list, so typing costs no extra Keystore operations.
 */
fun Note.matches(query: String): Boolean {
    val needle = query.trim()
    return needle.isEmpty() ||
        title.contains(needle, ignoreCase = true) ||
        content.contains(needle, ignoreCase = true) ||
        tags.any { it.contains(needle, ignoreCase = true) }
}
