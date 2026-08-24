package com.safevault.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Search rule used by the vault list (title, tags and decrypted content). */
class NoteMatchesTest {

    private val note = Note(
        id = 1L,
        title = "Bank card",
        content = "PIN 1234",
        tags = listOf("finance", "cards"),
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `blank query matches everything`() {
        assertTrue(note.matches(""))
        assertTrue(note.matches("   "))
    }

    @Test
    fun `matches title tags and content case-insensitively`() {
        assertTrue(note.matches("bank"))
        assertTrue(note.matches("CARDS"))
        assertTrue(note.matches("1234"))
    }

    @Test
    fun `query is trimmed before matching`() {
        assertTrue(note.matches("  bank  "))
    }

    @Test
    fun `unrelated query does not match`() {
        assertFalse(note.matches("passport"))
    }
}
