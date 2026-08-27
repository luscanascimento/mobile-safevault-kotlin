package com.safevault.app.domain.model

/**
 * A decrypted vault note as used by the UI and domain layers. The plaintext
 * [content] is encrypted by the repository before it reaches storage; see
 * `NoteEntity` for what is and is not persisted as ciphertext.
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
 * Case-insensitive substring search over title, tags and decrypted content.
 * A blank query matches everything.
 */
fun Note.matches(query: String): Boolean {
    val needle = query.trim()
    return needle.isEmpty() ||
        title.contains(needle, ignoreCase = true) ||
        content.contains(needle, ignoreCase = true) ||
        tags.any { it.contains(needle, ignoreCase = true) }
}
